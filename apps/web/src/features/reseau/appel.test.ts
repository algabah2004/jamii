import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { serveur } from '../../test-setup'
import {
  appeler,
  basculerCamera,
  basculerMicro,
  decrocher,
  raccrocher,
  reagir,
  recevoir,
  refuser,
  reinitialiserAppel,
  useAppel,
  verifierEntrant,
} from './appel'
import { FausseConnexion, installerFauxWebrtc } from './fauxWebrtc'

const AMINA = { id: 'd2e00000-0000-4000-8000-000000000002', nom: 'Amina Diallo', avatarUrl: null }
const APPEL = 'a9900000-0000-4000-8000-000000000001'
const { etat, fluxLocal } = useAppel()

beforeEach(() => {
  installerFauxWebrtc()
})
afterEach(() => {
  reinitialiserAppel()
  vi.unstubAllGlobals()
})

describe('un appel dans Jamii (#157)', () => {
  it("dépose l'offre, attend la réponse de l'autre, puis passe le micro, les réactions et la fin par la connexion directe", async () => {
    const recu: { offre?: string; video?: boolean }[] = []
    let reponse: string | null = null
    let fin = false
    serveur.use(
      http.get('/api/appels/ice', () => HttpResponse.json({ iceServers: [{ urls: ['turn:turn.jamii.test:3478'], username: '1:moi', credential: 'signe' }], relais: true })),
      http.post('/api/conversations/:id/appels', async ({ request }) => {
        recu.push((await request.json()) as { offre?: string; video?: boolean })
        return HttpResponse.json({ id: APPEL, statut: 'sonne', video: true, deMoi: true, personne: AMINA, creeLe: new Date().toISOString() }, { status: 201 })
      }),
      http.get('/api/appels/:id', () => HttpResponse.json({ id: APPEL, statut: reponse ? 'en_cours' : 'sonne', video: true, deMoi: true, personne: AMINA, reponse, creeLe: new Date().toISOString() })),
      http.post('/api/appels/:id/fin', () => {
        fin = true
        return HttpResponse.json({ id: APPEL, statut: 'termine', video: true, deMoi: true, personne: AMINA, creeLe: new Date().toISOString() })
      }),
    )

    await appeler(AMINA, true)
    const pc = FausseConnexion.derniere!
    expect(pc.config).toMatchObject({ iceServers: [{ urls: ['turn:turn.jamii.test:3478'], username: '1:moi', credential: 'signe' }] })
    expect(recu).toEqual([{ offre: 'v=0\r\no=- offre\r\n', video: true }])
    expect(etat.phase).toBe('sonne_chez_lautre')
    expect(etat.camera).toBe(true)
    // Une voie audio et une voie vidéo, toujours : la caméra peut s'allumer sans renégocier.
    expect(pc.voies.map((v) => v.receiver.track.kind)).toEqual(['audio', 'video'])

    reponse = 'v=0\r\no=- reponse\r\n'
    await vi.waitFor(() => expect(pc.remoteDescription).toEqual({ type: 'answer', sdp: reponse }), { timeout: 3000 })
    pc.connecter()
    expect(etat.phase).toBe('en_cours')

    basculerMicro()
    expect(fluxLocal.value!.getAudioTracks()[0]!.enabled).toBe(false)
    expect(pc.canal.send).toHaveBeenLastCalledWith(JSON.stringify({ t: 'etat', micro: false, camera: true }))

    await basculerCamera()
    expect(etat.camera).toBe(false)
    expect(pc.voies[1]!.sender.replaceTrack).toHaveBeenLastCalledWith(null)

    reagir('👍')
    reagir('<img src=x>')
    expect(etat.reactions.map((r) => r.emoji)).toEqual(['👍'])
    expect(pc.canal.send).toHaveBeenLastCalledWith(JSON.stringify({ t: 'reaction', emoji: '👍' }))

    await raccrocher()
    expect(pc.canal.send).toHaveBeenLastCalledWith(JSON.stringify({ t: 'fin' }))
    expect(fin).toBe(true)
    expect(etat.phase).toBe('fini')
    expect(etat.fin).toBe('termine')
    expect(pc.connectionState).toBe('closed')
  })

  it("fait sonner l'appel entrant, puis décroche avec sa réponse", async () => {
    let reponse: string | null = null
    serveur.use(
      http.get('/api/appels/entrant', () => HttpResponse.json({ id: APPEL, statut: 'sonne', video: false, deMoi: false, personne: AMINA, offre: 'v=0\r\no=- offre\r\n', creeLe: new Date().toISOString() })),
      http.post('/api/appels/:id/reponse', async ({ request }) => {
        reponse = ((await request.json()) as { reponse: string }).reponse
        return HttpResponse.json({ id: APPEL, statut: 'en_cours', video: false, deMoi: false, personne: AMINA, creeLe: new Date().toISOString() })
      }),
    )

    await verifierEntrant()
    expect(etat.phase).toBe('sonne_chez_moi')
    expect(etat.personne?.nom).toBe('Amina Diallo')

    await decrocher()
    const pc = FausseConnexion.derniere!
    expect(pc.remoteDescription).toEqual({ type: 'offer', sdp: 'v=0\r\no=- offre\r\n' })
    expect(reponse).toBe('v=0\r\no=- reponse\r\n')
    // Nos pistes branchées sur les voies de l'offre, ouvertes dans les deux sens ; pas de caméra pour un appel vocal.
    expect(pc.voies.every((v) => v.direction === 'sendrecv')).toBe(true)
    expect(pc.voies[0]!.sender.replaceTrack).toHaveBeenCalledOnce()
    expect(pc.voies[1]!.sender.replaceTrack).not.toHaveBeenCalled()
    expect(etat.camera).toBe(false)
  })

  it("s'arrête de sonner quand l'appelant raccroche, et refuser prévient l'API", async () => {
    let entrant = true
    let refus = false
    serveur.use(
      http.get('/api/appels/entrant', () => entrant
        ? HttpResponse.json({ id: APPEL, statut: 'sonne', video: false, deMoi: false, personne: AMINA, offre: 'v=0\r\n', creeLe: new Date().toISOString() })
        : new HttpResponse(null, { status: 204 })),
      http.post('/api/appels/:id/refus', () => {
        refus = true
        return HttpResponse.json({ id: APPEL, statut: 'refuse', video: false, deMoi: false, personne: AMINA, creeLe: new Date().toISOString() })
      }),
    )
    await verifierEntrant()
    entrant = false
    await verifierEntrant()
    expect(etat.phase).toBe('fini')
    expect(etat.fin).toBe('manque')

    reinitialiserAppel()
    entrant = true
    await verifierEntrant()
    await refuser()
    expect(refus).toBe(true)
    expect(etat.fin).toBe('refuse')
  })

  it("explique un micro refusé, sans rien envoyer à l'API", async () => {
    installerFauxWebrtc({ micro: false })
    const depose = vi.fn()
    serveur.use(http.post('/api/conversations/:id/appels', () => {
      depose()
      return HttpResponse.json({}, { status: 500 })
    }))
    await appeler(AMINA, false)
    expect(etat.phase).toBe('fini')
    expect(etat.fin).toBe('micro_refuse')
    expect(depose).not.toHaveBeenCalled()
  })

  it("ne retient de l'autre navigateur que des signaux connus", () => {
    recevoir('pas du json')
    recevoir(JSON.stringify({ t: 'reaction', emoji: '<script>alert(1)</script>' }))
    recevoir('x'.repeat(600))
    expect(etat.reactions).toEqual([])
    recevoir(JSON.stringify({ t: 'reaction', emoji: '🎉' }))
    expect(etat.reactions.map((r) => r.emoji)).toEqual(['🎉'])
    recevoir(JSON.stringify({ t: 'etat', micro: false, camera: true }))
    expect(etat.microDistant).toBe(false)
    expect(etat.cameraDistante).toBe(true)
  })
})
