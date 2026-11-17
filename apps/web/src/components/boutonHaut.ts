/** Une page « longue » fait plus de trois écrans ; le bouton apparaît après un écran et demi de défilement. */
export const PAGES_MIN = 3
export const DEFILEMENT_MIN = 1.5

export function doitAfficherHaut(defilement: number, hauteurEcran: number, hauteurPage: number): boolean {
  if (hauteurEcran <= 0) return false
  return hauteurPage > hauteurEcran * PAGES_MIN && defilement > hauteurEcran * DEFILEMENT_MIN
}
