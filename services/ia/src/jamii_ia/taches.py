"""La file de tâches : dépôt et lecture. Seul endroit du service qui écrit du SQL sur ia.taches."""

import hashlib
import json
from typing import Any
from uuid import UUID, uuid4

import psycopg
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool, PoolTimeout


class CleReutiliseeError(Exception):
    """La clé d'idempotence a déjà servi pour une entrée différente."""


class LimiteAtteinteError(Exception):
    """Trop de tâches déposées dans la dernière heure (dernier rempart, voir contracts/ia.yaml)."""


def empreinte(type_tache: str, entree: dict[str, Any]) -> str:
    # Clés triées : deux entrées identiques donnent la même empreinte, quel que soit l'ordre.
    texte = json.dumps([type_tache, entree], sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(texte.encode()).hexdigest()


class DepotTaches:
    def __init__(self, pool: ConnectionPool, limite_par_heure: int) -> None:
        self._pool = pool
        self._limite_par_heure = limite_par_heure

    def deposer(self, cle: UUID, type_tache: str, entree: dict[str, Any]) -> tuple[dict, bool]:
        """Renvoie (tâche, créée). `créée` est faux si la clé avait déjà servi pour cette entrée."""
        marque = empreinte(type_tache, entree)
        with self._pool.connection() as connexion:
            existante = self._par_cle(connexion, cle)
            if existante is None:
                self._verifier_limite(connexion)
                creee = connexion.execute(
                    "INSERT INTO ia.taches (id, cle_idempotence, type, entree, empreinte) "
                    "VALUES (%s, %s, %s, %s, %s) "
                    "ON CONFLICT (cle_idempotence) DO NOTHING RETURNING *",
                    (uuid4(), cle, type_tache, Jsonb(entree), marque),
                ).fetchone()
                if creee is not None:
                    return creee, True
                # Deux requêtes avec la même clé au même instant : l'autre a gagné.
                existante = self._par_cle(connexion, cle)
            if existante["empreinte"] != marque:
                raise CleReutiliseeError
            return existante, False

    def lire(self, tache_id: UUID) -> dict | None:
        with self._pool.connection() as connexion:
            return connexion.execute(
                "SELECT * FROM ia.taches WHERE id = %s", (tache_id,)
            ).fetchone()

    def base_joignable(self) -> bool:
        try:
            with self._pool.connection() as connexion:
                connexion.execute("SELECT 1")
            return True
        except (psycopg.Error, PoolTimeout):
            return False

    @staticmethod
    def _par_cle(connexion: psycopg.Connection, cle: UUID) -> dict | None:
        return connexion.execute(
            "SELECT * FROM ia.taches WHERE cle_idempotence = %s", (cle,)
        ).fetchone()

    def _verifier_limite(self, connexion: psycopg.Connection) -> None:
        # Compte approximatif sous forte concurrence : suffisant pour un dernier rempart,
        # la limite précise par utilisateur est faite par l'API.
        ligne = connexion.execute(
            "SELECT count(*) AS n FROM ia.taches WHERE cree_le > now() - interval '1 hour'"
        ).fetchone()
        if ligne["n"] >= self._limite_par_heure:
            raise LimiteAtteinteError
