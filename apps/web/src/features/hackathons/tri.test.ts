import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierHackathon, modifierInscriptions, reinitialiserHackathons } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/hackathons/:id', component: HackathonPage }] })
  await routeur.push(`/hackathons/${ID}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

// Un hackathon sur candidature, lu par son organisateur : Fatou et Moussa attendent une réponse.
beforeEach(() => {
  reinitialiserHackathons()
  modifierHackathon(ID, (h) => Object.assign(h, { modeInscription: 'candidature', moi: { role: 'organisateur', actions: ['trier_inscriptions'] } }))
  modifierInscriptions(ID, (i) => Object.assign(i, { statut: 'en_attente' }))
})

describe('trier les candidatures', () => {
  it('accepte une candidature et montre son nouveau statut', async () => {
    let corps: unknown
    let type: string | null = null
    serveur.use(
      http.patch('/api/hackathons/:id/inscriptions/:inscriptionId', async ({ request }) => {
        corps = await request.clone().json()
        type = request.headers.get('Content-Type')
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="candidature"]')
    expect(page.get('[data-test="a-trier"]').text()).toBe('2 candidatures attendent ta réponse.')
    const premiere = page.findAll('[data-test="candidature"]')[0]!
    expect(premiere.text()).toContain('Fatou Ndiaye')
    expect(premiere.get('[data-test="statut-candidature"]').text()).toBe('Attend ta réponse')

    await premiere.get('[data-test="decider-acceptee"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="a-trier"]').text()).toBe('Une candidature attend ta réponse.'))
    expect(corps).toEqual({ statut: 'acceptee' })
    expect(type).toContain('application/merge-patch+json')
    const fatou = page.findAll('[data-test="candidature"]').find((c) => c.text().includes('Fatou Ndiaye'))!
    expect(fatou.get('[data-test="statut-candidature"]').text()).toBe('Acceptée')
    expect(fatou.find('[data-test="decider-acceptee"]').exists()).toBe(false)
  })

  it('n’apparaît pas quand l’inscription est ouverte à tous', async () => {
    modifierHackathon(ID, (h) => Object.assign(h, { modeInscription: 'ouvert' }))
    const page = await monter()
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="tri"]').exists()).toBe(false)
  })

  it('dit quand la réponse n’a pas pu être enregistrée', async () => {
    serveur.use(http.patch('/api/hackathons/:id/inscriptions/:inscriptionId', () => HttpResponse.json({ code: 'etat_invalide' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="candidature"]')
    await page.findAll('[data-test="candidature"]')[0]!.get('[data-test="decider-refusee"]').trigger('click')
    await attendre(page, '[data-test="erreur-tri"]')
    expect(page.get('[data-test="erreur-tri"]').text()).toContain('ne peuvent plus changer')
  })
})
