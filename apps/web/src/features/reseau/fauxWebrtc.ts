import { vi } from 'vitest'

/*
 * Un faux WebRTC pour les tests (jsdom n'a ni RTCPeerConnection, ni MediaStream, ni micro) : assez pour suivre la mise
 * en relation de features/reseau/appel.ts, sans réseau ni média réel.
 */
export type FaussePiste = { kind: 'audio' | 'video'; enabled: boolean; stop: ReturnType<typeof vi.fn>; onended?: unknown }

export const piste = (kind: 'audio' | 'video'): FaussePiste => ({ kind, enabled: true, stop: vi.fn() })

export class FauxFlux {
  pistes: FaussePiste[]
  constructor(pistes: FaussePiste[] = []) {
    this.pistes = [...pistes]
  }
  getTracks() { return this.pistes }
  getAudioTracks() { return this.pistes.filter((p) => p.kind === 'audio') }
  getVideoTracks() { return this.pistes.filter((p) => p.kind === 'video') }
  addTrack(p: FaussePiste) { this.pistes.push(p) }
}

type FausseVoie = { sender: { replaceTrack: ReturnType<typeof vi.fn> }; receiver: { track: { kind: string } }; direction: string }

export class FausseConnexion {
  static derniere: FausseConnexion | null = null
  config: unknown
  localDescription: { type: string; sdp: string } | null = null
  remoteDescription: { type: string; sdp: string } | null = null
  iceGatheringState = 'complete'
  connectionState = 'new'
  signalingState = 'stable'
  /** Toutes les connexions créées, pour l'appel du cercle (une par participant). */
  static toutes: FausseConnexion[] = []
  ontrack: unknown = null
  onconnectionstatechange: (() => void) | null = null
  voies: FausseVoie[] = []
  canal = { readyState: 'open', send: vi.fn(), close: vi.fn(), onopen: null as unknown, onmessage: null as unknown }

  constructor(config: unknown) {
    this.config = config
    FausseConnexion.derniere = this
    FausseConnexion.toutes.push(this)
  }
  addTransceiver(p: FaussePiste | string) {
    const voie = { sender: { replaceTrack: vi.fn(async () => undefined) }, receiver: { track: { kind: typeof p === 'string' ? p : p.kind } }, direction: 'sendrecv' }
    this.voies.push(voie)
    return voie
  }
  getTransceivers() { return this.voies }
  createDataChannel() { return this.canal }
  async createOffer() { return { type: 'offer', sdp: 'v=0\r\no=- offre\r\n' } }
  async createAnswer() { return { type: 'answer', sdp: 'v=0\r\no=- reponse\r\n' } }
  async setLocalDescription(d: { type: string; sdp: string }) {
    this.localDescription = d
    this.signalingState = d.type === 'offer' ? 'have-local-offer' : 'stable'
  }
  async setRemoteDescription(d: { type: string; sdp: string }) {
    this.remoteDescription = d
    this.signalingState = d.type === 'offer' ? 'have-remote-offer' : 'stable'
    if (d.type === 'offer') {
      this.addTransceiver('audio').direction = 'recvonly'
      this.addTransceiver('video').direction = 'recvonly'
    }
  }
  addEventListener() {}
  close() { this.connectionState = 'closed' }
  connecter() {
    this.connectionState = 'connected'
    this.onconnectionstatechange?.()
  }
}

/** Installe le faux WebRTC ; `micro: false` simule un micro refusé. */
export function installerFauxWebrtc({ micro = true } = {}) {
  FausseConnexion.derniere = null
  FausseConnexion.toutes = []
  vi.stubGlobal('RTCPeerConnection', FausseConnexion)
  vi.stubGlobal('MediaStream', FauxFlux)
  const getUserMedia = vi.fn(async (c: { audio?: unknown; video?: unknown }) => {
    if (!micro && c.audio) throw new DOMException('refusé', 'NotAllowedError')
    return new FauxFlux([...(c.audio ? [piste('audio')] : []), ...(c.video ? [piste('video')] : [])])
  })
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true })
  return { getUserMedia }
}
