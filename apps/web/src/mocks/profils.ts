import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/profils'

type MonProfil = components['schemas']['MonProfil']
type Modification = components['schemas']['ModificationProfil']

// Données de démo (docs/plan-final.md) : Awa Diop, la personne connectée de handlers.ts. Jamais de vraies données.
const AWA_ID = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'

/** Durée simulée de la lecture de GitHub, pour voir l'état « en cours » (immédiate dans les tests). */
export const DELAI_GENERATION_MS = import.meta.env.MODE === 'test' ? 0 : 1500

const maintenant = () => new Date().toISOString()

/** Ce que l'API renvoie après la génération (mêmes règles que AnalyseGithub côté API). */
function profilGenere(): MonProfil {
  return {
    utilisateurId: AWA_ID,
    nom: 'Awa Diop',
    avatarUrl: null,
    loginGithub: 'awa-demo',
    bio: null,
    resumeIa: null,
    pays: null,
    ville: null,
    disponibilite: 'disponible',
    heuresParSemaine: null,
    langages: [
      { nom: 'Dart', part: 0.745 },
      { nom: 'TypeScript', part: 0.223 },
      { nom: 'HTML', part: 0.032 },
    ],
    competences: ['dart', 'typescript', 'html', 'flutter', 'firebase', 'node.js', 'postgresql'].map((competence) => ({
      competence,
      source: 'github' as const,
    })),
    projets: [],
    depotsGithub: [
      { nom: 'marche-facile', url: 'https://github.com/awa-demo/marche-facile', description: 'Commandes WhatsApp pour les vendeuses du marché', langage: 'Dart', etoiles: 42, misAJourLe: maintenant() },
      { nom: 'api-paiements', url: 'https://github.com/awa-demo/api-paiements', description: 'Paiement mobile, avec reprise si le réseau coupe', langage: 'TypeScript', etoiles: 17, misAJourLe: maintenant() },
      { nom: 'agri-alertes', url: 'https://github.com/awa-demo/agri-alertes', description: 'Alertes météo par SMS pour les agriculteurs', langage: 'Dart', etoiles: 8, misAJourLe: maintenant() },
    ],
    fiabilite: [],
    misAJourLe: maintenant(),
    publie: false,
    afficherResumeIa: true,
    resumeIaPropose: null,
    // La carte proposée par l'IA (analyse_profil) : des preuves prises dans les dépôts ci-dessus, jamais inventées.
    competencesProposees: [
      { competence: 'flutter', niveau: 'confirme', preuves: ['marche-facile', 'agri-alertes'], raison: 'Deux applications mobiles complètes, utilisées hors ligne.' },
      { competence: 'paiement-mobile', niveau: 'intermediaire', preuves: ['api-paiements'], raison: 'Une API de paiement qui reprend quand le réseau coupe.' },
      { competence: 'typescript', niveau: 'intermediaire', preuves: ['api-paiements'], raison: 'Le serveur de paiement est écrit en TypeScript.' },
      { competence: 'sms', niveau: 'debutant', preuves: ['agri-alertes'], raison: 'Envoi d’alertes par SMS.' },
    ],
    generation: { statut: 'terminee', erreur: null, lanceeLe: maintenant(), termineeLe: maintenant() },
    version: 0,
  }
}

let profil: MonProfil | null = null
let pretLe = 0

export function reinitialiserProfil() {
  profil = null
  pretLe = 0
}

const probleme = (status: number, code: string, title: string) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title, status, code }, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/** La génération se termine quand on relit le profil après le délai. */
function lire(): MonProfil | null {
  if (profil?.generation.statut === 'en_cours' && Date.now() >= pretLe) {
    const precedent = profil
    profil = { ...profilGenere(), ...declare(precedent), version: precedent.version + 1 }
  }
  return profil
}

/** Ce que la personne a déclaré : une régénération ne l'efface jamais. */
function declare(p: MonProfil): Partial<MonProfil> {
  if (p.langages.length === 0) return {}
  return {
    bio: p.bio, pays: p.pays, ville: p.ville, disponibilite: p.disponibilite, heuresParSemaine: p.heuresParSemaine,
    publie: p.publie, afficherResumeIa: p.afficherResumeIa,
    competences: [...profilGenere().competences, ...p.competences.filter((c) => c.source === 'declaree')],
  }
}

export const handlersProfils = [
  http.get('/api/me/profil', () => {
    const p = lire()
    return p ? HttpResponse.json(p) : probleme(404, 'profil_non_genere', 'Aucun profil pour l’instant')
  }),
  http.post('/api/me/profil/generation', () => {
    const p = lire()
    if (p?.generation.statut === 'en_cours') return probleme(409, 'generation_en_cours', 'Génération déjà en cours')
    const vide: MonProfil = { ...profilGenere(), langages: [], competences: [], depotsGithub: [], version: 0 }
    profil = {
      ...(p ?? vide),
      generation: { statut: 'en_cours', erreur: null, lanceeLe: maintenant(), termineeLe: null },
    }
    pretLe = Date.now() + DELAI_GENERATION_MS
    return HttpResponse.json(profil.generation, { status: 202 })
  }),
  http.patch('/api/me/profil', async ({ request }) => {
    const p = lire()
    if (!p) return probleme(404, 'profil_non_genere', 'Aucun profil pour l’instant')
    const m = (await request.json()) as Modification
    if (m.version !== p.version) return probleme(409, 'modification_concurrente', 'Le profil a changé entre-temps')
    const { competencesDeclarees, competencesIaRetenues, version: _version, ...champs } = m
    let competences = competencesDeclarees
      ? [...p.competences.filter((c) => c.source !== 'declaree'), ...competencesDeclarees.map((competence) => ({ competence, source: 'declaree' as const }))]
      : p.competences
    if (competencesIaRetenues) {
      const proposees = p.competencesProposees ?? []
      competences = [
        ...competences.filter((c) => c.source !== 'ia'),
        ...proposees.filter((c) => competencesIaRetenues.includes(c.competence)).map((c) => ({ competence: c.competence, source: 'ia' as const, niveau: c.niveau, preuves: c.preuves })),
      ]
    }
    profil = { ...p, ...champs, competences, version: p.version + 1, misAJourLe: maintenant() }
    return HttpResponse.json(profil)
  }),
  http.post('/api/me/profil/publier', () => {
    const p = lire()
    if (!p) return probleme(404, 'profil_non_genere', 'Aucun profil pour l’instant')
    profil = { ...p, publie: true, version: p.version + 1 }
    return HttpResponse.json(profil)
  }),
  http.post('/api/me/profil/retirer', () => {
    const p = lire()
    if (!p) return probleme(404, 'profil_non_genere', 'Aucun profil pour l’instant')
    profil = { ...p, publie: false, version: p.version + 1 }
    return HttpResponse.json(profil)
  }),
]
