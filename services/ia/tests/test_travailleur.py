import asyncio
import time
from uuid import uuid4

from conftest import reglages_de_test, vider_la_file
from fastapi.testclient import TestClient

from jamii_ia.fournisseurs.simule import FournisseurSimule
from jamii_ia.main import creer_app
from jamii_ia.modeles import PlanProjet
from jamii_ia.travailleur import ESSAIS_MAX, Travailleur

AMINA, KOFI = str(uuid4()), str(uuid4())
ENTREE = {
    "source": "demande_pme",
    "titre": "Site de commande pour un atelier de couture",
    "description": "Une couturière de Thiès veut un site pour prendre ses commandes en ligne.",
    "langue": "fr",
    "membres": [
        {"membreId": AMINA, "competences": ["backend", "devops"]},
        {"membreId": KOFI, "competences": ["frontend"]},
    ],
}


async def _deposer(depot, entree=ENTREE):
    tache, _ = await depot.deposer(uuid4(), "plan_projet", entree)
    return tache["id"]


async def _rendre_disponible(depot, identifiant):
    await depot._executer("UPDATE ia.taches SET disponible_le = now() WHERE id = $1", identifiant)


async def test_une_demande_de_pme_devient_un_plan(depot):
    identifiant = await _deposer(depot)
    assert await Travailleur(depot, FournisseurSimule()).traiter_une()

    tache = await depot.lire(identifiant)
    assert (tache["statut"], tache["origine"]) == ("terminee", "ia")
    plan = PlanProjet.model_validate(tache["resultat"])
    assert len(plan.jalons) == 3
    suggeres = {str(t.membreSuggere) for t in plan.taches if t.membreSuggere}
    assert suggeres == {AMINA, KOFI}
    assert {r.role for r in plan.rolesManquants} == {"produit", "design", "test"}
    assert {s.service for s in plan.servicesSuggeres} == {"datacloud", "orion"}


async def test_file_vide(depot):
    assert not await Travailleur(depot, FournisseurSimule()).traiter_une()


async def test_si_l_ia_tombe_la_tache_est_reessayee_puis_repliee(depot):
    identifiant = await _deposer(depot)
    travailleur = Travailleur(depot, FournisseurSimule(en_panne=True))
    for essai in range(1, ESSAIS_MAX + 1):
        assert await travailleur.traiter_une()
        tache = await depot.lire(identifiant)
        if essai < ESSAIS_MAX:
            assert tache["statut"] == "en_attente"
            assert not await travailleur.traiter_une()  # pas avant le délai
            await _rendre_disponible(depot, identifiant)

    assert (tache["statut"], tache["origine"]) == ("terminee", "repli")
    assert PlanProjet.model_validate(tache["resultat"]).taches == []


async def test_une_reponse_non_conforme_finit_en_echec(depot):
    identifiant = await _deposer(depot)
    travailleur = Travailleur(depot, FournisseurSimule(repondre=lambda _: "Voici votre plan !"))
    for _ in range(ESSAIS_MAX):
        await travailleur.traiter_une()
        await _rendre_disponible(depot, identifiant)

    tache = await depot.lire(identifiant)
    assert (tache["statut"], tache["erreur_code"]) == ("echouee", "resultat_invalide")
    assert tache["resultat"] is None


async def test_un_membre_invente_par_l_ia_est_refuse(depot):
    faux = FournisseurSimule().generer

    async def avec_intrus(demande):
        reponse = await faux(demande)
        return type(reponse)(texte=reponse.texte.replace(AMINA, str(uuid4())), fournisseur="simule")

    identifiant = await _deposer(depot)
    intrus = FournisseurSimule()
    intrus.generer = avec_intrus
    await Travailleur(depot, intrus).traiter_une()
    assert (await depot.lire(identifiant))["statut"] == "en_attente"


async def test_la_meme_entree_est_servie_depuis_le_cache(depot):
    await _deposer(depot)
    seconde = await _deposer(depot)
    await Travailleur(depot, FournisseurSimule()).traiter_une()
    # Même en panne, la deuxième tâche aboutit : l'IA n'est pas rappelée.
    await Travailleur(depot, FournisseurSimule(en_panne=True)).traiter_une()
    assert (await depot.lire(seconde))["origine"] == "cache"


async def test_deux_travailleurs_ne_prennent_jamais_la_meme_tache(depot):
    for numero in range(6):
        await _deposer(depot, {**ENTREE, "titre": f"Projet numéro {numero}"})
    prises = await asyncio.gather(*(depot.prendre_suivante() for _ in range(6)))
    assert len({tache["id"] for tache in prises}) == 6


async def test_une_tache_qui_plante_toujours_finit_en_echec(depot):
    """Erreur inattendue à chaque fois : la tâche ne tourne pas sans fin (revue d'Alga, #37)."""
    identifiant = await _deposer(depot)
    for _ in range(ESSAIS_MAX):
        assert await depot.prendre_suivante() is not None  # le travailleur « meurt » ensuite
        await _rendre_disponible(depot, identifiant)
    assert await depot.prendre_suivante() is None
    tache = await depot.lire(identifiant)
    assert (tache["statut"], tache["erreur_code"]) == ("echouee", "delai_depasse")


async def test_une_ecriture_tardive_d_un_ancien_travailleur_est_ignoree(depot):
    """Un appel IA trop long : un 2e travailleur reprend la tâche, le 1er ne l'écrase pas."""
    identifiant = await _deposer(depot)
    premier = await depot.prendre_suivante()
    await _rendre_disponible(depot, identifiant)
    second = await depot.prendre_suivante()
    assert second["essais"] == premier["essais"] + 1

    assert not await depot.terminer(premier, {"vieux": True}, "ia")
    assert await depot.terminer(second, {"neuf": True}, "ia")
    assert (await depot.lire(identifiant))["resultat"] == {"neuf": True}


def test_de_bout_en_bout_par_http(url_base, jeton):
    vider_la_file(url_base)
    app = creer_app(reglages_de_test(url_base, IA_TRAVAILLEUR="true"))
    with TestClient(app) as client:
        corps = {"type": "plan_projet", "entree": ENTREE}
        entetes = jeton | {"Idempotency-Key": str(uuid4())}
        adresse = client.post("/taches", json=corps, headers=entetes).headers["Location"]
        fin = time.monotonic() + 10
        while (tache := client.get(adresse, headers=jeton).json())["statut"] != "terminee":
            assert time.monotonic() < fin, tache
            time.sleep(0.2)
    assert tache["origine"] == "ia"
    assert tache["resultat"]["jalons"][2]["titre"] == "Mise en ligne"
