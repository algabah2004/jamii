import { http, HttpResponse } from 'msw'
import { piecesDe } from './fichiers'
import type { components } from '../api/generated/reseau'
import { cerclesDemo, resumeCercle } from './cercles'

type ProfilRecommande = components['schemas']['ProfilRecommande']
type Message = components['schemas']['Message']
type Personne = components['schemas']['Personne']
type Notification = components['schemas']['Notification']
type StatutLien = components['schemas']['StatutLien']

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

/** Tous ceux à qui on peut écrire en démo : les profils recommandés et Kofi, créateur du cercle d'Abidjan. */
const KOFI = 'c1a00000-0000-4000-8000-000000000002'
const ABIDJAN = 'a0c1e2c1-0000-4000-8000-0000000ab1d1'
const personnes = (): Personne[] => [
  { id: KOFI, nom: 'Kofi Yao', avatarUrl: null, ville: 'Abidjan' },
  ...PROFILS.map((p) => ({ id: p.utilisateurId, nom: p.nom, avatarUrl: null, ville: p.ville })),
  ...cerclesDemo().filter((c) => ![KOFI, AWA].includes(c.createur.id)).map((c) => ({ ...c.createur, ville: c.ville })),
]
const personne = (id: string) => personnes().find((p) => p.id === id)

const invitations = new Map<string, Set<string>>()
/** Par personne, les messages échangés avec Awa, les plus récents d'abord. */
type AppelDemo = components['schemas']['Appel'] & { message: Message }
const appels = new Map<string, AppelDemo>()
const messages = new Map<string, Message[]>()
/** Le lien de contact entre Awa et chaque personne (absent : aucun). */
const contacts = new Map<string, { statut: Exclude<StatutLien, 'aucun'>; depuis: string }>()
let notifications: Notification[] = []
let suivant = 1
const nouvelId = () => `e3f00000-0000-4000-8000-${String(suivant++).padStart(12, '0')}`
const ilYa = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()

export function reinitialiserReseau() {
  invitations.clear()
  messages.clear()
  contacts.clear()
  suivant = 1
  // Kofi invite Awa dans son cercle d'Abidjan (non lu), Amina lui a répondu hier (lu).
  const abidjan = cerclesDemo().find((c) => c.id === ABIDJAN)
  messages.set(KOFI, [
    { id: nouvelId(), deMoi: false, texte: 'Salut Awa ! Notre cercle cherche quelqu’un qui connaît Flutter. Tu nous rejoins ?', invitation: abidjan ? { cercle: resumeCercle(abidjan) } : null, envoyeLe: ilYa(12), luLe: null, piecesJointes: [] },
  ])
  messages.set(PROFILS[1].utilisateurId, [
    { id: nouvelId(), deMoi: false, texte: 'Avec plaisir, je regarde ton idée ce soir.', invitation: null, envoyeLe: ilYa(60 * 20), luLe: ilYa(60 * 19), piecesJointes: [] },
    { id: nouvelId(), deMoi: true, texte: 'Bonjour Amina, tu aurais un moment pour relire les écrans de mon appli ?', invitation: null, envoyeLe: ilYa(60 * 22), luLe: ilYa(60 * 21), piecesJointes: [] },
  ])
  // Amina est un contact ; Ibrahima (Saint-Louis) a demandé à en être un.
  const AMINA = PROFILS[1].utilisateurId
  const IBRAHIMA = PROFILS[2].utilisateurId
  contacts.set(AMINA, { statut: 'accepte', depuis: ilYa(60 * 19) })
  contacts.set(IBRAHIMA, { statut: 'recue', depuis: ilYa(40) })
  notifications = [
    { id: nouvelId(), type: 'invitation_cercle', personne: personne(KOFI)!, cercle: abidjan ? resumeCercle(abidjan) : null, lien: `/messages/${KOFI}`, creeLe: ilYa(12), lue: false },
    { id: nouvelId(), type: 'demande_contact', personne: personne(IBRAHIMA)!, cercle: null, lien: '/contacts', creeLe: ilYa(40), lue: false },
    { id: nouvelId(), type: 'contact_accepte', personne: personne(AMINA)!, cercle: null, lien: '/contacts', creeLe: ilYa(60 * 19), lue: true },
  ]
}
reinitialiserReseau()

const nonLus = (liste: Message[]) => liste.filter((m) => !m.deMoi && !m.luLe).length

const erreur = (code: string, status = 409) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })

export const handlersReseau = [
  http.get('/api/recommandations/cercles', ({ request }) => {
    const elements = cerclesDemo()
      .filter((c) => c.statut === 'ouvert' && !c.tirage && !c.moi.estMembre && c.nombreMembres < c.taille)
      .map((c) => ({
        cercle: resumeCercle(c),
        competences: COMPETENCES_AWA.slice(0, 1),
        raisons: c.taille - c.nombreMembres === 1 ? ['competence_manquante', 'presque_complet'] : ['competence_manquante'],
      }))
    const limite = Math.max(1, Math.min(20, Number(new URL(request.url).searchParams.get('limite')) || 6))
    return HttpResponse.json({ elements: elements.slice(0, limite) })
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
    if (!personne(personneId)) return erreur('introuvable', 404)
    const corps = (await request.json()) as { texte?: string; cercleId?: string; pieces?: string[] }
    const texte = corps.texte?.trim() ?? ''
    if ((!texte && !corps.pieces?.length) || texte.length > 2000) return erreur('validation', 400)
    let invitation: Message['invitation'] = null
    if (corps.cercleId) {
      const c = cerclesDemo().find((x) => x.id === corps.cercleId)
      if (!c) return erreur('introuvable', 404)
      if (!c.moi.estMembre) return erreur('pas_membre_du_cercle')
      if (c.tirage || c.nombreMembres >= c.taille) return erreur('cercle_ferme')
      invitations.set(c.id, (invitations.get(c.id) ?? new Set()).add(personneId))
      invitation = { cercle: resumeCercle(c) }
    }
    const message: Message = { id: nouvelId(), deMoi: true, texte, invitation, envoyeLe: new Date().toISOString(), luLe: null, piecesJointes: piecesDe(corps.pieces, personneId) }
    messages.set(personneId, [message, ...(messages.get(personneId) ?? [])])
    return HttpResponse.json(message, { status: 201 })
  }),

  // « … est en train d'écrire » (#157) : en démo, personne n'écrit.
  http.post('/api/conversations/:personneId/ecriture', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/conversations/:personneId/ecriture', () => HttpResponse.json({ ecrit: false })),

  // Les appels WebRTC (#157). En démo sans API, personne ne décroche : l'appel sonne, puis on raccroche.
  http.get('/api/appels/ice', () => HttpResponse.json({ iceServers: [{ urls: ['stun:stun.l.google.com:19302'] }], relais: false })),

  http.get('/api/appels/entrant', () => new HttpResponse(null, { status: 204 })),

  http.post('/api/conversations/:personneId/appels', async ({ params, request }) => {
    const personneId = String(params.personneId)
    if (personneId === AWA) return erreur('soi_meme')
    const p = personne(personneId)
    if (!p) return erreur('introuvable', 404)
    const corps = (await request.json().catch(() => null)) as { offre?: string; video?: boolean } | null
    if (!corps?.offre?.startsWith('v=0')) return erreur('validation', 400)
    const id = nouvelId()
    const video = corps.video === true
    const message: Message = { id: nouvelId(), deMoi: true, texte: video ? '📹 Appel vidéo' : '📞 Appel vocal', invitation: null, envoyeLe: new Date().toISOString(), luLe: null, appel: { id, enCours: true, statut: 'sonne', video }, piecesJointes: [] }
    messages.set(personneId, [message, ...(messages.get(personneId) ?? [])])
    appels.set(id, { id, statut: 'sonne', video, deMoi: true, personne: p, creeLe: message.envoyeLe, message })
    return HttpResponse.json(appels.get(id), { status: 201 })
  }),

  http.get('/api/appels/:id', ({ params }) => {
    const a = appels.get(String(params.id))
    return a ? HttpResponse.json(a) : erreur('introuvable', 404)
  }),

  http.post('/api/appels/:id/fin', ({ params }) => {
    const a = appels.get(String(params.id))
    if (!a) return erreur('introuvable', 404)
    if (a.statut === 'sonne') a.statut = 'annule'
    else if (a.statut === 'en_cours') a.statut = 'termine'
    a.message.appel = { ...a.message.appel!, statut: a.statut, enCours: false }
    return HttpResponse.json(a)
  }),

  http.get('/api/conversations', () => {
    const elements = [...messages.entries()]
      .filter(([, liste]) => liste.length)
      .map(([id, liste]) => ({ personne: personne(id)!, dernierMessage: liste[0]!, nonLus: nonLus(liste) }))
      .sort((a, b) => b.dernierMessage.envoyeLe.localeCompare(a.dernierMessage.envoyeLe))
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),

  http.get('/api/conversations/:personneId/messages', ({ params }) => {
    const id = String(params.personneId)
    const p = personne(id)
    if (!p) return erreur('introuvable', 404)
    const liste = messages.get(id) ?? []
    const elements = liste.map((m) => ({ ...m }))
    // Lire la conversation la marque comme lue (la réponse garde l'état d'avant, pour montrer « nouveau »).
    liste.forEach((m) => { if (!m.deMoi && !m.luLe) m.luLe = new Date().toISOString() })
    notifications.forEach((n) => { if (n.personne?.id === id && ['message_recu', 'invitation_cercle'].includes(n.type)) n.lue = true })
    return HttpResponse.json({ personne: p, elements, curseurSuivant: null })
  }),

  http.get('/api/me/activite', () => {
    const messagesNonLus = [...messages.values()].reduce((n, liste) => n + nonLus(liste), 0)
    const demandesContact = [...contacts.values()].filter((c) => c.statut === 'recue').length
    return HttpResponse.json({ notificationsNonLues: notifications.filter((n) => !n.lue).length, messagesNonLus, demandesContact })
  }),

  http.get('/api/me/notifications', () => HttpResponse.json({ elements: notifications, curseurSuivant: null })),
  http.post('/api/me/notifications/lues', () => {
    notifications.forEach((n) => (n.lue = true))
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/me/contacts', ({ request }) => {
    const statut = new URL(request.url).searchParams.get('statut') ?? 'accepte'
    const elements = [...contacts.entries()]
      .filter(([, c]) => c.statut === statut)
      .map(([id, c]) => ({ personne: personne(id)!, statut: c.statut, depuis: c.depuis, competences: [...(PROFILS.find((p) => p.utilisateurId === id)?.competences ?? [])] }))
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),
  http.get('/api/contacts/:personneId', ({ params }) => {
    const id = String(params.personneId)
    if (!personne(id)) return erreur('introuvable', 404)
    const c = contacts.get(id)
    return HttpResponse.json(c ?? { statut: 'aucun', depuis: null })
  }),
  http.put('/api/contacts/:personneId', ({ params }) => {
    const id = String(params.personneId)
    if (id === AWA) return erreur('soi_meme')
    if (!personne(id)) return erreur('introuvable', 404)
    const c = contacts.get(id)
    // Elle m'avait demandé : j'accepte. Sinon la demande part (idempotent).
    const statut = c?.statut === 'recue' || c?.statut === 'accepte' ? 'accepte' : 'envoyee'
    const lien = { statut, depuis: c && statut === c.statut ? c.depuis : new Date().toISOString() } as const
    contacts.set(id, lien)
    return HttpResponse.json(lien)
  }),
  http.delete('/api/contacts/:personneId', ({ params }) => {
    contacts.delete(String(params.personneId))
    return new HttpResponse(null, { status: 204 })
  }),
]
