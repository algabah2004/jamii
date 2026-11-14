import { afterEach, describe, expect, it } from 'vitest'
import { reinitialiserMissions, type Mission } from './missions'

afterEach(() => reinitialiserMissions())

const json = (corps: unknown) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) })
const nouvelle = {
  titre: 'Site pour une association',
  besoin: 'Une page pour présenter nos actions et recevoir des dons par Mobile Money.',
  competences: ['vue'],
  visibilite: 'publique',
}

describe('maquettes des missions (contracts/missions.yaml)', () => {
  it('liste les missions publiques publiées, sans les brouillons', async () => {
    await fetch('/api/missions', json(nouvelle))
    const page = await (await fetch('/api/missions')).json()
    expect(page.curseurSuivant).toBeNull()
    expect(page.elements).toHaveLength(1)
    expect(page.elements[0].statut).toBe('publiee')
  })

  it('crée un brouillon puis le publie avec la bonne version', async () => {
    const reponse = await fetch('/api/missions', json(nouvelle))
    expect(reponse.status).toBe(201)
    const brouillon: Mission = await reponse.json()
    expect(brouillon.statut).toBe('brouillon')

    const perimee = await fetch(`/api/missions/${brouillon.id}/publier`, json({ version: brouillon.version + 1 }))
    expect(perimee.status).toBe(409)
    expect((await perimee.json()).code).toBe('modification_concurrente')

    const publiee: Mission = await (await fetch(`/api/missions/${brouillon.id}/publier`, json({ version: brouillon.version }))).json()
    expect(publiee.statut).toBe('publiee')
    expect(publiee.version).toBe(brouillon.version + 1)
  })

  it('refuse une mission sans besoin décrit', async () => {
    const reponse = await fetch('/api/missions', json({ ...nouvelle, besoin: 'trop court' }))
    expect(reponse.status).toBe(400)
  })

  it('ajoute une proposition et la compte', async () => {
    const [mission] = (await (await fetch('/api/missions')).json()).elements
    const proposition = {
      message: 'Je fais ce genre de site depuis deux ans, avec le paiement Wave et Orange Money.',
      prix: { centimes: 300000, devise: 'XOF' },
      delaiJours: 14,
    }
    expect((await fetch(`/api/missions/${mission.id}/propositions`, json(proposition))).status).toBe(201)
    const page = await (await fetch(`/api/missions/${mission.id}/propositions`)).json()
    expect(page.elements).toHaveLength(2)
    expect((await (await fetch(`/api/missions/${mission.id}`)).json()).nombrePropositions).toBe(2)
  })
})
