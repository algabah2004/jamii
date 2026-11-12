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
