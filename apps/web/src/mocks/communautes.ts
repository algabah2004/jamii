import { http, HttpResponse } from 'msw'
import { piecesDe } from './fichiers'
import type { components } from '../api/generated/communautes'

type Communaute = components['schemas']['Communaute']
type Publication = components['schemas']['Publication']
type Reponse = components['schemas']['Reponse']
type Personne = { id: string; nom: string; avatarUrl: null }

// Données de démo : plusieurs pays, jamais de vraies personnes (docs/plan-final.md).
// L'utilisateur connecté est Awa Diop (moiDemo dans handlers.ts).
const AWA: Personne = { id: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11', nom: 'Awa Diop', avatarUrl: null }
const KOFFI: Personne = { id: '0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b', nom: 'Koffi Yao', avatarUrl: null }
const WANJIRU: Personne = { id: '4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c', nom: 'Wanjiru Kamau', avatarUrl: null }
const CHIDI: Personne = { id: '5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d', nom: 'Chidi Okafor', avatarUrl: null }
const AYA: Personne = { id: '3e4f5a6b-7c8d-4e9f-8a0b-1c2d3e4f5a6b', nom: 'Aya Kouassi', avatarUrl: null }
const FATOU: Personne = { id: '2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a', nom: 'Fatou Ndiaye', avatarUrl: null }

const ilYa = (heures: number) => new Date(Date.now() - heures * 3_600_000).toISOString()

type Etat = {
  communautes: Communaute[]
  publications: (Omit<Publication, 'piecesJointes'> & { piecesJointes?: Publication['piecesJointes'] })[]
}

function reponse(r: Partial<Reponse> & Pick<Reponse, 'id' | 'contenu'>): Reponse {
  return { auteur: null, acceptee: false, creeLe: ilYa(1), ...r }
}

function etatInitial(): Etat {
  const communaute = (slug: string, nom: string, description: string, theme: string, pays: string | null, nombreMembres: number, membre: boolean) =>
    ({ id: crypto.randomUUID(), slug, nom, description, theme, pays, nombreMembres, questionsOuvertes: 0, membre, moderateur: slug === 'flutter', couverture: null }) as Communaute
  return {
    communautes: [
      communaute('flutter', 'Flutter et Dart', 'Applications mobiles qui marchent aussi avec peu de réseau.', 'flutter', null, 214, true),
      communaute('fintech', 'Fintech et paiement mobile', 'Mobile Money, paiements marchands, conformité.', 'fintech', null, 168, false),
      communaute('ia', 'IA appliquée', 'Des modèles utiles, dans nos langues et sur nos données.', 'ia', null, 131, true),
      communaute('antenne-dakar', 'Antenne Dakar', 'Les devs de Dakar : rencontres, entraide et projets locaux.', 'antenne', 'SN', 57, true),
    ],
    publications: [
      {
        id: '7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d', communaute: 'flutter', type: 'question',
        titre: 'Comment garder les données hors ligne avec Riverpod ?',
        contenu: "Mon appli de livraison perd la liste des commandes dès que le réseau coupe. J'utilise Riverpod : comment garder les données et les synchroniser au retour du réseau ?",
        auteur: KOFFI, nombreReponses: 1, accepteeLe: null, creeLe: ilYa(2), projetId: null, reponseAccepteeId: null,
        moi: { estAuteur: false, estMembre: true },
        reponses: [reponse({
          id: 'a1a1a1a1-b2b2-4c3c-8d4d-e5e5e5e5e5e5', auteur: WANJIRU, creeLe: ilYa(2),
          contenu: "Garde une copie locale des commandes (Hive ou Isar), et fais lire cette copie à ton provider. Au retour du réseau, envoie les changements en attente puis recharge la liste. Ainsi l'écran ne dépend jamais du réseau.",
        })],
      },
      {
        id: '8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e', communaute: 'flutter', type: 'question',
        titre: 'Notifications push sans Firebase sur Android ?',
        contenu: "Pour une PME, je cherche à envoyer des notifications sans dépendre de Firebase. Quelles solutions avez-vous testées ?",
        auteur: AWA, nombreReponses: 1, accepteeLe: null, creeLe: ilYa(5), projetId: null, reponseAccepteeId: null,
        moi: { estAuteur: true, estMembre: true },
        reponses: [reponse({
          id: 'b2b2b2b2-c3c3-4d4d-8e5e-f6f6f6f6f6f6', auteur: WANJIRU, creeLe: ilYa(4),
          contenu: "On utilise ntfy, auto-hébergé : l'appli s'abonne à un sujet, et le serveur envoie un simple POST. Ça tient très bien avec quelques milliers d'utilisateurs.",
        })],
      },
      {
        id: '9c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f', communaute: 'flutter', type: 'question',
        titre: 'Réduire la taille de l’APK pour les téléphones d’entrée de gamme',
        contenu: "Mon APK fait 48 Mo. Beaucoup d'utilisateurs ont peu de stockage. Par où commencer ?",
        auteur: CHIDI, nombreReponses: 1, accepteeLe: ilYa(20), creeLe: ilYa(26), projetId: null,
        reponseAccepteeId: 'c3c3c3c3-d4d4-4e5e-8f6f-a7a7a7a7a7a7',
        moi: { estAuteur: false, estMembre: true },
        reponses: [reponse({
          id: 'c3c3c3c3-d4d4-4e5e-8f6f-a7a7a7a7a7a7', auteur: AYA, acceptee: true, creeLe: ilYa(24),
          contenu: "Construis avec --split-per-abi : chaque téléphone ne télécharge que sa version. Puis vérifie les images et les polices embarquées, c'est souvent là que tout se joue.",
        })],
      },
      {
        id: '0d4e5f6a-7b8c-4d9e-9f0a-2b3c4d5e6f7a', communaute: 'flutter', type: 'discussion',
        titre: 'Qui vient à la démo du vendredi ?',
        contenu: 'On se retrouve en visio vendredi à 18 h GMT : 5 minutes par projet. Dites-moi si vous voulez montrer le vôtre.',
        auteur: FATOU, nombreReponses: 0, accepteeLe: null, creeLe: ilYa(30), projetId: null, reponseAccepteeId: null,
        moi: { estAuteur: false, estMembre: true }, reponses: [],
      },
    ],
  }
}

let etat = etatInitial()

// Les salons (#155) : les mêmes trois dans chaque communauté, et quelques messages pour la démo.
type MessageSalon = components['schemas']['MessageSalon']
const SALONS = [
  { slug: 'general', nom: 'général', sujet: 'Se présenter et parler de tout.' },
  { slug: 'entraide', nom: 'entraide', sujet: "Une question rapide, un blocage : quelqu'un t'aide." },
  { slug: 'projets', nom: 'projets', sujet: "Montrer ce qu'on construit et trouver des coéquipiers." },
]
let numeroSalon = 0
let versionSalon = 0
const message = (auteur: Personne, texte: string, minutes: number, pieces: string[] = []): MessageSalon => ({
  id: crypto.randomUUID(), numero: ++numeroSalon, auteur, texte, envoyeLe: new Date(Date.now() - minutes * 60_000).toISOString(), appel: null,
  piecesJointes: piecesDe(pieces), version: ++versionSalon, reponseA: null, modifieLe: null, supprime: null, reactions: [],
})
function messagesInitiaux(): Map<string, MessageSalon[]> {
  numeroSalon = 0
  versionSalon = 0
  return new Map([
    ['flutter/entraide', [
      message(WANJIRU, "Quelqu'un a déjà gardé des commandes hors ligne avec Flutter ? Mes clients à Nakuru perdent le réseau.", 42),
      message(KOFFI, 'Hive marche bien pour ça. Je synchronise quand le réseau revient, avec un horodatage par commande.', 38),
      message(CHIDI, "Pareil, et j'affiche « En attente d'envoi » sur chaque commande, les vendeurs sont rassurés.", 31),
    ]],
    ['flutter/general', [
      message(AYA, "Bonjour à tous, je suis Aya, d'Abidjan. Je cherche une équipe pour une appli de rendez-vous médicaux.", 26 * 60),
      message(KOFFI, 'Bienvenue Aya ! Passe dans #projets, il y a souvent des cercles qui cherchent du monde.', 25 * 60 + 50),
      message(AYA, 'Merci Koffi, je regarde ça ce soir.', 25 * 60 + 45),
      message(FATOU, "Petit rappel : l'atelier Flutter de l'antenne de Dakar a lieu samedi à 10 h (lieu dans #général).", 95),
      message(WANJIRU, "Je le suivrai à distance depuis Nairobi si quelqu'un lance l'appel du salon.", 80),
      message(FATOU, "Oui, je lancerai l'appel ici à 10 h : vous le verrez en haut du salon.", 78),
      message(CHIDI, 'Top. Quelqu’un a déjà essayé Flutter 4 en production ?', 12),
    ]],
    ['flutter/projets', [
      message(KOFFI, "Je montre mon carnet de livraison hors ligne, pour les boutiques d'Abidjan. Retours bienvenus !", 30 * 60),
      message(WANJIRU, "Très propre. La synchro à la reconnexion, tu la fais comment ?", 29 * 60 + 40),
      message(KOFFI, 'Une file de changements en attente, rejouée dans l’ordre quand le réseau revient.', 29 * 60 + 30),
      message(AWA, 'Une PME de Thiès cherche deux devs Flutter pour une appli de commandes. Qui est dispo ?', 50),
      message(CHIDI, 'Moi, à partir de lundi. Je suis à Lagos mais je travaille à distance.', 44),
    ]],
    ['ia/general', [
      message(WANJIRU, "Quelqu'un a testé un modèle de reconnaissance vocale en wolof ou en swahili ?", 3 * 60),
      message(FATOU, 'Pour le wolof, il y a des jeux de données ouverts, mais il faut encore beaucoup nettoyer.', 2 * 60 + 40),
      message(KOFFI, "Je prépare un petit benchmark, je le partage ici dès qu'il est prêt.", 35),
    ]],
    ['antenne-dakar/general', [
      message(FATOU, 'Rencontre de l’antenne jeudi 19 h, lieu annoncé la veille. On parle déploiement sur Systalink.', 4 * 60),
      message(AWA, 'Je viens, et j’amène deux personnes de mon cercle.', 3 * 60 + 30),
    ]],
  ])
}
let messagesSalons = messagesInitiaux()
/** Les salons créés pendant la visite (v0.4), en plus des trois de départ. */
let salonsCrees = new Map<string, { slug: string; nom: string; sujet: string }[]>()
/** L'appel en cours de chaque salon (v0.7), par « communauté/salon ». */
let appels = new Map<string, { id: string; lancePar: Personne; lanceLe: string; url: string }>()
/** L'état d'un message qui annonce un appel : en cours tant que c'est l'appel du salon. */
const messageDuSalon = (params: Record<string, unknown>) =>
  (messagesSalons.get(`${String(params.slug)}/${String(params.salon)}`) ?? []).find((m) => m.id === params.id)
const avecAppel = (cle: string, m: MessageSalon): MessageSalon => (m.appel ? { ...m, appel: { id: m.appel.id, enCours: appels.get(cle)?.id === m.appel.id } } : m)

/** Les salons supprimés pendant la visite (v0.5). */
let salonsSupprimes = new Set<string>()
const salonsDe = (slug: string) => [...SALONS, ...(salonsCrees.get(slug) ?? [])].filter((s) => !salonsSupprimes.has(`${slug}/${s.slug}`))
const nomCourt = (nom: string) => nom.replace(/^#\s*/, '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

/** Pour les tests : repart des données de démo. */
export function reinitialiserCommunautes() {
  etat = etatInitial()
  messagesSalons = messagesInitiaux()
  salonsCrees = new Map()
  salonsSupprimes = new Set()
  appels = new Map()
}

function questionsOuvertes(slug: string) {
  return etat.publications.filter((p) => p.communaute === slug && p.type === 'question' && !p.reponseAccepteeId).length
}

function avecCompteurs(c: Communaute): Communaute {
  return { ...c, questionsOuvertes: questionsOuvertes(c.slug) }
}

function erreur(status: number, code: string) {
  return HttpResponse.json(
    { type: `urn:jamii:erreur:${code}`, title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function sansInterne(p: Etat['publications'][number]): Publication {
  return { ...p, piecesJointes: p.piecesJointes ?? [] }
}

export const handlersCommunautes = [
  http.get('/api/communautes', ({ request }) => {
    const seulementMiennes = new URL(request.url).searchParams.get('membre') === 'moi'
    const elements = etat.communautes.filter((c) => !seulementMiennes || c.membre).map(avecCompteurs)
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),

  http.get('/api/communautes/:slug', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    return c ? HttpResponse.json(avecCompteurs(c)) : erreur(404, 'introuvable')
  }),

  http.post('/api/communautes/:slug/adhesion', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (!c.membre) Object.assign(c, { membre: true, nombreMembres: c.nombreMembres + 1 })
    return new HttpResponse(null, { status: 204 })
  }),

  http.delete('/api/communautes/:slug/adhesion', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (c.membre) Object.assign(c, { membre: false, nombreMembres: c.nombreMembres - 1 })
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/communautes/:slug/publications', ({ params, request }) => {
    const ouvertes = new URL(request.url).searchParams.get('sansReponseAcceptee') === 'true'
    const elements = etat.publications
      .filter((p) => p.communaute === params.slug && (!ouvertes || (p.type === 'question' && !p.reponseAccepteeId)))
      .sort((a, b) => b.creeLe.localeCompare(a.creeLe))
      .map((p) => {
        const { id, communaute, type, titre, auteur, nombreReponses, accepteeLe, creeLe, contenu } = p
        return { id, communaute, type, titre, auteur, nombreReponses, accepteeLe, creeLe, contenu }
      })
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),

  http.post('/api/communautes/:slug/publications', async ({ params, request }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (!c.membre) return erreur(403, 'pas_membre')
    const corps = (await request.json()) as components['schemas']['NouvellePublication']
    if (corps.titre.trim().length < 10 || corps.contenu.trim().length < (corps.pieces?.length ? 0 : 20)) return erreur(400, 'validation')
    const p: Etat['publications'][number] = {
      id: crypto.randomUUID(), communaute: c.slug, type: corps.type, titre: corps.titre.trim(), contenu: corps.contenu.trim(),
      auteur: AWA, nombreReponses: 0, accepteeLe: null, creeLe: new Date().toISOString(), projetId: corps.projetId ?? null,
      reponseAccepteeId: null,
      moi: { estAuteur: true, estMembre: true }, reponses: [], piecesJointes: piecesDe(corps.pieces),
    }
    etat.publications.push(p)
    return HttpResponse.json(sansInterne(p), { status: 201, headers: { Location: `/api/publications/${p.id}` } })
  }),

  http.get('/api/communautes/:slug/membres', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    // Les gens qui ont écrit dans les salons de la communauté, et moi si j'en fais partie.
    const vus = new Map<string, Personne>()
    if (c.membre) vus.set(AWA.id, AWA)
    for (const [cle, messages] of messagesSalons) if (cle.startsWith(`${c.slug}/`)) for (const m of messages) vus.set(m.auteur.id, m.auteur as Personne)
    const elements = [...vus.values()].map((personne, i) => ({
      personne, role: i === 1 ? 'moderateur' : 'membre', rejointLe: ilYa(24 * (30 - i)), actif: i < 4,
    })).sort((a, b) => Number(b.role === 'moderateur') - Number(a.role === 'moderateur'))
    return HttpResponse.json({ elements, total: c.nombreMembres })
  }),

  http.post('/api/communautes', async ({ request }) => {
    const corps = (await request.json()) as { nom?: string; description?: string; theme?: string; pays?: string | null; couvertureId?: string | null }
    const nom = corps.nom?.trim() ?? ''
    if (nom.length < 3 || (corps.description?.trim().length ?? 0) < 10 || !corps.theme) return erreur(400, 'validation')
    let slug = nomCourt(nom)
    for (let i = 2; etat.communautes.some((c) => c.slug === slug); i++) slug = `${nomCourt(nom)}-${i}`
    const c = {
      id: crypto.randomUUID(), slug, nom, description: corps.description!.trim(), theme: corps.theme, pays: corps.pays ?? null,
      nombreMembres: 1, questionsOuvertes: 0, membre: true, moderateur: true,
      couverture: corps.couvertureId ? `/api/fichiers/${corps.couvertureId}` : null,
    } as Communaute
    etat.communautes.unshift(c)
    return HttpResponse.json(c, { status: 201, headers: { Location: `/api/communautes/${slug}` } })
  }),

  http.put('/api/communautes/:slug/couverture', async ({ params, request }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (!c.moderateur) return erreur(403, 'pas_moderateur')
    const { fichierId } = (await request.json()) as { fichierId?: string | null }
    c.couverture = fichierId ? `/api/fichiers/${fichierId}` : null
    return HttpResponse.json(c)
  }),

  http.post('/api/communautes/:slug/salons', async ({ params, request }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (!c.moderateur) return erreur(403, 'pas_moderateur')
    const corps = (await request.json()) as { nom?: string; sujet?: string }
    const slug = nomCourt(corps.nom ?? '')
    if (slug.length < 2 || (corps.sujet?.trim().length ?? 0) < 5) return erreur(400, 'validation')
    if (salonsDe(c.slug).some((s) => s.slug === slug)) return erreur(409, 'salon_existe')
    const salon = { slug, nom: corps.nom!.replace(/^#\s*/, '').trim().toLowerCase(), sujet: corps.sujet!.trim() }
    salonsCrees.set(c.slug, [...(salonsCrees.get(c.slug) ?? []), salon])
    return HttpResponse.json({ id: crypto.randomUUID(), ...salon, appel: null }, { status: 201 })
  }),

  http.post('/api/communautes/:slug/salons/:salon/appel', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c || !salonsDe(c.slug).some((s) => s.slug === params.salon)) return erreur(404, 'introuvable')
    if (!c.membre) return erreur(403, 'pas_membre')
    const cle = `${c.slug}/${params.salon}`
    const enCours = appels.get(cle)
    if (enCours) return HttpResponse.json(enCours)
    const appel = { id: crypto.randomUUID(), lancePar: AWA, lanceLe: new Date().toISOString(), url: `https://meet.jit.si/jamii-${cle.replace('/', '-')}-${crypto.randomUUID().slice(0, 8)}` }
    appels.set(cle, appel)
    const annonce = { ...message(AWA, 'a lancé un appel', 0), appel: { id: appel.id, enCours: true } }
    messagesSalons.set(cle, [...(messagesSalons.get(cle) ?? []), annonce])
    return HttpResponse.json(appel, { status: 201 })
  }),

  http.delete('/api/communautes/:slug/salons/:salon/appel', ({ params }) => {
    const cle = `${params.slug}/${params.salon}`
    if (!appels.has(cle)) return erreur(404, 'introuvable')
    appels.delete(cle)
    return new HttpResponse(null, { status: 204 })
  }),

  http.delete('/api/communautes/:slug/salons/:salon', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c || !salonsDe(c.slug).some((s) => s.slug === params.salon)) return erreur(404, 'introuvable')
    if (!c.moderateur) return erreur(403, 'pas_moderateur')
    if (params.salon === 'general') return erreur(409, 'salon_protege')
    salonsSupprimes.add(`${c.slug}/${params.salon}`)
    messagesSalons.delete(`${c.slug}/${params.salon}`)
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/communautes/:slug/salons', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    const elements = salonsDe(c.slug).map((s, i) => ({
      id: `5a1005${String(i).padStart(2, '0')}-0000-4000-8000-${c.id.slice(-12)}`, ...s,
      appel: appels.has(`${c.slug}/${s.slug}`)
        ? { ...appels.get(`${c.slug}/${s.slug}`)!, url: c.membre ? appels.get(`${c.slug}/${s.slug}`)!.url : null }
        : null,
    }))
    return HttpResponse.json({ elements })
  }),

  http.get('/api/communautes/:slug/salons/:salon/messages', ({ params, request }) => {
    if (!etat.communautes.some((x) => x.slug === params.slug) || !salonsDe(String(params.slug)).some((s) => s.slug === params.salon)) return erreur(404, 'introuvable')
    const apres = Number(new URL(request.url).searchParams.get('apres') ?? -1)
    const cle = `${params.slug}/${params.salon}`
    const tous = (messagesSalons.get(cle) ?? []).map((m) => avecAppel(cle, m))
    return HttpResponse.json({ elements: apres >= 0 ? tous.filter((m) => m.version > apres) : tous.slice(-50) })
  }),

  http.post('/api/communautes/:slug/salons/:salon/messages', async ({ params, request }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c || !salonsDe(String(params.slug)).some((s) => s.slug === params.salon)) return erreur(404, 'introuvable')
    if (!c.membre) return erreur(403, 'pas_membre')
    const corps = (await request.json()) as { texte?: string; pieces?: string[]; reponseA?: string }
    const texte = corps.texte?.trim() ?? ''
    if ((!texte && !corps.pieces?.length) || texte.length > 2000) return erreur(400, 'validation')
    const cle = `${params.slug}/${params.salon}`
    const cite = (messagesSalons.get(cle) ?? []).find((x) => x.id === corps.reponseA)
    const m = { ...message(AWA, texte, 0, corps.pieces), reponseA: cite ? { id: cite.id, auteur: cite.auteur, texte: cite.supprime ? '' : cite.texte.slice(0, 140), supprime: !!cite.supprime } : null }
    messagesSalons.set(cle, [...(messagesSalons.get(cle) ?? []), m])
    return HttpResponse.json(m, { status: 201 })
  }),
  // Corriger, supprimer (l'auteur, ou un modérateur), réagir (#157).
  http.patch('/api/communautes/:slug/salons/:salon/messages/:id', async ({ params, request }) => {
    const m = messageDuSalon(params)
    if (!m) return erreur(404, 'introuvable')
    if (m.auteur.id !== AWA.id || m.supprime) return erreur(409, 'non_modifiable')
    const { texte } = (await request.json()) as { texte: string }
    if (!texte.trim()) return erreur(400, 'validation')
    Object.assign(m, { texte: texte.trim(), modifieLe: new Date().toISOString(), version: ++versionSalon })
    return HttpResponse.json(m)
  }),
  http.delete('/api/communautes/:slug/salons/:salon/messages/:id', ({ params }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    const m = messageDuSalon(params)
    if (!c || !m) return erreur(404, 'introuvable')
    if (m.auteur.id !== AWA.id && !c.moderateur) return erreur(403, 'interdit')
    if (!m.supprime) Object.assign(m, { texte: '', piecesJointes: [], reactions: [], supprime: m.auteur.id === AWA.id ? 'auteur' : 'moderation', version: ++versionSalon })
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/api/communautes/:slug/salons/:salon/messages/:id/reactions', async ({ params, request }) => {
    const m = messageDuSalon(params)
    if (!m) return erreur(404, 'introuvable')
    if (m.supprime) return erreur(409, 'message_supprime')
    const { emoji, active } = (await request.json()) as { emoji: string; active: boolean }
    const existante = m.reactions.find((r) => r.emoji === emoji)
    if (active && !existante?.deMoi) {
      m.reactions = existante
        ? m.reactions.map((r) => (r === existante ? { ...r, nombre: r.nombre + 1, deMoi: true, noms: [...r.noms, 'moi'] } : r))
        : [...m.reactions, { emoji, nombre: 1, deMoi: true, noms: ['moi'] }]
    } else if (!active && existante?.deMoi) {
      m.reactions = existante.nombre > 1
        ? m.reactions.map((r) => (r === existante ? { ...r, nombre: r.nombre - 1, deMoi: false, noms: r.noms.filter((n) => n !== 'moi') } : r))
        : m.reactions.filter((r) => r !== existante)
    }
    m.version = ++versionSalon
    return HttpResponse.json(m)
  }),

  http.get('/api/publications/:id', ({ params }) => {
    const p = etat.publications.find((x) => x.id === params.id)
    if (!p) return erreur(404, 'introuvable')
    return HttpResponse.json(sansInterne(p))
  }),

  http.post('/api/publications/:id/reponses', async ({ params, request }) => {
    const p = etat.publications.find((x) => x.id === params.id)
    if (!p) return erreur(404, 'introuvable')
    if (!p.moi.estMembre) return erreur(403, 'pas_membre')
    const { contenu } = (await request.json()) as { contenu: string }
    if (contenu.trim().length < 10) return erreur(400, 'validation')
    const r = reponse({ id: crypto.randomUUID(), auteur: AWA, contenu: contenu.trim(), creeLe: new Date().toISOString() })
    p.reponses.push(r)
    p.nombreReponses = p.reponses.length
    return HttpResponse.json(r, { status: 201 })
  }),

  http.post('/api/reponses/:id/accepter', ({ params }) => {
    const p = etat.publications.find((x) => x.reponses.some((r) => r.id === params.id))
    const r = p?.reponses.find((x) => x.id === params.id)
    if (!p || !r) return erreur(404, 'introuvable')
    if (!p.moi.estAuteur) return erreur(403, 'pas_auteur_question')
    if (r.auteur?.id === p.auteur?.id) return erreur(409, 'propre_reponse')
    if (p.reponseAccepteeId) return erreur(409, 'deja_acceptee')
    Object.assign(r, { acceptee: true })
    Object.assign(p, { reponseAccepteeId: r.id, accepteeLe: new Date().toISOString() })
    return HttpResponse.json(sansInterne(p))
  }),

  http.post('/api/signalements', () => new HttpResponse(null, { status: 202 })),
]
