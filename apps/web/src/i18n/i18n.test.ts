import { describe, expect, it } from 'vitest'
import { langueInitiale } from '.'
import fr from './fr.json'
import en from './en.json'

function cles(objet: object, prefixe = ''): string[] {
  return Object.entries(objet).flatMap(([cle, valeur]) =>
    typeof valeur === 'object' ? cles(valeur, `${prefixe}${cle}.`) : [`${prefixe}${cle}`],
  )
}

describe('traductions', () => {
  it("l'anglais a exactement les mêmes clés que le français", () => {
    expect(cles(en).sort()).toEqual(cles(fr).sort())
  })
  it("suit la langue du navigateur, sauf si on a déjà choisi avec le bouton FR/EN", () => {
    expect(langueInitiale(null, ['en-US', 'fr'])).toBe('en')
    expect(langueInitiale(null, ['fr-SN'])).toBe('fr')
    expect(langueInitiale(null, ['wo-SN', 'en'])).toBe('fr')
    expect(langueInitiale(null, [])).toBe('fr')
    expect(langueInitiale('fr', ['en-GB'])).toBe('fr')
    expect(langueInitiale('en', ['fr-FR'])).toBe('en')
    expect(langueInitiale('<script>', ['fr'])).toBe('fr')
  })
})
