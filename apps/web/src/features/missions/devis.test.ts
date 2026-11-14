import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierMission, reinitialiserMissions } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'

/** La mission publiée des maquettes et la proposition de Kofi. */
const ID = 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91'
const PROPOSITION = 'c5e2a7b1-9d3f-4e6a-8b10-7f2c4d6e8a01'

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

const post = (chemin: string, corps: unknown) =>
  fetch(chemin, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) })

/** La cliente retient la proposition de Kofi (version 2 de la mission des maquettes). */
const retenir = () => post(`/api/missions/${ID}/propositions/${PROPOSITION}/retenir`, { version: 2 })

const DEVIS = {
  total: { centimes: 400000, devise: 'XOF' },
  retouchesComprises: 1,
  conditions: 'Hébergement non compris.',
  jalons: [
    { titre: 'Le catalogue', montant: { centimes: 250000, devise: 'XOF' }, delaiJours: 10 },
    { titre: 'Les commandes WhatsApp', montant: { centimes: 150000, devise: 'XOF' }, delaiJours: 21 },
  ],
}

async function remplirJalon(page: ReturnType<typeof mount>, i: number, titre: string, montant: string, delai: string) {
  const champs = page.findAll('[data-test="ligne-jalon"]')[i]!.findAll('input')
  await champs[0]!.setValue(titre)
  await champs[1]!.setValue(montant)
  await champs[2]!.setValue(delai)
}

describe('le devis du développeur retenu', () => {
  beforeEach(async () => {
    await retenir()
    modifierMission(ID, (m) => (m.moi = { role: 'candidat', actions: ['envoyer_devis'] }))
  })

  it('calcule le total à partir des jalons, et garde le bouton éteint tant qu’un jalon est incomplet', async () => {
    const page = await monter()
    await attendre(page, '[data-test="form-devis"]')
    await remplirJalon(page, 0, 'Le catalogue', '250 000', '10')
    expect(page.get('[data-test="total"]').text()).toMatch(/250\s?000/)
    expect(page.get('[data-test="envoyer-devis"]').attributes('disabled')).toBeUndefined()

    await page.get('[data-test="ajouter-jalon"]').trigger('click')
    expect(page.findAll('[data-test="ligne-jalon"]')).toHaveLength(2)
    expect(page.get('[data-test="envoyer-devis"]').attributes('disabled')).toBeDefined()
    await remplirJalon(page, 1, 'WA', 'beaucoup', '0')
    expect(page.get('[data-test="envoyer-devis"]').attributes('disabled')).toBeDefined()

    await page.findAll('[data-test="enlever-jalon"]')[1]!.trigger('click')
    expect(page.findAll('[data-test="ligne-jalon"]')).toHaveLength(1)
    expect(page.get('[data-test="envoyer-devis"]').attributes('disabled')).toBeUndefined()
  })

  it('envoie les jalons et leur somme en unité mineure, puis attend la réponse du client', async () => {
    let envoye: unknown = null
    serveur.use(
      http.post('/api/missions/:id/devis', async ({ request }) => {
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-devis"]')
    await remplirJalon(page, 0, 'Le catalogue', '250 000', '10')
    await page.get('[data-test="ajouter-jalon"]').trigger('click')
    await remplirJalon(page, 1, 'Les commandes WhatsApp', '150 000', '21')
    await page.get('[data-test="form-devis"] textarea').setValue('Hébergement non compris.')
    await page.get('[data-test="form-devis"]').trigger('submit')

    await attendre(page, '[data-test="devis-propose"]')
    expect(envoye).toEqual(DEVIS)
    expect(page.get('[data-test="attente-client"]').text()).toContain('pas encore répondu')
    expect(page.get('#devis-titre').text()).toBe('Envoyer un nouveau devis')
  })

  it('dit quand la somme des jalons ne tombe pas juste (409)', async () => {
    serveur.use(http.post('/api/missions/:id/devis', () => HttpResponse.json({ code: 'somme_jalons_invalide' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-devis"]')
    await remplirJalon(page, 0, 'Le catalogue', '250 000', '10')
    await page.get('[data-test="form-devis"]').trigger('submit')
    await attendre(page, '[data-test="erreur-devis"]')
    expect(page.get('[data-test="erreur-devis"]').text()).toContain('ne correspond pas au total')
  })
})

describe('la cliente accepte le devis', () => {
  it('lit les jalons et le total, accepte, et la mission est attribuée', async () => {
    await retenir()
    await post(`/api/missions/${ID}/devis`, DEVIS)
    const page = await monter()
    await attendre(page, '[data-test="accepter-devis"]')
    expect(page.findAll('[data-test="jalon-devis"]')).toHaveLength(2)
    expect(page.get('[data-test="jalon-devis"]').text()).toContain('10 jours après le démarrage')
    expect(page.get('[data-test="total-devis"]').text()).toMatch(/400\s?000/)
    expect(page.get('[data-test="devis-propose"]').text()).toContain('Hébergement non compris.')
    expect(page.find('[data-test="suivi"]').exists()).toBe(false)

    await page.get('[data-test="accepter-devis"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut"]').text()).toBe('Attribuée'))
    expect(page.find('[data-test="devis-propose"]').exists()).toBe(false)
    expect(page.find('[data-test="propositions"]').exists()).toBe(false)
    await attendre(page, '[data-test="form-demarrer"]')
  })
})
