import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/missions'

type S = components['schemas']
export type Mission = S['Mission']
export type MissionResume = S['MissionResume']
export type NouvelleMission = S['NouvelleMission']
export type NouvelleProposition = S['NouvelleProposition']
export type Proposition = S['Proposition']

// Données de démo (contracts/missions.yaml) : jamais de vraies données personnelles.
const AWA = { id: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11', nom: 'Awa Diop', avatarUrl: null }
const KOFI = { id: '0b9f3c1d-2e4a-4f6b-8c7d-9e0a1b2c3d4e', nom: 'Kofi Mensah', avatarUrl: null }

function erreur(status: number, code: string) {
  return HttpResponse.json(
    { type: `urn:jamii:erreur:${code}`, title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function missionDemo(): Mission {
  return {
    id: 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91',
    titre: 'Boutique en ligne pour un atelier de couture',
    besoin:
      "L'atelier veut montrer ses modèles avec photos et prix, recevoir les commandes sur WhatsApp et être payé par Mobile Money.",
    statut: 'publiee',
    competences: ['vue', 'spring', 'mobile-money'],
    budget: { centimes: 450000, devise: 'XOF' },
    ville: 'Dakar',
    pays: 'SN',
    aDistance: true,
    client: AWA,
    nombrePropositions: 1,
    publieeLe: '2026-11-14T10:00:00Z',
    demandeId: null,
    dateSouhaitee: '2026-12-15',
    visibilite: 'publique',
    version: 2,
    creeLe: '2026-11-14T09:30:00Z',
    moi: { role: 'client', actions: ['modifier', 'fermer', 'inviter', 'retenir'] },
    travail: null,
  }
}

function propositionDemo(): Proposition {
  return {
    id: 'c5e2a7b1-9d3f-4e6a-8b10-7f2c4d6e8a01',
    developpeur: KOFI,
    message: "J'ai déjà fait deux boutiques avec paiement Wave. Je commence par le catalogue, puis la commande WhatsApp.",
    prix: { centimes: 400000, devise: 'XOF' },
    delaiJours: 21,
    reponses: [],
    statut: 'envoyee',
    envoyeeLe: '2026-11-14T12:00:00Z',
  }
}

const missions = new Map<string, Mission>()
const propositions = new Map<string, Proposition[]>()

export function reinitialiserMissions() {
  missions.clear()
  propositions.clear()
  const m = missionDemo()
  missions.set(m.id, m)
  propositions.set(m.id, [propositionDemo()])
}
reinitialiserMissions()

function resume({ id, titre, statut, competences, budget, ville, pays, aDistance, client, nombrePropositions, publieeLe }: Mission): MissionResume {
  return { id, titre, statut, competences, budget, ville, pays, aDistance, client, nombrePropositions, publieeLe }
}

export const handlersMissions = [
  http.get('/api/missions', ({ request }) => {
    const vue = new URL(request.url).searchParams.get('vue') ?? 'publiques'
    const liste = [...missions.values()].filter((m) =>
      vue === 'miennes' ? m.client.id === AWA.id : m.visibilite === 'publique' && m.statut === 'publiee',
    )
    return HttpResponse.json({ elements: liste.map(resume), curseurSuivant: null })
  }),

  http.post('/api/missions', async ({ request }) => {
    const corps = (await request.json()) as NouvelleMission
    if (!corps.titre || corps.titre.trim().length < 3 || !corps.besoin || corps.besoin.trim().length < 20 || !corps.competences?.length) {
      return erreur(400, 'validation')
    }
    const mission: Mission = {
      ...missionDemo(),
      id: crypto.randomUUID(),
      titre: corps.titre,
      besoin: corps.besoin,
      competences: corps.competences,
      budget: corps.budget ?? null,
      dateSouhaitee: corps.dateSouhaitee ?? null,
      visibilite: corps.visibilite,
      ville: corps.ville ?? null,
      pays: corps.pays ?? null,
      aDistance: corps.aDistance ?? true,
      demandeId: corps.demandeId ?? null,
      statut: 'brouillon',
      nombrePropositions: 0,
      publieeLe: null,
      version: 1,
      creeLe: new Date().toISOString(),
      moi: { role: 'client', actions: ['modifier', 'publier', 'inviter'] },
    }
    missions.set(mission.id, mission)
    propositions.set(mission.id, [])
    return HttpResponse.json(mission, { status: 201, headers: { Location: `/api/missions/${mission.id}` } })
  }),

  http.get('/api/missions/:id', ({ params }) => {
    const mission = missions.get(String(params.id))
    return mission ? HttpResponse.json(mission) : erreur(404, 'introuvable')
  }),

  http.post('/api/missions/:id/publier', async ({ params, request }) => {
    const mission = missions.get(String(params.id))
    if (!mission) return erreur(404, 'introuvable')
    const { version } = (await request.json()) as { version: number }
    if (version !== mission.version) return erreur(409, 'modification_concurrente')
    if (mission.statut !== 'brouillon') return erreur(409, 'etat_invalide')
    Object.assign(mission, {
      statut: 'publiee',
      publieeLe: new Date().toISOString(),
      version: mission.version + 1,
      moi: { role: 'client', actions: ['modifier', 'fermer', 'inviter', 'retenir'] },
    })
    return HttpResponse.json(mission)
  }),

  http.get('/api/missions/:id/propositions', ({ params }) => {
    const liste = propositions.get(String(params.id))
    return liste ? HttpResponse.json({ elements: liste, curseurSuivant: null }) : erreur(404, 'introuvable')
  }),

  http.post('/api/missions/:id/propositions', async ({ params, request }) => {
    const mission = missions.get(String(params.id))
    if (!mission) return erreur(404, 'introuvable')
    if (mission.statut !== 'publiee') return erreur(409, 'etat_invalide')
    const corps = (await request.json()) as NouvelleProposition
    if (!corps.message || corps.message.trim().length < 30 || corps.delaiJours < 1) return erreur(400, 'validation')
    const proposition: Proposition = {
      id: crypto.randomUUID(),
      developpeur: KOFI,
      message: corps.message,
      prix: corps.prix,
      delaiJours: corps.delaiJours,
      reponses: corps.reponses ?? [],
      statut: 'envoyee',
      envoyeeLe: new Date().toISOString(),
    }
    propositions.get(mission.id)?.push(proposition)
    mission.nombrePropositions += 1
    return HttpResponse.json(proposition, { status: 201 })
  }),
]
