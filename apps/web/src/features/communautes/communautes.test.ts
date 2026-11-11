import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'

async function ouvrir(adresse: string) {
  // Mode composition : la langue est une ref à l'exécution (le type de createI18n ne le dit pas).
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

describe('Communautés', () => {
  it('liste les communautés, avec les antennes locales et celles dont je fais partie', async () => {
    const { page } = await ouvrir('/communautes')
    const cartes = page.findAll('[data-test="communautes"] > li')
    expect(cartes).toHaveLength(4)
    expect(page.text()).toContain('Antenne locale · Sénégal')
    expect(cartes[0]!.find('[data-test="deja-membre"]').exists()).toBe(true)
  })

  it('rejoindre une communauté en un clic ouvre le formulaire de question', async () => {
    const { page } = await ouvrir('/communautes/fintech')
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
    await page.get('[data-test="adhesion"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="adhesion"]').text()).toBe('Quitter'))
    expect(page.find('[data-test="publier"]').exists()).toBe(true)
  })

  it('le filtre « questions sans réponse acceptée » reste dans l’adresse', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    expect(page.findAll('[data-test="publications"] > li')).toHaveLength(4)
    await page.findAll('.filtres button')[1]!.trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="publications"] > li')).toHaveLength(2))
    expect(routeur.currentRoute.value.query.filtre).toBe('ouvertes')
  })

  it('une communauté inconnue affiche « introuvable »', async () => {
    const { page } = await ouvrir('/communautes/inconnue')
    expect(page.find('[data-test="publications"]').exists()).toBe(false)
    expect(page.text()).toContain('Toutes les communautés')
  })
})
