import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { MISSION_EN_COURS, reinitialiserMissions, type Mission } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'
import { enRetard, prochaineEtape } from './prochaineEtape'

beforeEach(() => reinitialiserMissions())

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/missions/:id', component: MissionPage }] })
  await routeur.push(`/missions/${MISSION_EN_COURS}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

const lire = async () => (await (await fetch(`/api/missions/${MISSION_EN_COURS}`)).json()) as Mission

describe('la prochaine étape d’une mission en cours', () => {
  it('la cliente vérifie d’abord le jalon livré, puis le paie', async () => {
    const m = await lire()
    expect(prochaineEtape(m)).toMatchObject({ cle: 'verifier', jalon: { ordre: 1 } })
    m.moi.actions = ['declarer_paye']
    m.travail!.devis.jalons[0]!.statut = 'accepte'
    expect(prochaineEtape(m)).toMatchObject({ cle: 'payer', jalon: { ordre: 1 } })
  })

  it('le développeur livre le jalon en cours, puis confirme le paiement reçu', async () => {
    const m = await lire()
    m.moi = { role: 'developpeur_retenu', actions: ['livrer'] }
    expect(prochaineEtape(m)).toMatchObject({ cle: 'livrer', jalon: { ordre: 2 } })
    m.moi.actions = ['declarer_recu']
    m.travail!.devis.jalons[0]!.paiement = 'declare_paye'
    expect(prochaineEtape(m)).toMatchObject({ cle: 'recevoir', jalon: { ordre: 1 } })
  })

  it('n’annonce rien sans action proposée par l’API, ni hors d’une mission en cours', async () => {
    const m = await lire()
    m.moi.actions = []
    expect(prochaineEtape(m)).toBeNull()
    const terminee = await lire()
    terminee.statut = 'terminee'
    expect(prochaineEtape(terminee)).toBeNull()
  })

  it('un jalon pas livré dont la date est passée est en retard', async () => {
    const [livre, enCours] = (await lire()).travail!.devis.jalons
    const apres = Date.parse('2026-12-01T00:00:00Z')
    expect(enRetard(enCours!, apres)).toBe(true)
    expect(enRetard(enCours!, Date.parse('2026-11-01T00:00:00Z'))).toBe(false)
    expect(enRetard(livre!, apres)).toBe(false)
    expect(enRetard({ ...enCours!, echeance: null }, apres)).toBe(false)
  })
})

describe('la page d’une mission en cours', () => {
  it('met la prochaine étape en haut du résumé, avec un lien vers le jalon', async () => {
    const page = await monter()
    await attendre(page, '[data-test="prochaine-etape"]')
    const etape = page.get('[data-test="prochaine-etape"]')
    expect(etape.text()).toContain('Vérifier le jalon 1')
    expect(etape.text()).toContain('Le catalogue avec photos et prix')
    const id = page.findAll('[data-test="jalon"]')[0]!.attributes('id')
    expect(page.get('[data-test="aller-jalon"]').attributes('href')).toBe(`#${id}`)
    // Le développeur est choisi : le nombre de propositions n'a plus d'intérêt.
    expect(page.find('[data-test="nombre-propositions"]').exists()).toBe(false)
    expect(page.get('[data-test="barre-mobile"]').text()).toContain('Vérifier le jalon 1')

    await page.get('[data-test="accepter"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="prochaine-etape"]').text()).toContain('Payer le jalon 1'))
  })

  it('signale un jalon en retard', async () => {
    const m = await lire()
    m.travail!.devis.jalons[1]!.echeance = '2026-01-10T09:00:00Z'
    serveur.use(http.get('/api/missions/:id', () => HttpResponse.json(m)))
    const page = await monter()
    await attendre(page, '[data-test="jalon"]')
    const [livre, enCours] = page.findAll('[data-test="jalon"]')
    expect(enCours!.get('[data-test="en-retard"]').text()).toBe('En retard')
    expect(livre!.find('[data-test="en-retard"]').exists()).toBe(false)
  })
})
