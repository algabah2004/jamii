/*
 * Les hackathons (contracts/hackathons.yaml, #170) : un organisateur crée et publie un hackathon, les développeurs le
 * voient. Types et routes générés dans api/generated/hackathons.ts. Les écrans de ce dossier ont un design PROVISOIRE :
 * ils montrent le parcours avec les composants actuels, et seront repris avec le design system.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/hackathons'
import type { Reponse } from '../questionnaires/questionnaire'

type Schemas = components['schemas']
export type Hackathon = Schemas['Hackathon']
export type HackathonResume = Schemas['HackathonResume']
export type NouveauHackathon = Schemas['NouveauHackathon']
export type StatutHackathon = Schemas['StatutHackathon']
export type Calendrier = Schemas['Calendrier']
export type Critere = Schemas['Critere']
export type Prix = Schemas['Prix']

export const LIMITES = {
  titreMin: 3,
  titreMax: 120,
  themeMin: 2,
  themeMax: 120,
  descriptionMin: 20,
  descriptionMax: 8000,
  codeMin: 20,
  codeMax: 4000,
  criteresMax: 8,
  prixMax: 10,
  questionsMax: 5,
  raisonMin: 10,
  raisonMax: 1000,
} as const

export const LIEUX = ['en_ligne', 'sur_place', 'hybride'] as const
export const MODES = ['ouvert', 'candidature'] as const
export const FILTRES = ['tous', 'publie', 'en_cours', 'evaluation', 'resultats'] as const
/** `miens` : ce que j'organise (brouillons compris), où je suis juré ou inscrit. Connexion requise. */
export type Filtre = (typeof FILTRES)[number] | 'miens'

/** Des devises du continent d'abord ; le contrat accepte toute devise ISO 4217 en majuscules. */
export const DEVISES = ['XOF', 'XAF', 'KES', 'NGN', 'GHS', 'MAD', 'ZAR', 'EUR', 'USD'] as const

/** Une erreur de l'API : son `code` métier, et le code de chaque champ en erreur (`validation`). */
export type Echec = { code: string | null; champs: Record<string, string> }
export type Resultat<T> = { ok: true; valeur: T } | ({ ok: false } & Echec)

function echec(erreur: unknown): Echec {
  const corps = (erreur ?? {}) as { code?: string; erreurs?: { champ: string; code: string }[] }
  const champs: Record<string, string> = {}
  for (const e of corps.erreurs ?? []) champs[e.champ] ??= e.code
  return { code: corps.code ?? null, champs }
}

async function resultat<T>(appel: Promise<{ data?: T; error?: unknown }>): Promise<Resultat<T>> {
  const { data, error } = await appel
  return data ? { ok: true, valeur: data } : { ok: false, ...echec(error) }
}

// ---------------------------------------------------------------- aides de saisie

/**
 * Une date saisie dans un champ « date et heure » (« 2026-12-12T09:00 », dans le fuseau de la personne) en instant
 * UTC pour l'API ; `null` si ce n'est pas une date.
 */
export function versInstant(saisie: string): string | null {
  if (!saisie) return null
  const date = new Date(saisie)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

/** Le calendrier saisi, ou `null` s'il manque une date ou si elles ne sont pas dans l'ordre du contrat. */
export function lireCalendrier(saisie: Record<'ouvertes' | 'fermees' | 'debut' | 'fin' | 'resultats', string>): Calendrier | null {
  const [ouvertes, fermees, debut, fin, resultats] = [saisie.ouvertes, saisie.fermees, saisie.debut, saisie.fin, saisie.resultats].map(versInstant)
  if (!ouvertes || !fermees || !debut || !fin || !resultats) return null
  const t = (iso: string) => new Date(iso).getTime()
  if (!(t(ouvertes) < t(fermees) && t(fermees) <= t(debut) && t(debut) < t(fin) && t(fin) < t(resultats))) return null
  return { inscriptionsOuvertesLe: ouvertes, inscriptionsFermeesLe: fermees, debutLe: debut, finRendusLe: fin, resultatsLe: resultats }
}

/** Une question par ligne, sans ligne vide ni doublon, cinq au plus. */
export function lireQuestions(texte: string): string[] {
  const vues = new Set<string>()
  for (const ligne of texte.split('\n')) {
    const q = ligne.trim()
    if (q.length >= 3) vues.add(q.slice(0, 200))
  }
  return [...vues].slice(0, LIMITES.questionsMax)
}

/** Le total des poids des critères : seul le rapport compte, mais la somme aide à se repérer. */
export const sommePoids = (criteres: readonly { poids: number }[]) => criteres.reduce((total, c) => total + (Number.isFinite(c.poids) ? c.poids : 0), 0)

/** Combien de chiffres après la virgule pour cette devise (0 pour le franc CFA, 2 pour l'euro), selon le navigateur. */
export function decimales(devise: string): number {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency: devise }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

/** Ce que la personne a tapé (« 1 000 000 ») en unité mineure ; `null` si ce n'est pas un montant. */
export function versMontant(texte: string, devise: string): { centimes: number; devise: string } | null {
  const propre = texte.replace(/[\s  ]/g, '').replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(propre)) return null
  const centimes = Math.round(Number(propre) * 10 ** decimales(devise))
  return Number.isSafeInteger(centimes) ? { centimes, devise } : null
}

/** « 1 000 000 F CFA » : un montant en unité mineure, écrit dans la langue de la personne. */
export function formaterMontant(montant: { centimes: number; devise: string }, langue: string): string {
  const valeur = montant.centimes / 10 ** decimales(montant.devise)
  try {
    return new Intl.NumberFormat(langue, { style: 'currency', currency: montant.devise }).format(valeur)
  } catch {
    return `${valeur} ${montant.devise}`
  }
}

/** Une date et une heure, dans la langue et le fuseau de la personne. */
export function formaterDate(iso: string, langue: string): string {
  return new Intl.DateTimeFormat(langue, { dateStyle: 'long', timeStyle: 'short' }).format(new Date(iso))
}

// ---------------------------------------------------------------- l'API

export async function listerHackathons(filtre: Filtre, curseur?: string): Promise<{ elements: HackathonResume[]; curseurSuivant: string | null }> {
  const { data, error } = await api.GET('/api/hackathons', {
    params: {
      query: {
        limite: 20,
        ...(filtre === 'miens' ? { vue: 'miens' as const } : filtre === 'tous' ? {} : { statut: filtre }),
        ...(curseur ? { curseur } : {}),
      },
    },
  })
  if (error || !data) throw new Error('hackathons indisponibles')
  // Le contrat étend la page commune : ses éléments sont des `HackathonResume`, que le type généré ne précise pas.
  return { elements: (data.elements ?? []) as HackathonResume[], curseurSuivant: data.curseurSuivant ?? null }
}

/** `null` si le hackathon n'existe pas, ou si on n'a pas le droit de le voir (404). */
export async function lireHackathon(id: string): Promise<Hackathon | null> {
  const { data, response } = await api.GET('/api/hackathons/{hackathonId}', { params: { path: { hackathonId: id } } })
  if (response.status === 404 || response.status === 400) return null
  if (!data) throw new Error('hackathon indisponible')
  return data
}

export const creerHackathon = (hackathon: NouveauHackathon) => resultat<Hackathon>(api.POST('/api/hackathons', { body: hackathon }))

export const publierHackathon = (id: string, version: number) =>
  resultat<Hackathon>(api.POST('/api/hackathons/{hackathonId}/publier', { params: { path: { hackathonId: id } }, body: { version } }))

export const annulerHackathon = (id: string, version: number, raison: string) =>
  resultat<Hackathon>(api.POST('/api/hackathons/{hackathonId}/annuler', { params: { path: { hackathonId: id } }, body: { version, raison } }))

// ---------------------------------------------------------------- inscriptions et équipes (#171)

export type Inscription = Schemas['Inscription']
export type Equipe = Schemas['Equipe']

export const LIMITES_EQUIPE = { nomMin: 2, nomMax: 60 } as const

const chemin = (hackathonId: string) => ({ params: { path: { hackathonId } } })

export const sInscrire = (id: string, chercheEquipe: boolean, reponses: Reponse[] = []) =>
  resultat<Inscription>(api.POST('/api/hackathons/{hackathonId}/inscriptions', { ...chemin(id), body: { chercheEquipe, ...(reponses.length ? { reponses } : {}) } }))

/** Les réponses ne servent que si je ne suis pas encore inscrit : l'inscription naît avec l'équipe. */
export const creerEquipe = (id: string, nom: string, reponses: Reponse[] = []) =>
  resultat<Equipe>(api.POST('/api/hackathons/{hackathonId}/equipes', { ...chemin(id), body: { nom, ...(reponses.length ? { reponses } : {}) } }))

export const inviterMembre = (id: string, equipeId: string, utilisateurId: string) =>
  resultat<Equipe>(
    api.POST('/api/hackathons/{hackathonId}/equipes/{equipeId}/membres', { params: { path: { hackathonId: id, equipeId } }, body: { utilisateurId } }),
  )

export async function listerEquipes(id: string): Promise<Equipe[]> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/equipes', { params: { path: { hackathonId: id }, query: { limite: 100 } } })
  if (error || !data) throw new Error('équipes indisponibles')
  // Le contrat étend la page commune : ses éléments sont des `Equipe`, que le type généré ne précise pas.
  return (data.elements ?? []) as Equipe[]
}

/** « Je cherche une équipe » : les participants acceptés, seuls et volontaires (réservé aux participants). */
export async function listerSansEquipe(id: string): Promise<Inscription[]> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/sans-equipe', { params: { path: { hackathonId: id }, query: { limite: 100 } } })
  if (error || !data) throw new Error('participants indisponibles')
  return (data.elements ?? []) as Inscription[]
}

// ---------------------------------------------------------------- le rendu d'une équipe (#172)

export type Rendu = Schemas['Rendu']
export type NouveauRendu = Schemas['NouveauRendu']

export const LIMITES_RENDU = { titreMin: 3, titreMax: 120, descriptionMin: 20, descriptionMax: 4000, lienMax: 500, reponseMax: 1000 } as const

/** Une adresse `https` valide (le contrat refuse le reste ; l'API refuse aussi les adresses internes). */
export function estHttps(texte: string): boolean {
  if (texte.length > LIMITES_RENDU.lienMax) return false
  try {
    return new URL(texte).protocol === 'https:'
  } catch {
    return false
  }
}

/** `null` si l'équipe n'a encore rien rendu (404). */
export async function lireRendu(id: string, equipeId: string): Promise<Rendu | null> {
  const { data, response } = await api.GET('/api/hackathons/{hackathonId}/equipes/{equipeId}/rendu', { params: { path: { hackathonId: id, equipeId } } })
  if (response.status === 404) return null
  if (!data) throw new Error('rendu indisponible')
  return data
}

export const rendre = (id: string, equipeId: string, rendu: NouveauRendu) =>
  resultat<Rendu>(api.PUT('/api/hackathons/{hackathonId}/equipes/{equipeId}/rendu', { params: { path: { hackathonId: id, equipeId } }, body: rendu }))

// ---------------------------------------------------------------- le jury et les notes (#173)

export type Jure = Schemas['Jure']
export type Notation = Schemas['Notation']
export type NotesDuJure = Schemas['NotesDuJure']

export const LIMITES_JURY = { groupeMax: 40, commentaireMax: 2000 } as const

// ---------------------------------------------------------------- la discussion

export type Annonce = Schemas['Annonce']
export type DiscussionHackathon = Schemas['Discussion']
export type QuestionOuverte = { id: string; titre: string; nombreReponses: number }

export async function lireDiscussion(id: string): Promise<DiscussionHackathon> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/discussion', { ...chemin(id) })
  if (error || !data) throw new Error('discussion indisponible')
  return data
}

export const annoncer = (id: string, texte: string) =>
  resultat<Annonce>(api.POST('/api/hackathons/{hackathonId}/annonces', { ...chemin(id), body: { texte } }))

export async function retirerAnnonce(id: string, annonceId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/hackathons/{hackathonId}/annonces/{annonceId}', {
    params: { path: { hackathonId: id, annonceId } },
  })
  return response.ok
}

/**
 * Ouvrir l'espace d'échange : une communauté (contracts/communautes.yaml) dont l'organisateur devient modérateur, avec
 * ses salons de départ, puis liée au hackathon. Si la création échoue (limite de 3 par jour…), rien n'est lié.
 */
export async function ouvrirEspace(h: Hackathon): Promise<Resultat<string>> {
  const cree = await api.POST('/api/communautes', {
    body: { nom: h.titre.slice(0, 80), description: `Questions et échanges du hackathon « ${h.titre} ».`.slice(0, 500), theme: 'autre' },
  })
  if (!cree.data) return { ok: false, ...echec(cree.error) }
  const slug = cree.data.slug
  const { response, error } = await api.PUT('/api/hackathons/{hackathonId}/communaute', { ...chemin(h.id), body: { communaute: slug } })
  return response.ok ? { ok: true, valeur: slug } : { ok: false, ...echec(error) }
}

/** Les questions encore sans réponse acceptée dans l'espace d'échange (les communautés demandent une connexion). */
export async function questionsOuvertes(slug: string): Promise<QuestionOuverte[]> {
  const { data } = await api.GET('/api/communautes/{slug}/publications', {
    params: { path: { slug }, query: { type: 'question', sansReponseAcceptee: true, limite: 3 } },
  })
  return (data?.elements ?? []).map((p) => ({ id: p.id, titre: p.titre, nombreReponses: p.nombreReponses }))
}

/** Ce qu'on montre de l'espace d'échange : la communauté liée et ses salons (les communautés demandent une connexion). */
export type Espace = { nom: string; nombreMembres: number; questionsOuvertes: number; membre: boolean; salons: { slug: string; nom: string }[] }

export async function lireEspace(slug: string): Promise<Espace | null> {
  const chemin = { params: { path: { slug } } }
  const [c, s] = await Promise.all([api.GET('/api/communautes/{slug}', chemin), api.GET('/api/communautes/{slug}/salons', chemin)])
  // `null` : la communauté liée n'existe pas (ou plus). Toute autre panne est une erreur, pour ne pas la confondre avec ça.
  if (c.response.status === 404) return null
  if (!c.data) throw new Error('espace indisponible')
  const { nom, nombreMembres, questionsOuvertes, membre } = c.data
  return { nom, nombreMembres, questionsOuvertes, membre, salons: (s.data?.elements ?? []).slice(0, 4).map(({ slug, nom }) => ({ slug, nom })) }
}

/** « à l'instant », « il y a 5 min », « il y a 3 h », puis la date : une annonce se lit d'abord par sa fraîcheur. */
export function depuis(iso: string, langue: string, maintenant = Date.now()): string {
  const secondes = Math.round((maintenant - Date.parse(iso)) / 1000)
  const relatif = new Intl.RelativeTimeFormat(langue, { numeric: 'auto' })
  if (secondes < 60) return relatif.format(0, 'second')
  if (secondes < 3600) return relatif.format(-Math.floor(secondes / 60), 'minute')
  if (secondes < 86400) return relatif.format(-Math.floor(secondes / 3600), 'hour')
  return new Intl.DateTimeFormat(langue, { day: 'numeric', month: 'long' }).format(new Date(iso))
}

/** L'identifiant d'un compte, tiré du lien de son profil Jamii (`…/p/<uuid>`, la page publique de son profil) ou donné tel quel ; `null` sinon. */
export function lireIdentifiant(texte: string): string | null {
  const trouve = texte.trim().match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/?$/i)
  return trouve ? trouve[1]!.toLowerCase() : null
}

export async function listerJures(id: string): Promise<Jure[]> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/jures', { ...chemin(id) })
  if (error || !data) throw new Error('jury indisponible')
  return data
}

export const inviterJure = (id: string, utilisateurId: string, groupe: string | null) =>
  resultat<Jure>(api.POST('/api/hackathons/{hackathonId}/jures', { ...chemin(id), body: { utilisateurId, groupe } }))

/** Les rendus à noter (juré) ou tous les rendus (organisateur). */
export async function listerRendus(id: string): Promise<Rendu[]> {
  const rendus: Rendu[] = []
  const lus = new Set<string>()
  let curseur: string | undefined
  do {
    const { data, error } = await api.GET('/api/hackathons/{hackathonId}/rendus', { params: { path: { hackathonId: id }, query: { limite: 100, curseur } } })
    if (error || !data) throw new Error('rendus indisponibles')
    rendus.push(...((data.elements ?? []) as Rendu[]))
    curseur = data.curseurSuivant ?? undefined
    if (curseur && lus.has(curseur)) throw new Error('curseur de rendus répété')
    if (curseur) lus.add(curseur)
  } while (curseur)
  return rendus
}

export const noter = (id: string, renduId: string, notes: { critereId: string; valeur: number }[], commentaire: string | null) =>
  resultat<NotesDuJure>(
    api.PUT('/api/hackathons/{hackathonId}/rendus/{renduId}/notes', { params: { path: { hackathonId: id, renduId } }, body: { notes, commentaire } }),
  )

/** Les notes que j'ai déjà données (juré) : pour les retrouver après avoir quitté la page. */
export async function lireMesNotes(id: string): Promise<NotesDuJure[]> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/mes-notes', { ...chemin(id) })
  if (error || !data) throw new Error('notes indisponibles')
  return data
}

export async function suivreNotation(id: string): Promise<Notation> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/notation', { ...chemin(id) })
  if (error || !data) throw new Error('notation indisponible')
  return data
}

/** Le lien de l'export CSV des notes (organisateur) : le navigateur le télécharge avec la session en cours. */
export const lienExportNotes = (id: string) => `/api/hackathons/${encodeURIComponent(id)}/notes.csv`

// ---------------------------------------------------------------- résultats (H7)

export type Resultats = Schemas['Resultats']
export type Gagnant = Schemas['ConfirmationResultats']['gagnants'][number]

export const LIMITES_RESULTATS = { raisonMax: 1000 } as const

/** Les résultats publiés ; `null` tant qu'ils ne le sont pas (404). */
export async function lireResultats(id: string): Promise<Resultats | null> {
  const { data, error, response } = await api.GET('/api/hackathons/{hackathonId}/resultats', { ...chemin(id) })
  if (response.status === 404) return null
  if (error || !data) throw new Error('résultats indisponibles')
  return data
}

/** Confirmer les gagnants : le hackathon passe en `resultats` et les notes sont verrouillées. */
export const confirmerResultats = (id: string, version: number, gagnants: Gagnant[]) =>
  resultat<Resultats>(api.POST('/api/hackathons/{hackathonId}/resultats', { ...chemin(id), body: { version, gagnants } }))

// ---------------------------------------------------------------- tri des candidatures (H3)

export type StatutInscription = Schemas['StatutInscription']
export const DECISIONS = ['acceptee', 'attente', 'refusee'] as const

/** Toutes les inscriptions (organisateur), les plus anciennes d'abord : premier arrivé, premier lu. */
export async function listerInscriptions(id: string): Promise<Inscription[]> {
  const { data, error } = await api.GET('/api/hackathons/{hackathonId}/inscriptions', { params: { path: { hackathonId: id }, query: { limite: 100 } } })
  if (error || !data) throw new Error('inscriptions indisponibles')
  return (data.elements ?? []) as Inscription[]
}

export const trierInscription = (id: string, inscriptionId: string, statut: (typeof DECISIONS)[number]) =>
  resultat<Inscription>(
    api.PATCH('/api/hackathons/{hackathonId}/inscriptions/{inscriptionId}', {
      params: { path: { hackathonId: id, inscriptionId } },
      body: { statut },
      headers: { 'Content-Type': 'application/merge-patch+json' },
    }),
  )
