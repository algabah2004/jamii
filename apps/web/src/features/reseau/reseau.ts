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
export async function envoyerMessage(personneId: string, texte: string, cercleId?: string): Promise<Envoi> {
  const { data, error } = await api.POST('/api/conversations/{personneId}/messages', {
    params: { path: { personneId } },
    body: { texte: texte.trim(), ...(cercleId ? { cercleId } : {}) },
  })
  return data ? { ok: true, message: data } : { ok: false, code: (error as { code?: string } | undefined)?.code ?? null }
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
