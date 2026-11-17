<script setup lang="ts">
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JSquelette } from '../../design-system'
import { ajouterTache, changerTache, lireTaches, type Tache } from './cercles'
import AyoAvatar from '../demandes/AyoAvatar.vue'
import { lireCompetences, proposerRepartition, type Suggestion } from './repartition'

/*
 * Les tâches du tour : le travail que le cercle cotise au projet du bénéficiaire (contracts/projets.yaml). Tous les
 * membres du cercle sont dans l'équipe du projet : chacun peut ajouter une tâche, en prendre une, la cocher livrée.
 * Ordre d'affichage : en cours, à faire, puis livrées. La barre d'avancement de la page suit (`maj`).
 */
const props = withDefaults(
  defineProps<{ projetId: string; moiId: string | null; membres?: { id: string; nom: string }[]; beneficiaireId?: string | null }>(),
  { membres: () => [], beneficiaireId: null },
)
const emit = defineEmits<{ maj: [taches: Tache[]] }>()
const { t } = useI18n()

const taches = ref<Tache[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
const enCours = ref<string | null>(null)
const erreur = ref<string | null>(null)
const titre = ref('')
const ajout = ref(false)

const RANG = { en_cours: 0, a_faire: 1, livree: 2 } as const
const triees = computed(() => [...taches.value].sort((a, b) => RANG[a.statut] - RANG[b.statut] || a.creeLe.localeCompare(b.creeLe)))
const titreValide = computed(() => titre.value.trim().length >= 3)

function publier(liste: Tache[]) {
  taches.value = liste
  emit('maj', liste)
}

onMounted(async () => {
  const liste = await lireTaches(props.projetId)
  if (liste) {
    publier(liste)
    etat.value = 'pret'
  } else etat.value = 'erreur'
})

async function ajouter() {
  if (!titreValide.value) return
  ajout.value = true
  erreur.value = null
  const r = await ajouterTache(props.projetId, titre.value.trim())
  ajout.value = false
  if (!r.ok) return void (erreur.value = t('cercles.tachesTour.erreur'))
  titre.value = ''
  publier([...taches.value, r.tache])
}

async function modifier(tache: Tache, changement: { statut?: Tache['statut']; assigneeId?: string | null }) {
  enCours.value = tache.id
  erreur.value = null
  const r = await changerTache(props.projetId, tache, changement)
  enCours.value = null
  if (!r.ok) {
    erreur.value = t(r.code === 'modification_concurrente' ? 'cercles.tachesTour.concurrente' : 'cercles.tachesTour.erreur')
    const fraiches = await lireTaches(props.projetId)
    if (fraiches) publier(fraiches)
    return
  }
  publier(taches.value.map((x) => (x.id === r.tache.id ? r.tache : x)))
}

const cocher = (tache: Tache) => modifier(tache, { statut: tache.statut === 'livree' ? (tache.assignee ? 'en_cours' : 'a_faire') : 'livree' })
const prendre = (tache: Tache) => modifier(tache, { assigneeId: props.moiId, statut: 'en_cours' })
const lacher = (tache: Tache) => modifier(tache, { assigneeId: null, statut: 'a_faire' })
const aMoi = (tache: Tache) => !!props.moiId && tache.assignee?.id === props.moiId

// Ayo propose qui prend quoi (repartition.ts) ; un membre confie chaque tâche d'un clic, rien n'est fait sans lui.
const libres = computed(() => taches.value.filter((x) => x.statut === 'a_faire' && !x.assignee))
const suggestions = ref<Suggestion[] | null>(null)
const reflechit = ref(false)
async function demanderAyo() {
  reflechit.value = true
  const membres = await lireCompetences(props.membres)
  suggestions.value = proposerRepartition(taches.value, membres, props.beneficiaireId)
  reflechit.value = false
}
const encoreLibre = (s: Suggestion) => libres.value.some((x) => x.id === s.tache.id)
const visibles = computed(() => (suggestions.value ?? []).filter(encoreLibre))
async function confier(s: Suggestion) {
  const actuelle = taches.value.find((x) => x.id === s.tache.id)
  if (actuelle) await modifier(actuelle, { assigneeId: s.membre.id, statut: 'en_cours' })
}
async function toutConfier() {
  for (const s of [...visibles.value]) await confier(s)
}
</script>

<template>
  <section class="taches" aria-labelledby="taches-titre" data-test="taches-du-tour">
    <h3 id="taches-titre">{{ t('cercles.tachesTour.titre') }}</h3>

    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'erreur'" role="alert" class="note">{{ t('etat.indisponible') }}</p>
    <template v-else>
      <p v-if="!taches.length" class="note" data-test="aucune-tache">{{ t('cercles.tachesTour.aucune') }}</p>
      <TransitionGroup v-else tag="ul" name="tache" class="liste">
        <li v-for="tache in triees" :key="tache.id" class="tache" :class="{ 'tache--livree': tache.statut === 'livree' }" data-test="tache">
          <input
            :id="`tache-${tache.id}`"
            type="checkbox"
            class="tache__case"
            :checked="tache.statut === 'livree'"
            :disabled="enCours === tache.id"
            data-test="cocher"
            @change="cocher(tache)"
          />
          <label :for="`tache-${tache.id}`" class="tache__titre">{{ tache.titre }}</label>
          <span class="tache__qui">
            <template v-if="tache.assignee">
              <LienPersonne :id="tache.assignee.id" :nom="tache.assignee.nom"><JAvatar :nom="tache.assignee.nom" :url="tache.assignee.avatarUrl" :taille="24" /></LienPersonne>
              <span class="visuellement-cache">{{ t('cercles.tachesTour.prisePar', { nom: tache.assignee.nom }) }}</span>
              <button v-if="aMoi(tache) && tache.statut !== 'livree'" type="button" class="lien" :disabled="enCours === tache.id" data-test="lacher" @click="lacher(tache)">
                {{ t('cercles.tachesTour.lacher') }}
              </button>
            </template>
            <button v-else-if="tache.statut !== 'livree' && moiId" type="button" class="lien" :disabled="enCours === tache.id" data-test="prendre" @click="prendre(tache)">
              {{ t('cercles.tachesTour.prendre') }}
            </button>
          </span>
        </li>
      </TransitionGroup>

      <div v-if="moiId && membres.length && (libres.length || visibles.length)" class="repartition" data-test="repartition-ayo">
        <div class="repartition__tete">
          <AyoAvatar :taille="32" :reflechit="reflechit" />
          <p class="repartition__texte">{{ t('cercles.repartition.intro') }}</p>
          <button v-if="!suggestions || !visibles.length" type="button" class="lien lien--ayo" :disabled="reflechit" :aria-busy="reflechit || undefined" data-test="demander-ayo" @click="demanderAyo">
            {{ t('cercles.repartition.demander') }}
          </button>
        </div>
        <template v-if="suggestions">
          <ul v-if="visibles.length" class="repartition__liste">
            <li v-for="s in visibles" :key="s.tache.id" class="repartition__ligne" data-test="suggestion">
              <span class="repartition__quoi"><strong>{{ s.tache.titre }}</strong> → {{ s.membre.nom }}</span>
              <span class="repartition__pourquoi">
                {{ s.competences.length ? t('cercles.repartition.parCompetences', { competences: s.competences.join(', ') }) : t('cercles.repartition.parCharge', { n: s.charge }, s.charge) }}
              </span>
              <button type="button" class="lien" :disabled="enCours === s.tache.id" data-test="confier" @click="confier(s)">{{ t('cercles.repartition.confier') }}</button>
            </li>
          </ul>
          <p v-else class="note" data-test="repartition-vide">{{ t('cercles.repartition.rien') }}</p>
          <div v-if="visibles.length > 1" class="repartition__actions">
            <button type="button" class="lien lien--ayo" :disabled="!!enCours" data-test="tout-confier" @click="toutConfier">{{ t('cercles.repartition.toutConfier') }}</button>
          </div>
          <p class="note">{{ t('cercles.repartition.note') }}</p>
        </template>
      </div>

      <form class="ajout" data-test="ajouter-tache" @submit.prevent="ajouter">
        <label for="nouvelle-tache" class="visuellement-cache">{{ t('cercles.tachesTour.nouvelle') }}</label>
        <input id="nouvelle-tache" v-model="titre" class="ajout__champ" :placeholder="t('cercles.tachesTour.nouvelle')" maxlength="200" autocomplete="off" />
        <button type="submit" class="ajout__bouton" :disabled="!titreValide || ajout" :aria-busy="ajout || undefined">{{ t('cercles.tachesTour.ajouter') }}</button>
      </form>
      <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
    </template>
  </section>
</template>

<style scoped>
.taches { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-3); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.taches h3 { margin: 0; font-size: var(--type-body-size); }
.note { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-1); }
.tache {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-3);
  min-height: 44px; padding: var(--space-1) var(--space-2); border-radius: var(--radius-md);
}
.tache:hover { background: var(--surface-raised); }
.tache__case { width: 18px; height: 18px; margin: 0; accent-color: var(--proof); cursor: pointer; }
.tache__case:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.tache__titre { overflow-wrap: anywhere; cursor: pointer; }
.tache--livree .tache__titre { color: var(--ink-muted); text-decoration: line-through; }
.tache__qui { display: flex; align-items: center; gap: var(--space-2); }
/* Téléphone : qui s'en charge passe sous le titre, au lieu de pousser la ligne hors de la carte. */
@media (max-width: 480px) {
  .tache { grid-template-columns: auto minmax(0, 1fr); }
  .tache__qui { grid-column: 2; flex-wrap: wrap; }
}
.lien {
  padding: var(--space-1) var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-pill);
  background: transparent; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); cursor: pointer;
}
.lien:hover { border-color: var(--action); }
.lien:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.lien:disabled { opacity: 0.6; cursor: progress; }
.ajout { display: flex; gap: var(--space-2); }
.ajout__champ {
  flex: 1; min-width: 0; min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md);
  background: var(--surface-raised); color: var(--ink); font: inherit;
}
.ajout__champ:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.ajout__bouton {
  flex-shrink: 0; min-height: 44px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer;
}
.ajout__bouton:disabled { opacity: 0.5; cursor: not-allowed; }
.ajout__bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
/* Ayo propose une répartition : la même carte douce que ses conseils sur les demandes. */
.repartition { display: grid; gap: var(--space-3); padding: var(--space-3) var(--space-4); border-radius: var(--radius-lg); background: var(--action-soft); }
.repartition__tete { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.repartition__texte { flex: 1; min-width: 12rem; margin: 0; font-size: var(--type-body-sm-size); }
.repartition__liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.repartition__ligne { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-1) var(--space-3); align-items: center; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--surface); }
.repartition__quoi { overflow-wrap: anywhere; }
.repartition__pourquoi { grid-column: 1; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.repartition__ligne .lien { grid-column: 2; grid-row: 1 / span 2; }
.repartition__actions { display: flex; justify-content: flex-end; }
.lien--ayo { background: var(--surface); border-color: var(--action); color: var(--action-ink); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-size: var(--type-body-sm-size); font-weight: 600; }
.tache-move { transition: transform var(--motion-base) var(--ease-out); }
@media (prefers-reduced-motion: reduce) { .tache-move { transition: none; } }
</style>
