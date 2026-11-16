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

export type Preuve = components['schemas']['Preuve']

/**
 * La pastille de couleur d'un langage, comme sur GitHub (linguist), pour qu'un développeur s'y retrouve d'un coup
 * d'œil. Un langage absent de la table prend une teinte stable tirée de son nom.
 */
const COULEURS_LANGAGES: Record<string, string> = {
  dart: '#00B4AB', typescript: '#3178C6', javascript: '#F1E05A', python: '#3572A5', java: '#B07219',
  kotlin: '#A97BFF', swift: '#F05138', go: '#00ADD8', rust: '#DEA584', php: '#4F5D95', ruby: '#701516',
  'c#': '#178600', 'c++': '#F34B7D', c: '#555555', html: '#E34C26', css: '#663399', scss: '#C6538C',
  vue: '#41B883', svelte: '#FF3E00', shell: '#89E051', 'jupyter notebook': '#DA5B0B', r: '#198CE7',
  elixir: '#6E4A7E', scala: '#C22D40', lua: '#000080', 'objective-c': '#438EFF', solidity: '#AA6746',
}

export function couleurLangage(nom: string | null | undefined): string {
  const cle = (nom ?? '').toLowerCase()
  if (COULEURS_LANGAGES[cle]) return COULEURS_LANGAGES[cle]
  let somme = 0
  for (const lettre of cle) somme = (somme * 31 + lettre.charCodeAt(0)) % 360
  return `hsl(${somme} 55% 50%)`
}

/** « il y a 3 jours », « 2 months ago » : la date de mise à jour d'un dépôt, dans la langue de l'interface. */
export function ilYa(date: string | null | undefined, langue: string, maintenant = Date.now()): string | null {
  if (!date) return null
  const ecart = (new Date(date).getTime() - maintenant) / 1000
  if (Number.isNaN(ecart)) return null
  const format = new Intl.RelativeTimeFormat(langue, { numeric: 'auto' })
  const unites: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000], ['month', 2_592_000], ['week', 604_800], ['day', 86_400], ['hour', 3600], ['minute', 60],
  ]
  for (const [unite, secondes] of unites) {
    if (Math.abs(ecart) >= secondes) return format.format(Math.round(ecart / secondes), unite)
  }
  return format.format(0, 'minute')
}
