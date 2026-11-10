"""Connexion à PostgreSQL et migrations du schéma « ia ».

Pilote : asyncpg (licence Apache 2.0). Le règlement de la CADEV interdit les composants
sous licence GPL, AGPL ou LGPL.
"""

import json
from importlib import resources

import asyncpg

# Identifiant arbitraire du verrou PostgreSQL : deux instances qui démarrent en même temps
# ne jouent pas les migrations deux fois.
_VERROU_MIGRATIONS = 7420_2611_09


async def migrer(url: str) -> list[str]:
    """Applique les fichiers `migrations/*.sql` pas encore joués. Renvoie leurs noms.

    Tout se passe dans une seule transaction : une migration qui échoue ne laisse rien à moitié.
    """
    dossier = resources.files("jamii_ia") / "migrations"
    fichiers = sorted(
        (f for f in dossier.iterdir() if f.name.endswith(".sql")), key=lambda f: f.name
    )
    appliquees = []
    connexion = await asyncpg.connect(url)
    try:
        async with connexion.transaction():
            await connexion.execute("SELECT pg_advisory_xact_lock($1)", _VERROU_MIGRATIONS)
            await connexion.execute("CREATE SCHEMA IF NOT EXISTS ia")
            await connexion.execute(
                "CREATE TABLE IF NOT EXISTS ia.migrations ("
                "nom text PRIMARY KEY, appliquee_le timestamptz NOT NULL DEFAULT now())"
            )
            lignes = await connexion.fetch("SELECT nom FROM ia.migrations")
            deja = {ligne["nom"] for ligne in lignes}
            for fichier in fichiers:
                if fichier.name in deja:
                    continue
                await connexion.execute(fichier.read_text(encoding="utf-8"))
                await connexion.execute("INSERT INTO ia.migrations (nom) VALUES ($1)", fichier.name)
                appliquees.append(fichier.name)
    finally:
        await connexion.close()
    return appliquees


async def _preparer(connexion: asyncpg.Connection) -> None:
    # Les colonnes jsonb entrent et sortent comme des dictionnaires Python.
    await connexion.set_type_codec(
        "jsonb", encoder=json.dumps, decoder=json.loads, schema="pg_catalog"
    )


async def ouvrir_pool(url: str) -> asyncpg.Pool:
    # Délais courts : si la base tombe, la sonde de santé répond 503 vite au lieu d'attendre.
    return await asyncpg.create_pool(
        url, min_size=1, max_size=5, init=_preparer, timeout=5, command_timeout=5
    )
