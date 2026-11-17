<script setup lang="ts">
import Composeur from '../../components/texte/Composeur.vue'
import EnregistreurVocal from '../../components/texte/EnregistreurVocal.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import TransfertMessage from './TransfertMessage.vue'
import BarreReactions from '../../components/texte/BarreReactions.vue'
import PastillesReactions from '../../components/texte/PastillesReactions.vue'
import { textePresence, usePresence } from './presence'
import { envoyerFichier, type PieceJointe } from '../../components/texte/fichiers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { rejoindreCercle } from '../cercles/cercles'
import { rafraichirActivite } from './activite'
import { usePleinEcranTelephone } from '../../components/pleinEcran'
import { appeler, useAppel } from './appel'
import {
  reagirMessage,
  ajouterContact,
  envoyerMessage,
  LONGUEUR_MESSAGE,
  lireConversation,
  lireEcriture,
  signalerEcriture,
  lireLien,
  modifiable,
  modifierMessage,
  prenom,
  supprimerMessage,
  transferable,
  type Message,
  type Personne,
  type StatutLien,
} from './reseau'

/*
 * La conversation avec une personne, comme une messagerie : l'en-tête avec l'appel, le fil groupé par jour, et la
 * zone de saisie collée en bas (Entrée envoie, Maj + Entrée va à la ligne). Le fil se relit toutes les 5 secondes
 * tant que l'onglet est visible : pas de WebSocket, comme les salons. Les textes restent du texte brut (jamais v-html).
 */
const emit = defineEmits<{ actualise: [] }>()
const { t, locale } = useI18n()
// Sur téléphone, la conversation ouverte prend tout l'écran : son bandeau, le fil, la zone pour écrire.
usePleinEcranTelephone(() => true)
const route = useRoute()
const routeur = useRouter()

const personne = ref<Personne | null>(null)
const messages = ref<Message[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const fil = ref<HTMLElement | null>(null)
let versionConversation = 0
/** Les messages présents à l'ouverture : ils sont là, sans animation. Seuls les nouveaux arrivent en glissant. */
let dejaLa = new Set<string>()

let actif = true

async function charger() {
  const version = ++versionConversation
  texte.value = ''
  pieces.value = []
  envoi.value = false
  erreur.value = null
  etat.value = 'chargement'
  try {
    const r = await lireConversation(String(route.params.personneId))
    if (!actif || version !== versionConversation) return
    if (!r) {
      etat.value = 'introuvable'
      return
    }
    personne.value = r.personne
    dejaLa = new Set(r.messages.map((m) => m.id))
    messages.value = r.messages
    etat.value = 'pret'
    const contact = await lireLien(r.personne.id).catch(() => null)
    if (!actif || version !== versionConversation) return
    lien.value = contact
    // La lecture a marqué la conversation comme lue : la pastille du menu se met à jour tout de suite.
    void rafraichirActivite()
    emit('actualise')
    await descendre()
  } catch {
    if (actif && version === versionConversation) etat.value = 'indisponible'
  }
}


/** Relire en silence : les nouveaux messages arrivent sans recharger l'écran. */
async function relire() {
  if (etat.value !== 'pret' || (typeof document !== 'undefined' && document.visibilityState === 'hidden')) return
  const version = versionConversation
  try {
    const r = await lireConversation(String(route.params.personneId))
    if (!r || !actif || version !== versionConversation) return
    const dernier = messages.value.at(-1)?.id
    const enBas = !fil.value || fil.value.scrollHeight - fil.value.scrollTop - fil.value.clientHeight < 80
    messages.value = r.messages
    if (r.messages.at(-1)?.id !== dernier) {
      emit('actualise')
      void rafraichirActivite()
      if (enBas) await descendre(true)
    }
  } catch {
    // La prochaine relecture réessaiera.
  }
}
const minuterie = setInterval(() => void relire(), 5000)

onBeforeUnmount(() => { actif = false; clearInterval(minuterie); clearInterval(minuterieEcriture) })

/** Descendre en bas du fil : d'un coup à l'ouverture, en douceur ensuite (sauf mouvement réduit). */
async function descendre(doux = false) {
  await nextTick()
  const f = fil.value
  if (!f) return
  const calme = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  if (doux && !calme && typeof f.scrollTo === 'function') f.scrollTo({ top: f.scrollHeight, behavior: 'smooth' })
  else f.scrollTop = f.scrollHeight
}

/** Le fil groupé par jour : « Aujourd'hui », « Hier », puis la date. */
const jours = computed(() => {
  const groupes: { cle: string; titre: string; messages: Message[] }[] = []
  for (const m of messages.value) {
    const cle = new Date(m.envoyeLe).toDateString()
    if (groupes.at(-1)?.cle !== cle) groupes.push({ cle, titre: titreJour(m.envoyeLe), messages: [] })
    groupes.at(-1)!.messages.push(m)
  }
  return groupes
})
function titreJour(iso: string) {
  const date = new Date(iso)
  const aujourdhui = new Date()
  const hier = new Date(aujourdhui)
  hier.setDate(aujourdhui.getDate() - 1)
  if (date.toDateString() === aujourdhui.toDateString()) return t('reseau.discussion.aujourdhui')
  if (date.toDateString() === hier.toDateString()) return t('reseau.discussion.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(date)
}
const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
/** Deux bulles de suite de la même personne se collent : l'avatar n'est montré qu'une fois. */
const suite = (liste: Message[], i: number) => i > 0 && liste[i - 1]!.deMoi === liste[i]!.deMoi

// Le lien de contact : un seul bouton, qui dit ce qu'il fera (demander, accepter) ou l'état (demande envoyée, contact).
const lien = ref<StatutLien | null>(null)
const ajout = ref(false)
async function ajouter() {
  if (!personne.value) return
  ajout.value = true
  try {
    lien.value = (await ajouterContact(personne.value.id)) ?? lien.value
    void rafraichirActivite()
  } finally {
    ajout.value = false
  }
}

// ---------------------------------------------------------------- écrire
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
/** Pendant un message vocal, le bouton d’envoi du composeur laisse la place à celui de l’enregistreur. */
const vocalEnCours = ref(false)
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
const valide = computed(() =>
  (texte.value.trim().length > 0 || pieces.value.length > 0 || (!!edition.value && edition.value.piecesJointes.length > 0)) && texte.value.length <= LONGUEUR_MESSAGE)

async function envoyer() {
  if (!valide.value || !personne.value || envoi.value) return
  const version = versionConversation
  envoi.value = true
  erreur.value = null
  try {
    const enCours = edition.value
    const r = enCours
      ? await modifierMessage(personne.value.id, enCours.id, texte.value)
      : await envoyerMessage(personne.value.id, texte.value, undefined, pieces.value.map((p) => p.id), reponse.value?.id)
    if (!actif || version !== versionConversation) return
    if (r.ok) {
      if (enCours) remplacer(r.message)
      else messages.value.push(r.message)
      emit('actualise')
      texte.value = ''
      pieces.value = []
      reponse.value = null
      edition.value = null
      composeur.value?.rafraichir()
      if (!enCours) await descendre(true)
      composeur.value?.focus()
    } else erreur.value = t(`reseau.erreurs.${r.code ?? 'autre'}`, t('reseau.erreurs.autreMessage'))
  } catch {
    if (actif && version === versionConversation) erreur.value = t('etat.indisponible')
  } finally {
    if (actif && version === versionConversation) envoi.value = false
  }
}

// ---------------------------------------------------------------- répondre, modifier, supprimer, transférer (#157)

const reponse = ref<Message | null>(null)
const edition = ref<Message | null>(null)
const menu = ref<string | null>(null)
const aSupprimer = ref<string | null>(null)
const aTransferer = ref<Message | null>(null)
const info = ref<string | null>(null)
const surligne = ref<string | null>(null)
const maintenant = ref(Date.now())
const horloge = setInterval(() => (maintenant.value = Date.now()), 30_000)

function remplacer(m: Message) {
  messages.value = messages.value.map((x) => (x.id === m.id ? m : x))
}
/** Réagir avec un emoji (#157) : un deuxième clic sur le même retire ma réaction. */
async function reagir(m: Message, emoji: string) {
  if (!personne.value) return
  menu.value = null
  const active = !m.reactions.some((r) => r.emoji === emoji && r.deMoi)
  try {
    const reactions = await reagirMessage(personne.value.id, m.id, emoji, active)
    if (!reactions) throw new Error('refus')
    const actuel = messages.value.find((x) => x.id === m.id)
    if (actuel) remplacer({ ...actuel, reactions })
  } catch {
    erreur.value = t('reactions.erreur')
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
  composeur.value?.focus()
}
function modifier(m: Message) {
  menu.value = null
  reponse.value = null
  edition.value = m
  texte.value = m.texte
  composeur.value?.focus()
}
function annulerBandeau() {
  if (edition.value) texte.value = ''
  edition.value = null
  reponse.value = null
}
function transferer(m: Message) {
  menu.value = null
  aTransferer.value = m
}
function transfere(n: number) {
  aTransferer.value = null
  annoncer(t('reseau.actions.transfert.envoye', { n }, n))
}
function annoncer(texteInfo: string) {
  info.value = texteInfo
  setTimeout(() => {
    if (info.value === texteInfo) info.value = null
  }, 3_500)
}
async function supprimer(m: Message) {
  if (!personne.value) return
  menu.value = null
  aSupprimer.value = null
  try {
    if (!(await supprimerMessage(personne.value.id, m.id))) throw new Error('refus')
    remplacer({ ...m, texte: '', piecesJointes: [], invitation: null, appel: null, supprime: true })
    if (edition.value?.id === m.id || reponse.value?.id === m.id) annulerBandeau()
    emit('actualise')
  } catch {
    erreur.value = t('reseau.actions.erreurSuppression')
  }
}

/** Ce que montre une citation ou le bandeau : le texte, ou la sorte de la pièce jointe. */
function apercu(texteMessage: string, sorte: string | null | undefined, supprime: boolean) {
  if (supprime) return t('reseau.actions.supprime')
  if (texteMessage) return texteMessage.replace(/[*_`~>#]/g, '').slice(0, 140)
  return sorte ? t(`reseau.actions.sortes.${sorte}`) : ''
}
const apercuMessage = (m: Message) => apercu(m.texte, m.piecesJointes[0]?.sorte, m.supprime)
const auteur = (deMoi: boolean) => (deMoi ? t('reseau.actions.toi') : prenom(personne.value?.nom ?? ''))

/** Un clic sur une citation remonte au message cité, s'il est dans la page, et le fait briller un instant. */
async function allerA(id: string) {
  const cible = fil.value?.querySelector<HTMLElement>(`[data-message="${id}"]`)
  if (!cible) return
  cible.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  surligne.value = id
  setTimeout(() => {
    if (surligne.value === id) surligne.value = null
  }, 1_400)
}

// Glisser une bulle vers la droite (au doigt) pour y répondre, comme dans les messageries mobiles.
const SEUIL_GLISSE = 64 as const
const glisse = ref<{ id: string; dx: number } | null>(null)
let depart: { id: string; x: number; y: number; horizontal: boolean | null } | null = null
function toucher(e: PointerEvent, m: Message) {
  if (e.pointerType !== 'touch' || m.supprime || m.appel) return
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

// Fermer le menu d'un clic ailleurs ou avec Échap.
function clicAilleurs(e: MouseEvent) {
  if (menu.value && !(e.target as Element | null)?.closest?.('.actions')) menu.value = null
}
function echap(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (menu.value) menu.value = null
  else if (reponse.value || edition.value) annulerBandeau()
}
onMounted(() => {
  document.addEventListener('click', clicAilleurs)
  document.addEventListener('keydown', echap)
})
onBeforeUnmount(() => {
  clearInterval(horloge)
  document.removeEventListener('click', clicAilleurs)
  document.removeEventListener('keydown', echap)
})
watch(() => route.params.personneId, () => {
  reponse.value = null
  edition.value = null
  menu.value = null
  aTransferer.value = null
})

// ---------------------------------------------------------------- un message vocal (#157)

/** Le son est envoyé comme un fichier privé de la conversation, puis joint seul à un message. */
async function envoyerVocal(fichier: File) {
  if (!personne.value || envoi.value) return
  const version = versionConversation
  const destinataire = personne.value.id
  envoi.value = true
  erreur.value = null
  try {
    const f = await envoyerFichier(fichier, destinataire, undefined, true)
    if (!actif || version !== versionConversation) return
    if (!f.ok) {
      erreur.value = t(`vocal.erreurs.${f.code}`)
      return
    }
    const r = await envoyerMessage(destinataire, '', undefined, [f.piece.id], reponse.value?.id)
    if (!actif || version !== versionConversation) return
    if (r.ok) {
      reponse.value = null
      messages.value.push(r.message)
      emit('actualise')
      await descendre(true)
    } else erreur.value = t(`reseau.erreurs.${r.code ?? 'autre'}`, t('reseau.erreurs.autreMessage'))
  } catch {
    if (actif && version === versionConversation) erreur.value = t('etat.indisponible')
  } finally {
    if (actif && version === versionConversation) envoi.value = false
  }
}

// ---------------------------------------------------------------- appeler (#157)
watch(() => route.params.personneId, charger, { immediate: true })
const { etat: etatAppel } = useAppel()
const occupe = computed(() => etatAppel.phase !== 'repos' && etatAppel.phase !== 'fini')

/** L'appel se passe dans Jamii, en WebRTC (features/reseau/appel.ts) : la fenêtre d'appel s'ouvre par-dessus la page. */
async function appelerPersonne(video: boolean) {
  if (!personne.value || occupe.value) return
  erreur.value = null
  await appeler(personne.value, video)
  if (etatAppel.phase === 'fini' && etatAppel.fin && ['impossible', 'trop_de_requetes'].includes(etatAppel.fin)) {
    erreur.value = t(`appels.fin.${etatAppel.fin}`)
  }
}

/** Ce que dit la carte d'un appel dans le fil. */
function texteAppel(m: Message): string {
  const a = m.appel!
  const nom = personne.value ? prenom(personne.value.nom) : ''
  if (!a.id) return a.enCours ? t('reseau.discussion.enCours') : t('reseau.discussion.termine')
  if (a.statut === 'termine' && a.dureeSecondes != null) return t('appels.carte.duree', { duree: dureeLisible(a.dureeSecondes) })
  if (a.statut === 'manque') return m.deMoi ? t('appels.carte.sansReponse') : t('appels.carte.manque')
  if (a.statut === 'refuse') return m.deMoi ? t('appels.carte.refuse', { nom }) : t('appels.carte.tuAsRefuse')
  if (a.statut === 'annule') return t('appels.carte.annule')
  return a.enCours ? t('reseau.discussion.enCours') : t('reseau.discussion.termine')
}

function dureeLisible(secondes: number): string {
  if (secondes < 60) return t('appels.carte.secondes', { n: secondes })
  const minutes = Math.round(secondes / 60)
  return minutes < 60 ? t('appels.carte.minutes', { n: minutes }) : t('appels.carte.heures', { h: Math.floor(minutes / 60), m: minutes % 60 })
}

// ---------------------------------------------------------------- « … est en train d'écrire » (#157)
/** Pendant la frappe, on le signale au plus toutes les 3 s ; l'autre le lit toutes les 2 s (Ecriture.java le garde 6 s). */
const SIGNAL_ECRITURE_MS = 3_000
const LECTURE_ECRITURE_MS = 2_000
const autreEcrit = ref(false)
// « En ligne » ou « Vu il y a … » sous le nom (#157).
const presences = usePresence(() => (personne.value ? [personne.value.id] : []))
const presenceAutre = computed(() => (personne.value ? presences.value.get(personne.value.id) : undefined))
const statutAutre = computed(() => textePresence(presenceAutre.value, t, locale.value))
let dernierSignal = 0
watch(texte, (valeur) => {
  if (!personne.value || !valeur.trim()) return
  const maintenant = Date.now()
  if (maintenant - dernierSignal < SIGNAL_ECRITURE_MS) return
  dernierSignal = maintenant
  void signalerEcriture(personne.value.id)
})
async function lireSiEcrit() {
  if (etat.value !== 'pret' || !personne.value || (typeof document !== 'undefined' && document.visibilityState === 'hidden')) return
  const id = personne.value.id
  const ecrit = await lireEcriture(id)
  if (actif && personne.value?.id === id) {
    const enBas = !fil.value || fil.value.scrollHeight - fil.value.scrollTop - fil.value.clientHeight < 80
    autreEcrit.value = ecrit
    if (ecrit && enBas) await descendre(true)
  }
}
const minuterieEcriture = setInterval(() => void lireSiEcrit(), LECTURE_ECRITURE_MS)
watch(() => route.params.personneId, () => { autreEcrit.value = false; dernierSignal = 0 })

// Rejoindre depuis l'invitation : la même route que le bouton de la page du cercle (cercles.yaml).
const rejoint = ref<string | null>(null)
const erreurInvitation = ref<string | null>(null)
async function rejoindre(cercleId: string) {
  rejoint.value = cercleId
  erreurInvitation.value = null
  try {
    const r = await rejoindreCercle(cercleId)
    if (r.ok) await routeur.push(`/cercles/${cercleId}`)
    // Déjà membre : on y va quand même, c'est ce que la personne voulait.
    else if (r.code === 'deja_membre') await routeur.push(`/cercles/${cercleId}`)
    else erreurInvitation.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreurInvitation.value = t('etat.indisponible')
  } finally {
    rejoint.value = null
  }
}
const placeLibre = (m: Message) => !!m.invitation && m.invitation.cercle.statut === 'ouvert' && m.invitation.cercle.nombreMembres < m.invitation.cercle.taille
</script>

<template>
  <section class="discussion" :aria-busy="etat === 'chargement'">
    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/messages">{{ t('reseau.messages.tous') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <div v-else-if="personne" class="cadre">
      <!-- Le fond de la discussion : de petits dessins tech, fixes pendant que les messages défilent. Décoratif. -->
      <div class="cadre__motif motif fond-discussion" aria-hidden="true" data-test="motif-discussion" />
      <header class="tete verre">
        <RouterLink to="/messages" class="tete__retour" :aria-label="t('reseau.messages.tous')">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M12.5 4.5 7 10l5.5 5.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </RouterLink>
        <span class="avatar-presence">
          <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="44" />
          <span v-if="presenceAutre?.enLigne" class="point-en-ligne" aria-hidden="true" />
        </span>
        <!-- Deux lignes : le nom (qui mène au profil) et le lien de contact, puis la présence et la ville. -->
        <div class="tete__qui">
          <div class="tete__ligne">
            <h2><RouterLink :to="`/p/${personne.id}`" class="tete__nom" :title="t('reseau.messages.voirProfil')">{{ personne.nom }}</RouterLink></h2>
            <span v-if="lien && lien !== 'aucun' && lien !== 'recue'" class="tete__lien" data-test="lien-contact">{{ t(`reseau.contacts.statut.${lien}`) }}</span>
          </div>
          <p class="tete__sous">
            <span v-if="statutAutre" class="tete__presence" :class="{ 'tete__presence--en-ligne': presenceAutre?.enLigne }" data-test="presence">{{ statutAutre }}</span>
            <span v-if="statutAutre && personne.ville" class="tete__ville" aria-hidden="true">·</span>
            <span v-if="personne.ville" class="tete__ville">{{ personne.ville }}</span>
          </p>
        </div>
        <div class="tete__actions">
          <JBouton v-if="lien === 'aucun' || lien === 'recue'" class="ajout-contact" :variante="lien === 'recue' ? 'principal' : 'secondaire'" :occupe="ajout" data-test="ajouter-contact" @click="ajouter">
            <svg class="ajout-contact__icone" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><circle cx="10" cy="8" r="3.6" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="M3.5 19a6.5 6.5 0 0 1 13 0M19 8v6M16 11h6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
            <span class="ajout-contact__texte">{{ lien === 'recue' ? t('reseau.contacts.accepter') : t('reseau.contacts.ajouter') }}</span>
          </JBouton>
          <button type="button" class="rond rond--appel" :disabled="occupe" :aria-label="t('reseau.discussion.appeler', { nom: prenom(personne.nom) })" :title="t('reseau.discussion.appeler', { nom: prenom(personne.nom) })" data-test="appeler" @click="appelerPersonne(false)">
            <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
          </button>
          <button type="button" class="rond rond--appel" :disabled="occupe" :aria-label="t('appels.appelVideo', { nom: prenom(personne.nom) })" :title="t('appels.appelVideo', { nom: prenom(personne.nom) })" data-test="appeler-video" @click="appelerPersonne(true)">
            <svg viewBox="0 0 24 24" width="21" height="21" aria-hidden="true"><path d="M4 7a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2.5l3.4-2.3A1 1 0 0 1 21 8v8a1 1 0 0 1-1.6.8L16 14.5V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z" fill="currentColor" /></svg>
          </button>
        </div>
      </header>

      <div ref="fil" class="fil" data-test="fil-defilant">
        <div v-if="!messages.length" class="accueil" data-test="conversation-vide">
          <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="72" />
          <strong>{{ personne.nom }}</strong>
          <p>{{ t('reseau.messages.premier', { nom: prenom(personne.nom) }) }}</p>
        </div>

        <ol class="jours" data-test="fil" aria-live="polite">
          <template v-for="j in jours" :key="j.cle">
            <li class="jour" aria-hidden="true"><span>{{ j.titre }}</span></li>
            <li
              v-for="(m, i) in j.messages"
              :key="m.id"
              class="ligne"
              :class="[m.deMoi ? 'bulle--moi' : 'bulle--autre', { 'ligne--suite': suite(j.messages, i), 'ligne--surlignee': surligne === m.id, 'ligne--menu': menu === m.id, 'ligne--glisse': glisse?.id === m.id, 'ligne--arrivee': !dejaLa.has(m.id) }]"
              :data-message="m.id"
              @pointerdown="toucher($event, m)"
              @pointermove="bouger"
              @pointerup="lacher"
              @pointercancel="lacher"
            >
              <span v-if="glisse?.id === m.id" class="ligne__repondre" :class="{ 'ligne__repondre--pret': glisse.dx >= SEUIL_GLISSE }" aria-hidden="true">
                <svg viewBox="0 0 20 20" width="18" height="18"><path d="M8 5 3.5 9.5 8 14M4 9.5h7.5a5 5 0 0 1 5 5v1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </span>
              <span class="ligne__avatar" aria-hidden="true">
                <JAvatar v-if="!m.deMoi && !suite(j.messages, i)" :nom="personne.nom" :url="personne.avatarUrl" :taille="28" />
              </span>

              <!-- La trace d'un appel : qui a appelé, comment ça s'est fini, et « Rappeler ». Les anciens appels Jitsi gardent leur lien. -->
              <div v-if="m.appel" class="appel-carte" :class="{ 'appel-carte--fini': !m.appel.enCours, 'appel-carte--manque': m.appel.statut === 'manque' && !m.deMoi }" data-test="appel">
                <span class="appel-carte__icone" aria-hidden="true">
                  <svg v-if="m.appel.video" viewBox="0 0 24 24" width="18" height="18"><path d="M4 7a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2.5l3.4-2.3A1 1 0 0 1 21 8v8a1 1 0 0 1-1.6.8L16 14.5V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z" fill="currentColor" /></svg>
                  <svg v-else viewBox="0 0 20 20" width="18" height="18"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
                </span>
                <span class="appel-carte__texte">
                  <strong>{{ m.deMoi ? t(m.appel.video ? 'appels.carte.tuAppellesVideo' : 'reseau.discussion.tuAppelles') : t(m.appel.video ? 'appels.carte.tAppelleVideo' : 'reseau.discussion.tAppelle', { nom: prenom(personne.nom) }) }}</strong>
                  <span data-test="appel-statut">{{ texteAppel(m) }} · {{ heure(m.envoyeLe) }}</span>
                </span>
                <a v-if="m.appel.url && m.appel.enCours" :href="m.appel.url" target="_blank" rel="noopener noreferrer" class="appel-carte__rejoindre" data-test="rejoindre-appel">
                  {{ t('reseau.discussion.rejoindre') }}<span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
                </a>
                <button v-else-if="m.appel.id && !m.appel.enCours" type="button" class="appel-carte__rejoindre" :disabled="occupe" data-test="rappeler" @click="appelerPersonne(m.appel.video)">
                  {{ t('appels.rappeler') }}
                </button>
              </div>

              <div v-else-if="m.supprime" class="bulle bulle--supprimee" data-test="message-supprime">
                <span class="bulle__supprime">
                  <svg viewBox="0 0 20 20" width="15" height="15" aria-hidden="true"><circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="1.6" /><path d="m5.2 14.8 9.6-9.6" stroke="currentColor" stroke-width="1.6" /></svg>
                  {{ m.deMoi ? t('reseau.actions.tuAsSupprime') : t('reseau.actions.supprime') }}
                </span>
                <span class="bulle__meta"><time :datetime="m.envoyeLe">{{ heure(m.envoyeLe) }}</time></span>
              </div>

              <div v-else class="bulle" :style="glisse?.id === m.id ? { transform: `translateX(${glisse.dx}px)` } : undefined">
                <span v-if="m.transfere" class="bulle__transfere" data-test="transfere">
                  <svg viewBox="0 0 20 20" width="13" height="13" aria-hidden="true"><path d="M12 5l4.5 4.5L12 14M16 9.5H8.5a5 5 0 0 0-5 5v1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  {{ t('reseau.actions.transfere') }}
                </span>
                <button v-if="m.reponseA" type="button" class="citation" data-test="citation" @click="allerA(m.reponseA.id)">
                  <strong>{{ auteur(m.reponseA.deMoi) }}</strong>
                  <span>{{ apercu(m.reponseA.texte, m.reponseA.sorte, m.reponseA.supprime) }}</span>
                </button>
                <TexteRiche v-if="m.texte" class="bulle__texte" :texte="m.texte" />
                <PiecesJointes :pieces="m.piecesJointes ?? []" />
                <!-- L'invitation : le cercle tel qu'il est maintenant, et le bouton pour le rejoindre s'il reste de la place. -->
                <div v-if="m.invitation" class="invitation" data-test="invitation">
                  <span class="invitation__titre">{{ t('reseau.messages.invitation') }}</span>
                  <RouterLink :to="`/cercles/${m.invitation.cercle.id}`" class="invitation__cercle">{{ m.invitation.cercle.nom }}</RouterLink>
                  <span class="invitation__infos">{{ m.invitation.cercle.ville ? `${m.invitation.cercle.ville} · ` : '' }}{{ t('cercles.membres', { n: m.invitation.cercle.nombreMembres, taille: m.invitation.cercle.taille }) }}</span>
                  <template v-if="!m.deMoi">
                    <JBouton v-if="placeLibre(m)" :occupe="rejoint === m.invitation.cercle.id" data-test="rejoindre-invitation" @click="rejoindre(m.invitation.cercle.id)">{{ t('cercles.rejoindre') }}</JBouton>
                    <span v-else class="invitation__infos">{{ t('reseau.messages.cercleFerme') }}</span>
                  </template>
                </div>
                <PastillesReactions :reactions="m.reactions" @basculer="(e) => reagir(m, e)" />
                <span class="bulle__meta">
                  <span v-if="m.modifieLe" class="bulle__modifie" data-test="modifie">{{ t('reseau.actions.modifie') }}</span>
                  <time :datetime="m.envoyeLe">{{ heure(m.envoyeLe) }}</time>
                  <span v-if="m.deMoi" class="bulle__lu" :class="{ 'bulle__lu--oui': m.luLe }" :title="m.luLe ? t('reseau.discussion.lu') : t('reseau.discussion.envoye')">
                    {{ m.luLe ? '✓✓' : '✓' }}<span class="visuellement-cache">{{ m.luLe ? t('reseau.discussion.lu') : t('reseau.discussion.envoye') }}</span>
                  </span>
                </span>
              </div>

              <!-- Les actions sur un message : un bouton « ⋯ » à côté de la bulle (au survol, au clavier, toujours au doigt). -->
              <div v-if="!m.supprime && !m.appel" class="actions">
                <button type="button" class="actions__ouvrir" :aria-expanded="menu === m.id" :aria-label="t('reseau.actions.menu')" data-test="actions-message" @click.stop="ouvrirMenu(m)">
                  <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="4.5" cy="10" r="1.6" fill="currentColor" /><circle cx="10" cy="10" r="1.6" fill="currentColor" /><circle cx="15.5" cy="10" r="1.6" fill="currentColor" /></svg>
                </button>
                <div v-if="menu === m.id" class="actions__menu" role="menu">
                  <template v-if="aSupprimer !== m.id">
                    <BarreReactions :actives="m.reactions.filter((r) => r.deMoi).map((r) => r.emoji)" @choisir="(e) => reagir(m, e)" />
                    <button type="button" role="menuitem" data-test="action-repondre" @click="repondre(m)">{{ t('reseau.actions.repondre') }}</button>
                    <button v-if="modifiable(m, maintenant)" type="button" role="menuitem" data-test="action-modifier" @click="modifier(m)">{{ t('reseau.actions.modifier') }}</button>
                    <button v-if="transferable(m)" type="button" role="menuitem" data-test="action-transferer" @click="transferer(m)">{{ t('reseau.actions.transferer') }}</button>
                    <button v-if="m.deMoi" type="button" role="menuitem" class="danger" data-test="action-supprimer" @click.stop="aSupprimer = m.id">{{ t('reseau.actions.supprimer') }}</button>
                  </template>
                  <template v-else>
                    <p class="actions__question">{{ t('reseau.actions.confirmerSuppression') }}</p>
                    <button type="button" role="menuitem" class="danger" data-test="confirmer-suppression" @click="supprimer(m)">{{ t('reseau.actions.supprimer') }}</button>
                    <button type="button" role="menuitem" @click.stop="aSupprimer = null">{{ t('reseau.actions.annuler') }}</button>
                  </template>
                </div>
              </div>
            </li>
          </template>
        </ol>
        <!-- « … est en train d'écrire » : trois points, annoncés une fois aux lecteurs d'écran. -->
        <div v-if="autreEcrit" class="ecrit" data-test="en-train-d-ecrire">
          <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="28" />
          <span class="ecrit__bulle" aria-hidden="true"><i /><i /><i /></span>
          <span class="ecrit__texte" role="status">{{ t('reseau.discussion.ecrit', { nom: prenom(personne.nom) }) }}</span>
        </div>
        <p v-if="erreurInvitation" role="alert" class="erreur">{{ erreurInvitation }}</p>
      </div>

      <form class="saisie" data-test="form-message" @submit.prevent="envoyer">
        <p v-if="info" class="saisie__info" role="status" data-test="info-action">{{ info }}</p>
        <!-- Je réponds à un message, ou j'en corrige un : le bandeau le rappelle au-dessus de la zone de saisie. -->
        <div v-if="reponse || edition" class="bandeau" data-test="bandeau-action">
          <svg v-if="edition" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M4 13.5V16h2.5l7.4-7.4-2.5-2.5L4 13.5ZM13 4.5l2.5 2.5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
          <svg v-else viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M8 5 3.5 9.5 8 14M4 9.5h7.5a5 5 0 0 1 5 5v1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <span class="bandeau__texte">
            <strong>{{ edition ? t('reseau.actions.enModification') : t('reseau.actions.repondreA', { nom: auteur(reponse!.deMoi) }) }}</strong>
            <span>{{ apercuMessage((edition ?? reponse)!) }}</span>
          </span>
          <button type="button" class="bandeau__fermer" :aria-label="t('reseau.actions.annuler')" data-test="annuler-action" @click="annulerBandeau">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
          </button>
        </div>
        <Composeur
          :key="personne.id"
          :destinataire-id="personne.id"
          ref="composeur"
          v-model="texte"
          v-model:pieces="pieces"
          id-zone="message"
          :placeholder="t('reseau.messages.ecrire', { nom: prenom(personne.nom) })"
          :libelle-envoyer="edition ? t('reseau.actions.enregistrer') : t('reseau.messages.envoyer')"
          :envoi="envoi"
          :erreur="erreur"
          :max="LONGUEUR_MESSAGE"
          test-envoyer="envoyer-message"
          :masquer-envoi="vocalEnCours && !edition"
          @envoyer="envoyer"
        >
          <template #action>
            <EnregistreurVocal v-if="!edition" :desactive="envoi" @envoyer="envoyerVocal" @en-cours="(actif) => (vocalEnCours = actif)" @erreur="(code) => (erreur = t(`vocal.erreurs.${code}`))" />
          </template>
        </Composeur>
      </form>
      <TransfertMessage v-if="aTransferer" :personne-id="personne.id" :message="aTransferer" @fermer="aTransferer = null" @transfere="transfere" />
    </div>
  </section>
</template>

<style scoped>
.discussion { height: 100%; min-width: 0; container: discussion / inline-size; }
.cadre {
  /* minmax(0, 1fr) : à 768 px, la colonne prenait la largeur de son contenu et le fil sortait de l'écran. */
  display: grid; grid-template-rows: auto 1fr auto auto; grid-template-columns: minmax(0, 1fr);
  height: 100%; min-height: 0;
  background: var(--bg); overflow: hidden;
}

.tete { display: flex; align-items: center; gap: var(--space-3); min-height: 72px; padding: 12px 16px 12px 18px; margin: 14px 14px -10px; border-radius: 22px; z-index: 2; }
.tete__retour { display: none; place-items: center; width: 36px; height: 36px; border-radius: 50%; color: var(--ink); }
.tete__retour:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.tete__qui { flex: 1; min-width: 0; }
.tete__ligne { display: flex; align-items: center; gap: 8px; min-width: 0; }
.tete__nom { color: inherit; text-decoration: none; border-radius: 6px; transition: color 160ms var(--ease-out); }
.tete__nom:hover { color: var(--action-ink); }
.tete__nom:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.tete__sous { margin: 2px 0 0; display: flex; gap: 6px; align-items: center; color: var(--ink-muted); font-size: 0.8125rem; min-width: 0; white-space: nowrap; overflow: hidden; }
.tete__presence--en-ligne { color: var(--en-ligne); font-weight: 600; }
.avatar-presence { position: relative; display: inline-flex; flex: none; }
.point-en-ligne { position: absolute; right: 0; bottom: 0; width: 12px; height: 12px; border-radius: 50%; background: var(--en-ligne); box-shadow: 0 0 0 2px var(--surface); }
.tete__qui h2 { margin: 0; min-width: 0; font-size: 1.0625rem; line-height: 1.3; letter-spacing: -0.01em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tete__lien { flex: none; padding: 1px 9px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-weight: 700; font-size: 0.75rem; }
.tete__actions { display: flex; align-items: center; gap: var(--space-2); }
.ajout-contact__icone { display: none; }
/* Conversation étroite (téléphone) : « Ajouter aux contacts » devient un rond à icône, à côté des appels. L'en-tête
   tient sur une ligne et le fil garde sa place. Le libellé reste lu par les lecteurs d'écran. */
@container discussion (max-width: 26rem) {
  .cadre .tete { flex-wrap: nowrap; min-height: 64px; }
  .cadre .tete__qui { min-width: 0; }
  .tete__actions { gap: 6px; }
  .tete__actions .ajout-contact { width: 44px; min-width: 44px; padding: 0; justify-content: center; border-radius: 50%; }
  .ajout-contact__icone { display: block; }
  .ajout-contact__texte { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  .rond { width: 40px; height: 40px; }
  /* La ville coupée (« Abidj… ») n'apprenait rien : on garde la présence, la ville reste sur le profil. */
  .tete__ville { display: none; }
}
/* Très étroit (téléphone de 320 à 360 px) : l'avatar cède sa place au nom ; « En ligne » reste écrit dessous. */
@container discussion (max-width: 21rem) {
  .cadre .tete .avatar-presence { display: none; }
  .cadre .tete { gap: 6px; padding-inline: 6px 8px; }
}

.rond { display: grid; place-items: center; flex: none; width: 44px; height: 44px; border-radius: 50%; border: 0; cursor: pointer; transition: transform 260ms var(--ease-pop), background 200ms var(--ease-out), box-shadow 200ms var(--ease-out); }
.rond:disabled { opacity: 0.45; cursor: default; }
.rond:not(:disabled):hover { transform: translateY(-1px) scale(1.06); box-shadow: 0 8px 18px -8px color-mix(in srgb, var(--action) 60%, transparent); }
.rond:not(:disabled):active { transform: scale(0.92); transition-duration: 90ms; }
.rond:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.rond--appel { background: var(--action-soft); color: var(--action-ink); }
.rond--envoyer { background: var(--proof); color: var(--on-proof); }

.fil {
  grid-row: 2; grid-column: 1; position: relative; z-index: 1;
  min-height: 0; overflow-y: auto; padding: 34px clamp(20px, 4vw, 64px) 40px; overscroll-behavior: contain; scrollbar-width: thin;
}
/* Le motif occupe la place de l'en-tête et du fil (même cellule de grille), derrière les deux. */
.tete { grid-row: 1; grid-column: 1; }
.cadre__motif { grid-row: 1 / 3; grid-column: 1; }
.accueil { display: grid; justify-items: center; gap: var(--space-2); padding: var(--space-8, 3rem) var(--space-4); text-align: center; color: var(--ink-muted); }
.accueil strong { color: var(--ink); font-size: 1.125rem; }
.accueil p { margin: 0; }

.jours { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
/* Le jour reste collé en haut du fil pendant qu'on remonte, sur un verre léger. */
.jour { position: sticky; top: 4px; z-index: 3; display: flex; justify-content: center; margin: var(--space-4) 0 var(--space-2); pointer-events: none; }
.jour span { padding: 4px 12px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 82%, transparent); backdrop-filter: blur(12px) saturate(1.4); -webkit-backdrop-filter: blur(12px) saturate(1.4); border: 1px solid var(--line); box-shadow: 0 4px 14px -8px color-mix(in srgb, var(--ink) 25%, transparent); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 700; text-transform: capitalize; }

.ligne { display: flex; align-items: flex-end; gap: var(--space-2); margin-top: var(--space-2); }
/* Un nouveau message (pas ceux déjà là à l'ouverture) : la bulle grandit depuis son coin, comme si elle sortait de l'avatar. */
.ligne--arrivee > .bulle, .ligne--arrivee > .appel-carte { animation: arrive 340ms var(--ease-out) backwards; transform-origin: bottom left; }
.bulle--moi > .bulle, .bulle--moi > .appel-carte { transform-origin: bottom right; }
.ligne:not(.ligne--glisse) > .bulle { transition: transform 300ms var(--ease-pop), box-shadow 200ms var(--ease-out); }
.ligne--suite { margin-top: 0; }
.ligne__avatar { flex: none; width: 28px; }
.bulle--moi { flex-direction: row-reverse; }
.bulle--moi .ligne__avatar { display: none; }

.bulle { display: grid; gap: var(--space-1); max-width: min(75%, 34rem); padding: 9px 14px; border-radius: 20px; line-height: 1.5; box-shadow: 0 1px 2px color-mix(in srgb, var(--ink) 7%, transparent); }
.bulle--autre .bulle { background: var(--surface); color: var(--ink); border: 1px solid color-mix(in srgb, var(--line) 70%, transparent); border-bottom-left-radius: 6px; }
.bulle--moi .bulle { background: linear-gradient(160deg, color-mix(in srgb, var(--proof) 86%, white), var(--proof) 55%); color: var(--on-proof); border-bottom-right-radius: 6px; box-shadow: 0 6px 16px -10px color-mix(in srgb, var(--proof) 80%, transparent); }
.ligne--suite.bulle--autre .bulle { border-top-left-radius: 6px; }
.ligne--suite.bulle--moi .bulle { border-top-right-radius: 6px; }
.bulle__texte { margin: 0; }
.bulle__meta { display: inline-flex; justify-self: end; gap: 4px; align-items: center; font-size: 0.75rem; font-variant-numeric: tabular-nums; opacity: 0.8; }
.bulle__lu { letter-spacing: -2px; }
.bulle__lu { transition: color 300ms var(--ease-out); }
.bulle__lu--oui { font-weight: 800; }

.invitation { display: grid; gap: var(--space-1); justify-items: start; margin-top: var(--space-1); padding: var(--space-3); border-radius: 12px; background: var(--surface); color: var(--ink); border: 1px solid var(--line); }
.invitation__titre { font-size: 0.875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-muted); }
.invitation__cercle { font-weight: 700; color: var(--ink); }
.invitation__infos { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.appel-carte { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4); border-radius: 16px; background: var(--surface); border: 1px solid var(--line); max-width: min(85%, 26rem); }
.appel-carte__icone { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; background: var(--success, #1f9d55); color: #fff; flex: none; }
.appel-carte--manque .appel-carte__icone { background: var(--danger); color: #fff; }
.appel-carte--fini .appel-carte__icone { background: var(--line-strong); color: var(--ink-muted); }
.appel-carte__texte { display: grid; flex: 1; line-height: 1.3; }
.appel-carte__texte span { color: var(--ink-muted); font-size: 0.9375rem; }
.appel-carte__rejoindre { padding: 6px 14px; border-radius: var(--radius-pill); background: var(--success, #1f9d55); color: #fff; font-weight: 700; text-decoration: none; white-space: nowrap; }
.appel-carte__rejoindre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.saisie { position: relative; margin: 0 var(--space-6) var(--space-4); z-index: 2; }
.saisie__info { position: absolute; left: 50%; bottom: calc(100% + 8px); transform: translateX(-50%); margin: 0; padding: 6px 14px; border-radius: var(--radius-pill); background: var(--ink); color: var(--bg); font-size: var(--type-body-sm-size); font-weight: 600; white-space: nowrap; animation: info-entre 280ms var(--ease-pop); }

/* Répondre, modifier, supprimer, transférer (#157). */
.ligne { position: relative; touch-action: pan-y; }
.ligne--surlignee .bulle { box-shadow: 0 0 0 3px var(--action); transition: box-shadow 200ms ease-out; }
.ligne__repondre { position: absolute; left: 4px; top: 50%; display: grid; place-items: center; width: 30px; height: 30px; margin-top: -15px; border-radius: 50%; background: var(--surface); color: var(--ink-muted); opacity: 0.6; transition: opacity 160ms var(--ease-out), transform 260ms var(--ease-pop), background 160ms; animation: apparait 200ms var(--ease-out); }
.ligne__repondre--pret { opacity: 1; color: var(--on-action, #fff); background: var(--action); transform: scale(1.15); }
.bulle__transfere { display: inline-flex; align-items: center; gap: 4px; font-size: 0.8125rem; font-style: italic; opacity: 0.75; }
.bulle__modifie { font-style: italic; }
.bulle--supprimee { background: transparent !important; color: var(--ink-muted) !important; border: 1px dashed var(--line-strong); box-shadow: none; }
.bulle__supprime { display: inline-flex; align-items: center; gap: 6px; font-style: italic; }
.citation {
  display: grid; gap: 1px; justify-items: start; min-width: 0; max-width: 100%; padding: 6px 10px; border: 0; border-left: 3px solid currentColor;
  border-radius: 8px; background: color-mix(in srgb, currentColor 10%, transparent); color: inherit; font: inherit; text-align: left; cursor: pointer;
}
.citation strong { font-size: 0.8125rem; }
.citation span { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 0.875rem; opacity: 0.85; }
.citation:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }

.actions { position: relative; align-self: center; opacity: 0; transform: scale(0.8); transition: opacity 180ms var(--ease-out), transform 220ms var(--ease-pop); }
.ligne:hover .actions, .actions:focus-within, .ligne--menu .actions { opacity: 1; transform: none; }
@media (hover: none) { .actions { opacity: 0.55; transform: none; } }
.actions__ouvrir { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; transition: background 160ms var(--ease-out), color 160ms var(--ease-out); }
.actions__ouvrir:hover, .actions__ouvrir[aria-expanded="true"] { background: var(--surface); color: var(--ink); }
.actions__ouvrir:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.actions__menu {
  position: absolute; bottom: calc(100% + 4px); z-index: 5; display: grid; min-width: 11rem; padding: 6px; border-radius: 14px;
  background: color-mix(in srgb, var(--surface) 92%, transparent); backdrop-filter: blur(16px) saturate(1.5); -webkit-backdrop-filter: blur(16px) saturate(1.5);
  color: var(--ink); border: 1px solid var(--line); box-shadow: 0 1px 2px rgb(0 0 0 / 0.06), 0 18px 40px -12px rgb(0 0 0 / 0.28);
  transform-origin: bottom left; animation: menu-entre 200ms var(--ease-out);
}
.bulle--autre .actions__menu { left: 0; }
.bulle--moi .actions__menu { right: 0; transform-origin: bottom right; }
.actions__menu button { padding: 9px 12px; border: 0; border-radius: 9px; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: background 140ms var(--ease-out); }
.actions__menu button:hover, .actions__menu button:focus-visible { background: var(--surface-raised); outline: none; }
.actions__menu .danger { color: var(--danger); }
.actions__menu .danger:hover { background: color-mix(in srgb, var(--danger) 10%, transparent); }
.actions__question { margin: 4px 12px 6px; font-weight: 600; font-size: var(--type-body-sm-size); }

.bandeau { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-2); padding: 8px 8px 8px 14px; border-radius: 16px; background: var(--surface); border: 1px solid var(--line); border-left: 4px solid var(--action); color: var(--action-ink); box-shadow: 0 10px 24px -16px color-mix(in srgb, var(--ink) 30%, transparent); animation: monte 260ms var(--ease-out); }
.bandeau__texte { display: grid; flex: 1; min-width: 0; color: var(--ink); }
.bandeau__texte strong { font-size: 0.875rem; color: var(--action-ink); }
.bandeau__texte span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); font-size: 0.875rem; }
.bandeau__fermer { display: grid; place-items: center; flex: none; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; }
.bandeau__fermer:hover { background: var(--surface-raised); color: var(--ink); }
@media (prefers-reduced-motion: reduce) { .ligne__repondre, .actions { transition: none; } }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

@keyframes arrive { from { opacity: 0; transform: translateY(10px) scale(0.94); } }
@keyframes apparait { from { opacity: 0; transform: scale(0.5); } }
@keyframes menu-entre { from { opacity: 0; transform: translateY(6px) scale(0.94); } }
@keyframes monte { from { opacity: 0; transform: translateY(10px); } }
@keyframes info-entre { from { opacity: 0; transform: translate(-50%, 8px) scale(0.9); } }
@media (prefers-reduced-motion: reduce) {
  .discussion *, .discussion *::before, .discussion *::after { animation: none !important; transition-duration: 0ms !important; }
}
@media (max-width: 45rem) {
  .tete__retour { display: grid; }
  /* Plein écran sur téléphone : le bandeau colle au bord, comme dans une application de messagerie. */
  .cadre .tete { margin: 0; border-radius: 0; border-width: 0 0 1px; min-height: 60px; }
  /* Les messages prennent la largeur : peu de marge, et pas d'avatar à côté de chaque bulle (le bandeau dit déjà
     à qui on parle). */
  .cadre .fil { padding: var(--space-3) var(--space-2) var(--space-4); }
  .cadre .ligne__avatar { display: none; }
  .cadre .bulle { max-width: 88%; }
  .cadre .saisie { margin: 0 var(--space-2) max(var(--space-2), env(safe-area-inset-bottom)); }
}
/* Selon la largeur de la conversation, pas de l'écran : à 768 px, la liste est à côté et la conversation n'a que
   400 px ; le nom de la personne disparaissait derrière les boutons. */
@container discussion (max-width: 45rem) {
  .tete { padding: 10px; margin: 10px 10px -10px; gap: 8px; min-height: 76px; flex-wrap: wrap; }
  .tete__qui { min-width: 100px; }
  .tete__actions { margin-left: auto; }
  .tete__actions .j-bouton { font-size: 0.8125rem; padding-inline: 8px; }
  /* Le nom d'abord : « Dans tes contacts » prenait sa place sur un petit écran. */
  .tete__lien { display: none; }
  /* Le menu « ⋯ » s'ouvre par-dessus la bulle, vers l'intérieur de l'écran : vers l'extérieur, il sortait de l'écran. */
  .bulle--moi .actions__menu { left: 0; right: auto; transform-origin: bottom left; }
  .bulle--autre .actions__menu { left: auto; right: 0; transform-origin: bottom right; }
  .fil { padding: 30px 16px 40px; }
  .saisie { margin: 0 var(--space-3) var(--space-3); }
  .aide { display: none; }
  .bulle { max-width: 85%; }
}
.ecrit { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0 var(--space-1); color: var(--ink-muted); font-size: 0.85rem; animation: monte 260ms var(--ease-out); }
.ecrit__bulle { display: inline-flex; gap: 4px; padding: 12px 14px; border-radius: 20px; border-bottom-left-radius: 6px; background: var(--surface); border: 1px solid var(--line); }
.ecrit__bulle i { width: 7px; height: 7px; border-radius: 50%; background: var(--ink-muted); animation: points 1.3s cubic-bezier(.45,0,.55,1) infinite; }
.ecrit__bulle i:nth-child(2) { animation-delay: 0.15s; }
.ecrit__bulle i:nth-child(3) { animation-delay: 0.3s; }
@keyframes points { 0%, 60%, 100% { opacity: 0.35; transform: translateY(0) scale(0.9); } 30% { opacity: 1; transform: translateY(-4px) scale(1); } }
@media (prefers-reduced-motion: reduce) { .ecrit__bulle i { animation: none; } }
</style>
