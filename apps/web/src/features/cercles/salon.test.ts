import { describe, expect, it } from 'vitest'
import { fusionner, lignesDuSalon, type MessageCercle } from './salon'

const m = (numero: number, auteur: string | null, envoyeLe: string, type: MessageCercle['type'] = 'texte'): MessageCercle => ({
  id: `m${numero}`, numero, type, auteur: auteur ? { id: auteur, nom: auteur } : null, texte: 'x', point: null, evenement: null, coupDeMain: null, appel: null, envoyeLe,
})

describe('les lignes du salon', () => {
  it('sépare les jours et groupe les messages libres d’un même auteur proches dans le temps', () => {
    const lignes = lignesDuSalon([
      m(1, 'A', '2026-11-14T10:00:00Z'),
      m(2, 'A', '2026-11-14T10:03:00Z'),
      m(3, 'A', '2026-11-14T10:20:00Z'),
      m(4, 'A', '2026-11-14T10:21:00Z', 'point'),
      m(5, 'A', '2026-11-15T08:00:00Z'),
    ])
    expect(lignes.map((l) => (l.type === 'jour' ? 'jour' : `${l.message.numero}${l.suite ? '+' : ''}`))).toEqual(['jour', '1', '2+', '3', '4', 'jour', '5'])
  })

  it('fusionne sans doublon, dans l’ordre des numéros', () => {
    const a = [m(1, 'A', '2026-11-14T10:00:00Z'), m(3, 'A', '2026-11-14T10:00:00Z')]
    expect(fusionner(a, [m(2, 'B', '2026-11-14T10:00:00Z'), m(3, 'A', '2026-11-14T10:00:00Z')]).map((x) => x.numero)).toEqual([1, 2, 3])
  })
})
