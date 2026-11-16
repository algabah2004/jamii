/* Suivre quelqu'un, comme sur LinkedIn (#157, contracts/reseau.yaml, tag `abonnes`). Les contacts restent réciproques. */
import { api } from '../../api/client'
import type { components } from '../../api/generated/reseau'

export type Suivi = components['schemas']['Suivi']
export type ListeAbonnes = components['schemas']['ListeAbonnes']

export async function lireSuivi(personneId: string): Promise<Suivi> {
  const { data } = await api.GET('/api/personnes/{personneId}/abonnement', { params: { path: { personneId } } })
  if (!data) throw new Error('abonnés indisponibles')
  return data
}

export async function changerSuivi(personneId: string, suivre: boolean): Promise<Suivi> {
  const options = { params: { path: { personneId } } }
  const { data } = suivre
    ? await api.PUT('/api/personnes/{personneId}/abonnement', options)
    : await api.DELETE('/api/personnes/{personneId}/abonnement', options)
  if (!data) throw new Error('abonnement impossible')
  return data
}

export async function listerAbonnes(personneId: string, sens: 'abonnes' | 'abonnements'): Promise<ListeAbonnes> {
  const options = { params: { path: { personneId } } }
  const { data } = sens === 'abonnes'
    ? await api.GET('/api/personnes/{personneId}/abonnes', options)
    : await api.GET('/api/personnes/{personneId}/abonnements', options)
  if (!data) throw new Error('abonnés indisponibles')
  return data
}

/** « 1 234 » ou « 12,5 k » au-delà de 10 000 : lisible sur une carte de profil. */
export function nombreCourt(n: number, locale: string): string {
  if (n < 10_000) return n.toLocaleString(locale)
  return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}
