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
