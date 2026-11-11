import pytest
from fastapi.testclient import TestClient

from jamii_ia.config import Reglages
from jamii_ia.fournisseurs import FournisseurInconnuError
from jamii_ia.main import creer_app


def _client() -> TestClient:
    return TestClient(creer_app(Reglages(IA_PROVIDER="simule")))


def test_liveness_repond_sans_dependance():
    reponse = _client().get("/health/liveness")
    assert reponse.status_code == 200
    assert reponse.json() == {"status": "UP"}


def test_readiness_indique_le_fournisseur():
    assert _client().get("/health/readiness").json() == {"status": "UP", "fournisseur": "simule"}


def test_fournisseur_inconnu_bloque_le_demarrage():
    with pytest.raises(FournisseurInconnuError, match="simule"):
        creer_app(Reglages(IA_PROVIDER="inexistant"))


def test_la_documentation_interactive_n_est_pas_exposee():
    client = _client()
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
