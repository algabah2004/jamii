<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { creerEquipe, LIMITES_EQUIPE, sInscrire, type Hackathon, type Resultat } from './hackathons'
import RemplirQuestionnaire from '../questionnaires/RemplirQuestionnaire.vue'
import { erreursParQuestion, focaliserQuestion, manquantes, versReponses, type Valeurs } from '../questionnaires/questionnaire'

/*
 * Participer à un hackathon (contracts/hackathons.yaml, `sInscrire` et `creerEquipe`) : s'inscrire seul, en se
 * signalant ou non comme « je cherche une équipe », ou créer directement son équipe (l'inscription suit).
 * Si l'organisation a posé des questions, on y répond d'abord : elles valent pour les deux chemins.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const emit = defineEmits<{ inscrit: [] }>()
const { t } = useI18n()

const chercheEquipe = ref(true)
const nom = ref('')
const envoi = ref(false)
const code = ref<string | null>(null)
const valeurs = ref<Valeurs>({})
const erreursQuestions = ref<Record<string, string>>({})

/** Les questions ne se posent qu'à qui n'est pas encore inscrit (un inscrit peut encore créer son équipe). */
const questions = computed(() => (peut('sinscrire') ? props.hackathon.questionnaire : []))
const nbManquantes = computed(() => Object.values(erreursQuestions.value).filter((c) => c === 'obligatoire').length)

const peut = (action: string) => (props.hackathon.moi.actions as string[]).includes(action)
const nomValide = computed(() => nom.value.trim().length >= LIMITES_EQUIPE.nomMin)
const message = computed(() => {
  if (!code.value) return ''
  if (code.value === 'validation' && Object.keys(erreursQuestions.value).length) {
    return t('questionnaire.remplir.manquantes', { n: nbManquantes.value }, Math.max(nbManquantes.value, 1))
  }
  const connus = ['deja_inscrit', 'delai_depasse', 'equipes_verrouillees', 'etat_invalide', 'validation']
  return t(`hackathons.inscription.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

async function agir(appel: () => Promise<Resultat<unknown>>) {
  if (envoi.value) return
  // Les réponses obligatoires sont signalées tout de suite ; l'API revérifie de toute façon.
  const vides = manquantes(questions.value, valeurs.value)
  erreursQuestions.value = Object.fromEntries(vides.map((id) => [id, 'obligatoire']))
  if (vides.length) {
    code.value = 'validation'
    focaliserQuestion(vides[0]!)
    return
  }
  envoi.value = true
  code.value = null
  try {
    const r = await appel()
    if (r.ok) emit('inscrit')
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

const reponses = () => versReponses(questions.value, valeurs.value)
const inscrire = () => agir(() => sInscrire(props.hackathon.id, chercheEquipe.value, reponses()))
function creer() {
  if (nomValide.value) void agir(() => creerEquipe(props.hackathon.id, nom.value.trim(), reponses()))
}
</script>

<template>
  <section class="carte participer" aria-labelledby="participer-titre" data-test="participer">
    <h2 id="participer-titre">{{ t('hackathons.inscription.titre') }}</h2>
    <p class="texte-discret">
      {{ hackathon.modeInscription === 'candidature' ? t('hackathons.inscription.introCandidature') : t('hackathons.inscription.intro') }}
    </p>

    <div v-if="questions.length" class="questions" data-test="questions-inscription">
      <h3>{{ t('questionnaire.remplir.titre') }}</h3>
      <RemplirQuestionnaire v-model="valeurs" :questions="questions" :erreurs="erreursQuestions" />
    </div>

    <div v-if="peut('sinscrire')" class="choix" data-test="inscription-seul">
      <h3>{{ t('hackathons.inscription.seul') }}</h3>
      <label class="case"><input v-model="chercheEquipe" type="checkbox" data-test="cherche-equipe" /> {{ t('hackathons.inscription.chercheEquipe') }}</label>
      <div>
        <JBouton :occupe="envoi" data-test="sinscrire" @click="inscrire">{{ t('hackathons.inscription.sinscrire') }}</JBouton>
      </div>
    </div>

    <form v-if="peut('sinscrire') || peut('creer_equipe')" class="choix" data-test="form-equipe" @submit.prevent="creer">
      <h3>{{ t('hackathons.inscription.equipe') }}</h3>
      <JChamp
        v-model="nom"
        :libelle="t('hackathons.inscription.nomEquipe')"
        :aide="t('hackathons.inscription.aideEquipe', { max: hackathon.tailleEquipe.max })"
        :maxlength="LIMITES_EQUIPE.nomMax"
        requis
      />
      <div>
        <JBouton type="submit" variante="secondaire" :occupe="envoi" :desactive="!nomValide" data-test="creer-equipe">
          {{ t('hackathons.inscription.creerEquipe') }}
        </JBouton>
      </div>
    </form>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-inscription">{{ message }}</p>
  </section>
</template>

<style scoped>
.participer { display: grid; gap: var(--space-4); padding: var(--space-8); }
.participer h2 { margin: 0; font-size: 1.25rem; }
.participer h3 { margin: 0; font-size: 1rem; }
.texte-discret { margin: 0; }
.choix { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.case { display: flex; gap: var(--space-2); align-items: center; min-height: 44px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.questions { display: grid; gap: var(--space-3); }
</style>
