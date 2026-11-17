<script setup lang="ts">
import { appelCercleActif, quitter as quitterAppelCercle, reduire as reduireAppelCercle, rejoindre, useAppelCercle } from './appelCercle'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import IconeCercle, { type NomIcone } from './IconeCercle.vue'
import BarreReactions from '../../components/texte/BarreReactions.vue'
import Composeur from '../../components/texte/Composeur.vue'
import EnregistreurVocal from '../../components/texte/EnregistreurVocal.vue'
import PastillesReactions from '../../components/texte/PastillesReactions.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import { envoyerFichier, type PieceJointe } from '../../components/texte/fichiers'
import { useSession } from '../../session'
import { ilYa } from '../vitrine/vitrine'
import type { Avancement, Cercle } from './cercles'
import {
  actionnable,
  derniereVersion,
  envoyer,
  estAMoi,
  fusionner,
  lancerAppel,
  LIMITES_SALON,
  lignesDuSalon,
  lireSalon,
  modifiable,
  modifierMessage,
  prenom,
  reagir as reagirMessage,
  resoudre,
  supprimerMessage,
  terminerAppel,
  type AppelCercle,
  type MessageCercle,
  type ModeSaisie,
} from './salon'

/*
 * Le salon du cercle (contracts/cercles.yaml v0.2) : pas un salon de discussion comme les autres, celui d'une équipe
 * qui construit ensemble pendant deux semaines.
 * - En tête, où en est le tour, qui a fait son point du jour, et l'appel du cercle.
 * - Les coups de main ouverts restent épinglés tant que personne ne les a réglés : on aide d'abord qui bloque.
 * - Le fil mêle les messages, les points du jour (fait, prévu, bloqué) et les étapes du cercle racontées par Jamii.
 * - On relit les nouveautés toutes les 4 secondes (`apres` : la dernière version connue, qui ramène aussi les messages
 *   modifiés, supprimés ou réagis), et on s'arrête quand l'onglet est caché.
 * - Sur chaque message : réagir, répondre (menu ou glisser au doigt), corriger pendant 15 minutes, supprimer (#157).
 * Les textes sont affichés échappés (jamais v-html).
 */
const props = defineProps<{ cercle: Cercle; suivi: Avancement | null; titreTour: string | null }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const INTERVALLE = 4000

const messages = ref<MessageCercle[]>([])
const coupsDeMain = ref<MessageCercle[]>([])
const pointsDuJour = ref<string[]>([])
const appel = ref<AppelCercle | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const coupe = ref(false)

const lignes = computed(() => lignesDuSalon(messages.value))
const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)

// ---------------------------------------------------------------- lecture en direct

const liste = ref<HTMLElement | null>(null)
const nonVus = ref(0)
let minuterie: ReturnType<typeof setInterval> | null = null
let lecture = 0

const enBas = () => {
  const el = liste.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
}
function allerEnBas() {
  void nextTick(() => {
    if (liste.value) liste.value.scrollTop = liste.value.scrollHeight
    nonVus.value = 0
  })
}

async function lire(complet = false) {
  const numero = ++lecture
  const apres = complet ? undefined : derniereVersion(messages.value)
  try {
    const r = await lireSalon(props.cercle.id, apres)
    if (numero !== lecture) return
    if (!r.ok) {
      if (complet) etat.value = 'indisponible'
      else coupe.value = true
      return
    }
    coupe.value = false
    const etaitEnBas = enBas()
    const avant = messages.value.length
    messages.value = complet ? r.salon.elements : fusionner(messages.value, r.salon.elements)
    coupsDeMain.value = r.salon.coupsDeMain
    pointsDuJour.value = r.salon.pointsDuJour
    appel.value = r.salon.appel
    etat.value = 'pret'
    if (complet || etaitEnBas) allerEnBas()
    else nonVus.value += Math.max(0, messages.value.length - avant)
  } catch {
    if (numero !== lecture) return
    if (complet) etat.value = 'indisponible'
    else coupe.value = true
  }
}

function demarrer() {
  arreter()
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
  minuterie = setInterval(() => void lire(), INTERVALLE)
}
function arreter() {
  if (minuterie) clearInterval(minuterie)
  minuterie = null
}
function visibilite() {
  if (document.visibilityState === 'hidden') return arreter()
  void lire()
  demarrer()
}
onMounted(() => {
  void lire(true)
  demarrer()
  document.addEventListener('visibilitychange', visibilite)
  document.addEventListener('click', clicAilleurs)
  document.addEventListener('keydown', echap)
})
onBeforeUnmount(() => {
  arreter()
  clearInterval(horloge)
  document.removeEventListener('visibilitychange', visibilite)
  document.removeEventListener('click', clicAilleurs)
  document.removeEventListener('keydown', echap)
})

// ---------------------------------------------------------------- écrire
// Un message, ou une demande d'aide (« Je bloque ») qui reste en vue tant que personne ne l'a réglée. Le point du jour a
// son propre outil (Point du soir) : le salon reste une conversation.

const bloque = ref(false)
const mode = computed<ModeSaisie>(() => (bloque.value ? 'coup_de_main' : 'texte'))
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
const pret = computed(() => !envoi.value && (!!texte.value.trim() || (!bloque.value && !edition.value && pieces.value.length > 0)))

function basculerBloque() {
  bloque.value = !bloque.value
  erreur.value = null
  void nextTick(() => composeur.value?.focus())
}

async function soumettre() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = null
  try {
    if (edition.value) {
      const corrige = await modifierMessage(props.cercle.id, edition.value.id, texte.value.trim())
      if (!corrige) {
        erreur.value = t('cercles.salon.erreurs.envoi')
        return
      }
      remplacer(corrige)
      edition.value = null
      texte.value = ''
      composeur.value?.rafraichir()
      return
    }
    const r = await envoyer(props.cercle.id, {
      type: mode.value as 'texte' | 'coup_de_main',
      texte: texte.value.trim(),
      pieces: pieces.value.map((p) => p.id),
      ...(reponse.value ? { reponseA: reponse.value.id } : {}),
    })
    if (!r.ok) {
      erreur.value = t(r.statut === 429 ? 'cercles.salon.erreurs.trop' : 'cercles.salon.erreurs.envoi')
      return
    }
    messages.value = fusionner(messages.value, [r.message])
    if (r.message.type === 'coup_de_main') coupsDeMain.value = [...coupsDeMain.value, r.message]
    texte.value = ''
    pieces.value = []
    bloque.value = false
    reponse.value = null
    composeur.value?.rafraichir()
    allerEnBas()
  } catch {
    erreur.value = t('cercles.salon.erreurs.envoi')
  } finally {
    envoi.value = false
  }
}

// ---------------------------------------------------------------- un message vocal (#157)
// Comme dans une discussion privée : le son part seul, en message, sans toucher au texte en cours d'écriture. Comme
// les images du salon, c'est un fichier du cercle : seuls ses membres actuels peuvent le lire.

const vocalEnCours = ref(false)
async function envoyerVocal(fichier: File) {
  if (envoi.value) return
  const cercleId = props.cercle.id
  envoi.value = true
  erreur.value = null
  try {
    const f = await envoyerFichier(fichier, undefined, cercleId, true)
    if (!f.ok) {
      erreur.value = t(`vocal.erreurs.${f.code}`)
      return
    }
    const r = await envoyer(cercleId, { type: 'texte', texte: '', pieces: [f.piece.id], ...(reponse.value ? { reponseA: reponse.value.id } : {}) })
    if (cercleId !== props.cercle.id) return
    if (!r.ok) {
      erreur.value = t(r.statut === 429 ? 'cercles.salon.erreurs.trop' : 'cercles.salon.erreurs.envoi')
      return
    }
    messages.value = fusionner(messages.value, [r.message])
    reponse.value = null
    allerEnBas()
  } catch {
    erreur.value = t('cercles.salon.erreurs.envoi')
  } finally {
    envoi.value = false
  }
}

// ---------------------------------------------------------------- réagir, répondre, corriger, supprimer (#157)

const reponse = ref<MessageCercle | null>(null)
const edition = ref<MessageCercle | null>(null)
const menu = ref<string | null>(null)
const aSupprimer = ref<string | null>(null)
const surligne = ref<string | null>(null)
const maintenant = ref(Date.now())
const horloge = setInterval(() => (maintenant.value = Date.now()), 30_000)

function remplacer(m: MessageCercle) {
  messages.value = messages.value.map((x) => (x.id === m.id ? m : x))
}
/** Un deuxième clic sur le même emoji retire ma réaction. */
async function reagir(m: MessageCercle, emoji: string) {
  menu.value = null
  const active = !m.reactions.some((r) => r.emoji === emoji && r.deMoi)
  try {
    const maj = await reagirMessage(props.cercle.id, m.id, emoji, active)
    if (!maj) throw new Error('refus')
    remplacer(maj)
  } catch {
    erreur.value = t('reactions.erreur')
  }
}
function ouvrirMenu(m: MessageCercle) {
  aSupprimer.value = null
  menu.value = menu.value === m.id ? null : m.id
}
function repondre(m: MessageCercle) {
  menu.value = null
  if (edition.value) texte.value = ''
  edition.value = null
  reponse.value = m
  void nextTick(() => composeur.value?.focus())
}
function modifier(m: MessageCercle) {
  menu.value = null
  reponse.value = null
  bloque.value = false
  edition.value = m
  texte.value = m.texte ?? ''
  void nextTick(() => composeur.value?.focus())
}
function annulerBandeau() {
  if (edition.value) texte.value = ''
  edition.value = null
  reponse.value = null
}
async function supprimer(m: MessageCercle) {
  menu.value = null
  aSupprimer.value = null
  try {
    if (!(await supprimerMessage(props.cercle.id, m.id))) throw new Error('refus')
    remplacer({ ...m, texte: null, piecesJointes: [], point: null, reactions: [], supprime: true })
    coupsDeMain.value = coupsDeMain.value.filter((x) => x.id !== m.id)
    if (edition.value?.id === m.id || reponse.value?.id === m.id) annulerBandeau()
  } catch {
    erreur.value = t('reseau.actions.erreurSuppression')
  }
}
const nomAuteur = (m: { auteur?: { id: string; nom: string } | null }) =>
  m.auteur?.id && m.auteur.id === moi.value?.id ? t('reseau.actions.toi') : prenom(m.auteur?.nom) || t('cercles.salon.quelquun')
function apercu(m: MessageCercle) {
  if (m.supprime) return t('reseau.actions.supprime')
  if (m.texte) return m.texte.replace(/[*_`~>#]/g, '').slice(0, 140)
  if (m.point) return m.point.fait
  const sorte = m.piecesJointes[0]?.sorte
  return sorte ? t(`reseau.actions.sortes.${sorte}`) : ''
}
/** Un clic sur une citation remonte au message cité, s'il est chargé, et le fait briller un instant. */
function allerA(id: string) {
  const cible = liste.value?.querySelector<HTMLElement>(`[data-message="${id}"]`)
  if (!cible) return
  cible.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  surligne.value = id
  setTimeout(() => {
    if (surligne.value === id) surligne.value = null
  }, 1_400)
}

// Glisser un message vers la droite (au doigt) pour y répondre.
const SEUIL_GLISSE = 64 as const
const glisse = ref<{ id: string; dx: number } | null>(null)
let depart: { id: string; x: number; y: number; horizontal: boolean | null } | null = null
function toucher(e: PointerEvent, m: MessageCercle) {
  if (e.pointerType !== 'touch' || !actionnable(m)) return
  depart = { id: m.id, x: e.clientX, y: e.clientY, horizontal: null }
}
function bouger(e: PointerEvent) {
  if (!depart) return
  const dx = e.clientX - depart.x
  const dy = e.clientY - depart.y
  if (depart.horizontal === null && Math.hypot(dx, dy) > 8) depart.horizontal = Math.abs(dx) > Math.abs(dy) && dx > 0
  if (depart.horizontal) glisse.value = { id: depart.id, dx: Math.max(0, Math.min(dx, SEUIL_GLISSE + 24)) }
}
function lacher() {
  const g = glisse.value
  const m = g && messages.value.find((x) => x.id === g.id)
  if (g && m && g.dx >= SEUIL_GLISSE) {
    repondre(m)
    navigator.vibrate?.(12)
  }
  depart = null
  glisse.value = null
}
function clicAilleurs(e: MouseEvent) {
  if (menu.value && !(e.target as Element | null)?.closest?.('.actions')) menu.value = null
}
function echap(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (menu.value) menu.value = null
  else if (reponse.value || edition.value) annulerBandeau()
}

// ---------------------------------------------------------------- coups de main

const enCours = ref<string | null>(null)
const voirCoups = ref(false)

/** « Je peux aider » : on répond dans le fil, en s'adressant à la personne. */
function aider(m: MessageCercle) {
  bloque.value = false
  voirCoups.value = false
  texte.value = `@${prenom(m.auteur?.nom)} `
  void nextTick(() => composeur.value?.focus())
}

async function regler(m: MessageCercle) {
  enCours.value = m.id
  try {
    const resolu = await resoudre(props.cercle.id, m.id)
    if (resolu) {
      coupsDeMain.value = coupsDeMain.value.filter((x) => x.id !== m.id)
      messages.value = messages.value.map((x) => (x.id === resolu.id ? resolu : x))
    }
  } finally {
    enCours.value = null
  }
}

// ---------------------------------------------------------------- l'appel du cercle (#157)

const etatAppel = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const peutTerminer = computed(() => !!appel.value && !!moi.value && (appel.value.lancePar?.id === moi.value.id || props.cercle.moi.estCreateur))
const { etat: appelEnDirect } = useAppelCercle()
const dansCetAppel = computed(() => appelCercleActif() && appelEnDirect.cercleId === props.cercle.id)

/** L'appel se passe dans Jamii, en WebRTC (appelCercle.ts) : lancer ou rejoindre ouvre la fenêtre d'appel. */
async function appeler(video = false) {
  if (etatAppel.value === 'envoi') return
  if (dansCetAppel.value) return reduireAppelCercle(false)
  etatAppel.value = 'envoi'
  try {
    await rejoindre({ id: props.cercle.id, nom: props.cercle.nom }, video)
    const fin = appelEnDirect.phase === 'fini' ? appelEnDirect.fin : null
    etatAppel.value = fin === 'trop_de_requetes' ? 'trop' : fin === 'impossible' ? 'erreur' : 'repos'
    void lire()
  } catch {
    etatAppel.value = 'erreur'
  }
}
async function raccrocher() {
  if (dansCetAppel.value) await quitterAppelCercle()
  await terminerAppel(props.cercle.id)
  appel.value = null
  void lire()
}

// ---------------------------------------------------------------- affichage

const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
function jour(date: Date) {
  const aujourdhui = new Date()
  const hier = new Date(aujourdhui.getTime() - 86_400_000)
  const meme = (a: Date, b: Date) => a.toDateString() === b.toDateString()
  if (meme(date, aujourdhui)) return t('cercles.salon.aujourdhui')
  if (meme(date, hier)) return t('cercles.salon.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(date)
}
const ICONES: Record<string, NomIcone> = {
  cercle_cree: 'creation',
  membre_rejoint: 'arrivee',
  tirage: 'tirage',
  tour_ouvert: 'depart',
  tour_livre: 'livre',
  cercle_termine: 'fin',
}
function evenement(m: MessageCercle) {
  const e = m.evenement!
  return t(`cercles.salon.evenements.${e.code}`, { nom: prenom(e.personne?.nom) || t('cercles.salon.quelquun'), n: e.tour ?? '' })
}
const contexte = computed(() => {
  const c = props.cercle
  if (c.statut === 'termine') return t('cercles.salon.contexteTermine')
  if (!c.tirage) return t('cercles.salon.contexteAvant', { n: c.membres.length, taille: c.taille })
  if (!tour.value) return ''
  const base = t('cercles.salon.contexteTour', { n: tour.value.numero, titre: props.titreTour ?? t('cercles.projetDe', { nom: prenom(tour.value.beneficiaire.nom) }) })
  return props.suivi ? `${base} · ${t('cercles.salon.contexteJour', { jour: props.suivi.jour, faites: props.suivi.faites, total: props.suivi.total })}` : base
})
</script>

<template>
  <section class="salon carte" aria-labelledby="salon-titre" data-test="salon-cercle">
    <!-- Une barre fine : de quoi on parle, et l'appel du cercle. -->
    <header class="salon__barre">
      <div class="salon__titres">
        <h2 id="salon-titre"><IconeCercle nom="salon" :taille="18" />{{ t('cercles.qg.outils.salon') }}</h2>
        <p v-if="contexte" class="salon__contexte" data-test="contexte">{{ contexte }}</p>
      </div>
      <div class="appel" data-test="appel-cercle">
        <template v-if="appel">
          <span class="appel__direct"><span class="appel__pulse" aria-hidden="true" />{{ t('cercles.salon.appelEnCours', { nom: prenom(appel.lancePar?.nom) || t('cercles.salon.quelquun'), quand: ilYa(appel.lanceLe, locale) }) }}<template v-if="appel.presents"> · {{ t('cercles.salon.presents', { n: appel.presents }, appel.presents) }}</template></span>
          <button v-if="dansCetAppel" type="button" class="bouton bouton--direct" data-test="revenir-appel" @click="reduireAppelCercle(false)">{{ t('cercles.salon.revenirAppel') }}</button>
          <template v-else>
            <button type="button" class="bouton bouton--direct" :disabled="etatAppel === 'envoi'" data-test="rejoindre-appel" @click="appeler(false)">{{ t('cercles.salon.rejoindre') }}</button>
            <button type="button" class="bouton" :disabled="etatAppel === 'envoi'" data-test="rejoindre-appel-video" @click="appeler(true)">{{ t('cercles.salon.rejoindreVideo') }}</button>
          </template>
          <button v-if="peutTerminer" type="button" class="bouton bouton--discret" data-test="terminer-appel" @click="raccrocher">{{ t('cercles.salon.terminer') }}</button>
        </template>
        <button v-else type="button" class="bouton" :disabled="etatAppel === 'envoi'" :aria-busy="etatAppel === 'envoi' || undefined" data-test="lancer-appel" @click="appeler(false)">
          <IconeCercle nom="appel" :taille="16" />{{ t('cercles.salon.lancerAppel') }}
        </button>
      </div>
      <p v-if="etatAppel === 'erreur' || etatAppel === 'trop'" role="alert" class="erreur salon__erreur">{{ t(etatAppel === 'trop' ? 'cercles.salon.erreurs.tropAppels' : 'etat.indisponible') }}</p>
    </header>

    <!-- Les demandes d'aide ouvertes : une ligne discrète, dépliable. On aide d'abord qui bloque. -->
    <section v-if="coupsDeMain.length" class="aides" aria-labelledby="coups-titre" data-test="coups-de-main">
      <button type="button" class="aides__bascule" :aria-expanded="voirCoups" @click="voirCoups = !voirCoups">
        <span class="aides__point" aria-hidden="true" />
        <strong id="coups-titre">{{ t('cercles.salon.coupsTitre', { n: coupsDeMain.length }, coupsDeMain.length) }}</strong>
        <span class="aides__apercu">{{ prenom(coupsDeMain[0]!.auteur?.nom) }} · {{ coupsDeMain[0]!.texte }}</span>
        <span class="aides__voir">{{ voirCoups ? t('cercles.salon.masquer') : t('cercles.salon.voir') }}</span>
      </button>
      <ul v-show="voirCoups" class="aides__liste">
        <li v-for="c in coupsDeMain" :key="c.id" class="aide" data-test="coup-de-main">
          <LienPersonne :id="c.auteur?.id" :nom="c.auteur?.nom"><JAvatar :nom="c.auteur?.nom ?? '?'" :url="c.auteur?.avatarUrl" :taille="28" /></LienPersonne>
          <p class="aide__texte"><strong>{{ prenom(c.auteur?.nom) }}</strong> {{ c.texte }} <span class="discret">· {{ ilYa(c.envoyeLe, locale) }}</span></p>
          <span class="aide__actions">
            <button v-if="c.auteur?.id !== moi?.id" type="button" class="bouton bouton--petit" data-test="aider" @click="aider(c)">{{ t('cercles.salon.aider') }}</button>
            <button type="button" class="bouton bouton--petit" :disabled="enCours === c.id" data-test="regler" @click="regler(c)"><IconeCercle nom="coche" :taille="14" />{{ t('cercles.salon.regle') }}</button>
          </span>
        </li>
      </ul>
    </section>

    <p v-if="etat === 'chargement'" class="salon__etat" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'indisponible'" class="salon__etat" role="alert">{{ t('etat.indisponible') }}</p>

    <div v-else class="fil-cadre motif fond-discussion" data-test="motif-discussion">
      <ol ref="liste" class="fil" aria-live="polite" :aria-label="t('cercles.salon.fil')" @scroll="enBas() && (nonVus = 0)">
        <li v-if="!messages.length" class="vide">{{ t('cercles.salon.vide') }}</li>
        <template v-for="l in lignes" :key="l.cle">
          <li v-if="l.type === 'jour'" class="jour" role="separator"><span>{{ jour(l.date) }}</span></li>

          <li v-else-if="l.message.type === 'evenement'" class="evenement" data-test="evenement">
            <IconeCercle :nom="ICONES[l.message.evenement?.code ?? ''] ?? 'cercle'" :taille="14" />
            <span>{{ evenement(l.message) }}</span>
            <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
          </li>

          <li
            v-else
            class="message"
            :class="{ 'message--suite': l.suite && !l.message.reponseA, 'message--menu': menu === l.message.id, 'message--surligne': surligne === l.message.id, 'message--glisse': glisse?.id === l.message.id }"
            :data-message="l.message.id"
            data-test="message"
            @pointerdown="toucher($event, l.message)"
            @pointermove="bouger"
            @pointerup="lacher"
            @pointercancel="lacher"
          >
            <span v-if="glisse?.id === l.message.id" class="message__repondre" :class="{ 'message__repondre--pret': glisse.dx >= SEUIL_GLISSE }" aria-hidden="true">
              <svg viewBox="0 0 20 20" width="16" height="16"><path d="M8 5 3.5 9.5 8 14M4 9.5h7.5a5 5 0 0 1 5 5v1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <span class="message__avatar">
              <LienPersonne v-if="!l.suite" :id="l.message.auteur?.id" doublon><JAvatar :nom="l.message.auteur?.nom ?? '?'" :url="l.message.auteur?.avatarUrl" :taille="36" /></LienPersonne>
              <time v-else class="message__heure-survol" :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
            </span>
            <div class="message__corps" :style="glisse?.id === l.message.id ? { transform: `translateX(${glisse.dx}px)` } : undefined">
              <p v-if="!l.suite || l.message.reponseA" class="message__meta">
                <LienPersonne :id="l.message.auteur?.id" class="message__nom">{{ l.message.auteur?.nom ?? t('communautes.compteSupprime') }}</LienPersonne>
                <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
              </p>

              <button v-if="l.message.reponseA && !l.message.supprime" type="button" class="citation" data-test="citation" @click="allerA(l.message.reponseA.id)">
                <strong>{{ nomAuteur(l.message.reponseA) }}</strong>
                <span>{{ l.message.reponseA.supprime ? t('reseau.actions.supprime') : l.message.reponseA.texte }}</span>
              </button>

              <p v-if="l.message.supprime" class="message__supprime" data-test="message-supprime">
                {{ estAMoi(l.message, moi?.id) ? t('reseau.actions.tuAsSupprime') : t('reseau.actions.supprime') }}
              </p>

              <template v-else-if="l.message.type === 'texte'">
                <TexteRiche v-if="l.message.texte" :texte="l.message.texte" />
                <PiecesJointes :pieces="l.message.piecesJointes ?? []" />
              </template>

              <div v-else-if="l.message.type === 'point' && l.message.point" class="bloc" data-test="point-du-jour">
                <p class="bloc__titre"><IconeCercle nom="points" :taille="14" />{{ t('cercles.salon.sonPoint') }}</p>
                <dl class="bloc__lignes">
                  <div><dt>{{ t('cercles.salon.fait') }}</dt><dd>{{ l.message.point.fait }}</dd></div>
                  <div><dt>{{ t('cercles.salon.prevu') }}</dt><dd>{{ l.message.point.prevu }}</dd></div>
                  <div v-if="l.message.point.bloque" class="bloc__alerte"><dt>{{ t('cercles.salon.bloque') }}</dt><dd>{{ l.message.point.bloque }}</dd></div>
                </dl>
              </div>

              <div v-else-if="l.message.type === 'coup_de_main'" class="bloc" :class="l.message.coupDeMain?.resoluLe ? 'bloc--regle' : 'bloc--aide'">
                <p class="bloc__titre"><IconeCercle :nom="l.message.coupDeMain?.resoluLe ? 'coche' : 'priorite'" :taille="14" />{{ l.message.coupDeMain?.resoluLe ? (l.message.coupDeMain.resoluPar ? t('cercles.salon.regleePar', { nom: prenom(l.message.coupDeMain.resoluPar.nom) }) : t('cercles.salon.reglee')) : t('cercles.salon.demandeTitre') }}</p>
                <TexteRiche :texte="l.message.texte ?? ''" />
                <PiecesJointes :pieces="l.message.piecesJointes ?? []" compact />
              </div>

              <div v-else-if="l.message.type === 'decision'" class="bloc" data-test="decision-fil">
                <p class="bloc__titre"><IconeCercle nom="decisions" :taille="14" />{{ t('cercles.qg.decisionPrise') }}</p>
                <TexteRiche :texte="l.message.texte ?? ''" />
              </div>

              <p v-else-if="l.message.type === 'appel'" class="message__appel">
                <IconeCercle nom="appel" :taille="16" />{{ t('cercles.salon.aLanceAppel') }}
                <button v-if="l.message.appel?.enCours && appel && appel.id === l.message.appel.id && !dansCetAppel" type="button" class="lien-fort lien-bouton" @click="appeler(false)">{{ t('cercles.salon.rejoindre') }}</button>
                <span v-else class="discret">· {{ t('cercles.salon.appelTermine') }}</span>
              </p>

              <span v-if="l.message.modifieLe && !l.message.supprime" class="message__modifie" data-test="modifie">({{ t('reseau.actions.modifie') }})</span>
              <PastillesReactions v-if="!l.message.supprime" :reactions="l.message.reactions" @basculer="(e) => reagir(l.message, e)" />
            </div>

            <!-- Les actions : un « ⋯ » en haut à droite du message (au survol, au clavier, toujours au doigt). -->
            <div v-if="actionnable(l.message)" class="actions">
              <button type="button" class="actions__ouvrir" :aria-expanded="menu === l.message.id" :aria-label="t('reseau.actions.menu')" data-test="actions-message" @click.stop="ouvrirMenu(l.message)">
                <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="4.5" cy="10" r="1.6" fill="currentColor" /><circle cx="10" cy="10" r="1.6" fill="currentColor" /><circle cx="15.5" cy="10" r="1.6" fill="currentColor" /></svg>
              </button>
              <div v-if="menu === l.message.id" class="actions__menu" role="menu">
                <template v-if="aSupprimer !== l.message.id">
                  <BarreReactions :actives="l.message.reactions.filter((r) => r.deMoi).map((r) => r.emoji)" @choisir="(e) => reagir(l.message, e)" />
                  <button type="button" role="menuitem" data-test="action-repondre" @click="repondre(l.message)">{{ t('reseau.actions.repondre') }}</button>
                  <button v-if="modifiable(l.message, moi?.id, maintenant)" type="button" role="menuitem" data-test="action-modifier" @click="modifier(l.message)">{{ t('reseau.actions.modifier') }}</button>
                  <button v-if="estAMoi(l.message, moi?.id)" type="button" role="menuitem" class="danger" data-test="action-supprimer" @click.stop="aSupprimer = l.message.id">{{ t('reseau.actions.supprimer') }}</button>
                </template>
                <template v-else>
                  <p class="actions__question">{{ t('reseau.actions.confirmerSuppression') }}</p>
                  <button type="button" role="menuitem" class="danger" data-test="confirmer-suppression" @click="supprimer(l.message)">{{ t('reseau.actions.supprimer') }}</button>
                  <button type="button" role="menuitem" @click.stop="aSupprimer = null">{{ t('reseau.actions.annuler') }}</button>
                </template>
              </div>
            </div>
          </li>
        </template>
      </ol>
      <button v-if="nonVus" type="button" class="nouveaux" @click="allerEnBas">{{ t('cercles.salon.nouveaux', { n: nonVus }, nonVus) }}</button>
    </div>
    <p v-if="coupe" class="salon__etat" role="status">{{ t('cercles.salon.coupe') }}</p>

    <!-- La saisie : un message, ou « Je bloque » pour demander de l'aide. -->
    <form class="saisie" data-test="saisie" @submit.prevent="soumettre">
      <!-- Je réponds à un message, ou j'en corrige un : le bandeau le rappelle au-dessus de la saisie. -->
      <div v-if="reponse || edition" class="bandeau" data-test="bandeau-action">
        <span class="bandeau__texte">
          <strong>{{ edition ? t('reseau.actions.enModification') : t('reseau.actions.repondreA', { nom: nomAuteur(reponse!) }) }}</strong>
          <span>{{ apercu((edition ?? reponse)!) }}</span>
        </span>
        <button type="button" class="bandeau__fermer" :aria-label="t('reseau.actions.annuler')" data-test="annuler-action" @click="annulerBandeau">
          <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        </button>
      </div>
      <Composeur
        ref="composeur"
        v-model="texte"
        v-model:pieces="pieces"
        id-zone="salon-texte"
        :cercle-id="cercle.id"
        :placeholder="t(bloque ? 'cercles.salon.bloquePlaceholder' : 'cercles.salon.placeholder')"
        :libelle-envoyer="edition ? t('reseau.actions.enregistrer') : t(bloque ? 'cercles.salon.envoyerBloque' : 'cercles.salon.envoyer')"
        :envoi="envoi"
        :erreur="erreur"
        :accent="bloque ? 'alerte' : 'normal'"
        :max="LIMITES_SALON.texte"
        :masquer-envoi="vocalEnCours && !edition && !bloque"
        @envoyer="soumettre"
      >
        <template v-if="!edition" #outils>
          <span class="separateur" aria-hidden="true" />
          <button type="button" class="bascule" :aria-pressed="bloque" data-test="mode-bloque" @click="basculerBloque">
            <IconeCercle nom="priorite" :taille="14" />{{ t('cercles.salon.modeBloque') }}
          </button>
        </template>
        <!-- Pas de vocal pour une demande d'aide (« Je bloque ») : elle doit pouvoir se lire d'un coup d'œil. -->
        <template v-if="!edition && !bloque" #action>
          <EnregistreurVocal :desactive="envoi" @envoyer="envoyerVocal" @en-cours="(actif) => (vocalEnCours = actif)" @erreur="(code) => (erreur = t(`vocal.erreurs.${code}`))" />
        </template>
      </Composeur>
    </form>
  </section>
</template>

<style scoped>
/* Une seule carte, à la hauteur de l'écran : la barre, le fil qui défile, la saisie collée en bas. */
/* Le salon prend tout l'écran sous la barre du site, comme une messagerie : le fil défile, la saisie reste en bas. */
.salon { display: flex; flex-direction: column; height: max(28rem, calc(100dvh - var(--salon-marge, 7rem))); padding: 0; overflow: hidden; }

.salon__barre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--line); }
.salon__titres { display: grid; gap: 2px; min-width: 0; }
.salon__titres h2 { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: 1.125rem; }
.salon__contexte { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.salon__erreur { width: 100%; }
.salon__etat { margin: 0; padding: var(--space-3) var(--space-6); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.appel { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.appel__direct { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.appel__pulse { width: 8px; height: 8px; border-radius: 50%; background: #2EAD5F; box-shadow: 0 0 0 0 rgb(46 173 95 / 0.6); animation: pulse 1.6s infinite; }
@keyframes pulse { 70% { box-shadow: 0 0 0 7px rgb(46 173 95 / 0); } 100% { box-shadow: 0 0 0 0 rgb(46 173 95 / 0); } }

.bouton {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 38px; padding: 0 var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-pill);
  background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; text-decoration: none; cursor: pointer; white-space: nowrap;
}
.bouton:hover { border-color: var(--ink-muted); }
.bouton:disabled { opacity: 0.6; cursor: progress; }
.bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.bouton--direct { background: #1F7A5C; border-color: #1F7A5C; color: #fff; }
.bouton--direct:hover { background: #18654B; border-color: #18654B; }
.bouton--discret { border-color: transparent; color: var(--ink-muted); }
.bouton--petit { min-height: 32px; padding: 0 var(--space-3); font-size: 0.8125rem; }

/* Les demandes d'aide : une ligne, un point orange, rien de plus tant qu'on ne déplie pas. */
.aides { border-bottom: 1px solid var(--line); background: var(--surface-raised); }
.aides__bascule { display: flex; align-items: center; gap: var(--space-2); width: 100%; min-height: 44px; padding: 0 var(--space-6); border: 0; background: none; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); text-align: left; cursor: pointer; }
.aides__bascule:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.aides__point { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--proof); }
.aides__apercu { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); }
.aides__voir { flex: none; color: var(--action-ink); font-weight: 600; }
.aides__liste { list-style: none; margin: 0; padding: 0 var(--space-6) var(--space-3); display: grid; gap: var(--space-2); }
.aide { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; }
.aide__texte { margin: 0; font-size: var(--type-body-sm-size); overflow-wrap: anywhere; }
.aide__actions { display: flex; gap: var(--space-2); }

.fil-cadre { position: relative; flex: 1; min-height: 0; }
.fil { list-style: none; margin: 0; height: 100%; box-sizing: border-box; padding: var(--space-3) var(--space-6); display: grid; gap: 2px; overflow-y: auto; overscroll-behavior: contain; align-content: start; }
.vide { padding: var(--space-12) 0; text-align: center; color: var(--ink-muted); }
.jour { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-4) 0 var(--space-2); color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; }
.jour::before, .jour::after { content: ''; flex: 1; height: 1px; background: var(--line); }
.jour span::first-letter { text-transform: uppercase; }

.evenement { display: flex; justify-content: center; align-items: center; gap: var(--space-2); margin: var(--space-1) 0; color: var(--ink-muted); font-size: 0.8125rem; text-align: center; }
.evenement time { font-size: 0.75rem; opacity: 0.75; }

.message { display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-2) var(--space-2); margin: 0 calc(-1 * var(--space-2)); border-radius: var(--radius-md); }
.message:hover { background: var(--surface-raised); }
.message--suite { padding-top: 2px; }
.message__heure-survol { display: block; padding-top: 3px; color: var(--ink-muted); font-size: 0.6875rem; text-align: right; opacity: 0; }
.message:hover .message__heure-survol { opacity: 1; }
.message__corps { min-width: 0; display: grid; gap: var(--space-1); }
.message__meta { margin: 0; display: flex; align-items: baseline; gap: var(--space-2); }
.message__nom { font-weight: 700; }
.message__meta time { color: var(--ink-muted); font-size: 0.75rem; }
.message__texte { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; }
.message__appel { margin: 0; display: inline-flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); color: var(--ink-muted); }
.lien-fort { color: #1F7A5C; font-weight: 700; }

/* Les messages structurés : un même bloc sobre, un seul accent de couleur à gauche quand il faut agir. */
.bloc { display: grid; gap: var(--space-1); max-width: 40rem; padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); }
.bloc__titre { margin: 0; display: inline-flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; }
.bloc--aide { border-left: 3px solid var(--proof); }
.bloc--aide .bloc__titre { color: var(--proof-ink); }
.bloc--regle .bloc__titre { color: var(--ink-muted); }
.bloc__lignes { margin: 0; display: grid; gap: var(--space-1); }
.bloc__lignes > div { display: grid; grid-template-columns: 4.5rem minmax(0, 1fr); gap: var(--space-2); }
.bloc__lignes dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.bloc__lignes dd { margin: 0; overflow-wrap: anywhere; }
.bloc__alerte dt, .bloc__alerte dd { color: var(--proof-ink); }
.discret { color: var(--ink-muted); }

.nouveaux { position: absolute; left: 50%; bottom: var(--space-3); translate: -50% 0; padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-pill); background: var(--ink); color: var(--surface); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }

.saisie { margin: var(--space-3) var(--space-6) var(--space-4); }
.separateur { width: 1px; height: 18px; margin: 0 var(--space-1); background: var(--line); }
.bascule { display: inline-flex; align-items: center; gap: 6px; min-height: 30px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); background: transparent; color: var(--ink-muted); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer; }
.bascule[aria-pressed='true'] { border-color: var(--proof); background: var(--proof-soft); color: var(--proof-ink); }
.bascule:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-size: var(--type-body-sm-size); font-weight: 600; }

@media (max-width: 720px) {
  .salon { height: calc(100dvh - 9rem); border-radius: var(--radius-md); }
  .salon__barre, .aides__bascule, .fil { padding-inline: var(--space-4); }
  .saisie { margin-inline: var(--space-3); }
  .aide { grid-template-columns: auto minmax(0, 1fr); }
  .aide__actions { grid-column: 2; }
}
@media (prefers-reduced-motion: reduce) { .appel__pulse { animation: none; } }
.lien-bouton { border: 0; background: none; padding: 0; font: inherit; cursor: pointer; }

/* Réagir, répondre, corriger, supprimer (#157). */
.message { position: relative; touch-action: pan-y; }
.message--menu, .message--surligne { background: var(--surface-raised); }
.message--surligne { box-shadow: inset 3px 0 0 var(--action); transition: box-shadow 200ms ease-out; }
.message:not(.message--glisse) .message__corps { transition: transform 300ms var(--ease-pop); }
.message__repondre { position: absolute; left: 4px; top: 50%; display: grid; place-items: center; width: 30px; height: 30px; margin-top: -15px; border-radius: 50%; background: var(--surface); color: var(--ink-muted); opacity: 0.6; }
.message__repondre--pret { opacity: 1; color: var(--on-action, #fff); background: var(--action); }
.message__supprime { margin: 0; color: var(--ink-muted); font-style: italic; }
.message__modifie { color: var(--ink-muted); font-size: 0.75rem; font-style: italic; }
.citation {
  display: grid; gap: 1px; justify-items: start; min-width: 0; max-width: 40rem; padding: 6px 10px; border: 0; border-left: 3px solid var(--action);
  border-radius: 8px; background: var(--surface-raised); color: inherit; font: inherit; text-align: left; cursor: pointer;
}
.citation strong { font-size: 0.8125rem; color: var(--action-ink); }
.citation span { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 0.875rem; color: var(--ink-muted); }
.citation:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.actions { position: absolute; top: 2px; right: var(--space-2); opacity: 0; transition: opacity 160ms var(--ease-out); }
.message:hover .actions, .actions:focus-within, .message--menu .actions { opacity: 1; }
@media (hover: none) { .actions { opacity: 0.55; } }
.actions__ouvrir { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: var(--surface); color: var(--ink-muted); cursor: pointer; }
.actions__ouvrir:hover, .actions__ouvrir[aria-expanded="true"] { color: var(--ink); border-color: var(--ink-muted); }
.actions__ouvrir:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.actions__menu {
  position: absolute; top: calc(100% + 4px); right: 0; z-index: 5; display: grid; min-width: 11rem; padding: 6px; border-radius: 14px;
  background: var(--surface); color: var(--ink); border: 1px solid var(--line); box-shadow: 0 1px 2px rgb(0 0 0 / 0.06), 0 18px 40px -12px rgb(0 0 0 / 0.28);
  transform-origin: top right; animation: menu-entre 180ms var(--ease-out);
}
.actions__menu button { padding: 9px 12px; border: 0; border-radius: 9px; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.actions__menu button:hover, .actions__menu button:focus-visible { background: var(--surface-raised); outline: none; }
.actions__menu .danger { color: var(--danger); }
.actions__question { margin: 4px 12px 6px; font-weight: 600; font-size: var(--type-body-sm-size); }
@keyframes menu-entre { from { opacity: 0; transform: translateY(-6px) scale(0.96); } }

.bandeau { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-2); padding: 8px 8px 8px 14px; border-radius: 14px; background: var(--surface); border: 1px solid var(--line); border-left: 4px solid var(--action); }
.bandeau__texte { display: grid; flex: 1; min-width: 0; }
.bandeau__texte strong { font-size: 0.875rem; color: var(--action-ink); }
.bandeau__texte span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); font-size: 0.875rem; }
.bandeau__fermer { display: grid; place-items: center; flex: none; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; }
.bandeau__fermer:hover { background: var(--surface-raised); color: var(--ink); }
@media (prefers-reduced-motion: reduce) { .actions__menu, .message__corps { animation: none; transition: none; } }
</style>
