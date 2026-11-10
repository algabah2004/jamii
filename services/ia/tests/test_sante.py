import pytest
from conftest import reglages_de_test
from fastapi.testclient import TestClient

from jamii_ia.config import Reglages
from jamii_ia.fournisseurs import FournisseurInconnuError
from jamii_ia.main import ConfigurationError, creer_app

SANS_BASE = "postgresql://personne:rien@127.0.0.1:1/absente?connect_timeout=1"


def test_liveness_repond_sans_base():
    # Pas de bloc `with` : l'appli n'est pas démarrée, la base n'est jamais contactée.
    client = TestClient(creer_app(reglages_de_test(SANS_BASE)))
    reponse = client.get("/health/liveness")
    assert reponse.status_code == 200
    assert reponse.json() == {"status": "UP"}


def test_readiness_indique_le_fournisseur(client):
    reponse = client.get("/health/readiness")
    assert reponse.status_code == 200
    assert reponse.json() == {"status": "UP", "fournisseur": "simule"}


def test_readiness_repond_503_si_la_base_tombe(client):
    client.app.state.depot._pool.close()
    reponse = client.get("/health/readiness")
    assert reponse.status_code == 503
    assert reponse.json()["status"] == "DOWN"


def test_fournisseur_inconnu_bloque_le_demarrage(url_base):
    with pytest.raises(FournisseurInconnuError, match="simule"):
        creer_app(reglages_de_test(url_base, IA_PROVIDER="inexistant"))


@pytest.mark.parametrize("jeton", ["", "trop-court"])
def test_jeton_interne_absent_ou_faible_bloque_le_demarrage(url_base, jeton):
    with pytest.raises(ConfigurationError, match="IA_JETON_INTERNE"):
        creer_app(reglages_de_test(url_base, IA_JETON_INTERNE=jeton))


def test_base_non_configuree_bloque_le_demarrage():
    with pytest.raises(ConfigurationError, match="DATABASE_URL"):
        creer_app(Reglages(IA_PROVIDER="simule", IA_JETON_INTERNE="x" * 16, DATABASE_URL=""))


def test_la_documentation_interactive_n_est_pas_exposee(client):
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
