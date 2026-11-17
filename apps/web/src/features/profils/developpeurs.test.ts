import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { normaliserCompetence } from './recherche'

enableAutoUnmount(afterEach)

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  return { page: mount(App, { global: { plugins: [i18n, routeur] }, attachTo: document.body }), routeur }
}
const noms = (page: ReturnType<typeof mount>) => page.findAll('[data-test="profil"] h2').map((h) => h.text())

describe('trouver un développeur', () => {
  it('écrit une compétence comme l’API l’attend', () => {
    expect(normaliserCompetence(' Spring Boot ')).toBe('spring-boot')
    expect(normaliserCompetence('Réseau')).toBe('reseau')
    expect(normaliserCompetence('-c#!')).toBe('c#')
  })

  it('la loupe de l’en-tête mène à la recherche', async () => {
    const { page, routeur } = await ouvrir('/communautes')
    await vi.waitFor(() => page.get('[data-test="lien-developpeurs"]'))
    await page.get('[data-test="lien-developpeurs"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/profils'))
  })

  it('cherche par nom, par compétence et parmi les disponibles', async () => {
    const { page } = await ouvrir('/profils')
    await vi.waitFor(() => expect(noms(page)).toHaveLength(4))
    // Les disponibles d'abord.
    expect(noms(page).at(-1)).toBe('Zawadi Otieno')

    await page.get('[data-test="recherche-nom"]').setValue('AMINA')
    await vi.waitFor(() => expect(noms(page)).toEqual(['Amina Bello']))
    expect(page.get('[data-test="ecrire-profil"]').attributes('href')).toBe('/messages/d2e00000-0000-4000-8000-000000000002')

    await page.get('[data-test="recherche-nom"]').setValue('')
    await page.get('[data-test="recherche-competence"]').setValue('React')
    await vi.waitFor(() => expect(noms(page)).toEqual(['Kwame Mensah', 'Zawadi Otieno']))
    await page.get('[data-test="recherche-disponibles"]').setValue(true)
    await vi.waitFor(() => expect(noms(page)).toEqual(['Kwame Mensah']))

    await page.get('[data-test="recherche-nom"]').setValue('personne')
    await vi.waitFor(() => expect(page.find('[data-test="aucun-profil"]').exists()).toBe(true))
  })
})
