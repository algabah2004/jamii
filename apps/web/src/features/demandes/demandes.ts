import { lireJetonCsrf } from '../../api/client'

/*
 * Étape 3 de la démo : une PME décrit son besoin, l'assistant propose un plan.
 * Le contrat public `/api/demandes` n'existe pas encore (module `demandes`, #17) : ces types suivent la
 * proposition faite à @sowibrr, et `PlanProjet` reprend exactement contracts/ia.yaml.
 * Quand le contrat arrive, on remplace ce fichier par les types générés (npm run api:types).
 */

export type Priorite = 'indispensable' | 'utile' | 'plus_tard'
export type ServiceSystalink = 'datacloud' | 'orion' | 'yokk' | 'domaine'

export type PlanProjet = {
  cahierDesCharges: {
    resume: string
    objectifs: string[]
    fonctionnalites: { titre: string; description: string; priorite: Priorite }[]
    horsPerimetre: string[]
  }
  jalons: { titre: string; semaine: number; livrable: string }[]
  taches: { titre: string; description?: string; jalon: number; competences: string[] }[]
  rolesManquants: { role: string; competences: string[]; raison: string }[]
  servicesSuggeres: { service: ServiceSystalink; raison: string }[]
}

export type EtatPlan = {
  statut: 'en_attente' | 'en_cours' | 'terminee' | 'echouee'
  /** `repli` : l'IA était indisponible, le plan est un modèle à compléter (le dire à l'utilisateur). */
  origine?: 'ia' | 'cache' | 'repli'
  resultat?: PlanProjet
  erreurCode?: 'entree_refusee' | 'resultat_invalide' | 'delai_depasse'
}

export type Demande = {
  id: string
  titre: string
  description: string
  dureeSemaines: number | null
  creeLe: string
  plan: EtatPlan
}

export type NouvelleDemande = { titre: string; description: string; dureeSemaines?: number }

/** Les limites de EntreePlanProjet (contracts/ia.yaml), vérifiées aussi par le serveur. */
export const LIMITES = { titreMin: 3, titreMax: 120, descriptionMin: 20, descriptionMax: 4000 } as const
export const DUREES = [2, 4, 8, 12] as const

/** Relire la demande toutes les 2 s tant que le plan se prépare, au plus 1 minute. */
export const INTERVALLE_PLAN_MS = 2000
export const ATTENTE_PLAN_MAX_MS = 60_000

export function demandeValide(d: NouvelleDemande): boolean {
  const titre = d.titre.trim().length
  const description = d.description.trim().length
  return titre >= LIMITES.titreMin && titre <= LIMITES.titreMax && description >= LIMITES.descriptionMin && description <= LIMITES.descriptionMax
}

export const PRIORITES: Priorite[] = ['indispensable', 'utile', 'plus_tard']

/** Les fonctionnalités rangées par priorité, dans l'ordre d'importance ; les groupes vides sont retirés. */
export function fonctionnalitesParPriorite(plan: PlanProjet) {
  return PRIORITES.map((priorite) => ({
    priorite,
    liste: plan.cahierDesCharges.fonctionnalites.filter((f) => f.priorite === priorite),
  })).filter((g) => g.liste.length > 0)
}

/** Chaque jalon avec ses tâches, dans l'ordre des semaines. Une tâche au jalon inconnu est ignorée. */
export function jalonsAvecTaches(plan: PlanProjet) {
  return plan.jalons
    .map((jalon, position) => ({ ...jalon, taches: plan.taches.filter((t) => t.jalon === position) }))
    .sort((a, b) => a.semaine - b.semaine)
}

const adresse = (chemin: string) => new URL(chemin, window.location.origin)

async function envoyer(chemin: string, corps: unknown): Promise<Response> {
  const entetes: Record<string, string> = { 'Content-Type': 'application/json' }
  const jeton = lireJetonCsrf()
  if (jeton) entetes['X-XSRF-TOKEN'] = jeton
  return globalThis.fetch(adresse(chemin), { method: 'POST', credentials: 'same-origin', headers: entetes, body: JSON.stringify(corps) })
}

/** POST /api/demandes : 201 avec la demande, son plan en préparation. */
export async function creerDemande(d: NouvelleDemande): Promise<Demande | null> {
  const reponse = await envoyer('/api/demandes', {
    titre: d.titre.trim(),
    description: d.description.trim(),
    ...(d.dureeSemaines ? { dureeSemaines: d.dureeSemaines } : {}),
  })
  return reponse.ok ? ((await reponse.json()) as Demande) : null
}

/** GET /api/demandes/{id} : `null` si elle n'existe pas (404). Lève une erreur si l'API ne répond pas. */
export async function lireDemande(id: string): Promise<Demande | null> {
  const reponse = await globalThis.fetch(adresse(`/api/demandes/${encodeURIComponent(id)}`), { credentials: 'same-origin' })
  if (reponse.status === 404) return null
  if (!reponse.ok) throw new Error('demande indisponible')
  return (await reponse.json()) as Demande
}

/** POST /api/projets (contracts/projets.yaml) : créer le projet depuis la demande. Renvoie son id. */
export async function creerProjet(demande: Demande): Promise<{ id: string } | { erreur: string }> {
  const reponse = await envoyer('/api/projets', {
    titre: demande.titre,
    description: demande.description,
    origine: 'demande',
    demandeId: demande.id,
  })
  if (reponse.ok) return { id: ((await reponse.json()) as { id: string }).id }
  const corps = (await reponse.json().catch(() => ({}))) as { code?: string }
  return { erreur: corps.code ?? 'autre' }
}
