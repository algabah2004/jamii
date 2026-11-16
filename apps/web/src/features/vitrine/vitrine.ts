import type { components } from '../../api/generated/vitrine'

export type ProjetEnVitrine = components['schemas']['ProjetEnVitrine']
export type Hebergeur = components['schemas']['Hebergeur']

/**
 * Le lien vient d'un utilisateur : on ne l'affiche que s'il est en https, même si l'API le garantit déjà
 * (défense en profondeur contre `javascript:` ou `http:`).
 */
export function lienSur(url: string): string | null {
  try {
    return new URL(url).protocol === 'https:' ? url : null
  } catch {
    return null
  }
}

const UNITES: [Intl.RelativeTimeFormatUnit, number][] = [
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
]

/** « il y a 3 heures », « 2 hours ago »… dans la langue de l'interface. */
export function ilYa(date: string, langue: string, maintenant: Date = new Date()): string {
  const secondes = Math.max(0, (maintenant.getTime() - new Date(date).getTime()) / 1000)
  const format = new Intl.RelativeTimeFormat(langue, { numeric: 'auto' })
  for (const [unite, duree] of UNITES) {
    if (secondes >= duree) return format.format(-Math.floor(secondes / duree), unite)
  }
  return format.format(0, 'minute')
}

/** Les initiales des deux premiers mots du titre : « Boutique en ligne » → « Be ». Le monogramme du projet. */
export function initiales(titre: string): string {
  return titre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((mot) => mot[0] ?? '')
    .join('')
}

/** Une teinte stable par projet (bleu ou orange de la charte), pour que les monogrammes ne se ressemblent pas tous. */
export function teinte(projetId: string): 'action' | 'preuve' {
  let somme = 0
  for (const c of projetId) somme = (somme + c.charCodeAt(0)) % 2
  return somme === 0 ? 'action' : 'preuve'
}
