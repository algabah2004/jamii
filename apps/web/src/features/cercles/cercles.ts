/*
 * Les cercles de la Tech Tontine (docs/priorites.md) : 4 à 6 développeurs construisent, à tour de rôle,
 * le projet de chacun. Un tour est un projet de l'équipe (contracts/projets.yaml).
 * Types et routes : contracts/cercles.yaml, générés dans api/generated/cercles.ts.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/cercles'
import type { components as ComposantsProjets } from '../../api/generated/projets'

export type Cercle = components['schemas']['Cercle']
export type CercleResume = components['schemas']['CercleResume']
export type MembreCercle = components['schemas']['MembreCercle']
export type Tour = components['schemas']['Tour']
export type Idee = components['schemas']['Idee']
export type DepotIdee = components['schemas']['DepotIdee']
export type Tache = ComposantsProjets['schemas']['Tache']
export type Jalon = ComposantsProjets['schemas']['Jalon']

export const TAILLES = [4, 5, 6] as const
export const LIMITES = { nomMin: 3, nomMax: 80, ideeTitreMin: 3, ideeTitreMax: 120, ideeDescriptionMin: 10, ideeDescriptionMax: 5000 } as const
/** Un tour dure deux semaines (docs/priorites.md). */
export const DUREE_TOUR_JOURS = 14
const JOUR = 86_400_000

/** Les membres dans l'ordre de passage ; ceux qui n'ont pas encore de rang restent à la fin, dans leur ordre d'arrivée. */
export function ordreDePassage(cercle: Pick<Cercle, 'membres'>): MembreCercle[] {
  return cercle.membres
    .map((m, arrivee) => ({ m, arrivee }))
    .sort((a, b) => (a.m.rang ?? Infinity) - (b.m.rang ?? Infinity) || a.arrivee - b.arrivee)
    .map(({ m }) => m)
}

export function tourEnCours(cercle: Pick<Cercle, 'tours' | 'tourEnCours'>): Tour | null {
  return cercle.tourEnCours ? (cercle.tours.find((t) => t.numero === cercle.tourEnCours) ?? null) : null
}

/** Le titre d'un tour : l'idée de son bénéficiaire (visible des membres), sinon rien. */
export function titreDuTour(cercle: Pick<Cercle, 'membres'>, tour: Pick<Tour, 'beneficiaire'>): string | null {
  return cercle.membres.find((m) => m.personne.id === tour.beneficiaire.id)?.idee?.titre ?? null
}

/** Ce qui manque encore avant le tirage : des membres, des idées, ou rien (le créateur peut tirer). */
export function avantTirage(cercle: Pick<Cercle, 'membres' | 'taille' | 'tirage'>): { manquants: number; sansIdee: number; pret: boolean } {
  const manquants = Math.max(0, cercle.taille - cercle.membres.length)
  const sansIdee = cercle.membres.filter((m) => !m.aDeposeIdee).length
  return { manquants, sansIdee, pret: !cercle.tirage && manquants === 0 && sansIdee === 0 }
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
const round = (n: number) => Math.round(n * 100) / 100

/** Où en est un tour : jour (à partir de 1), jours restants (jamais négatif) et tâches livrées. */
export interface Avancement {
  jour: number
  joursRestants: number
  faites: number
  total: number
}

export function avancement(debut: string, taches: Pick<Tache, 'statut'>[], maintenant = new Date()): Avancement {
  const ecoule = maintenant.getTime() - new Date(debut).getTime()
  const jour = Math.max(1, Math.floor(ecoule / JOUR) + 1)
  return {
    jour,
    joursRestants: Math.max(0, DUREE_TOUR_JOURS - jour),
    faites: taches.filter((t) => t.statut === 'livree').length,
    total: taches.length,
  }
}

export function pourcentage({ faites, total }: Pick<Avancement, 'faites' | 'total'>): number {
  return total > 0 ? Math.round((faites / total) * 100) : 0
}

// --- Appels à l'API (contracts/cercles.yaml et projets.yaml) ---

export type Resultat = { ok: true; cercle: Cercle } | { ok: false; code: string | null }
const code = (erreur: unknown) => (erreur as { code?: string } | undefined)?.code ?? null

async function resultat(appel: Promise<{ data?: unknown; error?: unknown }>): Promise<Resultat> {
  const { data, error } = await appel
  return data ? { ok: true, cercle: data as Cercle } : { ok: false, code: code(error) }
}

export async function lireCercles(portee: 'mes' | 'ouverts'): Promise<CercleResume[]> {
  const elements: CercleResume[] = []
  const curseurs = new Set<string>()
  let curseur: string | undefined
  do {
    const { data, error } = await api.GET('/api/cercles', { params: { query: { portee, limite: 100, ...(curseur ? { curseur } : {}) } } })
    if (error || !data) throw new Error('cercles indisponibles')
    elements.push(...(data.elements ?? []))
    curseur = data.curseurSuivant ?? undefined
    if (curseur && curseurs.has(curseur)) throw new Error('pagination des cercles invalide')
    if (curseur) curseurs.add(curseur)
  } while (curseur)
  return elements
}

export async function lireCercle(id: string): Promise<Cercle | null> {
  const { data, response } = await api.GET('/api/cercles/{cercleId}', { params: { path: { cercleId: id } } })
  if (response.status === 404 || response.status === 400) return null
  if (!data) throw new Error('cercle indisponible')
  return data
}

/** L'avancement du tour en cours, lu dans son projet. `null` si on n'y a pas accès (pas membre) ou si l'API ne répond pas. */
export async function lireAvancement(projetId: string): Promise<Avancement | null> {
  try {
    const [projet, taches] = await Promise.all([
      api.GET('/api/projets/{projetId}', { params: { path: { projetId } } }),
      api.GET('/api/projets/{projetId}/taches', { params: { path: { projetId }, query: { limite: 100 } } }),
    ])
    if (!projet.data || !taches.data) return null
    return avancement(projet.data.creeLe, taches.data.elements ?? [])
  } catch {
    return null
  }
}

/** Les tâches du projet d'un tour (100 au plus, de quoi tenir deux semaines). `null` si on n'y a pas accès. */
export async function lireTaches(projetId: string): Promise<Tache[] | null> {
  try {
    const { data } = await api.GET('/api/projets/{projetId}/taches', { params: { path: { projetId }, query: { limite: 100 } } })
    return data?.elements ?? null
  } catch {
    return null
  }
}

export type ResultatTache = { ok: true; tache: Tache } | { ok: false; code: string | null }

/** Les détails d'une tâche qu'on écrit (création ou modification). */
export type DetailsTache = { description?: string | null; priorite?: Tache['priorite']; echeance?: string | null; assigneeId?: string | null }

export async function ajouterTache(projetId: string, titre: string, details: DetailsTache | string | null = {}): Promise<ResultatTache> {
  // Ancienne forme : le 3e argument était le responsable.
  const d: DetailsTache = typeof details === 'string' ? { assigneeId: details } : (details ?? {})
  const corps = Object.fromEntries(Object.entries({ titre, ...d }).filter(([, v]) => v !== undefined && v !== null && v !== ''))
  try {
    const { data, error } = await api.POST('/api/projets/{projetId}/taches', { params: { path: { projetId } }, body: corps as { titre: string } })
    return data ? { ok: true, tache: data } : { ok: false, code: code(error) }
  } catch {
    return { ok: false, code: null }
  }
}

/** `version` vient de la tâche lue : si quelqu'un l'a changée entre-temps, l'API répond `modification_concurrente`. */
export async function changerTache(projetId: string, tache: Tache, changement: DetailsTache & { titre?: string; statut?: Tache['statut'] }): Promise<ResultatTache> {
  try {
    const { data, error } = await api.PATCH('/api/projets/{projetId}/taches/{tacheId}', {
      params: { path: { projetId, tacheId: tache.id } },
      body: { ...changement, version: tache.version },
    })
    return data ? { ok: true, tache: data } : { ok: false, code: code(error) }
  } catch {
    return { ok: false, code: null }
  }
}

export type ProfilRecherche = components['schemas']['ProfilRecherche']
/** Les profils qu'un cercle peut rechercher, dans l'ordre où on les propose. */
export const PROFILS: readonly ProfilRecherche[] = ['front', 'back', 'mobile', 'design', 'data', 'ia', 'devops', 'securite', 'produit', 'test']
/** Au plus un profil par place libre, et 5 en tout. */
export const profilsMax = (taille: number) => Math.min(5, taille - 1)

export const creerCercle = (c: { nom: string; taille: number; profils?: ProfilRecherche[] }) =>
  resultat(api.POST('/api/cercles', { body: { nom: c.nom.trim(), taille: c.taille, ...(c.profils?.length ? { profils: c.profils } : {}) } }))
export const rejoindreCercle = (cercleId: string) => resultat(api.POST('/api/cercles/{cercleId}/membres', { params: { path: { cercleId } } }))
export async function deposerIdee(cercleId: string, idee: DepotIdee): Promise<Resultat> {
  const { data, error } = await api.PUT('/api/cercles/{cercleId}/membres/moi/idee', {
    params: { path: { cercleId } },
    body: { ...idee, titre: idee.titre.trim(), description: idee.description.trim() },
  })
  if (error || !data) return { ok: false, code: code(error) }
  // L'API renvoie le membre modifié ; la page a besoin du cercle entier, comme pour les autres actions.
  const cercle = await lireCercle(cercleId)
  return cercle ? { ok: true, cercle } : { ok: false, code: 'introuvable' }
}
export const tirerAuSort = (cercleId: string) => resultat(api.POST('/api/cercles/{cercleId}/tirage', { params: { path: { cercleId } } }))
export const livrerTour = (cercleId: string, numero: number) =>
  resultat(api.POST('/api/cercles/{cercleId}/tours/{numero}/livrer', { params: { path: { cercleId, numero } } }))

// --- Le calendrier du tour : les jalons du projet, tenus par le chef de projet (contracts/projets.yaml) ---

export async function lireJalons(projetId: string): Promise<Jalon[] | null> {
  try {
    const { data } = await api.GET('/api/projets/{projetId}/jalons', { params: { path: { projetId } } })
    return data ?? null
  } catch {
    return null
  }
}

export type ResultatJalon = { ok: true; jalon: Jalon } | { ok: false; code: string | null }

export async function ajouterJalon(projetId: string, titre: string, echeance: string): Promise<ResultatJalon> {
  try {
    const { data, error } = await api.POST('/api/projets/{projetId}/jalons', { params: { path: { projetId } }, body: { titre, echeance } })
    return data ? { ok: true, jalon: data } : { ok: false, code: code(error) }
  } catch {
    return { ok: false, code: null }
  }
}

export async function changerJalon(projetId: string, jalonId: string, changement: { statut?: Jalon['statut']; titre?: string; echeance?: string | null }): Promise<ResultatJalon> {
  try {
    const { data, error } = await api.PATCH('/api/projets/{projetId}/jalons/{jalonId}', { params: { path: { projetId, jalonId } }, body: changement })
    return data ? { ok: true, jalon: data } : { ok: false, code: code(error) }
  } catch {
    return { ok: false, code: null }
  }
}

/**
 * Le chef de projet : le membre dont c'est le tour. Il tient le calendrier et consigne les décisions. Avant le tirage
 * et après le dernier tour, c'est le créateur du cercle.
 */
export function chefDeProjet(cercle: Pick<Cercle, 'tours' | 'tourEnCours' | 'createur'>) {
  return cercle.tours.find((x) => x.numero === cercle.tourEnCours)?.beneficiaire ?? cercle.createur
}
