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

/**
 * Les lignes d'une répartition par pays (inscrits, membres des cercles), du plus grand au plus petit, puis « Autres
 * pays » en dernier s'il y en a. `autres` n'est jamais un pays : il est marqué `regroupe`.
 */
export function lignesPays(
  parPays: { pays: string; nombre: number }[],
  autres: number,
  langue: string,
  libelleAutres: string,
) {
  const lignes = [...parPays]
    .sort((a, b) => b.nombre - a.nombre)
    .map((p) => ({ nom: nomPays(p.pays, langue), nombre: p.nombre, regroupe: false }))
  if (autres > 0) lignes.push({ nom: libelleAutres, nombre: autres, regroupe: true })
  return lignes
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
