import { describe, expect, it } from 'vitest'
import { lignesPays, lirePeriode, momentsTries, nomPays, pourcentage } from './calculs'
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

describe('lignesPays', () => {
  it('trie du plus grand au plus petit et met « Autres pays » en dernier, marqué comme regroupement', () => {
    const lignes = lignesPays([{ pays: 'CI', nombre: 6 }, { pays: 'SN', nombre: 9 }], 3, 'fr', 'Autres pays')
    expect(lignes.map((l) => l.nombre)).toEqual([9, 6, 3])
    expect(lignes[0]!.nom).toBe('Sénégal')
    expect(lignes.at(-1)).toEqual({ nom: 'Autres pays', nombre: 3, regroupe: true })
  })

  it("n'ajoute pas de ligne « Autres pays » vide", () => {
    expect(lignesPays([{ pays: 'SN', nombre: 5 }], 0, 'fr', 'Autres pays')).toHaveLength(1)
  })
})
