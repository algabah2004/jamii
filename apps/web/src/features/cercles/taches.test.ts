import { describe, expect, it } from 'vitest'
import { composerDescription, decouperDescription, etatEcheance, ordreDeTravail } from './taches'

describe('les tâches', () => {
  it('se rangent par priorité, puis par échéance (sans échéance à la fin), puis par ancienneté', () => {
    const t = (id: string, priorite: 'haute' | 'normale' | 'basse', echeance: string | null, creeLe = '2026-11-10') => ({ id, priorite, echeance, creeLe })
    const ordre = ordreDeTravail([t('a', 'basse', '2026-11-16'), t('b', 'normale', null), t('c', 'haute', '2026-11-20'), t('d', 'normale', '2026-11-17'), t('e', 'haute', '2026-11-18')])
    expect(ordre.map((x) => x.id)).toEqual(['e', 'c', 'd', 'b', 'a'])
  })

  it('découpe et recompose la description : ce qu’il faut faire, et quand c’est fini', () => {
    const texte = composerDescription('Connexion par numéro.', 'le code SMS arrive.', 'Fini quand')
    expect(texte).toBe('Connexion par numéro.\n\nFini quand : le code SMS arrive.')
    expect(decouperDescription(texte)).toEqual({ faire: 'Connexion par numéro.', fini: 'le code SMS arrive.' })
    expect(decouperDescription('Seulement le quoi.')).toEqual({ faire: 'Seulement le quoi.', fini: '' })
    expect(decouperDescription('Garder les pesées.\nFini quand une pesée reste après redémarrage.').fini).toBe('une pesée reste après redémarrage.')
    expect(composerDescription('  ', '', 'Fini quand')).toBeNull()
  })

  it('dit si une échéance est dépassée, pour aujourd’hui, demain ou plus tard', () => {
    const maintenant = new Date('2026-11-15T10:00:00Z')
    expect(etatEcheance('2026-11-14', maintenant)).toBe('depassee')
    expect(etatEcheance('2026-11-15', maintenant)).toBe('aujourdhui')
    expect(etatEcheance('2026-11-16', maintenant)).toBe('demain')
    expect(etatEcheance('2026-11-30', maintenant)).toBe('plus_tard')
    expect(etatEcheance(null, maintenant)).toBeNull()
  })
})
