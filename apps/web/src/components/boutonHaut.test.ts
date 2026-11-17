import { describe, expect, it } from 'vitest'
import { doitAfficherHaut } from './boutonHaut'

describe('doitAfficherHaut', () => {
  it('reste caché sur une page courte, même tout en bas', () => {
    expect(doitAfficherHaut(1500, 800, 2400)).toBe(false)
  })
  it('reste caché en haut d’une page longue', () => {
    expect(doitAfficherHaut(600, 800, 6000)).toBe(false)
  })
  it('apparaît après un écran et demi sur une page longue', () => {
    expect(doitAfficherHaut(1300, 800, 6000)).toBe(true)
  })
  it('ne casse pas sans hauteur d’écran', () => {
    expect(doitAfficherHaut(100, 0, 0)).toBe(false)
  })
})
