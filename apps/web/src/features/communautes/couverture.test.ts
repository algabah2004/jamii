import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'

// jsdom n'envoie pas de FormData : l'envoi de l'image est simulé, le reste passe par les vrais écrans et les mocks.
vi.mock('../../components/texte/fichiers', async (original) => {
  const vrai = await original<typeof import('../../components/texte/fichiers')>()
  return {
    ...vrai,
    envoyerFichier: async (f: File) => {
      const id = crypto.randomUUID()
      return { ok: true, piece: { id, nom: f.name, type: f.type, sorte: f.type.startsWith('image/') ? 'image' : 'document', taille: f.size, url: `/api/fichiers/${id}` } }
    },
  }
})

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

async function choisirImage(page: Awaited<ReturnType<typeof ouvrir>>['page'], nom = 'couverture.png', type = 'image/png') {
  const entree = page.get('[data-test="fichier-couverture"]')
  Object.defineProperty(entree.element, 'files', { value: [new File(['x'], nom, { type })], configurable: true })
  await entree.trigger('change')
}

describe('la couverture d’une communauté', () => {
  it('sur la liste : l’image si elle existe, le motif sinon', async () => {
    const { page } = await ouvrir('/communautes')
    await vi.waitFor(() => expect(page.findAll('[data-test="communautes"] > li').length).toBeGreaterThan(1))
    expect(page.find('[data-test="couverture-image"]').exists()).toBe(false)
    expect(page.findAll('[data-test="communautes"] > li .texture').length).toBe(page.findAll('[data-test="communautes"] > li').length)
  })

  it('un modérateur met une image depuis la bannière, puis revient au motif', async () => {
    const recus: unknown[] = []
    serveur.events.on('request:start', async ({ request }) => {
      if (request.method === 'PUT' && request.url.endsWith('/couverture')) recus.push(await request.clone().json())
    })
    try {
      const { page } = await ouvrir('/communautes/flutter')
      await vi.waitFor(() => expect(page.find('[data-test="choix-couverture"]').exists()).toBe(true))
      expect(page.find('[data-test="banniere-photo"]').exists()).toBe(false)
      await choisirImage(page)
      await vi.waitFor(() => expect(page.find('[data-test="banniere-photo"]').exists()).toBe(true))
      expect(page.get('[data-test="banniere-photo"]').attributes('src')).toMatch(/^\/api\/fichiers\//)
      await page.get('[data-test="garder-motif"]').trigger('click')
      await vi.waitFor(() => expect(page.find('[data-test="banniere-photo"]').exists()).toBe(false))
      expect(recus).toHaveLength(2)
      expect(recus[1]).toEqual({ fichierId: null })
    } finally {
      serveur.events.removeAllListeners()
    }
  })

  it('un membre qui ne modère pas ne voit pas le choix de couverture', async () => {
    const { page } = await ouvrir('/communautes/ia')
    await vi.waitFor(() => expect(page.find('[data-test="banniere"]').exists()).toBe(true))
    expect(page.find('[data-test="choix-couverture"]').exists()).toBe(false)
  })

  it('à la création, l’image choisie part avec la communauté ; un fichier qui n’est pas une image est refusé', async () => {
    let recu: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/communautes', async ({ request }) => {
        recu = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'n', slug: 'devs-du-sahel', nom: 'Devs du Sahel', description: 'Des devs.', theme: 'web', pays: null, nombreMembres: 1, questionsOuvertes: 0, membre: true, moderateur: true, couverture: null }, { status: 201 })
      }),
    )
    const { page } = await ouvrir('/communautes/nouvelle')
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    await page.get('[data-test="champ-nom"]').setValue('Devs du Sahel')
    await page.get('[data-test="champ-nom"]').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.find('[data-test="theme-web"]').exists()).toBe(true))
    await page.get('[data-test="theme-web"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="champ-description"]').exists()).toBe(true), { timeout: 3000 })
    await page.get('[data-test="champ-description"]').setValue('Des développeurs de Niamey à Nouakchott.')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="choix-couverture"]').exists()).toBe(true))
    expect(page.text()).toContain('La communauté garde son motif')

    await choisirImage(page, 'notes.pdf', 'application/pdf')
    await vi.waitFor(() => expect(page.get('[data-test="choix-couverture"] [role="alert"]').text()).toContain('Choisis une image'))
    await choisirImage(page)
    await vi.waitFor(() => expect(page.find('[data-test="banniere-photo"]').exists()).toBe(true))
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(recu.couvertureId).toBeTruthy())
  })
})
