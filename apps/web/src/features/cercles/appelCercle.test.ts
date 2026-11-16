import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { serveur } from '../../test-setup'
import { chargerSession } from '../../session'
import { moiDemo } from '../../mocks/handlers'
import { FausseConnexion, installerFauxWebrtc } from '../reseau/fauxWebrtc'
import { basculerMicro, quitter, recevoir, reinitialiserAppelCercle, rejoindre, useAppelCercle } from './appelCercle'

const CERCLE = { id: 'ce000000-0000-4000-8000-000000000001', nom: 'Cercle de Dakar' }
const APPEL = 'a9e00000-0000-4000-8000-000000000001'
const FATOU = { id: 'c1a00000-0000-4000-8000-000000000003', nom: 'Fatou Ndiaye', avatarUrl: null }
const KOFI = { id: 'c1a00000-0000-4000-8000-000000000002', nom: 'Kofi Yao', avatarUrl: null }
const MOI = { id: moiDemo.id, nom: moiDemo.nom, avatarUrl: null }
const { etat } = useAppelCercle()

type Signal = { a: string; type: string; sdp: string }

/** Une salle : Fatou est arrivée avant moi, Kofi après ; Kofi m'a déjà envoyé son offre. */
function salle(options: { presence?: () => Response | undefined } = {}) {
  const envoyes: Signal[] = []
  let parti = false
  let signauxLus = false
  serveur.use(
    http.get('/api/appels/ice', () => HttpResponse.json({ iceServers: [{ urls: ['stun:stun.jamii.test:3478'] }], relais: false })),
    http.post('/api/cercles/:id/salon/appel', () =>
      HttpResponse.json({ id: APPEL, lancePar: FATOU, lanceLe: '2026-11-16T10:00:00Z', url: 'https://meet.jit.si/x', presents: 2 })),
    http.post('/api/cercles/:id/salon/appel/presence', () => {
      const autre = options.presence?.()
      if (autre) return autre
      const signaux = signauxLus ? [] : [{ de: KOFI.id, type: 'offre', sdp: 'v=0\r\no=- offre de kofi\r\n' }]
      signauxLus = true
      return HttpResponse.json({
        appelId: APPEL,
        presents: [
          { personne: FATOU, arriveLe: '2026-11-16T10:00:00Z' },
          { personne: MOI, arriveLe: '2026-11-16T10:00:05Z' },
          { personne: KOFI, arriveLe: '2026-11-16T10:00:09Z' },
        ],
        signaux,
      })
    }),
    http.post('/api/cercles/:id/salon/appel/signaux', async ({ request }) => {
      envoyes.push((await request.json()) as Signal)
      return new HttpResponse(null, { status: 204 })
    }),
    http.delete('/api/cercles/:id/salon/appel/presence', () => {
      parti = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return { envoyes, parti: () => parti }
}

beforeEach(async () => {
  installerFauxWebrtc()
  await chargerSession()
})
afterEach(() => {
  reinitialiserAppelCercle()
  vi.unstubAllGlobals()
})

describe("l'appel du cercle, dans Jamii (#157)", () => {
  it("relie chaque paire une fois : j'offre à qui est arrivé avant moi, je réponds à qui est arrivé après", async () => {
    const s = salle()
    await rejoindre(CERCLE)

    expect(etat.phase).toBe('en_cours')
    expect(etat.participants.map((p) => p.nom)).toEqual(['Fatou Ndiaye', 'Kofi Yao'])
    await vi.waitFor(() => expect(s.envoyes).toHaveLength(2))
    expect(s.envoyes).toContainEqual({ a: KOFI.id, type: 'reponse', sdp: 'v=0\r\no=- reponse\r\n' })
    expect(s.envoyes).toContainEqual({ a: FATOU.id, type: 'offre', sdp: 'v=0\r\no=- offre\r\n' })
    // Une connexion par personne, toutes avec nos serveurs ICE.
    expect(FausseConnexion.toutes).toHaveLength(2)
    expect(FausseConnexion.toutes[0]!.config).toMatchObject({ iceServers: [{ urls: ['stun:stun.jamii.test:3478'] }] })

    // Couper le micro prévient tout le monde par la connexion directe.
    basculerMicro()
    for (const pc of FausseConnexion.toutes) expect(pc.canal.send).toHaveBeenLastCalledWith(JSON.stringify({ t: 'etat', micro: false, camera: false }))

    // Ce que les autres envoient : seulement des valeurs connues.
    recevoir(KOFI.id, JSON.stringify({ t: 'etat', micro: false, camera: true }))
    recevoir(KOFI.id, JSON.stringify({ t: 'reaction', emoji: '<img src=x>' }))
    recevoir(KOFI.id, JSON.stringify({ t: 'reaction', emoji: '👏' }))
    expect(etat.participants.find((p) => p.id === KOFI.id)).toMatchObject({ micro: false, camera: true })
    expect(etat.reactions.map((r) => [r.emoji, r.nom])).toEqual([['👏', 'Kofi Yao']])

    await quitter()
    expect(s.parti()).toBe(true)
    expect(etat.phase).toBe('fini')
    for (const pc of FausseConnexion.toutes) expect(pc.connectionState).toBe('closed')
  })

  it("se ferme si l'appel est terminé par quelqu'un d'autre", async () => {
    let termine = false
    salle({ presence: () => (termine ? HttpResponse.json({ code: 'pas_d_appel' }, { status: 404 }) : undefined) })
    await rejoindre(CERCLE)
    expect(etat.phase).toBe('en_cours')
    termine = true
    await vi.waitFor(() => expect(etat.phase).toBe('fini'), { timeout: 4000 })
    expect(etat.fin).toBe('termine')
  })

  it("n'entre pas sans micro", async () => {
    installerFauxWebrtc({ micro: false })
    salle()
    await rejoindre(CERCLE)
    expect(etat.phase).toBe('fini')
    expect(etat.fin).toBe('micro_refuse')
  })
})
