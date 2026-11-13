import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/reseau'
import { cerclesDemo, resumeCercle } from './cercles'

type ProfilRecommande = components['schemas']['ProfilRecommande']
type Message = components['schemas']['Message']

// Données de démo (contracts/reseau.yaml) : personnes fictives. Awa est la personne connectée.
const AWA = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'
const COMPETENCES_AWA = ['flutter', 'firebase']

const PROFILS = [
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000001', nom: 'Kwame Mensah', avatarUrl: null, pays: 'GH', ville: 'Accra', disponibilite: 'disponible', heuresParSemaine: 10, competences: ['react', 'typescript', 'postgresql'], projetsEnLigne: 3 },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000002', nom: 'Amina Bello', avatarUrl: null, pays: 'NG', ville: 'Lagos', disponibilite: 'disponible', heuresParSemaine: 8, competences: ['ux-design', 'figma'], projetsEnLigne: 2 },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000003', nom: 'Ibrahima Fall', avatarUrl: null, pays: 'SN', ville: 'Saint-Louis', disponibilite: 'disponible', heuresParSemaine: 12, competences: ['spring-boot', 'postgresql'], projetsEnLigne: 1 },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000004', nom: 'Zawadi Otieno', avatarUrl: null, pays: 'KE', ville: 'Nairobi', disponibilite: 'occupe', heuresParSemaine: 4, competences: ['react', 'node'], projetsEnLigne: 4 },
] as const

/** Ce qu'un cercle a déjà comme compétences, d'après ses membres (en démo, un jeu fixe). */
const COUVERT = ['flutter', 'firebase', 'react']
const RECHERCHE = ['postgresql', 'ux-design', 'spring-boot', 'typescript']

const invitations = new Map<string, Set<string>>()
const messages = new Map<string, Message[]>()
let suivant = 1

export function reinitialiserReseau() {
  invitations.clear()
  messages.clear()
  suivant = 1
}

const erreur = (code: string, status = 409) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })

export const handlersReseau = [
  http.get('/api/recommandations/cercles', () => {
    const elements = cerclesDemo()
      .filter((c) => c.statut === 'ouvert' && !c.tirage && !c.moi.estMembre && c.nombreMembres < c.taille)
      .map((c) => ({
        cercle: resumeCercle(c),
        competences: COMPETENCES_AWA.slice(0, 1),
        raisons: c.taille - c.nombreMembres === 1 ? ['competence_manquante', 'presque_complet'] : ['competence_manquante'],
      }))
    return HttpResponse.json({ elements })
  }),

  http.get('/api/recommandations/profils', ({ request }) => {
    const id = new URL(request.url).searchParams.get('cercleId')
    const c = cerclesDemo().find((x) => x.id === id)
    if (!c) return erreur('introuvable', 404)
    if (!c.moi.estMembre) return erreur('interdit', 403)
    if (c.tirage || c.nombreMembres >= c.taille) return HttpResponse.json({ competencesManquantes: [], elements: [] })
    const deja = invitations.get(c.id) ?? new Set()
    const elements: ProfilRecommande[] = PROFILS.map((p) => {
      const competences = p.competences.filter((x) => RECHERCHE.includes(x) && !COUVERT.includes(x))
      const raisons: ProfilRecommande['raisons'] = []
      if (competences.length) raisons.push('competence_manquante')
      if (p.ville === c.ville) raisons.push('meme_ville')
      if (p.disponibilite === 'disponible') raisons.push('disponible')
      return { profil: { ...p, competences: [...p.competences] }, competences, raisons, invite: deja.has(p.utilisateurId), lien: 'aucun' as const }
    })
      .filter((r) => r.raisons.length && r.profil.disponibilite === 'disponible')
      // La même ville d'abord, puis le plus de compétences qui manquent.
      .sort((a, b) => Number(b.raisons.includes('meme_ville')) - Number(a.raisons.includes('meme_ville')) || b.competences.length - a.competences.length)
    return HttpResponse.json({ competencesManquantes: RECHERCHE, elements })
  }),

  http.post('/api/conversations/:personneId/messages', async ({ params, request }) => {
    const personneId = String(params.personneId)
    if (personneId === AWA) return erreur('soi_meme')
    if (!PROFILS.some((p) => p.utilisateurId === personneId)) return erreur('introuvable', 404)
    const corps = (await request.json()) as { texte?: string; cercleId?: string }
    const texte = corps.texte?.trim() ?? ''
    if (!texte || texte.length > 2000) return erreur('validation', 400)
    let invitation: Message['invitation'] = null
    if (corps.cercleId) {
      const c = cerclesDemo().find((x) => x.id === corps.cercleId)
      if (!c) return erreur('introuvable', 404)
      if (!c.moi.estMembre) return erreur('pas_membre_du_cercle')
      if (c.tirage || c.nombreMembres >= c.taille) return erreur('cercle_ferme')
      invitations.set(c.id, (invitations.get(c.id) ?? new Set()).add(personneId))
      invitation = { cercle: resumeCercle(c) }
    }
    const message: Message = { id: `e3f00000-0000-4000-8000-${String(suivant++).padStart(12, '0')}`, deMoi: true, texte, invitation, envoyeLe: new Date().toISOString(), luLe: null }
    messages.set(personneId, [message, ...(messages.get(personneId) ?? [])])
    return HttpResponse.json(message, { status: 201 })
  }),
]
