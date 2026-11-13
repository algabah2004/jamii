"""La file de tâches : dépôt et lecture. Seul endroit du service qui écrit du SQL sur ia.taches."""

import hashlib
import json
from typing import Any
from uuid import UUID, uuid4

import asyncpg

# Au-delà, une tâche n'est plus reprise : elle échoue au lieu de tourner sans fin.
ESSAIS_MAX = 3

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
        Le nombre d'essais sert de jeton de réservation : seul le dernier travailleur à
        l'avoir prise peut l'écrire (voir `_ecrire`).
        """
        async with self._pool.acquire() as connexion:
            # Une tâche abandonnée en cours de route après le dernier essai ne repart pas.
            await connexion.execute(
                "UPDATE ia.taches SET statut = 'echouee', erreur_code = 'delai_depasse', "
                "termine_le = now() WHERE statut = 'en_cours' AND disponible_le <= now() "
                "AND essais >= $1",
                ESSAIS_MAX,
            )
            ligne = await connexion.fetchrow(
                "UPDATE ia.taches SET statut = 'en_cours', essais = essais + 1, "
                "disponible_le = now() + interval '2 minutes' "
                "WHERE id = (SELECT id FROM ia.taches "
                "WHERE statut IN ('en_attente', 'en_cours') AND disponible_le <= now() "
                "AND essais < $1 "
                "ORDER BY disponible_le FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *",
                ESSAIS_MAX,
            )
        return dict(ligne) if ligne else None

    async def terminer(self, tache: dict[str, Any], resultat: dict[str, Any], origine: str) -> bool:
        return await self._ecrire(
            tache,
            "statut = 'terminee', resultat = $3, origine = $4, termine_le = now()",
            resultat,
            origine,
        )

    async def echouer(self, tache: dict[str, Any], erreur_code: str) -> bool:
        return await self._ecrire(
            tache, "statut = 'echouee', erreur_code = $3, termine_le = now()", erreur_code
        )

    async def remettre(self, tache: dict[str, Any], delai_secondes: int) -> bool:
        return await self._ecrire(
            tache,
            "statut = 'en_attente', disponible_le = now() + make_interval(secs => $3)",
            delai_secondes,
        )

    async def _ecrire(self, tache: dict[str, Any], affectations: str, *valeurs: Any) -> bool:
        """Écrit seulement si la tâche est toujours réservée par ce travailleur.

        Si un appel IA a dépassé la réservation et qu'un autre travailleur a repris la tâche,
        `essais` a changé : l'écriture tardive est ignorée. Renvoie faux dans ce cas.
        """
        async with self._pool.acquire() as connexion:
            etat = await connexion.execute(
                f"UPDATE ia.taches SET {affectations} "  # noqa: S608 (affectations fixes, pas d'entrée)
                "WHERE id = $1 AND statut = 'en_cours' AND essais = $2",
                tache["id"],
                tache["essais"],
                *valeurs,
            )
        return etat == "UPDATE 1"

    async def resultat_en_cache(self, marque: str) -> dict[str, Any] | None:
        """Résultat produit par l'IA pour la même entrée il y a moins de 24 h, s'il existe."""
        async with self._pool.acquire() as connexion:
            return await connexion.fetchval(
                "SELECT resultat FROM ia.taches WHERE empreinte = $1 AND statut = 'terminee' "
                "AND origine = 'ia' AND termine_le > now() - interval '24 hours' "
                "ORDER BY termine_le DESC LIMIT 1",
                marque,
            )

    async def ajouter_jetons(self, tache_id: UUID, jetons: int) -> None:
        """Compté dès l'appel, même si la réponse est ensuite refusée : c'est payé."""
        if jetons > 0:
            await self._executer(
                "UPDATE ia.taches SET jetons = jetons + $2 WHERE id = $1", tache_id, jetons
            )

    async def jetons_du_jour(self) -> int:
        """Jetons consommés depuis minuit UTC, tous appels confondus."""
        async with self._pool.acquire() as connexion:
            return await connexion.fetchval(
                "SELECT coalesce(sum(jetons), 0) FROM ia.taches "
                "WHERE jetons > 0 AND cree_le >= date_trunc('day', now() AT TIME ZONE 'UTC') "
                "AT TIME ZONE 'UTC'"
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
