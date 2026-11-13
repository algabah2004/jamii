import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN, ID_DAKAR } from '../../mocks/cercles'
import { avancement, avantTirage, ordreDePassage, positionsAnneau, pourcentage, titreDuTour, type MembreCercle } from './cercles'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

const m = (id: string, rang: number | null, idee: string | null = null): MembreCercle => ({
  personne: { id, nom: id },
  rang,
  aDeposeIdee: !!idee,
  idee: idee ? { titre: idee, description: '…' } : null,
  rejointLe: '2026-11-01T00:00:00Z',
})

describe('les règles du cercle', () => {
  it("range les membres par rang, ceux sans rang à la fin dans leur ordre d'arrivée", () => {
    const ordre = ordreDePassage({ membres: [m('C', null), m('B', 2), m('D', null), m('A', 1)] })
    expect(ordre.map((x) => x.personne.id)).toEqual(['A', 'B', 'C', 'D'])
  })

  it("pose le premier membre en haut de l'anneau, puis tourne dans le sens des aiguilles d'une montre", () => {
    const [haut, droite, bas, gauche] = positionsAnneau(4, 40)
    expect(haut).toEqual({ x: 50, y: 10 })
    expect(droite).toEqual({ x: 90, y: 50 })
    expect(bas).toEqual({ x: 50, y: 90 })
    expect(gauche).toEqual({ x: 10, y: 50 })
  })

  it("n'autorise le tirage que si le cercle est complet, que chacun a son idée, et une seule fois", () => {
    const quatre = [m('A', null, 'a'), m('B', null, 'b'), m('C', null, 'c'), m('D', null, 'd')]
    expect(avantTirage({ membres: quatre, taille: 4, tirage: null }).pret).toBe(true)
    expect(avantTirage({ membres: quatre.slice(0, 3), taille: 4, tirage: null })).toEqual({ manquants: 1, sansIdee: 0, pret: false })
    expect(avantTirage({ membres: [...quatre.slice(0, 3), m('D', null)], taille: 4, tirage: null }).sansIdee).toBe(1)
    expect(avantTirage({ membres: quatre, taille: 4, tirage: { effectueLe: '', parId: 'A' } }).pret).toBe(false)
  })

  it("donne au tour le titre de l'idée de son bénéficiaire, et rien si on ne la voit pas", () => {
    expect(titreDuTour({ membres: [m('A', 1, 'Covoiturage')] }, { beneficiaire: { id: 'A', nom: 'A' } })).toBe('Covoiturage')
    expect(titreDuTour({ membres: [m('A', 1)] }, { beneficiaire: { id: 'A', nom: 'A' } })).toBeNull()
  })

  it('compte le jour du tour, les jours restants (jamais négatifs) et les tâches livrées', () => {
    const maintenant = new Date('2026-11-13T12:00:00Z')
    const taches = [{ statut: 'livree' as const }, { statut: 'en_cours' as const }, { statut: 'a_faire' as const }]
    expect(avancement('2026-11-11T09:00:00Z', taches, maintenant)).toEqual({ jour: 3, joursRestants: 11, faites: 1, total: 3 })
    expect(avancement('2026-10-01T09:00:00Z', [], maintenant).joursRestants).toBe(0)
    expect(pourcentage({ faites: 7, total: 12 })).toBe(58)
    expect(pourcentage({ faites: 0, total: 0 })).toBe(0)
  })
})

describe('la page du cercle', () => {
  it("montre l'anneau, le tour en cours avec son avancement et son dépôt, et le point du tour", async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(page.find('[data-test="avancement"]').exists()).toBe(true))
    expect(page.findAll('.anneau__membre')).toHaveLength(6)
    expect(page.find('.anneau__membre--tour .anneau__nom').text()).toBe('Awa')
    expect(page.find('[data-test="tour"]').text()).toContain('Tour 1 · le projet de Awa')
    expect(page.find('[data-test="tour"] h2').text()).toBe('Appli de covoiturage étudiant')
    expect(page.find('[data-test="avancement"]').text()).toContain('7 tâches sur 12')
    expect(page.find('[data-test="depot"]').attributes('rel')).toContain('noopener')
    expect(page.find('[data-test="point"]').text()).toContain('Jour 3 du tour de Awa')
    expect(page.findAll('[data-test="ordre"] li')).toHaveLength(6)
  })

  it("cache les idées et les boutons du créateur à qui n'est pas membre", async () => {
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="attente-tirage"]').exists()).toBe(true))
    expect(page.find('[data-test="attente-tirage"]').text()).toContain('Il manque 2 membres')
    expect(page.find('[data-test="ordre"]').text()).toContain('Idée déposée')
    expect(page.find('[data-test="ordre"]').text()).not.toContain('Boutique en ligne')
    expect(page.find('[data-test="tirer"]').exists()).toBe(false)
  })

  it('dit que le cercle est introuvable, et propose de réessayer si le serveur ne répond pas', async () => {
    const { page } = await ouvrir('/cercles/a0c1e2c1-0000-4000-8000-000000000999')
    await vi.waitFor(() => expect(page.text()).toContain("Cette page n'existe pas."))

    serveur.use(http.get('/api/cercles/:id', () => HttpResponse.json({}, { status: 503 })))
    const autre = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(autre.page.find('[role="alert"]').exists()).toBe(true))
  })

  it("met en avant le tour en cours de mes cercles, avec ses membres et son avancement", async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.find('[data-test="vedette"]').text()).toContain('7 tâches sur 12'))
    const vedette = page.find('[data-test="vedette"]')
    expect(vedette.text()).toContain('Tour 1 · le projet de Awa')
    expect(vedette.text()).toContain('Appli de covoiturage étudiant')
    expect(vedette.attributes('href')).toBe(`/cercles/${ID_DAKAR}`)
    expect(vedette.findAll('.visages__un')).toHaveLength(6)
    // Mes cercles disent où ils en sont : le tour en cours, ou les membres qui manquent.
    expect(page.find('[data-test="cercles-mes"]').text()).toContain('Il manque 2 membres.')
  })

  it('sépare mes cercles de ceux qui cherchent des membres', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles-mes"] li')).toHaveLength(2))
    // Le seul cercle ouvert m'est recommandé : il n'apparaît qu'une fois, dans « Cercles qui ont besoin de toi ».
    expect(page.findAll('[data-test="cercles-pour-moi"] li')).toHaveLength(1)
    expect(page.find('[data-test="vide-ouverts"]').exists()).toBe(true)
    expect(page.find('nav a[href="/cercles"]').exists()).toBe(true)
  })
})

describe('du cercle vide au tirage', () => {
  it("crée un cercle, dépose son idée, puis tire l'ordre au sort : le premier tour commence", async () => {
    const { page, routeur } = await ouvrir('/cercles/nouveau')
    const champs = page.findAll('[data-test="nouveau-cercle"] input:not([type="radio"])')
    await champs[0]!.setValue('Cercle de Thiès')
    await champs[1]!.setValue('Thiès')
    await page.find('input[type="radio"][value="4"]').setValue(true)
    await page.find('[data-test="nouveau-cercle"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('cercle'))
    await vi.waitFor(() => expect(page.find('[data-test="attente-tirage"]').exists()).toBe(true))

    // Les 3 autres membres ont déjà déposé leur idée ; le tirage attend la mienne.
    expect(page.find('[data-test="tirer"]').attributes('disabled')).toBeDefined()
    await page.find('[data-test="ouvrir-idee"]').trigger('click')
    const form = page.find('[data-test="form-idee"]')
    await form.find('input').setValue('Appli de covoiturage étudiant')
    await form.find('textarea').setValue('Pour les étudiants de Thiès qui vont à Dakar chaque semaine.')
    await form.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="mon-idee"]').text()).toContain('Appli de covoiturage étudiant'))

    await page.find('[data-test="tirer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="tour"]').exists()).toBe(true))
    expect(page.findAll('[data-test="ordre"] li .ordre__numero').map((n) => n.text())).toEqual(['1', '2', '3', '4'])
  })

  it('rejoint un cercle ouvert : on voit alors les idées, mais pas le bouton du créateur', async () => {
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre"]').exists()).toBe(true))
    await page.find('[data-test="rejoindre"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('.anneau__membre')).toHaveLength(4))
    expect(page.find('[data-test="rejoindre"]').exists()).toBe(false)
    expect(page.find('[data-test="ordre"]').text()).toContain('Boutique en ligne')
    expect(page.find('[data-test="ouvrir-idee"]').exists()).toBe(true)
    expect(page.find('[data-test="tirer"]').exists()).toBe(false)
  })

  it("explique un refus de l'API au lieu d'échouer en silence", async () => {
    serveur.use(http.post('/api/cercles/:id/membres', () => HttpResponse.json({ code: 'cercle_complet' }, { status: 409 })))
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre"]').exists()).toBe(true))
    await page.find('[data-test="rejoindre"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[role="alert"]').text()).toBe('Le cercle est déjà complet.'))
  })
})

describe('livrer un tour', () => {
  it('demande confirmation, livre le tour et ouvre celui du membre suivant', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(page.find('[data-test="livrer"]').exists()).toBe(true))
    await page.find('[data-test="livrer"]').trigger('click')
    expect(page.find('[data-test="confirmer-livraison"]').text()).toContain('le tour de Kofi commence')
    await page.find('[data-test="confirmer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="livre"]').text()).toContain('Le projet de Awa est livré.'))
    expect(page.find('[data-test="tour"]').text()).toContain('Tour 2 · le projet de Kofi')
    expect(page.find('.ordre__ligne--livre').text()).toContain('Awa Diop')
  })

  it("cache « Livrer » à un membre qui n'est ni le bénéficiaire ni le créateur", async () => {
    const original = await (await fetch(new URL(`/api/cercles/${ID_DAKAR}`, window.location.origin))).json()
    serveur.use(
      http.get('/api/cercles/:id', () =>
        HttpResponse.json({ ...original, moi: { estCreateur: false, estMembre: true }, tours: original.tours.map((x: { numero: number; beneficiaire: unknown }, i: number) => (i === 0 ? { ...x, beneficiaire: original.membres[1].personne } : x)) }),
      ),
    )
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(page.find('[data-test="tour"]').exists()).toBe(true))
    expect(page.find('[data-test="livrer"]').exists()).toBe(false)
  })
})
