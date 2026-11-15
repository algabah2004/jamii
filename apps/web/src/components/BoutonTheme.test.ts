import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { i18n } from '../i18n'
import { choisirTheme, themeActuel } from '../design-system'
import BoutonTheme from './BoutonTheme.vue'

describe('le bouton du thème', () => {
  it('passe du clair au sombre et retour, et le dit pour les lecteurs d’écran', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    choisirTheme('clair')
    const bouton = mount(BoutonTheme, { global: { plugins: [i18n] } })
    expect(bouton.attributes('aria-label')).toBe('Passer au thème sombre')

    await bouton.trigger('click')
    expect(themeActuel.value).toBe('sombre')
    expect(document.documentElement.dataset.theme).toBe('sombre')
    expect(bouton.attributes('aria-label')).toBe('Passer au thème clair')

    await bouton.trigger('click')
    expect(themeActuel.value).toBe('clair')
    expect(document.documentElement.dataset.theme).toBe('clair')
  })
})
