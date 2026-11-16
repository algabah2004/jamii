/*
 * Le réseau (contracts/reseau.yaml) : les recommandations qui remplissent les cercles,
 * les messages (avec l'invitation à un cercle), les contacts et les notifications.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/reseau'

export type CercleRecommande = components['schemas']['CercleRecommande']
export type ProfilRecommande = components['schemas']['ProfilRecommande']
export type Raison = components['schemas']['RaisonRecommandation']
export type Message = components['schemas']['Message']

export const LONGUEUR_MESSAGE = 2000

export async function lireCerclesRecommandes(): Promise<CercleRecommande[]> {
  const { data } = await api.GET('/api/recommandations/cercles', { params: { query: { limite: 6 } } })
  if (!data) throw new Error('recommandations indisponibles')
  return data.elements
}

export async function lireProfilsRecommandes(cercleId: string): Promise<{ competencesManquantes: string[]; elements: ProfilRecommande[] }> {
  const { data } = await api.GET('/api/recommandations/profils', { params: { query: { cercleId, limite: 6 } } })
  if (!data) throw new Error('recommandations indisponibles')
  return data
}

export type Envoi = { ok: true; message: Message } | { ok: false; code: string | null }

/** Écrire à quelqu'un ; avec `cercleId`, le message porte l'invitation à ce cercle. */
export async function envoyerMessage(personneId: string, texte: string, cercleId?: string, pieces: string[] = [], reponseA?: string): Promise<Envoi> {
  const { data, error } = await api.POST('/api/conversations/{personneId}/messages', {
    params: { path: { personneId } },
    body: { texte: texte.trim(), ...(cercleId ? { cercleId } : {}), ...(pieces.length ? { pieces } : {}), ...(reponseA ? { reponseA } : {}) },
  })
  return data ? { ok: true, message: data } : { ok: false, code: (error as { code?: string } | undefined)?.code ?? null }
}

// ---------------------------------------------------------------- modifier, supprimer, transférer (#157)

/** On corrige un message pendant 15 minutes (Messagerie.DELAI_MODIFICATION) ; on le transfère à 5 personnes au plus. */
export const DELAI_MODIFICATION_MS = 15 * 60_000
export const TRANSFERT_MAX = 5

export const modifiable = (m: Message, maintenant = Date.now()) =>
  m.deMoi && !m.supprime && !m.appel && !m.invitation && maintenant - Date.parse(m.envoyeLe) < DELAI_MODIFICATION_MS
export const transferable = (m: Message) => !m.supprime && !m.appel && !m.invitation

export async function modifierMessage(personneId: string, messageId: string, texte: string): Promise<Envoi> {
  const { data, error } = await api.PATCH('/api/conversations/{personneId}/messages/{messageId}', {
    params: { path: { personneId, messageId } },
    body: { texte: texte.trim() },
  })
  return data ? { ok: true, message: data } : { ok: false, code: (error as { code?: string } | undefined)?.code ?? null }
}

export async function supprimerMessage(personneId: string, messageId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/conversations/{personneId}/messages/{messageId}', { params: { path: { personneId, messageId } } })
  return response.ok
}

export type Reaction = components['schemas']['Reaction']

/** Ajouter (`active`) ou retirer ma réaction ; les réactions à jour, ou `null` en cas de refus. */
export async function reagirMessage(personneId: string, messageId: string, emoji: string, active: boolean): Promise<Reaction[] | null> {
  const { data } = await api.POST('/api/conversations/{personneId}/messages/{messageId}/reactions', {
    params: { path: { personneId, messageId } },
    body: { emoji, active },
  })
  return data ?? null
}

export async function transfererMessage(personneId: string, messageId: string, destinataires: string[]): Promise<{ ok: boolean; code: string | null }> {
  const { response, error } = await api.POST('/api/conversations/{personneId}/messages/{messageId}/transfert', {
    params: { path: { personneId, messageId } },
    body: { destinataires },
  })
  return { ok: response.ok, code: (error as { code?: string } | undefined)?.code ?? null }
}

/** Lancer un appel vocal et vidéo avec une personne (#157) : le message qui l'annonce porte la salle. */
/** « Je suis en train d'écrire » (#157) : silencieux en cas d'échec, ce n'est qu'un indice. */
export async function signalerEcriture(personneId: string): Promise<void> {
  await api.POST('/api/conversations/{personneId}/ecriture', { params: { path: { personneId } } }).catch(() => undefined)
}

export async function lireEcriture(personneId: string): Promise<boolean> {
  try {
    const { data } = await api.GET('/api/conversations/{personneId}/ecriture', { params: { path: { personneId } } })
    return data?.ecrit === true
  } catch {
    return false
  }
}

/** Une personne recommandée pour un besoin, transmise jusqu'au cercle par l'état de la navigation. */
export type RecommandeDuBesoin = { id: string; nom: string }

/** Relit cette liste depuis `history.state` : rien n'y est garanti, on ne garde que des entrées bien formées. */
export function lireDuBesoin(valeur: unknown): RecommandeDuBesoin[] {
  if (!Array.isArray(valeur)) return []
  return valeur
    .filter((v): v is RecommandeDuBesoin => !!v && typeof v.id === 'string' && typeof v.nom === 'string' && v.id.length <= 64 && v.nom.length <= 120)
    .slice(0, 5)
    .map(({ id, nom }) => ({ id, nom }))
}

/** Le premier mot du nom : « Kwame » pour « Kwame Mensah ». */
export const prenom = (nom: string) => nom.split(' ')[0] ?? nom

// --- Messages ---

export type Conversation = components['schemas']['Conversation']
export type Personne = components['schemas']['Personne']

export async function lireConversations(): Promise<Conversation[]> {
  const { data } = await api.GET('/api/conversations', { params: { query: { limite: 50 } } })
  if (!data) throw new Error('messages indisponibles')
  return data.elements ?? []
}

/** La conversation avec une personne, dans l'ordre de lecture (le plus ancien en haut). `null` si la personne n'existe pas. */
export async function lireConversation(personneId: string): Promise<{ personne: Personne; messages: Message[] } | null> {
  const { data, response } = await api.GET('/api/conversations/{personneId}/messages', { params: { path: { personneId }, query: { limite: 100 } } })
  if (response.status === 404 || response.status === 400) return null
  if (!data) throw new Error('messages indisponibles')
  return { personne: data.personne, messages: [...(data.elements ?? [])].reverse() }
}

/** L'heure si c'est aujourd'hui, sinon le jour et le mois. */
export function quand(iso: string, locale: string, maintenant = new Date()): string {
  const date = new Date(iso)
  const memeJour = date.toDateString() === maintenant.toDateString()
  return new Intl.DateTimeFormat(locale, memeJour ? { hour: '2-digit', minute: '2-digit' } : { day: 'numeric', month: 'short' }).format(date)
}

// --- Contacts ---

export type Contact = components['schemas']['Contact']
export type StatutLien = components['schemas']['StatutLien']
export type Notification = components['schemas']['Notification']

export async function lireContacts(statut: 'accepte' | 'recue' | 'envoyee'): Promise<Contact[]> {
  const { data } = await api.GET('/api/me/contacts', { params: { query: { statut, limite: 100 } } })
  if (!data) throw new Error('contacts indisponibles')
  return data.elements ?? []
}

export async function lireLien(personneId: string): Promise<StatutLien> {
  const { data } = await api.GET('/api/contacts/{personneId}', { params: { path: { personneId } } })
  return data?.statut ?? 'aucun'
}

/** Demander le contact, ou accepter si la personne l'a déjà demandé. `null` si l'API refuse. */
export async function ajouterContact(personneId: string): Promise<StatutLien | null> {
  const { data } = await api.PUT('/api/contacts/{personneId}', { params: { path: { personneId } } })
  return data?.statut ?? null
}

export async function retirerContact(personneId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/contacts/{personneId}', { params: { path: { personneId } } })
  return response.ok
}

// --- Notifications ---

export async function lireNotifications(): Promise<Notification[]> {
  const { data } = await api.GET('/api/me/notifications', { params: { query: { limite: 50 } } })
  if (!data) throw new Error('notifications indisponibles')
  return data.elements ?? []
}

export async function toutMarquerLu(): Promise<void> {
  await api.POST('/api/me/notifications/lues')
}
