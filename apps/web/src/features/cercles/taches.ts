/*
 * Les règles des tâches d'un tour (contracts/projets.yaml v0.3), sans Vue : priorités, ordre, et la description en deux
 * parties (ce qu'il faut faire, quand c'est fini).
 */
import type { Tache } from './cercles'

export type Priorite = Tache['priorite']
export const PRIORITES: Priorite[] = ['haute', 'normale', 'basse']
const RANG_PRIORITE: Record<Priorite, number> = { haute: 0, normale: 1, basse: 2 }

/**
 * Les tâches ouvertes dans l'ordre où on les attaque : la priorité d'abord, puis l'échéance la plus proche (sans
 * échéance à la fin), puis la plus ancienne.
 */
export function ordreDeTravail<T extends Pick<Tache, 'priorite' | 'echeance' | 'creeLe'>>(taches: T[]): T[] {
  return [...taches].sort(
    (a, b) =>
      RANG_PRIORITE[a.priorite ?? 'normale'] - RANG_PRIORITE[b.priorite ?? 'normale'] ||
      (a.echeance ?? '9999').localeCompare(b.echeance ?? '9999') ||
      a.creeLe.localeCompare(b.creeLe),
  )
}

/** Le séparateur entre les deux parties, tel qu'il est écrit (« Fini quand… », « Done when… »), deux-points facultatifs. */
const FINI = /\n+\s*(?:c['’]est\s+)?(?:fini quand|done when)\s*:?\s*/i

/** Sépare une description en « ce qu'il faut faire » et « c'est fini quand ». */
export function decouperDescription(description: string | null | undefined): { faire: string; fini: string } {
  if (!description) return { faire: '', fini: '' }
  const m = FINI.exec(description)
  if (!m) return { faire: description.trim(), fini: '' }
  return { faire: description.slice(0, m.index).trim(), fini: description.slice(m.index + m[0].length).trim() }
}

/** Recompose la description stockée. `libelle` : « Fini quand » dans la langue de qui l'écrit. */
export function composerDescription(faire: string, fini: string, libelle: string): string | null {
  const a = faire.trim()
  const b = fini.trim()
  if (!a && !b) return null
  if (!b) return a
  return `${a}\n\n${libelle} : ${b}`.trim()
}

/** L'échéance par rapport à aujourd'hui : passée, aujourd'hui, demain, ou plus tard. */
export function etatEcheance(echeance: string | null | undefined, maintenant = new Date()): 'depassee' | 'aujourdhui' | 'demain' | 'plus_tard' | null {
  if (!echeance) return null
  const jour = maintenant.toISOString().slice(0, 10)
  const demain = new Date(maintenant.getTime() + 86_400_000).toISOString().slice(0, 10)
  if (echeance < jour) return 'depassee'
  if (echeance === jour) return 'aujourdhui'
  if (echeance === demain) return 'demain'
  return 'plus_tard'
}
