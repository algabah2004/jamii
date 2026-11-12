import { lireJetonCsrf } from '../../api/client'

/*
 * Les cercles de la Tech Tontine (docs/priorites.md) : 4 à 6 développeurs construisent, à tour de rôle,
 * le projet de chacun. Un tour est un projet de l'équipe (contracts/projets.yaml).
 *
 * Types écrits à la main en attendant contracts/cercles.yaml (demande #98) : à remplacer par les types
 * générés dès que le contrat est fusionné. Les écrans tournent sur données factices (mocks/cercles.ts).
 */

export type StatutCercle = 'ouvert' | 'en_cours' | 'termine'
export type StatutTour = 'a_venir' | 'en_cours' | 'livre'

export interface IdeeMembre {
  titre: string
  description: string
}

export interface MembreCercle {
  utilisateurId: string
  nom: string
  avatarUrl: string | null
  ville: string
  /** Code pays ISO 3166-1 alpha-2. */
  pays: string
  competences: string[]
  idee: IdeeMembre | null
  /** Place dans l'ordre de passage (1 = premier), `null` tant que le tirage n'est pas fait. */
  ordre: number | null
  /** Score de confiance de 0 à 100 : l'indice de fiabilité du profil (contracts/profils.yaml). */
  confiance: number
}

export interface DepotCode {
  fournisseur: 'gitea' | 'github'
  url: string
  cloneUrl: string
}

export interface Tour {
  numero: number
  beneficiaireId: string
  projetId: string | null
  titre: string
  statut: StatutTour
  debut: string
  fin: string
  avancement: { faites: number; total: number }
  depot: DepotCode | null
}

export interface MessageGriot {
  texte: string
  le: string
}

export interface Cercle {
  id: string
  createurId: string
  nom: string
  ville: string
  pays: string
  taille: number
  statut: StatutCercle
  tirageFait: boolean
  membres: MembreCercle[]
  tours: Tour[]
  messageGriot: MessageGriot | null
}

export type CercleResume = Pick<Cercle, 'id' | 'nom' | 'ville' | 'pays' | 'taille' | 'statut'> & {
  membres: Pick<MembreCercle, 'utilisateurId' | 'nom' | 'avatarUrl'>[]
  tourEnCours: { numero: number; beneficiaire: string } | null
}

/** Les membres dans l'ordre de passage ; ceux qui n'ont pas encore de place restent à la fin, dans leur ordre d'arrivée. */
export function ordreDePassage(cercle: Pick<Cercle, 'membres'>): MembreCercle[] {
  return cercle.membres
    .map((m, arrivee) => ({ m, arrivee }))
    .sort((a, b) => (a.m.ordre ?? Infinity) - (b.m.ordre ?? Infinity) || a.arrivee - b.arrivee)
    .map(({ m }) => m)
}

export function tourEnCours(cercle: Pick<Cercle, 'tours'>): Tour | null {
  return cercle.tours.find((t) => t.statut === 'en_cours') ?? null
}

/**
 * Où poser chaque membre sur l'anneau, en pourcentage du carré qui le contient.
 * Le premier est en haut, puis on tourne dans le sens des aiguilles d'une montre, comme le tour de parole.
 */
export function positionsAnneau(nombre: number, rayon = 40): { x: number; y: number }[] {
  return Array.from({ length: nombre }, (_, i) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / nombre
    return { x: round(50 + rayon * Math.cos(angle)), y: round(50 + rayon * Math.sin(angle)) }
  })
}

/** Jours pleins restants avant la fin du tour (0 le dernier jour, jamais négatif). */
export function joursRestants(tour: Pick<Tour, 'fin'>, maintenant = new Date()): number {
  const ms = new Date(tour.fin).getTime() - maintenant.getTime()
  return Math.max(0, Math.floor(ms / 86_400_000))
}

export function pourcentage({ faites, total }: Tour['avancement']): number {
  return total > 0 ? Math.round((faites / total) * 100) : 0
}

const round = (n: number) => Math.round(n * 100) / 100

const ENTETES = { Accept: 'application/json' }

export const TAILLES = [4, 5, 6] as const
export const LIMITES = { nomMin: 3, nomMax: 80, villeMin: 2, villeMax: 80, ideeTitreMin: 3, ideeTitreMax: 120, ideeDescriptionMin: 10, ideeDescriptionMax: 5000 } as const

export const estMembre = (cercle: Pick<Cercle, 'membres'>, moiId: string | undefined) => !!moiId && cercle.membres.some((m) => m.utilisateurId === moiId)

/** Ce qui manque encore avant le tirage : des membres, des idées, ou rien (le créateur peut tirer). */
export function avantTirage(cercle: Pick<Cercle, 'membres' | 'taille' | 'tirageFait'>): { manquants: number; sansIdee: number; pret: boolean } {
  const manquants = Math.max(0, cercle.taille - cercle.membres.length)
  const sansIdee = cercle.membres.filter((m) => !m.idee).length
  return { manquants, sansIdee, pret: !cercle.tirageFait && manquants === 0 && sansIdee === 0 }
}

export type Resultat = { ok: true; cercle: Cercle } | { ok: false; code: string | null }

async function envoyer(chemin: string, methode: 'POST' | 'PUT', corps?: unknown): Promise<Resultat> {
  const entetes: Record<string, string> = { ...ENTETES, 'Content-Type': 'application/json' }
  const jeton = lireJetonCsrf()
  if (jeton) entetes['X-XSRF-TOKEN'] = jeton
  const r = await fetch(chemin, { method: methode, credentials: 'same-origin', headers: entetes, body: corps === undefined ? undefined : JSON.stringify(corps) })
  if (r.ok) return { ok: true, cercle: (await r.json()) as Cercle }
  const erreur = (await r.json().catch(() => null)) as { code?: string } | null
  return { ok: false, code: erreur?.code ?? null }
}

export const creerCercle = (c: { nom: string; ville: string; taille: number }) => envoyer('/api/cercles', 'POST', { nom: c.nom.trim(), ville: c.ville.trim(), taille: c.taille })
export const rejoindreCercle = (id: string) => envoyer(`/api/cercles/${encodeURIComponent(id)}/membres`, 'POST')
export const deposerIdee = (id: string, idee: IdeeMembre) => envoyer(`/api/cercles/${encodeURIComponent(id)}/membres/moi/idee`, 'PUT', { titre: idee.titre.trim(), description: idee.description.trim() })
export const tirerAuSort = (id: string) => envoyer(`/api/cercles/${encodeURIComponent(id)}/tirage`, 'POST')

export async function lireCercles(): Promise<CercleResume[]> {
  const r = await fetch('/api/cercles', { headers: ENTETES, credentials: 'same-origin' })
  if (!r.ok) throw new Error(`cercles ${r.status}`)
  return ((await r.json()) as { elements: CercleResume[] }).elements
}

export async function lireCercle(id: string): Promise<Cercle | null> {
  const r = await fetch(`/api/cercles/${encodeURIComponent(id)}`, { headers: ENTETES, credentials: 'same-origin' })
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`cercle ${r.status}`)
  return (await r.json()) as Cercle
}
