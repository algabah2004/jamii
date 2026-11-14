import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/missions'

type S = components['schemas']
export type Mission = S['Mission']
export type MissionResume = S['MissionResume']
export type NouvelleMission = S['NouvelleMission']
export type NouvelleProposition = S['NouvelleProposition']
export type Proposition = S['Proposition']
export type Jalon = S['Jalon']

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

/** Une mission en cours, vue par sa cliente : un jalon livré qui attend sa réponse, un autre encore en cours. */
export const MISSION_EN_COURS = 'd8f4b2a6-1c3e-4a5b-9d7f-6e2c0a8b4f13'

function jalonDemo(ordre: number, titre: string, centimes: number, statut: Jalon['statut']): Jalon {
  return {
    id: `f0e1d2c3-b4a5-4968-8776-5544332211${String(ordre).padStart(2, '0')}`,
    ordre,
    titre,
    description: null,
    montant: { centimes, devise: 'XOF' },
    delaiJours: 10 * ordre,
    echeance: `2026-11-${String(10 + 10 * ordre).padStart(2, '0')}T09:00:00Z`,
    statut,
    livraisons: [],
    acceptationAutomatiqueLe: null,
    accepteLe: null,
    acceptationAutomatique: false,
    paiement: 'non_paye',
  }
}

function missionEnCoursDemo(): Mission {
  const livre = jalonDemo(1, 'Le catalogue avec photos et prix', 200000, 'livre')
  livre.livraisons = [{ message: 'Le catalogue est en ligne, avec 24 modèles.', liens: ['https://atelier.example/catalogue'], livreeLe: '2026-11-13T16:00:00Z', retouche: null }]
  livre.acceptationAutomatiqueLe = '2026-11-16T16:00:00Z'
  return {
    ...missionDemo(),
    id: MISSION_EN_COURS,
    titre: 'Prise de rendez-vous pour un salon de coiffure',
    statut: 'en_cours',
    nombrePropositions: 3,
    version: 7,
    moi: { role: 'client', actions: ['accepter_jalon', 'demander_retouche', 'annuler', 'ouvrir_desaccord', 'declarer_paye'] },
    travail: {
      developpeur: KOFI,
      devis: {
        id: 'b7c6d5e4-f3a2-4b1c-8d9e-0f1a2b3c4d5e',
        total: { centimes: 400000, devise: 'XOF' },
        retouchesComprises: 2,
        conditions: null,
        jalons: [livre, jalonDemo(2, 'Les rappels par SMS', 200000, 'en_cours')],
        envoyeLe: '2026-11-08T10:00:00Z',
        accepteLe: '2026-11-09T10:00:00Z',
      },
      informationsDepart: 'Les photos des coiffures sont dans le dossier partagé. Contact : la gérante, au salon.',
      demarreLe: '2026-11-10T09:00:00Z',
      retouchesRestantes: 2,
      annulationDemandeePar: null,
      desaccord: null,
      avis: [],
    },
  }
}

/** Ce que la cliente peut faire, recalculé comme l'API après chaque action du suivi. */
function actionsCliente(m: Mission): Mission['moi']['actions'] {
  const jalons = m.travail?.devis.jalons ?? []
  const actions: Mission['moi']['actions'] = []
  if (m.statut === 'attribuee') return ['demarrer', 'annuler']
  if (m.statut === 'en_cours' && jalons.some((j) => j.statut === 'livre')) actions.push('accepter_jalon', 'demander_retouche')
  if (m.statut === 'en_cours') actions.push('annuler', 'ouvrir_desaccord')
  if (jalons.some((j) => ['livre', 'accepte'].includes(j.statut) && j.paiement === 'non_paye')) actions.push('declarer_paye')
  if (m.statut === 'terminee' && !m.travail?.avis?.some((a) => a.auteur === 'client')) actions.push('donner_avis')
  return actions
}

/** Une action de fin de mission : version vérifiée, puis `changer` modifie la mission (ou renvoie un code d'erreur). */
async function finir(params: Record<string, unknown>, request: Request, changer: (m: Mission, t: NonNullable<Mission['travail']>, corps: Record<string, unknown>) => string | void) {
  const mission = missions.get(String(params.id))
  if (!mission?.travail) return erreur(404, 'introuvable')
  const corps = (await request.json()) as Record<string, unknown>
  if (corps.version !== mission.version) return erreur(409, 'modification_concurrente')
  const code = changer(mission, mission.travail, corps)
  if (code) return erreur(code === 'validation' ? 400 : 409, code)
  mission.version += 1
  mission.moi = { ...mission.moi, actions: actionsCliente(mission) }
  return HttpResponse.json(mission)
}

/** Une action du suivi sur un jalon : version vérifiée, puis `changer` modifie la mission (ou renvoie un code d'erreur). */
async function suivre(params: Record<string, unknown>, request: Request, changer: (m: Mission, j: Jalon, corps: Record<string, unknown>) => string | void) {
  const mission = missions.get(String(params.id))
  const jalon = mission?.travail?.devis.jalons.find((j) => j.id === String(params.jalonId))
  if (!mission || !jalon) return erreur(404, 'introuvable')
  const corps = (await request.json()) as Record<string, unknown>
  if (corps.version !== mission.version) return erreur(409, 'modification_concurrente')
  const code = changer(mission, jalon, corps)
  if (code) return erreur(code === 'validation' ? 400 : 409, code)
  mission.version += 1
  if (mission.travail?.devis.jalons.every((j) => j.statut === 'accepte')) mission.statut = 'terminee'
  mission.moi = { ...mission.moi, actions: actionsCliente(mission) }
  return HttpResponse.json(mission)
}

const missions = new Map<string, Mission>()
const propositions = new Map<string, Proposition[]>()

export function reinitialiserMissions() {
  missions.clear()
  propositions.clear()
  const m = missionDemo()
  missions.set(m.id, m)
  propositions.set(m.id, [propositionDemo()])
  const enCours = missionEnCoursDemo()
  missions.set(enCours.id, enCours)
  propositions.set(enCours.id, [])
}
reinitialiserMissions()

/** Pour un test : changer une mission des maquettes (autre rôle, autre statut), que toutes les routes verront. */
export function modifierMission(id: string, changer: (m: Mission) => void) {
  const mission = missions.get(id)
  if (mission) changer(mission)
}

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

  http.post('/api/missions/:id/fermer', async ({ params, request }) => {
    const mission = missions.get(String(params.id))
    if (!mission) return erreur(404, 'introuvable')
    const { version } = (await request.json()) as { version: number }
    if (version !== mission.version) return erreur(409, 'modification_concurrente')
    if (mission.statut !== 'publiee') return erreur(409, 'etat_invalide')
    Object.assign(mission, { statut: 'fermee', version: mission.version + 1, moi: { role: 'client', actions: [] } })
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

  http.post('/api/missions/:id/demarrer', async ({ params, request }) => {
    const mission = missions.get(String(params.id))
    if (!mission?.travail) return erreur(404, 'introuvable')
    const corps = (await request.json()) as { version: number; informations?: string }
    if (corps.version !== mission.version) return erreur(409, 'modification_concurrente')
    if (mission.statut !== 'attribuee') return erreur(409, 'etat_invalide')
    if (!corps.informations?.trim()) return erreur(400, 'validation')
    mission.statut = 'en_cours'
    mission.version += 1
    mission.travail = { ...mission.travail, informationsDepart: corps.informations, demarreLe: new Date().toISOString() }
    for (const j of mission.travail.devis.jalons) j.statut = 'en_cours'
    mission.moi = { ...mission.moi, actions: actionsCliente(mission) }
    return HttpResponse.json(mission)
  }),

  http.post('/api/missions/:id/jalons/:jalonId/livrer', ({ params, request }) =>
    suivre(params, request, (_m, j, corps) => {
      if (j.statut !== 'en_cours') return 'etat_invalide'
      const message = String(corps.message ?? '')
      if (message.trim().length < 10) return 'validation'
      j.statut = 'livre'
      j.livraisons.push({ message, liens: (corps.liens as string[]) ?? [], livreeLe: new Date().toISOString(), retouche: null })
    }),
  ),

  http.post('/api/missions/:id/jalons/:jalonId/accepter', ({ params, request }) =>
    suivre(params, request, (_m, j) => {
      if (j.statut !== 'livre') return 'etat_invalide'
      Object.assign(j, { statut: 'accepte', accepteLe: new Date().toISOString(), acceptationAutomatiqueLe: null })
    }),
  ),

  http.post('/api/missions/:id/jalons/:jalonId/demander-retouche', ({ params, request }) =>
    suivre(params, request, (m, j, corps) => {
      if (j.statut !== 'livre') return 'etat_invalide'
      if (!m.travail || m.travail.retouchesRestantes < 1) return 'retouches_epuisees'
      const raison = String(corps.raison ?? '')
      if (raison.trim().length < 10) return 'validation'
      m.travail.retouchesRestantes -= 1
      const derniere = j.livraisons.at(-1)
      if (derniere) derniere.retouche = raison
      Object.assign(j, { statut: 'en_cours', acceptationAutomatiqueLe: null })
    }),
  ),

  http.post('/api/missions/:id/jalons/:jalonId/declarer-paiement', ({ params, request }) =>
    suivre(params, request, (_m, j, corps) => {
      if (corps.sens === 'paye' && j.paiement === 'non_paye' && j.statut !== 'en_cours') j.paiement = 'declare_paye'
      else if (corps.sens === 'recu' && j.paiement === 'declare_paye') j.paiement = 'declare_recu'
      else return 'etat_invalide'
    }),
  ),

  http.post('/api/missions/:id/annuler', ({ params, request }) =>
    finir(params, request, (m, t, corps) => {
      if (!['attribuee', 'en_cours'].includes(m.statut)) return 'etat_invalide'
      if (String(corps.raison ?? '').trim().length < 10) return 'validation'
      if (t.annulationDemandeePar === 'developpeur') m.statut = 'annulee'
      else if (t.devis.jalons.some((j) => j.statut === 'livre')) return 'annulation_non_permise'
      else t.annulationDemandeePar = 'client'
    }),
  ),

  http.post('/api/missions/:id/desaccord', ({ params, request }) =>
    finir(params, request, (m, t, corps) => {
      if (m.statut !== 'en_cours') return 'etat_invalide'
      const raison = String(corps.raison ?? '')
      if (raison.trim().length < 20) return 'validation'
      m.statut = 'desaccord'
      t.desaccord = { ouvertPar: 'client', raison, ouvertLe: new Date().toISOString(), issue: null, explication: null, trancheLe: null }
    }),
  ),

  http.post('/api/missions/:id/desaccord/trancher', ({ params, request }) =>
    finir(params, request, (m, t, corps) => {
      if (m.statut !== 'desaccord' || !t.desaccord) return 'etat_invalide'
      const issue = corps.issue === 'terminee' ? 'terminee' : 'annulee'
      Object.assign(t.desaccord, { issue, explication: String(corps.explication ?? ''), trancheLe: new Date().toISOString() })
      m.statut = issue
    }),
  ),

  http.post('/api/missions/:id/avis', async ({ params, request }) => {
    const mission = missions.get(String(params.id))
    if (!mission?.travail) return erreur(404, 'introuvable')
    if (mission.statut !== 'terminee') return erreur(409, 'etat_invalide')
    if (mission.travail.avis?.some((a) => a.auteur === 'client')) return erreur(409, 'avis_deja_donne')
    const corps = (await request.json()) as { note: number; texte?: string | null }
    if (!Number.isInteger(corps.note) || corps.note < 1 || corps.note > 5) return erreur(400, 'validation')
    const avis = { auteur: 'client' as const, note: corps.note, texte: corps.texte ?? null, donneLe: new Date().toISOString() }
    mission.travail.avis = [...(mission.travail.avis ?? []), avis]
    mission.moi = { ...mission.moi, actions: actionsCliente(mission) }
    return HttpResponse.json(avis, { status: 201 })
  }),
]
