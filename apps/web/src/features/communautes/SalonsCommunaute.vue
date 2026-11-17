<script setup lang="ts">
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar, JBouton } from '../../design-system'
import { useSession } from '../../session'
import { ilYa } from '../vitrine/vitrine'
import { marquerLu } from './lus'
import SupprimerSalon from './SupprimerSalon.vue'
import BarreReactions from '../../components/texte/BarreReactions.vue'
import Composeur from '../../components/texte/Composeur.vue'
import EnregistreurVocal from '../../components/texte/EnregistreurVocal.vue'
import PastillesReactions from '../../components/texte/PastillesReactions.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import { envoyerFichier, type PieceJointe } from '../../components/texte/fichiers'

/*
 * Un salon d'une communauté, sur toute la hauteur (contracts/communautes.yaml v0.2, #155) :
 * - la conversation : les messages d'un même auteur à la suite sont groupés, un séparateur marque chaque jour ;
 * - « en direct » = on relit toutes les 3 secondes ce qui a changé (`apres` = la dernière version connue : les
 *   nouveaux messages, et ceux corrigés, supprimés ou réagis), et on s'arrête quand l'onglet est caché ;
 * - sur chaque message, pour les membres : réagir, répondre (menu ou glisser au doigt), corriger pendant 15 minutes,
 *   supprimer ; un modérateur supprime aussi le message d'un autre (#157) ;
 * - l'appel vocal ou vidéo du salon, pour les membres.
 * Les textes sont affichés échappés (jamais v-html).
 */
type Salon = components['schemas']['Salon']
type Message = components['schemas']['MessageSalon']

const props = defineProps<{ slug: string; salon?: string; membre: boolean; moderateur?: boolean; adhesionEnCours?: boolean }>()
const emit = defineEmits<{ rejoindre: []; salons: [salons: Salon[]]; supprime: [salon: string] }>()
const { t, locale } = useI18n()

const INTERVALLE = 3000
const LONGUEUR = 2000
/** Deux messages du même auteur à moins de 7 minutes d'écart : le second se range sous le premier. */
const ECART_GROUPE_MS = 7 * 60_000
/** On corrige son message pendant 15 minutes (Salons.DELAI_MODIFICATION). */
const DELAI_MODIFICATION_MS = 15 * 60_000

const salons = ref<Salon[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const actuel = computed(() => salons.value.find((s) => s.slug === props.salon) ?? salons.value[0] ?? null)

/** `silencieux` : une relecture pour suivre les appels, sans effacer l'écran. */
async function chargerSalons(silencieux = false) {
  if (!silencieux) etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } })
    if (!data) throw new Error('salons indisponibles')
    salons.value = data.elements
    emit('salons', data.elements)
    etat.value = 'pret'
  } catch {
    if (!silencieux) etat.value = 'indisponible'
  }
}

// ---------------------------------------------------------------- les appels (contrat v0.7)
// Un appel n'existe que quand quelqu'un le lance : une salle neuve, annoncée dans le salon, en cours une heure au plus.
const { moi } = useSession()
const appel = computed(() => actuel.value?.appel ?? null)
const peutTerminer = computed(() => Boolean(appel.value && (props.moderateur || (moi.value && appel.value.lancePar?.id === moi.value.id))))
const etatAppel = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')

/**
 * L'onglet de l'appel s'ouvre tout de suite, pendant le clic (sinon le navigateur le bloque), puis reçoit l'adresse
 * de la salle quand l'API a répondu.
 */
async function lancerAppel() {
  const salon = actuel.value
  if (!salon || etatAppel.value === 'envoi') return
  etatAppel.value = 'envoi'
  const fenetre = typeof window.open === 'function' ? window.open('', '_blank') : null
  try {
    const { data, response } = await api.POST('/api/communautes/{slug}/salons/{salon}/appel', {
      params: { path: { slug: props.slug, salon: salon.slug } },
    })
    if (!data?.url) {
      fenetre?.close()
      return void (etatAppel.value = response.status === 429 ? 'trop' : 'erreur')
    }
    if (fenetre) {
      fenetre.opener = null
      fenetre.location.href = data.url
    }
    etatAppel.value = 'repos'
    await chargerSalons(true)
    void lire()
  } catch {
    fenetre?.close()
    etatAppel.value = 'erreur'
  }
}
async function terminerAppel() {
  const salon = actuel.value
  if (!salon) return
  try {
    await api.DELETE('/api/communautes/{slug}/salons/{salon}/appel', { params: { path: { slug: props.slug, salon: salon.slug } } })
  } finally {
    await chargerSalons(true)
    void lire(true)
  }
}
/** L'annonce d'un appel donne « Rejoindre » tant que c'est l'appel en cours du salon. */
const rejoignable = (m: Message) => Boolean(m.appel?.enCours && appel.value && appel.value.id === m.appel.id && appel.value.url)
let suiviAppels: ReturnType<typeof setInterval> | null = null

const messages = ref<Message[]>([])
const premierChargement = ref(true)
const coupe = ref(false)
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
    retenirLu()
  })
}
function retenirLu() {
  const dernier = messages.value.at(-1)
  if (actuel.value && dernier) marquerLu(props.slug, actuel.value.slug, dernier.numero)
}
function defilement() {
  if (enBas()) {
    nonVus.value = 0
    retenirLu()
  }
}

/** Sans `complet` : seulement les messages arrivés depuis le dernier reçu. */
async function lire(complet = false) {
  const salon = actuel.value
  if (!salon) return
  const numero = ++lecture
  const apres = complet ? undefined : messages.value.reduce<number | undefined>((v, m) => (v === undefined || m.version > v ? m.version : v), undefined)
  try {
    const { data } = await api.GET('/api/communautes/{slug}/salons/{salon}/messages', {
      params: { path: { slug: props.slug, salon: salon.slug }, query: apres === undefined ? {} : { apres } },
    })
    // Une réponse arrivée après un changement de salon ne concerne plus l'écran.
    if (numero !== lecture || salon !== actuel.value) return
    if (!data) throw new Error('messages indisponibles')
    coupe.value = false
    if (!complet && data.elements.some((m) => m.appel)) void chargerSalons(true)
    ajouter(complet ? data.elements : [...messages.value, ...data.elements], complet)
  } catch {
    if (numero === lecture) coupe.value = true
  } finally {
    if (complet && numero === lecture) premierChargement.value = false
  }
}

/** Sans doublon : la version la plus récente d'un message remplace l'ancienne. On suit le bas si on y était. */
function ajouter(tous: Message[], forcerLeBas: boolean) {
  const avant = messages.value.length
  const etaitEnBas = enBas()
  const parId = new Map<string, Message>()
  for (const m of tous) parId.set(m.id, m)
  messages.value = [...parId.values()].sort((a, b) => a.numero - b.numero)
  if (forcerLeBas || etaitEnBas) allerEnBas()
  else nonVus.value += Math.max(0, messages.value.length - avant)
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

watch(actuel, (salon, avant) => {
  if (salon?.id === avant?.id) return
  messages.value = []
  nonVus.value = 0
  premierChargement.value = true
  texte.value = ''
  reponse.value = null
  edition.value = null
  menu.value = null
  void lire(true)
  demarrer()
})
watch(() => props.slug, () => void chargerSalons())
onMounted(async () => {
  document.addEventListener('visibilitychange', visibilite)
  document.addEventListener('click', clicAilleurs)
  document.addEventListener('keydown', echap)
  await chargerSalons()
  // Un appel peut se terminer tout seul (une heure) ou être terminé ailleurs : on relit les salons de temps en temps.
  suiviAppels = setInterval(() => {
    if (document.visibilityState !== 'hidden') void chargerSalons(true)
  }, 20_000)
})
onBeforeUnmount(() => {
  arreter()
  if (suiviAppels) clearInterval(suiviAppels)
  clearInterval(horloge)
  document.removeEventListener('visibilitychange', visibilite)
  document.removeEventListener('click', clicAilleurs)
  document.removeEventListener('keydown', echap)
})

/** Les lignes à afficher : un séparateur à chaque nouveau jour, et les messages groupés par auteur. */
type Ligne = { type: 'jour'; cle: string; libelle: string } | { type: 'message'; cle: string; message: Message; suite: boolean }
const jour = (iso: string) => new Date(iso).toDateString()
function libelleJour(iso: string) {
  const d = new Date(iso)
  const aujourdHui = new Date()
  const hier = new Date(aujourdHui.getTime() - 86_400_000)
  if (d.toDateString() === aujourdHui.toDateString()) return t('communautes.salons.aujourdhui')
  if (d.toDateString() === hier.toDateString()) return t('communautes.salons.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(d)
}
const lignes = computed<Ligne[]>(() => {
  const resultat: Ligne[] = []
  let precedent: Message | null = null
  for (const m of messages.value) {
    const nouveauJour = !precedent || jour(precedent.envoyeLe) !== jour(m.envoyeLe)
    if (nouveauJour) resultat.push({ type: 'jour', cle: `jour-${m.id}`, libelle: libelleJour(m.envoyeLe) })
    const suite = !nouveauJour && !!precedent && precedent.auteur.id === m.auteur.id && !m.reponseA
      && new Date(m.envoyeLe).getTime() - new Date(precedent.envoyeLe).getTime() < ECART_GROUPE_MS
    resultat.push({ type: 'message', cle: m.id, message: m, suite })
    precedent = m
  }
  return resultat
})

const texte = ref('')
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const valide = computed(() => (texte.value.trim().length > 0 || (!edition.value && pieces.value.length > 0)) && texte.value.length <= LONGUEUR)
async function envoyer() {
  const salon = actuel.value
  if (!salon || !valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  erreurVocal.value = null
  try {
    if (edition.value) {
      const { data } = await api.PATCH('/api/communautes/{slug}/salons/{salon}/messages/{messageId}', {
        params: { path: { slug: props.slug, salon: salon.slug, messageId: edition.value.id } },
        body: { texte: texte.value.trim() },
      })
      if (!data) return void (envoi.value = 'erreur')
      remplacer(data)
      edition.value = null
      texte.value = ''
      envoi.value = 'repos'
      composeur.value?.rafraichir()
      return
    }
    const { data, response } = await api.POST('/api/communautes/{slug}/salons/{salon}/messages', {
      params: { path: { slug: props.slug, salon: salon.slug } },
      body: { texte: texte.value.trim(), pieces: pieces.value.map((p) => p.id), ...(reponse.value ? { reponseA: reponse.value.id } : {}) },
    })
    if (!data) return void (envoi.value = response.status === 429 ? 'trop' : 'erreur')
    texte.value = ''
    pieces.value = []
    reponse.value = null
    envoi.value = 'repos'
    ajouter([...messages.value, data], true)
    composeur.value?.rafraichir()
  } catch {
    envoi.value = 'erreur'
  }
}

// ---------------------------------------------------------------- un message vocal (#157)
// Comme dans une discussion privée : le son part seul, en message, sans toucher au texte en cours d'écriture.

const vocalEnCours = ref(false)
const erreurVocal = ref<string | null>(null)
async function envoyerVocal(fichier: File) {
  const salon = actuel.value
  if (!salon || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  erreurVocal.value = null
  try {
    const f = await envoyerFichier(fichier, undefined, undefined, true)
    if (!f.ok) {
      envoi.value = 'repos'
      erreurVocal.value = t(`vocal.erreurs.${f.code}`)
      return
    }
    const { data, response } = await api.POST('/api/communautes/{slug}/salons/{salon}/messages', {
      params: { path: { slug: props.slug, salon: salon.slug } },
      body: { texte: '', pieces: [f.piece.id], ...(reponse.value ? { reponseA: reponse.value.id } : {}) },
    })
    if (!data) return void (envoi.value = response.status === 429 ? 'trop' : 'erreur')
    envoi.value = 'repos'
    // On a changé de salon entre-temps : le message est bien parti, il apparaîtra en y revenant.
    if (actuel.value?.slug !== salon.slug) return
    reponse.value = null
    ajouter([...messages.value, data], true)
  } catch {
    envoi.value = 'erreur'
  }
}

// ---------------------------------------------------------------- réagir, répondre, corriger, supprimer (#157)

const reponse = ref<Message | null>(null)
const edition = ref<Message | null>(null)
const menu = ref<string | null>(null)
const aSupprimer = ref<string | null>(null)
const surligne = ref<string | null>(null)
const erreurAction = ref<string | null>(null)
const maintenant = ref(Date.now())
const horloge = setInterval(() => (maintenant.value = Date.now()), 30_000)

const aMoi = (m: Message) => !!moi.value && m.auteur.id === moi.value.id
const actionnable = (m: Message) => props.membre && !m.supprime && !m.appel
const modifiable = (m: Message) => aMoi(m) && actionnable(m) && maintenant.value - Date.parse(m.envoyeLe) < DELAI_MODIFICATION_MS
const supprimable = (m: Message) => (aMoi(m) || !!props.moderateur) && actionnable(m)

function remplacer(m: Message) {
  messages.value = messages.value.map((x) => (x.id === m.id ? m : x))
}
async function reagir(m: Message, emoji: string) {
  const salon = actuel.value
  menu.value = null
  if (!salon || !props.membre) return
  const active = !m.reactions.some((r) => r.emoji === emoji && r.deMoi)
  try {
    const { data } = await api.POST('/api/communautes/{slug}/salons/{salon}/messages/{messageId}/reactions', {
      params: { path: { slug: props.slug, salon: salon.slug, messageId: m.id } },
      body: { emoji, active },
    })
    if (!data) throw new Error('refus')
    remplacer(data)
    erreurAction.value = null
  } catch {
    erreurAction.value = t('reactions.erreur')
  }
}
function ouvrirMenu(m: Message) {
  aSupprimer.value = null
  menu.value = menu.value === m.id ? null : m.id
}
function repondre(m: Message) {
  menu.value = null
  if (edition.value) texte.value = ''
  edition.value = null
  reponse.value = m
  void nextTick(() => composeur.value?.focus())
}
function modifier(m: Message) {
  menu.value = null
  reponse.value = null
  edition.value = m
  texte.value = m.texte
  void nextTick(() => composeur.value?.focus())
}
function annulerBandeau() {
  if (edition.value) texte.value = ''
  edition.value = null
  reponse.value = null
}
async function supprimer(m: Message) {
  const salon = actuel.value
  menu.value = null
  aSupprimer.value = null
  if (!salon) return
  try {
    const { response } = await api.DELETE('/api/communautes/{slug}/salons/{salon}/messages/{messageId}', {
      params: { path: { slug: props.slug, salon: salon.slug, messageId: m.id } },
    })
    if (!response.ok) throw new Error('refus')
    remplacer({ ...m, texte: '', piecesJointes: [], reactions: [], supprime: aMoi(m) ? 'auteur' : 'moderation' })
    if (edition.value?.id === m.id || reponse.value?.id === m.id) annulerBandeau()
    erreurAction.value = null
  } catch {
    erreurAction.value = t('reseau.actions.erreurSuppression')
  }
}
function texteSupprime(m: Message) {
  if (m.supprime === 'moderation') return t('communautes.salons.supprimeModeration')
  return aMoi(m) ? t('reseau.actions.tuAsSupprime') : t('reseau.actions.supprime')
}
const nomCite = (auteur: { id: string; nom: string } | null | undefined) =>
  auteur && moi.value && auteur.id === moi.value.id ? t('reseau.actions.toi') : (auteur?.nom ?? '')
function apercu(m: Message) {
  if (m.supprime) return t('reseau.actions.supprime')
  if (m.texte) return m.texte.replace(/[*_`~>#]/g, '').slice(0, 140)
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
function toucher(e: PointerEvent, m: Message) {
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

const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
/** Une couleur de nom stable par personne, prise dans la charte : on reconnaît qui parle d'un coup d'œil. */
const TEINTES = ['var(--action-ink)', 'var(--proof-ink)', 'var(--cantaloupe)', 'var(--ink)']
function teinte(id: string) {
  let h = 0
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return TEINTES[h % TEINTES.length]
}
</script>

<template>
  <section class="salon" :aria-label="actuel ? `# ${actuel.nom}` : t('communautes.salons.titre')" data-test="salons">
    <p v-if="etat === 'indisponible'" role="alert" class="salon__etat">
      {{ t('etat.indisponible') }}
      <JBouton variante="discret" @click="chargerSalons">{{ t('etat.reessayer') }}</JBouton>
    </p>

    <template v-else-if="actuel">
      <header class="salon__tete">
        <h1 class="salon__nom"><span aria-hidden="true">#</span> {{ actuel.nom }}</h1>
        <p class="salon__sujet">{{ actuel.sujet }}</p>
        <div class="salon__actions">
          <slot name="actions" />
          <!-- # général ne se supprime pas : c'est là qu'on arrive dans la communauté. -->
          <SupprimerSalon
            v-if="moderateur && actuel.slug !== 'general'"
            :slug="slug"
            :salon="actuel.slug"
            :nom="actuel.nom"
            :messages="messages.length"
            @supprime="emit('supprime', $event)"
          />
          <!-- Un appel en cours : on le rejoint. Sinon, un membre peut en lancer un. -->
          <template v-if="appel">
            <span class="en-cours" data-test="appel-en-cours">
              <span class="en-cours__point" aria-hidden="true" />
              {{ t('communautes.appels.enCours', { nom: appel.lancePar?.nom.split(' ')[0] ?? '…', quand: ilYa(appel.lanceLe, locale) }) }}
            </span>
            <a v-if="appel.url" :href="appel.url" target="_blank" rel="noopener noreferrer" class="appel" data-test="rejoindre-appel">
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
              <span>{{ t('communautes.appels.rejoindre') }}</span><span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
            </a>
            <button v-if="peutTerminer" type="button" class="terminer" data-test="terminer-appel" @click="terminerAppel">{{ t('communautes.appels.terminer') }}</button>
          </template>
          <button v-else-if="membre" type="button" class="appel" :disabled="etatAppel === 'envoi'" data-test="lancer-appel" @click="lancerAppel">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
            <span>{{ t('communautes.appels.lancer') }}</span>
          </button>
          <span v-if="etatAppel === 'trop' || etatAppel === 'erreur'" role="alert" class="appel__erreur">
            {{ etatAppel === 'trop' ? t('communautes.appels.trop') : t('etat.indisponible') }}
          </span>
        </div>
      </header>

      <div class="salon__fil motif fond-discussion" data-test="motif-discussion">
        <ol ref="liste" class="salon__messages" aria-live="polite" aria-relevant="additions" data-test="messages" @scroll.passive="defilement">
          <li class="accueil">
            <span class="accueil__signe" aria-hidden="true">#</span>
            <h2>{{ t('communautes.salons.bienvenue', { salon: actuel.nom }) }}</h2>
            <p>{{ actuel.sujet }} {{ t('communautes.salons.debut', { salon: actuel.nom }) }}</p>
          </li>
          <li v-if="premierChargement" class="salon__chargement texte-discret" role="status">{{ t('etat.chargement') }}</li>
          <template v-for="l in lignes" :key="l.cle">
            <li v-if="l.type === 'jour'" class="jour" role="separator"><span>{{ l.libelle }}</span></li>
            <li
              v-else
              class="message"
              :class="{ 'message--suite': l.suite, 'message--menu': menu === l.message.id, 'message--surligne': surligne === l.message.id, 'message--glisse': glisse?.id === l.message.id }"
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
              <span v-if="!l.suite" class="message__avatar"><LienPersonne :id="l.message.auteur.id" doublon><JAvatar :nom="l.message.auteur.nom" :url="l.message.auteur.avatarUrl" :taille="40" /></LienPersonne></span>
              <time v-else class="message__heure-survol" :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
              <div class="message__corps" :style="glisse?.id === l.message.id ? { transform: `translateX(${glisse.dx}px)` } : undefined">
                <p v-if="!l.suite" class="message__meta">
                  <strong :style="{ color: teinte(l.message.auteur.id) }"><LienPersonne :id="l.message.auteur.id">{{ l.message.auteur.nom }}</LienPersonne></strong>
                  <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
                </p>
                <button v-if="l.message.reponseA && !l.message.supprime" type="button" class="citation" data-test="citation" @click="allerA(l.message.reponseA.id)">
                  <strong>{{ nomCite(l.message.reponseA.auteur) }}</strong>
                  <span>{{ l.message.reponseA.supprime ? t('reseau.actions.supprime') : l.message.reponseA.texte }}</span>
                </button>
                <p v-if="l.message.supprime" class="message__supprime" data-test="message-supprime">{{ texteSupprime(l.message) }}</p>
                <div v-else-if="l.message.appel" class="annonce" :class="{ 'annonce--finie': !l.message.appel.enCours }" data-test="annonce-appel">
                  <span class="annonce__icone" aria-hidden="true">📞</span>
                  <span>{{ l.message.appel.enCours ? t('communautes.appels.annonce') : t('communautes.appels.termine') }}</span>
                  <a v-if="rejoignable(l.message)" :href="appel!.url!" target="_blank" rel="noopener noreferrer" class="annonce__rejoindre">
                    {{ t('communautes.appels.rejoindre') }}<span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
                  </a>
                </div>
                <template v-else>
                  <TexteRiche v-if="l.message.texte" :texte="l.message.texte" class="message__texte" />
                  <PiecesJointes :pieces="l.message.piecesJointes ?? []" />
                  <span v-if="l.message.modifieLe" class="message__modifie" data-test="modifie">({{ t('reseau.actions.modifie') }})</span>
                </template>
                <PastillesReactions v-if="!l.message.supprime" :reactions="l.message.reactions" @basculer="(e) => reagir(l.message, e)" />
              </div>

              <!-- Les actions, pour les membres : un « ⋯ » en haut à droite du message. -->
              <div v-if="actionnable(l.message)" class="actions">
                <button type="button" class="actions__ouvrir" :aria-expanded="menu === l.message.id" :aria-label="t('reseau.actions.menu')" data-test="actions-message" @click.stop="ouvrirMenu(l.message)">
                  <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="4.5" cy="10" r="1.6" fill="currentColor" /><circle cx="10" cy="10" r="1.6" fill="currentColor" /><circle cx="15.5" cy="10" r="1.6" fill="currentColor" /></svg>
                </button>
                <div v-if="menu === l.message.id" class="actions__menu" role="menu">
                  <template v-if="aSupprimer !== l.message.id">
                    <BarreReactions :actives="l.message.reactions.filter((r) => r.deMoi).map((r) => r.emoji)" @choisir="(e) => reagir(l.message, e)" />
                    <button type="button" role="menuitem" data-test="action-repondre" @click="repondre(l.message)">{{ t('reseau.actions.repondre') }}</button>
                    <button v-if="modifiable(l.message)" type="button" role="menuitem" data-test="action-modifier" @click="modifier(l.message)">{{ t('reseau.actions.modifier') }}</button>
                    <button v-if="supprimable(l.message)" type="button" role="menuitem" class="danger" data-test="action-supprimer" @click.stop="aSupprimer = l.message.id">{{ t('reseau.actions.supprimer') }}</button>
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
        <button v-if="nonVus > 0" type="button" class="nouveaux" data-test="nouveaux" @click="allerEnBas">
          {{ t('communautes.salons.nouveauxMessages', { n: nonVus }, nonVus) }} ↓
        </button>
      </div>

      <p v-if="coupe" class="salon__coupe" role="status">{{ t('communautes.salons.coupe') }}</p>

      <form v-if="membre" class="ecrire" data-test="ecrire" @submit.prevent="envoyer">
        <p v-if="erreurAction" class="erreur-action" role="alert">{{ erreurAction }}</p>
        <!-- Je réponds à un message, ou j'en corrige un : le bandeau le rappelle au-dessus de la saisie. -->
        <div v-if="reponse || edition" class="bandeau" data-test="bandeau-action">
          <span class="bandeau__texte">
            <strong>{{ edition ? t('reseau.actions.enModification') : t('reseau.actions.repondreA', { nom: nomCite(reponse!.auteur) }) }}</strong>
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
          :id-zone="`ecrire-${actuel.slug}`"
          :placeholder="t('communautes.salons.ecrireDans', { salon: actuel.nom })"
          :libelle-envoyer="edition ? t('reseau.actions.enregistrer') : t('communautes.salons.envoyer')"
          :envoi="envoi === 'envoi'"
          :erreur="envoi === 'trop' ? t('communautes.salons.trop') : envoi === 'erreur' ? t('etat.indisponible') : erreurVocal"
          :max="LONGUEUR"
          :masquer-envoi="vocalEnCours && !edition"
          @envoyer="envoyer"
        >
          <template v-if="!edition" #action>
            <EnregistreurVocal :desactive="envoi === 'envoi'" @envoyer="envoyerVocal" @en-cours="(actif) => (vocalEnCours = actif)" @erreur="(code) => (erreurVocal = t(`vocal.erreurs.${code}`))" />
          </template>
        </Composeur>
      </form>
      <div v-else class="rejoindre">
        <p>{{ t('communautes.salons.rejoindre') }}</p>
        <JBouton :occupe="adhesionEnCours" data-test="rejoindre-salon" @click="emit('rejoindre')">{{ t('communautes.rejoindre') }}</JBouton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.salon { flex: 1; min-height: 0; display: flex; flex-direction: column; }

/* Réagir, répondre, corriger, supprimer (#157) : le même langage que le salon d'un cercle. */
.message { position: relative; touch-action: pan-y; }
.message--menu, .message--surligne { background: var(--surface-raised); }
.message--surligne { box-shadow: inset 3px 0 0 var(--action); transition: box-shadow 200ms ease-out; }
.message:not(.message--glisse) .message__corps { transition: transform 300ms var(--ease-pop); }
.message__repondre { position: absolute; left: 4px; top: 50%; display: grid; place-items: center; width: 30px; height: 30px; margin-top: -15px; border-radius: 50%; background: var(--surface); color: var(--ink-muted); opacity: 0.6; }
.message__repondre--pret { opacity: 1; color: var(--on-action, #fff); background: var(--action); }
.message__supprime { margin: 0; color: var(--ink-muted); font-style: italic; }
.message__modifie { color: var(--ink-muted); font-size: 0.75rem; font-style: italic; }
.citation {
  display: grid; gap: 1px; justify-items: start; min-width: 0; max-width: 40rem; margin-bottom: 2px; padding: 6px 10px; border: 0; border-left: 3px solid var(--action);
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
.erreur-action { margin: 0 0 var(--space-2); color: var(--danger); font-size: var(--type-body-sm-size); font-weight: 600; }
@media (prefers-reduced-motion: reduce) { .actions__menu, .message__corps { animation: none; transition: none; } }
.salon__etat { margin: var(--space-6); }

.salon__tete {
  display: flex; align-items: center; gap: var(--space-3); min-height: 56px; padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line); box-shadow: 0 1px 0 color-mix(in srgb, var(--ink) 4%, transparent);
}
.salon__nom { margin: 0; flex-shrink: 0; font-size: 1.0625rem; font-family: var(--font-text); font-weight: 700; }
.salon__nom span { color: var(--ink-muted); font-weight: 500; }
.salon__sujet {
  margin: 0; flex: 1; min-width: 0; padding-inline-start: var(--space-3); border-inline-start: 1px solid var(--line);
  color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.salon__actions { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }
.appel {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-3);
  border-radius: var(--radius-pill); background: color-mix(in srgb, #23a55a 14%, transparent); color: #1a7f45;
  font-weight: 600; font-size: var(--type-body-sm-size); text-decoration: none;
}
:root[data-theme='sombre'] .appel { color: #57d68d; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .appel { color: #57d68d; } }
.appel:hover { background: color-mix(in srgb, #23a55a 24%, transparent); }
.appel:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
button.appel { border: 0; font: inherit; font-weight: 600; font-size: var(--type-body-sm-size); cursor: pointer; }
button.appel:disabled { opacity: 0.6; cursor: progress; }
.appel__erreur { color: var(--danger); font-size: 0.8125rem; font-weight: 600; }
.en-cours { display: inline-flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: 0.8125rem; }
.en-cours__point { width: 8px; height: 8px; border-radius: 50%; background: #23a55a; animation: pouls-appel 2s infinite; }
@keyframes pouls-appel { 0% { box-shadow: 0 0 0 0 rgb(35 165 90 / 0.6); } 70% { box-shadow: 0 0 0 7px transparent; } 100% { box-shadow: 0 0 0 0 transparent; } }
@media (prefers-reduced-motion: reduce) { .en-cours__point { animation: none; } }
.terminer {
  min-height: 36px; padding: 0 var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong);
  background: transparent; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer;
}
.terminer:hover { border-color: var(--danger); color: var(--danger); }
.terminer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.annonce {
  display: inline-flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md); background: color-mix(in srgb, #23a55a 12%, transparent); font-weight: 600; font-size: var(--type-body-sm-size);
}
.annonce--finie { background: var(--surface-raised); color: var(--ink-muted); font-weight: 500; }
.annonce__rejoindre { padding: 2px 12px; border-radius: var(--radius-pill); background: #1a7f45; color: #fff; text-decoration: none; font-weight: 700; }
.annonce__rejoindre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.salon__fil { position: relative; flex: 1; min-height: 0; display: flex; }
.salon__messages { flex: 1; list-style: none; margin: 0; padding: var(--space-4) 0 var(--space-3); overflow-y: auto; overscroll-behavior: contain; display: flex; flex-direction: column; }
/* Les messages se posent en bas, comme dans une messagerie, même quand il y en a peu. */
.salon__messages > :first-child { margin-top: auto; }

.accueil { padding: var(--space-6) var(--space-4) var(--space-4); }
.accueil__signe {
  display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%;
  background: var(--surface-raised); color: var(--ink); font-size: 2.25rem; font-weight: 400;
}
.accueil h2 { margin: var(--space-3) 0 var(--space-1); font-size: 1.5rem; }
.accueil p { margin: 0; color: var(--ink-muted); }
.salon__chargement { padding: 0 var(--space-4); }

.jour { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-4) var(--space-4) var(--space-2); color: var(--ink-muted); font-size: 0.75rem; font-weight: 600; }
.jour::before, .jour::after { content: ''; flex: 1; border-top: 1px solid var(--line); }

.message {
  position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3);
  padding: 2px var(--space-4); margin-top: var(--space-3);
}
.message--suite { margin-top: 0; }
.message:hover { background: color-mix(in srgb, var(--ink) 3%, transparent); }
.message p { margin: 0; }
.message__meta { display: flex; align-items: baseline; gap: var(--space-2); }
.message__meta strong { font-size: 0.9375rem; }
.message__meta time { color: var(--ink-muted); font-size: 0.75rem; }
.message__heure-survol { visibility: hidden; align-self: center; color: var(--ink-muted); font-size: 0.6875rem; text-align: center; }
.message:hover .message__heure-survol { visibility: visible; }
.message__texte { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.45; }

.nouveaux {
  position: absolute; bottom: var(--space-3); left: 50%; transform: translateX(-50%);
  padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600;
  box-shadow: var(--ombre-carte); cursor: pointer;
}
.nouveaux:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.salon__coupe { margin: 0; padding: 0 var(--space-4) var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.ecrire { padding: 0 var(--space-4) var(--space-3); }

.rejoindre {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3);
  margin: 0 var(--space-4) var(--space-3); padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--action-soft);
}
.rejoindre p { margin: 0; font-weight: 600; }

@media (max-width: 760px) {
  .salon__sujet { display: none; }
  .appel span:first-of-type { display: none; }
  .ecrire__aide { display: none; }
}
</style>
