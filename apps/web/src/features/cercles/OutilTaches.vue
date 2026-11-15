<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar, JSquelette } from '../../design-system'
import AyoAvatar from '../demandes/AyoAvatar.vue'
import CollineDuTour from './CollineDuTour.vue'
import DialogueTache from './DialogueTache.vue'
import EnTeteOutil from './EnTeteOutil.vue'
import IconeCercle from './IconeCercle.vue'
import { changerTache, lireTaches, type Cercle, type Tache } from './cercles'
import { lireCompetences, proposerRepartition, type Suggestion } from './repartition'
import { decouperDescription, etatEcheance, ordreDeTravail } from './taches'

/*
 * Les tâches du tour (les « to-dos » de Basecamp). Une tâche se comprend sans demander : un titre, ce qu'il faut faire,
 * quand c'est fini, sa priorité, son échéance, qui s'en charge. La liste suit l'ordre de travail (priorité, puis
 * échéance) ; les tâches livrées se replient en bas. Un clic ouvre la fiche ; « Nouvelle tâche » ouvre le formulaire.
 * Tous les membres du cercle sont dans l'équipe du projet : chacun crée, prend, livre.
 */
const props = defineProps<{ cercle: Cercle; moiId: string | null }>()
const emit = defineEmits<{ maj: [taches: Tache[]] }>()
const { t, locale } = useI18n()

const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)
const membres = computed(() => props.cercle.membres.map((m) => m.personne))
const taches = ref<Tache[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
const erreur = ref<string | null>(null)
const enCours = ref<string | null>(null)

function publier(liste: Tache[]) {
  taches.value = liste
  emit('maj', liste)
}
onMounted(async () => {
  if (!tour.value?.projetId) return void (etat.value = 'pret')
  const liste = await lireTaches(tour.value.projetId)
  if (!liste) return void (etat.value = 'erreur')
  publier(liste)
  etat.value = 'pret'
})
function remplacer(tache: Tache) {
  const existe = taches.value.some((x) => x.id === tache.id)
  publier(existe ? taches.value.map((x) => (x.id === tache.id ? tache : x)) : [...taches.value, tache])
}

// ---------------------------------------------------------------- filtres et ordre

const filtre = ref<'toutes' | 'miennes' | 'libres'>('toutes')
const ouvertes = computed(() => taches.value.filter((x) => x.statut !== 'livree'))
const compte = computed(() => ({
  toutes: ouvertes.value.length,
  miennes: ouvertes.value.filter((x) => x.assignee?.id === props.moiId).length,
  libres: ouvertes.value.filter((x) => !x.assignee).length,
}))
const visibles = computed(() =>
  ordreDeTravail(
    ouvertes.value.filter((x) => (filtre.value === 'miennes' ? x.assignee?.id === props.moiId : filtre.value === 'libres' ? !x.assignee : true)),
  ),
)
const livrees = computed(() => [...taches.value.filter((x) => x.statut === 'livree')].sort((a, b) => (b.livreeLe ?? '').localeCompare(a.livreeLe ?? '')))
const voirLivrees = ref(false)

// ---------------------------------------------------------------- actions rapides

async function modifier(tache: Tache, changement: { statut?: Tache['statut']; assigneeId?: string | null }) {
  if (!tour.value?.projetId) return
  enCours.value = tache.id
  erreur.value = null
  const r = await changerTache(tour.value.projetId, tache, changement)
  enCours.value = null
  if (!r.ok) {
    erreur.value = t(r.code === 'modification_concurrente' ? 'cercles.tachesTour.concurrente' : 'cercles.tachesTour.erreur')
    const fraiches = await lireTaches(tour.value.projetId)
    if (fraiches) publier(fraiches)
    return
  }
  remplacer(r.tache)
}
const cocher = (x: Tache) => modifier(x, { statut: x.statut === 'livree' ? (x.assignee ? 'en_cours' : 'a_faire') : 'livree' })
const prendre = (x: Tache) => modifier(x, { assigneeId: props.moiId, statut: 'en_cours' })

// ---------------------------------------------------------------- la fiche

const dialogue = ref(false)
const choisie = ref<string | null>(null)
const tacheChoisie = computed(() => taches.value.find((x) => x.id === choisie.value) ?? null)
function ouvrir(x: Tache | null) {
  choisie.value = x?.id ?? null
  dialogue.value = true
}
function enregistree(x: Tache) {
  remplacer(x)
  choisie.value = x.id
}

// ---------------------------------------------------------------- Ayo propose qui prend quoi (repartition.ts)

const suggestions = ref<Suggestion[] | null>(null)
const reflechit = ref(false)
async function demanderAyo() {
  reflechit.value = true
  suggestions.value = proposerRepartition(taches.value, await lireCompetences(membres.value), tour.value?.beneficiaire.id ?? null)
  reflechit.value = false
}
const propositions = computed(() => (suggestions.value ?? []).filter((s) => taches.value.some((x) => x.id === s.tache.id && !x.assignee && x.statut === 'a_faire')))
async function confier(s: Suggestion) {
  const x = taches.value.find((y) => y.id === s.tache.id)
  if (x) await modifier(x, { assigneeId: s.membre.id, statut: 'en_cours' })
}

// ---------------------------------------------------------------- affichage

const resume = (x: Tache) => decouperDescription(x.description).faire.split('\n')[0] ?? ''
const echeanceCourte = (iso: string) => new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${iso}T12:00:00Z`))
</script>

<template>
  <section class="outil-taches" aria-labelledby="taches-outil-titre" data-test="outil-taches-page">
    <div class="tete">
      <EnTeteOutil id="taches-outil-titre" icone="taches" :titre="t('cercles.qg.outils.taches')" :aide="t('cercles.qg.tachesAide')" />
      <button v-if="tour?.projetId" type="button" class="nouvelle" data-test="nouvelle-tache" @click="ouvrir(null)">
        <IconeCercle nom="plus" :taille="16" />{{ t('cercles.tache.nouvelle') }}
      </button>
    </div>

    <p v-if="!tour?.projetId" class="texte-discret">{{ t('cercles.tableau.pasDeTour') }}</p>
    <JSquelette v-else-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'erreur'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>

    <div v-else class="grille">
      <aside class="cote">
        <section class="carte" aria-labelledby="colline-titre">
          <h3 id="colline-titre">{{ t('cercles.qg.colline') }}</h3>
          <CollineDuTour v-if="taches.length" :taches="taches" />
          <p v-else class="texte-discret">{{ t('cercles.qg.aucuneTache') }}</p>
          <p class="texte-discret legende">{{ t('cercles.qg.collineAide') }}</p>
        </section>

        <section v-if="moiId && compte.libres" class="carte ayo" data-test="repartition-ayo">
          <div class="ayo__tete">
            <AyoAvatar :taille="28" :reflechit="reflechit" />
            <p>{{ t('cercles.repartition.intro') }}</p>
          </div>
          <button v-if="!suggestions || !propositions.length" type="button" class="pilule" :disabled="reflechit" data-test="demander-ayo" @click="demanderAyo">{{ t('cercles.repartition.demander') }}</button>
          <ul v-if="propositions.length" class="ayo__liste">
            <li v-for="s in propositions" :key="s.tache.id">
              <span><strong>{{ s.tache.titre }}</strong> → {{ s.membre.nom }}</span>
              <button type="button" class="pilule" :disabled="enCours === s.tache.id" data-test="confier" @click="confier(s)">{{ t('cercles.repartition.confier') }}</button>
            </li>
          </ul>
          <p v-else-if="suggestions" class="texte-discret">{{ t('cercles.repartition.rien') }}</p>
        </section>
      </aside>

      <section class="carte liste-carte" :aria-label="t('cercles.qg.outils.taches')">
        <div class="filtres" role="tablist" :aria-label="t('cercles.tache.filtres')">
          <button v-for="f in (['toutes', 'miennes', 'libres'] as const)" :key="f" type="button" role="tab" :aria-selected="filtre === f" class="filtre" :class="{ actif: filtre === f }" :data-test="`filtre-${f}`" @click="filtre = f">
            {{ t(`cercles.tache.filtresNoms.${f}`) }} <span class="filtre__nombre">{{ compte[f] }}</span>
          </button>
        </div>

        <p v-if="!visibles.length" class="vide" data-test="aucune-tache">
          {{ filtre === 'toutes' ? t('cercles.tache.aucuneOuverte') : t(`cercles.tache.aucune.${filtre}`) }}
        </p>
        <TransitionGroup v-else tag="ul" name="ligne" class="lignes">
          <li v-for="x in visibles" :key="x.id" class="ligne" :class="`ligne--${x.priorite}`" data-test="ligne-tache">
            <input type="checkbox" class="case" :checked="false" :disabled="enCours === x.id" :aria-label="t('cercles.tache.cocher', { titre: x.titre })" data-test="cocher-ligne" @change="cocher(x)" />
            <button type="button" class="ligne__corps" data-test="ouvrir-tache" @click="ouvrir(x)">
              <span class="ligne__titre">{{ x.titre }}</span>
              <span v-if="resume(x)" class="ligne__resume">{{ resume(x) }}</span>
            </button>
            <span class="ligne__meta">
              <span v-if="x.priorite !== 'normale'" class="priorite" :class="`priorite--${x.priorite}`"><IconeCercle nom="priorite" :taille="13" />{{ t(`cercles.tache.priorites.${x.priorite}`) }}</span>
              <span v-if="x.echeance" class="echeance" :class="`echeance--${etatEcheance(x.echeance)}`">{{ echeanceCourte(x.echeance) }}</span>
              <LienPersonne v-if="x.assignee" :id="x.assignee.id" :nom="x.assignee.nom"><JAvatar :nom="x.assignee.nom" :url="x.assignee.avatarUrl" :taille="26" /></LienPersonne>
              <button v-else-if="moiId" type="button" class="pilule" :disabled="enCours === x.id" data-test="prendre-ligne" @click="prendre(x)">{{ t('cercles.tachesTour.prendre') }}</button>
            </span>
          </li>
        </TransitionGroup>

        <div v-if="livrees.length" class="livrees">
          <button type="button" class="livrees__bascule" :aria-expanded="voirLivrees" data-test="voir-livrees" @click="voirLivrees = !voirLivrees">
            <IconeCercle nom="livre" :taille="16" />{{ t('cercles.tache.livrees', { n: livrees.length }, livrees.length) }}
          </button>
          <ul v-if="voirLivrees" class="lignes">
            <li v-for="x in livrees" :key="x.id" class="ligne ligne--livree">
              <input type="checkbox" class="case" checked :disabled="enCours === x.id" :aria-label="t('cercles.tache.rouvrirLibelle', { titre: x.titre })" @change="cocher(x)" />
              <button type="button" class="ligne__corps" @click="ouvrir(x)"><span class="ligne__titre">{{ x.titre }}</span></button>
              <span class="ligne__meta">
                <LienPersonne v-if="x.assignee" :id="x.assignee.id" :nom="x.assignee.nom"><JAvatar :nom="x.assignee.nom" :url="x.assignee.avatarUrl" :taille="22" /></LienPersonne>
              </span>
            </li>
          </ul>
        </div>
        <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      </section>
    </div>

    <DialogueTache
      v-if="tour?.projetId"
      :projet-id="tour.projetId"
      :membres="membres"
      :moi-id="moiId"
      :tache="tacheChoisie"
      :ouvert="dialogue"
      @ferme="dialogue = false"
      @enregistre="enregistree"
    />
  </section>
</template>

<style scoped>
.outil-taches { display: grid; gap: var(--space-6); }
.tete { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.nouvelle {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer;
}
.nouvelle:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.grille { display: grid; gap: var(--space-4); grid-template-columns: minmax(15rem, 20rem) minmax(0, 1fr); align-items: start; }
.cote { display: grid; gap: var(--space-4); }
.cote h3 { margin: 0 0 var(--space-3); font-size: var(--type-body-size); }
.legende { margin: var(--space-3) 0 0; font-size: 0.8125rem; }

.ayo { display: grid; gap: var(--space-3); }
.ayo__tete { display: flex; gap: var(--space-2); align-items: flex-start; }
.ayo__tete p { margin: 0; font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.ayo__liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.ayo__liste li { display: flex; justify-content: space-between; align-items: center; gap: var(--space-2); }

.liste-carte { display: grid; gap: var(--space-3); padding-bottom: var(--space-4); }
.filtres { display: flex; gap: var(--space-1); padding: 4px; border-radius: var(--radius-pill); background: var(--surface-raised); justify-self: start; }
.filtre { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill); background: transparent; color: var(--ink-muted); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }
.filtre.actif { background: var(--surface); color: var(--ink); box-shadow: 0 1px 3px rgb(2 7 10 / 0.15); }
.filtre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.filtre__nombre { min-width: 20px; padding: 0 6px; border-radius: var(--radius-pill); background: var(--surface-raised); font-size: 0.75rem; text-align: center; }
.filtre.actif .filtre__nombre { background: var(--surface-raised); }

/* Une ligne par tâche : cocher, lire, voir qui et quand. Des séparations fines, pas de cartes dans la carte. */
.lignes { list-style: none; margin: 0; padding: 0; }
.ligne { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; min-height: 56px; padding: var(--space-2) var(--space-2); border-top: 1px solid var(--line); }
.ligne:first-child { border-top: 0; }
.ligne:hover { background: color-mix(in srgb, var(--surface-raised) 60%, transparent); }
.case { width: 20px; height: 20px; margin: 0; accent-color: var(--proof); cursor: pointer; }
.case:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ligne__corps { display: grid; gap: 2px; min-width: 0; padding: var(--space-1) 0; border: 0; background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
.ligne__corps:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: var(--radius-sm); }
.ligne__titre { font-weight: 600; overflow-wrap: anywhere; }
.ligne__corps:hover .ligne__titre { text-decoration: underline; text-underline-offset: 3px; text-decoration-thickness: 1px; }
.ligne__resume { color: var(--ink-muted); font-size: var(--type-body-sm-size); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ligne__meta { display: flex; align-items: center; gap: var(--space-2); }
.ligne--livree .ligne__titre { color: var(--ink-muted); font-weight: 500; text-decoration: line-through; }

.priorite { display: inline-flex; align-items: center; gap: 3px; padding: 1px var(--space-2); border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; white-space: nowrap; }
.priorite--haute { background: var(--proof-soft); color: var(--proof-ink); }
.priorite--basse { color: var(--ink-muted); border: 1px solid var(--line); }
.priorite--basse :deep(svg) { transform: rotate(180deg); }
.echeance { font-size: 0.75rem; font-weight: 600; color: var(--ink-muted); white-space: nowrap; }
.echeance--depassee { color: var(--danger); }
.echeance--aujourdhui, .echeance--demain { color: var(--proof-ink); }
.pilule { min-height: 32px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer; white-space: nowrap; }
.pilule:hover { border-color: var(--action); }
.pilule:disabled { opacity: 0.6; cursor: progress; }
.pilule:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.vide { margin: var(--space-6) 0; text-align: center; color: var(--ink-muted); }
.livrees { border-top: 1px solid var(--line); padding-top: var(--space-2); }
.livrees__bascule { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 40px; padding: 0 var(--space-2); border: 0; background: none; color: var(--ink-muted); font: inherit; font-weight: 600; cursor: pointer; }
.livrees__bascule:hover { color: var(--ink); }
.livrees__bascule:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.ligne-move { transition: transform var(--motion-base) var(--ease-out); }
@media (prefers-reduced-motion: reduce) { .ligne-move { transition: none; } }
@media (max-width: 900px) {
  .grille { grid-template-columns: 1fr; }
  .cote { order: 2; }
}
@media (max-width: 560px) {
  .ligne { grid-template-columns: auto minmax(0, 1fr); }
  .ligne__meta { grid-column: 2; }
}
</style>
