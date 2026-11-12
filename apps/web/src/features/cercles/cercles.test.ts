import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { joursRestants, ordreDePassage, positionsAnneau, pourcentage, type MembreCercle } from './cercles'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

const m = (nom: string, ordre: number | null) => ({ nom, ordre }) as MembreCercle

describe('les règles du cercle', () => {
  it("range les membres dans l'ordre de passage, ceux sans place à la fin dans leur ordre d'arrivée", () => {
    const ordre = ordreDePassage({ membres: [m('C', null), m('B', 2), m('D', null), m('A', 1)] })
    expect(ordre.map((x) => x.nom)).toEqual(['A', 'B', 'C', 'D'])
  })

  it("pose le premier membre en haut de l'anneau, puis tourne dans le sens des aiguilles d'une montre", () => {
    const [haut, droite, bas, gauche] = positionsAnneau(4, 40)
    expect(haut).toEqual({ x: 50, y: 10 })
    expect(droite).toEqual({ x: 90, y: 50 })
    expect(bas).toEqual({ x: 50, y: 90 })
    expect(gauche).toEqual({ x: 10, y: 50 })
  })

  it('compte les jours restants sans jamais passer sous zéro', () => {
    const maintenant = new Date('2026-11-12T12:00:00Z')
    expect(joursRestants({ fin: '2026-11-15T13:00:00Z' }, maintenant)).toBe(3)
    expect(joursRestants({ fin: '2026-11-10T12:00:00Z' }, maintenant)).toBe(0)
  })

  it("donne l'avancement en pourcentage, à 0 quand le tour n'a pas encore de tâche", () => {
    expect(pourcentage({ faites: 7, total: 12 })).toBe(58)
    expect(pourcentage({ faites: 0, total: 0 })).toBe(0)
  })
})

describe('la page du cercle', () => {
  it("montre l'anneau, le tour en cours avec son dépôt, et le message du Griot", async () => {
    const { page } = await ouvrir('/cercles/cercle-dakar')
    await vi.waitFor(() => expect(page.find('[data-test="anneau"]').exists()).toBe(true))
    expect(page.findAll('.anneau__membre')).toHaveLength(6)
    expect(page.find('.anneau__membre--tour .anneau__nom').text()).toBe('Awa')
    expect(page.find('[data-test="tour"]').text()).toContain('Tour 1 · le projet de Awa')
    expect(page.find('[data-test="depot"]').attributes('rel')).toContain('noopener')
    expect(page.find('[data-test="griot"]').text()).toContain('7 tâches sur 12')
    expect(page.findAll('[data-test="ordre"] li')).toHaveLength(6)
  })

  it("invite à compléter le cercle tant que le tirage n'est pas fait", async () => {
    const { page } = await ouvrir('/cercles/cercle-abidjan')
    await vi.waitFor(() => expect(page.find('[data-test="attente-tirage"]').exists()).toBe(true))
    expect(page.find('[data-test="attente-tirage"]').text()).toContain('Il manque 2 membres')
    expect(page.find('[data-test="griot"]').exists()).toBe(false)
  })

  it('dit que le cercle est introuvable, et propose de réessayer si le serveur ne répond pas', async () => {
    const { page } = await ouvrir('/cercles/inconnu')
    await vi.waitFor(() => expect(page.text()).toContain("Cette page n'existe pas."))

    serveur.use(http.get('/api/cercles/:id', () => HttpResponse.json({}, { status: 503 })))
    const autre = await ouvrir('/cercles/cercle-dakar')
    await vi.waitFor(() => expect(autre.page.find('[role="alert"]').exists()).toBe(true))
  })

  it('liste les cercles depuis le menu', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles"] li')).toHaveLength(2))
    expect(page.find('nav a[href="/cercles"]').exists()).toBe(true)
  })
})
