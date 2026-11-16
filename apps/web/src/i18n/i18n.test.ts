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
  it("choisit le français par défaut, l'anglais seulement si on l'a choisi", () => {
    // Français par défaut, même pour un navigateur en anglais : l'anglais seulement si on l'a choisi.
    expect(langueInitiale(null)).toBe('fr')
    expect(langueInitiale('fr')).toBe('fr')
    expect(langueInitiale('en')).toBe('en')
    expect(langueInitiale('<script>')).toBe('fr')
  })
})
