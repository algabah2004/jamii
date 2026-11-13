import { http, HttpResponse } from 'msw'
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

/** Délai de « réflexion » de l'IA simulée, pour voir l'état « L'IA prépare une réponse… ». */
export const DELAI_IA_MS = 4000

const ilYa = (heures: number) => new Date(Date.now() - heures * 3_600_000).toISOString()

type Etat = {
  communautes: Communaute[]
  publications: (Publication & { iaPreteLe?: number })[]
}

function reponse(r: Partial<Reponse> & Pick<Reponse, 'id' | 'contenu'>): Reponse {
  return { auteur: null, proposeeParIa: false, valideePar: null, acceptee: false, creeLe: ilYa(1), ...r }
}

function etatInitial(): Etat {
  const communaute = (slug: string, nom: string, description: string, theme: string, pays: string | null, nombreMembres: number, membre: boolean) =>
    ({ id: crypto.randomUUID(), slug, nom, description, theme, pays, nombreMembres, questionsOuvertes: 0, membre }) as Communaute
  return {
    communautes: [
      communaute('flutter', 'Flutter et Dart', 'Applications mobiles qui marchent aussi avec peu de réseau.', 'flutter', null, 214, true),
      communaute('fintech', 'Fintech et paiement mobile', 'Mobile Money, paiements marchands, conformité.', 'fintech', null, 168, false),
      communaute('ia', 'IA appliquée', 'Des modèles utiles, dans nos langues et sur nos données.', 'ia', null, 131, false),
      communaute('antenne-dakar', 'Antenne Dakar', 'Les devs de Dakar : rencontres, entraide et projets locaux.', 'antenne', 'SN', 57, false),
    ],
    publications: [
      {
        id: '7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d', communaute: 'flutter', type: 'question',
        titre: 'Comment garder les données hors ligne avec Riverpod ?',
        contenu: "Mon appli de livraison perd la liste des commandes dès que le réseau coupe. J'utilise Riverpod : comment garder les données et les synchroniser au retour du réseau ?",
        auteur: KOFFI, nombreReponses: 1, accepteeLe: null, creeLe: ilYa(2), projetId: null, reponseAccepteeId: null,
        propositionIa: { statut: 'prete' }, moi: { estAuteur: false, estMembre: true },
        reponses: [reponse({
          id: 'a1a1a1a1-b2b2-4c3c-8d4d-e5e5e5e5e5e5', proposeeParIa: true, creeLe: ilYa(2),
          contenu: "Garde une copie locale des commandes (Hive ou Isar), et fais lire cette copie à ton provider. Au retour du réseau, envoie les changements en attente puis recharge la liste. Ainsi l'écran ne dépend jamais du réseau.",
        })],
      },
      {
        id: '8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e', communaute: 'flutter', type: 'question',
        titre: 'Notifications push sans Firebase sur Android ?',
        contenu: "Pour une PME, je cherche à envoyer des notifications sans dépendre de Firebase. Quelles solutions avez-vous testées ?",
        auteur: AWA, nombreReponses: 1, accepteeLe: null, creeLe: ilYa(5), projetId: null, reponseAccepteeId: null,
        propositionIa: { statut: 'indisponible' }, moi: { estAuteur: true, estMembre: true },
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
        reponseAccepteeId: 'c3c3c3c3-d4d4-4e5e-8f6f-a7a7a7a7a7a7', propositionIa: { statut: 'indisponible' },
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
        propositionIa: null, moi: { estAuteur: false, estMembre: true }, reponses: [],
      },
    ],
  }
}

let etat = etatInitial()

/** Pour les tests : repart des données de démo. */
export function reinitialiserCommunautes() {
  etat = etatInitial()
}

function questionsOuvertes(slug: string) {
  return etat.publications.filter((p) => p.communaute === slug && p.type === 'question' && !p.reponseAccepteeId).length
}

function avecCompteurs(c: Communaute): Communaute {
  return { ...c, questionsOuvertes: questionsOuvertes(c.slug) }
}

/** L'IA simulée « répond » quand son délai est passé, comme la tâche de fond de l'API. */
function avancerIa(p: Etat['publications'][number]) {
  if (p.propositionIa?.statut === 'en_attente' && p.iaPreteLe !== undefined && Date.now() >= p.iaPreteLe) {
    p.propositionIa = { statut: 'prete' }
    p.reponses.push(reponse({
      id: crypto.randomUUID(), proposeeParIa: true, creeLe: new Date().toISOString(),
      contenu: `Piste proposée pour « ${p.titre} » : commence par reproduire le problème sur un petit exemple, puis vérifie la documentation officielle de l'outil concerné. Un pair de la communauté doit confirmer cette réponse avant qu'elle soit acceptée.`,
    }))
    p.nombreReponses = p.reponses.length
  }
}

function erreur(status: number, code: string) {
  return HttpResponse.json(
    { type: `urn:jamii:erreur:${code}`, title: code, status, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function sansInterne({ iaPreteLe: _interne, ...p }: Etat['publications'][number]): Publication {
  return p
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
      .map(({ id, communaute, type, titre, auteur, nombreReponses, accepteeLe, creeLe }) =>
        ({ id, communaute, type, titre, auteur, nombreReponses, accepteeLe, creeLe }))
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),

  http.post('/api/communautes/:slug/publications', async ({ params, request }) => {
    const c = etat.communautes.find((x) => x.slug === params.slug)
    if (!c) return erreur(404, 'introuvable')
    if (!c.membre) return erreur(403, 'pas_membre')
    const corps = (await request.json()) as components['schemas']['NouvellePublication']
    if (corps.titre.trim().length < 10 || corps.contenu.trim().length < 20) return erreur(400, 'validation')
    const question = corps.type === 'question'
    const p: Etat['publications'][number] = {
      id: crypto.randomUUID(), communaute: c.slug, type: corps.type, titre: corps.titre.trim(), contenu: corps.contenu.trim(),
      auteur: AWA, nombreReponses: 0, accepteeLe: null, creeLe: new Date().toISOString(), projetId: corps.projetId ?? null,
      reponseAccepteeId: null, propositionIa: question ? { statut: 'en_attente' } : null,
      moi: { estAuteur: true, estMembre: true }, reponses: [],
      iaPreteLe: question ? Date.now() + DELAI_IA_MS : undefined,
    }
    etat.publications.push(p)
    return HttpResponse.json(sansInterne(p), { status: 201, headers: { Location: `/api/publications/${p.id}` } })
  }),

  http.get('/api/publications/:id', ({ params }) => {
    const p = etat.publications.find((x) => x.id === params.id)
    if (!p) return erreur(404, 'introuvable')
    avancerIa(p)
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
    if (r.proposeeParIa) return erreur(409, 'proposee_par_ia')
    if (r.auteur?.id === p.auteur?.id) return erreur(409, 'propre_reponse')
    if (p.reponseAccepteeId) return erreur(409, 'deja_acceptee')
    Object.assign(r, { acceptee: true })
    Object.assign(p, { reponseAccepteeId: r.id, accepteeLe: new Date().toISOString() })
    return HttpResponse.json(sansInterne(p))
  }),

  http.post('/api/reponses/:id/valider', ({ params }) => {
    const p = etat.publications.find((x) => x.reponses.some((r) => r.id === params.id))
    const r = p?.reponses.find((x) => x.id === params.id)
    if (!p || !r) return erreur(404, 'introuvable')
    if (!p.moi.estMembre) return erreur(403, 'pas_membre')
    if (p.moi.estAuteur) return erreur(409, 'propre_question')
    if (!r.proposeeParIa) return erreur(409, 'pas_proposee_par_ia')
    if (p.reponseAccepteeId) return erreur(409, 'deja_acceptee')
    Object.assign(r, { acceptee: true, valideePar: AWA })
    Object.assign(p, { reponseAccepteeId: r.id, accepteeLe: new Date().toISOString() })
    return HttpResponse.json(sansInterne(p))
  }),

  http.post('/api/signalements', () => new HttpResponse(null, { status: 202 })),
]
