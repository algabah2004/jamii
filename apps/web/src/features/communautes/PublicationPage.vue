<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JIllustration, JSquelette } from '../../design-system'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { ilYa } from '../vitrine/vitrine'
import { useSession } from '../../session'
import BoutonSignaler from './BoutonSignaler.vue'
import EspaceCommunautes from './EspaceCommunautes.vue'

type Publication = components['schemas']['Publication']
type Reponse = components['schemas']['Reponse']

/** Le contrat : relire toutes les 3 s tant que l'IA prépare sa réponse, au plus 1 minute. */
const INTERVALLE_IA_MS = 3000
const ATTENTE_IA_MAX_MS = 60_000

const { t, locale } = useI18n()
const { moi } = useSession()
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
  // Pas sur sa propre réponse : on ne se donne pas une preuve à soi-même (409 propre_reponse).
  return Boolean(p && !r.proposeeParIa && r.auteur && r.auteur.id !== p.auteur?.id && !dejaAcceptee.value
    && p.moi.estAuteur && p.type === 'question')
}

const MESSAGES_ERREUR = new Set([
  'pas_membre', 'pas_auteur_question', 'propre_question', 'propre_reponse', 'proposee_par_ia', 'deja_acceptee',
])
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
const zoneReponse = ref<HTMLElement | null>(null)
/** « Répondre » sous la question : amène à la zone de réponse et y place le curseur. */
function allerRepondre() {
  zoneReponse.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  zoneReponse.value?.querySelector('textarea')?.focus({ preventScroll: true })
}
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
  <EspaceCommunautes :actuelle="publication?.communaute">
    <JSquelette v-if="etat === 'chargement' && !publication" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="publication">
      <p class="retour"><RouterLink :to="`/communautes/${publication.communaute}`">{{ t('question.retourCommunaute') }}</RouterLink></p>

      <!-- Toute la discussion dans un seul panneau : le message, les réponses, puis la zone de réponse. -->
      <section class="discussion" :class="{ 'discussion--resolue': dejaAcceptee }">
      <!-- Le message d'origine. -->
      <article class="message-origine" aria-labelledby="titre-fil">
        <header class="auteur">
          <JAvatar :nom="publication.auteur?.nom ?? '?'" :url="publication.auteur?.avatarUrl" :taille="44" />
          <div class="auteur__texte">
            <strong>{{ publication.auteur?.nom ?? t('communautes.compteSupprime') }}</strong>
            <span>{{ ilYa(publication.creeLe, locale) }}</span>
          </div>
          <span class="etiquette">{{ t(`communautes.types.${publication.type}`) }}</span>
        </header>
        <h1 id="titre-fil">{{ publication.titre }}</h1>
        <!-- Texte brut, affiché échappé (jamais v-html) : aucun HTML d'un utilisateur ne s'exécute. -->
        <p class="texte">{{ publication.contenu }}</p>
        <footer class="barre">
          <JBadgePreuve v-if="dejaAcceptee">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
          <span v-else-if="publication.type === 'question'" class="statut-ouvert">{{ t('question.ouverte') }}</span>
          <span class="barre__espace" />
          <button v-if="publication.moi.estMembre" type="button" class="barre__action" @click="allerRepondre">{{ t('question.repondre') }}</button>
          <BoutonSignaler cible-type="publication" :cible-id="publication.id" />
        </footer>
      </article>

      <p v-if="message" role="status" class="annonce">{{ message }}</p>
      <p v-if="publication.propositionIa?.statut === 'en_attente' && !iaTropLongue" role="status" class="annonce annonce--ia" data-test="ia-attente">
        <span class="points-attente" aria-hidden="true"><i /><i /><i /></span> {{ t('question.iaPrepare') }}
      </p>
      <p v-else-if="publication.propositionIa?.statut === 'indisponible' || iaTropLongue" class="annonce annonce--discrete" data-test="ia-indisponible">
        {{ t('question.iaIndisponible') }}
      </p>

      <h2 class="separateur">{{ t('communautes.reponses', { n: reponses.length }, reponses.length) }}</h2>

      <!-- Le fil : chaque réponse est reliée à la suivante par une ligne, comme une conversation. -->
      <ol class="fil" data-test="reponses">
        <li v-for="r in reponses" :key="r.id" class="reponse" :class="{ acceptee: r.acceptee, ia: r.proposeeParIa }">
          <span v-if="r.proposeeParIa" class="avatar-ia" aria-hidden="true">✨</span>
          <JAvatar v-else :nom="r.auteur?.nom ?? '?'" :url="r.auteur?.avatarUrl" :taille="40" />
          <div class="bulle">
            <div class="bulle__tete">
              <p v-if="r.proposeeParIa" class="etiquette-ia" data-test="etiquette-ia">{{ t(r.valideePar ? 'question.proposeeParIaVerifiee' : 'question.proposeeParIa') }}</p>
              <p v-else class="auteur__texte">
                <strong>{{ r.auteur?.nom ?? t('communautes.compteSupprime') }}</strong>
                <span>{{ ilYa(r.creeLe, locale) }}</span>
              </p>
              <p v-if="r.acceptee" class="badge-acceptee" data-test="acceptee">
                <JBadgePreuve :celebrer="vientDEtreAcceptee === r.id">
                  ✓ {{ t('communautes.reponseAcceptee') }}<template v-if="r.valideePar"> · {{ t('question.valideePar', { nom: r.valideePar.nom }) }}</template>
                </JBadgePreuve>
              </p>
            </div>
            <p class="texte">{{ r.contenu }}</p>
            <p v-if="r.proposeeParIa && !r.acceptee && publication.moi.estAuteur" class="note">{{ t('question.attendUnPair') }}</p>
            <div class="bulle__actions">
              <JBouton v-if="peutValider(r)" class="principal" data-test="valider" @click="agir('valider', r.id)">
                {{ t('question.valider') }}
              </JBouton>
              <JBouton v-if="peutAccepter(r)" class="principal" data-test="accepter" @click="agir('accepter', r.id)">
                {{ t('question.accepter') }}
              </JBouton>
              <BoutonSignaler v-if="!r.proposeeParIa" cible-type="reponse" :cible-id="r.id" />
            </div>
          </div>
        </li>
      </ol>

      <form v-if="publication.moi.estMembre" ref="zoneReponse" class="compositeur" @submit.prevent="repondre">
        <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
        <div class="compositeur__corps">
          <JChamp v-model="nouvelleReponse" :libelle="t('question.taReponse')" multiligne :lignes="3" :maxlength="10000" requis />
          <JBouton type="submit" class="principal" :desactive="nouvelleReponse.trim().length < 10" :occupe="!!envoi">
            {{ t('question.repondre') }}
          </JBouton>
        </div>
      </form>
      <p v-else class="annonce annonce--discrete">{{ t('communautes.rejoindrePourPublier') }}</p>
      </section>
    </template>

    <template v-if="publication" #contexte>
      <section class="carte contexte" aria-labelledby="fonctionnement">
        <JIllustration nom="question" />
        <h2 id="fonctionnement">{{ t('communautes.fonctionnement.titre') }}</h2>
        <ol class="etapes">
          <li>{{ t('communautes.fonctionnement.poser') }}</li>
          <li>{{ t('communautes.fonctionnement.ia') }}</li>
          <li>{{ t('communautes.fonctionnement.accepter') }}</li>
        </ol>
      </section>
    </template>
  </EspaceCommunautes>
</template>

<style scoped>
.discussion { display: grid; gap: var(--space-4); padding-bottom: var(--space-4); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); overflow: hidden; }
.discussion > .annonce, .discussion > .separateur, .discussion > .fil { margin-inline: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.texte { white-space: pre-wrap; margin: 0; overflow-wrap: anywhere; }

.message-origine { display: grid; gap: var(--space-4); padding: var(--space-6) var(--space-6) 0; }
/* Orange = la preuve : une discussion résolue porte un liseré orange. */
.discussion--resolue { box-shadow: inset 4px 0 0 var(--proof); }
.message-origine h1 { margin: 0; font-size: clamp(1.5rem, 3vw, 1.875rem); line-height: 1.2; letter-spacing: -0.5px; }
.message-origine .texte { font-size: 1.0625rem; line-height: 1.65; }
.auteur { display: flex; align-items: center; gap: var(--space-3); }
.auteur .etiquette { margin-inline-start: auto; }
.auteur__texte { display: grid; line-height: 1.3; margin: 0; }
.auteur__texte strong { font-size: var(--type-body-sm-size); }
.auteur__texte span { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.barre { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.barre__espace { flex: 1; }
.barre__action { background: none; border: 0; padding: var(--space-1) var(--space-2); border-radius: var(--radius-sm); color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }
.barre__action:hover { background: var(--action-soft); }
.statut-ouvert { padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--action-soft); font-size: var(--type-label-size); font-weight: 600; }

.annonce { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--surface-raised); font-weight: 600; }
.annonce--ia { display: flex; align-items: center; gap: var(--space-3); background: var(--action-soft); }
.annonce--discrete { background: transparent; padding: 0; color: var(--ink-muted); font-weight: 400; font-size: var(--type-body-sm-size); }
/* L'IA écrit : trois points qui respirent (immobiles si le mouvement est réduit). */
.points-attente { display: inline-flex; gap: 4px; }
.points-attente i { width: 6px; height: 6px; border-radius: 50%; background: var(--action-ink); animation: respire 1.2s var(--ease-out) infinite; }
.points-attente i:nth-child(2) { animation-delay: 0.15s; }
.points-attente i:nth-child(3) { animation-delay: 0.3s; }
@keyframes respire { 50% { opacity: 0.25; transform: translateY(-2px); } }
@media (prefers-reduced-motion: reduce) { .points-attente i { animation: none; } }

.separateur { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-2) 0 0; font-size: 1rem; color: var(--ink-muted); }
.separateur::after { content: ''; flex: 1; height: 1px; background: var(--line); }

.fil { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.reponse { position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3); }
/* La ligne du fil, d'un avatar au suivant. */
.reponse:not(:last-child)::before { content: ''; position: absolute; left: 19px; top: 48px; bottom: calc(-1 * var(--space-4)); width: 2px; background: var(--line); border-radius: 2px; }
.avatar-ia { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: var(--action-soft); border: 1px dashed var(--line-strong); font-size: 1.125rem; }
.bulle { display: grid; gap: var(--space-2); justify-items: start; padding: var(--space-3) var(--space-4); background: var(--surface-raised); border: 1px solid transparent; border-radius: 4px var(--radius-lg) var(--radius-lg) var(--radius-lg); }
.bulle > * { margin: 0; }
.bulle .texte { justify-self: stretch; }
.bulle__tete { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-3); justify-self: stretch; }
.bulle__tete > * { margin: 0; }
.bulle__actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); justify-self: stretch; }
.bulle__actions:empty { display: none; }
.bulle__actions .signaler { margin-inline-start: auto; }
/* Charte : la proposition de l'IA est en pointillé, ce n'est pas encore une preuve. */
.reponse.ia .bulle { border: 1px dashed var(--line-strong); background: var(--action-soft); }
/* Orange = la preuve : la réponse acceptée. */
.reponse.acceptee .bulle { border: 2px solid var(--proof); }
.etiquette-ia { font-weight: 600; font-size: var(--type-body-sm-size); }
.note { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.compositeur { display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-4) var(--space-6) 0; border-top: 1px solid var(--line); }
.compositeur__corps { display: grid; gap: var(--space-3); justify-items: end; }
.compositeur__corps > :first-child { justify-self: stretch; }

.contexte { padding: var(--space-6); display: grid; gap: var(--space-3); }
.contexte h2 { margin: 0; font-size: 1.0625rem; }
.etapes { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-3); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.etapes ::marker { color: var(--ink); font-weight: 700; }
</style>
