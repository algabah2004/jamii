import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN, ID_DAKAR, ID_SAINT_LOUIS } from '../../mocks/cercles'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

const KWAME = 'd2e00000-0000-4000-8000-000000000001'

describe('les cercles qui ont besoin de moi', () => {
  it('recommande un cercle ouvert en disant pourquoi, et mène à sa page', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.find('[data-test="cercles-pour-moi"]').exists()).toBe(true))
    const carte = page.find('[data-test="cercles-pour-moi"] a')
    expect(carte.text()).toContain('Cercle d’Abidjan')
    expect(carte.text()).toContain('Il leur manque flutter, que tu connais')
    expect(carte.attributes('href')).toBe(`/cercles/${ID_ABIDJAN}`)
  })

  it("affiche quand même mes cercles si les recommandations ne répondent pas", async () => {
    serveur.use(http.get('/api/recommandations/cercles', () => HttpResponse.json({}, { status: 503 })))
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles-mes"] li')).toHaveLength(2))
    expect(page.find('[data-test="cercles-pour-moi"]').exists()).toBe(false)
    expect(page.find('[role="alert"]').exists()).toBe(false)
  })
})

describe('les profils qui manquent au cercle', () => {
  it('montre, dans mon cercle ouvert, les profils disponibles qui apportent ce qui manque, la même ville d’abord', async () => {
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find('[data-test="profils-recommandes"]').exists()).toBe(true))
    const noms = page.findAll('[data-test="profils-recommandes"] .reco strong').map((n) => n.text())
    expect(noms[0]).toBe('Ibrahima Fall')
    expect(noms).toContain('Kwame Mensah')
    // Indisponible : jamais recommandé.
    expect(noms).not.toContain('Zawadi Otieno')
    expect(page.find('[data-test="profils-recommandes"]').text()).toContain('Dans la ville du cercle')
  })

  it("invite par un message pré-rempli, puis marque l'invitation comme envoyée", async () => {
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(true))
    await page.find(`[data-test="inviter-${KWAME}"]`).trigger('click')
    const form = page.find('[data-test="form-invitation"]')
    expect((form.find('textarea').element as HTMLTextAreaElement).value).toContain('Bonjour Kwame, notre « Cercle de Saint-Louis »')
    await form.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="invitation-envoyee"]').text()).toContain('Kwame la verra dans ses messages'))
    expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(false)
  })

  it("explique un refus de l'API (cercle complet entre-temps)", async () => {
    serveur.use(http.post('/api/conversations/:id/messages', () => HttpResponse.json({ code: 'cercle_ferme' }, { status: 409 })))
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(true))
    await page.find(`[data-test="inviter-${KWAME}"]`).trigger('click')
    await page.find('[data-test="form-invitation"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="form-invitation"] [role="alert"]').text()).toBe("Le cercle est complet, ou l'ordre est déjà tiré."))
  })

  it("n'apparaît ni pour qui n'est pas membre, ni dans un cercle déjà tiré", async () => {
    const abidjan = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(abidjan.page.find('[data-test="rejoindre"]').exists()).toBe(true))
    expect(abidjan.page.find('[data-test="profils-recommandes"]').exists()).toBe(false)
    const dakar = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(dakar.page.find('[data-test="tour"]').exists()).toBe(true))
    expect(dakar.page.find('[data-test="profils-recommandes"]').exists()).toBe(false)
  })
})
