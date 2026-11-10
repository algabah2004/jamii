"""Les tests tournent sur un vrai PostgreSQL, dans une base à part : `jamii_ia_test`.

En local : `docker compose up -d postgres` à la racine du dépôt.
En CI : le service `postgres` du job.
"""

import asyncio
import os
from collections.abc import AsyncIterator
from urllib.parse import urlsplit, urlunsplit

import asyncpg
import pytest

from jamii_ia.base_donnees import migrer, ouvrir_pool
from jamii_ia.taches import DepotTaches

BASE_DE_TEST = "jamii_ia_test"
URL_DEV = "postgresql://jamii:jamii_dev_only@localhost:5432/jamii"


async def _creer_base_de_test(url: str) -> None:
    connexion = await asyncpg.connect(url, timeout=3)
    try:
        existe = await connexion.fetchval(
            "SELECT 1 FROM pg_database WHERE datname = $1", BASE_DE_TEST
        )
        if not existe:
            await connexion.execute(f"CREATE DATABASE {BASE_DE_TEST}")
    finally:
        await connexion.close()


@pytest.fixture(scope="session")
def url_base() -> str:
    url = os.environ.get("DATABASE_URL", URL_DEV)
    try:
        asyncio.run(_creer_base_de_test(url))
    except (OSError, TimeoutError, asyncpg.PostgresError) as erreur:
        if os.environ.get("CI"):
            raise  # En CI, une base absente est une erreur, pas un test ignoré.
        pytest.skip(f"PostgreSQL injoignable (docker compose up -d postgres) : {erreur}")
    url_test = urlunsplit(urlsplit(url)._replace(path=f"/{BASE_DE_TEST}"))
    asyncio.run(migrer(url_test))
    return url_test


async def _vider(url_base: str) -> None:
    connexion = await asyncpg.connect(url_base)
    try:
        await connexion.execute("TRUNCATE ia.taches")
    finally:
        await connexion.close()


def vider_la_file(url_base: str) -> None:
    asyncio.run(_vider(url_base))


@pytest.fixture
async def depot(url_base: str) -> AsyncIterator[DepotTaches]:
    await _vider(url_base)
    pool = await ouvrir_pool(url_base)
    yield DepotTaches(pool, limite_par_heure=100)
    await pool.close()
