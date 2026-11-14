import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierMission, reinitialiserMissions } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'

/** La mission publiée des maquettes : une proposition de Kofi, vue par sa cliente. */
const ID = 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91'

beforeEach(() => reinitialiserMissions())

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/missions/:id', component: MissionPage }] })
  await routeur.push(`/missions/${ID}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

const visiteur = () => modifierMission(ID, (m) => (m.moi = { role: 'visiteur', actions: ['proposer'] }))

async function remplir(page: ReturnType<typeof mount>, message: string, prix: string, delai: string) {
  const champs = page.findAll('[data-test="form-proposer"] input')
  await page.get('[data-test="form-proposer"] textarea').setValue(message)
  await champs[0]!.setValue(prix)
  await champs[1]!.setValue(delai)
}

const MESSAGE = 'J’ai déjà fait trois boutiques avec paiement Wave, je commence par le catalogue.'

describe('les propositions reçues par la cliente', () => {
  it('montre chaque proposition avec son prix et son délai, et la retient', async () => {
    const page = await monter()
    await attendre(page, '[data-test="proposition"]')
    const carte = page.get('[data-test="proposition"]')
    expect(carte.text()).toContain('Kofi Mensah')
    expect(carte.text()).toMatch(/400\s?000/)
    expect(carte.text()).toContain('21 jours')
    expect(carte.get('[data-test="statut-proposition"]').text()).toBe('Envoyée')
    expect(page.find('[data-test="retirer"]').exists()).toBe(false)

    await carte.get('[data-test="retenir"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut-proposition"]').text()).toBe('Retenue'))
    expect(page.get('[data-test="aide-retenue"]').text()).toContain('prépare son devis')
    expect(page.find('[data-test="retenir"]').exists()).toBe(false)
  })

  it('pas de propositions à montrer sur un brouillon', async () => {
    modifierMission(ID, (m) => (m.statut = 'brouillon'))
    const page = await monter()
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="propositions"]').exists()).toBe(false)
  })
})

describe('proposer ses services', () => {
  it('garde le bouton éteint tant que le message, le prix ou le délai manquent', async () => {
    visiteur()
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    expect(page.find('[data-test="propositions"]').exists()).toBe(false)
    await remplir(page, 'Trop court.', '350 000', '20')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeDefined()
    await remplir(page, MESSAGE, 'beaucoup', '20')
    expect(page.get('[data-test="form-proposer"]').text()).toContain('pas un montant valide')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeDefined()
    await remplir(page, MESSAGE, '350 000', '400')
    expect(page.get('[data-test="form-proposer"]').text()).toContain('Entre 1 et 365 jours')
    await remplir(page, MESSAGE, '350 000', '20')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeUndefined()
  })

  it('envoie le prix en unité mineure, dans la devise du budget, puis montre ma proposition', async () => {
    visiteur()
    let envoye: unknown = null
    serveur.use(
      http.post('/api/missions/:id/propositions', async ({ request }) => {
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    await remplir(page, MESSAGE, '350 000', '20')
    await page.get('[data-test="form-proposer"]').trigger('submit')
    await attendre(page, '[data-test="propositions"]')
    expect(envoye).toEqual({ message: MESSAGE, prix: { centimes: 350000, devise: 'XOF' }, delaiJours: 20 })
    expect(page.find('[data-test="form-proposer"]').exists()).toBe(false)
    expect(page.get('[data-test="propositions"]').text()).toContain('Ma proposition')
  })

  it('dit quand on a déjà proposé ses services', async () => {
    visiteur()
    serveur.use(http.post('/api/missions/:id/propositions', () => HttpResponse.json({ code: 'deja_candidat' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    await remplir(page, MESSAGE, '350 000', '20')
    await page.get('[data-test="form-proposer"]').trigger('submit')
    await attendre(page, '[data-test="erreur-proposer"]')
    expect(page.get('[data-test="erreur-proposer"]').text()).toContain('déjà proposé')
  })

  it('le développeur retire sa proposition', async () => {
    modifierMission(ID, (m) => (m.moi = { role: 'candidat', actions: [] }))
    const page = await monter()
    await attendre(page, '[data-test="retirer"]')
    expect(page.get('[data-test="propositions"]').text()).toContain('Ma proposition')
    await page.get('[data-test="retirer"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut-proposition"]').text()).toBe('Retirée'))
    expect(page.find('[data-test="retirer"]').exists()).toBe(false)
  })
})
