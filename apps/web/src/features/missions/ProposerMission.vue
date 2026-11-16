<script setup lang="ts">
import IconeMission from './IconeMission.vue'
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { DEVISES, LIMITES_PROPOSITION, proposer, versMontant, type Mission } from './missions'
import RemplirQuestionnaire from '../questionnaires/RemplirQuestionnaire.vue'
import { erreursParQuestion, erreursRestantes, focaliserQuestion, manquantes, resumeErreurs, versReponses, type Valeurs } from '../questionnaires/questionnaire'

/*
 * Un développeur propose ses services (contracts/missions.yaml, `proposer`) : comment il s'y prendrait, son prix et
 * son délai, et ses réponses aux questions du client s'il en a posé. Le client est prévenu ; la page se recharge
 * ensuite pour montrer la proposition envoyée.
 */
const props = defineProps<{ mission: Mission }>()
const emit = defineEmits<{ envoyee: [] }>()
const { t } = useI18n()
const id = useId()

const message = ref('')
const prix = ref('')
const devise = ref<string>(props.mission.budget?.devise ?? 'XOF')
const delai = ref('')
const envoi = ref(false)
const code = ref<string | null>(null)
const valeurs = ref<Valeurs>({})
const erreursQuestions = ref<Record<string, string>>({})

const montant = computed(() => versMontant(prix.value, devise.value))
const jours = computed(() => {
  // Un champ `type="number"` rend en fait un nombre une fois rempli : on relit la saisie en texte.
  const texte = String(delai.value).trim()
  return /^\d+$/.test(texte) ? Number(texte) : null
})
const delaiValide = computed(() => jours.value !== null && jours.value >= LIMITES_PROPOSITION.delaiMin && jours.value <= LIMITES_PROPOSITION.delaiMax)
const pret = computed(() => message.value.trim().length >= LIMITES_PROPOSITION.messageMin && montant.value !== null && delaiValide.value)
const erreur = computed(() => {
  if (!code.value) return ''
  if (code.value === 'validation' && Object.keys(erreursQuestions.value).length) {
    const reste = resumeErreurs(erreursRestantes(props.mission.questionnaire, valeurs.value, erreursQuestions.value))
    // Tout est corrigé depuis le refus : plus rien à dire, le bouton suffit.
    if (!reste) return ''
    if (reste.manquantes) return t('questionnaire.remplir.manquantes', { n: reste.manquantes }, reste.manquantes)
  }
  const connus = ['deja_candidat', 'propre_mission', 'etat_invalide', 'limite_atteinte', 'validation']
  return t(`missions.proposition.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

async function envoyer() {
  if (!pret.value || !montant.value || jours.value === null || envoi.value) return
  const vides = manquantes(props.mission.questionnaire, valeurs.value)
  erreursQuestions.value = Object.fromEntries(vides.map((q) => [q, 'obligatoire']))
  if (vides.length) {
    code.value = 'validation'
    focaliserQuestion(vides[0]!)
    return
  }
  envoi.value = true
  code.value = null
  try {
    const reponses = versReponses(props.mission.questionnaire, valeurs.value)
    const r = await proposer(props.mission.id, { message: message.value.trim(), prix: montant.value, delaiJours: jours.value, ...(reponses.length ? { reponses } : {}) })
    if (r.ok) emit('envoyee')
    else {
      code.value = r.code ?? 'autre'
      erreursQuestions.value = erreursParQuestion(r.champs)
    }
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <form class="carte proposer m-bloc" aria-labelledby="proposer-titre" data-test="form-proposer" @submit.prevent="envoyer">
    <header class="m-tete">
      <IconeMission nom="proposer" />
      <div><h2 id="proposer-titre">{{ t('missions.proposition.proposer.titre') }}</h2><p>{{ t('missions.proposition.proposer.intro') }}</p></div>
    </header>
    <JChamp
      v-model="message"
      :libelle="t('missions.proposition.proposer.message')"
      :aide="t('missions.proposition.proposer.aideMessage', { n: LIMITES_PROPOSITION.messageMin })"
      multiligne
      requis
      :maxlength="LIMITES_PROPOSITION.messageMax"
    />
    <div class="ligne offre">
      <JChamp
        v-model="prix"
        :libelle="t('missions.proposition.proposer.prix')"
        :erreur="prix && !montant ? t('missions.proposition.proposer.prixInvalide') : undefined"
        requis
      />
      <div class="devise">
        <label :for="`${id}-devise`">{{ t('missions.nouvelle.devise') }}</label>
        <select :id="`${id}-devise`" v-model="devise">
          <option v-for="d in DEVISES" :key="d" :value="d">{{ d }}</option>
        </select>
      </div>
      <JChamp
        v-model="delai"
        type="number"
        :libelle="t('missions.proposition.proposer.delai')"
        :erreur="delai && !delaiValide ? t('missions.proposition.proposer.delaiInvalide') : undefined"
        requis
      />
    </div>
    <div v-if="mission.questionnaire.length" class="questions" data-test="questions-mission">
      <h3>{{ t('questionnaire.remplir.titreMission') }}</h3>
      <RemplirQuestionnaire v-model="valeurs" :questions="mission.questionnaire" :erreurs="erreursQuestions" />
    </div>
    <div>
      <JBouton type="submit" :occupe="envoi" :desactive="!pret" data-test="proposer">{{ t('missions.proposition.proposer.envoyer') }}</JBouton>
    </div>
    <p v-if="erreur" role="alert" class="erreur" data-test="erreur-proposer">{{ erreur }}</p>
  </form>
</template>

<style scoped>
.proposer { display: grid; gap: var(--space-5, 20px); }
/* Le prix et le délai : ce que le client compare d'abord, dans un encart teinté. */
.offre { padding: var(--space-4); border-radius: 14px; background: color-mix(in srgb, var(--action) 6%, var(--surface-raised)); }
.texte-discret { margin: 0; }
.ligne { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); align-items: start; }
.devise { display: grid; gap: var(--space-1); }
.devise label { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.devise select { min-height: 44px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.questions { display: grid; gap: var(--space-3); }
.questions h3 { margin: 0; font-size: 1.0625rem; }
</style>
