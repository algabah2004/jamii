"""Connexion à PostgreSQL et migrations du schéma « ia »."""

from importlib import resources

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

# Identifiant arbitraire du verrou PostgreSQL : deux instances qui démarrent en même temps
# ne jouent pas les migrations deux fois.
_VERROU_MIGRATIONS = 7420_2611_09


def migrer(url: str) -> list[str]:
    """Applique les fichiers `migrations/*.sql` pas encore joués. Renvoie leurs noms.

    Tout se passe dans une seule transaction : une migration qui échoue ne laisse rien à moitié.
    """
    fichiers = sorted(
        (
            f
            for f in (resources.files("jamii_ia") / "migrations").iterdir()
            if f.name.endswith(".sql")
        ),
        key=lambda f: f.name,
    )
    appliquees = []
    with psycopg.connect(url) as connexion:
        connexion.execute("SELECT pg_advisory_xact_lock(%s)", (_VERROU_MIGRATIONS,))
        connexion.execute("CREATE SCHEMA IF NOT EXISTS ia")
        connexion.execute(
            "CREATE TABLE IF NOT EXISTS ia.migrations ("
            "nom text PRIMARY KEY, appliquee_le timestamptz NOT NULL DEFAULT now())"
        )
        deja = {ligne[0] for ligne in connexion.execute("SELECT nom FROM ia.migrations")}
        for fichier in fichiers:
            if fichier.name in deja:
                continue
            connexion.execute(fichier.read_text(encoding="utf-8"))
            connexion.execute("INSERT INTO ia.migrations (nom) VALUES (%s)", (fichier.name,))
            appliquees.append(fichier.name)
    return appliquees


def ouvrir_pool(url: str) -> ConnectionPool:
    # timeout court : si la base tombe, la sonde de santé répond 503 vite au lieu d'attendre.
    return ConnectionPool(
        url, min_size=1, max_size=5, timeout=5, kwargs={"row_factory": dict_row}, open=True
    )
