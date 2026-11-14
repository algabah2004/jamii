import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { reinitialiserMissions } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'
import MissionsPage from './MissionsPage.vue'
import NouvelleMissionPage from './NouvelleMissionPage.vue'
import { decimales, formaterMontant, lireCompetences, versMontant } from './missions'

beforeEach(() => reinitialiserMissions())

async function monter(chemin: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/missions', component: MissionsPage },
      { path: '/missions/nouvelle', component: NouvelleMissionPage },
      { path: '/missions/:id', component: MissionPage },
    ],
  })
  await routeur.push(chemin)
  await routeur.isReady()
  const page = mount(RouterView, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

describe('les montants et les compétences', () => {
  it('connaît les décimales de chaque devise', () => {
    expect(decimales('XOF')).toBe(0)
    expect(decimales('EUR')).toBe(2)
  })

  it('lit un montant tapé en unité mineure, et refuse le reste', () => {
    expect(versMontant('450 000', 'XOF')).toEqual({ centimes: 450000, devise: 'XOF' })
    expect(versMontant('1 250,50', 'EUR')).toEqual({ centimes: 125050, devise: 'EUR' })
    for (const mauvais of ['', 'abc', '-5', '12,5,3', '1e9']) expect(versMontant(mauvais, 'XOF')).toBeNull()
  })

  it('écrit un montant dans la langue de la personne', () => {
    expect(formaterMontant({ centimes: 450000, devise: 'XOF' }, 'fr')).toMatch(/450\s?000/)
    expect(formaterMontant({ centimes: 125050, devise: 'EUR' }, 'fr')).toMatch(/1\s?250,50/)
  })

  it('range les compétences : sans accent, sans doublon, au plus dix', () => {
    expect(lireCompetences('Vue, UX design, SPRING, vue, Données')).toEqual(['vue', 'ux-design', 'spring', 'donnees'])
    expect(lireCompetences(' , ;; ')).toEqual([])
    expect(lireCompetences(Array.from({ length: 15 }, (_, i) => `c${i}`).join(','))).toHaveLength(10)
  })
})

describe('la liste des missions', () => {
  it('montre les missions ouvertes avec leur budget, leur client et leurs propositions', async () => {
    const { page } = await monter('/missions')
    await attendre(page, '[data-test="liste"]')
    const carte = page.get('[data-test="mission"]')
    expect(carte.text()).toContain('Boutique en ligne pour un atelier de couture')
    expect(carte.text()).toContain('Ouverte aux propositions')
    expect(carte.text()).toMatch(/450\s?000/)
    expect(carte.text()).toContain('Awa Diop')
    expect(carte.text()).toContain('1 proposition')
  })

  it('propose « Publier une mission » et « Les miennes » à une personne connectée', async () => {
    const { page } = await monter('/missions')
    await attendre(page, '[data-test="liste"]')
    expect(page.find('[data-test="publier-une-mission"]').exists()).toBe(true)
    await page.get('[data-test="onglet-miennes"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="onglet-miennes"]').attributes('aria-pressed')).toBe('true'))
  })

  it('dit quand il n’y a aucune mission', async () => {
    serveur.use(http.get('/api/missions', () => HttpResponse.json({ elements: [], curseurSuivant: null })))
    const { page } = await monter('/missions')
    await attendre(page, '[data-test="aucune"]')
    expect(page.get('[data-test="aucune"]').text()).toContain('Aucune mission ouverte')
  })

  it('le dit quand l’API ne répond pas', async () => {
    serveur.use(http.get('/api/missions', () => HttpResponse.json({ code: 'erreur' }, { status: 500 })))
    const { page } = await monter('/missions')
    await attendre(page, '[data-test="indisponible"]')
  })
})

describe('publier une mission', () => {
  async function remplir(page: ReturnType<typeof mount>) {
    await page.get('input[type="text"]').setValue('Un site pour mon association')
    await page.get('textarea').setValue('Une page pour présenter nos actions et recevoir des dons par Mobile Money.')
    await page.findAll('input[type="text"]')[1]!.setValue('Vue, ux design')
  }

  it('garde le bouton éteint tant que le titre, la description et une compétence manquent', async () => {
    const { page } = await monter('/missions/nouvelle')
    expect(page.get('[data-test="creer"]').attributes('disabled')).toBeDefined()
    await remplir(page)
    expect(page.get('[data-test="creer"]').attributes('disabled')).toBeUndefined()
  })

  it('envoie le brouillon avec le budget en unité mineure et les compétences rangées, puis ouvre la mission', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/missions', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'abc', titre: 'x' }, { status: 201 })
      }),
    )
    const { page, routeur } = await monter('/missions/nouvelle')
    await remplir(page)
    await page.findAll('input[type="text"]')[2]!.setValue('450 000')
    await page.get('[data-test="devise"]').setValue('XOF')
    await page.get('[data-test="invitation"]').setValue(true)
    await page.get('[data-test="nouvelle-mission"]').trigger('submit')

    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/missions/abc'))
    expect(corps).toMatchObject({
      titre: 'Un site pour mon association',
      competences: ['vue', 'ux-design'],
      visibilite: 'invitation',
      budget: { centimes: 450000, devise: 'XOF' },
    })
    expect(corps).not.toHaveProperty('dateSouhaitee')
  })

  it('refuse un budget qui n’est pas un montant, sans rien envoyer', async () => {
    let envoye = false
    serveur.use(http.post('/api/missions', () => ((envoye = true), HttpResponse.json({}, { status: 201 }))))
    const { page } = await monter('/missions/nouvelle')
    await remplir(page)
    await page.findAll('input[type="text"]')[2]!.setValue('beaucoup')
    await page.get('[data-test="nouvelle-mission"]').trigger('submit')
    await vi.waitFor(() => expect(page.text()).toContain('pas un montant valide'))
    expect(envoye).toBe(false)
  })

  it('signale à côté de chaque champ ce que l’API a refusé', async () => {
    serveur.use(
      http.post('/api/missions', () =>
        HttpResponse.json(
          { code: 'validation', erreurs: [{ champ: 'titre', code: 'trop_court' }, { champ: 'competences', code: 'doublon' }] },
          { status: 400 },
        ),
      ),
    )
    const { page } = await monter('/missions/nouvelle')
    await remplir(page)
    await page.get('[data-test="nouvelle-mission"]').trigger('submit')
    await vi.waitFor(() => expect(page.text()).toContain('Le titre doit faire entre 3 et 120 caractères.'))
    expect(page.text()).toContain('de une à dix compétences')
  })

  it('dit qu’un écran est provisoire', async () => {
    const { page } = await monter('/missions/nouvelle')
    expect(page.get('[data-test="provisoire"]').text()).toContain('provisoire')
  })
})

describe('une mission', () => {
  const ID = 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91'

  it('montre l’annonce, et les actions du client seulement', async () => {
    const { page } = await monter(`/missions/${ID}`)
    await attendre(page, '[data-test="titre"]')
    expect(page.get('[data-test="titre"]').text()).toBe('Boutique en ligne pour un atelier de couture')
    expect(page.get('[data-test="budget"]').text()).toMatch(/450\s?000/)
    expect(page.find('[data-test="fermer"]').exists()).toBe(true)
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
  })

  it('publie un brouillon, puis le statut change', async () => {
    const { page } = await monter('/missions/nouvelle')
    await page.get('input[type="text"]').setValue('Un site pour mon association')
    await page.get('textarea').setValue('Une page pour présenter nos actions et recevoir des dons par Mobile Money.')
    await page.findAll('input[type="text"]')[1]!.setValue('vue')
    await page.get('[data-test="nouvelle-mission"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="brouillon"]').exists()).toBe(true), { timeout: 4000 })
    expect(page.get('[data-test="statut"]').text()).toBe('Brouillon')

    await page.get('[data-test="publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut"]').text()).toBe('Ouverte aux propositions'))
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
  })

  it('dit quand la mission a changé entre-temps (409)', async () => {
    serveur.use(http.post('/api/missions/:id/fermer', () => HttpResponse.json({ code: 'modification_concurrente' }, { status: 409 })))
    const { page } = await monter(`/missions/${ID}`)
    await attendre(page, '[data-test="fermer"]')
    await page.get('[data-test="fermer"]').trigger('click')
    await attendre(page, '[data-test="erreur"]')
    expect(page.get('[data-test="erreur"]').text()).toContain('a changé entre-temps')
  })

  it('une mission inconnue ou privée est « introuvable », sans dire laquelle', async () => {
    const { page } = await monter('/missions/00000000-0000-4000-8000-000000000000')
    await attendre(page, '[data-test="introuvable"]')
  })

  it('affiche le texte du client comme du texte, jamais comme du HTML', async () => {
    serveur.use(
      http.get('/api/missions/:id', () =>
        HttpResponse.json({
          id: ID, titre: '<img src=x onerror=alert(1)>', besoin: '<b>gras</b> et <script>alert(1)</script>', statut: 'publiee',
          competences: [], budget: null, aDistance: true, client: { id: ID, nom: 'Awa', avatarUrl: null },
          nombrePropositions: 0, publieeLe: null, visibilite: 'publique', version: 1, creeLe: '2026-11-14T10:00:00Z',
          moi: { role: 'visiteur', actions: ['proposer'] }, travail: null,
        }),
      ),
    )
    const { page } = await monter(`/missions/${ID}`)
    await attendre(page, '[data-test="titre"]')
    expect(page.find('img').exists()).toBe(false)
    expect(page.find('script').exists()).toBe(false)
    expect(page.get('[data-test="besoin"]').text()).toContain('<b>gras</b>')
    expect(page.find('[data-test="form-proposer"]').exists()).toBe(true)
  })
})
