import { reactive, readonly, shallowRef } from 'vue'
import { api, lireJetonCsrf } from '../../api/client'
import { useSession } from '../../session'
import { appelActif } from '../reseau/appel'

/*
 * L'appel du cercle (#157), dans Jamii : au plus 6 membres, chacun relié directement à chacun (un « maillage »). Pas de
 * serveur média : le son et l'image vont d'un navigateur à l'autre, ou par notre relais TURN quand un réseau l'empêche.
 * L'API (contracts/cercles.yaml) ne fait que la mise en relation, en HTTP, sans WebSocket :
 *
 *   - toutes les 1,5 s, je signale que je suis là ; je reçois la liste des présents, et les offres ou réponses qui
 *     m'attendent ;
 *   - pour chaque paire, le dernier arrivé envoie l'offre (tous les navigateurs le savent sans se concerter) ;
 *   - ensuite, micro, caméra, réactions passent par un canal de données WebRTC avec chacun.
 *
 * Pour tenir à 6 sur une connexion africaine ordinaire, la vidéo est limitée (360p, 15 images/s, ~350 kbit/s par
 * personne) : chacun envoie son image à 5 autres.
 */
export type FinCercle = 'termine' | 'micro_refuse' | 'impossible' | 'trop_de_requetes' | 'echec'

export interface Participant {
  id: string
  nom: string
  avatarUrl: string | null
  micro: boolean
  camera: boolean
  connecte: boolean
}

export interface ReactionCercle {
  id: number
  emoji: string
  nom: string
  deMoi: boolean
}

export const REACTIONS = ['👍', '❤️', '😂', '👏', '🎉', '🙏'] as const
export const INTERVALLE_PRESENCE_MS = 1_500
/** Une offre restée sans réponse au-delà est renvoyée. */
const OFFRE_SANS_REPONSE_MS = 20_000
const ATTENTE_CANDIDATS_MS = 4_000
const DEBIT_VIDEO_MAX = 350_000
const CONTRAINTES_VIDEO: MediaTrackConstraints = { width: { ideal: 640 }, height: { ideal: 360 }, frameRate: { ideal: 15, max: 20 }, facingMode: 'user' }

interface Etat {
  phase: 'repos' | 'preparation' | 'en_cours' | 'fini'
  cercleId: string | null
  cercleNom: string
  micro: boolean
  camera: boolean
  ecran: boolean
  reduit: boolean
  participants: Participant[]
  reactions: ReactionCercle[]
  fin: FinCercle | null
  debut: number | null
}

interface Paire {
  pc: RTCPeerConnection
  canal: RTCDataChannel
  emetteurVideo: RTCRtpSender
  arriveLe: string
  offreur: boolean
  offreLe: number
  distant: MediaStream
}

const etat = reactive<Etat>(etatInitial())
const fluxLocal = shallowRef<MediaStream | null>(null)
/** Le flux de chaque participant, par identifiant. */
const fluxDistants = shallowRef<Record<string, MediaStream>>({})

const paires = new Map<string, Paire>()
let iceServers: RTCIceServer[] | null = null
let minuteur: ReturnType<typeof setTimeout> | null = null
let pisteCamera: MediaStreamTrack | null = null
let pisteEcran: MediaStreamTrack | null = null
let echecs = 0
let session = 0
let compteurReactions = 0
let derniereReaction = 0

function etatInitial(): Etat {
  return { phase: 'repos', cercleId: null, cercleNom: '', micro: true, camera: false, ecran: false, reduit: false, participants: [], reactions: [], fin: null, debut: null }
}

export const useAppelCercle = () => ({ etat: readonly(etat), fluxLocal, fluxDistants })
export const appelCercleActif = () => etat.phase === 'preparation' || etat.phase === 'en_cours'

// ---------------------------------------------------------------- entrer, sortir

/** Lancer l'appel du cercle, ou rejoindre celui qui est en cours. */
export async function rejoindre(cercle: { id: string; nom: string }, video = false): Promise<void> {
  if (appelCercleActif() || appelActif()) return
  const s = ++session
  Object.assign(etat, etatInitial(), { phase: 'preparation', cercleId: cercle.id, cercleNom: cercle.nom })
  const flux = await ouvrirMedia(video)
  if (s !== session) return arreter(flux)
  if (!flux) return finir('micro_refuse')
  fluxLocal.value = flux
  pisteCamera = flux.getVideoTracks()[0] ?? null
  etat.camera = !!pisteCamera
  try {
    const { data, response } = await api.POST('/api/cercles/{cercleId}/salon/appel', { params: { path: { cercleId: cercle.id } } })
    if (s !== session) return
    if (!data) return finir(response.status === 429 ? 'trop_de_requetes' : 'impossible')
    iceServers = await lireIce()
    if (s !== session) return
    etat.phase = 'en_cours'
    etat.debut = Date.now()
    echecs = 0
    window.addEventListener('pagehide', quitterPage)
    await tour(s)
  } catch {
    if (s === session) finir('impossible')
  }
}

/** Quitter l'appel ; le dernier à partir le termine. */
export async function quitter(): Promise<void> {
  const cercleId = etat.cercleId
  const etaitDedans = etat.phase === 'en_cours'
  finir('termine')
  if (cercleId && etaitDedans) {
    await api.DELETE('/api/cercles/{cercleId}/salon/appel/presence', { params: { path: { cercleId } } }).catch(() => undefined)
  }
}

function quitterPage() {
  if (etat.phase !== 'en_cours' || !etat.cercleId) return
  const jeton = lireJetonCsrf()
  void fetch(`/api/cercles/${etat.cercleId}/salon/appel/presence`, {
    method: 'DELETE', keepalive: true, credentials: 'same-origin', headers: jeton ? { 'X-XSRF-TOKEN': jeton } : {},
  }).catch(() => undefined)
}

export function fermer(): void {
  if (etat.phase === 'fini') Object.assign(etat, etatInitial())
}

// ---------------------------------------------------------------- la mise en relation

/** Un signe de vie : je suis là ; qui est là ; ce qui m'attend. Puis le suivant, 1,5 s plus tard. */
async function tour(s: number): Promise<void> {
  if (s !== session || etat.phase !== 'en_cours' || !etat.cercleId) return
  try {
    const { data, response } = await api.POST('/api/cercles/{cercleId}/salon/appel/presence', { params: { path: { cercleId: etat.cercleId } } })
    if (s !== session) return
    if (!data) {
      // L'appel a été terminé (par son lanceur, le créateur, ou parce qu'il a duré une heure).
      if (response.status === 404 || response.status === 403) return finir('termine')
      throw new Error(String(response.status))
    }
    echecs = 0
    await appliquer(s, data)
  } catch {
    // 8 échecs de suite, soit 12 s sans l'API : l'API nous a déjà comptés partis.
    if (++echecs >= 8 && s === session) return finir('echec')
  }
  if (s === session && etat.phase === 'en_cours') minuteur = setTimeout(() => void tour(s), INTERVALLE_PRESENCE_MS)
}

type Salle = { presents: { personne: { id: string; nom: string; avatarUrl?: string | null }; arriveLe: string }[]; signaux: { de: string; type: 'offre' | 'reponse'; sdp: string }[] }

async function appliquer(s: number, salle: Salle) {
  const moi = useSession().moi.value?.id
  const presentMoi = salle.presents.find((p) => p.personne.id === moi)
  if (!moi || !presentMoi) return
  const autres = salle.presents.filter((p) => p.personne.id !== moi)

  // Les participants affichés, dans l'ordre d'arrivée, en gardant ce qu'on sait de leur micro et de leur caméra.
  const avant = new Map(etat.participants.map((p) => [p.id, p]))
  etat.participants = autres.map((p) => avant.get(p.personne.id) ?? {
    id: p.personne.id, nom: p.personne.nom, avatarUrl: p.personne.avatarUrl ?? null, micro: true, camera: false, connecte: false,
  })

  // Ceux qui sont partis, ou revenus (nouvelle arrivée) : on ferme leur connexion.
  for (const [id, paire] of paires) {
    const present = autres.find((p) => p.personne.id === id)
    if (!present || present.arriveLe !== paire.arriveLe) fermerPaire(id)
  }

  // Les offres et réponses reçues.
  for (const signal of salle.signaux) {
    const present = autres.find((p) => p.personne.id === signal.de)
    if (!present) continue
    if (signal.type === 'offre') await repondre(s, signal.de, present.arriveLe, signal.sdp)
    else {
      const paire = paires.get(signal.de)
      if (paire?.pc.signalingState === 'have-local-offer') await paire.pc.setRemoteDescription({ type: 'answer', sdp: signal.sdp }).catch(() => fermerPaire(signal.de))
    }
  }

  // Les nouveaux venus : c'est le dernier arrivé de chaque paire qui envoie l'offre.
  const maintenant = Date.now()
  for (const p of autres) {
    const paire = paires.get(p.personne.id)
    const ecart = Date.parse(presentMoi.arriveLe) - Date.parse(p.arriveLe)
    const jeSuisLeDernier = ecart > 0 || (ecart === 0 && moi > p.personne.id)
    const bloquee = paire && (paire.pc.connectionState === 'failed' || (paire.offreur && !paire.pc.remoteDescription && maintenant - paire.offreLe > OFFRE_SANS_REPONSE_MS))
    if (bloquee) fermerPaire(p.personne.id)
    if (jeSuisLeDernier && (!paire || bloquee)) await offrir(s, p.personne.id, p.arriveLe)
  }
}

async function offrir(s: number, id: string, arriveLe: string) {
  const paire = creerPaire(id, arriveLe, true)
  try {
    await paire.pc.setLocalDescription(await paire.pc.createOffer())
    await attendreCandidats(paire.pc)
    if (s !== session || paires.get(id) !== paire) return
    paire.offreLe = Date.now()
    await envoyerSignal(id, 'offre', paire.pc.localDescription!.sdp)
  } catch {
    fermerPaire(id)
  }
}

async function repondre(s: number, id: string, arriveLe: string, offre: string) {
  fermerPaire(id)
  const paire = creerPaire(id, arriveLe, false)
  try {
    await paire.pc.setRemoteDescription({ type: 'offer', sdp: offre })
    // Les voies de l'offre : on y branche nos pistes, dans les deux sens.
    for (const t of paire.pc.getTransceivers()) {
      const genre = t.receiver.track.kind
      t.direction = 'sendrecv'
      const piste = genre === 'audio' ? fluxLocal.value?.getAudioTracks()[0] : (pisteEcran ?? pisteCamera)
      if (piste) await t.sender.replaceTrack(piste)
      if (genre === 'video') {
        paire.emetteurVideo = t.sender
        limiterDebit(t.sender)
      }
    }
    await paire.pc.setLocalDescription(await paire.pc.createAnswer())
    await attendreCandidats(paire.pc)
    if (s !== session || paires.get(id) !== paire) return
    await envoyerSignal(id, 'reponse', paire.pc.localDescription!.sdp)
  } catch {
    fermerPaire(id)
  }
}

function creerPaire(id: string, arriveLe: string, offreur: boolean): Paire {
  const pc = new RTCPeerConnection({ iceServers: iceServers ?? [] })
  const distant = new MediaStream()
  let emetteurVideo: RTCRtpSender | null = null
  if (offreur) {
    const audio = fluxLocal.value?.getAudioTracks()[0]
    pc.addTransceiver(audio ?? 'audio', { direction: 'sendrecv' })
    // Toujours une voie vidéo : la caméra peut s'allumer ensuite sans renégocier.
    emetteurVideo = pc.addTransceiver(pisteEcran ?? pisteCamera ?? 'video', { direction: 'sendrecv' }).sender
    limiterDebit(emetteurVideo)
  }
  const canal = pc.createDataChannel('jamii', { negotiated: true, id: 0 })
  const paire: Paire = { pc, canal, emetteurVideo: emetteurVideo!, arriveLe, offreur, offreLe: Date.now(), distant }
  pc.ontrack = (e) => {
    distant.addTrack(e.track)
    fluxDistants.value = { ...fluxDistants.value, [id]: new MediaStream(distant.getTracks()) }
  }
  pc.onconnectionstatechange = () => {
    if (paires.get(id) !== paire) return
    modifierParticipant(id, { connecte: pc.connectionState === 'connected' })
  }
  canal.onopen = () => envoyerEtat(canal)
  canal.onmessage = (e) => recevoir(id, e.data)
  paires.set(id, paire)
  return paire
}

function fermerPaire(id: string) {
  const paire = paires.get(id)
  if (!paire) return
  paires.delete(id)
  paire.canal.close()
  paire.pc.close()
  const { [id]: _parti, ...reste } = fluxDistants.value
  fluxDistants.value = reste
  modifierParticipant(id, { connecte: false })
}

async function envoyerSignal(a: string, type: 'offre' | 'reponse', sdp: string) {
  if (!etat.cercleId) return
  await api.POST('/api/cercles/{cercleId}/salon/appel/signaux', { params: { path: { cercleId: etat.cercleId } }, body: { a, type, sdp } })
}

function limiterDebit(emetteur: RTCRtpSender) {
  try {
    const parametres = emetteur.getParameters()
    if (!parametres.encodings?.length) parametres.encodings = [{}]
    parametres.encodings[0]!.maxBitrate = DEBIT_VIDEO_MAX
    void emetteur.setParameters(parametres).catch(() => undefined)
  } catch {
    /* certains navigateurs n'acceptent les paramètres qu'après la négociation : la contrainte 360p suffit alors */
  }
}

function attendreCandidats(pc: RTCPeerConnection): Promise<void> {
  return new Promise((fini) => {
    if (pc.iceGatheringState === 'complete') return fini()
    const delai = setTimeout(fini, ATTENTE_CANDIDATS_MS)
    pc.addEventListener('icegatheringstatechange', () => {
      if (pc.iceGatheringState === 'complete') {
        clearTimeout(delai)
        fini()
      }
    })
  })
}

async function lireIce(): Promise<RTCIceServer[]> {
  try {
    const { data } = await api.GET('/api/appels/ice')
    if (data?.iceServers.length) return data.iceServers.map((s) => ({ urls: s.urls, username: s.username ?? undefined, credential: s.credential ?? undefined }))
  } catch {
    /* un STUN public */
  }
  return [{ urls: 'stun:stun.l.google.com:19302' }]
}

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

// ---------------------------------------------------------------- pendant l'appel

export function basculerMicro(): void {
  const piste = fluxLocal.value?.getAudioTracks()[0]
  if (!piste) return
  piste.enabled = !piste.enabled
  etat.micro = piste.enabled
  diffuserEtat()
}

export async function basculerCamera(): Promise<void> {
  if (etat.camera) {
    pisteCamera?.stop()
    fluxLocal.value = new MediaStream((fluxLocal.value?.getTracks() ?? []).filter((p) => p !== pisteCamera))
    pisteCamera = null
    etat.camera = false
    if (!etat.ecran) await remplacerVideo(null)
    return diffuserEtat()
  }
  try {
    const flux = await navigator.mediaDevices.getUserMedia({ video: CONTRAINTES_VIDEO })
    const piste = flux.getVideoTracks()[0]
    if (!piste) return
    if (etat.ecran) arreterEcran()
    pisteCamera = piste
    fluxLocal.value = new MediaStream([...(fluxLocal.value?.getTracks() ?? []), piste])
    etat.camera = true
    await remplacerVideo(piste)
    diffuserEtat()
  } catch {
    /* caméra refusée ou absente : on reste en vocal */
  }
}

export async function basculerEcran(): Promise<void> {
  if (etat.ecran) {
    arreterEcran()
    await remplacerVideo(pisteCamera)
    return diffuserEtat()
  }
  try {
    const flux = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: { ideal: 10, max: 15 } }, audio: false })
    const piste = flux.getVideoTracks()[0]
    if (!piste) return
    pisteEcran = piste
    piste.onended = () => {
      arreterEcran()
      void remplacerVideo(pisteCamera)
      diffuserEtat()
    }
    etat.ecran = true
    await remplacerVideo(piste)
    diffuserEtat()
  } catch {
    /* choix de l'écran annulé */
  }
}

function arreterEcran() {
  pisteEcran?.stop()
  pisteEcran = null
  etat.ecran = false
}

async function remplacerVideo(piste: MediaStreamTrack | null) {
  await Promise.all([...paires.values()].map((p) => p.emetteurVideo?.replaceTrack(piste).catch(() => undefined)))
}

export function reagir(emoji: string): void {
  if (!(REACTIONS as readonly string[]).includes(emoji)) return
  const maintenant = Date.now()
  if (maintenant - derniereReaction < 300) return
  derniereReaction = maintenant
  diffuser(JSON.stringify({ t: 'reaction', emoji }))
  montrerReaction(emoji, '', true)
}

export function reduire(valeur: boolean): void {
  etat.reduit = valeur
}

function montrerReaction(emoji: string, nom: string, deMoi: boolean) {
  const id = ++compteurReactions
  etat.reactions = [...etat.reactions.slice(-9), { id, emoji, nom, deMoi }]
  setTimeout(() => (etat.reactions = etat.reactions.filter((r) => r.id !== id)), 2_600)
}

function envoyerEtat(canal: RTCDataChannel) {
  if (canal.readyState === 'open') canal.send(JSON.stringify({ t: 'etat', micro: etat.micro, camera: etat.camera || etat.ecran }))
}

function diffuserEtat() {
  for (const p of paires.values()) envoyerEtat(p.canal)
}

function diffuser(message: string) {
  for (const p of paires.values()) if (p.canal.readyState === 'open') p.canal.send(message)
}

/** Ce qu'un autre navigateur envoie : seulement des valeurs connues, jamais affichées telles quelles. */
export function recevoir(de: string, brut: unknown): void {
  if (typeof brut !== 'string' || brut.length > 500) return
  let signal: Record<string, unknown>
  try {
    signal = JSON.parse(brut)
  } catch {
    return
  }
  if (signal.t === 'etat') modifierParticipant(de, { micro: signal.micro !== false, camera: signal.camera === true })
  else if (signal.t === 'reaction' && typeof signal.emoji === 'string' && (REACTIONS as readonly string[]).includes(signal.emoji)) {
    montrerReaction(signal.emoji, etat.participants.find((p) => p.id === de)?.nom ?? '', false)
  }
}

function modifierParticipant(id: string, valeurs: Partial<Participant>) {
  etat.participants = etat.participants.map((p) => (p.id === id ? { ...p, ...valeurs } : p))
}

function arreter(flux: MediaStream | null) {
  flux?.getTracks().forEach((p) => p.stop())
}

function finir(fin: FinCercle) {
  const s = ++session
  if (minuteur) clearTimeout(minuteur)
  minuteur = null
  window.removeEventListener('pagehide', quitterPage)
  for (const id of [...paires.keys()]) fermerPaire(id)
  arreter(fluxLocal.value)
  pisteEcran?.stop()
  pisteCamera = null
  pisteEcran = null
  fluxLocal.value = null
  fluxDistants.value = {}
  etat.phase = 'fini'
  etat.fin = fin
  etat.reactions = []
  etat.reduit = false
  setTimeout(() => {
    if (s === session && etat.phase === 'fini') Object.assign(etat, etatInitial())
  }, 2_500)
}

/** Pour les tests. */
export function reinitialiserAppelCercle(): void {
  finir('termine')
  fermer()
}
