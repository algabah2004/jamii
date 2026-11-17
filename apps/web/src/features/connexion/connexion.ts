import { api } from '../../api/client'

/*
 * La connexion par lien e-mail (contracts/socle.yaml, `/api/auth/lien-email`) : pour une PME, une association
 * ou toute personne sans compte GitHub. Aucun mot de passe : un lien valable 15 minutes, qui ne sert qu'une fois.
 */

/** Un chemin de retour sûr : relatif, un seul `/` au début (contrat : CheminRetour). Sinon, l'accueil. */
export function cheminSur(retour: unknown, parDefaut = '/'): string {
  if (typeof retour !== 'string' || retour.length > 512) return parDefaut
  return /^\/(?!\/)[^\s\\]*$/.test(retour) ? retour : parDefaut
}

export type ResultatDemande = { etat: 'envoye' } | { etat: 'invalide' } | { etat: 'trop'; minutes: number } | { etat: 'indisponible' }

/** Demande un lien. L'API répond 202 que l'adresse soit inscrite ou non : on ne révèle pas qui utilise Jamii. */
export async function demanderLien(email: string, retour: string): Promise<ResultatDemande> {
  try {
    const { response } = await api.POST('/api/auth/lien-email', { body: { email: email.trim(), retour: cheminSur(retour) } })
    if (response.status === 202) return { etat: 'envoye' }
    if (response.status === 400) return { etat: 'invalide' }
    if (response.status === 429) return { etat: 'trop', minutes: minutesAvant(response.headers.get('Retry-After')) }
    return { etat: 'indisponible' }
  } catch {
    return { etat: 'indisponible' }
  }
}

export type ResultatConfirmation = 'connecte' | 'lien_invalide' | 'indisponible'

/** Échange le jeton du lien contre une session (cookie posé par l'API). */
export async function confirmerLien(jeton: string): Promise<ResultatConfirmation> {
  if (jeton.length < 32 || jeton.length > 128) return 'lien_invalide'
  try {
    const { response } = await api.POST('/api/auth/lien-email/confirmation', { body: { jeton } })
    if (response.status === 204) return 'connecte'
    return response.status === 400 ? 'lien_invalide' : 'indisponible'
  } catch {
    return 'indisponible'
  }
}

/** `Retry-After` en secondes, arrondi à la minute supérieure (au moins 1). */
export function minutesAvant(entete: string | null): number {
  const secondes = Number(entete)
  return Number.isFinite(secondes) && secondes > 0 ? Math.ceil(secondes / 60) : 1
}

/** Les façons de se connecter que le serveur propose. Google n'est montré que s'il est configuré, le compte démo aussi. */
export async function lireFournisseurs(): Promise<{ google: boolean; demo: boolean }> {
  try {
    const { data } = await api.GET('/api/auth/fournisseurs')
    return { google: data?.google === true, demo: data?.demo === true }
  } catch {
    return { google: false, demo: false }
  }
}

/** Ouvre une session sur un compte démo à soi (#157), créé pour ce visiteur. Vrai si c'est fait. */
export async function entrerEnDemo(): Promise<boolean> {
  try {
    const { response } = await api.POST('/api/auth/demo')
    return response.status === 204
  } catch {
    return false
  }
}

/** Les codes posés par l'API sur `/connexion?erreur=…` quand la connexion GitHub ou Google échoue. */
const ERREURS_GITHUB = ['refus_github', 'requete_invalide', 'github_indisponible', 'refus_google', 'google_indisponible', 'email_non_verifie'] as const
export type ErreurGithub = (typeof ERREURS_GITHUB)[number]

export function erreurGithub(code: unknown): ErreurGithub | null {
  if (typeof code !== 'string' || !code) return null
  return (ERREURS_GITHUB as readonly string[]).includes(code) ? (code as ErreurGithub) : 'github_indisponible'
}
