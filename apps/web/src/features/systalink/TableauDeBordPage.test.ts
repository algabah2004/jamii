import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import TableauDeBordPage from './TableauDeBordPage.vue'
import { tableauDemo } from '../../mocks/handlers'

async function monter(adresse = '/systalink') {
  // Mode composition : la langue est une ref à l'exécution (le type de createI18n ne le dit pas).
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(TableauDeBordPage, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('[role="status"]').exists()).toBe(false))
  return { page, routeur }
}

describe('Tableau de bord Systalink', () => {
  it('met en avant les projets sur DataCloud et leur part parmi les projets en ligne', async () => {
    const { page } = await monter()
    const bloc = page.get('[data-test="datacloud"]').text()
    expect(bloc).toContain('7')
    expect(bloc).toContain('78 % des projets en ligne sont hébergés sur DataCloud')
  })

  it('affiche le taux de chaque service, du plus proposé au moins proposé', async () => {
    const { page } = await monter()
    const lignes = page.findAll('[data-test="moments"] tbody tr').map((l) => l.text())
    expect(lignes[0]).toMatch(/DataCloud.*12.*7.*58 %/)
    expect(lignes.map((l) => l.split(/\d/)[0])).toEqual(['DataCloud', 'Yokk', 'Orion'])
  })

  it('nomme les pays dans la langue de l’interface et regroupe les petits pays', async () => {
    const { page } = await monter()
    const pays = page.get('[data-test="pays"]').text()
    expect(pays).toContain('Sénégal')
    expect(pays).toContain('Autres pays')
  })

  it('montre les cercles actifs, les tours livrés et le pays de leurs membres', async () => {
    const { page } = await monter()
    const bloc = page.get('[data-test="cercles"]').text()
    expect(bloc).toContain('Cercles actifs')
    expect(bloc).toContain('1 cercle terminé')
    expect(bloc).toMatch(/Tours livrés\s*7\s*\+4 sur la période/)
    const pays = page.findAll('[data-test="pays-cercles"] li').map((l) => l.text())
    expect(pays).toEqual(['Sénégal9', 'Côte d’Ivoire6', 'Autres pays7'])
  })

  it("ne montre pas de répartition des membres tant qu'aucun cercle n'a de membre", async () => {
    serveur.use(
      http.get('/api/systalink/tableau-de-bord', () =>
        HttpResponse.json({
          ...tableauDemo,
          cercles: { actifs: 0, termines: 0, toursLivres: 0, nouveauxToursLivres: 0, membres: { total: 0, parPays: [], autres: 0 } },
        }),
      ),
    )
    const { page } = await monter()
    expect(page.get('[data-test="cercles"]').text()).toContain('aucun cercle terminé')
    expect(page.find('[data-test="pays-cercles"]').exists()).toBe(false)
  })

  it('demande la période choisie et la garde dans l’adresse', async () => {
    let demandee: string | null = null
    serveur.use(
      http.get('/api/systalink/tableau-de-bord', ({ request }) => {
        demandee = new URL(request.url).searchParams.get('periode')
        return HttpResponse.json(tableauDemo)
      }),
    )
    const { page, routeur } = await monter()
    expect(demandee).toBe('mois')
    await page.findAll('.periodes button')[0]!.trigger('click')
    await vi.waitFor(() => expect(demandee).toBe('semaine'))
    expect(routeur.currentRoute.value.query.periode).toBe('semaine')
  })

  it("n'invente pas de taux quand aucun projet n'est en ligne", async () => {
    serveur.use(
      http.get('/api/systalink/tableau-de-bord', () =>
        HttpResponse.json({ ...tableauDemo, projets: { ...tableauDemo.projets, enLigne: 0, surDatacloud: 0 }, moments: [] }),
      ),
    )
    const { page } = await monter()
    expect(page.get('[data-test="datacloud"]').text()).not.toContain('%')
    expect(page.text()).toContain('Aucun service proposé sur cette période.')
  })

  it("propose de réessayer quand l'API ne répond pas", async () => {
    serveur.use(http.get('/api/systalink/tableau-de-bord', () => new HttpResponse(null, { status: 503 })))
    const { page } = await monter()
    expect(page.get('[role="alert"]').text()).toContain('Réessayer')
  })
})
