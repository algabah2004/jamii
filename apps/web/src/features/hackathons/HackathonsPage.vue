<script setup lang="ts">
import MotifsCoins from '../../components/MotifsCoins.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { FILTRES, LIEUX, listerHackathons, type Filtre, type HackathonResume } from './hackathons'
import EnteteExploration from '../../components/exploration/EnteteExploration.vue'
import RechercheExploration from '../../components/exploration/RechercheExploration.vue'
import { correspond } from '../../components/exploration/recherche'

/*
 * Les hackathons publiés : à venir, en cours, en évaluation ou terminés. Lisible sans connexion ; en créer un demande
 * de se connecter.
 * Chaque hackathon est une carte : une bannière à sa couleur avec la date de début, puis le défi, les dates et les inscrits.
 */
const { t, locale } = useI18n()
const { etat: session } = useSession()

const filtre = ref<Filtre>('tous')
const hackathons = ref<HackathonResume[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const plus = ref(false)
// Changer de statut recharge la liste : on garde l'ancienne, atténuée, plutôt que de la remplacer par un squelette.
const rafraichit = ref(false)
// L'en-tête (hackathon à la une, chiffres) reflète tout le catalogue : il ne change pas, donc ne bouge pas, quand on filtre.
const ensemble = ref<HackathonResume[]>([])
const connecte = computed(() => session.value === 'connecte')
const recherche = ref('')
const lieux = ref<(typeof LIEUX)[number][]>([])
const duree = ref('tous')
const tri = ref('debut')
const vedette = computed(() => ensemble.value.find((h) => h.statut === 'publie' || h.statut === 'en_cours') ?? ensemble.value[0])
const visibles = computed(() => hackathons.value.filter((h) => {
  const jours = Math.max(1, Math.ceil((Date.parse(h.finRendusLe) - Date.parse(h.debutLe)) / 86_400_000))
  return (!lieux.value.length || lieux.value.includes(h.lieu))
    && (duree.value === 'tous' || (duree.value === 'court' ? jours <= 3 : duree.value === 'semaine' ? jours > 3 && jours <= 7 : jours > 7))
    && correspond(recherche.value, [h.titre, h.theme, h.ville, h.organisateur.nom, t(`hackathons.lieux.${h.lieu}`)])
}).sort((a, b) => tri.value === 'participants' ? b.nombreInscrits - a.nombreInscrits : tri.value === 'titre' ? a.titre.localeCompare(b.titre, locale.value) : Date.parse(a.debutLe) - Date.parse(b.debutLe)))
const DUREES = ['tous', 'court', 'semaine', 'long'] as const
const nombreFiltres = computed(() => Number(filtre.value !== 'tous') + lieux.value.length + Number(duree.value !== 'tous'))
function reinitialiser() { filtre.value = 'tous'; lieux.value = []; duree.value = 'tous'; recherche.value = ''; tri.value = 'debut' }
const statistiques = computed(() => [
  { valeur: ensemble.value.length, libelle: t('exploration.hackathons.statDefis', ensemble.value.length) },
  { valeur: ensemble.value.reduce((total, h) => total + h.nombreInscrits, 0), libelle: t('exploration.hackathons.statInscrits', ensemble.value.reduce((total, h) => total + h.nombreInscrits, 0)) },
  { valeur: new Set(ensemble.value.map((h) => h.ville).filter(Boolean)).size, libelle: t('exploration.hackathons.statVilles', new Set(ensemble.value.map((h) => h.ville).filter(Boolean)).size) },
])

// Chaque chargement a son numéro : la réponse d'un ancien filtre, arrivée en retard, n'écrase pas la liste.
let dernier = 0
async function charger(curseur?: string) {
  const numero = ++dernier
  if (curseur) plus.value = true
  else if (etat.value === 'pret') rafraichit.value = true
  else etat.value = 'chargement'
  try {
    const page = await listerHackathons(filtre.value, curseur)
    if (numero !== dernier) return
    hackathons.value = curseur ? [...hackathons.value, ...page.elements] : page.elements
    suivant.value = page.curseurSuivant
    if (filtre.value === 'tous') ensemble.value = hackathons.value
    etat.value = 'pret'
  } catch {
    if (!curseur && numero === dernier) etat.value = 'indisponible'
  } finally {
    if (numero === dernier) { plus.value = false; rafraichit.value = false }
  }
}
watch(filtre, () => void charger(), { immediate: true })

// Filtrer quand on est descendu dans la liste : la liste raccourcit et le navigateur remonterait la page d'un coup,
// emportant le panneau. On ramène plutôt le haut des résultats sous la barre : le panneau ne bouge pas.
const catalogue = ref<HTMLElement | null>(null)
/** Téléphone : les filtres sont repliés au départ (sur ordinateur, ils restent toujours visibles). */
const filtresOuverts = ref(false)
watch([filtre, lieux, duree], () => {
  const boite = catalogue.value?.getBoundingClientRect()
  if (!boite?.height || boite.top >= 100) return
  window.scrollTo({ top: window.scrollY + boite.top - 100, behavior: 'instant' })
})

/** Une teinte tirée du titre : toujours la même couleur de bannière pour un hackathon. */
function teinte(titre: string): number {
  let h = 7
  for (const c of titre) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return h % 360
}
const jour = (iso: string) => new Intl.DateTimeFormat(locale.value, { day: 'numeric' }).format(new Date(iso))
const mois = (iso: string) => new Intl.DateTimeFormat(locale.value, { month: 'short' }).format(new Date(iso)).replace('.', '')
const periode = (debut: string, fin: string) =>
  new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'long', year: 'numeric' }).formatRange(new Date(debut), new Date(fin))
</script>

<template>
  <section class="avec-motifs hackathons exploration">
    <!-- Des taches de dessins sur les bords de la page, à son image. -->
    <MotifsCoins planche="hackathons" />
    <EnteteExploration :surtitre="t('exploration.hackathons.surtitre')" :titre="t('exploration.hackathons.titre')" :accent="t('exploration.hackathons.accent')" :intro="t('hackathons.intro')" :statistiques="statistiques">
      <template #actions><RouterLink v-if="connecte" to="/hackathons/nouveau" class="exploration__principal" data-test="creer-un-hackathon"><span aria-hidden="true">＋</span>{{ t('hackathons.creer') }}</RouterLink><a href="#hackathons-liste" class="exploration__secondaire">{{ t('exploration.hackathons.explorer') }} <span aria-hidden="true">↓</span></a></template>
      <p class="exploration__surtitre">{{ t('exploration.hackathons.vedette') }}</p>
      <template v-if="vedette">
        <div class="exploration__calendrier" aria-hidden="true"><strong>{{ jour(vedette.debutLe) }}</strong><span>{{ mois(vedette.debutLe) }}</span></div>
        <h2><RouterLink :to="`/hackathons/${vedette.id}`">{{ vedette.titre }}</RouterLink></h2>
        <p>{{ vedette.theme }}</p>
        <div class="exploration__etiquettes"><span>{{ t(`hackathons.statuts.${vedette.statut}`) }}</span><span>{{ [t(`hackathons.lieux.${vedette.lieu}`), vedette.ville].filter(Boolean).join(' · ') }}</span></div>
        <div class="exploration__pied-vedette"><div><small>{{ periode(vedette.debutLe, vedette.finRendusLe) }}</small><strong>{{ t('hackathons.inscrits', { n: vedette.nombreInscrits }, vedette.nombreInscrits) }}</strong></div><RouterLink :to="`/hackathons/${vedette.id}`" class="exploration__fleche" :aria-label="t('exploration.ouvrir', { titre: vedette.titre })">↗</RouterLink></div>
      </template>
      <template v-else><span class="exploration__icone" aria-hidden="true">⚑</span><h2>{{ t('exploration.hackathons.attenteTitre') }}</h2><p>{{ t('exploration.hackathons.attente') }}</p></template>
    </EnteteExploration>

    <div class="exploration__outils" id="hackathons-liste">
      <RechercheExploration v-model="recherche" :libelle="t('exploration.hackathons.recherche')" />
      <span v-if="suivant" class="texte-discret">{{ t('exploration.dansLaListe') }}</span>
    </div>

    <div ref="catalogue" class="catalogue">
      <aside class="catalogue__filtres" :aria-label="t('hackathons.decouverte.filtres')">
        <div class="catalogue__filtres-tete">
          <h2>{{ t('hackathons.decouverte.filtres') }}<span v-if="nombreFiltres" class="catalogue__nombre">{{ nombreFiltres }}</span></h2>
          <!-- Téléphone : les filtres se replient derrière ce bouton pour laisser la place aux hackathons. -->
          <button type="button" class="catalogue__deplier" :aria-expanded="filtresOuverts" aria-controls="filtres-hackathons" data-test="deplier-filtres" @click="filtresOuverts = !filtresOuverts">
            {{ t('hackathons.decouverte.filtres') }}<span v-if="nombreFiltres" class="catalogue__nombre">{{ nombreFiltres }}</span>
            <svg aria-hidden="true" viewBox="0 0 16 16"><path d="M4 6l4 4 4-4" /></svg>
          </button>
          <button type="button" class="catalogue__effacer" :class="{ 'catalogue__effacer--cache': !nombreFiltres && !recherche }" data-test="reinitialiser-filtres" @click="reinitialiser">{{ t('hackathons.decouverte.effacer') }}</button>
        </div>
        <div id="filtres-hackathons" class="catalogue__corps" :class="{ 'catalogue__corps--ouvert': filtresOuverts }">
        <fieldset class="catalogue__statuts">
          <legend>{{ t('hackathons.decouverte.statut') }}</legend>
          <button
            v-for="f in connecte ? [...FILTRES, 'miens' as const] : FILTRES"
            :key="f"
            type="button"
            :aria-pressed="filtre === f"
            :data-test="`filtre-${f}`"
            @click="filtre = f"
          >{{ t(`hackathons.filtres.${f}`) }}<svg aria-hidden="true" viewBox="0 0 16 16"><path d="M3.5 8.5l3 3 6-7" /></svg></button>
        </fieldset>
        <fieldset class="catalogue__pastilles">
          <legend>{{ t('hackathons.nouveau.lieu') }}</legend>
          <label v-for="lieu in LIEUX" :key="lieu"><input v-model="lieux" type="checkbox" :value="lieu" :data-test="`lieu-${lieu}`" />{{ t(`hackathons.lieux.${lieu}`) }}</label>
        </fieldset>
        <fieldset class="catalogue__pastilles">
          <legend>{{ t('hackathons.decouverte.duree') }}</legend>
          <label v-for="d in DUREES" :key="d"><input v-model="duree" type="radio" name="duree-hackathon" :value="d" :data-test="`duree-${d}`" />{{ t(`hackathons.decouverte.durees.${d}`) }}</label>
        </fieldset>
        </div>
      </aside>

      <div class="catalogue__contenu" :class="{ 'catalogue__contenu--maj': rafraichit }" :aria-busy="rafraichit">

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message" data-test="indisponible">{{ t('hackathons.indisponible') }}</p>
    <template v-else>
      <div class="exploration__resultats"><div><h2>{{ t('exploration.hackathons.resultats') }}</h2><p aria-live="polite">{{ t('exploration.resultats', { n: visibles.length }, visibles.length) }}</p></div><label class="catalogue__tri"><span>{{ t('hackathons.decouverte.trier') }}</span><select v-model="tri" data-test="tri-hackathons"><option value="debut">{{ t('hackathons.decouverte.tris.debut') }}</option><option value="participants">{{ t('hackathons.decouverte.tris.participants') }}</option><option value="titre">{{ t('hackathons.decouverte.tris.titre') }}</option></select></label></div>
      <p v-if="!hackathons.length" class="carte message" data-test="aucun">{{ filtre === 'miens' ? t('hackathons.aucunMiens') : t('hackathons.aucun') }}</p>
      <div v-else-if="!visibles.length" class="exploration__vide" data-test="sans-resultat"><h2>{{ t('exploration.aucunResultat') }}</h2><p>{{ t('exploration.essayerAutrement') }}</p><button type="button" class="exploration__effacer" @click="recherche = ''; lieux = []; duree = 'tous'">{{ t('exploration.reinitialiser') }}</button></div>
      <ul v-else class="liste" :class="{ 'liste--unique': visibles.length === 1 }" data-test="liste">
        <li v-for="h in visibles" :key="h.id" class="carte hackathon" :data-statut="h.statut" data-test="hackathon">
          <div class="hackathon__banniere" :style="{ '--teinte': teinte(h.titre) }" aria-hidden="true">
            <span class="hackathon__date">
              <span class="hackathon__jour">{{ jour(h.debutLe) }}</span>
              <span class="hackathon__mois">{{ mois(h.debutLe) }}</span>
            </span>
          </div>
          <div class="hackathon__corps">
            <p class="hackathon__haut">
              <span class="etiquette" :data-statut="h.statut">{{ t(`hackathons.statuts.${h.statut}`) }}</span>
              <span class="texte-discret">{{ [t(`hackathons.lieux.${h.lieu}`), h.ville].filter(Boolean).join(' · ') }}</span>
            </p>
            <h2 class="hackathon__titre"><RouterLink :to="`/hackathons/${h.id}`" class="lien-carte">{{ h.titre }}</RouterLink></h2>
            <p class="hackathon__theme">{{ h.theme }}</p>
            <p class="hackathon__periode">{{ periode(h.debutLe, h.finRendusLe) }}</p>
            <p class="hackathon__bas">
              <span class="personne"><JAvatar :nom="h.organisateur.nom" :url="h.organisateur.avatarUrl" :taille="24" /> {{ t('hackathons.par', { nom: h.organisateur.nom }) }}</span>
              <span class="compteur">{{ t('hackathons.inscrits', { n: h.nombreInscrits }, h.nombreInscrits) }}</span>
            </p>
          </div>
        </li>
      </ul>
      <div v-if="suivant" class="suite">
        <JBouton :occupe="plus" data-test="plus" @click="charger(suivant ?? undefined)">{{ t('hackathons.plus') }}</JBouton>
      </div>
    </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hackathons { display: grid; gap: var(--space-6); min-width: 0; }
.message { margin: 0; padding: var(--space-6); }

/* Des cartes en grille ; toute la carte mène au hackathon (le lien du titre s'étend sur elle). */
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
.hackathon { position: relative; display: grid; grid-template-rows: auto 1fr; padding: 0; overflow: hidden; transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease; }
.hackathon:hover { transform: translateY(-3px); box-shadow: 0 12px 28px color-mix(in srgb, #000 28%, transparent); border-color: color-mix(in srgb, var(--action) 50%, var(--line-strong)); }
.hackathon:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
.hackathon[data-statut='annule'] { opacity: 0.7; }
@media (prefers-reduced-motion: reduce) { .hackathon { transition: none; } .hackathon:hover { transform: none; } }

/* La bannière : un dégradé tiré du titre (toujours le même pour un hackathon), avec de fins traits. */
.hackathon__banniere {
  position: relative; block-size: 6.5rem; padding: var(--space-4);
  background:
    repeating-linear-gradient(-35deg, color-mix(in srgb, #fff 10%, transparent) 0 1px, transparent 1px 14px),
    linear-gradient(135deg, hsl(var(--teinte) 70% 42%), hsl(calc(var(--teinte) + 40) 65% 22%));
}
.hackathon__date {
  display: inline-grid; justify-items: center; min-inline-size: 3.5rem; padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md); background: var(--surface); color: var(--ink); line-height: 1.1;
  box-shadow: 0 4px 12px color-mix(in srgb, #000 25%, transparent);
}
.hackathon__jour { font-size: 1.5rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.hackathon__mois { font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }

.hackathon__corps { display: grid; gap: var(--space-3); padding: var(--space-6); align-content: start; }
.hackathon__corps p { margin: 0; }
.hackathon__haut { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.hackathon__titre { margin: 0; font-size: 1.25rem; line-height: 1.25; }
.lien-carte { color: inherit; text-decoration: none; }
.lien-carte::after { content: ''; position: absolute; inset: 0; z-index: 1; }
.lien-carte:focus-visible { outline: none; }
.hackathon:hover .lien-carte { color: var(--action-ink); }
.hackathon__theme { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.hackathon__periode { font-size: var(--type-body-sm-size); font-weight: 600; }
.hackathon__bas { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; padding-block-start: var(--space-3); border-block-start: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); }
.personne { display: inline-flex; gap: var(--space-2); align-items: center; }
.compteur { font-weight: 700; }
.etiquette { padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 700; }
.etiquette[data-statut='publie'], .etiquette[data-statut='en_cours'] { border-color: var(--action); }
.suite { display: flex; justify-content: center; }
.hackathons.exploration { gap: clamp(28px, 4vw, 48px); }
.hackathon { border-radius: 24px; }
.hackathon__banniere { block-size: 9rem; padding: 24px; }
.hackathon__titre { font-size: 1.5rem; line-height: 1.3; letter-spacing: -.025em; }
.catalogue { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 32px; align-items: start; }
.catalogue__filtres { position: sticky; top: 100px; display: grid; gap: 22px; border: 1px solid var(--line); border-radius: 22px; padding: 20px; background: var(--surface); }
.catalogue__filtres-tete { display: flex; justify-content: space-between; align-items: center; gap: 8px; min-height: 32px; }
.catalogue__filtres-tete h2 { display: flex; gap: 8px; align-items: center; margin: 0; font-size: 1.0625rem; }
.catalogue__nombre { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 7px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: .75rem; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; }
.catalogue__effacer { padding: 4px 8px; margin-inline-end: -8px; border: 0; border-radius: 8px; background: transparent; color: var(--ink-muted); font: inherit; font-size: .8125rem; font-weight: 600; cursor: pointer; transition: color 150ms ease, background-color 150ms ease; }
.catalogue__effacer--cache { visibility: hidden; }
.catalogue__corps { display: grid; gap: 22px; }
.catalogue__deplier { display: none; }
.catalogue__effacer:hover { color: var(--ink); background: var(--surface-raised); }
.catalogue fieldset { display: flex; flex-wrap: wrap; gap: 6px; padding: 0; margin: 0; border: 0; min-width: 0; }
.catalogue legend { padding: 0; margin-bottom: 10px; line-height: 1.4; color: var(--ink-muted); font-size: .75rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
/* Les statuts : une liste, la ligne choisie est surlignée et cochée. */
.catalogue__statuts { flex-direction: column; flex-wrap: nowrap; gap: 2px; }
.catalogue__statuts button { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 10px; min-height: 38px; border: 0; border-radius: 10px; background: transparent; color: var(--ink-muted); font: inherit; font-size: .9375rem; font-weight: 600; text-align: start; cursor: pointer; transition: color 150ms ease, background-color 150ms ease; }
.catalogue__statuts button:hover { color: var(--ink); background: var(--surface-raised); }
.catalogue__statuts svg { flex: none; width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; opacity: 0; }
.catalogue__statuts button[aria-pressed='true'] { color: var(--action-ink); background: var(--action-soft); }
.catalogue__statuts button[aria-pressed='true'] svg { opacity: 1; }
.catalogue__statuts button:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
/* Le format et la durée : des pastilles ; la vraie case reste dans la pastille pour le clavier et les lecteurs d'écran. */
.catalogue__pastilles label { position: relative; display: inline-flex; align-items: center; min-height: 34px; padding: 0 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-pill); color: var(--ink-muted); font-size: .875rem; font-weight: 600; cursor: pointer; transition: color 150ms ease, background-color 150ms ease, border-color 150ms ease; }
.catalogue__pastilles label:hover { color: var(--ink); border-color: var(--ink-muted); }
.catalogue__pastilles input { position: absolute; inset: 0; margin: 0; opacity: 0; cursor: pointer; }
.catalogue__pastilles label:has(:checked) { color: var(--action-ink); background: var(--action-soft); border-color: color-mix(in srgb, var(--action) 55%, transparent); }
.catalogue__pastilles label:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .catalogue__statuts button, .catalogue__pastilles label, .catalogue__effacer, .catalogue__contenu { transition: none; } }
.catalogue__contenu { display: grid; gap: 24px; align-content: start; min-width: 0; min-height: calc(100vh - 120px); min-height: calc(100dvh - 120px); transition: opacity 150ms ease; }
.catalogue__contenu--maj { opacity: .55; pointer-events: none; }
.catalogue__tri { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; font-size: .875rem; color: var(--ink-muted); }
.catalogue__tri select { border-radius: 50px; font-size: .875rem; }
.catalogue .liste { grid-template-columns: minmax(0, 1fr); }
.catalogue .hackathon { grid-template-columns: 160px minmax(0, 1fr); grid-template-rows: auto; }
.catalogue .hackathon__banniere { block-size: 100%; min-block-size: 240px; display: grid; align-content: center; justify-items: center; }
.catalogue .hackathon__corps { padding: 28px; }
@media (max-width: 900px) { .catalogue { grid-template-columns: 200px minmax(0, 1fr); gap: 20px; } .catalogue__filtres { padding: 16px; top: 125px; } .catalogue .hackathon { grid-template-columns: 100px minmax(0, 1fr); } .catalogue .hackathon__corps { padding: 20px; } .catalogue .hackathon__banniere { padding: 16px; } }
@media (max-width: 680px) { .catalogue { grid-template-columns: minmax(0, 1fr); } .catalogue__filtres { position: static; } .catalogue__statuts { flex-direction: row; flex-wrap: wrap; gap: 6px; } .catalogue__statuts button { min-height: 34px; padding: 0 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-pill); font-size: .875rem; } .catalogue__statuts button[aria-pressed='true'] { border-color: color-mix(in srgb, var(--action) 55%, transparent); } .catalogue__statuts svg { display: none; } .catalogue .hackathon { grid-template-columns: minmax(0, 1fr); } .catalogue .hackathon__banniere { min-block-size: 100px; justify-items: start; } }
/* Téléphone : les filtres se replient derrière un bouton, et chaque groupe tient sur une ligne qui défile. */
@media (max-width: 680px) {
  .catalogue__filtres { gap: 0; padding: 6px 12px; border-radius: 18px; }
  .catalogue__filtres-tete h2 { display: none; }
  .catalogue__deplier { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 4px; border: 0; background: transparent; color: var(--ink); font: inherit; font-weight: 700; cursor: pointer; }
  .catalogue__deplier svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; transition: transform 150ms ease; }
  .catalogue__deplier[aria-expanded='true'] svg { transform: rotate(180deg); }
  .catalogue__deplier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: 8px; }
  .catalogue__corps { display: none; gap: 16px; padding: 8px 0 14px; }
  .catalogue__corps--ouvert { display: grid; }
  .catalogue__corps fieldset { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; margin-inline: -12px; padding-inline: 12px; }
  .catalogue__corps fieldset::-webkit-scrollbar { display: none; }
  .catalogue__corps fieldset > * { flex: none; white-space: nowrap; }
}
@media (max-width: 680px) and (prefers-reduced-motion: reduce) { .catalogue__deplier svg { transition: none; } }
</style>
