import asyncio
from uuid import uuid4

import pytest

from jamii_ia.base_donnees import migrer
from jamii_ia.taches import CleReutiliseeError, DepotTaches, LimiteAtteinteError, empreinte

ENTREE = {"source": "demande_pme", "titre": "Boutique en ligne", "langue": "fr"}


async def test_deposer_cree_une_tache_en_attente(depot):
    tache, creee = await depot.deposer(uuid4(), "plan_projet", ENTREE)
    assert creee
    assert tache["statut"] == "en_attente"
    assert tache["entree"] == ENTREE
    assert tache["essais"] == 0
    assert tache["resultat"] is None


async def test_lire_renvoie_la_tache_ou_rien(depot):
    tache, _ = await depot.deposer(uuid4(), "plan_projet", ENTREE)
    assert await depot.lire(tache["id"]) == tache
    assert await depot.lire(uuid4()) is None


async def test_rejouer_la_meme_cle_ne_cree_pas_de_doublon(depot):
    cle = uuid4()
    premiere, _ = await depot.deposer(cle, "plan_projet", ENTREE)
    seconde, creee = await depot.deposer(cle, "plan_projet", ENTREE)
    assert not creee
    assert seconde["id"] == premiere["id"]


async def test_meme_cle_pour_une_autre_entree_est_refusee(depot):
    cle = uuid4()
    await depot.deposer(cle, "plan_projet", ENTREE)
    with pytest.raises(CleReutiliseeError):
        await depot.deposer(cle, "plan_projet", {**ENTREE, "titre": "Un tout autre projet"})


async def test_la_meme_cle_au_meme_instant_ne_cree_qu_une_tache(depot):
    cle = uuid4()
    resultats = await asyncio.gather(*(depot.deposer(cle, "plan_projet", ENTREE) for _ in range(8)))
    assert len({tache["id"] for tache, _ in resultats}) == 1
    assert sum(creee for _, creee in resultats) == 1


async def test_la_limite_par_heure_bloque_les_nouvelles_taches(depot):
    strict = DepotTaches(depot._pool, limite_par_heure=2)
    cle = uuid4()
    await strict.deposer(cle, "plan_projet", ENTREE)
    await strict.deposer(uuid4(), "plan_projet", ENTREE)
    with pytest.raises(LimiteAtteinteError):
        await strict.deposer(uuid4(), "plan_projet", ENTREE)
    # Rejouer une clé déjà acceptée reste possible : ce n'est pas une nouvelle tâche.
    assert (await strict.deposer(cle, "plan_projet", ENTREE))[1] is False


async def test_base_joignable_puis_injoignable(depot):
    assert await depot.base_joignable()
    await depot._pool.close()
    assert not await depot.base_joignable()


async def test_les_migrations_ne_sont_jouees_qu_une_fois(url_base):
    assert await migrer(url_base) == []


def test_l_empreinte_ne_depend_pas_de_l_ordre_des_cles():
    assert empreinte("plan_projet", {"a": 1, "b": 2}) == empreinte("plan_projet", {"b": 2, "a": 1})
    assert empreinte("plan_projet", {"a": 1}) != empreinte("plan_projet", {"a": 2})
