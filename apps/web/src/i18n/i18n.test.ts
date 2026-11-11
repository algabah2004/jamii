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
  it("choisit le français par défaut, l'anglais si le navigateur le préfère", () => {
    expect(langueInitiale([])).toBe('fr')
    expect(langueInitiale(['wo-SN', 'fr'])).toBe('fr')
    expect(langueInitiale(['en-NG'])).toBe('en')
  })
})
