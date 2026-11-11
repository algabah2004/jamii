import pytest

from jamii_ia.fournisseurs import DemandeIA, FournisseurIndisponibleError, creer_fournisseur
from jamii_ia.fournisseurs.simule import FournisseurSimule

DEMANDE = DemandeIA(consigne="Résume.", donnees="Une boutique veut vendre en ligne.")


async def test_simule_repond_toujours_la_meme_chose():
    fournisseur = creer_fournisseur("simule")
    premiere = await fournisseur.generer(DEMANDE)
    seconde = await fournisseur.generer(DEMANDE)
    assert premiere == seconde
    assert premiere.fournisseur == "simule"


async def test_simule_accepte_une_reponse_sur_mesure():
    fournisseur = FournisseurSimule(repondre=lambda d: d.donnees.upper())
    assert (await fournisseur.generer(DEMANDE)).texte == "UNE BOUTIQUE VEUT VENDRE EN LIGNE."


async def test_simule_peut_jouer_la_panne():
    with pytest.raises(FournisseurIndisponibleError):
        await FournisseurSimule(en_panne=True).generer(DEMANDE)
