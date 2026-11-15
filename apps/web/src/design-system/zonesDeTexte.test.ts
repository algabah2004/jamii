import { describe, expect, it } from 'vitest'
import { ajusterZone } from './zonesDeTexte'

describe('les zones d’écriture', () => {
  it('prennent la hauteur de leur texte, sans barre, puis défilent seulement au-delà de leur hauteur maximale', () => {
    const zone = document.createElement('textarea')
    zone.style.maxHeight = '200px'
    document.body.appendChild(zone)
    Object.defineProperty(zone, 'scrollHeight', { configurable: true, get: () => 120 })
    ajusterZone(zone)
    expect(zone.style.height).toBe('120px')
    expect(zone.style.overflowY).toBe('hidden')
    Object.defineProperty(zone, 'scrollHeight', { configurable: true, get: () => 500 })
    ajusterZone(zone)
    expect(zone.style.height).toBe('200px')
    expect(zone.style.overflowY).toBe('auto')
    zone.remove()
  })
})
