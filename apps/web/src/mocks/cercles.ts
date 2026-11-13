import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/cercles'

type Cercle = components['schemas']['Cercle']
type MembreCercle = components['schemas']['MembreCercle']
type Tour = components['schemas']['Tour']
type Personne = MembreCercle['personne']

// Données de démo (docs/priorites.md) : le cercle de Dakar. Personnes fictives, adresses en example.com.
// Les réponses suivent contracts/cercles.yaml ; le tirage du mock mélange au hasard, le vrai est fait par le serveur.
const JOUR = 86_400_000
const ilYa = (jours: number) => new Date(Date.now() - jours * JOUR).toISOString()

const AWA = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'
const p = (id: string, nom: string): Personne => ({ id, nom, avatarUrl: null })
const PERSONNES: { personne: Personne; idee: string }[] = [
  { personne: p(AWA, 'Awa Diop'), idee: 'Appli de covoiturage étudiant' },
  { personne: p('c1a00000-0000-4000-8000-000000000002', 'Kofi Yao'), idee: 'Boutique en ligne pour artisans' },
  { personne: p('c1a00000-0000-4000-8000-000000000003', 'Fatou Ndiaye'), idee: 'Suivi des cultures par SMS' },
  { personne: p('c1a00000-0000-4000-8000-000000000004', 'Moussa Traoré'), idee: 'Plateforme de cours de wolof' },
  { personne: p('c1a00000-0000-4000-8000-000000000005', 'Nia Wanjiru'), idee: 'Carnet de santé pour les cliniques' },
  { personne: p('c1a00000-0000-4000-8000-000000000006', 'Yann Kouassi'), idee: 'Annuaire des artisans du quartier' },
]

const slug = (texte: string) => texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

function membre(i: number, rang: number | null, avecIdee = true): MembreCercle {
  const { personne, idee } = PERSONNES[i]!
  return { personne, rang, aDeposeIdee: avecIdee, idee: avecIdee ? { titre: idee, description: `${idee}, pensé pour un téléphone et une connexion lente.` } : null, rejointLe: ilYa(10 - i) }
}

/** Un projet par tour ouvert : sa date de création et ses tâches donnent l'avancement (contracts/projets.yaml). */
const projets = new Map<string, { creeLe: string; livrees: number; total: number }>()
let suivantProjet = 1

function ouvrirTour(t: Tour, depuisJours: number, livrees: number, total: number): Tour {
  const projetId = `b1d2c3a4-0000-4000-8000-${String(suivantProjet++).padStart(12, '0')}`
  projets.set(projetId, { creeLe: ilYa(depuisJours), livrees, total })
  const nom = slug(PERSONNES.find((x) => x.personne.id === t.beneficiaire.id)?.idee ?? t.beneficiaire.nom)
  return { ...t, statut: 'en_cours', projetId, depot: { fournisseur: 'gitea', url: `https://code.example.com/cercles/${nom}`, cloneUrl: `https://code.example.com/cercles/${nom}.git` } }
}

function toursPour(membres: MembreCercle[]): Tour[] {
  return [...membres].sort((a, b) => a.rang! - b.rang!).map((m, i) => ({ numero: i + 1, statut: 'a_venir', beneficiaire: m.personne, projetId: null, depot: null, livreLe: null }))
}

function cercleDakar(): Cercle {
  const membres = PERSONNES.map((_, i) => membre(i, i + 1))
  const tours = toursPour(membres)
  tours[0] = ouvrirTour(tours[0]!, 2, 7, 12)
  return {
    id: 'a0c1e2c1-0000-4000-8000-00000000da4a', nom: 'Cercle de Dakar', ville: 'Dakar', taille: 6, statut: 'en_cours',
    nombreMembres: 6, createur: PERSONNES[0]!.personne, creeLe: ilYa(10),
    membres, tours, tourEnCours: 1, tirage: { effectueLe: ilYa(2), parId: AWA }, moi: { estCreateur: true, estMembre: true },
  }
}

function cercleAbidjan(): Cercle {
  const membres = [1, 2, 3].map((i) => membre(i, null))
  return {
    id: 'a0c1e2c1-0000-4000-8000-0000000ab1d1', nom: 'Cercle d’Abidjan', ville: 'Abidjan', taille: 5, statut: 'ouvert',
    nombreMembres: 3, createur: PERSONNES[1]!.personne, creeLe: ilYa(1),
    // Awa n'en fait pas partie : elle ne voit ni les idées, ni les boutons du créateur.
    membres: membres.map((m) => ({ ...m, idee: undefined })), tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: false, estMembre: false },
  }
}

// Le cercle qu'Awa vient de créer : il lui manque 2 membres, la page lui recommande des profils (mocks/reseau.ts).
function cercleSaintLouis(): Cercle {
  const membres = [membre(0, null), membre(2, null)]
  return {
    id: 'a0c1e2c1-0000-4000-8000-0000000051a1', nom: 'Cercle de Saint-Louis', ville: 'Saint-Louis', taille: 4, statut: 'ouvert',
    nombreMembres: 2, createur: PERSONNES[0]!.personne, creeLe: ilYa(0),
    membres, tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: true, estMembre: true },
  }
}

export const ID_DAKAR = 'a0c1e2c1-0000-4000-8000-00000000da4a'
export const ID_SAINT_LOUIS = 'a0c1e2c1-0000-4000-8000-0000000051a1'
export const ID_ABIDJAN = 'a0c1e2c1-0000-4000-8000-0000000ab1d1'

let cercles: Cercle[] = []
/** Remet les cercles de démo à leur état de départ (entre deux tests). */
export function reinitialiserCercles() {
  projets.clear()
  suivantProjet = 1
  cercles = [cercleSaintLouis(), cercleDakar(), cercleAbidjan()]
}
/** Les cercles de démo tels qu'ils sont maintenant (pour les recommandations de mocks/reseau.ts). */
export const cerclesDemo = () => cercles
export const resumeCercle = (c: Cercle) => resume(c)
reinitialiserCercles()

const erreur = (code: string, status = 409) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })
const trouver = (id: unknown) => cercles.find((c) => c.id === id)
const resume = ({ id, nom, ville, taille, statut, nombreMembres, createur, creeLe }: Cercle) => ({ id, nom, ville, taille, statut, nombreMembres, createur, creeLe })

function melanger<T>(liste: T[]): T[] {
  const copie = [...liste]
  for (let i = copie.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copie[i], copie[j]] = [copie[j]!, copie[i]!]
  }
  return copie
}

export const handlersCercles = [
  http.get('/api/cercles', ({ request }) => {
    const portee = new URL(request.url).searchParams.get('portee') ?? 'ouverts'
    const liste = portee === 'mes' ? cercles.filter((c) => c.moi.estMembre) : cercles.filter((c) => c.statut === 'ouvert' && !c.tirage && c.nombreMembres < c.taille)
    return HttpResponse.json({ elements: liste.map(resume), suivant: null })
  }),
  http.post('/api/cercles', async ({ request }) => {
    const c = (await request.json()) as { nom: string; ville: string; taille: number }
    if (!c.nom || c.nom.length < 3 || !c.ville || c.ville.length < 2 || ![4, 5, 6].includes(c.taille)) return erreur('invalide', 400)
    // En démo, les autres membres rejoignent tout de suite avec leur idée : on peut aller au tirage seule.
    const membres = [membre(0, null, false), ...[1, 2, 3, 4, 5].slice(0, c.taille - 1).map((i) => membre(i, null))]
    const cercle: Cercle = {
      id: `a0c1e2c1-0000-4000-8000-${String(cercles.length + 1).padStart(12, '0')}`, nom: c.nom, ville: c.ville, taille: c.taille, statut: 'ouvert',
      nombreMembres: membres.length, createur: PERSONNES[0]!.personne, creeLe: new Date().toISOString(),
      membres, tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: true, estMembre: true },
    }
    cercles.unshift(cercle)
    return HttpResponse.json(cercle, { status: 201 })
  }),
  http.get('/api/cercles/:id', ({ params }) => {
    const c = trouver(params.id)
    return c ? HttpResponse.json(c) : erreur('introuvable', 404)
  }),
  http.post('/api/cercles/:id/membres', ({ params }) => {
    const c = trouver(params.id)
    if (!c) return erreur('introuvable', 404)
    if (c.moi.estMembre) return erreur('deja_membre')
    if (c.membres.length >= c.taille) return erreur('cercle_complet')
    // Devenue membre, Awa voit les idées des autres.
    c.membres = [...c.membres.map((m, i) => ({ ...m, idee: membre(i + 1, null).idee })), membre(0, null, false)]
    c.nombreMembres = c.membres.length
    c.moi = { ...c.moi, estMembre: true }
    return HttpResponse.json(c)
  }),
  http.put('/api/cercles/:id/membres/moi/idee', async ({ params, request }) => {
    const c = trouver(params.id)
    const moi = c?.membres.find((m) => m.personne.id === AWA)
    if (!c || !moi) return erreur('pas_membre', 403)
    if (c.tirage) return erreur('tirage_deja_fait')
    moi.idee = (await request.json()) as MembreCercle['idee']
    moi.aDeposeIdee = true
    return HttpResponse.json(c)
  }),
  http.post('/api/cercles/:id/tirage', ({ params }) => {
    const c = trouver(params.id)
    if (!c) return erreur('introuvable', 404)
    if (!c.moi.estCreateur) return erreur('interdit', 403)
    if (c.tirage) return erreur('tirage_deja_fait')
    if (c.membres.length < c.taille) return erreur('cercle_incomplet')
    if (c.membres.some((m) => !m.aDeposeIdee)) return erreur('idees_manquantes')
    melanger(c.membres).forEach((m, i) => (m.rang = i + 1))
    c.membres.sort((a, b) => a.rang! - b.rang!)
    c.tours = toursPour(c.membres)
    c.tours[0] = ouvrirTour(c.tours[0]!, 0, 0, 12)
    c.tourEnCours = 1
    c.tirage = { effectueLe: new Date().toISOString(), parId: AWA }
    c.statut = 'en_cours'
    return HttpResponse.json(c)
  }),
  http.post('/api/cercles/:id/tours/:numero/livrer', ({ params }) => {
    const c = trouver(params.id)
    if (!c) return erreur('introuvable', 404)
    const i = c.tours.findIndex((t) => t.numero === Number(params.numero))
    const t = c.tours[i]
    if (!t || t.statut !== 'en_cours') return erreur('tour_non_en_cours')
    if (!c.moi.estCreateur && t.beneficiaire.id !== AWA) return erreur('interdit', 403)
    c.tours[i] = { ...t, statut: 'livre', livreLe: new Date().toISOString() }
    const suivant = c.tours[i + 1]
    if (suivant) {
      c.tours[i + 1] = ouvrirTour(suivant, 0, 0, 10)
      c.tourEnCours = suivant.numero
    } else {
      c.tourEnCours = null
      c.statut = 'termine'
    }
    return HttpResponse.json(c)
  }),
  // Le projet d'un tour : seulement ce que la page du cercle lit (date de création et tâches).
  http.get('/api/projets/:projetId', ({ params }) => {
    const pr = projets.get(String(params.projetId))
    return pr ? HttpResponse.json({ id: params.projetId, creeLe: pr.creeLe }) : erreur('introuvable', 404)
  }),
  http.get('/api/projets/:projetId/taches', ({ params }) => {
    const pr = projets.get(String(params.projetId))
    if (!pr) return erreur('introuvable', 404)
    const elements = Array.from({ length: pr.total }, (_, i) => ({ id: `t${i}`, titre: `Tâche ${i + 1}`, statut: i < pr.livrees ? 'livree' : 'a_faire', proposeeParIa: true, version: 1, creeLe: pr.creeLe }))
    return HttpResponse.json({ elements, suivant: null })
  }),
]
