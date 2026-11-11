/** Rapport de contraste WCAG 2.1 entre deux couleurs CSS (#rrggbb ou rgb(r, g, b)). */
export function contraste(a: string, b: string): number {
  const [la, lb] = [luminance(a), luminance(b)]
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

function luminance(couleur: string): number {
  const canaux = lireCanaux(couleur).map((v) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * canaux[0]! + 0.7152 * canaux[1]! + 0.0722 * canaux[2]!
}

function lireCanaux(couleur: string): number[] {
  const hexa = /^#([0-9a-f]{6})$/i.exec(couleur.trim())
  if (hexa) return [0, 2, 4].map((i) => parseInt(hexa[1]!.slice(i, i + 2), 16))
  const rgb = /rgba?\(\s*(\d+)[ ,]+(\d+)[ ,]+(\d+)/.exec(couleur)
  if (rgb) return [rgb[1], rgb[2], rgb[3]].map(Number)
  throw new Error(`couleur illisible : ${couleur}`)
}
