<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { FILTRES, listerHackathons, type Filtre, type HackathonResume } from './hackathons'
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
const connecte = computed(() => session.value === 'connecte')
const recherche = ref('')
const vedette = computed(() => hackathons.value.find((h) => h.statut === 'publie' || h.statut === 'en_cours') ?? hackathons.value[0])
const visibles = computed(() => hackathons.value.filter((h) => correspond(recherche.value, [h.titre, h.theme, h.ville, h.organisateur.nom, t(`hackathons.lieux.${h.lieu}`)])))
const statistiques = computed(() => [
  { valeur: hackathons.value.length, libelle: t('exploration.hackathons.statDefis', hackathons.value.length) },
  { valeur: hackathons.value.reduce((total, h) => total + h.nombreInscrits, 0), libelle: t('exploration.hackathons.statInscrits', hackathons.value.reduce((total, h) => total + h.nombreInscrits, 0)) },
  { valeur: new Set(hackathons.value.map((h) => h.ville).filter(Boolean)).size, libelle: t('exploration.hackathons.statVilles', new Set(hackathons.value.map((h) => h.ville).filter(Boolean)).size) },
])

// Chaque chargement a son numéro : la réponse d'un ancien filtre, arrivée en retard, n'écrase pas la liste.
let dernier = 0
async function charger(curseur?: string) {
  const numero = ++dernier
  if (curseur) plus.value = true
  else etat.value = 'chargement'
  try {
    const page = await listerHackathons(filtre.value, curseur)
    if (numero !== dernier) return
    hackathons.value = curseur ? [...hackathons.value, ...page.elements] : page.elements
    suivant.value = page.curseurSuivant
    etat.value = 'pret'
  } catch {
    if (!curseur && numero === dernier) etat.value = 'indisponible'
  } finally {
    if (numero === dernier) plus.value = false
  }
}
watch(filtre, () => void charger(), { immediate: true })

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
  <section class="hackathons exploration">
    <EnteteExploration :surtitre="t('exploration.hackathons.surtitre')" :titre="t('exploration.hackathons.titre')" :accent="t('exploration.hackathons.accent')" :intro="t('hackathons.intro')" :statistiques="statistiques">
      <template #actions><RouterLink v-if="connecte" to="/hackathons/nouveau" class="exploration__principal" data-test="creer-un-hackathon"><span aria-hidden="true">＋</span>{{ t('hackathons.creer') }}</RouterLink><a href="#hackathons-liste" class="exploration__secondaire">{{ t('exploration.hackathons.explorer') }} <span aria-hidden="true">↓</span></a></template>
      <p class="exploration__surtitre">{{ t('exploration.hackathons.vedette') }}</p>
      <template v-if="vedette && etat === 'pret'">
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
      <div class="filtres exploration__filtres" role="group" :aria-label="t('hackathons.titre')">
        <button
          v-for="f in FILTRES"
          :key="f"
          type="button"
          class="filtre"
          :aria-pressed="filtre === f"
          :data-test="`filtre-${f}`"
          @click="filtre = f"
        >{{ t(`hackathons.filtres.${f}`) }}</button>
        <button
          v-if="connecte"
          type="button"
          class="filtre filtre--miens"
          :aria-pressed="filtre === 'miens'"
          data-test="filtre-miens"
          @click="filtre = 'miens'"
        >{{ t('hackathons.filtres.miens') }}</button>
      </div>
      <span v-if="suivant" class="texte-discret">{{ t('exploration.dansLaListe') }}</span>
    </div>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message" data-test="indisponible">{{ t('hackathons.indisponible') }}</p>
    <template v-else>
      <div class="exploration__resultats"><h2>{{ t('exploration.hackathons.resultats') }}</h2><p aria-live="polite">{{ t('exploration.resultats', { n: visibles.length }, visibles.length) }}</p></div>
      <p v-if="!hackathons.length" class="carte message" data-test="aucun">{{ filtre === 'miens' ? t('hackathons.aucunMiens') : t('hackathons.aucun') }}</p>
      <div v-else-if="!visibles.length" class="exploration__vide" data-test="sans-resultat"><h2>{{ t('exploration.aucunResultat') }}</h2><p>{{ t('exploration.essayerAutrement') }}</p><button type="button" class="exploration__effacer" @click="recherche = ''">{{ t('exploration.effacer') }}</button></div>
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
  </section>
</template>

<style scoped>
.hackathons { display: grid; gap: var(--space-6); min-width: 0; }
.filtre--miens { margin-inline-start: auto; }
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
.liste--unique { grid-template-columns: minmax(0, 1fr); }
@media (min-width: 850px) {
  .liste--unique .hackathon { grid-template-columns: minmax(200px, .4fr) minmax(0, 1fr); grid-template-rows: auto; }
  .liste--unique .hackathon__banniere { block-size: 100%; min-block-size: 240px; }
  .liste--unique .hackathon__corps { padding: 32px; }
}
</style>
