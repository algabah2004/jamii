import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierHackathon, reinitialiserHackathons, type Hackathon } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'

beforeEach(() => reinitialiserHackathons())

const role = (statut: Hackathon['statut'], moi: Hackathon['moi']) => modifierHackathon(ID, (h) => Object.assign(h, { statut, moi }))

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routes = [
    { path: '/hackathons/:id', component: HackathonPage },
    { path: '/:autre(.*)*', component: { render: () => null } },
  ]
  const routeur = createRouter({ history: createMemoryHistory(), routes })
  await routeur.push(`/hackathons/${ID}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

describe('la discussion d’un hackathon', () => {
  it('l’organisatrice publie une annonce, la retire, et ouvre l’espace d’échange', async () => {
    role('en_cours', { role: 'organisateur', actions: [] })
    const page = await monter()
    await attendre(page, '[data-test="nouvelle-annonce"]')
    await page.get('[data-test="nouvelle-annonce"]').trigger('click')
    await attendre(page, '[data-test="form-annonce"]')
    expect(page.find('[data-test="aucune-annonce"]').exists()).toBe(true)
    expect(page.get('[data-test="publier-annonce"]').attributes('disabled')).toBeDefined()

    await page.get('#annonce-texte').setValue('Le dépôt des rendus ouvre demain à 9 h.')
    await page.get('[data-test="form-annonce"]').trigger('submit')
    await attendre(page, '[data-test="annonce"]')
    expect(page.get('[data-test="annonce"]').text()).toContain('Le dépôt des rendus ouvre demain à 9 h.')
    // Publiée : le formulaire se replie.
    expect(page.find('[data-test="form-annonce"]').exists()).toBe(false)
    expect(page.get('[data-test="annonce-nouvelle"]').text()).toBe('Nouveau')
    expect(page.get('[data-test="nouvelles-annonces"]').text()).toContain('1')

    // Retirer ne s'annule pas : on demande confirmation, et « Garder » ne retire rien.
    await page.get('[data-test="retirer-annonce"]').trigger('click')
    await page.get('[data-test="garder-annonce"]').trigger('click')
    expect(page.findAll('[data-test="annonce"]')).toHaveLength(1)
    await page.get('[data-test="retirer-annonce"]').trigger('click')
    await page.get('[data-test="confirmer-retrait"]').trigger('click')
    await attendre(page, '[data-test="aucune-annonce"]')

    await page.get('[data-test="ouvrir-espace"]').trigger('click')
    await attendre(page, '[data-test="poser-question"]')
    expect(page.get('[data-test="poser-question"]').attributes('href')).toMatch(/^\/communautes\/[a-z0-9-]+\?onglet=ouvertes$/)
  })

  it('un participant lit les annonces, voit les questions ouvertes et sait où poser la sienne', async () => {
    serveur.use(
      http.get('/api/hackathons/:id/discussion', () =>
        HttpResponse.json({
          communaute: 'dakar-cloud-hack',
          annonces: [{ id: 'a1', auteur: { id: 'o1', nom: 'Awa Diop', avatarUrl: null }, texte: 'Bienvenue à toutes et à tous !', publieeLe: '2026-11-15T08:00:00Z' }],
        }),
      ),
      http.get('/api/communautes/:slug/publications', () =>
        HttpResponse.json({ elements: [{ id: 'q1', titre: 'Peut-on utiliser une API externe ?', nombreReponses: 2 }], curseurSuivant: null }),
      ),
    )
    role('en_cours', { role: 'participant', actions: [] })
    const page = await monter()
    await attendre(page, '[data-test="questions"]')
    expect(page.get('[data-test="annonce"]').text()).toContain('Bienvenue')
    expect(page.find('[data-test="form-annonce"]').exists()).toBe(false)
    expect(page.find('[data-test="retirer-annonce"]').exists()).toBe(false)
    expect(page.get('[data-test="questions"]').text()).toContain('Peut-on utiliser une API externe ?')
    expect(page.get('[data-test="questions"]').text()).toContain('2 réponses')
    expect(page.get('[data-test="salons"]').attributes('href')).toBe('/communautes/dakar-cloud-hack?salon=general')
    expect(page.get('[data-test="aller-discussion"]').attributes('href')).toBe('#discussion')
  })

  it('range la page en onglets, au clic comme au clavier', async () => {
    role('en_cours', { role: 'participant', actions: [] })
    const page = await monter()
    await attendre(page, '[data-test="onglet-discussion"]')
    const choisi = (o: string) => page.get(`[data-test="onglet-${o}"]`).attributes('aria-selected')
    expect(choisi('presentation')).toBe('true')
    expect(page.find('[data-test="onglet-gestion"]').exists()).toBe(false)
    await page.get('[data-test="onglet-discussion"]').trigger('click')
    await vi.waitFor(() => expect(choisi('discussion')).toBe('true'))
    expect(page.get('#panneau-discussion').attributes('style') ?? '').not.toContain('display: none')
    expect(page.get('#panneau-presentation').attributes('style')).toContain('display: none')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    await vi.waitFor(() => expect(choisi('equipes')).toBe('true'))
  })

  it('dit quand l’organisation n’a pas encore ouvert d’espace', async () => {
    role('publie', { role: 'visiteur', actions: [] })
    const page = await monter()
    await attendre(page, '[data-test="pas-d-espace"]')
    expect(page.find('[data-test="ouvrir-espace"]').exists()).toBe(false)
  })
})
