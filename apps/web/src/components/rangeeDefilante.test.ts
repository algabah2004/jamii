import { describe, expect, it } from 'vitest'
import { decalagePourVoir } from './rangeeDefilante'

// Une rangée de 300 px (de 10 à 310), comme le menu à 320 px, avec 32 px de fondu de chaque côté.
const rangee = { left: 10, right: 310 }

describe('decalagePourVoir', () => {
  it('ne bouge pas un élément déjà visible en entier', () => {
    expect(decalagePourVoir(rangee, { left: 100, right: 200 })).toBe(0)
  })

  it('fait défiler vers la droite un élément à moitié caché sous le fondu (« Missions » à 320 px)', () => {
    // Missions : de 291 à 366, la rangée s'arrête à 310 → il faut 366 - (310 - 32) = 88 px.
    expect(decalagePourVoir(rangee, { left: 291, right: 366 })).toBe(88)
  })

  it('fait défiler vers la gauche un élément coupé au début', () => {
    expect(decalagePourVoir(rangee, { left: -20, right: 60 })).toBe(-62)
  })

  it('montre le début d’un élément plus large que la place disponible', () => {
    expect(decalagePourVoir(rangee, { left: 200, right: 600 })).toBe(158)
  })
})
