/** Les petits mots qu'on saute pour former le monogramme d'une communauté. */
const MOTS_VIDES = new Set(['et', 'de', 'des', 'du', 'la', 'le', 'les', 'and', 'of', 'the', 'à', 'en'])

/**
 * Le monogramme d'une communauté : l'initiale de ses deux premiers mots utiles.
 * « Flutter et Dart » → « FD », « IA appliquée » → « IA », « Antenne Dakar » → « AD ».
 */
export function monogramme(nom: string): string {
  const mots = nom.split(/[\s\-–]+/).filter((m) => m && !MOTS_VIDES.has(m.toLowerCase()))
  if (mots.length === 1) return mots[0]!.slice(0, 2).toUpperCase()
  return mots.slice(0, 2).map((m) => m[0]!.toUpperCase()).join('')
}

/** Les motifs de la charte qu'une communauté peut recevoir. */
export const MOTIFS_COMMUNAUTE = ['reseau', 'ondes', 'capsules', 'croix', 'points'] as const
export type MotifCommunaute = (typeof MOTIFS_COMMUNAUTE)[number]

/**
 * Le motif d'une communauté, tiré de son nom court : toujours le même pour une communauté donnée,
 * et des communautés voisines ont des motifs différents. Purement décoratif.
 */
export function motifDe(slug: string): MotifCommunaute {
  let h = 0
  for (const c of slug) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return MOTIFS_COMMUNAUTE[h % MOTIFS_COMMUNAUTE.length]!
}
