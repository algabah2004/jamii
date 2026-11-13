<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JBouton, JSquelette } from '../../design-system'
import {
  ATTENTE_PLAN_MAX_MS,
  creerProjet,
  fonctionnalitesParPriorite,
  INTERVALLE_PLAN_MS,
  jalonsAvecTaches,
  lireDemande,
  type Demande,
} from './demandes'

/*
 * Le plan proposé par l'assistant pour une demande de PME. « L'IA propose, l'humain décide » :
 * tout est présenté comme une proposition, et seul un clic humain en fait un projet.
 * Les textes du plan sont générés : affichés comme du texte brut, jamais interprétés.
 */
const { t } = useI18n()
const route = useRoute()
const id = computed(() => String(route.params.id))

const demande = ref<Demande | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const tropLong = ref(false)
let minuterie: ReturnType<typeof setTimeout> | undefined
let debut = 0

async function charger(silencieux = false) {
  if (!silencieux) etat.value = 'chargement'
  try {
    const lue = await lireDemande(id.value)
    if (!lue) return void (etat.value = 'introuvable')
    demande.value = lue
    etat.value = 'pret'
  } catch {
    if (!silencieux) etat.value = 'indisponible'
  }
  surveiller()
}

/** Tant que le plan se prépare, on relit la demande, sans jamais dépasser une minute. */
function surveiller() {
  clearTimeout(minuterie)
  const statut = demande.value?.plan.statut
  if (statut !== 'en_attente' && statut !== 'en_cours') return
  if (!debut) debut = Date.now()
  if (Date.now() - debut >= ATTENTE_PLAN_MAX_MS) return void (tropLong.value = true)
  minuterie = setTimeout(() => void charger(true), INTERVALLE_PLAN_MS)
}
onBeforeUnmount(() => clearTimeout(minuterie))
watch(id, () => {
  debut = 0
  tropLong.value = false
  void charger()
}, { immediate: true })

const plan = computed(() => (demande.value?.plan.statut === 'terminee' ? (demande.value.plan.resultat ?? null) : null))
const enPreparation = computed(() => ['en_attente', 'en_cours'].includes(demande.value?.plan.statut ?? '') && !tropLong.value)
const fonctionnalites = computed(() => (plan.value ? fonctionnalitesParPriorite(plan.value) : []))
const jalons = computed(() => (plan.value ? jalonsAvecTaches(plan.value) : []))

const lancement = ref<{ etat: 'repos' | 'envoi' | 'fait' | 'erreur'; code?: string }>({ etat: 'repos' })
async function lancer() {
  if (!demande.value) return
  lancement.value = { etat: 'envoi' }
  try {
    const r = await creerProjet(demande.value)
    lancement.value = 'id' in r ? { etat: 'fait' } : { etat: 'erreur', code: r.erreur }
  } catch {
    lancement.value = { etat: 'erreur', code: 'autre' }
  }
}
const messageErreur = computed(() =>
  lancement.value.code === 'demande_deja_prise' ? t('demandes.plan.dejaPrise') : t('demandes.plan.erreurLancement'),
)
</script>

<template>
  <section class="demande fond-verre">
    <p class="retour"><RouterLink to="/demandes/nouvelle">{{ t('demandes.plan.autre') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement' && !demande" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/demandes/nouvelle">{{ t('demandes.plan.autre') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="demande">
      <header class="tete motif motif--croix">
        <h1>{{ demande.titre }}</h1>
        <!-- Texte brut, affiché échappé : les mots de la PME, jamais interprétés. -->
        <blockquote class="carte besoin">{{ demande.description }}</blockquote>
      </header>

      <div v-if="enPreparation" class="carte attente" role="status" data-test="plan-attente">
        <span class="points" aria-hidden="true"><i /><i /><i /></span>
        <div>
          <strong>{{ t('demandes.plan.prepare') }}</strong>
          <p>{{ t('demandes.plan.prepareAide') }}</p>
        </div>
      </div>
      <div v-else-if="!plan" role="alert" class="carte attente attente--echec" data-test="plan-echec">
        <div>
          <strong>{{ t('demandes.plan.echec') }}</strong>
          <p>{{ t('demandes.plan.echecAide') }}</p>
        </div>
        <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
      </div>

      <template v-if="plan">
        <p class="proposition" data-test="plan-propose">✨ {{ t('demandes.plan.propose') }}</p>
        <p v-if="demande.plan.origine === 'repli'" class="carte repli" role="status" data-test="plan-repli">{{ t('demandes.plan.repli') }}</p>

        <section class="carte bloc" aria-labelledby="resume">
          <h2 id="resume">{{ t('demandes.plan.resume') }}</h2>
          <p class="resume">{{ plan.cahierDesCharges.resume }}</p>
          <ul class="objectifs">
            <li v-for="o in plan.cahierDesCharges.objectifs" :key="o">{{ o }}</li>
          </ul>
        </section>

        <section class="bloc-nu" aria-labelledby="fonctionnalites">
          <h2 id="fonctionnalites">{{ t('demandes.plan.fonctionnalites') }}</h2>
          <div class="priorites">
            <div v-for="g in fonctionnalites" :key="g.priorite" class="carte priorite" :class="`priorite--${g.priorite}`" data-test="priorite">
              <h3>{{ t(`demandes.priorites.${g.priorite}`) }}</h3>
              <ul>
                <li v-for="f in g.liste" :key="f.titre"><strong>{{ f.titre }}</strong><span>{{ f.description }}</span></li>
              </ul>
            </div>
          </div>
        </section>

        <section class="bloc-nu" aria-labelledby="jalons">
          <h2 id="jalons">{{ t('demandes.plan.jalons') }}</h2>
          <ol class="jalons" data-test="jalons">
            <li v-for="j in jalons" :key="j.titre" class="carte jalon">
              <span class="jalon__semaine">{{ t('demandes.plan.semaine', { n: j.semaine }) }}</span>
              <h3>{{ j.titre }}</h3>
              <p>{{ j.livrable }}</p>
              <ul v-if="j.taches.length" class="taches">
                <li v-for="tache in j.taches" :key="tache.titre">
                  <span>{{ tache.titre }}</span>
                  <span class="taches__competences">{{ tache.competences.join(' · ') }}</span>
                </li>
              </ul>
            </li>
          </ol>
        </section>

        <div class="deux">
          <section v-if="plan.rolesManquants.length" class="carte bloc" aria-labelledby="roles">
            <h2 id="roles">{{ t('demandes.plan.roles') }}</h2>
            <p class="texte-discret">{{ t('demandes.plan.rolesAide') }}</p>
            <ul class="roles" data-test="roles">
              <li v-for="r in plan.rolesManquants" :key="r.role">
                <strong>{{ r.role }}</strong>
                <span class="roles__competences"><span v-for="c in r.competences" :key="c" class="etiquette">{{ c }}</span></span>
                <span class="texte-discret">{{ r.raison }}</span>
              </li>
            </ul>
          </section>
          <section v-if="plan.servicesSuggeres.length" class="carte bloc" aria-labelledby="services">
            <h2 id="services">{{ t('demandes.plan.services') }}</h2>
            <p class="texte-discret">{{ t('demandes.plan.servicesAide') }}</p>
            <ul class="services">
              <li v-for="s in plan.servicesSuggeres" :key="s.service">
                <strong>{{ t(`systalink.services.${s.service === 'domaine' ? 'domaines' : s.service}`) }}</strong>
                <span class="texte-discret">{{ s.raison }}</span>
              </li>
            </ul>
          </section>
        </div>

        <section v-if="plan.cahierDesCharges.horsPerimetre.length" class="bloc-nu" aria-labelledby="hors">
          <h2 id="hors">{{ t('demandes.plan.horsPerimetre') }}</h2>
          <ul class="hors">
            <li v-for="h in plan.cahierDesCharges.horsPerimetre" :key="h">{{ h }}</li>
          </ul>
        </section>

        <!-- La décision humaine, toujours visible en bas de l'écran. -->
        <div class="verre decision">
          <p v-if="lancement.etat === 'fait'" role="status" class="decision__fait" data-test="projet-cree">✓ {{ t('demandes.plan.lance') }}</p>
          <template v-else>
            <p class="decision__texte">{{ t('demandes.plan.decision') }}</p>
            <p v-if="lancement.etat === 'erreur'" role="alert" class="erreur">{{ messageErreur }}</p>
            <JBouton :occupe="lancement.etat === 'envoi'" data-test="lancer" @click="lancer">{{ t('demandes.plan.lancer') }}</JBouton>
          </template>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.demande { display: grid; gap: var(--space-6); max-width: 64rem; }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.tete { display: grid; gap: var(--space-4); }
.tete h1 { margin: 0; font-size: clamp(1.875rem, 4vw, 2.5rem); line-height: 1.1; letter-spacing: -0.6px; }
.besoin { margin: 0; padding: var(--space-4) var(--space-6); white-space: pre-wrap; color: var(--ink-muted); font-style: italic; border-inline-start: 3px solid var(--line-strong); }

.attente { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); padding: var(--space-6); }
.attente strong { display: block; }
.attente p { margin: var(--space-1) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.attente--echec { justify-content: space-between; }
.points { display: inline-flex; gap: 5px; }
.points i { width: 8px; height: 8px; border-radius: 50%; background: var(--action); animation: respire 1.2s var(--ease-out) infinite; }
.points i:nth-child(2) { animation-delay: 0.15s; }
.points i:nth-child(3) { animation-delay: 0.3s; }
@keyframes respire { 50% { opacity: 0.25; transform: translateY(-3px); } }
@media (prefers-reduced-motion: reduce) { .points i { animation: none; } }

/* Charte : une proposition de l'IA n'est pas une preuve, d'où le pointillé et pas d'orange. */
.proposition { justify-self: start; margin: 0; padding: var(--space-1) var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--action-soft) 70%, transparent); font-size: var(--type-body-sm-size); font-weight: 600; }
.repli { margin: 0; padding: var(--space-4) var(--space-6); font-weight: 600; }

.bloc { display: grid; gap: var(--space-3); padding: var(--space-6) var(--space-8); }
.bloc-nu { display: grid; gap: var(--space-4); }
.bloc h2, .bloc-nu h2 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.resume { margin: 0; font-size: 1.0625rem; line-height: 1.65; white-space: pre-wrap; }
.objectifs { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.objectifs li { display: flex; gap: var(--space-2); }
.objectifs li::before { content: '→'; color: var(--action-ink); font-weight: 700; }

.priorites { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr)); }
.priorite { padding: var(--space-6); display: grid; gap: var(--space-3); align-content: start; }
.priorite h3 { margin: 0; font-size: 1rem; display: flex; align-items: center; gap: var(--space-2); }
.priorite h3::before { content: ''; width: 10px; height: 10px; border-radius: 50%; background: var(--line-strong); }
.priorite--indispensable h3::before { background: var(--action); }
.priorite--utile h3::before { background: color-mix(in srgb, var(--action) 45%, var(--surface)); }
.priorite ul { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.priorite li { display: grid; gap: 2px; }
.priorite li span { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

/* Les jalons : une frise, de la première semaine à la dernière. */
.jalons { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 17rem), 1fr)); counter-reset: jalon; }
.jalon { position: relative; display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); }
.jalon__semaine { justify-self: start; padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-size: var(--type-label-size); font-weight: 700; }
.jalon h3 { margin: 0; font-size: 1.0625rem; }
.jalon p { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.taches { list-style: none; margin: var(--space-2) 0 0; padding: var(--space-3) 0 0; border-top: 1px solid color-mix(in srgb, var(--ink) 10%, transparent); display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.taches li { display: grid; }
.taches__competences { color: var(--ink-muted); font-size: var(--type-label-size); }

.deux { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr)); align-items: start; }
.roles, .services { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.roles li, .services li { display: grid; gap: var(--space-1); }
.roles__competences { display: flex; flex-wrap: wrap; gap: var(--space-1); }
.hors { margin: 0; padding-inline-start: 1.25rem; color: var(--ink-muted); display: grid; gap: var(--space-1); }

.decision {
  position: sticky; bottom: var(--space-4); z-index: 2;
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3) var(--space-6);
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-8); border-radius: var(--radius-pill);
}
.decision__texte { margin: 0; font-weight: 600; }
.decision__fait { margin: 0; font-weight: 700; color: var(--ink); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
