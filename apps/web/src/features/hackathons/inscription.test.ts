import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierHackathon, reinitialiserHackathons } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'

/** Le hackathon publié des maquettes, inscriptions ouvertes, vu par Kofi qui n'y est pas encore inscrit. */
const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'

beforeEach(() => reinitialiserHackathons())

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

describe('s’inscrire à un hackathon', () => {
  it('s’inscrit seul sans chercher d’équipe, puis peut encore créer la sienne', async () => {
    let envoye: unknown = null
    serveur.use(
      http.post('/api/hackathons/:id/inscriptions', async ({ request }) => {
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="sinscrire"]')
    await page.get('[data-test="cherche-equipe"]').setValue(false)
    await page.get('[data-test="sinscrire"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="inscription-seul"]').exists()).toBe(false))
    expect(envoye).toEqual({ chercheEquipe: false })
    expect(page.find('[data-test="form-equipe"]').exists()).toBe(true)
  })

  it('dit quand les inscriptions sont fermées', async () => {
    serveur.use(http.post('/api/hackathons/:id/inscriptions', () => HttpResponse.json({ code: 'delai_depasse' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="sinscrire"]')
    await page.get('[data-test="sinscrire"]').trigger('click')
    await attendre(page, '[data-test="erreur-inscription"]')
    expect(page.get('[data-test="erreur-inscription"]').text()).toContain('fermées')
  })
})

describe('former son équipe', () => {
  it('crée l’équipe, puis invite une personne qui cherche une équipe', async () => {
    const page = await monter()
    await attendre(page, '[data-test="aucune-equipe"]')
    const nom = page.get('[data-test="form-equipe"] input')
    await nom.setValue('T')
    expect(page.get('[data-test="creer-equipe"]').attributes('disabled')).toBeDefined()
    await nom.setValue('Teranga')
    await page.get('[data-test="form-equipe"]').trigger('submit')

    await attendre(page, '[data-test="ma-equipe"]')
    expect(page.find('[data-test="participer"]').exists()).toBe(false)
    expect(page.get('[data-test="equipe"]').text()).toContain('Teranga')
    expect(page.get('[data-test="equipe"]').text()).toContain('1 sur 5')
    await attendre(page, '[data-test="seul"]')
    const seuls = page.findAll('[data-test="seul"]')
    expect(seuls).toHaveLength(2)
    expect(seuls[0]!.text()).toContain('Fatou Ndiaye')
    expect(seuls[0]!.text()).toContain('flutter, ux-design')

    await seuls[0]!.get('[data-test="inviter"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="equipe"]').text()).toContain('2 sur 5'))
    expect(page.get('[data-test="equipe"]').text()).toContain('Fatou Ndiaye')
    await vi.waitFor(() => expect(page.findAll('[data-test="seul"]')).toHaveLength(1))
  })

  it('pas d’équipes à montrer sur un brouillon', async () => {
    modifierHackathon(ID, (h) => {
      h.statut = 'brouillon'
      h.moi = { role: 'organisateur', actions: ['modifier', 'publier'] }
    })
    const page = await monter()
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="equipes"]').exists()).toBe(false)
    expect(page.find('[data-test="participer"]').exists()).toBe(false)
  })
})
