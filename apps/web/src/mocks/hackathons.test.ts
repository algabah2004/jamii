import { afterEach, describe, expect, it } from 'vitest'
import { reinitialiserHackathons, type Hackathon } from './hackathons'

afterEach(() => reinitialiserHackathons())

const json = (corps: unknown) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) })

const nouveau = {
  titre: 'Hackathon des écoles',
  theme: 'Le numérique au service des écoles',
  description: 'Deux jours pour construire un outil utile à une école de quartier.',
  lieu: 'en_ligne',
  calendrier: {
    inscriptionsOuvertesLe: '2026-12-01T09:00:00Z',
    inscriptionsFermeesLe: '2026-12-10T09:00:00Z',
    debutLe: '2026-12-12T09:00:00Z',
    finRendusLe: '2026-12-14T09:00:00Z',
    resultatsLe: '2026-12-16T09:00:00Z',
  },
  tailleEquipe: { min: 2, max: 4 },
  modeInscription: 'ouvert',
  criteres: [{ nom: 'Utilité', poids: 100 }],
}

describe('maquettes des hackathons (contracts/hackathons.yaml)', () => {
  it('liste les hackathons publiés, sans les brouillons', async () => {
    await fetch('/api/hackathons', json(nouveau))
    const page = await (await fetch('/api/hackathons')).json()
    expect(page.curseurSuivant).toBeNull()
    expect(page.elements).toHaveLength(1)
    expect(page.elements[0].statut).toBe('publie')
  })

  it('crée un brouillon puis le publie avec la bonne version', async () => {
    const reponse = await fetch('/api/hackathons', json(nouveau))
    expect(reponse.status).toBe(201)
    const brouillon: Hackathon = await reponse.json()
    expect(brouillon.statut).toBe('brouillon')
    expect(brouillon.criteres[0]!.id).toBeTruthy()

    const perimee = await fetch(`/api/hackathons/${brouillon.id}/publier`, json({ version: brouillon.version + 1 }))
    expect(perimee.status).toBe(409)
    expect((await perimee.json()).code).toBe('modification_concurrente')

    const publie: Hackathon = await (await fetch(`/api/hackathons/${brouillon.id}/publier`, json({ version: brouillon.version }))).json()
    expect(publie.statut).toBe('publie')
  })

  it('refuse un hackathon sans critère', async () => {
    expect((await fetch('/api/hackathons', json({ ...nouveau, criteres: [] }))).status).toBe(400)
  })

  it("s'inscrire deux fois est refusé", async () => {
    const [h] = (await (await fetch('/api/hackathons')).json()).elements
    expect((await fetch(`/api/hackathons/${h.id}/inscriptions`, json({}))).status).toBe(201)
    const encore = await fetch(`/api/hackathons/${h.id}/inscriptions`, json({}))
    expect(encore.status).toBe(409)
    expect((await encore.json()).code).toBe('deja_inscrit')
  })
})
