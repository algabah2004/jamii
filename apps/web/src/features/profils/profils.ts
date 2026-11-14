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

export type Profil = components['schemas']['Profil']
export type Realisation = components['schemas']['Realisation']

/**
 * Langues proposées dans le formulaire (codes ISO 639-1) : les grandes langues de travail du continent, puis les
 * langues africaines les plus parlées. Le nom s'affiche dans la langue de l'interface (Intl.DisplayNames).
 */
export const LANGUES_PROPOSEES = [
  'fr', 'en', 'ar', 'pt', 'sw', 'ha', 'yo', 'ig', 'am', 'om', 'so', 'wo', 'ff', 'bm', 'ln', 'rw', 'mg', 'ak', 'ee',
  'zu', 'xh', 'sn', 'ti',
] as const

export function nomLangue(code: string, langue: string): string {
  try {
    const nom = new Intl.DisplayNames([langue], { type: 'language' }).of(code)
    return nom ? nom.charAt(0).toLocaleUpperCase(langue) + nom.slice(1) : code
  } catch {
    return code
  }
}

// Drapeaux en SVG (flag-icons, MIT) : Windows n'affiche pas les drapeaux en emoji. Seules des adresses sont importées,
// le navigateur ne télécharge que le drapeau affiché.
const DRAPEAUX = import.meta.glob('/node_modules/flag-icons/flags/4x3/*.svg', {
  query: '?url&no-inline',
  import: 'default',
  eager: true,
}) as Record<string, string>

export function urlDrapeau(pays: string | null | undefined): string | null {
  if (!pays || !/^[A-Z]{2}$/.test(pays)) return null
  return DRAPEAUX[`/node_modules/flag-icons/flags/4x3/${pays.toLowerCase()}.svg`] ?? null
}

/** Les compétences à mettre en avant : celles choisies par la personne, sinon les premières de son profil. */
export function competencesPhares(profil: Pick<Profil, 'competencesPhares' | 'competences'>, nombre = 3): string[] {
  if (profil.competencesPhares.length) return profil.competencesPhares.slice(0, nombre)
  return profil.competences.slice(0, nombre).map((c) => c.competence)
}

/** L'adresse publique d'un profil : celle du lien partagé et du QR code. */
export function lienProfil(utilisateurId: string, origine = window.location.origin): string {
  return `${origine}/p/${utilisateurId}`
}

/** Le nom d'hôte d'un lien, pour l'afficher sans « https://www. ». */
export function hoteLisible(lien: string): string {
  try {
    const url = new URL(lien)
    const chemin = url.pathname.replace(/\/$/, '')
    return url.host.replace(/^www\./, '') + (chemin.length > 1 && chemin.length < 30 ? chemin : '')
  } catch {
    return lien
  }
}
