import { describe, expect, it } from 'vitest'
import { lirePeriode, momentsTries, nomPays, pourcentage } from './calculs'
import { tableauDemo } from '../../mocks/handlers'

describe('pourcentage', () => {
  it('arrondit à l’entier', () => {
    expect(pourcentage(7, 12)).toBe(58)
  })
  it('ne divise jamais par zéro', () => {
    expect(pourcentage(0, 0)).toBeNull()
  })
  it('ne dépasse jamais 100 %, même si les comptes sont incohérents', () => {
    expect(pourcentage(5, 3)).toBe(100)
  })
})

describe('momentsTries', () => {
  it('classe les services du plus proposé au moins proposé, avec leur taux', () => {
    expect(momentsTries(tableauDemo).map((m) => [m.service, m.taux])).toEqual([
      ['datacloud', 58],
      ['yokk', 38],
      ['orion', 40],
    ])
  })
})

describe('lirePeriode', () => {
  it('accepte une période du contrat et revient à « mois » sinon', () => {
    expect(lirePeriode('semaine')).toBe('semaine')
    expect(lirePeriode('annee')).toBe('mois')
    expect(lirePeriode(undefined)).toBe('mois')
  })
})

describe('nomPays', () => {
  it('traduit le code ISO dans la langue de l’interface', () => {
    expect(nomPays('SN', 'fr')).toBe('Sénégal')
    expect(nomPays('KE', 'en')).toBe('Kenya')
  })
})
