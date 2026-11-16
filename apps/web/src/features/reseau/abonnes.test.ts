import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { rafraichirActivite } from './activite'
import { nombreCourt } from './abonnes'

enableAutoUnmount(afterEach)

const KOFI = 'c1a00000-0000-4000-8000-000000000002'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  await rafraichirActivite()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  return mount(App, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
}

describe('les abonnés (#157)', () => {
  it('écrit les grands nombres en court', () => {
    expect(nombreCourt(128, 'fr')).toBe('128')
    expect(nombreCourt(12_500, 'en')).toBe('12.5K')
  })

  it('je suis quelqu’un depuis son profil, et les compteurs suivent', async () => {
    const page = await ouvrir(`/p/${KOFI}`)
    const bloc = await vi.waitFor(() => page.get('[data-test="abonnes-profil"]'))
    expect(bloc.get('[data-test="suivre"]').text()).toBe('Suivre')
    expect(bloc.get('[data-test="voir-abonnes"]').text()).toBe('0 abonné')
    // Kofi suit déjà Awa : « Te suit ».
    expect(bloc.find('[data-test="te-suit"]').exists()).toBe(true)

    await bloc.get('[data-test="suivre"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="suivre"]').text()).toBe('Abonné'))
    expect(page.get('[data-test="suivre"]').attributes('aria-pressed')).toBe('true')
    expect(page.get('[data-test="voir-abonnes"]').text()).toBe('1 abonné')

    // La liste des abonnés.
    await page.get('[data-test="voir-abonnes"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="liste-abonnes"]').text()).toContain('Awa'))

    // Un deuxième clic se désabonne.
    await page.get('[data-test="liste-abonnes"] button').trigger('click')
    await page.get('[data-test="suivre"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="suivre"]').text()).toBe('Suivre'))
  })

  it('sur mon profil : mes compteurs, sans bouton pour me suivre', async () => {
    const page = await ouvrir("/profil")
    const bloc = await vi.waitFor(() => page.get('[data-test="abonnes-profil"]'))
    expect(bloc.find('[data-test="suivre"]').exists()).toBe(false)
    expect(bloc.get('[data-test="voir-abonnes"]').text()).toBe('1 abonné')
    await bloc.get('[data-test="voir-abonnes"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="liste-abonnes"]').text()).toContain('Kofi Yao'))
  })
})
