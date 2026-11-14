/*
 * Les hackathons (contracts/hackathons.yaml, #170) : un organisateur crée et publie un hackathon, les développeurs le
 * voient. Types et routes générés dans api/generated/hackathons.ts. Les écrans de ce dossier ont un design PROVISOIRE :
 * ils montrent le parcours avec les composants actuels, et seront repris avec le design system.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/hackathons'

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
export type Filtre = (typeof FILTRES)[number]

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
    params: { query: { limite: 20, ...(filtre === 'tous' ? {} : { statut: filtre }), ...(curseur ? { curseur } : {}) } },
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

export const sInscrire = (id: string, chercheEquipe: boolean) =>
  resultat<Inscription>(api.POST('/api/hackathons/{hackathonId}/inscriptions', { ...chemin(id), body: { chercheEquipe } }))

export const creerEquipe = (id: string, nom: string) =>
  resultat<Equipe>(api.POST('/api/hackathons/{hackathonId}/equipes', { ...chemin(id), body: { nom } }))

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
