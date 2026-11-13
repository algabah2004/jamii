import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN, ID_SAINT_LOUIS } from '../../mocks/cercles'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

describe('un cercle publié dans une communauté', () => {
  it('se publie depuis le cercle, avec un titre et un texte pré-remplis qui contiennent son lien', async () => {
    const { page, routeur } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find('[data-test="ouvrir-partage"]').exists()).toBe(true))
    await page.find('[data-test="ouvrir-partage"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="form-partage"] select option').exists()).toBe(true))
    const form = page.find('[data-test="form-partage"]')
    expect((form.find('input').element as HTMLInputElement).value).toBe('Le Cercle de Saint-Louis cherche 2 membres')
    expect((form.find('textarea').element as HTMLTextAreaElement).value).toContain(`/cercles/${ID_SAINT_LOUIS}`)
    await form.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="partage-publie"]').text()).toContain('Publié dans Flutter et Dart.'))

    // Dans la publication, le lien devient une carte du cercle (Awa en est déjà membre : « Ouvrir le cercle »).
    await routeur.push(page.find('[data-test="partage-publie"] a').attributes('href')!)
    await vi.waitFor(() => expect(page.find('[data-test="carte-cercle"]').text()).toContain('Cercle de Saint-Louis'))
    expect(page.find('[data-test="carte-cercle"]').text()).toContain('Ouvrir le cercle')
  })

  it("rejoint le cercle depuis la carte d'une publication quand il reste de la place", async () => {
    // Une publication qui contient le lien du cercle d'Abidjan, dont Awa n'est pas membre.
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.find('input').exists() || page.find('button').exists()).toBe(true))
    const reponse = await fetch(new URL('/api/communautes/flutter/publications', window.location.origin), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'discussion', titre: 'Le cercle d’Abidjan cherche du monde', contenu: `Rejoignez-nous : https://jamii.example.com/cercles/${ID_ABIDJAN}` }),
    })
    const { id } = await reponse.json()
    await routeur.push(`/publications/${id}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre-depuis-publication"]').exists()).toBe(true))
    await page.find('[data-test="rejoindre-depuis-publication"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe(`/cercles/${ID_ABIDJAN}`))
  })
})
