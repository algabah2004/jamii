import { lireJetonCsrf } from '../../api/client'

import type { components } from '../../api/generated/demandes'

/*
 * Une personne ou une PME décrit son besoin, Jamii l'oriente (contracts/demandes.yaml v0.2, étape 1 de la démo) :
 * les services Systalink qui suffisent, sinon les développeurs qui peuvent le faire, et le plan.
 */
type Schemas = components['schemas']

export type PlanProjet = Schemas['PlanProjet']
export type Priorite = PlanProjet['cahierDesCharges']['fonctionnalites'][number]['priorite']
export type EtatPlan = Schemas['PlanDeLaDemande']
export type EtatOrientation = Schemas['OrientationDeLaDemande']
export type Orientation = Schemas['Orientation']
export type ServiceSystalink = Schemas['ServiceSystalink']
export type Recommandation = Schemas['Recommandation']
export type Demande = Schemas['Demande']

export type NouvelleDemande = {
  titre: string
  description: string
  dureeSemaines?: number
  secteur?: string
  ville?: string
  offresSystalink?: boolean
}

/** Des secteurs à choisir d'un clic (le contrat accepte aussi un texte libre). */
export const SECTEURS = ['commerce', 'juridique', 'sante', 'education', 'agriculture', 'association', 'autre'] as const

/**
 * L'état de l'analyse : celui de l'orientation, ou celui du plan pour une API plus ancienne.
 * `pret` : l'orientation est là (avec ou sans plan, selon qu'il faut des développeurs).
 */
export function etatAnalyse(d: Demande): 'attente' | 'pret' | 'echec' {
  const etat = d.orientation ?? d.plan
  if (etat.statut === 'en_attente' || etat.statut === 'en_cours') return 'attente'
  if (etat.statut === 'echouee') return 'echec'
  return d.orientation ? (d.orientation.resultat ? 'pret' : 'echec') : d.plan.resultat ? 'pret' : 'echec'
}

/** L'analyse a été faite sans IA : le dire simplement. */
export function estRepli(d: Demande): boolean {
  return (d.orientation ?? d.plan).origine === 'repli'
}

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

async function envoyer(chemin: string, corps: unknown, methode: 'POST' | 'PUT' = 'POST'): Promise<Response> {
  const entetes: Record<string, string> = { 'Content-Type': 'application/json' }
  const jeton = lireJetonCsrf()
  if (jeton) entetes['X-XSRF-TOKEN'] = jeton
  return globalThis.fetch(adresse(chemin), { method: methode, credentials: 'same-origin', headers: entetes, body: JSON.stringify(corps) })
}

/** POST /api/demandes : 201 avec la demande, son plan en préparation. */
export async function creerDemande(d: NouvelleDemande): Promise<Demande | null> {
  const secteur = d.secteur?.trim()
  const ville = d.ville?.trim()
  const reponse = await envoyer('/api/demandes', {
    titre: d.titre.trim(),
    description: d.description.trim(),
    ...(d.dureeSemaines ? { dureeSemaines: d.dureeSemaines } : {}),
    ...(secteur ? { secteur } : {}),
    ...(ville ? { ville } : {}),
    offresSystalink: d.offresSystalink === true,
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

/** PUT /api/demandes/{id}/offres-systalink : donner ou retirer son accord. Renvoie la demande à jour, `null` si refusé. */
export async function choisirOffres(id: string, accepte: boolean): Promise<Demande | null> {
  const reponse = await envoyer(`/api/demandes/${encodeURIComponent(id)}/offres-systalink`, { accepte }, 'PUT')
  return reponse.ok ? ((await reponse.json()) as Demande) : null
}
