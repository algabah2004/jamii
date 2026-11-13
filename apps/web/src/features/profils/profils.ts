import type { components } from '../../api/generated/profils'

export type MonProfil = components['schemas']['MonProfil']
export type Disponibilite = components['schemas']['Disponibilite']

export const DISPONIBILITES: Disponibilite[] = ['disponible', 'occupe', 'indisponible']

/** Les 54 pays du continent, proposés dans le formulaire. Le nom s'affiche dans la langue de l'interface. */
export const PAYS_AFRIQUE = [
  'DZ', 'AO', 'BJ', 'BW', 'BF', 'BI', 'CV', 'CM', 'CF', 'TD', 'KM', 'CG', 'CD', 'CI', 'DJ', 'EG', 'GQ', 'ER',
  'SZ', 'ET', 'GA', 'GM', 'GH', 'GN', 'GW', 'KE', 'LS', 'LR', 'LY', 'MG', 'MW', 'ML', 'MR', 'MU', 'MA', 'MZ',
  'NA', 'NE', 'NG', 'RW', 'ST', 'SN', 'SC', 'SL', 'SO', 'ZA', 'SS', 'SD', 'TZ', 'TG', 'TN', 'UG', 'ZM', 'ZW',
] as const

/** « 74 % » : la part est une estimation (contrat Langage.part), on arrondit à l'unité. */
export function pourcentage(part: number, langue: string): string {
  return new Intl.NumberFormat(langue, { style: 'percent', maximumFractionDigits: 0 }).format(part)
}

/** « Flutter, firebase ,  » → ['flutter', 'firebase'] : l'API normalise, on retire seulement les vides et doublons. */
export function lireCompetences(saisie: string): string[] {
  return [...new Set(saisie.split(',').map((c) => c.trim()).filter(Boolean))]
}
