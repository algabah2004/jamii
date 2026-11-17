<script setup lang="ts">
import BoutonSignaler from './BoutonSignaler.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar } from '../../design-system'
import { ilYa } from '../vitrine/vitrine'

type PublicationResume = components['schemas']['PublicationResume']
type Publication = components['schemas']['Publication']
type Reponse = components['schemas']['Reponse']

/*
 * Une publication dans le fil, comme sur un réseau social : le texte en entier, les réponses qui s'ouvrent en dessous,
 * et la réponse qu'on écrit sans quitter le fil. Accepter une réponse se fait ici aussi, avec
 * les règles du contrat (le serveur les revérifie). Les textes sont affichés échappés (jamais v-html).
 */
const props = defineProps<{ publication: PublicationResume; membre: boolean }>()
const emit = defineEmits<{ maj: [publication: PublicationResume] }>()
const { t, locale } = useI18n()

const p = computed(() => props.publication)
/** Une publication écrite dans le fil a pour titre le début de son texte : on ne le répète pas. */
// Le titre n'est montré que s'il dit autre chose que le début du texte (sans les marques de mise en forme). Une
// publication faite seulement d'une image a un titre écrit pour elle (« Awa a partagé une image ») : on le tait.
const sansMarques = (texte: string) => texte.replace(/[*_~`>#]/g, '').replace(/\s+/g, ' ').trim()
const titreSepare = computed(() => !!p.value.contenu.trim() && !sansMarques(p.value.contenu).startsWith(sansMarques(p.value.titre.replace(/…$/, ''))))
const long = computed(() => p.value.contenu.length > 420)
const deplie = ref(false)

const ouvert = ref(false)
const detail = ref<Publication | null>(null)
const etat = ref<'repos' | 'chargement' | 'indisponible'>('repos')
async function chargerFil() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/publications/{publicationId}', { params: { path: { publicationId: p.value.id } } })
    if (!data) throw new Error('fil indisponible')
    detail.value = data
    etat.value = 'repos'
    emit('maj', { ...p.value, nombreReponses: data.nombreReponses, accepteeLe: data.accepteeLe })
  } catch {
    etat.value = 'indisponible'
  }
}
async function basculer() {
  ouvert.value = !ouvert.value
  if (ouvert.value && !detail.value) await chargerFil()
}

const zone = ref<HTMLTextAreaElement | null>(null)
async function repondreIci() {
  if (!ouvert.value) await basculer()
  await nextTick()
  zone.value?.focus()
}

const texte = ref('')
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const valide = computed(() => texte.value.trim().length >= 10)
async function repondre() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const { data } = await api.POST('/api/publications/{publicationId}/reponses', {
      params: { path: { publicationId: p.value.id } },
      body: { contenu: texte.value.trim() },
    })
    if (!data) return void (envoi.value = 'erreur')
    texte.value = ''
    envoi.value = 'repos'
    await chargerFil()
  } catch {
    envoi.value = 'erreur'
  }
}
function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    void repondre()
  }
}

// Les règles de l'entraide (contrat) : l'auteur de la question accepte une réponse. Le serveur les revérifie de toute façon.
const dejaAcceptee = computed(() => Boolean(detail.value?.reponseAccepteeId))
const peutAccepter = (r: Reponse) => Boolean(detail.value && r.auteur && r.auteur.id !== detail.value.auteur?.id
  && !dejaAcceptee.value && detail.value.moi.estAuteur && detail.value.type === 'question')
const message = ref<string | null>(null)
const ERREURS = new Set(['pas_membre', 'pas_auteur_question', 'propre_reponse', 'deja_acceptee'])
async function accepter(reponseId: string) {
  message.value = null
  const { data, error } = await api.POST('/api/reponses/{reponseId}/accepter', { params: { path: { reponseId } } })
  if (data) {
    detail.value = data
    emit('maj', { ...p.value, nombreReponses: data.nombreReponses, accepteeLe: data.accepteeLe })
    message.value = t('question.merciAcceptation')
    return
  }
  const code = (error as { code?: string } | undefined)?.code ?? ''
  message.value = t(ERREURS.has(code) ? `question.erreurs.${code}` : 'question.erreurs.autre')
}

const copie = ref(false)
async function copierLien() {
  try {
    await navigator.clipboard.writeText(new URL(`/publications/${p.value.id}`, window.location.origin).toString())
    copie.value = true
    setTimeout(() => (copie.value = false), 1800)
  } catch {
    copie.value = false
  }
}
</script>

<template>
  <article class="publication" :aria-labelledby="`auteur-${p.id}`" data-test="publication">
    <LienPersonne :id="p.auteur?.id" doublon class="publication__avatar"><JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="44" /></LienPersonne>
    <div class="publication__corps">
      <header class="tete">
        <strong :id="`auteur-${p.id}`"><LienPersonne :id="p.auteur?.id">{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</LienPersonne></strong>
        <span class="tete__quand">· {{ ilYa(p.creeLe, locale) }}</span>
        <span class="type" :class="`type--${p.type}`">{{ t(`communautes.types.${p.type}`) }}</span>
      </header>

      <p v-if="titreSepare" class="titre">{{ p.titre }}</p>
      <TexteRiche v-if="p.contenu" class="texte" :class="{ 'texte--coupe': long && !deplie }" :texte="p.contenu" data-test="texte" />
      <PiecesJointes :pieces="p.piecesJointes ?? []" class="medias" />
      <button v-if="long" type="button" class="lien" @click="deplie = !deplie">{{ deplie ? t('communautes.fil.voirMoins') : t('communautes.fil.voirPlus') }}</button>

      <p v-if="p.accepteeLe" class="statut statut--resolue">✓ {{ t('communautes.reponseAcceptee') }}</p>

      <footer class="actions">
        <button type="button" class="action" :aria-expanded="ouvert" :data-test="`reponses-${p.id}`" @click="basculer">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
          <span>{{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}</span>
        </button>
        <button v-if="membre" type="button" class="action" :data-test="`repondre-${p.id}`" @click="repondreIci">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M8 5 3 10l5 5M3.5 10H12a5 5 0 0 1 5 5v1" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <span>{{ t('communautes.fil.repondre') }}</span>
        </button>
        <button type="button" class="action" @click="copierLien">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M8.5 11.5a3 3 0 0 0 4.2 0l2.8-2.8a3 3 0 0 0-4.2-4.2l-.9.9M11.5 8.5a3 3 0 0 0-4.2 0L4.5 11.3a3 3 0 0 0 4.2 4.2l.9-.9" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" /></svg>
          <span role="status">{{ copie ? t('communautes.fil.lienCopie') : t('communautes.fil.copierLien') }}</span>
        </button>
        <BoutonSignaler cible-type="publication" :cible-id="p.id" integre />
      </footer>

      <section v-if="ouvert" class="fil" :aria-label="t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses)" :data-test="`fil-${p.id}`">
        <p v-if="etat === 'chargement' && !detail" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
        <p v-else-if="etat === 'indisponible'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>
        <ol v-if="detail?.reponses.length" class="reponses">
          <li v-for="r in detail.reponses" :key="r.id" class="reponse" :class="{ 'reponse--acceptee': r.acceptee }" data-test="reponse">
            <LienPersonne :id="r.auteur?.id" doublon><JAvatar :nom="r.auteur?.nom ?? '?'" :url="r.auteur?.avatarUrl" :taille="32" /></LienPersonne>
            <div class="reponse__corps">
              <p class="reponse__tete">
                <strong><LienPersonne :id="r.auteur?.id">{{ r.auteur?.nom ?? t('communautes.compteSupprime') }}</LienPersonne></strong>
                <span class="tete__quand">· {{ ilYa(r.creeLe, locale) }}</span>
                <span v-if="r.acceptee" class="pastille pastille--acceptee">✓ {{ t('communautes.fil.acceptee') }}</span>
              </p>
              <TexteRiche class="reponse__texte" :texte="r.contenu" />
              <div class="reponse__commandes">
                <button v-if="peutAccepter(r)" type="button" class="petit" :data-test="`accepter-${r.id}`" @click="accepter(r.id)">{{ t('communautes.fil.accepter') }}</button>
                <BoutonSignaler cible-type="reponse" :cible-id="r.id" integre />
              </div>
            </div>
          </li>
        </ol>
        <p v-else-if="detail" class="texte-discret">{{ t('communautes.espace.premier') }}</p>
        <p v-if="message" role="status" class="message">{{ message }}</p>

        <form v-if="membre" class="repondre" :data-test="`form-reponse-${p.id}`" @submit.prevent="repondre">
          <label class="visuellement-cache" :for="`repondre-${p.id}`">{{ t('communautes.fil.tonRepondre') }}</label>
          <textarea :id="`repondre-${p.id}`" ref="zone" v-model="texte" rows="2" :maxlength="10000" :placeholder="t('communautes.fil.tonRepondre')" @keydown="touche" />
          <div class="repondre__bas">
            <span class="texte-discret" :class="{ ok: valide }">{{ valide ? t('communautes.fil.raccourci') : t('communautes.fil.minimum') }}</span>
            <button type="submit" class="publier" :disabled="!valide || envoi === 'envoi'">{{ t('communautes.fil.repondre') }}</button>
          </div>
          <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('question.erreurs.autre') }}</p>
        </form>
      </section>
    </div>
  </article>
</template>

<style scoped>
/* Une publication : pas de carte qui grandit au survol, juste une ligne de séparation et un fond très léger. */
.publication { display: grid; grid-template-columns: 44px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-4) var(--space-6); }
.publication + .publication { border-top: 1px solid var(--line); }
.publication__corps { display: grid; gap: var(--space-2); min-width: 0; }
.tete { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px var(--space-2); }
.tete strong { font-size: 0.9375rem; }
.tete__quand { color: var(--ink-muted); font-size: 0.875rem; }
.type { margin-inline-start: auto; padding: 2px 10px; border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; background: var(--surface-raised); color: var(--ink-muted); }
.type--question { background: color-mix(in srgb, var(--electric-blue) 14%, transparent); color: var(--action-ink); }
.type--montre_ton_projet { background: color-mix(in srgb, var(--cantaloupe) 18%, transparent); color: var(--proof-ink); }
.titre { margin: 0; font-weight: 700; font-size: 1.0625rem; line-height: 1.35; }
.texte { margin: 0; }
/* Un long texte se replie à une dizaine de lignes, avec un fondu en bas ; « Voir plus » le déplie. */
.texte--coupe { max-height: 15rem; overflow: hidden; -webkit-mask-image: linear-gradient(to bottom, #000 70%, transparent); mask-image: linear-gradient(to bottom, #000 70%, transparent); }
.medias { margin-top: var(--space-1); }
.lien { justify-self: start; padding: 0; border: 0; background: none; color: var(--action-ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }
.statut { margin: 0; font-size: 0.8125rem; font-weight: 600; }
.statut--resolue { color: #1a7f45; }

.actions { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-6); margin-top: var(--space-1); }
.action {
  display: inline-flex; align-items: center; gap: 6px; min-height: 34px; padding: 0 var(--space-2); margin-inline-start: calc(-1 * var(--space-2));
  border: 0; border-radius: var(--radius-pill); background: none; color: var(--ink-muted); font: inherit; font-size: 0.875rem; cursor: pointer;
  transition: color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.action:hover, .action[aria-expanded='true'] { color: var(--action-ink); background: color-mix(in srgb, var(--electric-blue) 10%, transparent); }
.action:focus-visible, .lien:focus-visible, .petit:focus-visible, .publier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.fil { display: grid; gap: var(--space-3); margin-top: var(--space-2); padding-top: var(--space-3); border-top: 1px dashed var(--line); }
.reponses { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.reponse { display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: var(--space-3); }
.reponse__corps { display: grid; gap: 4px; min-width: 0; }
.reponse__tete { margin: 0; display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px var(--space-2); font-size: 0.875rem; }
.reponse__texte { margin: 0; }
.reponse--acceptee .reponse__texte { padding-inline-start: var(--space-3); border-inline-start: 3px solid #23a55a; }
.pastille { padding: 1px 8px; border-radius: var(--radius-pill); font-size: 0.6875rem; font-weight: 700; }
.pastille--acceptee { background: color-mix(in srgb, #23a55a 16%, transparent); color: #1a7f45; }
.reponse__commandes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin-top: 2px; }
.petit { min-height: 30px; padding: 0 var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); background: transparent; color: var(--ink); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer; }
.petit:hover { background: var(--ink); color: var(--bg); border-color: var(--ink); }
.message { margin: 0; color: var(--action-ink); font-weight: 600; font-size: var(--type-body-sm-size); }

.repondre { display: grid; gap: var(--space-2); }
.repondre textarea {
  width: 100%; min-height: 64px; resize: vertical; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md);
  background: var(--surface-raised); color: var(--ink); font: inherit; line-height: 1.5;
}
.repondre textarea:focus { outline: none; border-color: var(--electric-blue); }
.repondre__bas { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.repondre__bas .ok { color: var(--ink-muted); }
.publier {
  min-height: 36px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill); background: var(--electric-blue); color: #fff;
  font: inherit; font-weight: 700; font-size: 0.875rem; cursor: pointer;
}
.publier:disabled { opacity: 0.45; cursor: default; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
:root[data-theme='sombre'] .statut--resolue, :root[data-theme='sombre'] .pastille--acceptee { color: #57d68d; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .statut--resolue, :root:not([data-theme='clair']) .pastille--acceptee { color: #57d68d; } }
/* Téléphone : l'avatar reste à côté du nom, mais le texte reprend toute la largeur au lieu de rester décalé sous lui. */
@media (max-width: 640px) {
  .publication { grid-template-columns: minmax(0, 1fr); padding: var(--space-4); }
  .publication__avatar { grid-area: 1 / 1; position: relative; z-index: 1; align-self: start; justify-self: start; }
  .publication__corps { grid-area: 1 / 1; }
  .tete { min-height: 44px; align-content: center; padding-inline-start: calc(44px + var(--space-3)); }
}
</style>
