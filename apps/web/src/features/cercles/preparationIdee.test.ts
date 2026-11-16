import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import axe from 'axe-core'
import PreparationIdee from './PreparationIdee.vue'
import { i18n } from '../../i18n'

it('rend le cahier et le CSV accessibles, avec un aperçu lisible des tâches', async () => {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  const page = mount(PreparationIdee, {
    props: { cercleId: 'cercle', document: null, backlog: [{ titre: 'Préparer la maquette', priorite: 'haute', echeance: null }] },
    global: { plugins: [i18n] }, attachTo: document.body,
  })
  try {
    expect(page.get('[data-test="apercu-backlog"]').text()).toContain('Haute')
    const resultat = await axe.run(page.element as HTMLElement, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(resultat.violations.map((v) => v.id)).toEqual([])
  } finally { page.unmount() }
})
