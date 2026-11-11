from jamii_ia.config import Reglages


def test_port_par_defaut(monkeypatch):
    monkeypatch.delenv("PORT", raising=False)
    monkeypatch.delenv("IA_PORT", raising=False)
    assert Reglages().port == 8000


def test_port_de_l_hebergeur_l_emporte(monkeypatch):
    monkeypatch.setenv("IA_PORT", "8001")
    monkeypatch.setenv("PORT", "9000")
    assert Reglages().port == 9000


def test_simule_par_defaut(monkeypatch):
    monkeypatch.delenv("IA_PROVIDER", raising=False)
    assert Reglages().fournisseur == "simule"
