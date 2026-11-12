import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { planDemo } from '../../mocks/demandes'
import { demandeValide, fonctionnalitesParPriorite, jalonsAvecTaches } from './demandes'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

describe('le plan proposé', () => {
  it('range les fonctionnalités par priorité, sans groupe vide', () => {
    const plan = planDemo()
    plan.cahierDesCharges.fonctionnalites = plan.cahierDesCharges.fonctionnalites.filter((f) => f.priorite !== 'utile')
    expect(fonctionnalitesParPriorite(plan).map((g) => g.priorite)).toEqual(['indispensable', 'plus_tard'])
  })

  it('donne à chaque jalon ses tâches, dans l’ordre des semaines', () => {
    const plan = planDemo(8)
    plan.jalons.reverse()
    plan.taches = [{ titre: 'A', jalon: 2, competences: [] }, { titre: 'perdue', jalon: 9, competences: [] }]
    const jalons = jalonsAvecTaches(plan)
    expect(jalons.map((j) => j.semaine)).toEqual([2, 5, 8])
    expect(jalons[0]!.taches.map((t) => t.titre)).toEqual(['A'])
    expect(jalons.flatMap((j) => j.taches).map((t) => t.titre)).not.toContain('perdue')
  })

  it('refuse une demande trop courte', () => {
    expect(demandeValide({ titre: 'Ok', description: 'Une description assez longue ici.' })).toBe(false)
    expect(demandeValide({ titre: 'Une boutique', description: 'Trop court' })).toBe(false)
    expect(demandeValide({ titre: 'Une boutique', description: 'Je vends des tissus et je veux un catalogue.' })).toBe(true)
  })
})

describe('Une PME décrit son besoin', () => {
  it('reprend un exemple, obtient un plan, puis lance le projet', async () => {
    const { page, routeur } = await ouvrir('/demandes/nouvelle')
    expect(page.get('[data-test="envoyer"]').attributes('disabled')).toBeDefined()
    await page.get('[data-test="exemple"]').trigger('click')
    await page.get('[data-test="demande"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('demande'))
    await vi.waitFor(() => expect(page.find('[data-test="plan-propose"]').exists()).toBe(true), { timeout: 4000 })
    expect(page.findAll('[data-test="priorite"]')).toHaveLength(3)
    expect(page.findAll('[data-test="jalons"] > li')).toHaveLength(3)
    expect(page.text()).toContain('Designer d’interface')
    await page.get('[data-test="lancer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="projet-cree"]').exists()).toBe(true))
  })

  it('dit clairement quand le plan est un modèle de repli', async () => {
    serveur.use(
      http.get('/api/demandes/:id', ({ params }) =>
        HttpResponse.json({
          id: params.id, titre: 'Une boutique', description: 'Je vends des tissus et je veux un catalogue.',
          dureeSemaines: null, creeLe: new Date().toISOString(),
          plan: { statut: 'terminee', origine: 'repli', resultat: planDemo() },
        }),
      ),
    )
    const { page } = await ouvrir('/demandes/0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b')
    await vi.waitFor(() => expect(page.find('[data-test="plan-repli"]').exists()).toBe(true))
  })

  it('affiche le texte de la PME sans jamais l’exécuter', async () => {
    serveur.use(
      http.get('/api/demandes/:id', ({ params }) =>
        HttpResponse.json({
          id: params.id, titre: 'Piège', description: '<img src=x onerror="alert(1)"> une description longue',
          dureeSemaines: null, creeLe: new Date().toISOString(), plan: { statut: 'echouee', erreurCode: 'delai_depasse' },
        }),
      ),
    )
    const { page } = await ouvrir('/demandes/0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b')
    await vi.waitFor(() => expect(page.find('[data-test="plan-echec"]').exists()).toBe(true))
    expect(page.find('main img').exists()).toBe(false)
    expect(page.text()).toContain('<img src=x onerror="alert(1)">')
  })

  it('signale qu’une autre équipe a déjà pris la demande', async () => {
    serveur.use(http.post('/api/projets', () => HttpResponse.json({ code: 'demande_deja_prise' }, { status: 409 })))
    const { page } = await ouvrir('/demandes/nouvelle')
    await page.get('[data-test="exemple"]').trigger('click')
    await page.get('[data-test="demande"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="lancer"]').exists()).toBe(true), { timeout: 4000 })
    await page.get('[data-test="lancer"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Une équipe a déjà lancé un projet sur cette demande'))
  })
})
