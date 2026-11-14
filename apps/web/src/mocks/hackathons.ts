import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/hackathons'

type S = components['schemas']
export type Hackathon = S['Hackathon']
export type HackathonResume = S['HackathonResume']
export type NouveauHackathon = S['NouveauHackathon']
export type Inscription = S['Inscription']
export type Equipe = S['Equipe']

// Données de démo (contracts/hackathons.yaml) : jamais de vraies données personnelles.
const AWA = { id: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11', nom: 'Awa Diop', avatarUrl: null }
const KOFI = { id: '0b9f3c1d-2e4a-4f6b-8c7d-9e0a1b2c3d4e', nom: 'Kofi Mensah', avatarUrl: null }
const FATOU = { id: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', nom: 'Fatou Ndiaye', avatarUrl: null }
const MOUSSA = { id: '9e8d7c6b-5a4f-4e3d-9c2b-1a0f9e8d7c6b', nom: 'Moussa Traoré', avatarUrl: null }

/** Des dates relatives à maintenant : la démo reste crédible quel que soit le jour où on la lance. */
const dansHeures = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString()

function erreur(status: number, code: string) {
  return HttpResponse.json(
    { type: `urn:jamii:erreur:${code}`, title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function hackathonDemo(): Hackathon {
  return {
    id: 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e',
    titre: 'Dakar Cloud Hack',
    theme: 'Des services utiles aux commerçants, hébergés sur le cloud Systalink',
    description: 'Deux jours pour construire, en équipe de 3 à 5, un service qui aide un commerçant de votre quartier. Mentors sur place.',
    lieu: 'hybride',
    ville: 'Dakar',
    statut: 'publie',
    debutLe: dansHeures(72),
    finRendusLe: dansHeures(120),
    organisateur: AWA,
    nombreInscrits: 12,
    calendrier: {
      inscriptionsOuvertesLe: dansHeures(-24),
      inscriptionsFermeesLe: dansHeures(48),
      debutLe: dansHeures(72),
      finRendusLe: dansHeures(120),
      resultatsLe: dansHeures(144),
    },
    tailleEquipe: { min: 3, max: 5 },
    modeInscription: 'ouvert',
    deploiementObligatoire: true,
    codeDeConduite: 'Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.',
    questions: ["Quel problème d'un commerçant résolvez-vous ?"],
    criteres: [
      { id: 'c1000000-0000-4000-8000-000000000001', nom: 'Utilité pour un commerçant', poids: 50 },
      { id: 'c1000000-0000-4000-8000-000000000002', nom: 'Qualité technique', poids: 30 },
      { id: 'c1000000-0000-4000-8000-000000000003', nom: 'Mise en ligne sur DataCloud', poids: 20 },
    ],
    prix: [
      { id: 'p1000000-0000-4000-8000-000000000001', nom: 'Prix du jury', montant: { centimes: 1000000, devise: 'XOF' }, sponsor: null },
      { id: 'p1000000-0000-4000-8000-000000000002', nom: 'Meilleur projet sur DataCloud', montant: null, sponsor: 'Systalink' },
    ],
    version: 1,
    creeLe: dansHeures(-48),
    moi: { role: 'visiteur', actions: ['sinscrire'] },
  }
}

const hackathons = new Map<string, Hackathon>()
const inscriptions = new Map<string, Inscription[]>()
const equipes = new Map<string, Equipe[]>()

/** Deux participants seuls, qui cherchent une équipe : de quoi montrer « Je cherche une équipe ». */
function seulDemo(personne: typeof FATOU, competences: string[]): Inscription {
  return { id: crypto.randomUUID(), personne, statut: 'acceptee', chercheEquipe: true, equipeId: null, competences, creeLe: dansHeures(-20) }
}

export function reinitialiserHackathons() {
  hackathons.clear()
  inscriptions.clear()
  equipes.clear()
  const h = hackathonDemo()
  hackathons.set(h.id, h)
  inscriptions.set(h.id, [seulDemo(FATOU, ['flutter', 'ux-design']), seulDemo(MOUSSA, ['spring'])])
  equipes.set(h.id, [])
}

/** Pour un test : changer un hackathon des maquettes (autre rôle, autre statut), que toutes les routes verront. */
export function modifierHackathon(id: string, changer: (h: Hackathon) => void) {
  const h = hackathons.get(id)
  if (h) changer(h)
}
reinitialiserHackathons()

function resume({ id, titre, theme, lieu, ville, statut, debutLe, finRendusLe, organisateur, nombreInscrits }: Hackathon): HackathonResume {
  return { id, titre, theme, lieu, ville, statut, debutLe, finRendusLe, organisateur, nombreInscrits }
}

export const handlersHackathons = [
  http.get('/api/hackathons', ({ request }) => {
    const statut = new URL(request.url).searchParams.get('statut')
    const liste = [...hackathons.values()].filter((h) => h.statut !== 'brouillon' && h.statut !== 'annule' && (!statut || h.statut === statut))
    return HttpResponse.json({ elements: liste.map(resume), curseurSuivant: null })
  }),

  http.post('/api/hackathons', async ({ request }) => {
    const corps = (await request.json()) as NouveauHackathon
    if (!corps.titre || corps.titre.trim().length < 3 || !corps.criteres?.length) return erreur(400, 'validation')
    const h: Hackathon = {
      ...hackathonDemo(),
      id: crypto.randomUUID(),
      titre: corps.titre,
      theme: corps.theme,
      description: corps.description,
      lieu: corps.lieu,
      ville: corps.ville ?? null,
      calendrier: corps.calendrier,
      debutLe: corps.calendrier.debutLe,
      finRendusLe: corps.calendrier.finRendusLe,
      tailleEquipe: corps.tailleEquipe,
      modeInscription: corps.modeInscription,
      deploiementObligatoire: corps.deploiementObligatoire ?? false,
      codeDeConduite: corps.codeDeConduite ?? null,
      questions: corps.questions ?? [],
      criteres: corps.criteres.map((c, i) => ({ ...c, id: `c2000000-0000-4000-8000-00000000000${i + 1}` })),
      prix: (corps.prix ?? []).map((p, i) => ({ ...p, id: `p2000000-0000-4000-8000-00000000000${i + 1}` })),
      statut: 'brouillon',
      nombreInscrits: 0,
      version: 1,
      creeLe: new Date().toISOString(),
      moi: { role: 'organisateur', actions: ['modifier', 'publier'] },
    }
    hackathons.set(h.id, h)
    inscriptions.set(h.id, [])
    return HttpResponse.json(h, { status: 201, headers: { Location: `/api/hackathons/${h.id}` } })
  }),

  http.get('/api/hackathons/:id', ({ params }) => {
    const h = hackathons.get(String(params.id))
    return h ? HttpResponse.json(h) : erreur(404, 'introuvable')
  }),

  http.post('/api/hackathons/:id/publier', async ({ params, request }) => {
    const h = hackathons.get(String(params.id))
    if (!h) return erreur(404, 'introuvable')
    const { version } = (await request.json()) as { version: number }
    if (version !== h.version) return erreur(409, 'modification_concurrente')
    if (h.statut !== 'brouillon') return erreur(409, 'etat_invalide')
    Object.assign(h, { statut: 'publie', version: h.version + 1, moi: { role: 'organisateur', actions: ['modifier', 'annuler', 'trier_inscriptions', 'inviter_jure'] } })
    return HttpResponse.json(h)
  }),

  http.post('/api/hackathons/:id/inscriptions', async ({ params, request }) => {
    const h = hackathons.get(String(params.id))
    if (!h) return erreur(404, 'introuvable')
    const liste = inscriptions.get(h.id) ?? []
    if (liste.some((i) => i.personne.id === KOFI.id)) return erreur(409, 'deja_inscrit')
    const inscription: Inscription = {
      id: crypto.randomUUID(),
      personne: KOFI,
      statut: h.modeInscription === 'ouvert' ? 'acceptee' : 'en_attente',
      chercheEquipe: ((await request.json()) as { chercheEquipe?: boolean }).chercheEquipe ?? true,
      equipeId: null,
      competences: [],
      creeLe: new Date().toISOString(),
    }
    liste.push(inscription)
    inscriptions.set(h.id, liste)
    h.nombreInscrits += 1
    h.moi = { role: 'participant', equipeId: null, actions: ['creer_equipe'] }
    return HttpResponse.json(inscription, { status: 201 })
  }),

  http.get('/api/hackathons/:id/equipes', ({ params }) => {
    const liste = equipes.get(String(params.id))
    return liste ? HttpResponse.json({ elements: liste, curseurSuivant: null }) : erreur(404, 'introuvable')
  }),

  http.post('/api/hackathons/:id/equipes', async ({ params, request }) => {
    const h = hackathons.get(String(params.id))
    if (!h) return erreur(404, 'introuvable')
    if (h.moi.equipeId) return erreur(409, 'deja_inscrit')
    const { nom } = (await request.json()) as { nom?: string }
    if (!nom || nom.trim().length < 2) return erreur(400, 'validation')
    const equipe: Equipe = { id: crypto.randomUUID(), nom: nom.trim(), chef: KOFI, membres: [KOFI], tailleMax: h.tailleEquipe.max, complete: false, creeLe: new Date().toISOString() }
    equipes.get(h.id)?.push(equipe)
    if (h.moi.role === 'visiteur') h.nombreInscrits += 1
    h.moi = { role: 'participant', equipeId: equipe.id, actions: ['inviter'] }
    return HttpResponse.json(equipe, { status: 201 })
  }),

  http.get('/api/hackathons/:id/sans-equipe', ({ params }) => {
    const liste = inscriptions.get(String(params.id))
    if (!liste) return erreur(404, 'introuvable')
    const seuls = liste.filter((i) => i.statut === 'acceptee' && i.chercheEquipe && !i.equipeId && i.personne.id !== KOFI.id)
    return HttpResponse.json({ elements: seuls, curseurSuivant: null })
  }),

  http.post('/api/hackathons/:id/equipes/:equipeId/membres', async ({ params, request }) => {
    const equipe = equipes.get(String(params.id))?.find((e) => e.id === String(params.equipeId))
    if (!equipe) return erreur(404, 'introuvable')
    const { utilisateurId } = (await request.json()) as { utilisateurId: string }
    const inscription = inscriptions.get(String(params.id))?.find((i) => i.personne.id === utilisateurId)
    if (!inscription || inscription.equipeId) return erreur(409, 'deja_inscrit')
    if (equipe.complete) return erreur(409, 'equipe_complete')
    inscription.equipeId = equipe.id
    equipe.membres.push(inscription.personne)
    equipe.complete = equipe.membres.length >= equipe.tailleMax
    return HttpResponse.json(equipe)
  }),
]
