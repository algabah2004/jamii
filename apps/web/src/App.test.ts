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

  it('ramène la page active dans le champ quand le menu défile', async () => {
    const { page, routeur } = await ouvrir('/missions')
    const nav = page.get('nav[aria-label]').element as HTMLElement
    // jsdom ne calcule pas la mise en page : on simule un menu plus large que l'écran.
    Object.defineProperty(nav, 'scrollWidth', { configurable: true, value: 900 })
    Object.defineProperty(nav, 'clientWidth', { configurable: true, value: 300 })
    await routeur.push('/systalink')
    const actif = nav.querySelector<HTMLElement>('a.router-link-active')!
    Object.defineProperty(actif, 'offsetLeft', { configurable: true, value: 760 })
    Object.defineProperty(actif, 'offsetWidth', { configurable: true, value: 100 })
    await routeur.push('/vitrine')
    await routeur.push('/systalink')
    await vi.waitFor(() => expect(nav.scrollLeft).toBe(660))
  })
})
