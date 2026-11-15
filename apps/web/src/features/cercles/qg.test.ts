import { describe, expect, it } from 'vitest'
import { joursDuTour, outilDepuis, pointsDeLaColline } from './qg'

describe('le QG du cercle', () => {
  it('ouvre le QG par défaut une fois l’ordre tiré, le cercle avant ou pour les non-membres', () => {
    expect(outilDepuis({}, true, true)).toBe('qg')
    expect(outilDepuis({}, true, false)).toBe('cercle')
    expect(outilDepuis({ outil: 'salon' }, false, true)).toBe('cercle')
    expect(outilDepuis({ onglet: 'salon' }, true, true)).toBe('salon')
    expect(outilDepuis({ outil: 'inconnu' }, true, true)).toBe('qg')
  })

  it('pose les tâches sur la colline : à faire à gauche, en cours au sommet, livrées à droite', () => {
    const [a, e, l] = pointsDeLaColline([
      { id: 'a', titre: 'a', statut: 'a_faire' },
      { id: 'e', titre: 'e', statut: 'en_cours' },
      { id: 'l', titre: 'l', statut: 'livree' },
    ])
    expect(a!.x).toBeLessThan(e!.x)
    expect(e!.x).toBeLessThan(l!.x)
    expect(e!.y).toBeLessThan(a!.y)
  })

  it('compte 14 jours, marque aujourd’hui et la livraison au 14e', () => {
    const jours = joursDuTour('2026-11-12T09:00:00Z', new Date('2026-11-15T10:00:00Z'))
    expect(jours).toHaveLength(14)
    expect(jours.findIndex((j) => j.aujourdhui)).toBe(3)
    expect(jours[6]!.jalon).toBeNull()
    expect(jours[13]!.jalon).toBe('livraison')
  })
})
