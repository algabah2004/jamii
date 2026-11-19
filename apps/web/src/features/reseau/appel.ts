import { reactive, readonly, shallowRef } from 'vue'
import { api, lireJetonCsrf } from '../../api/client'
import { appelCercleActif } from '../cercles/appelCercle'
import type { components } from '../../api/generated/reseau'
import type { EcrireA } from './reseau'

/*
 * Les appels en tête-à-tête dans Jamii (#157), en WebRTC : le son et l'image passent directement entre les deux
 * navigateurs, ou par notre relais TURN (coturn) quand un réseau l'empêche. L'API (contracts/reseau.yaml, tag
 * « appels ») ne fait que la mise en relation, en HTTP, sans WebSocket :
 *
 *   1. l'appelant prépare son offre (avec tous ses candidats ICE, pas d'échange au fil de l'eau) et la dépose ;
 *   2. l'appelé demande toutes les 2 secondes si on l'appelle, et voit l'appel sonner ;
 *   3. il décroche : sa réponse part chez l'appelant, qui la lit toutes les secondes ;
 *   4. ensuite tout passe par la connexion directe : micro, caméra, partage d'écran, réactions et fin de l'appel
 *      (un canal de données WebRTC). L'API n'apprend que la fin, pour l'historique.
 *
 * Une seule connexion par onglet : ce module est un état partagé, affiché par FenetreAppel.vue.
 */
export type Appel = components['schemas']['Appel']
type Personne = Pick<components['schemas']['Personne'], 'id' | 'nom' | 'avatarUrl'>

export type Phase = 'repos' | 'preparation' | 'sonne_chez_lautre' | 'sonne_chez_moi' | 'connexion' | 'en_cours' | 'fini'
export type Fin = 'termine' | 'refuse' | 'manque' | 'annule' | 'micro_refuse' | 'echec' | 'impossible' | 'trop_de_requetes' | 'compte_demo'

export const REACTIONS = ['👍', '❤️', '😂', '👏', '🎉', '🙏'] as const

/** L'appelé demande s'il est appelé à ce rythme ; l'appelant lit l'appel à ce rythme tant que ça sonne. */
export const INTERVALLE_ENTRANT_MS = 2_000
export const INTERVALLE_SONNERIE_MS = 1_000
/** Une fois connectés, on relit l'appel plus rarement : seulement pour savoir si l'autre a raccroché sans le dire. */
export const INTERVALLE_EN_COURS_MS = 5_000
/** Le temps laissé au navigateur pour trouver ses chemins réseau (STUN, TURN) avant d'envoyer l'offre. */
const ATTENTE_CANDIDATS_MS = 4_000
/** Le message de fin reste affiché ce temps-là. */
const AFFICHAGE_FIN_MS = 2_500

export interface Reaction {
  id: number
  emoji: string
  deMoi: boolean
}

interface Etat {
  phase: Phase
  id: string | null
  personne: Personne | null
  video: boolean
  deMoi: boolean
  micro: boolean
  camera: boolean
  ecran: boolean
  microDistant: boolean
  cameraDistante: boolean
  reactions: Reaction[]
  fin: Fin | null
  /** Avec la fin `compte_demo` : la personne de la démo à qui écrire à la place (#157). */
  ecrireA: EcrireA | null
  debut: number | null
  relais: boolean
  reduit: boolean
}

const etat = reactive<Etat>(etatInitial())
const fluxLocal = shallowRef<MediaStream | null>(null)
const fluxDistant = shallowRef<MediaStream | null>(null)

let pc: RTCPeerConnection | null = null
let canal: RTCDataChannel | null = null
let emetteurVideo: RTCRtpSender | null = null
let pisteCamera: MediaStreamTrack | null = null
let pisteEcran: MediaStreamTrack | null = null
let offreEntrante: string | null = null
let suivi: ReturnType<typeof setTimeout> | null = null
let entrants: ReturnType<typeof setInterval> | null = null
let finAffichee: ReturnType<typeof setTimeout> | null = null
let sonnerie: Sonnerie | null = null
/** Chaque appel a son numéro : une réponse arrivée pour un appel déjà fini est ignorée. */
let session = 0
let derniereReaction = 0
let compteurReactions = 0

function etatInitial(): Etat {
  return {
    phase: 'repos', id: null, personne: null, video: false, deMoi: false, micro: true, camera: false, ecran: false,
    microDistant: true, cameraDistante: false, reactions: [], fin: null, ecrireA: null, debut: null, relais: false, reduit: false,
  }
}

export const useAppel = () => ({ etat: readonly(etat), fluxLocal, fluxDistant })

export const appelActif = () => etat.phase !== 'repos' && etat.phase !== 'fini'

export const partageEcranPossible = () =>
  typeof navigator !== 'undefined' && !!navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function'

// ---------------------------------------------------------------- appeler

/** Appeler une personne depuis sa conversation. Un appel vocal peut allumer la caméra ensuite. */
export async function appeler(personne: Personne, video: boolean): Promise<void> {
  if (appelActif() || appelCercleActif()) return
  const s = nouvelleSession()
  Object.assign(etat, etatInitial(), { phase: 'preparation', personne, video, deMoi: true })
  const flux = await ouvrirMedia(video)
  if (s !== session) return arreterFlux(flux)
  if (!flux) return finir('micro_refuse')
  fluxLocal.value = flux
  try {
    const c = await preparerConnexion()
    if (s !== session) return
    const audio = flux.getAudioTracks()[0]!
    const camera = flux.getVideoTracks()[0] ?? null
    c.addTransceiver(audio, { direction: 'sendrecv', streams: [flux] })
    // Toujours une voie vidéo, même pour un appel vocal : la caméra s'allume ensuite sans renégocier.
    emetteurVideo = c.addTransceiver(camera ?? 'video', { direction: 'sendrecv', streams: [flux] }).sender
    pisteCamera = camera
    etat.camera = !!camera
    await c.setLocalDescription(await c.createOffer())
    await attendreCandidats(c)
    if (s !== session) return
    const { data, error } = await api.POST('/api/conversations/{personneId}/appels', {
      params: { path: { personneId: personne.id } },
      body: { offre: c.localDescription!.sdp, video },
    })
    if (s !== session) return
    if (!data) {
      const e = error as { code?: string; ecrireA?: EcrireA } | undefined
      if (e?.code === 'compte_demo') {
        finir('compte_demo')
        etat.ecrireA = e.ecrireA ?? null
        return
      }
      return finir(e?.code === 'trop_de_requetes' ? 'trop_de_requetes' : 'impossible')
    }
    etat.id = data.id
    etat.phase = 'sonne_chez_lautre'
    sonnerie = sonner('sortante')
    suivre(s, INTERVALLE_SONNERIE_MS)
  } catch {
    if (s === session) finir('impossible')
  }
}

/** L'appelant lit son appel : la réponse de l'autre, ou la fin. */
function suivre(s: number, delai: number) {
  suivi = setTimeout(async () => {
    if (s !== session || !etat.id) return
    try {
      const { data } = await api.GET('/api/appels/{appelId}', { params: { path: { appelId: etat.id } } })
      if (s !== session) return
      if (data) {
        if (['termine', 'refuse', 'manque', 'annule'].includes(data.statut)) return finir(data.statut as Fin)
        if (etat.deMoi && data.statut === 'en_cours' && data.reponse && pc && !pc.remoteDescription) {
          arreterSonnerie()
          etat.phase = 'connexion'
          await pc.setRemoteDescription({ type: 'answer', sdp: data.reponse })
        }
      }
    } catch {
      /* une panne réseau passagère : on relit au prochain tour */
    }
    if (s === session) suivre(s, etat.phase === 'sonne_chez_lautre' ? INTERVALLE_SONNERIE_MS : INTERVALLE_EN_COURS_MS)
  }, delai)
}

// ---------------------------------------------------------------- recevoir

/** Tant qu'on est connecté : est-ce qu'on m'appelle ? Seulement quand l'onglet est visible, comme les pastilles. */
export function suivreAppelsEntrants(): void {
  if (entrants) return
  entrants = setInterval(() => {
    // Sans document (environnement de test démonté), il n'y a plus personne à prévenir : la CI de main a échoué ainsi.
    if (typeof document !== 'undefined' && document.visibilityState === 'visible') void verifierEntrant()
  }, INTERVALLE_ENTRANT_MS)
  window.addEventListener('pagehide', quitterPage)
}

export function arreterAppelsEntrants(): void {
  if (entrants) clearInterval(entrants)
  entrants = null
  window.removeEventListener('pagehide', quitterPage)
  if (appelActif()) void raccrocher()
}

export async function verifierEntrant(): Promise<void> {
  if (etat.phase !== 'repos' && etat.phase !== 'sonne_chez_moi' && etat.phase !== 'fini') return
  if (appelCercleActif()) return
  try {
    const { data } = await api.GET('/api/appels/entrant')
    if (etat.phase === 'sonne_chez_moi') {
      // L'appelant a raccroché avant qu'on décroche, ou l'appel a trop sonné.
      if (!data || data.id !== etat.id) finir('manque')
      return
    }
    if (!data?.offre || appelActif()) return
    nouvelleSession()
    offreEntrante = data.offre
    Object.assign(etat, etatInitial(), { phase: 'sonne_chez_moi', id: data.id, personne: data.personne, video: data.video, deMoi: false })
    sonnerie = sonner('entrante')
  } catch {
    /* on réessaiera */
  }
}

/** Décrocher, avec la caméra si l'appel est en vidéo (ou si on le choisit). */
export async function decrocher(avecVideo = etat.video): Promise<void> {
  if (etat.phase !== 'sonne_chez_moi' || !etat.id || !offreEntrante) return
  const s = session
  const id = etat.id
  arreterSonnerie()
  etat.phase = 'connexion'
  const flux = await ouvrirMedia(avecVideo)
  if (s !== session) return arreterFlux(flux)
  if (!flux) {
    void api.POST('/api/appels/{appelId}/refus', { params: { path: { appelId: id } } }).catch(() => undefined)
    return finir('micro_refuse')
  }
  fluxLocal.value = flux
  try {
    const c = await preparerConnexion()
    if (s !== session) return
    await c.setRemoteDescription({ type: 'offer', sdp: offreEntrante })
    // Les voies audio et vidéo viennent de l'offre : on y branche nos pistes, et on les ouvre dans les deux sens.
    for (const t of c.getTransceivers()) {
      const genre = t.receiver.track.kind
      const piste = genre === 'audio' ? flux.getAudioTracks()[0] : flux.getVideoTracks()[0]
      t.direction = 'sendrecv'
      if (piste) await t.sender.replaceTrack(piste)
      if (genre === 'video') emetteurVideo = t.sender
    }
    pisteCamera = flux.getVideoTracks()[0] ?? null
    etat.camera = !!pisteCamera
    await c.setLocalDescription(await c.createAnswer())
    await attendreCandidats(c)
    if (s !== session) return
    const { data } = await api.POST('/api/appels/{appelId}/reponse', {
      params: { path: { appelId: id } },
      body: { reponse: c.localDescription!.sdp },
    })
    if (s !== session) return
    if (!data) return finir('annule')
    suivre(s, INTERVALLE_EN_COURS_MS)
  } catch {
    if (s === session) {
      void api.POST('/api/appels/{appelId}/fin', { params: { path: { appelId: id } } }).catch(() => undefined)
      finir('echec')
    }
  }
}

export async function refuser(): Promise<void> {
  if (etat.phase !== 'sonne_chez_moi' || !etat.id) return
  const id = etat.id
  finir('refuse')
  await api.POST('/api/appels/{appelId}/refus', { params: { path: { appelId: id } } }).catch(() => undefined)
}

/** Raccrocher : l'autre l'apprend tout de suite par la connexion directe, l'API pour l'historique. */
export async function raccrocher(): Promise<void> {
  const id = etat.id
  envoyer({ t: 'fin' })
  finir(etat.phase === 'en_cours' ? 'termine' : 'annule')
  if (id) await api.POST('/api/appels/{appelId}/fin', { params: { path: { appelId: id } } }).catch(() => undefined)
}

/** L'onglet se ferme pendant un appel : un dernier envoi que le navigateur termine même après la fermeture. */
function quitterPage() {
  if (!appelActif() || !etat.id) return
  envoyer({ t: 'fin' })
  const jeton = lireJetonCsrf()
  void fetch(`/api/appels/${etat.id}/fin`, {
    method: 'POST', keepalive: true, credentials: 'same-origin', headers: jeton ? { 'X-XSRF-TOKEN': jeton } : {},
  }).catch(() => undefined)
}

// ---------------------------------------------------------------- pendant l'appel

export function basculerMicro(): void {
  const piste = fluxLocal.value?.getAudioTracks()[0]
  if (!piste) return
  piste.enabled = !piste.enabled
  etat.micro = piste.enabled
  envoyerEtat()
}

/** Éteindre la caméra la libère vraiment (le voyant s'éteint) ; la rallumer la redemande au navigateur. */
export async function basculerCamera(): Promise<void> {
  if (!emetteurVideo) return
  if (etat.camera) {
    pisteCamera?.stop()
    retirerPiste(pisteCamera)
    pisteCamera = null
    etat.camera = false
    if (!etat.ecran) await emetteurVideo.replaceTrack(null)
    return envoyerEtat()
  }
  try {
    const flux = await navigator.mediaDevices.getUserMedia({ video: CONTRAINTES_VIDEO })
    pisteCamera = flux.getVideoTracks()[0] ?? null
    if (!pisteCamera) return
    if (etat.ecran) arreterEcran(false)
    await emetteurVideo.replaceTrack(pisteCamera)
    ajouterPiste(pisteCamera)
    etat.camera = true
    envoyerEtat()
  } catch {
    /* caméra refusée ou absente : l'appel continue en vocal */
  }
}

export async function basculerEcran(): Promise<void> {
  if (!emetteurVideo) return
  if (etat.ecran) return arreterEcran(true)
  try {
    const flux = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
    pisteEcran = flux.getVideoTracks()[0] ?? null
    if (!pisteEcran) return
    // Le bouton « Arrêter le partage » du navigateur.
    pisteEcran.onended = () => arreterEcran(true)
    await emetteurVideo.replaceTrack(pisteEcran)
    etat.ecran = true
    envoyerEtat()
  } catch {
    /* la personne a annulé le choix de l'écran */
  }
}

function arreterEcran(reprendreCamera: boolean) {
  pisteEcran?.stop()
  pisteEcran = null
  etat.ecran = false
  if (reprendreCamera) void emetteurVideo?.replaceTrack(pisteCamera)
  envoyerEtat()
}

export function reagir(emoji: string): void {
  if (!(REACTIONS as readonly string[]).includes(emoji)) return
  const maintenant = Date.now()
  if (maintenant - derniereReaction < 300) return
  derniereReaction = maintenant
  envoyer({ t: 'reaction', emoji })
  montrerReaction(emoji, true)
}

export function reduire(valeur: boolean): void {
  etat.reduit = valeur
}

function montrerReaction(emoji: string, deMoi: boolean) {
  const id = ++compteurReactions
  etat.reactions = [...etat.reactions.slice(-7), { id, emoji, deMoi }]
  setTimeout(() => (etat.reactions = etat.reactions.filter((r) => r.id !== id)), 2_600)
}

// ---------------------------------------------------------------- la connexion

const CONTRAINTES_VIDEO: MediaTrackConstraints = { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' }

/** Le micro, et la caméra si demandée. Sans caméra (refusée ou absente), on continue en vocal ; sans micro, non. */
async function ouvrirMedia(video: boolean): Promise<MediaStream | null> {
  if (!navigator.mediaDevices?.getUserMedia) return null
  const audio: MediaTrackConstraints = { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
  try {
    return await navigator.mediaDevices.getUserMedia({ audio, video: video ? CONTRAINTES_VIDEO : false })
  } catch {
    if (!video) return null
    try {
      return await navigator.mediaDevices.getUserMedia({ audio, video: false })
    } catch {
      return null
    }
  }
}

async function serveursIce(): Promise<RTCIceServer[]> {
  try {
    const { data } = await api.GET('/api/appels/ice')
    if (data?.iceServers.length) {
      etat.relais = data.relais
      return data.iceServers.map((s) => ({ urls: s.urls, username: s.username ?? undefined, credential: s.credential ?? undefined }))
    }
  } catch {
    /* sans réponse de l'API : un STUN public, l'appel passera sur la plupart des réseaux */
  }
  return [{ urls: 'stun:stun.l.google.com:19302' }]
}

async function preparerConnexion(): Promise<RTCPeerConnection> {
  const c = new RTCPeerConnection({ iceServers: await serveursIce() })
  pc = c
  const distant = new MediaStream()
  fluxDistant.value = distant
  c.ontrack = (e) => {
    distant.addTrack(e.track)
    // Un nouvel objet pour que l'affichage le remarque.
    fluxDistant.value = new MediaStream(distant.getTracks())
  }
  c.onconnectionstatechange = () => {
    if (c !== pc) return
    if (c.connectionState === 'connected' && etat.phase !== 'en_cours') {
      etat.phase = 'en_cours'
      etat.debut ??= Date.now()
    } else if (c.connectionState === 'failed') {
      void raccrocherSurEchec()
    }
  }
  // Un canal de données « négocié » : les deux côtés le créent avec le même numéro, sans attendre l'autre.
  canal = c.createDataChannel('jamii', { negotiated: true, id: 0 })
  canal.onopen = () => envoyerEtat()
  canal.onmessage = (e) => recevoir(e.data)
  return c
}

async function raccrocherSurEchec() {
  const id = etat.id
  finir('echec')
  if (id) await api.POST('/api/appels/{appelId}/fin', { params: { path: { appelId: id } } }).catch(() => undefined)
}

function attendreCandidats(c: RTCPeerConnection): Promise<void> {
  return new Promise((fini) => {
    if (c.iceGatheringState === 'complete') return fini()
    const minuteur = setTimeout(fini, ATTENTE_CANDIDATS_MS)
    c.addEventListener('icegatheringstatechange', () => {
      if (c.iceGatheringState === 'complete') {
        clearTimeout(minuteur)
        fini()
      }
    })
  })
}

type Signal = { t: 'etat'; micro: boolean; camera: boolean } | { t: 'reaction'; emoji: string } | { t: 'fin' }

function envoyer(signal: Signal) {
  if (canal?.readyState === 'open') canal.send(JSON.stringify(signal))
}

function envoyerEtat() {
  envoyer({ t: 'etat', micro: etat.micro, camera: etat.camera || etat.ecran })
}

/** Ce que l'autre navigateur envoie : jamais affiché tel quel, seulement des valeurs connues. */
export function recevoir(brut: unknown): void {
  if (typeof brut !== 'string' || brut.length > 500) return
  let signal: Partial<Signal> & Record<string, unknown>
  try {
    signal = JSON.parse(brut)
  } catch {
    return
  }
  if (signal.t === 'etat') {
    etat.microDistant = signal.micro !== false
    etat.cameraDistante = signal.camera === true
  } else if (signal.t === 'reaction' && typeof signal.emoji === 'string' && (REACTIONS as readonly string[]).includes(signal.emoji)) {
    montrerReaction(signal.emoji, false)
  } else if (signal.t === 'fin' && appelActif()) {
    finir('termine')
  }
}

function ajouterPiste(piste: MediaStreamTrack) {
  fluxLocal.value = new MediaStream([...(fluxLocal.value?.getTracks() ?? []), piste])
}

function retirerPiste(piste: MediaStreamTrack | null) {
  if (!piste) return
  fluxLocal.value = new MediaStream((fluxLocal.value?.getTracks() ?? []).filter((p) => p !== piste))
}

function arreterFlux(flux: MediaStream | null) {
  flux?.getTracks().forEach((p) => p.stop())
}

function nouvelleSession(): number {
  if (finAffichee) clearTimeout(finAffichee)
  finAffichee = null
  return ++session
}

/**
 * Tout libérer : la connexion, le micro, la caméra, l'écran, la sonnerie. Le message de fin reste affiché un instant.
 * `nouvelleSession` d'abord : une réponse encore en route pour cet appel sera ignorée.
 */
function finir(fin: Fin) {
  const s = nouvelleSession()
  arreterSonnerie()
  if (suivi) clearTimeout(suivi)
  suivi = null
  canal?.close()
  canal = null
  pc?.close()
  pc = null
  emetteurVideo = null
  arreterFlux(fluxLocal.value)
  pisteEcran?.stop()
  pisteCamera = null
  pisteEcran = null
  fluxLocal.value = null
  fluxDistant.value = null
  offreEntrante = null
  etat.phase = 'fini'
  etat.fin = fin
  etat.reactions = []
  etat.reduit = false
  finAffichee = setTimeout(() => {
    if (s === session && etat.phase === 'fini') Object.assign(etat, etatInitial())
  }, AFFICHAGE_FIN_MS)
}

/** Fermer tout de suite le message de fin. */
export function fermer(): void {
  if (etat.phase !== 'fini') return
  nouvelleSession()
  Object.assign(etat, etatInitial())
}

// ---------------------------------------------------------------- la sonnerie

interface Sonnerie {
  arreter(): void
}

/**
 * Une sonnerie synthétisée (aucun fichier son à charger) : deux notes pour un appel entrant, une tonalité longue pour
 * l'appel qui sonne chez l'autre. Si le navigateur bloque le son (aucun clic encore sur la page), l'écran suffit.
 */
function sonner(genre: 'entrante' | 'sortante'): Sonnerie | null {
  const Contexte = (window as unknown as { AudioContext?: typeof AudioContext }).AudioContext
  if (!Contexte) return null
  try {
    const ctx = new Contexte()
    const jouer = () => {
      const notes = genre === 'entrante' ? [[880, 0, 0.18], [660, 0.22, 0.18], [880, 0.44, 0.18]] : [[440, 0, 1.0]]
      for (const [frequence, debut, duree] of notes) {
        const osc = ctx.createOscillator()
        const volume = ctx.createGain()
        osc.frequency.value = frequence!
        volume.gain.setValueAtTime(0.0001, ctx.currentTime + debut!)
        volume.gain.exponentialRampToValueAtTime(genre === 'entrante' ? 0.18 : 0.06, ctx.currentTime + debut! + 0.02)
        volume.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + debut! + duree!)
        osc.connect(volume).connect(ctx.destination)
        osc.start(ctx.currentTime + debut!)
        osc.stop(ctx.currentTime + debut! + duree! + 0.05)
      }
    }
    jouer()
    const minuteur = setInterval(jouer, genre === 'entrante' ? 2_000 : 3_000)
    return { arreter: () => { clearInterval(minuteur); void ctx.close().catch(() => undefined) } }
  } catch {
    return null
  }
}

function arreterSonnerie() {
  sonnerie?.arreter()
  sonnerie = null
}

/** Pour les tests : repartir d'un état neuf. */
export function reinitialiserAppel(): void {
  if (entrants) clearInterval(entrants)
  entrants = null
  finir('termine')
  fermer()
}
