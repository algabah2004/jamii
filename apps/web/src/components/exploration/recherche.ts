/** Recherche locale, insensible à la casse et aux accents, dans la liste reçue de l’API. */
export function correspond(recherche: string, valeurs: (string | null | undefined)[]) {
  const normaliser = (texte: string) => texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase()
  const termes = normaliser(recherche).trim().split(/\s+/).filter(Boolean)
  const texte = normaliser(valeurs.filter(Boolean).join(' '))
  return termes.every((terme) => texte.includes(terme))
}
