<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JSquelette } from '../../design-system'
import RechercheExploration from '../../components/exploration/RechercheExploration.vue'
import { correspond } from '../../components/exploration/recherche'
import '../../components/exploration/exploration.css'
import { estHttps, formaterDate, lireResultats, listerEquipes, listerRendus, type Hackathon, type Rendu } from './hackathons'

const props = defineProps<{ hackathon: Hackathon }>()
const { t, locale } = useI18n()
const projets = ref<{ rendu: Rendu; equipe: string }[]>([])
const recherche = ref('')
const etat = ref<'chargement' | 'pret' | 'ferme' | 'indisponible'>('chargement')
const visibles = computed(() => projets.value.filter((p) => correspond(recherche.value, [p.rendu.titre, p.rendu.description, p.equipe])))
let derniereLecture = 0

/** Les rendus sont privés avant publication : seul le jury ou l’organisateur utilise leur API. */
async function charger() {
  const lecture = ++derniereLecture
  const h = props.hackathon
  recherche.value = ''
  etat.value = 'chargement'
  try {
    if (h.statut === 'resultats') {
      const resultats = await lireResultats(h.id)
      if (lecture !== derniereLecture) return
      if (!resultats) throw new Error('resultats indisponibles')
      projets.value = resultats.classement.map((p) => ({ rendu: p.rendu, equipe: p.equipe.nom }))
    } else if (h.moi.role === 'organisateur' || (h.moi.role === 'jure' && h.moi.actions.includes('noter'))) {
      const [rendus, equipes] = await Promise.all([listerRendus(h.id), listerEquipes(h.id)])
      if (lecture !== derniereLecture) return
      const noms = new Map(equipes.map((e) => [e.id, e.nom]))
      projets.value = rendus.map((rendu) => ({ rendu, equipe: noms.get(rendu.equipeId) ?? t('hackathons.galerie.equipe') }))
    } else {
      etat.value = 'ferme'
      return
    }
    etat.value = 'pret'
  } catch {
    if (lecture === derniereLecture) etat.value = 'indisponible'
  }
}
watch(() => [props.hackathon.id, props.hackathon.statut, props.hackathon.moi.role], () => void charger(), { immediate: true })
onBeforeUnmount(() => derniereLecture++)
</script>

<template>
  <section class="galerie carte" aria-labelledby="galerie-titre" data-test="galerie-hackathon">
    <header class="galerie__tete"><div><p class="galerie__surtitre">{{ t('hackathons.galerie.surtitre') }}</p><h2 id="galerie-titre">{{ t('hackathons.galerie.titre') }}</h2></div><span v-if="etat === 'pret'" class="galerie__compteur" aria-live="polite">{{ t('hackathons.galerie.nombre', { n: visibles.length }, visibles.length) }}</span></header>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'ferme'" class="galerie__vide" data-test="galerie-privee"><span aria-hidden="true">◇</span><h3>{{ t('hackathons.galerie.attenteTitre') }}</h3><p>{{ t('hackathons.galerie.attente') }}</p><p class="texte-discret">{{ t('hackathons.galerie.resultatsLe', { date: formaterDate(hackathon.calendrier.resultatsLe, locale) }) }}</p></div>
    <div v-else-if="etat === 'indisponible'" role="alert"><p>{{ t('hackathons.galerie.indisponible') }}</p><JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton></div>
    <template v-else>
      <p class="texte-discret">{{ t(hackathon.statut === 'resultats' ? 'hackathons.galerie.porteePublique' : hackathon.moi.role === 'jure' ? 'hackathons.galerie.porteeJury' : 'hackathons.galerie.porteeOrganisateur') }}</p>
      <RechercheExploration v-model="recherche" :libelle="t('hackathons.galerie.recherche')" />
      <p v-if="!projets.length" class="galerie__vide" data-test="galerie-vide">{{ t('hackathons.galerie.aucun') }}</p>
      <p v-else-if="!visibles.length" class="galerie__vide">{{ t('exploration.aucunResultat') }}</p>
      <ul v-else class="galerie__grille">
        <li v-for="p in visibles" :key="p.rendu.id" class="galerie__projet" data-test="projet-hackathon">
          <div class="galerie__visuel" aria-hidden="true"><span>{{ p.rendu.titre.slice(0, 2).toLocaleUpperCase(locale) }}</span><i /><b>↗</b></div>
          <div class="galerie__corps"><p class="galerie__equipe">{{ p.equipe }}</p><h3>{{ p.rendu.titre }}</h3><p class="galerie__description">{{ p.rendu.description }}</p>
            <details><summary>{{ t('hackathons.galerie.details') }}</summary><p class="galerie__texte">{{ p.rendu.description }}</p><a v-if="p.rendu.depot && estHttps(p.rendu.depot)" :href="p.rendu.depot" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.galerie.code') }} ↗</a><a v-if="p.rendu.video && estHttps(p.rendu.video)" :href="p.rendu.video" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.galerie.video') }} ↗</a></details>
            <a v-if="estHttps(p.rendu.lienProduit)" :href="p.rendu.lienProduit" target="_blank" rel="noopener noreferrer nofollow" class="galerie__lien">{{ t('hackathons.galerie.voir') }} <span aria-hidden="true">↗</span></a>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.galerie { display: grid; gap: 24px; padding: clamp(20px, 3vw, 36px); }
.galerie__tete { display: flex; flex-wrap: wrap; gap: 16px; justify-content: space-between; align-items: center; }
.galerie__surtitre { margin: 0 0 8px; color: var(--proof-ink); font-size: .8125rem; letter-spacing: .1em; text-transform: uppercase; font-weight: 700; }
.galerie h2 { margin: 0; font-size: 1.75rem; letter-spacing: -.035em; }
.galerie__compteur { padding: 7px 12px; border-radius: 50px; background: var(--action-soft); font-size: .875rem; }
.galerie__grille { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 270px), 1fr)); gap: 24px; list-style: none; padding: 0; margin: 0; }
.galerie__projet { display: flex; flex-direction: column; border: 1px solid var(--line-strong); border-radius: 20px; overflow: hidden; min-width: 0; }
.galerie__visuel { display: flex; align-items: center; gap: 20px; min-height: 130px; padding: 24px; background: linear-gradient(125deg, var(--action-soft), var(--proof-soft)); }
.galerie__visuel span { display: grid; place-items: center; width: 60px; height: 60px; background: var(--surface); color: var(--action-ink); border-radius: 18px; font: 700 1.5rem var(--font-display); transform: rotate(-7deg); }
.galerie__visuel i { flex: 1; height: 5px; border-radius: 5px; background: var(--proof); opacity: .5; }
.galerie__visuel b { font-size: 1.5rem; color: var(--proof-ink); }
.galerie__corps { display: flex; flex-direction: column; flex: 1; gap: 12px; padding: 24px; }
.galerie__equipe { color: var(--ink-muted); font-size: .875rem; margin: 0; }
.galerie h3 { font-size: 1.25rem; margin: 0; overflow-wrap: anywhere; }
.galerie__description { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; color: var(--ink-muted); margin: 0; }
.galerie summary { cursor: pointer; font-size: .875rem; color: var(--action-ink); font-weight: 600; }
.galerie details a { display: block; padding-block: 6px; }
.galerie__texte { white-space: pre-line; overflow-wrap: anywhere; margin-block: 16px; }
.galerie__lien { display: flex; justify-content: space-between; gap: 12px; border-top: 1px solid var(--line); padding-top: 16px; margin-top: auto; text-decoration: none; font-weight: 700; }
.galerie__vide { display: grid; justify-items: center; text-align: center; gap: 12px; padding: 40px 20px; border: 1px dashed var(--line-strong); border-radius: 20px; }
.galerie__vide > span { color: var(--proof-ink); font-size: 3rem; }
.galerie__vide h3, .galerie__vide p { margin: 0; max-width: 50ch; }
</style>
