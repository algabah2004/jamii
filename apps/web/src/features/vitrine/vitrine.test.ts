import { describe, expect, it } from 'vitest'
import { ilYa, initiales, lienSur, teinte } from './vitrine'

describe('lienSur', () => {
  it('garde une adresse https', () => {
    expect(lienSur('https://couture-thies.example.com')).toBe('https://couture-thies.example.com')
  })
  it.each(['http://exemple.com', 'javascript:alert(1)', 'pas une adresse'])('refuse %s', (url) => {
    expect(lienSur(url)).toBeNull()
  })
})

describe('ilYa', () => {
  const maintenant = new Date('2026-11-13T10:00:00Z')
  it('dit depuis combien de temps, dans la langue de l’interface', () => {
    expect(ilYa('2026-11-13T07:00:00Z', 'fr', maintenant)).toBe('il y a 3 heures')
    expect(ilYa('2026-11-12T10:00:00Z', 'en', maintenant)).toBe('yesterday')
    expect(ilYa('2026-11-13T09:59:30Z', 'fr', maintenant)).toBe('cette minute-ci')
  })
})

describe('monogramme', () => {
  it('prend la première lettre des deux premiers mots', () => {
    expect(initiales('Boutique en ligne pour une couturière')).toBe('Be')
    expect(initiales('  Tontine  ')).toBe('T')
  })

  it('donne toujours la même teinte au même projet', () => {
    expect(teinte('p-1')).toBe(teinte('p-1'))
    expect(['action', 'preuve']).toContain(teinte('p-2'))
  })
})
