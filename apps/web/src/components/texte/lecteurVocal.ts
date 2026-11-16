/*
 * Le calcul derrière le lecteur de message vocal (LecteurVocal.vue) : la forme d'onde et la durée, tirées du son
 * lui-même, une fois décodé dans le navigateur. Un vocal enregistré par Chrome (WebM) n'annonce pas sa durée
 * (`duration` vaut Infinity) : le décodage la donne, juste.
 */
export const BARRES = 36

/** La hauteur de chaque barre, entre 0,12 et 1 : la crête du son sur sa tranche, rapportée à la plus forte. */
export function formeDOnde(echantillons: Float32Array, barres = BARRES): number[] {
  if (!echantillons.length) return Array.from({ length: barres }, () => 0.12)
  const tranche = Math.max(1, Math.floor(echantillons.length / barres))
  const pics: number[] = []
  for (let b = 0; b < barres; b++) {
    let max = 0
    const fin = Math.min(echantillons.length, (b + 1) * tranche)
    for (let i = b * tranche; i < fin; i++) {
      const v = Math.abs(echantillons[i]!)
      if (v > max) max = v
    }
    pics.push(max)
  }
  const plusFort = Math.max(...pics)
  // Une racine carrée relève les passages calmes : une voix normale ne ressemble pas à une ligne plate.
  return pics.map((p) => (plusFort > 0 ? Math.max(0.12, Math.sqrt(p / plusFort)) : 0.12))
}

/** « 0:07 », « 2:41 ». */
export function minutes(secondes: number): string {
  const s = Number.isFinite(secondes) && secondes > 0 ? Math.round(secondes) : 0
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Un seul vocal joue à la fois : lancer l'un met les autres en pause. */
let enLecture: HTMLAudioElement | null = null
export function jouerSeul(audio: HTMLAudioElement) {
  if (enLecture && enLecture !== audio) enLecture.pause()
  enLecture = audio
}

export const VITESSES = [1, 1.5, 2] as const
