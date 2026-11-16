/*
 * « En ligne » et « Vu il y a … » (#157, contracts/reseau.yaml, tag `presence`). L'API note l'activité de chacun à
 * chaque requête ; on relit la présence toutes les 30 secondes quand l'onglet est visible. Chacun peut cacher la sienne,
 * et c'est réciproque : qui se cache ne voit plus celle des autres.
 */
import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { api } from '../../api/client'
import type { components } from '../../api/generated/reseau'

export type EtatPresence = components['schemas']['EtatPresence']

export const RELECTURE_PRESENCE_MS = 30_000
export const PERSONNES_MAX = 50

export async function lirePresence(personnes: string[]): Promise<Map<string, EtatPresence>> {
  const ids = [...new Set(personnes)].slice(0, PERSONNES_MAX)
  if (!ids.length) return new Map()
  const { data } = await api.GET('/api/presence', { params: { query: { personnes: ids.join(',') } } })
  if (!data) throw new Error('présence indisponible')
  return new Map(data.elements.map((e) => [e.personneId, e]))
}

export async function lireMaVisibilite(): Promise<boolean> {
  const { data } = await api.GET('/api/me/presence')
  if (!data) throw new Error('présence indisponible')
  return data.visible
}

export async function changerMaVisibilite(visible: boolean): Promise<boolean> {
  const { data } = await api.PUT('/api/me/presence', { body: { visible } })
  if (!data) throw new Error('présence indisponible')
  return data.visible
}

type Traduire = (cle: string, valeurs?: Record<string, unknown>) => string

/**
 * Le texte sous le nom : « En ligne », « Vu à l'instant », « Vu il y a 5 min », « Vu aujourd'hui à 14:02 »,
 * « Vu hier à 09:15 », « Vu le 12 nov. ». Rien si on ne sait pas (présence cachée, jamais vu, plus d'une semaine).
 */
export function textePresence(etat: EtatPresence | undefined, t: Traduire, locale: string, maintenant = new Date()): string {
  if (!etat) return ''
  if (etat.enLigne) return t('presence.enLigne')
  if (!etat.vuLe) return ''
  const vu = new Date(etat.vuLe)
  const minutes = Math.floor((maintenant.getTime() - vu.getTime()) / 60_000)
  if (minutes < 1) return t('presence.instant')
  if (minutes < 60) return t('presence.minutes', { n: minutes })
  const heure = vu.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
  const jour = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const ecart = Math.round((jour(maintenant) - jour(vu)) / 86_400_000)
  if (ecart === 0) return t('presence.aujourdhui', { heure })
  if (ecart === 1) return t('presence.hier', { heure })
  return t('presence.le', { date: vu.toLocaleDateString(locale, { day: 'numeric', month: 'short' }) })
}

/** La présence de ces personnes, relue toutes les 30 secondes (onglet visible) et quand la liste change. */
export function usePresence(personnes: () => string[]): Ref<Map<string, EtatPresence>> {
  const etats = ref(new Map<string, EtatPresence>())
  let actif = true
  async function relire() {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
    try {
      const lus = await lirePresence(personnes())
      if (actif) etats.value = lus
    } catch { /* La présence n'est qu'un indice : on garde la dernière lecture pendant une panne. */ }
  }
  watch(() => personnes().join(','), () => void relire(), { immediate: true })
  const minuterie = setInterval(() => void relire(), RELECTURE_PRESENCE_MS)
  onBeforeUnmount(() => { actif = false; clearInterval(minuterie) })
  return etats
}
