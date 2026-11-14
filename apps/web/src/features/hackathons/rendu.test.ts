import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierHackathon, reinitialiserHackathons } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
const EQUIPE = 'e1000000-0000-4000-8000-000000000001'

/** Le hackathon des maquettes, commencé, vu par Kofi, membre d'une équipe. */
beforeEach(() => {
  reinitialiserHackathons()
  modifierHackathon(ID, (h) => {
    h.statut = 'en_cours'
    h.moi = { role: 'participant', equipeId: EQUIPE, actions: ['rendre'] }
  })
})

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

async function remplir(page: ReturnType<typeof mount>, lien: string) {
  const form = page.get('[data-test="form-rendu"]')
  const [titre, produit, depot] = form.findAll('input')
  const [description, reponse] = form.findAll('textarea')
  await titre!.setValue('Carnet de caisse')
  await description!.setValue('Un carnet de caisse sur téléphone pour les boutiques du marché Sandaga.')
  await produit!.setValue(lien)
  await depot!.setValue('https://github.com/teranga/carnet')
  await reponse!.setValue('Savoir chaque soir ce qui a été vendu.')
}

describe('rendre le projet de mon équipe', () => {
  it('refuse un lien qui n’est pas en https, puis rend avec les réponses aux questions', async () => {
    let envoye: unknown = null
    serveur.use(
      http.put('/api/hackathons/:id/equipes/:equipeId/rendu', async ({ request }) => {
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-rendu"]')
    expect(page.get('[data-test="form-rendu"]').text()).toContain("Quel problème d'un commerçant résolvez-vous ?")
    await remplir(page, 'http://carnet.example')
    expect(page.get('[data-test="form-rendu"]').text()).toContain('https://')
    expect(page.get('[data-test="rendre"]').attributes('disabled')).toBeDefined()

    await remplir(page, 'https://carnet.example')
    await page.get('[data-test="form-rendu"]').trigger('submit')
    await attendre(page, '[data-test="rendu-enregistre"]')
    expect(envoye).toEqual({
      titre: 'Carnet de caisse',
      description: 'Un carnet de caisse sur téléphone pour les boutiques du marché Sandaga.',
      lienProduit: 'https://carnet.example',
      depot: 'https://github.com/teranga/carnet',
      video: null,
      reponses: ['Savoir chaque soir ce qui a été vendu.'],
    })
    expect(page.get('[data-test="etat-lien"]').text()).toContain('répond')
    expect(page.get('[data-test="rendre"]').text()).toBe('Enregistrer les changements')
  })

  it('prévient quand le lien du produit ne répond pas, et renvoie la version pour modifier', async () => {
    const page = await monter()
    await attendre(page, '[data-test="form-rendu"]')
    await remplir(page, 'https://carnet.invalid')
    await page.get('[data-test="form-rendu"]').trigger('submit')
    await attendre(page, '[data-test="etat-lien"]')
    expect(page.get('[data-test="etat-lien"]').attributes('data-repond')).toBe('false')
    expect(page.get('[data-test="etat-lien"]').text()).toContain('à corriger')

    let envoye: { version?: number } | null = null
    serveur.use(
      http.put('/api/hackathons/:id/equipes/:equipeId/rendu', async ({ request }) => {
        envoye = (await request.clone().json()) as { version?: number }
        return undefined
      }),
    )
    await page.get('[data-test="form-rendu"] input[type="url"]').setValue('https://carnet.example')
    await page.get('[data-test="form-rendu"]').trigger('submit')
    await vi.waitFor(() => expect(page.get('[data-test="etat-lien"]').attributes('data-repond')).toBe('true'))
    expect(envoye!.version).toBe(1)
  })

  it('dit quand l’heure limite est passée', async () => {
    serveur.use(http.put('/api/hackathons/:id/equipes/:equipeId/rendu', () => HttpResponse.json({ code: 'delai_depasse' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-rendu"]')
    await remplir(page, 'https://carnet.example')
    await page.get('[data-test="form-rendu"]').trigger('submit')
    await attendre(page, '[data-test="erreur-rendu"]')
    expect(page.get('[data-test="erreur-rendu"]').text()).toContain('heure limite')
  })

  it('pendant l’évaluation, l’équipe relit son rendu sans pouvoir le changer', async () => {
    modifierHackathon(ID, (h) => {
      h.statut = 'evaluation'
      h.moi = { role: 'participant', equipeId: EQUIPE, actions: [] }
    })
    const page = await monter()
    await attendre(page, '[data-test="aucun-rendu"]')
    expect(page.find('[data-test="form-rendu"]').exists()).toBe(false)
  })
})
