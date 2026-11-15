<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import { ajouterTache, changerTache, lireTaches, type Tache } from './cercles'
import { prenom } from './salon'

/*
 * Le tableau de bord du salon : ce que chacun a à faire dans le tour en cours, sans quitter la conversation.
 * - Mes tâches : celles que j'ai prises, à cocher quand elles sont finies.
 * - Les tâches libres : on en prend une d'un clic.
 * - L'équipe : où en est chacun (tâches livrées, point du jour), et lui écrire en privé.
 * Les tâches sont celles du projet du tour (contracts/projets.yaml) ; relues toutes les 15 secondes.
 */
type Personne = { id: string; nom: string; avatarUrl?: string | null }
const props = defineProps<{ projetId: string | null; membres: Personne[]; moiId: string | null; pointsDuJour: string[] }>()
const emit = defineEmits<{ maj: [taches: Tache[]] }>()
const { t } = useI18n()

const taches = ref<Tache[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
const enCours = ref<string | null>(null)
const erreur = ref(false)
const nouveauTitre = ref('')
const nouvelAssignee = ref('')
const ajoutEnCours = ref(false)
const statuts = ['a_faire', 'en_cours', 'livree'] as const

const miennes = computed(() =>
  taches.value
    .filter((x) => x.assignee?.id === props.moiId)
    .sort((a, b) => Number(a.statut === 'livree') - Number(b.statut === 'livree')),
)
const libres = computed(() => taches.value.filter((x) => !x.assignee && x.statut !== 'livree'))
const faites = computed(() => taches.value.filter((x) => x.statut === 'livree').length)
const pourcent = computed(() => (taches.value.length ? Math.round((faites.value / taches.value.length) * 100) : 0))
const parStatut = (statut: Tache['statut']) => taches.value.filter((x) => x.statut === statut)

/** Pour chaque membre : ses tâches livrées sur celles qu'il a prises, et s'il a fait son point aujourd'hui. */
const equipe = computed(() =>
  props.membres.map((p) => {
    const a = taches.value.filter((x) => x.assignee?.id === p.id)
    return { personne: p, total: a.length, livrees: a.filter((x) => x.statut === 'livree').length, point: props.pointsDuJour.includes(p.id) }
  }),
)

async function charger() {
  if (!props.projetId) return void (etat.value = 'pret')
  const liste = await lireTaches(props.projetId)
  if (!liste) {
    if (etat.value === 'chargement') etat.value = 'erreur'
    return
  }
  taches.value = liste
  etat.value = 'pret'
  emit('maj', liste)
}

let minuterie: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  void charger()
  minuterie = setInterval(() => void charger(), 15_000)
})
onBeforeUnmount(() => {
  if (minuterie) clearInterval(minuterie)
})

async function modifier(tache: Tache, changement: { statut?: Tache['statut']; assigneeId?: string | null }) {
  if (!props.projetId) return
  enCours.value = tache.id
  erreur.value = false
  const r = await changerTache(props.projetId, tache, changement)
  enCours.value = null
  if (!r.ok) {
    erreur.value = true
    return void charger()
  }
  taches.value = taches.value.map((x) => (x.id === r.tache.id ? r.tache : x))
  emit('maj', taches.value)
}
const cocher = (x: Tache) => modifier(x, { statut: x.statut === 'livree' ? 'en_cours' : 'livree' })
const prendre = (x: Tache) => modifier(x, { assigneeId: props.moiId, statut: 'en_cours' })

async function ajouter() {
  if (!props.projetId || nouveauTitre.value.trim().length < 3 || ajoutEnCours.value) return
  ajoutEnCours.value = true
  erreur.value = false
  const resultat = await ajouterTache(props.projetId, nouveauTitre.value.trim(), nouvelAssignee.value || null)
  ajoutEnCours.value = false
  if (!resultat.ok) return void (erreur.value = true)
  taches.value = [...taches.value, resultat.tache]
  nouveauTitre.value = ''
  nouvelAssignee.value = ''
  emit('maj', taches.value)
}
</script>

<template>
  <aside class="tableau" :aria-label="t('cercles.tableau.aria')" data-test="tableau-du-tour">
    <p v-if="!projetId" class="note">{{ t('cercles.tableau.pasDeTour') }}</p>
    <p v-else-if="etat === 'chargement'" class="note" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'erreur'" class="note" role="alert">{{ t('etat.indisponible') }}</p>

    <template v-else>
      <section class="bloc bloc--taches" aria-labelledby="tableau-toutes" data-test="toutes-les-taches">
        <div class="bloc__tete">
          <h3 id="tableau-toutes">{{ t('cercles.tableau.toutes') }}</h3>
          <span class="compte">{{ taches.length }}</span>
        </div>
        <div class="barre" role="progressbar" :aria-valuenow="pourcent" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.tableau.avancement')"><i :style="{ width: `${pourcent}%` }" /></div>
        <p class="note">{{ t('cercles.taches', { faites, total: taches.length }) }} · {{ pourcent }}%</p>
        <p v-if="!taches.length" class="note">{{ t('cercles.tableau.aucune') }}</p>
        <form class="nouvelle" data-test="nouvelle-tache-salon" @submit.prevent="ajouter">
          <label for="titre-tache-salon">{{ t('cercles.tableau.nouvelle') }}</label>
          <input id="titre-tache-salon" v-model="nouveauTitre" minlength="3" maxlength="200" required :placeholder="t('cercles.tableau.exemple')" />
          <label for="assignee-tache-salon">{{ t('cercles.tableau.responsable') }}</label>
          <select id="assignee-tache-salon" v-model="nouvelAssignee">
            <option value="">{{ t('cercles.tableau.nonAttribuee') }}</option>
            <option v-for="p in membres" :key="p.id" :value="p.id">{{ p.nom }}</option>
          </select>
          <button type="submit" class="pilule pilule--ajouter" :disabled="nouveauTitre.trim().length < 3 || ajoutEnCours">{{ t('cercles.tableau.ajouter') }}</button>
        </form>
        <div v-for="statut in statuts" :key="statut" v-show="parStatut(statut).length" class="groupe-taches">
          <h4>{{ t(`cercles.tableau.statut.${statut}`) }} <span class="compte">{{ parStatut(statut).length }}</span></h4>
          <ul class="liste">
            <li v-for="x in parStatut(statut)" :key="x.id" class="tache-detail">
              <strong>{{ x.titre }}</strong>
              <label>
                <span class="visuellement-cache">{{ t('cercles.tableau.responsable') }} — {{ x.titre }}</span>
                <select :value="x.assignee?.id ?? ''" :disabled="enCours === x.id" @change="modifier(x, { assigneeId: ($event.target as HTMLSelectElement).value || null })">
                  <option value="">{{ t('cercles.tableau.nonAttribuee') }}</option>
                  <option v-for="p in membres" :key="p.id" :value="p.id">{{ p.nom }}</option>
                </select>
              </label>
              <label>
                <span class="visuellement-cache">{{ t('cercles.tableau.etat') }} — {{ x.titre }}</span>
                <select :value="x.statut" :disabled="enCours === x.id" @change="modifier(x, { statut: ($event.target as HTMLSelectElement).value as Tache['statut'] })">
                  <option v-for="choix in statuts" :key="choix" :value="choix">{{ t(`cercles.tableau.statut.${choix}`) }}</option>
                </select>
              </label>
            </li>
          </ul>
        </div>
      </section>

      <section class="bloc" aria-labelledby="tableau-miennes" data-test="mes-taches">
        <h3 id="tableau-miennes">{{ t('cercles.tableau.miennes') }} <span class="compte">{{ miennes.filter((x) => x.statut !== 'livree').length }}</span></h3>
        <p v-if="!miennes.length" class="note">{{ t(taches.length ? 'cercles.tableau.aucuneMienne' : 'cercles.tableau.aucuneMienneSansTaches') }}</p>
        <ul v-else class="liste">
          <li v-for="x in miennes" :key="x.id" class="tache" :class="{ 'tache--livree': x.statut === 'livree' }">
            <input :id="`ma-tache-${x.id}`" type="checkbox" :checked="x.statut === 'livree'" :disabled="enCours === x.id" data-test="cocher-mienne" @change="cocher(x)" />
            <label :for="`ma-tache-${x.id}`">{{ x.titre }}</label>
          </li>
        </ul>
      </section>

      <section v-if="libres.length" class="bloc" aria-labelledby="tableau-libres" data-test="taches-libres">
        <h3 id="tableau-libres">{{ t('cercles.tableau.libres') }} <span class="compte">{{ libres.length }}</span></h3>
        <ul class="liste">
          <li v-for="x in libres.slice(0, 5)" :key="x.id" class="tache tache--libre">
            <span>{{ x.titre }}</span>
            <button type="button" class="pilule" :disabled="enCours === x.id || !moiId" data-test="prendre-libre" @click="prendre(x)">{{ t('cercles.tachesTour.prendre') }}</button>
          </li>
        </ul>
        <p v-if="libres.length > 5" class="note">{{ t('cercles.tableau.etAutres', { n: libres.length - 5 }) }}</p>
      </section>
      <p v-if="erreur" role="alert" class="erreur">{{ t('cercles.tachesTour.erreur') }}</p>
    </template>

    <section class="bloc" aria-labelledby="tableau-equipe" data-test="equipe">
      <h3 id="tableau-equipe">{{ t('cercles.tableau.equipe') }}</h3>
      <ul class="liste equipe">
        <li v-for="e in equipe" :key="e.personne.id" class="membre" data-test="membre-equipe">
          <LienPersonne :id="e.personne.id" doublon><JAvatar :nom="e.personne.nom" :url="e.personne.avatarUrl" :taille="32" /></LienPersonne>
          <span class="membre__qui">
            <LienPersonne :id="e.personne.id"><strong>{{ prenom(e.personne.nom) }}</strong></LienPersonne>
            <span class="note">
              {{ t('cercles.tableau.livrees', { n: e.livrees, total: e.total }) }}<template v-if="e.point"> · <span class="point">{{ t('cercles.tableau.pointFait') }}</span></template>
            </span>
          </span>
          <RouterLink v-if="e.personne.id !== moiId" :to="`/messages/${e.personne.id}`" class="pilule" :aria-label="t('reseau.profil.ecrire', { nom: prenom(e.personne.nom) })" data-test="ecrire-membre">
            {{ t('cercles.tableau.ecrire') }}
          </RouterLink>
          <span v-else class="note">{{ t('communautes.membresPage.toi') }}</span>
        </li>
      </ul>
    </section>
  </aside>
</template>

<style scoped>
.tableau { display: grid; gap: var(--space-4); align-content: start; position: sticky; top: 6.5rem; max-height: calc(100dvh - 7rem); overflow-y: auto; scrollbar-width: thin; }
.bloc { display: grid; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); }
.bloc h3 { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: var(--type-body-size); }
.bloc__tete { display: flex; align-items: center; justify-content: space-between; }
.bloc--taches { border-color: color-mix(in srgb, var(--action) 45%, var(--line)); }
.nouvelle { display: grid; gap: var(--space-2); padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface-raised); }
.nouvelle label { font-size: var(--type-body-sm-size); font-weight: 700; }
.nouvelle input, .nouvelle select, .tache-detail select { width: 100%; min-width: 0; }
.pilule--ajouter { justify-content: center; min-height: 40px; border-color: var(--action); background: var(--action); color: var(--on-action); }
.groupe-taches { display: grid; gap: var(--space-2); padding-top: var(--space-2); border-top: 1px solid var(--line); }
.groupe-taches h4 { display: flex; gap: var(--space-2); align-items: center; margin: 0; font-family: var(--font-display); font-size: var(--type-body-sm-size); }
.tache-detail { display: grid; gap: var(--space-2); padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); }
.tache-detail strong { overflow-wrap: anywhere; font-size: var(--type-body-sm-size); }
.tache-detail select { min-height: 38px; padding: var(--space-1) var(--space-2); font-size: var(--type-body-sm-size); }
.compte { min-width: 22px; padding: 0 6px; border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted); font-size: 0.75rem; text-align: center; }
.note { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.barre { height: 8px; border-radius: var(--radius-pill); background: var(--surface-raised); overflow: hidden; }
.barre i { display: block; height: 100%; background: var(--action); border-radius: inherit; transition: width var(--motion-base) var(--ease-out); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-1); }
.tache { display: flex; align-items: center; gap: var(--space-2); min-height: 36px; }
.tache input { width: 18px; height: 18px; margin: 0; accent-color: var(--proof); flex: none; cursor: pointer; }
.tache label { overflow-wrap: anywhere; cursor: pointer; }
.tache--livree label { color: var(--ink-muted); text-decoration: line-through; }
.tache--libre { justify-content: space-between; }
.tache--libre span { overflow-wrap: anywhere; min-width: 0; }
.membre { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-2); min-height: 44px; }
.membre__qui { display: grid; min-width: 0; }
.point { color: var(--proof-ink); font-weight: 600; }
.pilule {
  flex: none; display: inline-flex; align-items: center; min-height: 32px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill);
  background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; text-decoration: none; cursor: pointer;
}
.pilule:hover { border-color: var(--action); }
.pilule:disabled { opacity: 0.6; cursor: progress; }
.pilule:focus-visible, .tache input:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }
@media (prefers-reduced-motion: reduce) { .barre i { transition: none; } }
@media (max-width: 1000px) { .tableau { order: -1; position: static; max-height: min(60dvh, 35rem); overflow-y: auto; } }
</style>
