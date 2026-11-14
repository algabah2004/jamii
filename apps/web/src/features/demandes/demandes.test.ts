import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { orientationDemo, planDemo, RECOMMANDES } from '../../mocks/demandes'
import { demandeValide, etatAnalyse, fonctionnalitesParPriorite, jalonsAvecTaches, type Demande } from './demandes'

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

const ID = '0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b'
const base = { id: ID, titre: 'Nos dossiers', description: 'Le cabinet perd ses dossiers entre associés.', dureeSemaines: null, creeLe: '2026-11-14T10:00:00Z' }

describe('l’orientation d’un besoin', () => {
  it('est prête même sans plan, quand Systalink suffit', () => {
    const sansDev = { ...orientationDemo(), developpeursNecessaires: false, competences: [], plan: null }
    const d = { ...base, plan: { statut: 'terminee' }, orientation: { statut: 'terminee', origine: 'ia', resultat: sansDev } } as Demande
    expect(etatAnalyse(d)).toBe('pret')
    expect(etatAnalyse({ ...d, orientation: { statut: 'en_cours' } })).toBe('attente')
    expect(etatAnalyse({ ...d, orientation: { statut: 'echouee', erreurCode: 'delai_depasse' } })).toBe('echec')
  })

  it('envoie le secteur et la ville, et jamais la case Systalink cochée d’avance', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page, routeur } = await ouvrir('/besoin')
    expect(routeur.currentRoute.value.name).toBe('nouvelle-demande')
    expect(page.find('[data-test="prudence"]').exists()).toBe(true)
    expect((page.get('[data-test="offres"] input').element as HTMLInputElement).checked).toBe(false)
    await page.get('[data-test="exemple"]').trigger('click')
    await page.get('[data-test="secteur"][value="juridique"]').setValue(true)
    await page.get('[data-test="demande"]').trigger('submit')
    await vi.waitFor(() => expect(corps.titre).toBeTruthy())
    expect(corps).toMatchObject({ secteur: 'juridique', offresSystalink: false })
    expect(corps).not.toHaveProperty('ville')
  })

  it('montre les services Systalink, les développeurs et leurs raisons, puis enregistre l’accord', async () => {
    const { page } = await ouvrir('/demandes/nouvelle')
    await page.get('[data-test="exemple"]').trigger('click')
    await page.get('[data-test="demande"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true), { timeout: 4000 })
    const services = page.findAll('[data-test="service"]')
    expect(services).toHaveLength(2)
    expect(services[0]!.get('a').attributes()).toMatchObject({ href: 'https://systalink.com', rel: 'noopener noreferrer' })
    const recommandes = page.findAll('[data-test="recommande"]')
    expect(recommandes).toHaveLength(3)
    expect(recommandes[0]!.text()).toContain('React, confirmé (3 dépôts)')
    expect(recommandes[0]!.get('a').attributes('href')).toBe('/messages/d2e00000-0000-4000-8000-000000000001')
    expect(page.find('[data-test="former-cercle"]').exists()).toBe(true)

    const caseOffres = page.get('[data-test="offres-resultat"] input')
    expect((caseOffres.element as HTMLInputElement).checked).toBe(false)
    await caseOffres.setValue(true)
    await vi.waitFor(() => expect((caseOffres.element as HTMLInputElement).checked).toBe(true))
  })

  it('n’affiche ni développeurs ni plan quand Systalink suffit, et dit quand l’IA était absente', async () => {
    const sansDev = { ...orientationDemo(), developpeursNecessaires: false, competences: [], plan: null }
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, plan: { statut: 'terminee', origine: 'repli' }, orientation: { statut: 'terminee', origine: 'repli', resultat: sansDev }, recommandations: [] }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true))
    expect(page.text()).toContain('Analyse automatique indisponible')
    expect(page.find('[data-test="developpeurs"]').exists()).toBe(false)
    expect(page.find('[data-test="lancer"]').exists()).toBe(false)
    expect(page.find('[data-test="plan-echec"]').exists()).toBe(false)
  })
})

describe('du besoin au cercle', () => {
  it('« Former un cercle avec eux » ouvre la création avec le titre du besoin, sans le mettre dans l’adresse', async () => {
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, ville: 'Dakar', plan: { statut: 'terminee', origine: 'ia', resultat: planDemo() }, orientation: { statut: 'terminee', origine: 'ia', resultat: orientationDemo() }, recommandations: RECOMMANDES }),
      ),
    )
    const { page, routeur } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="former-cercle"]').exists()).toBe(true))
    await page.get('[data-test="former-cercle"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('nouveau-cercle'))
    expect(routeur.currentRoute.value.fullPath).toBe('/cercles/nouveau')
    await vi.waitFor(() => expect(page.find('[data-test="nouveau-cercle"]').exists()).toBe(true))
    const champs = page.findAll('[data-test="nouveau-cercle"] input:not([type="radio"])')
    expect((champs[0]!.element as HTMLInputElement).value).toBe('Nos dossiers')
    expect((champs[1]!.element as HTMLInputElement).value).toBe('Dakar')
  })
})
