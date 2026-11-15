<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { JChamp } from '../../design-system'
import { lireDemande } from '../demandes/demandes'
import { prefillDepuisDemande } from './depuisDemande'
import { creerMission, DEVISES, formaterMontant, LIMITES, lireCompetences, versMontant, type NouvelleMission } from './missions'
import AssistantCreation from '../../components/creation/AssistantCreation.vue'
import EditeurQuestionnaire from '../questionnaires/EditeurQuestionnaire.vue'
import { nettoyer, problemes, type Question } from '../questionnaires/questionnaire'

/*
 * Publier une mission : on enregistre d'abord un brouillon (personne ne le voit), puis on le publie depuis la page de la
 * mission. Chaque champ en erreur est signalé en même temps, avec son code venu de l'API.
 * Une étape à la fois, avec aperçu en direct : les valeurs sont conservées quand on revient en arrière.
 */
const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()

/** La demande dont part la mission (`?demande=<id>`) : elle préremplit le brouillon, et la mission y est rattachée. */
const demandeId = ref<string | null>(null)
const demandeTitre = ref('')
const idDemande = typeof route.query.demande === 'string' ? route.query.demande : null
onMounted(async () => {
  if (!idDemande) return
  try {
    const demande = await lireDemande(idDemande)
    if (!demande) return
    const base = prefillDepuisDemande(demande, {
      objectifs: t('missions.nouvelle.depuisDemande.objectifs'),
      indispensables: t('missions.nouvelle.depuisDemande.indispensables'),
      utiles: t('missions.nouvelle.depuisDemande.utiles'),
      horsPerimetre: t('missions.nouvelle.depuisDemande.horsPerimetre'),
    })
    // Rien n'écrase ce que la personne a déjà commencé à écrire pendant le chargement.
    if (!titre.value) titre.value = base.titre
    if (!besoin.value) besoin.value = base.besoin
    if (!competences.value) competences.value = base.competences
    if (!ville.value) ville.value = base.ville
    demandeId.value = demande.id
    demandeTitre.value = demande.titre
  } catch {
    // La demande ne répond pas : le formulaire reste vide, la mission se crée sans elle.
  }
})

const titre = ref('')
const besoin = ref('')
const competences = ref('')
const budget = ref('')
const devise = ref<string>('XOF')
const date = ref('')
const ville = ref('')
const visibilite = ref<'publique' | 'invitation'>('publique')
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const champs = ref<Record<string, string>>({})
const questionnaire = ref<Question[]>([])

const valide = computed(
  () =>
    titre.value.trim().length >= LIMITES.titreMin &&
    besoin.value.trim().length >= LIMITES.besoinMin &&
    lireCompetences(competences.value).length > 0 &&
    !Object.keys(problemes(questionnaire.value)).length,
)

const etape = ref(0)
const cles = ['titre', 'besoin', 'competences', 'modalites', 'candidatures', 'apercu'] as const
const etapes = computed(() => cles.map((cle) => ({ nom: t(`creationGuidee.mission.${cle}.nom`), titre: t(`creationGuidee.mission.${cle}.titre`), aide: t(`creationGuidee.mission.${cle}.aide`) })))
const budgetTexte = computed(() => {
  const montant = budget.value.trim() ? versMontant(budget.value, devise.value) : null
  return montant ? formaterMontant(montant, locale.value) : t('creationGuidee.aDiscuter')
})
const montantValide = computed(() => !budget.value.trim() || versMontant(budget.value, devise.value) !== null)
const valides = computed(() => [
  titre.value.trim().length >= LIMITES.titreMin,
  besoin.value.trim().length >= LIMITES.besoinMin,
  lireCompetences(competences.value).length > 0,
  montantValide.value,
  !Object.keys(problemes(questionnaire.value)).length,
  valide.value && montantValide.value,
])
const ETAPE_CHAMP: Record<string, number> = { titre: 0, besoin: 1, competences: 2, budget: 3, dateSouhaitee: 3, ville: 3, visibilite: 4, questionnaire: 4 }
const revenirAuChamp = (champ: string) => { etape.value = ETAPE_CHAMP[champ.split(/[.[]/)[0]!] ?? 0 }

/** Le message d'un champ en erreur : un texte par champ, quel que soit le code. */
const erreur = (champ: string) => ((champs.value[champ] || (champ === 'budget' && !montantValide.value)) ? t(`missions.nouvelle.invalide.${champ in INVALIDES ? champ : 'autre'}`) : undefined)
const INVALIDES: Record<string, true> = { titre: true, besoin: true, competences: true, budget: true, dateSouhaitee: true, visibilite: true }

async function creer() {
  if (!valide.value || envoi.value === 'envoi') return
  champs.value = {}
  const montant = budget.value.trim() ? versMontant(budget.value, devise.value) : null
  if (budget.value.trim() && !montant) return void (champs.value = { budget: 'montant_invalide' })
  const mission: NouvelleMission = {
    ...(demandeId.value ? { demandeId: demandeId.value } : {}),
    titre: titre.value.trim(),
    besoin: besoin.value.trim(),
    competences: lireCompetences(competences.value),
    visibilite: visibilite.value,
    // Une mission est à distance par défaut (le contrat le dit) ; l'écran ne propose pas encore de choix.
    aDistance: true,
    ...(montant ? { budget: montant } : {}),
    ...(date.value ? { dateSouhaitee: date.value } : {}),
    ...(ville.value.trim() ? { ville: ville.value.trim() } : {}),
    ...(questionnaire.value.length ? { questionnaire: nettoyer(questionnaire.value) } : {}),
  }
  envoi.value = 'envoi'
  try {
    const r = await creerMission(mission)
    if (r.ok) return void (await router.push(`/missions/${r.valeur.id}`))
    champs.value = r.champs
    if (Object.keys(r.champs).length) revenirAuChamp(Object.keys(r.champs)[0]!)
    envoi.value = Object.keys(r.champs).length ? 'repos' : 'erreur'
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <AssistantCreation v-model:etape="etape" :titre="t('missions.nouvelle.titre')" :etapes="etapes" :valides="valides" retour="/missions" :retour-libelle="t('missions.detail.retour')" :action="t('missions.nouvelle.creer')" test-formulaire="nouvelle-mission" test-action="creer" :occupe="envoi === 'envoi'" @terminer="creer">
    <p v-if="demandeId" class="note" role="status" data-test="depuis-demande">{{ t('missions.nouvelle.depuisDemande.note', { titre: demandeTitre }) }}</p>
    <div v-if="etape === 0" class="question-principale"><JChamp v-model="titre" :libelle="t('missions.nouvelle.champTitre')" :aide="t('missions.nouvelle.aideTitre')" :erreur="erreur('titre')" :maxlength="LIMITES.titreMax" requis /><div class="pastilles"><button v-for="exemple in (['site', 'appli', 'design'] as const)" :key="exemple" type="button" class="pastille" @click="titre = t(`creationGuidee.mission.exemples.${exemple}`)">{{ t(`creationGuidee.mission.exemples.${exemple}`) }}</button></div></div>
    <div v-else-if="etape === 1" class="question-principale"><JChamp v-model="besoin" :libelle="t('missions.nouvelle.champBesoin')" :aide="t('missions.nouvelle.aideBesoin', { n: LIMITES.besoinMin })" :erreur="erreur('besoin')" multiligne :lignes="5" :maxlength="LIMITES.besoinMax" requis /></div>
    <div v-else-if="etape === 2" class="question-principale"><JChamp v-model="competences" :libelle="t('missions.nouvelle.champCompetences')" :aide="t('missions.nouvelle.aideCompetences')" :erreur="erreur('competences')" requis /><div class="pastilles"><button v-for="c in ['Vue, TypeScript', 'Flutter, Firebase', 'UX design, Figma']" :key="c" type="button" class="pastille" @click="competences = c">{{ c }}</button></div></div>
    <template v-else-if="etape === 3">
      <div class="grille-champs"><JChamp v-model="budget" :libelle="t('missions.nouvelle.budget')" :aide="t('missions.nouvelle.aideBudget')" :erreur="erreur('budget')" /><label class="devise"><span>{{ t('missions.nouvelle.devise') }}</span><select v-model="devise" data-test="devise"><option v-for="d in DEVISES" :key="d" :value="d">{{ d }}</option></select></label></div>
      <div class="grille-champs"><label class="date"><span>{{ t('missions.nouvelle.date') }}</span><input v-model="date" type="date" data-test="date" /><span v-if="erreur('dateSouhaitee')" role="alert" class="erreur">{{ erreur('dateSouhaitee') }}</span></label><JChamp v-model="ville" :libelle="t('missions.nouvelle.ville')" :maxlength="LIMITES.villeMax" /></div>
    </template>
    <template v-else-if="etape === 4"><fieldset class="choix"><legend>{{ t('missions.nouvelle.visibilite') }}</legend><label><input v-model="visibilite" type="radio" name="visibilite" value="publique" data-test="publique" /><span>{{ t('missions.nouvelle.publique') }}</span></label><label><input v-model="visibilite" type="radio" name="visibilite" value="invitation" data-test="invitation" /><span>{{ t('missions.nouvelle.invitation') }}</span></label></fieldset><div class="questionnaire"><EditeurQuestionnaire v-model="questionnaire" contexte="mission" /></div></template>
    <template v-else><dl class="recap" data-test="apercu-mission"><div><dt>{{ t('missions.nouvelle.champTitre') }}</dt><dd>{{ titre }}</dd></div><div><dt>{{ t('missions.nouvelle.champBesoin') }}</dt><dd>{{ besoin }}</dd></div><div><dt>{{ t('missions.nouvelle.champCompetences') }}</dt><dd>{{ lireCompetences(competences).join(', ') }}</dd></div><div><dt>{{ t('missions.nouvelle.budget') }}</dt><dd>{{ budgetTexte }}</dd></div><div><dt>{{ t('missions.nouvelle.date') }}</dt><dd>{{ date || t('creationGuidee.aDefinir') }}</dd></div><div><dt>{{ t('missions.nouvelle.ville') }}</dt><dd>{{ ville || t('creationGuidee.aDistance') }}</dd></div><div><dt>{{ t('missions.nouvelle.visibilite') }}</dt><dd>{{ t(`missions.nouvelle.${visibilite}`) }}</dd></div><div v-if="questionnaire.length"><dt>{{ t('questionnaire.editeur.titre') }}</dt><dd v-for="q in questionnaire" :key="q.id">{{ q.intitule }}</dd></div></dl><p class="texte-discret">{{ t('creationGuidee.brouillon') }}</p></template>
    <template #erreurs><p v-if="envoi === 'erreur'" role="alert" class="erreur" data-test="erreur">{{ t('missions.nouvelle.erreur') }}</p><ul v-if="Object.keys(champs).length" role="alert" class="erreurs-champs"><li v-for="champ in Object.keys(champs)" :key="champ"><button type="button" @click="revenirAuChamp(champ)">{{ erreur(champ) }}</button></li></ul></template>
    <template #apercu><h2>{{ titre || t('creationGuidee.mission.apercuVide') }}</h2><p class="apercu__texte">{{ besoin || t('missions.nouvelle.intro') }}</p><div v-if="lireCompetences(competences).length" class="etiquettes"><span v-for="c in lireCompetences(competences)" :key="c">{{ c }}</span></div><div class="apercu__info"><span>{{ budgetTexte }}</span><span>{{ ville || t('creationGuidee.aDistance') }}</span><span>{{ t(`missions.nouvelle.${visibilite}`) }}</span></div></template>
  </AssistantCreation>
</template>

<style scoped>
.nouvelle { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }

.budget, .choix { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.budget legend, .choix legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.budget legend { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.devise, .date { display: grid; gap: var(--space-2); font-weight: 600; max-width: 14rem; }
.devise select, .date input { min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: inherit; font: inherit; }
.choix label { display: flex; gap: var(--space-3); align-items: center; font-weight: 400; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.questionnaire { padding: var(--space-4); border-radius: var(--radius-md); border: 1px solid var(--line-strong); }
.erreurs-champs { margin: 0; padding-left: 18px; color: var(--danger); }
.erreurs-champs button { background: transparent; border: 0; padding: 4px 0; color: inherit; text-align: left; text-decoration: underline; cursor: pointer; }
</style>
