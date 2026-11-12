<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp } from '../../design-system'
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
/** La réponse qui vient d'être validée ou acceptée : son badge apparaît avec la célébration de la charte. */
const vientDEtreAcceptee = ref<string | null>(null)
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
    vientDEtreAcceptee.value = reponseId
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
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <article v-else-if="publication">
      <p><RouterLink :to="`/communautes/${publication.communaute}`">{{ t('question.retourCommunaute') }}</RouterLink></p>
      <h1>{{ publication.titre }}</h1>
      <p class="auteur">
        <JAvatar :nom="publication.auteur?.nom ?? '?'" :url="publication.auteur?.avatarUrl" :taille="32" />
        <span class="discret"><strong>{{ publication.auteur?.nom ?? t('communautes.compteSupprime') }}</strong> · {{ ilYa(publication.creeLe, locale) }}</span>
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
            <JBadgePreuve :celebrer="vientDEtreAcceptee === r.id">
              ✓ {{ t('communautes.reponseAcceptee') }}<template v-if="r.valideePar"> · {{ t('question.valideePar', { nom: r.valideePar.nom }) }}</template>
            </JBadgePreuve>
          </p>
          <p v-if="r.proposeeParIa" class="etiquette-ia" data-test="etiquette-ia">✨ {{ t(r.valideePar ? 'question.proposeeParIaVerifiee' : 'question.proposeeParIa') }}</p>
          <p v-else class="auteur">
            <JAvatar :nom="r.auteur?.nom ?? '?'" :url="r.auteur?.avatarUrl" :taille="28" />
            <span class="discret"><strong>{{ r.auteur?.nom ?? t('communautes.compteSupprime') }}</strong> · {{ ilYa(r.creeLe, locale) }}</span>
          </p>
          <p class="texte">{{ r.contenu }}</p>
          <p v-if="r.proposeeParIa && !r.acceptee && publication.moi.estAuteur" class="discret">{{ t('question.attendUnPair') }}</p>
          <JBouton v-if="peutValider(r)" class="principal" data-test="valider" @click="agir('valider', r.id)">
            {{ t('question.valider') }}
          </JBouton>
          <JBouton v-if="peutAccepter(r)" class="principal" data-test="accepter" @click="agir('accepter', r.id)">
            {{ t('question.accepter') }}
          </JBouton>
        </li>
      </ul>

      <form v-if="publication.moi.estMembre" class="formulaire" @submit.prevent="repondre">
        <JChamp v-model="nouvelleReponse" :libelle="t('question.taReponse')" multiligne :maxlength="10000" requis />
        <JBouton type="submit" class="principal" :desactive="nouvelleReponse.trim().length < 10" :occupe="!!envoi">
          {{ t('question.repondre') }}
        </JBouton>
      </form>
      <p v-else class="discret">{{ t('communautes.rejoindrePourPublier') }}</p>
    </article>
  </section>
</template>

<style scoped>
.texte { white-space: pre-wrap; }
.discret { color: var(--ink-muted); font-size: 0.875rem; }
.message { background: var(--surface-raised); border-radius: 0.375rem; padding: 0.5rem 0.75rem; }
.attente { background: var(--surface-raised); border-radius: 0.375rem; padding: 0.75rem; font-weight: 600; }
.reponses { list-style: none; padding: 0; margin: 0 0 1.5rem; display: grid; gap: 0.75rem; }
.reponses li { background: var(--surface); border: 1px solid var(--line); border-radius: 0.5rem; padding: 1rem; }
.reponses li.ia { border: 1px dashed var(--line-strong); background: var(--action-soft); } /* charte : proposition de l'IA, pas encore une preuve */
/* Orange = la preuve (tokens.json) : la réponse acceptée. */
.reponses li.acceptee { border: 2px solid var(--proof); border-style: solid; }
.badge-acceptee { margin: 0 0 var(--space-2); }
.auteur { display: flex; align-items: center; gap: var(--space-2); }
.auteur strong { color: var(--ink); }
.etiquette-ia { font-weight: 600; font-size: 0.875rem; margin: 0 0 0.5rem; }
.formulaire { display: grid; gap: 0.75rem; }
.formulaire .principal { justify-self: start; }
</style>
