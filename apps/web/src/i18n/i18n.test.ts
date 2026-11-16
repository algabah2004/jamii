import { describe, expect, it } from 'vitest'
import { i18n } from '.'
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
  it("entre toujours en français, quelle que soit la langue du navigateur", () => {
    expect((i18n.global.locale as unknown as { value: string }).value).toBe('fr')
  })
})
