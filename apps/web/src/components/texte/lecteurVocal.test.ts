import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { i18n } from '../../i18n'
import { formeDOnde, jouerSeul, minutes } from './lecteurVocal'
import LecteurVocal from './LecteurVocal.vue'

enableAutoUnmount(afterEach)

describe('le lecteur de message vocal (#157)', () => {
  it('tire la forme d’onde du son : une barre par tranche, la plus forte à 1, jamais plate', () => {
    const son = new Float32Array(400)
    son.fill(0.01)
    son[50] = -0.8 // une crête négative compte aussi
    son[350] = 0.2
    const barres = formeDOnde(son, 4)
    expect(barres).toHaveLength(4)
    expect(barres[0]).toBe(1)
    expect(barres[3]).toBeCloseTo(Math.sqrt(0.25))
    expect(barres[1]).toBe(Math.max(0.12, Math.sqrt(0.01 / 0.8)))
    expect(formeDOnde(new Float32Array(0), 3)).toEqual([0.12, 0.12, 0.12])
    expect(formeDOnde(new Float32Array(10), 2)).toEqual([0.12, 0.12])
  })

  it('écrit les durées en minutes, même quand le navigateur ne les connaît pas', () => {
    expect(minutes(7.4)).toBe('0:07')
    expect(minutes(161)).toBe('2:41')
    expect(minutes(Infinity)).toBe('0:00')
    expect(minutes(Number.NaN)).toBe('0:00')
  })

  it('ne laisse jouer qu’un vocal à la fois', () => {
    const a = { pause: vi.fn() } as unknown as HTMLAudioElement
    const b = { pause: vi.fn() } as unknown as HTMLAudioElement
    jouerSeul(a)
    jouerSeul(b)
    expect(a.pause).toHaveBeenCalledOnce()
    expect(b.pause).not.toHaveBeenCalled()
  })

  it('remplace le lecteur du navigateur : lecture, forme d’onde en curseur, vitesse', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    const v = mount(LecteurVocal, { props: { url: '/api/fichiers/x', nom: 'message vocal' }, global: { plugins: [i18n] } })
    expect(v.find('audio[controls]').exists()).toBe(false)
    expect(v.get('[data-test="jouer-vocal"]').attributes('aria-label')).toBe('Écouter le message vocal')
    const curseur = v.get('[role="slider"]')
    expect(curseur.attributes('tabindex')).toBe('0')
    expect(v.findAll('.vocal__barre')).toHaveLength(36)
    await v.get('[data-test="vitesse-vocal"]').trigger('click')
    expect(v.get('[data-test="vitesse-vocal"]').text()).toBe('×1,5')
    await v.get('[data-test="vitesse-vocal"]').trigger('click')
    await v.get('[data-test="vitesse-vocal"]').trigger('click')
    expect(v.get('[data-test="vitesse-vocal"]').text()).toBe('×1')
  })
})
