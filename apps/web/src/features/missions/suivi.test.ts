import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { MISSION_EN_COURS, reinitialiserMissions, type Mission } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'
import { lireLiens } from './missions'

beforeEach(() => reinitialiserMissions())

async function monter(id = MISSION_EN_COURS) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/missions/:id', component: MissionPage }] })
  await routeur.push(`/missions/${id}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

/** La mission en cours des maquettes, retouchée pour un test (autre rôle, autre statut). */
async function variante(changer: (m: Mission) => void) {
  const mission = (await (await fetch(`/api/missions/${MISSION_EN_COURS}`)).json()) as Mission
  changer(mission)
  serveur.use(http.get('/api/missions/:id', () => HttpResponse.json(mission)))
  return mission
}

describe('les liens d’une livraison', () => {
  it('garde les liens https, un par ligne, et refuse le reste', () => {
    expect(lireLiens(' https://a.example/test \n\nhttps://github.com/x/y ')).toEqual(['https://a.example/test', 'https://github.com/x/y'])
    expect(lireLiens('')).toEqual([])
    expect(lireLiens('http://a.example')).toBeNull()
    expect(lireLiens('javascript:alert(1)')).toBeNull()
    expect(lireLiens('pas un lien')).toBeNull()
    expect(lireLiens(Array.from({ length: 6 }, (_, i) => `https://a.example/${i}`).join('\n'))).toBeNull()
  })
})

describe('le suivi d’une mission', () => {
  it('montre le développeur, l’avancement, et chaque jalon avec sa livraison', async () => {
    const page = await monter()
    await attendre(page, '[data-test="suivi"]')
    expect(page.get('[data-test="suivi"]').text()).toContain('Kofi Mensah')
    expect(page.get('[data-test="avancement"]').text()).toBe('0 jalons acceptés sur 2')
    const [livre, enCours] = page.findAll('[data-test="jalon"]')
    expect(livre!.get('[data-test="statut-jalon"]').text()).toBe('Livré, à vérifier')
    expect(livre!.get('[data-test="acceptation-automatique"]').text()).toContain('accepté automatiquement le 16 novembre 2026')
    const lien = livre!.get('[data-test="livraison"] a')
    expect(lien.attributes('href')).toBe('https://atelier.example/catalogue')
    expect(lien.attributes('rel')).toContain('noopener')
    expect(enCours!.get('[data-test="statut-jalon"]').text()).toBe('En cours')
    expect(enCours!.find('[data-test="accepter"]').exists()).toBe(false)
    expect(page.get('[data-test="informations-depart"]').text()).toContain('dossier partagé')
  })

  it('la cliente accepte un jalon livré, puis peut déclarer l’avoir payé', async () => {
    const page = await monter()
    await attendre(page, '[data-test="accepter"]')
    await page.get('[data-test="accepter"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="avancement"]').text()).toBe('1 jalon accepté sur 2'))
    expect(page.findAll('[data-test="statut-jalon"]')[0]!.text()).toBe('Accepté')
    expect(page.find('[data-test="accepter"]').exists()).toBe(false)

    await page.get('[data-test="payer"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="paiement"]')[0]!.text()).toBe('Payé selon le client'))
  })

  it('demande une retouche avec une raison, dans la limite du devis', async () => {
    const page = await monter()
    await attendre(page, '[data-test="ouvrir-retouche"]')
    expect(page.get('[data-test="ouvrir-retouche"]').text()).toContain('2 restantes')
    await page.get('[data-test="ouvrir-retouche"]').trigger('click')
    await attendre(page, '[data-test="form-retouche"]')
    const champ = page.get('[data-test="form-retouche"] textarea')
    await champ.setValue('court')
    expect(page.get('[data-test="envoyer-retouche"]').attributes('disabled')).toBeDefined()
    await champ.setValue('Les prix des modèles en promotion ne s’affichent pas.')
    await page.get('[data-test="form-retouche"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="statut-jalon"]')[0]!.text()).toBe('En cours'))
    expect(page.get('[data-test="retouche"]').text()).toContain('ne s’affichent pas')
    expect(page.get('[data-test="retouches"]').text()).toBe('Retouches restantes : 1 sur 2.')
  })

  it('dit quand toutes les retouches ont été utilisées', async () => {
    await variante((m) => (m.travail!.retouchesRestantes = 0))
    const page = await monter()
    await attendre(page, '[data-test="accepter"]')
    expect(page.find('[data-test="ouvrir-retouche"]').exists()).toBe(false)
    expect(page.get('[data-test="plus-de-retouche"]').text()).toContain('ont été utilisées')
  })

  it('le développeur livre un jalon avec ses liens https, et rien n’est envoyé si un lien est mauvais', async () => {
    await variante((m) => (m.moi = { role: 'developpeur_retenu', actions: ['livrer', 'annuler', 'ouvrir_desaccord'] }))
    let envoye: unknown = null
    serveur.use(
      http.post('/api/missions/:id/jalons/:jalonId/livrer', async ({ request }) => {
        envoye = await request.json()
        return HttpResponse.json({ code: 'modification_concurrente' }, { status: 409 })
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-livrer"]')
    expect(page.findAll('[data-test="form-livrer"]')).toHaveLength(1)
    expect(page.find('[data-test="accepter"]').exists()).toBe(false)
    const [message, liens] = page.findAll('[data-test="form-livrer"] textarea')
    await message!.setValue('Les rappels SMS partent la veille du rendez-vous.')
    await liens!.setValue('http://test.example')
    expect(page.get('[data-test="form-livrer"]').text()).toContain('https://')
    expect(page.get('[data-test="livrer"]').attributes('disabled')).toBeDefined()

    await liens!.setValue('https://test.example/rappels')
    await page.get('[data-test="form-livrer"]').trigger('submit')
    await attendre(page, '[data-test="erreur-livrer"]')
    expect(envoye).toEqual({ version: 7, message: 'Les rappels SMS partent la veille du rendez-vous.', liens: ['https://test.example/rappels'] })
    expect(page.get('[data-test="erreur-livrer"]').text()).toContain('a changé entre-temps')
  })

  it('la cliente démarre une mission attribuée, avec un rappel de ne jamais donner de mot de passe', async () => {
    await variante((m) => {
      m.statut = 'attribuee'
      m.moi = { role: 'client', actions: ['demarrer', 'annuler'] }
      Object.assign(m.travail!, { informationsDepart: null, demarreLe: null })
      for (const j of m.travail!.devis.jalons) Object.assign(j, { statut: 'a_faire', echeance: null, livraisons: [], acceptationAutomatiqueLe: null })
    })
    let envoye: unknown = null
    serveur.use(
      http.post('/api/missions/:id/demarrer', async ({ request }) => {
        envoye = await request.json()
        return HttpResponse.json({ code: 'etat_invalide' }, { status: 409 })
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="form-demarrer"]')
    expect(page.get('[data-test="form-demarrer"]').text()).toContain('mot de passe')
    expect(page.find('[data-test="echeance"]').exists()).toBe(false)
    await page.get('[data-test="form-demarrer"] textarea').setValue('Les photos sont dans le dossier partagé.')
    await page.get('[data-test="form-demarrer"]').trigger('submit')
    await attendre(page, '[data-test="erreur-demarrer"]')
    expect(envoye).toEqual({ version: 7, informations: 'Les photos sont dans le dossier partagé.' })
  })

  it('le développeur attend les informations de départ', async () => {
    await variante((m) => {
      m.statut = 'attribuee'
      m.moi = { role: 'developpeur_retenu', actions: ['annuler'] }
    })
    const page = await monter()
    await attendre(page, '[data-test="attente-depart"]')
    expect(page.find('[data-test="form-demarrer"]').exists()).toBe(false)
  })

  it('n’affiche aucun suivi à qui ne voit pas le travail', async () => {
    const page = await monter('a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91')
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="suivi"]').exists()).toBe(false)
  })
})
