<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { ilYa } from '../vitrine/vitrine'

type Publication = components['schemas']['Publication']
type Reponse = components['schemas']['Reponse']

/** Le contrat : relire toutes les 3 s tant que l'IA prépare sa réponse, au plus 1 minute. */
const INTERVALLE_IA_MS = 3000
const ATTENTE_IA_MAX_MS = 60_000

const { t, locale } = useI18n()
const route = useRoute()
const id = computed(() => String(route.params.id))

const publication = ref<Publication | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const message = ref<string | null>(null)
/** Vrai quand l'IA n'a pas répondu en une minute : on arrête de relire, et on le dit. */
const iaTropLongue = ref(false)

let minuterie: ReturnType<typeof setTimeout> | undefined
let debutAttente = 0

async function charger(silencieux = false) {
  if (!silencieux) etat.value = 'chargement'
  try {
    const { data, response } = await api.GET('/api/publications/{publicationId}', {
      params: { path: { publicationId: id.value } },
    })
    if (response.status === 404) return void (etat.value = 'introuvable')
    if (!data) throw new Error('publication indisponible')
    publication.value = data
    etat.value = 'pret'
  } catch {
    if (!silencieux) etat.value = 'indisponible'
  }
  surveillerIa()
}

/** Tant que l'IA prépare sa réponse, on relit la question, sans jamais dépasser une minute. */
function surveillerIa() {
  clearTimeout(minuterie)
  if (publication.value?.propositionIa?.statut !== 'en_attente') return
  if (!debutAttente) debutAttente = Date.now()
  if (Date.now() - debutAttente >= ATTENTE_IA_MAX_MS) return void (iaTropLongue.value = true)
  minuterie = setTimeout(() => void charger(true), INTERVALLE_IA_MS)
}
onBeforeUnmount(() => clearTimeout(minuterie))
watch(id, () => {
  debutAttente = 0
  iaTropLongue.value = false
  void charger()
}, { immediate: true })

/** La réponse acceptée d'abord, puis celle de l'IA, puis les autres (ordre du contrat). */
const reponses = computed(() => publication.value?.reponses ?? [])
const dejaAcceptee = computed(() => Boolean(publication.value?.reponseAccepteeId))

// Les boutons suivent les règles du contrat. Le serveur les revérifie de toute façon.
function peutValider(r: Reponse) {
  const p = publication.value
  return Boolean(p && r.proposeeParIa && !dejaAcceptee.value && p.moi.estMembre && !p.moi.estAuteur)
}
function peutAccepter(r: Reponse) {
  const p = publication.value
  return Boolean(p && !r.proposeeParIa && r.auteur && !dejaAcceptee.value && p.moi.estAuteur && p.type === 'question')
}

const MESSAGES_ERREUR = new Set(['pas_membre', 'pas_auteur_question', 'propre_question', 'deja_acceptee'])
async function agir(action: 'valider' | 'accepter', reponseId: string) {
  message.value = null
  const chemin = action === 'valider' ? '/api/reponses/{reponseId}/valider' : '/api/reponses/{reponseId}/accepter'
  const { data, error } = await api.POST(chemin, { params: { path: { reponseId } } })
  if (data) {
    publication.value = data
    message.value = t(action === 'valider' ? 'question.merciValidation' : 'question.merciAcceptation')
    return
  }
  const code = (error as { code?: string } | undefined)?.code ?? ''
  message.value = t(MESSAGES_ERREUR.has(code) ? `question.erreurs.${code}` : 'question.erreurs.autre')
}

const nouvelleReponse = ref('')
const envoi = ref(false)
async function repondre() {
  if (nouvelleReponse.value.trim().length < 10) return
  envoi.value = true
  const { data } = await api.POST('/api/publications/{publicationId}/reponses', {
    params: { path: { publicationId: id.value } },
    body: { contenu: nouvelleReponse.value.trim() },
  })
  envoi.value = false
  if (!data) return void (message.value = t('question.erreurs.autre'))
  nouvelleReponse.value = ''
  await charger(true)
}
</script>

<template>
  <section>
    <p v-if="etat === 'chargement' && !publication" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'introuvable'">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <button type="button" @click="charger()">{{ t('etat.reessayer') }}</button>
    </div>

    <article v-else-if="publication">
      <p><RouterLink :to="`/communautes/${publication.communaute}`">{{ t('question.retourCommunaute') }}</RouterLink></p>
      <h1>{{ publication.titre }}</h1>
      <p class="discret">
        {{ publication.auteur?.nom ?? t('communautes.compteSupprime') }} · {{ ilYa(publication.creeLe, locale) }}
      </p>
      <!-- Texte brut, affiché échappé (jamais v-html) : aucun HTML d'un utilisateur ne s'exécute. -->
      <p class="texte">{{ publication.contenu }}</p>

      <p v-if="message" role="status" class="message">{{ message }}</p>

      <p v-if="publication.propositionIa?.statut === 'en_attente' && !iaTropLongue" role="status" class="attente" data-test="ia-attente">
        ✨ {{ t('question.iaPrepare') }}
      </p>
      <p v-else-if="publication.propositionIa?.statut === 'indisponible' || iaTropLongue" class="discret" data-test="ia-indisponible">
        {{ t('question.iaIndisponible') }}
      </p>

      <h2>{{ t('communautes.reponses', { n: reponses.length }, reponses.length) }}</h2>
      <ul class="reponses" data-test="reponses">
        <li v-for="r in reponses" :key="r.id" :class="{ acceptee: r.acceptee, ia: r.proposeeParIa }">
          <p v-if="r.acceptee" class="badge-acceptee" data-test="acceptee">
            ✓ {{ t('communautes.reponseAcceptee') }}<template v-if="r.valideePar"> · {{ t('question.valideePar', { nom: r.valideePar.nom }) }}</template>
          </p>
          <p v-if="r.proposeeParIa" class="etiquette-ia" data-test="etiquette-ia">✨ {{ t(r.valideePar ? 'question.proposeeParIaVerifiee' : 'question.proposeeParIa') }}</p>
          <p v-else class="discret">{{ r.auteur?.nom ?? t('communautes.compteSupprime') }} · {{ ilYa(r.creeLe, locale) }}</p>
          <p class="texte">{{ r.contenu }}</p>
          <p v-if="r.proposeeParIa && !r.acceptee && publication.moi.estAuteur" class="discret">{{ t('question.attendUnPair') }}</p>
          <button v-if="peutValider(r)" type="button" class="principal" data-test="valider" @click="agir('valider', r.id)">
            {{ t('question.valider') }}
          </button>
          <button v-if="peutAccepter(r)" type="button" class="principal" data-test="accepter" @click="agir('accepter', r.id)">
            {{ t('question.accepter') }}
          </button>
        </li>
      </ul>

      <form v-if="publication.moi.estMembre" class="formulaire" @submit.prevent="repondre">
        <label>
          {{ t('question.taReponse') }}
          <textarea v-model="nouvelleReponse" rows="4" maxlength="10000" required />
        </label>
        <button type="submit" class="principal" :disabled="nouvelleReponse.trim().length < 10 || envoi">
          {{ t('question.repondre') }}
        </button>
      </form>
      <p v-else class="discret">{{ t('communautes.rejoindrePourPublier') }}</p>
    </article>
  </section>
</template>

<style scoped>
.texte { white-space: pre-wrap; }
.discret { color: #4a5a65; font-size: 0.875rem; }
.message { background: #eaf1f5; border-radius: 0.375rem; padding: 0.5rem 0.75rem; }
.attente { background: #eaf1f5; border-radius: 0.375rem; padding: 0.75rem; font-weight: 600; }
.reponses { list-style: none; padding: 0; margin: 0 0 1.5rem; display: grid; gap: 0.75rem; }
.reponses li { background: #fff; border: 1px solid #d3dee5; border-radius: 0.5rem; padding: 1rem; }
.reponses li.ia { border-style: dashed; }
/* Orange = la preuve (tokens.json) : la réponse acceptée. */
.reponses li.acceptee { border: 2px solid #fc734d; border-style: solid; }
.badge-acceptee { display: inline-block; background: #fc734d; color: #02070a; font-weight: 600; font-size: 0.875rem; padding: 0.125rem 0.5rem; border-radius: 999px; margin: 0 0 0.5rem; }
.etiquette-ia { font-weight: 600; font-size: 0.875rem; margin: 0 0 0.5rem; }
.principal { background: #0297de; color: #02070a; border: 0; border-radius: 0.375rem; padding: 0.5rem 1rem; font-weight: 600; cursor: pointer; }
.principal:disabled { opacity: 0.5; cursor: not-allowed; }
.formulaire { display: grid; gap: 0.75rem; }
.formulaire label { display: grid; gap: 0.25rem; font-weight: 600; }
.formulaire textarea { font: inherit; padding: 0.5rem; border: 1px solid #8a9aa5; border-radius: 0.375rem; }
.formulaire .principal { justify-self: start; }
</style>
