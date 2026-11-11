"""La file de tâches : dépôt et lecture. Seul endroit du service qui écrit du SQL sur ia.taches."""

import hashlib
import json
from typing import Any
from uuid import UUID, uuid4

import asyncpg

# Identifiant arbitraire du verrou PostgreSQL qui sérialise les dépôts (limite exacte).
_VERROU_LIMITE = 7420_2611_10


class CleReutiliseeError(Exception):
    """La clé d'idempotence a déjà servi pour une entrée différente."""


class LimiteAtteinteError(Exception):
    """Trop de tâches déposées dans la dernière heure (dernier rempart, voir contracts/ia.yaml)."""


def empreinte(type_tache: str, entree: dict[str, Any]) -> str:
    # Clés triées : deux entrées identiques donnent la même empreinte, quel que soit l'ordre.
    texte = json.dumps([type_tache, entree], sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(texte.encode()).hexdigest()


class DepotTaches:
    def __init__(self, pool: asyncpg.Pool, limite_par_heure: int) -> None:
        self._pool = pool
        self._limite_par_heure = limite_par_heure

    async def deposer(
        self, cle: UUID, type_tache: str, entree: dict[str, Any]
    ) -> tuple[dict[str, Any], bool]:
        """Renvoie (tâche, créée). `créée` est faux si la clé avait déjà servi pour cette entrée."""
        marque = empreinte(type_tache, entree)
        async with self._pool.acquire() as connexion, connexion.transaction():
            existante = await self._par_cle(connexion, cle)
            if existante is None:
                # Verrou le temps de la transaction : le comptage et l'insertion ne peuvent pas
                # être entrelacés par un autre dépôt, donc la limite reste exacte.
                await connexion.execute("SELECT pg_advisory_xact_lock($1)", _VERROU_LIMITE)
                existante = await self._par_cle(connexion, cle)
            if existante is None:
                await self._verifier_limite(connexion)
                creee = await connexion.fetchrow(
                    "INSERT INTO ia.taches (id, cle_idempotence, type, entree, empreinte) "
                    "VALUES ($1, $2, $3, $4, $5) "
                    "ON CONFLICT (cle_idempotence) DO NOTHING RETURNING *",
                    uuid4(),
                    cle,
                    type_tache,
                    entree,
                    marque,
                )
                if creee is not None:
                    return dict(creee), True
                # Deux requêtes avec la même clé au même instant : l'autre a gagné.
                existante = await self._par_cle(connexion, cle)
            if existante["empreinte"] != marque:
                raise CleReutiliseeError
            return dict(existante), False

    async def lire(self, tache_id: UUID) -> dict[str, Any] | None:
        async with self._pool.acquire() as connexion:
            ligne = await connexion.fetchrow("SELECT * FROM ia.taches WHERE id = $1", tache_id)
        return dict(ligne) if ligne else None

    async def prendre_suivante(self) -> dict[str, Any] | None:
        """Réserve la prochaine tâche à traiter, ou rien si la file est vide.

        `SKIP LOCKED` : deux travailleurs ne prennent jamais la même tâche. La tâche est
        réservée 2 minutes ; si le travailleur meurt, elle redevient disponible ensuite.
        """
        async with self._pool.acquire() as connexion:
            ligne = await connexion.fetchrow(
                "UPDATE ia.taches SET statut = 'en_cours', essais = essais + 1, "
                "disponible_le = now() + interval '2 minutes' "
                "WHERE id = (SELECT id FROM ia.taches "
                "WHERE statut IN ('en_attente', 'en_cours') AND disponible_le <= now() "
                "ORDER BY disponible_le FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *"
            )
        return dict(ligne) if ligne else None

    async def terminer(self, tache_id: UUID, resultat: dict[str, Any], origine: str) -> None:
        await self._executer(
            "UPDATE ia.taches SET statut = 'terminee', resultat = $2, origine = $3, "
            "termine_le = now() WHERE id = $1",
            tache_id,
            resultat,
            origine,
        )

    async def echouer(self, tache_id: UUID, erreur_code: str) -> None:
        await self._executer(
            "UPDATE ia.taches SET statut = 'echouee', erreur_code = $2, termine_le = now() "
            "WHERE id = $1",
            tache_id,
            erreur_code,
        )

    async def remettre(self, tache_id: UUID, delai_secondes: int) -> None:
        await self._executer(
            "UPDATE ia.taches SET statut = 'en_attente', "
            "disponible_le = now() + make_interval(secs => $2) WHERE id = $1",
            tache_id,
            delai_secondes,
        )

    async def resultat_en_cache(self, marque: str) -> dict[str, Any] | None:
        """Résultat produit par l'IA pour la même entrée il y a moins de 24 h, s'il existe."""
        async with self._pool.acquire() as connexion:
            return await connexion.fetchval(
                "SELECT resultat FROM ia.taches WHERE empreinte = $1 AND statut = 'terminee' "
                "AND origine = 'ia' AND termine_le > now() - interval '24 hours' "
                "ORDER BY termine_le DESC LIMIT 1",
                marque,
            )

    async def _executer(self, requete: str, *valeurs: Any) -> None:
        async with self._pool.acquire() as connexion:
            await connexion.execute(requete, *valeurs)

    async def base_joignable(self) -> bool:
        try:
            async with self._pool.acquire(timeout=3) as connexion:
                await connexion.execute("SELECT 1")
            return True
        except (asyncpg.PostgresError, asyncpg.InterfaceError, OSError, TimeoutError):
            return False

    @staticmethod
    async def _par_cle(connexion: asyncpg.Connection, cle: UUID) -> asyncpg.Record | None:
        return await connexion.fetchrow("SELECT * FROM ia.taches WHERE cle_idempotence = $1", cle)

    async def _verifier_limite(self, connexion: asyncpg.Connection) -> None:
        # Appelé sous le verrou de `deposer`. La limite par utilisateur reste faite par l'API.
        nombre = await connexion.fetchval(
            "SELECT count(*) FROM ia.taches WHERE cree_le > now() - interval '1 hour'"
        )
        if nombre >= self._limite_par_heure:
            raise LimiteAtteinteError
