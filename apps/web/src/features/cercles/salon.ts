/*
 * Le salon du cercle (contracts/cercles.yaml v0.2) : types, appels à l'API et règles d'affichage, sans Vue.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/cercles'

export type SalonCercle = components['schemas']['SalonCercle']
export type MessageCercle = components['schemas']['MessageCercle']
export type AppelCercle = components['schemas']['AppelCercle']
export type PointDuJour = components['schemas']['PointDuJour']
export type ModeSaisie = 'texte' | 'point' | 'coup_de_main'

export const LIMITES_SALON = { texte: 2000, point: 500 } as const
/** Deux messages libres du même auteur à moins de 7 minutes : le second se range sous le premier. */
const ECART_GROUPE_MS = 7 * 60_000

export type Ligne =
  | { type: 'jour'; cle: string; date: Date }
  | { type: 'message'; cle: string; message: MessageCercle; suite: boolean }

const jourDe = (iso: string) => iso.slice(0, 10)

/** Les messages, avec un séparateur à chaque nouveau jour et les messages libres d'un même auteur groupés. */
export function lignesDuSalon(messages: MessageCercle[]): Ligne[] {
  const lignes: Ligne[] = []
  let precedent: MessageCercle | null = null
  for (const m of messages) {
    if (!precedent || jourDe(precedent.envoyeLe) !== jourDe(m.envoyeLe)) {
      lignes.push({ type: 'jour', cle: `jour-${jourDe(m.envoyeLe)}`, date: new Date(m.envoyeLe) })
      precedent = null
    }
    const suite =
      !!precedent &&
      m.type === 'texte' &&
      precedent.type === 'texte' &&
      !!m.auteur &&
      precedent.auteur?.id === m.auteur.id &&
      new Date(m.envoyeLe).getTime() - new Date(precedent.envoyeLe).getTime() < ECART_GROUPE_MS
    lignes.push({ type: 'message', cle: m.id, message: m, suite })
    precedent = m
  }
  return lignes
}

/** Le curseur de relecture : la plus grande version connue (un message modifié, supprimé ou réagi change de version). */
export const derniereVersion = (messages: MessageCercle[]) =>
  messages.reduce<number | undefined>((v, m) => (v === undefined || m.version > v ? m.version : v), undefined)

/** On corrige son message texte pendant 15 minutes (SalonCercle.DELAI_MODIFICATION). */
export const DELAI_MODIFICATION_MS = 15 * 60_000
export const estAMoi = (m: MessageCercle, moiId: string | null | undefined) => !!moiId && m.auteur?.id === moiId
export const modifiable = (m: MessageCercle, moiId: string | null | undefined, maintenant = Date.now()) =>
  estAMoi(m, moiId) && m.type === 'texte' && !m.supprime && maintenant - Date.parse(m.envoyeLe) < DELAI_MODIFICATION_MS
/** Les messages sur lesquels on agit : pas les étapes racontées par Jamii ni les annonces d'appel. */
export const actionnable = (m: MessageCercle) => !m.supprime && m.type !== 'evenement' && m.type !== 'appel'

/** Fusionne sans doublon (un message envoyé revient aussi par la relecture), dans l'ordre des numéros. */
export function fusionner(avant: MessageCercle[], nouveaux: MessageCercle[]): MessageCercle[] {
  const parId = new Map<string, MessageCercle>()
  for (const m of [...avant, ...nouveaux]) parId.set(m.id, m)
  return [...parId.values()].sort((a, b) => a.numero - b.numero)
}

/** Le prénom, pour parler aux gens comme dans un cercle d'amis. */
export const prenom = (nom: string | undefined | null) => (nom ?? '').split(' ')[0] ?? ''

// --- Appels à l'API ---

const code = (erreur: unknown) => (erreur as { code?: string } | undefined)?.code ?? null

export async function lireSalon(cercleId: string, apres?: number): Promise<{ ok: true; salon: SalonCercle } | { ok: false; statut: number }> {
  const { data, response } = await api.GET('/api/cercles/{cercleId}/salon', {
    params: { path: { cercleId }, query: apres === undefined ? { limite: 100 } : { apres, limite: 100 } },
  })
  return data ? { ok: true, salon: data } : { ok: false, statut: response.status }
}

export type Envoi = { ok: true; message: MessageCercle } | { ok: false; code: string | null; statut: number }

export async function envoyer(
  cercleId: string,
  corps: { type: 'texte' | 'coup_de_main' | 'decision'; texte: string; pieces?: string[]; reponseA?: string } | { type: 'point'; point: PointDuJour },
): Promise<Envoi> {
  const { data, error, response } = await api.POST('/api/cercles/{cercleId}/salon/messages', { params: { path: { cercleId } }, body: corps })
  return data ? { ok: true, message: data } : { ok: false, code: code(error), statut: response.status }
}

export async function modifierMessage(cercleId: string, messageId: string, texte: string): Promise<MessageCercle | null> {
  const { data } = await api.PATCH('/api/cercles/{cercleId}/salon/messages/{messageId}', { params: { path: { cercleId, messageId } }, body: { texte } })
  return data ?? null
}

export async function supprimerMessage(cercleId: string, messageId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/cercles/{cercleId}/salon/messages/{messageId}', { params: { path: { cercleId, messageId } } })
  return response.ok
}

export async function reagir(cercleId: string, messageId: string, emoji: string, active: boolean): Promise<MessageCercle | null> {
  const { data } = await api.POST('/api/cercles/{cercleId}/salon/messages/{messageId}/reactions', {
    params: { path: { cercleId, messageId } },
    body: { emoji, active },
  })
  return data ?? null
}

export async function resoudre(cercleId: string, messageId: string): Promise<MessageCercle | null> {
  const { data } = await api.POST('/api/cercles/{cercleId}/salon/messages/{messageId}/resolu', { params: { path: { cercleId, messageId } } })
  return data ?? null
}

export async function lancerAppel(cercleId: string): Promise<{ appel: AppelCercle | null; statut: number }> {
  const { data, response } = await api.POST('/api/cercles/{cercleId}/salon/appel', { params: { path: { cercleId } } })
  return { appel: data ?? null, statut: response.status }
}

export async function terminerAppel(cercleId: string): Promise<boolean> {
  const { response } = await api.DELETE('/api/cercles/{cercleId}/salon/appel', { params: { path: { cercleId } } })
  return response.ok
}
