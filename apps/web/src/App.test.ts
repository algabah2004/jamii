import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from './test-setup'
import { i18n } from './i18n'
import { creerRouteur } from './router'
import { chargerSession } from './session'
import App from './App.vue'

enableAutoUnmount(afterEach)

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
  return { page, routeur }
}

describe("le cadre de l'application", () => {
  it('se déconnecte aussi depuis le pied de page, où le bouton vit sur téléphone', async () => {
    let deconnecte = false
    serveur.use(http.post('/api/auth/deconnexion', () => ((deconnecte = true), new HttpResponse(null, { status: 204 }))))
    const { page } = await ouvrir('/missions')
    await vi.waitFor(() => expect(page.find('[data-test="deconnexion-pied"]').exists()).toBe(true))
    await page.get('[data-test="deconnexion-pied"]').trigger('click')
    await vi.waitFor(() => expect(deconnecte).toBe(true))
  })

  it('ouvre et referme le menu replié (téléphone) : bouton, Échap, changement de page', async () => {
    const { page, routeur } = await ouvrir('/missions')
    const bouton = page.get('[data-test="bouton-menu"]')
    expect(bouton.attributes('aria-controls')).toBe('menu-principal')
    expect(bouton.attributes('aria-expanded')).toBe('false')
    await bouton.trigger('click')
    expect(bouton.attributes('aria-expanded')).toBe('true')
    expect(page.get('header').classes()).toContain('entete--menu-ouvert')
    await page.get('header').trigger('keydown', { key: 'Escape' })
    expect(bouton.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(bouton.element)
    await bouton.trigger('click')
    await routeur.push('/vitrine')
    await vi.waitFor(() => expect(bouton.attributes('aria-expanded')).toBe('false'))
  })
})
