/*
 * Les missions freelance (contracts/missions.yaml, #176) : un client publie une mission, des développeurs la voient.
 * Types et routes générés dans api/generated/missions.ts. Les écrans de ce dossier ont un design PROVISOIRE : ils
 * montrent le parcours avec les composants actuels, et seront repris avec le design system.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/missions'

type Schemas = components['schemas']
export type Mission = Schemas['Mission']
export type MissionResume = Schemas['MissionResume']
export type NouvelleMission = Schemas['NouvelleMission']
export type Montant = Schemas['Montant']
export type StatutMission = Schemas['StatutMission']

export type Vue = 'publiques' | 'miennes'

export const LIMITES = { titreMin: 3, titreMax: 120, besoinMin: 20, besoinMax: 8000, competencesMax: 10, villeMax: 80 } as const

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

// ---------------------------------------------------------------- montants et compétences

/** Combien de chiffres après la virgule pour cette devise (0 pour le franc CFA, 2 pour l'euro), selon le navigateur. */
export function decimales(devise: string): number {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency: devise }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

/** « 4 500 FCFA » : un montant en unité mineure, écrit dans la langue de la personne. */
export function formaterMontant(montant: Montant, langue: string): string {
  const valeur = montant.centimes / 10 ** decimales(montant.devise)
  try {
    return new Intl.NumberFormat(langue, { style: 'currency', currency: montant.devise }).format(valeur)
  } catch {
    return `${valeur} ${montant.devise}`
  }
}

/** Ce que la personne a tapé (« 450 000 », « 1 250,50 ») en unité mineure ; `null` si ce n'est pas un montant. */
export function versMontant(texte: string, devise: string): Montant | null {
  const propre = texte.replace(/[\s  ]/g, '').replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(propre)) return null
  const centimes = Math.round(Number(propre) * 10 ** decimales(devise))
  return Number.isSafeInteger(centimes) ? { centimes, devise } : null
}

/** « Vue, ux design, Spring » → `vue`, `ux-design`, `spring` : sans doublon, et au plus dix. */
export function lireCompetences(texte: string): string[] {
  const vues = new Set<string>()
  for (const morceau of texte.split(/[,;\n]/)) {
    const c = morceau
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .trim()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9.+#-]/g, '')
    if (/^[a-z0-9]/.test(c) && c.length <= 50) vues.add(c)
  }
  return [...vues].slice(0, LIMITES.competencesMax)
}

// ---------------------------------------------------------------- l'API

export async function listerMissions(vue: Vue, curseur?: string): Promise<{ elements: MissionResume[]; curseurSuivant: string | null }> {
  const { data, error } = await api.GET('/api/missions', { params: { query: { vue, limite: 20, ...(curseur ? { curseur } : {}) } } })
  if (error || !data) throw new Error('missions indisponibles')
  // Le contrat étend la page commune : ses éléments sont des `MissionResume`, que le type généré ne précise pas.
  return { elements: (data.elements ?? []) as MissionResume[], curseurSuivant: data.curseurSuivant ?? null }
}

/** `null` si la mission n'existe pas, ou si on n'a pas le droit de la voir (404). */
export async function lireMission(id: string): Promise<Mission | null> {
  const { data, response } = await api.GET('/api/missions/{missionId}', { params: { path: { missionId: id } } })
  if (response.status === 404 || response.status === 400) return null
  if (!data) throw new Error('mission indisponible')
  return data
}

export const creerMission = (mission: NouvelleMission) => resultat<Mission>(api.POST('/api/missions', { body: mission }))

export const publierMission = (id: string, version: number) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/publier', { params: { path: { missionId: id } }, body: { version } }))

export const fermerMission = (id: string, version: number) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/fermer', { params: { path: { missionId: id } }, body: { version } }))

// ---------------------------------------------------------------- le suivi du travail (#178)

export type Travail = Schemas['Travail']
export type Jalon = Schemas['Jalon']
export type Livraison = Schemas['Livraison']

export const LIMITES_SUIVI = { informationsMax: 4000, messageMin: 10, messageMax: 4000, liensMax: 5, lienMax: 500, raisonMin: 10, raisonMax: 2000 } as const

/** Les liens d'une livraison, un par ligne : `null` s'il y en a trop, ou si l'un n'est pas une adresse `https`. */
export function lireLiens(texte: string): string[] | null {
  const liens = texte.split('\n').map((l) => l.trim()).filter(Boolean)
  if (liens.length > LIMITES_SUIVI.liensMax) return null
  for (const lien of liens) {
    if (lien.length > LIMITES_SUIVI.lienMax) return null
    try {
      if (new URL(lien).protocol !== 'https:') return null
    } catch {
      return null
    }
  }
  return liens
}

const chemin = (missionId: string) => ({ params: { path: { missionId } } })
const cheminJalon = (missionId: string, jalonId: string) => ({ params: { path: { missionId, jalonId } } })

export const demarrerMission = (id: string, version: number, informations: string) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/demarrer', { ...chemin(id), body: { version, informations } }))

export const livrerJalon = (id: string, jalonId: string, version: number, message: string, liens: string[]) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/jalons/{jalonId}/livrer', { ...cheminJalon(id, jalonId), body: { version, message, liens } }))

export const accepterJalon = (id: string, jalonId: string, version: number) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/jalons/{jalonId}/accepter', { ...cheminJalon(id, jalonId), body: { version } }))

export const demanderRetouche = (id: string, jalonId: string, version: number, raison: string) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/jalons/{jalonId}/demander-retouche', { ...cheminJalon(id, jalonId), body: { version, raison } }))

export const declarerPaiement = (id: string, jalonId: string, version: number, sens: 'paye' | 'recu') =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/jalons/{jalonId}/declarer-paiement', { ...cheminJalon(id, jalonId), body: { version, sens } }))

// ---------------------------------------------------------------- finir une mission (#179, #180)

export type Desaccord = Schemas['Desaccord']
export type Avis = Schemas['Avis']

export const LIMITES_FIN = { annulationMin: 10, annulationMax: 1000, desaccordMin: 20, desaccordMax: 4000, avisMax: 1000 } as const

/** `client` ou `developpeur` : la partie de la mission que je suis, `null` pour un modérateur ou un tiers. */
export function maPartie(mission: Mission): 'client' | 'developpeur' | null {
  return mission.moi.role === 'client' ? 'client' : mission.moi.role === 'developpeur_retenu' ? 'developpeur' : null
}

export const annulerMission = (id: string, version: number, raison: string) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/annuler', { ...chemin(id), body: { version, raison } }))

export const ouvrirDesaccord = (id: string, version: number, raison: string) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/desaccord', { ...chemin(id), body: { version, raison } }))

export const trancherDesaccord = (id: string, version: number, issue: 'terminee' | 'annulee', explication: string) =>
  resultat<Mission>(api.POST('/api/missions/{missionId}/desaccord/trancher', { ...chemin(id), body: { version, issue, explication } }))

/** L'avis donné ne renvoie pas la mission : on la relit pour mettre l'écran à jour. */
export async function donnerAvis(id: string, note: number, texte: string): Promise<Resultat<Mission>> {
  const r = await resultat<Avis>(api.POST('/api/missions/{missionId}/avis', { ...chemin(id), body: { note, texte: texte || null } }))
  if (!r.ok) return r
  const mission = await lireMission(id)
  return mission ? { ok: true, valeur: mission } : { ok: false, code: null, champs: {} }
}
