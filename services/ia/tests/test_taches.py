from uuid import uuid4

from conftest import reglages_de_test, vider_la_file
from fastapi.testclient import TestClient

from jamii_ia.main import creer_app

DEMANDE = {
    "type": "plan_projet",
    "entree": {
        "source": "demande_pme",
        "titre": "Boutique en ligne pour un atelier de couture",
        "description": "Une couturière de Thiès veut prendre ses commandes et mesures en ligne.",
    },
}


def deposer(client, jeton, corps=DEMANDE, cle=None):
    entetes = jeton | {"Idempotency-Key": str(cle or uuid4())}
    return client.post("/taches", json=corps, headers=entetes)


def test_deposer_met_la_tache_en_file(client, jeton):
    reponse = deposer(client, jeton)
    assert reponse.status_code == 202
    tache = reponse.json()
    assert tache["statut"] == "en_attente"
    assert tache["type"] == "plan_projet"
    assert reponse.headers["Location"] == f"/taches/{tache['id']}"
    # Un champ sans valeur est absent, pas `null` (docs/api-conventions.md).
    assert "resultat" not in tache


def test_lire_renvoie_la_tache_deposee(client, jeton):
    tache = deposer(client, jeton).json()
    reponse = client.get(f"/taches/{tache['id']}", headers=jeton)
    assert reponse.status_code == 200
    assert reponse.json() == tache


def test_rejouer_la_meme_cle_ne_cree_pas_de_doublon(client, jeton):
    cle = uuid4()
    premiere = deposer(client, jeton, cle=cle)
    seconde = deposer(client, jeton, cle=cle)
    assert seconde.status_code == 200
    assert seconde.json()["id"] == premiere.json()["id"]


def test_meme_cle_pour_une_autre_entree_est_un_conflit(client, jeton):
    cle = uuid4()
    deposer(client, jeton, cle=cle)
    autre = {**DEMANDE, "entree": {**DEMANDE["entree"], "titre": "Un tout autre projet"}}
    reponse = deposer(client, jeton, corps=autre, cle=cle)
    assert reponse.status_code == 409
    assert reponse.json()["code"] == "cle_reutilisee"


def test_sans_jeton_ou_avec_un_mauvais_jeton_c_est_401(client):
    for entetes in ({}, {"Authorization": "Bearer mauvais-jeton-0123456789"}):
        reponse = deposer(client, entetes)
        assert reponse.status_code == 401
        assert reponse.json()["code"] == "non_connecte"
        assert reponse.headers["content-type"] == "application/problem+json"
    assert client.get(f"/taches/{uuid4()}").status_code == 401


def test_entree_invalide_liste_les_champs_sans_renvoyer_leur_valeur(client, jeton):
    corps = {"type": "plan_projet", "entree": {"source": "idee", "titre": "ab", "secret": "x"}}
    reponse = deposer(client, jeton, corps=corps)
    assert reponse.status_code == 400
    probleme = reponse.json()
    assert probleme["code"] == "validation"
    assert {"champ": "entree.titre", "code": "trop_court"} in probleme["erreurs"]
    assert {"champ": "entree.description", "code": "obligatoire"} in probleme["erreurs"]
    assert {"champ": "entree.secret", "code": "champ_inconnu"} in probleme["erreurs"]
    assert all(set(erreur) == {"champ", "code"} for erreur in probleme["erreurs"])


def test_cle_d_idempotence_obligatoire(client, jeton):
    reponse = client.post("/taches", json=DEMANDE, headers=jeton)
    assert reponse.status_code == 400
    assert {"champ": "Idempotency-Key", "code": "obligatoire"} in reponse.json()["erreurs"]


def test_tache_inconnue_c_est_404(client, jeton):
    reponse = client.get(f"/taches/{uuid4()}", headers=jeton)
    assert reponse.status_code == 404
    assert reponse.json()["code"] == "introuvable"


def test_la_limite_globale_repond_429(url_base, jeton):
    with TestClient(creer_app(reglages_de_test(url_base, IA_LIMITE_PAR_HEURE=2))) as client:
        vider_la_file(url_base)
        assert deposer(client, jeton).status_code == 202
        assert deposer(client, jeton).status_code == 202
        reponse = deposer(client, jeton)
        assert reponse.status_code == 429
        assert reponse.json()["code"] == "trop_de_requetes"
        assert reponse.headers["Retry-After"] == "60"
