import type { components } from '../../api/generated/systalink'

export type TableauDeBord = components['schemas']['TableauDeBord']
export type Periode = components['schemas']['Periode']
export const PERIODES: Periode[] = ['semaine', 'mois', 'tout']

/** Part en pourcentage entier. `null` quand il n'y a rien à diviser : on affiche « — », jamais « 0 % » inventé. */
export function pourcentage(partie: number, total: number): number | null {
  if (total <= 0) return null
  return Math.round((Math.min(partie, total) / total) * 100)
}

/** Les moments les plus proposés d'abord : c'est l'ordre de lecture utile pour Systalink. */
export function momentsTries(tableau: TableauDeBord) {
  return [...tableau.moments]
    .sort((a, b) => b.proposes - a.proposes)
    .map((m) => ({ ...m, taux: pourcentage(m.acceptes, m.proposes) }))
}

/** Lit la période dans l'adresse, sinon celle par défaut du contrat. */
export function lirePeriode(valeur: unknown): Periode {
  return PERIODES.includes(valeur as Periode) ? (valeur as Periode) : 'mois'
}

/** Nom du pays dans la langue de l'interface (`SN` → « Sénégal »), sans liste à maintenir. */
export function nomPays(code: string, langue: string): string {
  try {
    return new Intl.DisplayNames([langue], { type: 'region' }).of(code) ?? code
  } catch {
    return code
  }
}
