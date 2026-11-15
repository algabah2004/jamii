import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierMission, reinitialiserMissions } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'

/** La mission publiée des maquettes : une proposition de Kofi, vue par sa cliente. */
const ID = 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91'

beforeEach(() => reinitialiserMissions())

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routes = [
    { path: '/missions/:id', component: MissionPage },
    { path: '/:autre(.*)*', component: { render: () => null } },
  ]
  const routeur = createRouter({ history: createMemoryHistory(), routes })
  await routeur.push(`/missions/${ID}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

const visiteur = () => modifierMission(ID, (m) => (m.moi = { role: 'visiteur', actions: ['proposer'] }))

async function remplir(page: ReturnType<typeof mount>, message: string, prix: string, delai: string) {
  const champs = page.findAll('[data-test="form-proposer"] input')
  await page.get('[data-test="form-proposer"] textarea').setValue(message)
  await champs[0]!.setValue(prix)
  await champs[1]!.setValue(delai)
}

const MESSAGE = 'J’ai déjà fait trois boutiques avec paiement Wave, je commence par le catalogue.'

describe('les propositions reçues par la cliente', () => {
  it('montre chaque proposition avec son prix et son délai, et la retient', async () => {
    const page = await monter()
    await attendre(page, '[data-test="proposition"]')
    const carte = page.get('[data-test="proposition"]')
    expect(carte.text()).toContain('Kofi Mensah')
    expect(carte.text()).toMatch(/400\s?000/)
    expect(carte.text()).toContain('21 jours')
    expect(carte.get('[data-test="statut-proposition"]').text()).toBe('Envoyée')
    expect(page.find('[data-test="retirer"]').exists()).toBe(false)

    await carte.get('[data-test="retenir"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut-proposition"]').text()).toBe('Retenue'))
    expect(page.get('[data-test="aide-retenue"]').text()).toContain('prépare son devis')
    expect(page.find('[data-test="retenir"]').exists()).toBe(false)
  })

  it('compare les propositions : tri par prix ou par délai, au-dessus du budget, lien vers le profil', async () => {
    const base = { message: MESSAGE, reponses: [], statut: 'envoyee', envoyeeLe: '2026-11-14T12:00:00Z' }
    serveur.use(
      http.get('/api/missions/:id/propositions', () =>
        HttpResponse.json({ curseurSuivant: null, elements: [
          { ...base, id: 'p1', developpeur: { id: 'd1', nom: 'Rapide Cher', avatarUrl: null }, prix: { centimes: 500000, devise: 'XOF' }, delaiJours: 7 },
          { ...base, id: 'p2', developpeur: { id: 'd2', nom: 'Lent Abordable', avatarUrl: null }, prix: { centimes: 300000, devise: 'XOF' }, delaiJours: 30 },
        ] }),
      ),
    )
    const page = await monter()
    await attendre(page, '[data-test="proposition"]')
    await attendre(page, '[data-test="tri-prix"]')
    const noms = () => page.findAll('[data-test="proposition"] [data-test="profil-developpeur"]').map((n) => n.text())
    await page.get('[data-test="tri-prix"]').trigger('click')
    await vi.waitFor(() => expect(noms()).toEqual(['Lent Abordable', 'Rapide Cher']))
    await page.get('[data-test="tri-delai"]').trigger('click')
    await vi.waitFor(() => expect(noms()).toEqual(['Rapide Cher', 'Lent Abordable']))
    // Budget de 450 000 XOF : seule l'offre à 500 000 est signalée.
    expect(page.findAll('[data-test="au-dessus"]')).toHaveLength(1)
    expect(page.get('[data-test="profil-developpeur"]').attributes('href')).toBe('/p/d1')
  })

  it('pas de propositions à montrer sur un brouillon', async () => {
    modifierMission(ID, (m) => (m.statut = 'brouillon'))
    const page = await monter()
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="propositions"]').exists()).toBe(false)
  })
})

describe('proposer ses services', () => {
  it('garde le bouton éteint tant que le message, le prix ou le délai manquent', async () => {
    visiteur()
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    expect(page.find('[data-test="propositions"]').exists()).toBe(false)
    await remplir(page, 'Trop court.', '350 000', '20')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeDefined()
    await remplir(page, MESSAGE, 'beaucoup', '20')
    expect(page.get('[data-test="form-proposer"]').text()).toContain('pas un montant valide')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeDefined()
    await remplir(page, MESSAGE, '350 000', '400')
    expect(page.get('[data-test="form-proposer"]').text()).toContain('Entre 1 et 365 jours')
    await remplir(page, MESSAGE, '350 000', '20')
    expect(page.get('[data-test="proposer"]').attributes('disabled')).toBeUndefined()
  })

  it('envoie le prix en unité mineure, dans la devise du budget, puis montre ma proposition', async () => {
    visiteur()
    let envoye: unknown = null
    serveur.use(
      http.post('/api/missions/:id/propositions', async ({ request }) => {
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    await remplir(page, MESSAGE, '350 000', '20')
    await page.get('[data-test="form-proposer"]').trigger('submit')
    await attendre(page, '[data-test="propositions"]')
    expect(envoye).toEqual({ message: MESSAGE, prix: { centimes: 350000, devise: 'XOF' }, delaiJours: 20 })
    expect(page.find('[data-test="form-proposer"]').exists()).toBe(false)
    expect(page.get('[data-test="propositions"]').text()).toContain('Ma proposition')
  })

  it('dit quand on a déjà proposé ses services', async () => {
    visiteur()
    serveur.use(http.post('/api/missions/:id/propositions', () => HttpResponse.json({ code: 'deja_candidat' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-proposer"]')
    await remplir(page, MESSAGE, '350 000', '20')
    await page.get('[data-test="form-proposer"]').trigger('submit')
    await attendre(page, '[data-test="erreur-proposer"]')
    expect(page.get('[data-test="erreur-proposer"]').text()).toContain('déjà proposé')
  })

  it('le développeur retire sa proposition', async () => {
    modifierMission(ID, (m) => (m.moi = { role: 'candidat', actions: [] }))
    const page = await monter()
    await attendre(page, '[data-test="retirer"]')
    expect(page.get('[data-test="propositions"]').text()).toContain('Ma proposition')
    expect(page.get('[data-test="statut-candidat"]').text()).toContain('pas encore choisi')
    expect(page.find('[data-test="tri-prix"]').exists()).toBe(false)
    await page.get('[data-test="retirer"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="statut-proposition"]').text()).toBe('Retirée'))
    expect(page.find('[data-test="retirer"]').exists()).toBe(false)
  })
})

describe('les échanges entre le client et un développeur', () => {
  const echanges = () => {
    const envoyes: unknown[] = []
    serveur.use(
      http.get('/api/conversations/:id/messages', ({ params }) =>
        HttpResponse.json({
          personne: { id: String(params.id), nom: 'Kofi Mensah', avatarUrl: null },
          elements: [{ id: 'm1', deMoi: false, texte: 'Bonjour, le paiement Wave est-il déjà ouvert ?', envoyeLe: '2026-11-14T13:00:00Z', luLe: null }],
          curseurSuivant: null,
        }),
      ),
      http.post('/api/conversations/:id/messages', async ({ request }) => {
        const corps = (await request.json()) as { texte: string }
        envoyes.push(corps)
        return HttpResponse.json({ id: 'm2', deMoi: true, texte: corps.texte, envoyeLe: '2026-11-14T13:05:00Z', luLe: null }, { status: 201 })
      }),
    )
    return envoyes
  }

  it('la cliente choisit une proposition, lit la conversation et répond', async () => {
    const envoyes = echanges()
    const page = await monter()
    await attendre(page, '[data-test="choisir"]')
    await attendre(page, '[data-test="ecrire-developpeur"]')
    await page.get('[data-test="ecrire-developpeur"]').trigger('click')
    await attendre(page, '[data-test="message"]')
    expect(page.get('[data-test="echanges"]').text()).toContain('Échanges avec Kofi Mensah')
    expect(page.get('[data-test="message"]').text()).toContain('paiement Wave')
    await page.get('[data-test="form-echange"] textarea').setValue('Oui, le compte marchand est prêt.')
    await page.get('[data-test="form-echange"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(2))
    expect(envoyes).toEqual([{ texte: 'Oui, le compte marchand est prêt.' }])
    expect(page.get('[data-test="ouvrir-messages"]').attributes('href')).toMatch(/^\/messages\//)
  })

  it('un développeur écrit au client avant de proposer ses services', async () => {
    echanges()
    visiteur()
    const page = await monter()
    await attendre(page, '[data-test="aller-echanges"]')
    expect(page.get('[data-test="aller-echanges"]').attributes('href')).toBe('#echanges')
    await attendre(page, '[data-test="form-echange"]')
    expect(page.find('[data-test="ecrire-developpeur"]').exists()).toBe(false)
  })

  it('rien à échanger sur un brouillon', async () => {
    modifierMission(ID, (m) => (m.statut = 'brouillon'))
    const page = await monter()
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="echanges"]').exists()).toBe(false)
  })
})
