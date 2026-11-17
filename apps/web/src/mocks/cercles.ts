import { http, HttpResponse } from 'msw'
import { piecesDe, piecesDuSalonCercle } from './fichiers'
import type { components } from '../api/generated/cercles'

type Cercle = components['schemas']['Cercle']
type TacheBacklog = components['schemas']['TacheBacklog']
type MembreCercle = components['schemas']['MembreCercle']
type Tour = components['schemas']['Tour']
type Personne = MembreCercle['personne']

// Données de démo (docs/priorites.md) : le cercle de Dakar. Personnes fictives, adresses en example.com.
// Les réponses suivent contracts/cercles.yaml ; le tirage du mock mélange au hasard, le vrai est fait par le serveur.
const JOUR = 86_400_000
const ilYa = (jours: number) => new Date(Date.now() - jours * JOUR).toISOString()
const ilYaHeures = (heures: number) => new Date(Date.now() - heures * 3_600_000).toISOString()

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
type TacheMock = { id: string; titre: string; description: string | null; priorite: 'haute' | 'normale' | 'basse'; statut: 'a_faire' | 'en_cours' | 'livree'; assignee: Personne | null; proposeeParIa: boolean; version: number; creeLe: string; livreeLe: string | null; jalonId: null; echeance: string | null }
type JalonMock = { id: string; titre: string; echeance: string | null; statut: 'prevu' | 'atteint'; ordre: number }
const projets = new Map<string, { creeLe: string; taches: TacheMock[]; porteurId: string; jalons: JalonMock[] }>()
const dansJours = (jours: number) => new Date(Date.now() + jours * JOUR).toISOString().slice(0, 10)
let suivanteTache = 1
const nouvelleTache = (titre: string, statut: TacheMock['statut'], creeLe: string, proposeeParIa = false): TacheMock => ({
  id: `7a5c0000-0000-4000-8000-${String(suivanteTache++).padStart(12, '0')}`, titre, description: null, priorite: 'normale', statut, assignee: null, proposeeParIa, version: 1, creeLe, livreeLe: statut === 'livree' ? creeLe : null, jalonId: null, echeance: null,
})
let suivantProjet = 1

function ouvrirTour(t: Tour, depuisJours: number, livrees: number, total: number, backlog?: TacheBacklog[]): Tour {
  const projetId = `b1d2c3a4-0000-4000-8000-${String(suivantProjet++).padStart(12, '0')}`
  const creeLe = ilYa(depuisJours)
  const taches = Array.from({ length: total }, (_, i) => nouvelleTache(`Tâche ${i + 1}`, i < livrees ? 'livree' : 'a_faire', creeLe, true))
  // Deux tâches ouvertes détaillées, pour voir une fiche complète en démo.
  const [a, b] = taches.filter((x) => x.statut === 'a_faire')
  if (a) Object.assign(a, { titre: 'Synchroniser les données au retour du réseau', priorite: 'haute', description: 'Envoyer au serveur ce qui a été saisi hors ligne.\n\nFini quand : deux téléphones retrouvent les mêmes totaux, sans doublon.' })
  if (b) Object.assign(b, { titre: 'Mettre l’API en ligne sur DataCloud', priorite: 'basse' })
  // Le calendrier du tour, posé par son chef de projet (le bénéficiaire) : une étape atteinte, deux à venir.
  const jalons: JalonMock[] = [
    { id: `${projetId.slice(0, -2)}j1`, titre: 'Maquette validée', echeance: dansJours(-1), statut: 'atteint', ordre: 0 },
    { id: `${projetId.slice(0, -2)}j2`, titre: 'Démo aux premiers utilisateurs', echeance: dansJours(3), statut: 'prevu', ordre: 1 },
    { id: `${projetId.slice(0, -2)}j3`, titre: 'Revue de code', echeance: dansJours(7), statut: 'prevu', ordre: 2 },
  ]
  if (backlog) taches.splice(0, taches.length, ...backlog.map((t) => Object.assign(nouvelleTache(t.titre, 'a_faire', creeLe), { description: t.description ?? null, priorite: t.priorite ?? 'normale', echeance: t.echeance ?? null })))
  projets.set(projetId, { creeLe, taches, porteurId: t.beneficiaire.id, jalons })
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
    nombreMembres: 6, createur: PERSONNES[0]!.personne, creeLe: ilYa(10), profilsRecherches: [],
    membres, tours, tourEnCours: 1, tirage: { effectueLe: ilYa(2), parId: AWA }, moi: { estCreateur: true, estMembre: true },
  }
}

function cercleAbidjan(): Cercle {
  const membres = [1, 2, 3].map((i) => membre(i, null))
  return {
    id: 'a0c1e2c1-0000-4000-8000-0000000ab1d1', nom: 'Cercle d’Abidjan', ville: 'Abidjan', taille: 5, statut: 'ouvert',
    nombreMembres: 3, createur: PERSONNES[1]!.personne, creeLe: ilYa(1), profilsRecherches: ['mobile', 'design'],
    // Awa n'en fait pas partie : elle ne voit ni les idées, ni les boutons du créateur.
    membres: membres.map((m) => ({ ...m, idee: undefined })), tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: false, estMembre: false },
  }
}

// Le cercle qu'Awa vient de créer : il lui manque 2 membres, la page lui recommande des profils (mocks/reseau.ts).
function cercleSaintLouis(): Cercle {
  const membres = [membre(0, null), membre(2, null)]
  return {
    id: 'a0c1e2c1-0000-4000-8000-0000000051a1', nom: 'Cercle de Saint-Louis', ville: 'Saint-Louis', taille: 4, statut: 'ouvert',
    nombreMembres: 2, createur: PERSONNES[0]!.personne, creeLe: ilYa(0), profilsRecherches: ['back'],
    membres, tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: true, estMembre: true },
  }
}

// Répertoire de démonstration uniquement : jamais inséré dans l’API ni dans une base de production.
const DECOUVERTES = [
  ['Sahel Mobile', 'Bamako', 'Aïssata Koné'], ['Lagos Builders', 'Lagos', 'Chinedu Okafor'],
  ['Nairobi Open Source', 'Nairobi', 'Wanjiku Kamau'], ['Design & Impact', 'Accra', 'Abena Owusu'],
  ['Santé Connect', 'Kigali', 'Diane Uwase'], ['Les Makers de Thiès', 'Thiès', 'Cheikh Sarr'],
  ['Casa Web Studio', 'Casablanca', 'Salma El Amrani'], ['Douala Numérique', 'Douala', 'Alain Ngono'],
  ['Cotonou Créatif', 'Cotonou', 'Grâce Dossou'], ['Tunis Code Lab', 'Tunis', 'Yasmine Ben Ali'],
  ['Ubuntu Builders', 'Cape Town', 'Lerato Molefe'], ['Freetown Devs', 'Freetown', 'Mariama Bangura'],
  ['AgriTech Ensemble', 'Ouagadougou', 'Adama Ouédraogo'], ['Les Artisanes du Web', 'Dakar', 'Sokhna Ba'],
  ['Conakry Connect', 'Conakry', 'Mamadou Diallo'], ['Luanda Digital', 'Luanda', 'Ana dos Santos'],
  ['Maputo Makers', 'Maputo', 'Celina Machel'], ['Tech pour l’école', 'Abidjan', 'Aya N’Guessan'],
  ['Addis Creative Code', 'Addis-Abeba', 'Hana Tesfaye'], ['Nouakchott Ensemble', 'Nouakchott', 'Aminet Mint Ahmed'],
  ['Antananarivo Studio', 'Antananarivo', 'Mialy Rakoto'],
] as const

function cerclesDecouverte(): Cercle[] {
  return DECOUVERTES.map(([nom, ville, fondateur], i) => {
    const createur = p(`c1a00000-0000-4000-8000-${String(100 + i).padStart(12, '0')}`, fondateur)
    const taille = 4 + i % 3
    const autres = Array.from({ length: i % (taille - 1) }, (_, j) => membre(1 + (i + j) % 5, null))
    const membres: MembreCercle[] = [{ personne: createur, rang: null, aDeposeIdee: true, rejointLe: ilYa(i + 2) }, ...autres]
    return {
      id: `a0c1e2c1-0000-4000-8000-${String(100 + i).padStart(12, '0')}`, nom, ville, taille, statut: 'ouvert',
      nombreMembres: membres.length, createur, creeLe: ilYa(i + 2), membres, tours: [], tourEnCours: null, tirage: null,
      profilsRecherches: [(['front', 'back', 'mobile', 'design', 'data', 'ia'] as const)[i % 6]!, ...(i % 3 === 0 ? (['devops'] as const) : [])],
      moi: { estCreateur: false, estMembre: false },
    }
  })
}

export const ID_DAKAR = 'a0c1e2c1-0000-4000-8000-00000000da4a'
export const ID_SAINT_LOUIS = 'a0c1e2c1-0000-4000-8000-0000000051a1'
export const ID_ABIDJAN = 'a0c1e2c1-0000-4000-8000-0000000ab1d1'

let cercles: Cercle[] = []

// --- Le salon des cercles (contracts/cercles.yaml v0.2) ---
type MessageCercle = components['schemas']['MessageCercle']
type AppelCercle = components['schemas']['AppelCercle']
const salons = new Map<string, MessageCercle[]>()
const appels = new Map<string, AppelCercle>()
let suivantNumero = 1
const ilYaMinutes = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()
const personneDe = (i: number) => PERSONNES[i]!.personne

let suivanteVersion = 1
type Defauts = 'id' | 'numero' | 'auteur' | 'texte' | 'point' | 'evenement' | 'coupDeMain' | 'appel' | 'piecesJointes' | 'version' | 'reponseA' | 'modifieLe' | 'supprime' | 'reactions'
function poster(cercleId: string, m: Omit<MessageCercle, Defauts> & Partial<MessageCercle>): MessageCercle {
  const numero = suivantNumero++
  const message: MessageCercle = {
    id: `5a10${String(numero).padStart(4, '0')}-0000-4000-8000-000000000000`, numero, auteur: null, texte: null, point: null, evenement: null, coupDeMain: null, appel: null,
    piecesJointes: [], version: suivanteVersion++, reponseA: null, modifieLe: null, supprime: false, reactions: [], ...m,
  }
  salons.set(cercleId, [...(salons.get(cercleId) ?? []), message])
  return message
}

/** Le salon du cercle de Dakar, au 3e jour du tour de Kofi : de quoi voir chaque sorte de message. */
function salonDakar() {
  const ev = (code: NonNullable<MessageCercle['evenement']>['code'], minutes: number, personne: Personne | null, tour: number | null = null) =>
    poster(ID_DAKAR, { type: 'evenement', envoyeLe: ilYaMinutes(minutes), evenement: { code, tour, personne } })
  ev('cercle_cree', 60 * 24 * 12, personneDe(0))
  ev('tirage', 60 * 24 * 9, personneDe(2))
  ev('tour_ouvert', 60 * 24 * 9, personneDe(0), 1)
  ev('tour_livre', 60 * 24 * 3, personneDe(0), 1)
  ev('tour_ouvert', 60 * 24 * 3, personneDe(1), 2)
  poster(ID_DAKAR, { type: 'texte', auteur: personneDe(1), reactions: [{ emoji: '👍', nombre: 2, deMoi: false, noms: [personneDe(2).nom, personneDe(3).nom] }], texte: 'J’ai découpé le projet en 12 tâches, prenez celles qui vous parlent.', envoyeLe: ilYaMinutes(60 * 24 * 3 - 10) })
  poster(ID_DAKAR, { type: 'point', auteur: personneDe(1), point: { fait: 'La connexion par numéro de téléphone.', prevu: 'Le tableau des pesées du jour.', bloque: null }, envoyeLe: ilYaMinutes(90) })
  poster(ID_DAKAR, { type: 'decision', auteur: personneDe(1), texte: 'On garde Flutter pour l’appli des pesées.', envoyeLe: ilYaMinutes(60 * 24 * 3 - 20) })
  poster(ID_DAKAR, { type: 'coup_de_main', auteur: personneDe(3), texte: 'L’APK ne s’installe pas sur les vieux Android 8. Une idée ?', coupDeMain: { resoluLe: null, resoluPar: null }, envoyeLe: ilYaMinutes(45) })
}
/** Remet les cercles de démo à leur état de départ (entre deux tests). */
export function reinitialiserCercles() {
  projets.clear()
  suivantProjet = 1
  cercles = [cercleSaintLouis(), cercleDakar(), cercleAbidjan(), ...cerclesDecouverte()]
  salons.clear()
  suivantNumero = 1
  suivanteVersion = 1
  appels.clear()
  salonDakar()
}
/** Les cercles de démo tels qu'ils sont maintenant (pour les recommandations de mocks/reseau.ts). */
export const cerclesDemo = () => cercles
export const resumeCercle = (c: Cercle) => resume(c)
reinitialiserCercles()

const erreur = (code: string, status = 409) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code }, { status, headers: { 'Content-Type': 'application/problem+json' } })
const trouver = (id: unknown) => cercles.find((c) => c.id === id)
const resume = ({ id, nom, ville, taille, statut, nombreMembres, createur, creeLe, profilsRecherches }: Cercle) => ({ id, nom, ville, taille, statut, nombreMembres, createur, creeLe, profilsRecherches })

function melanger<T>(liste: T[]): T[] {
  const copie = [...liste]
  for (let i = copie.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copie[i], copie[j]] = [copie[j]!, copie[i]!]
  }
  return copie
}

const salonDe = (id: unknown) => {
  const c = trouver(id)
  if (!c) return { reponse: erreur('introuvable', 404) }
  if (!c.moi.estMembre) return { reponse: erreur('pas_membre', 403) }
  return { cercle: c, messages: salons.get(c.id) ?? [] }
}
const debutDuJour = () => new Date().toISOString().slice(0, 10)

export const handlersCercles = [
  http.get('/api/cercles/:id/salon', ({ params, request }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const apres = new URL(request.url).searchParams.get('apres')
    const elements = apres === null ? s.messages.slice(-100) : s.messages.filter((m) => m.version > Number(apres))
    return HttpResponse.json({
      elements,
      coupsDeMain: s.messages.filter((m) => m.type === 'coup_de_main' && !m.coupDeMain?.resoluLe),
      decisions: s.messages.filter((m) => m.type === 'decision').reverse().slice(0, 30),
      pointsDuJour: [...new Set(s.messages.filter((m) => m.type === 'point' && m.envoyeLe.slice(0, 10) === debutDuJour()).map((m) => m.auteur!.id))],
      appel: appels.get(s.cercle.id) ?? null,
    })
  }),
  http.post('/api/cercles/:id/salon/messages', async ({ params, request }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const corps = (await request.json()) as { type: string; texte?: string; pieces?: string[]; reponseA?: string; point?: { fait: string; prevu: string; bloque?: string | null } }
    const moi = PERSONNES[0]!.personne
    const maintenant = new Date().toISOString()
    if (corps.type === 'point') {
      if (!corps.point?.fait?.trim() || !corps.point?.prevu?.trim()) return erreur('validation', 400)
      if (s.messages.some((m) => m.type === 'point' && m.auteur?.id === moi.id && m.envoyeLe.slice(0, 10) === debutDuJour())) return erreur('point_deja_fait')
      return HttpResponse.json(poster(s.cercle.id, { type: 'point', auteur: moi, point: { fait: corps.point.fait, prevu: corps.point.prevu, bloque: corps.point.bloque ?? null }, envoyeLe: maintenant }), { status: 201 })
    }
    const avecPieces = !!corps.pieces?.length && corps.type === 'texte'
    if ((!corps.texte?.trim() && !avecPieces) || !['texte', 'coup_de_main', 'decision'].includes(corps.type)) return erreur('validation', 400)
    // Les décisions sont consignées par le chef de projet : le bénéficiaire du tour en cours (ou le créateur).
    const chef = s.cercle.tours.find((x) => x.numero === s.cercle.tourEnCours)?.beneficiaire.id ?? s.cercle.createur.id
    if (corps.type === 'decision' && chef !== moi.id) return erreur('pas_chef_de_projet', 403)
    const type = corps.type as 'texte' | 'coup_de_main' | 'decision'
    const cite = s.messages.find((x) => x.id === corps.reponseA)
    const reponseA = cite ? { id: cite.id, auteur: cite.auteur, texte: cite.supprime ? '' : (cite.texte ?? '').slice(0, 140), supprime: cite.supprime } : null
    return HttpResponse.json(poster(s.cercle.id, { type, auteur: moi, texte: corps.texte?.trim() ?? '', piecesJointes: piecesDuSalonCercle(corps.pieces, s.cercle.id), coupDeMain: type === 'coup_de_main' ? { resoluLe: null, resoluPar: null } : null, envoyeLe: maintenant, reponseA }), { status: 201 })
  }),
  // Répondre, modifier, supprimer, réagir (#157).
  http.patch('/api/cercles/:id/salon/messages/:messageId', async ({ params, request }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const m = s.messages.find((x) => x.id === params.messageId)
    if (!m) return erreur('introuvable', 404)
    if (m.auteur?.id !== PERSONNES[0]!.personne.id || m.type !== 'texte' || m.supprime) return erreur('non_modifiable')
    const { texte } = (await request.json()) as { texte: string }
    if (!texte.trim()) return erreur('validation', 400)
    Object.assign(m, { texte: texte.trim(), modifieLe: new Date().toISOString(), version: suivanteVersion++ })
    return HttpResponse.json(m)
  }),
  http.delete('/api/cercles/:id/salon/messages/:messageId', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const m = s.messages.find((x) => x.id === params.messageId)
    if (!m) return erreur('introuvable', 404)
    if (m.auteur?.id !== PERSONNES[0]!.personne.id) return erreur('interdit', 403)
    Object.assign(m, { texte: null, piecesJointes: [], reactions: [], point: null, supprime: true, version: suivanteVersion++ })
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/api/cercles/:id/salon/messages/:messageId/reactions', async ({ params, request }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const m = s.messages.find((x) => x.id === params.messageId)
    if (!m) return erreur('introuvable', 404)
    if (m.supprime) return erreur('message_supprime')
    const { emoji, active } = (await request.json()) as { emoji: string; active: boolean }
    const existante = m.reactions.find((r) => r.emoji === emoji)
    if (active && !existante?.deMoi) {
      if (existante) Object.assign(existante, { nombre: existante.nombre + 1, deMoi: true, noms: [...existante.noms, 'moi'] })
      else m.reactions = [...m.reactions, { emoji, nombre: 1, deMoi: true, noms: ['moi'] }]
    } else if (!active && existante?.deMoi) {
      m.reactions = existante.nombre > 1
        ? m.reactions.map((r) => (r === existante ? { ...r, nombre: r.nombre - 1, deMoi: false, noms: r.noms.filter((n) => n !== 'moi') } : r))
        : m.reactions.filter((r) => r !== existante)
    }
    m.version = suivanteVersion++
    return HttpResponse.json(m)
  }),
  http.post('/api/cercles/:id/salon/messages/:messageId/resolu', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const m = s.messages.find((x) => x.id === params.messageId)
    if (!m) return erreur('introuvable', 404)
    if (m.type !== 'coup_de_main') return erreur('pas_un_coup_de_main')
    if (!m.coupDeMain?.resoluLe) Object.assign(m, { coupDeMain: { resoluLe: new Date().toISOString(), resoluPar: PERSONNES[0]!.personne }, version: suivanteVersion++ })
    return HttpResponse.json(m)
  }),
  http.post('/api/cercles/:id/salon/appel', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const deja = appels.get(s.cercle.id)
    if (deja) return HttpResponse.json(deja)
    const appel: AppelCercle = { id: `a9e10000-0000-4000-8000-${String(suivantNumero).padStart(12, '0')}`, lancePar: PERSONNES[0]!.personne, lanceLe: new Date().toISOString(), url: 'https://meet.jit.si/jamii-cercle-demo', presents: 0 }
    appels.set(s.cercle.id, appel)
    poster(s.cercle.id, { type: 'appel', auteur: PERSONNES[0]!.personne, appel: { id: appel.id, enCours: true }, envoyeLe: appel.lanceLe })
    return HttpResponse.json(appel, { status: 201 })
  }),
  // La salle WebRTC de l'appel (#157). En démo, on y est seul : les autres membres n'existent pas vraiment.
  http.post('/api/cercles/:id/salon/appel/presence', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const appel = appels.get(s.cercle.id)
    if (!appel) return erreur('pas_d_appel', 404)
    appel.presents = 1
    return HttpResponse.json({ appelId: appel.id, presents: [{ personne: PERSONNES[0]!.personne, arriveLe: appel.lanceLe }], signaux: [] })
  }),
  http.post('/api/cercles/:id/salon/appel/signaux', () => erreur('pas_dans_l_appel')),
  http.delete('/api/cercles/:id/salon/appel/presence', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const appel = appels.get(s.cercle.id)
    if (appel) {
      appels.delete(s.cercle.id)
      for (const m of s.messages) if (m.appel?.id === appel.id) m.appel = { id: appel.id, enCours: false }
    }
    return new HttpResponse(null, { status: 204 })
  }),
  http.delete('/api/cercles/:id/salon/appel', ({ params }) => {
    const s = salonDe(params.id)
    if (s.reponse) return s.reponse
    const appel = appels.get(s.cercle.id)
    if (!appel) return erreur('introuvable', 404)
    appels.delete(s.cercle.id)
    for (const m of s.messages) if (m.appel?.id === appel.id) m.appel = { id: appel.id, enCours: false }
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('/api/cercles', ({ request }) => {
    const portee = new URL(request.url).searchParams.get('portee') ?? 'ouverts'
    const liste = portee === 'mes' ? cercles.filter((c) => c.moi.estMembre) : cercles.filter((c) => c.statut === 'ouvert' && !c.tirage && c.nombreMembres < c.taille)
    return HttpResponse.json({ elements: liste.map(resume), suivant: null })
  }),
  http.post('/api/cercles', async ({ request }) => {
    const c = (await request.json()) as { nom: string; ville?: string; taille: number; profils?: Cercle['profilsRecherches'] }
    // La ville est facultative depuis le 15/11 : le formulaire ne la demande plus.
    if (!c.nom || c.nom.length < 3 || (c.ville != null && c.ville.length < 2) || ![4, 5, 6].includes(c.taille)) return erreur('invalide', 400)
    // En démo, les autres membres rejoignent tout de suite avec leur idée : on peut aller au tirage seule.
    const membres = [membre(0, null, false), ...[1, 2, 3, 4, 5].slice(0, c.taille - 1).map((i) => membre(i, null))]
    const cercle: Cercle = {
      id: `a0c1e2c1-0000-4000-8000-${String(cercles.length + 1).padStart(12, '0')}`, nom: c.nom, ville: c.ville ?? null, taille: c.taille, statut: 'ouvert',
      nombreMembres: membres.length, createur: PERSONNES[0]!.personne, creeLe: new Date().toISOString(),
      membres, tours: [], tourEnCours: null, tirage: null, moi: { estCreateur: true, estMembre: true },
      profilsRecherches: [...new Set(c.profils ?? [])],
    }
    cercles.unshift(cercle)
    return HttpResponse.json(cercle, { status: 201 })
  }),
  // Le code du tour (contracts/cercles.yaml, activiteDuTour) : ce que l'API lirait dans le dépôt GitHub du tour.
  http.get('/api/cercles/:id/tours/:numero/activite', ({ params }) => {
    const c = trouver(params.id)
    const tour = c?.tours.find((x) => x.numero === Number(params.numero))
    if (!c || !tour) return erreur('introuvable', 404)
    if (!tour.depot) {
      return HttpResponse.json({ etat: 'aucun_depot', depot: null, luLe: ilYa(0), commits: [], demandesDeFusion: [] })
    }
    const base = tour.depot.url
    const login = (i: number) => slug(PERSONNES[i]!.personne.nom)
    const commit = (sha: string, message: string, i: number, heures: number) => ({
      sha, message, auteur: { login: login(i), avatarUrl: null }, date: ilYaHeures(heures), url: `${base}/commit/${sha}`,
    })
    return HttpResponse.json({
      etat: 'disponible', depot: tour.depot, luLe: ilYa(0),
      commits: [
        commit('9fceb02d0ae598e95dc970b74767f19372d61af8', "Réservation d'un trajet depuis la carte", 1, 1),
        commit('7c41a3e0b2d94f6a8e15c0d7b9f2a6e4c3d1b0a9', 'Notifications SMS au conducteur', 2, 5),
        commit('3e8d6f1a9b7c4e2d0f5a8b6c1e9d7f3a2b4c6e8d', 'Écran de connexion par numéro de téléphone', 0, 26),
        commit('b2a4c6e8d0f1a3b5c7d9e1f3a5b7c9d1e3f5a7b9', 'Premier commit : README et structure du projet', 0, 50),
      ],
      demandesDeFusion: [
        { numero: 3, titre: 'Paiement mobile du trajet', etat: 'ouverte', auteur: { login: login(3), avatarUrl: null }, misAJourLe: ilYaHeures(2), url: `${base}/pull/3` },
        { numero: 2, titre: 'Carte des trajets proches', etat: 'fusionnee', auteur: { login: login(1), avatarUrl: null }, misAJourLe: ilYaHeures(20), url: `${base}/pull/2` },
      ],
    })
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
    const corps = (await request.json()) as components['schemas']['DepotIdee']
    const document = corps.cahierChargesId ? piecesDe([corps.cahierChargesId], undefined, c.id)[0] : null
    if (corps.cahierChargesId && !document) return erreur('piece_inconnue', 400)
    moi.idee = { titre: corps.titre, description: corps.description, cahierCharges: document ?? null, backlog: corps.backlog ?? [] }
    moi.aDeposeIdee = true
    return HttpResponse.json(moi)
  }),
  http.post('/api/cercles/:id/tirage', ({ params }) => {
    const c = trouver(params.id)
    if (!c) return erreur('introuvable', 404)
    if (!c.moi.estMembre) return erreur('interdit', 403)
    if (c.tirage) return erreur('tirage_deja_fait')
    if (c.membres.length < c.taille) return erreur('cercle_incomplet')
    if (c.membres.some((m) => !m.aDeposeIdee)) return erreur('idees_manquantes')
    melanger(c.membres).forEach((m, i) => (m.rang = i + 1))
    c.membres.sort((a, b) => a.rang! - b.rang!)
    c.tours = toursPour(c.membres)
    c.tours[0] = ouvrirTour(c.tours[0]!, 0, 0, 0, c.membres.find((m) => m.personne.id === c.tours[0]!.beneficiaire.id)?.idee?.backlog)
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
      c.tours[i + 1] = ouvrirTour(suivant, 0, 0, 0, c.membres.find((m) => m.personne.id === suivant.beneficiaire.id)?.idee?.backlog)
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
    return pr ? HttpResponse.json({ elements: pr.taches, suivant: null }) : erreur('introuvable', 404)
  }),
  http.get('/api/projets/:projetId/jalons', ({ params }) => {
    const pr = projets.get(String(params.projetId))
    return pr ? HttpResponse.json(pr.jalons) : erreur('introuvable', 404)
  }),
  http.post('/api/projets/:projetId/jalons', async ({ params, request }) => {
    const pr = projets.get(String(params.projetId))
    if (!pr) return erreur('introuvable', 404)
    if (pr.porteurId !== AWA) return erreur('pas_chef_de_projet', 403)
    const { titre, echeance } = (await request.json()) as { titre?: string; echeance?: string }
    if (!titre || titre.trim().length < 3) return erreur('validation', 400)
    const jalon: JalonMock = { id: `${String(params.projetId).slice(0, -2)}${pr.jalons.length + 10}`, titre: titre.trim(), echeance: echeance ?? null, statut: 'prevu', ordre: pr.jalons.length }
    pr.jalons.push(jalon)
    return HttpResponse.json(jalon, { status: 201 })
  }),
  http.patch('/api/projets/:projetId/jalons/:jalonId', async ({ params, request }) => {
    const pr = projets.get(String(params.projetId))
    const j = pr?.jalons.find((x) => x.id === params.jalonId)
    if (!pr || !j) return erreur('introuvable', 404)
    if (pr.porteurId !== AWA) return erreur('pas_chef_de_projet', 403)
    Object.assign(j, (await request.json()) as Partial<JalonMock>)
    return HttpResponse.json(j)
  }),
  http.post('/api/projets/:projetId/taches', async ({ params, request }) => {
    const pr = projets.get(String(params.projetId))
    if (!pr) return erreur('introuvable', 404)
    const { titre, assigneeId, description, priorite, echeance } = (await request.json()) as { titre?: string; assigneeId?: string; description?: string | null; priorite?: TacheMock['priorite']; echeance?: string | null }
    if (!titre || titre.trim().length < 3) return erreur('invalide', 400)
    if (priorite && !['haute', 'normale', 'basse'].includes(priorite)) return erreur('validation', 400)
    const tache = nouvelleTache(titre.trim(), 'a_faire', new Date().toISOString())
    Object.assign(tache, { description: description?.trim() || null, priorite: priorite ?? 'normale', echeance: echeance ?? null })
    if (assigneeId) {
      const personne = PERSONNES.find((x) => x.personne.id === assigneeId)?.personne
      if (!personne) return erreur('pas_membre', 409)
      tache.assignee = personne
    }
    pr.taches.push(tache)
    return HttpResponse.json(tache, { status: 201 })
  }),
  http.patch('/api/projets/:projetId/taches/:tacheId', async ({ params, request }) => {
    const pr = projets.get(String(params.projetId))
    const i = pr?.taches.findIndex((x) => x.id === params.tacheId) ?? -1
    if (!pr || i < 0) return erreur('introuvable', 404)
    const corps = (await request.json()) as { version: number; statut?: TacheMock['statut']; assigneeId?: string | null; titre?: string; description?: string | null; priorite?: TacheMock['priorite']; echeance?: string | null }
    const avant = pr.taches[i]!
    if (corps.version !== avant.version) return erreur('modification_concurrente', 409)
    const assignee = corps.assigneeId === undefined ? avant.assignee : corps.assigneeId === null ? null : (PERSONNES.find((x) => x.personne.id === corps.assigneeId)?.personne ?? null)
    const statut = corps.statut ?? avant.statut
    const apres = {
      ...avant,
      assignee,
      statut,
      titre: corps.titre?.trim() || avant.titre,
      description: corps.description === undefined ? avant.description : corps.description?.trim() || null,
      priorite: corps.priorite ?? avant.priorite,
      echeance: corps.echeance === undefined ? avant.echeance : corps.echeance,
      version: avant.version + 1,
      livreeLe: statut === 'livree' ? new Date().toISOString() : null,
    }
    pr.taches[i] = apres
    return HttpResponse.json(apres)
  }),
]
