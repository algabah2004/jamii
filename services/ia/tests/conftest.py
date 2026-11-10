"""Les tests tournent sur un vrai PostgreSQL, dans une base à part : `jamii_ia_test`.

En local : `docker compose up -d postgres` à la racine du dépôt.
En CI : le service `postgres` du job.
"""

import os
from collections.abc import Iterator

import psycopg
import pytest
from fastapi.testclient import TestClient
from psycopg.conninfo import make_conninfo

from jamii_ia.base_donnees import migrer, ouvrir_pool
from jamii_ia.config import Reglages
from jamii_ia.main import creer_app
from jamii_ia.taches import DepotTaches

BASE_DE_TEST = "jamii_ia_test"
JETON = "jeton-de-test-0123456789"
URL_DEV = "postgresql://jamii:jamii_dev_only@localhost:5432/jamii"


@pytest.fixture(scope="session")
def url_base() -> str:
    url = os.environ.get("DATABASE_URL", URL_DEV)
    try:
        with psycopg.connect(url, autocommit=True, connect_timeout=3) as connexion:
            existe = connexion.execute(
                "SELECT 1 FROM pg_database WHERE datname = %s", (BASE_DE_TEST,)
            ).fetchone()
            if not existe:
                connexion.execute(f"CREATE DATABASE {BASE_DE_TEST}")
    except psycopg.OperationalError as erreur:
        if os.environ.get("CI"):
            raise  # En CI, une base absente est une erreur, pas un test ignoré.
        pytest.skip(f"PostgreSQL injoignable (docker compose up -d postgres) : {erreur}")
    url_test = make_conninfo(url, dbname=BASE_DE_TEST)
    migrer(url_test)
    return url_test


def vider_la_file(url_base: str) -> None:
    with psycopg.connect(url_base, autocommit=True) as connexion:
        connexion.execute("TRUNCATE ia.taches")


@pytest.fixture
def depot(url_base: str) -> Iterator[DepotTaches]:
    vider_la_file(url_base)
    pool = ouvrir_pool(url_base)
    yield DepotTaches(pool, limite_par_heure=100)
    pool.close()


def reglages_de_test(url_base: str, **surcharges: object) -> Reglages:
    valeurs = {
        "IA_PROVIDER": "simule",
        "IA_JETON_INTERNE": JETON,
        "DATABASE_URL": url_base,
    } | surcharges
    return Reglages(**valeurs)


@pytest.fixture
def client(url_base: str) -> Iterator[TestClient]:
    vider_la_file(url_base)
    # Le bloc `with` lance le démarrage de l'appli : migrations, puis ouverture du pool.
    with TestClient(creer_app(reglages_de_test(url_base))) as client:
        yield client


@pytest.fixture
def jeton() -> dict[str, str]:
    return {"Authorization": f"Bearer {JETON}"}
