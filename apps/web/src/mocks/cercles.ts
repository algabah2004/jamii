import { http, HttpResponse } from 'msw'
import type { Cercle, CercleResume, MembreCercle, Tour } from '../features/cercles/cercles'

// Données de démo (docs/priorites.md) : le cercle de Dakar. Personnes fictives, adresses en example.com.
const JOUR = 86_400_000
const ilYa = (jours: number) => new Date(Date.now() - jours * JOUR).toISOString()
const dans = (jours: number) => new Date(Date.now() + jours * JOUR).toISOString()

const AWA = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'

function membre(id: string, nom: string, ville: string, pays: string, competences: string[], ordre: number, confiance: number, idee: string): MembreCercle {
  return { utilisateurId: id, nom, avatarUrl: null, ville, pays, competences, ordre, confiance, idee: { titre: idee, description: `${idee}, pensé pour un téléphone et une connexion lente.` } }
}

const membresDakar: MembreCercle[] = [
  membre(AWA, 'Awa Diop', 'Dakar', 'SN', ['vue', 'typescript'], 1, 92, 'Appli de covoiturage étudiant'),
  membre('c1a00000-0000-4000-8000-000000000002', 'Kofi Yao', 'Abidjan', 'CI', ['spring', 'postgresql'], 2, 88, 'Boutique en ligne pour artisans'),
  membre('c1a00000-0000-4000-8000-000000000003', 'Fatou Ndiaye', 'Thiès', 'SN', ['design', 'figma'], 3, 95, 'Suivi des cultures par SMS'),
  membre('c1a00000-0000-4000-8000-000000000004', 'Moussa Traoré', 'Bamako', 'ML', ['flutter', 'dart'], 4, 81, 'Plateforme de cours de wolof'),
  membre('c1a00000-0000-4000-8000-000000000005', 'Nia Wanjiru', 'Nairobi', 'KE', ['python', 'data'], 5, 90, 'Carnet de santé pour les cliniques'),
  membre('c1a00000-0000-4000-8000-000000000006', 'Yann Kouassi', 'Dakar', 'SN', ['devops', 'docker'], 6, 86, 'Annuaire des artisans du quartier'),
]

const slug = (texte: string) => texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

function tour(numero: number, m: MembreCercle, statut: Tour['statut'], debut: number): Tour {
  const enCours = statut === 'en_cours'
  return {
    numero,
    beneficiaireId: m.utilisateurId,
    projetId: enCours ? 'b1d2c3a4-0000-4000-8000-0000000000a1' : null,
    titre: m.idee!.titre,
    statut,
    debut: debut < 0 ? ilYa(-debut) : dans(debut),
    fin: dans(debut + 14),
    avancement: enCours ? { faites: 7, total: 12 } : { faites: 0, total: 0 },
    depot: enCours ? { fournisseur: 'gitea', url: `https://code.example.com/cercles/${slug(m.idee!.titre)}`, cloneUrl: `https://code.example.com/cercles/${slug(m.idee!.titre)}.git` } : null,
  }
}

export const cercleDakar: Cercle = {
  id: 'cercle-dakar',
  createurId: AWA,
  nom: 'Cercle de Dakar',
  ville: 'Dakar',
  pays: 'SN',
  taille: 6,
  statut: 'en_cours',
  tirageFait: true,
  membres: membresDakar,
  tours: membresDakar.map((m, i) => tour(i + 1, m, i === 0 ? 'en_cours' : 'a_venir', i * 14 - 3)),
  messageGriot: {
    texte: 'Jour 3 du tour d’Awa : 7 tâches sur 12 terminées. Kofi a livré l’écran de connexion, Fatou la base de données. Demain : la carte des trajets. On est dans les temps.',
    le: ilYa(0.1),
  },
}

export const cercleOuvert: Cercle = {
  id: 'cercle-abidjan',
  createurId: membresDakar[1]!.utilisateurId,
  nom: 'Cercle d’Abidjan',
  ville: 'Abidjan',
  pays: 'CI',
  taille: 5,
  statut: 'ouvert',
  tirageFait: false,
  membres: membresDakar.slice(1, 4).map((m) => ({ ...m, ordre: null })),
  tours: [],
  messageGriot: null,
}

const cercles: Cercle[] = [structuredClone(cercleDakar), structuredClone(cercleOuvert)]

/** Remet les cercles de démo à leur état de départ (entre deux tests). */
export function reinitialiserCercles() {
  cercles.splice(0, cercles.length, structuredClone(cercleDakar), structuredClone(cercleOuvert))
}

// En démo, les autres membres rejoignent tout de suite un nouveau cercle, avec leur idée : on peut aller au tirage seule.
const AUTRES = membresDakar.slice(1)
const erreur = (code: string, status = 409) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })

/** Mélange de Fisher-Yates. Ici pour la démo seulement : le vrai tirage est fait par le serveur (SecureRandom). */
function melanger<T>(liste: T[]): T[] {
  const copie = [...liste]
  for (let i = copie.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copie[i], copie[j]] = [copie[j]!, copie[i]!]
  }
  return copie
}

function resume(c: Cercle): CercleResume {
  const t = c.tours.find((x) => x.statut === 'en_cours')
  const beneficiaire = t && c.membres.find((m) => m.utilisateurId === t.beneficiaireId)
  return {
    id: c.id, nom: c.nom, ville: c.ville, pays: c.pays, taille: c.taille, statut: c.statut,
    membres: c.membres.map(({ utilisateurId, nom, avatarUrl }) => ({ utilisateurId, nom, avatarUrl })),
    tourEnCours: t && beneficiaire ? { numero: t.numero, beneficiaire: beneficiaire.nom } : null,
  }
}

export const handlersCercles = [
  http.post('/api/cercles', async ({ request }) => {
    const c = (await request.json()) as { nom: string; ville: string; taille: number }
    if (!c.nom || c.nom.length < 3 || !c.ville || c.ville.length < 2 || ![4, 5, 6].includes(c.taille)) return erreur('invalide', 400)
    const moi: MembreCercle = { ...membresDakar[0]!, ordre: null, idee: null }
    const cercle: Cercle = {
      id: `cercle-${cercles.length + 1}`, createurId: AWA, nom: c.nom, ville: c.ville, pays: 'SN', taille: c.taille,
      statut: 'ouvert', tirageFait: false, tours: [], messageGriot: null,
      membres: [moi, ...AUTRES.slice(0, c.taille - 1).map((m) => ({ ...m, ordre: null }))],
    }
    cercles.unshift(cercle)
    return HttpResponse.json(cercle, { status: 201 })
  }),
  http.post('/api/cercles/:id/membres', ({ params }) => {
    const c = cercles.find((x) => x.id === params.id)
    if (!c) return erreur('introuvable', 404)
    if (c.membres.some((m) => m.utilisateurId === AWA)) return erreur('deja_membre')
    if (c.membres.length >= c.taille) return erreur('cercle_complet')
    c.membres.push({ ...membresDakar[0]!, ordre: null, idee: null })
    return HttpResponse.json(c)
  }),
  http.put('/api/cercles/:id/membres/moi/idee', async ({ params, request }) => {
    const c = cercles.find((x) => x.id === params.id)
    const moi = c?.membres.find((m) => m.utilisateurId === AWA)
    if (!c || !moi) return erreur('pas_membre', 403)
    if (c.tirageFait) return erreur('tirage_deja_fait')
    moi.idee = (await request.json()) as MembreCercle['idee']
    return HttpResponse.json(c)
  }),
  http.post('/api/cercles/:id/tours/:numero/livrer', ({ params }) => {
    const c = cercles.find((x) => x.id === params.id)
    if (!c) return erreur('introuvable', 404)
    const i = c.tours.findIndex((t) => t.numero === Number(params.numero))
    if (i < 0 || c.tours[i]!.statut !== 'en_cours') return erreur('tour_non_en_cours')
    c.tours[i] = { ...c.tours[i]!, statut: 'livre', avancement: { ...c.tours[i]!.avancement, faites: c.tours[i]!.avancement.total } }
    const suivant = c.tours[i + 1]
    if (suivant) {
      const m = c.membres.find((x) => x.utilisateurId === suivant.beneficiaireId)!
      c.tours[i + 1] = { ...tour(suivant.numero, m, 'en_cours', 0), avancement: { faites: 0, total: 10 } }
    } else c.statut = 'termine'
    c.messageGriot = null
    return HttpResponse.json(c)
  }),
  http.post('/api/cercles/:id/tirage', ({ params }) => {
    const c = cercles.find((x) => x.id === params.id)
    if (!c) return erreur('introuvable', 404)
    if (c.createurId !== AWA) return erreur('pas_createur', 403)
    if (c.tirageFait) return erreur('tirage_deja_fait')
    if (c.membres.length < c.taille) return erreur('cercle_incomplet')
    if (c.membres.some((m) => !m.idee)) return erreur('idees_manquantes')
    melanger(c.membres).forEach((m, i) => (m.ordre = i + 1))
    c.tirageFait = true
    c.statut = 'en_cours'
    c.tours = [...c.membres].sort((a, b) => a.ordre! - b.ordre!).map((m, i) => ({ ...tour(i + 1, m, i === 0 ? 'en_cours' : 'a_venir', i * 14), avancement: { faites: 0, total: i === 0 ? 12 : 0 } }))
    return HttpResponse.json(c)
  }),
  http.get('/api/cercles', () => HttpResponse.json({ elements: cercles.map(resume), suivant: null })),
  http.get('/api/cercles/:id', ({ params }) => {
    const c = cercles.find((x) => x.id === params.id)
    return c
      ? HttpResponse.json(c)
      : HttpResponse.json({ type: 'urn:jamii:erreur:introuvable', title: 'Introuvable', status: 404, code: 'introuvable' }, { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
  }),
]
