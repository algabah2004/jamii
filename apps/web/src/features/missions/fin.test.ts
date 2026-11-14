import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { MISSION_EN_COURS, modifierMission, reinitialiserMissions, type Mission } from '../../mocks/missions'
import MissionPage from './MissionPage.vue'

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

const changer = (f: (m: Mission) => void) => modifierMission(MISSION_EN_COURS, f)

async function remplir(page: ReturnType<typeof mount>, formulaire: string, texte: string) {
  await page.get(`[data-test="${formulaire}"] textarea`).setValue(texte)
  await page.get(`[data-test="${formulaire}"]`).trigger('submit')
}

describe('annuler une mission', () => {
  it('un jalon livré ne s’annule pas : l’écran conseille un désaccord', async () => {
    const page = await monter()
    await attendre(page, '[data-test="ouvrir-annulation"]')
    await page.get('[data-test="ouvrir-annulation"]').trigger('click')
    await attendre(page, '[data-test="form-annulation"]')
    await remplir(page, 'form-annulation', 'Le salon ferme pour travaux.')
    await attendre(page, '[data-test="erreur-annulation"]')
    expect(page.get('[data-test="erreur-annulation"]').text()).toContain('signale plutôt un désaccord')
  })

  it('ma demande attend l’autre partie, et le bouton disparaît', async () => {
    changer((m) => (m.travail!.devis.jalons[0]!.statut = 'en_cours'))
    const page = await monter()
    await attendre(page, '[data-test="ouvrir-annulation"]')
    await page.get('[data-test="ouvrir-annulation"]').trigger('click')
    await attendre(page, '[data-test="form-annulation"]')
    expect(page.get('[data-test="annuler"]').attributes('disabled')).toBeDefined()
    await remplir(page, 'form-annulation', 'Le salon ferme pour travaux.')
    await attendre(page, '[data-test="annulation-demandee"]')
    expect(page.get('[data-test="annulation-demandee"]').text()).toContain("attend la réponse de l'autre partie")
    expect(page.find('[data-test="ouvrir-annulation"]').exists()).toBe(false)
  })

  it('confirme l’annulation demandée par le développeur', async () => {
    changer((m) => (m.travail!.annulationDemandeePar = 'developpeur'))
    const page = await monter()
    await attendre(page, '[data-test="annulation-demandee"]')
    expect(page.get('[data-test="annulation-demandee"]').text()).toContain('Le développeur demande')
    expect(page.get('[data-test="ouvrir-annulation"]').text()).toBe('Confirmer l\'annulation')
    await page.get('[data-test="ouvrir-annulation"]').trigger('click')
    await attendre(page, '[data-test="form-annulation"]')
    await remplir(page, 'form-annulation', 'D’accord, on arrête là.')
    await vi.waitFor(() => expect(page.get('[data-test="statut"]').text()).toBe('Annulée'))
  })
})

describe('un désaccord', () => {
  it('s’ouvre avec une raison, puis attend un modérateur', async () => {
    const page = await monter()
    await attendre(page, '[data-test="ouvrir-desaccord"]')
    await page.get('[data-test="ouvrir-desaccord"]').trigger('click')
    await attendre(page, '[data-test="form-desaccord"]')
    await page.get('[data-test="form-desaccord"] textarea').setValue('Pas conforme.')
    expect(page.get('[data-test="envoyer-desaccord"]').attributes('disabled')).toBeDefined()
    await remplir(page, 'form-desaccord', 'Le catalogue livré ne montre pas les prix prévus au devis.')
    await vi.waitFor(() => expect(page.get('[data-test="statut"]').text()).toBe('En désaccord'))
    const desaccord = page.get('[data-test="desaccord"]')
    expect(desaccord.text()).toContain('Ouvert par le client')
    expect(desaccord.text()).toContain('ne montre pas les prix')
    expect(desaccord.text()).toContain('Un modérateur va examiner')
    expect(page.find('[data-test="ouvrir-desaccord"]').exists()).toBe(false)
  })

  it('un modérateur le tranche, et sa décision est montrée', async () => {
    changer((m) => {
      m.statut = 'desaccord'
      m.moi = { role: 'moderateur', actions: ['trancher'] }
      m.travail!.desaccord = { ouvertPar: 'developpeur', raison: 'Le client ne répond plus depuis dix jours.', ouvertLe: '2026-11-13T10:00:00Z', issue: null, explication: null, trancheLe: null }
    })
    const page = await monter()
    await attendre(page, '[data-test="form-trancher"]')
    expect(page.find('[data-test="accepter"]').exists()).toBe(false)
    expect(page.get('[data-test="desaccord"]').text()).toContain('Ouvert par le développeur')
    expect(page.get('[data-test="trancher"]').attributes('disabled')).toBeDefined()
    await page.get('input[value="annulee"]').setValue(true)
    await remplir(page, 'form-trancher', 'Le travail n’a pas pu avancer sans réponse du client.')
    await attendre(page, '[data-test="decision"]')
    expect(page.get('[data-test="decision"]').text()).toContain('la mission est annulée')
    expect(page.get('[data-test="explication"]').text()).toContain('sans réponse du client')
  })
})

describe('les avis', () => {
  it('la cliente note la mission terminée une fois, à côté de l’avis du développeur', async () => {
    changer((m) => {
      m.statut = 'terminee'
      m.moi = { role: 'client', actions: ['donner_avis'] }
      for (const j of m.travail!.devis.jalons) Object.assign(j, { statut: 'accepte', accepteLe: '2026-11-14T10:00:00Z' })
      m.travail!.avis = [{ auteur: 'developpeur', note: 4, texte: 'Cliente claire et réactive.', donneLe: '2026-11-14T11:00:00Z' }]
    })
    const page = await monter()
    await attendre(page, '[data-test="form-avis"]')
    expect(page.get('[data-test="un-avis"]').text()).toContain('Avis du développeur')
    expect(page.get('[data-test="note"]').text()).toBe('4 sur 5')
    expect(page.get('[data-test="donner-avis"]').attributes('disabled')).toBeDefined()

    await page.get('input[name="note"][value="5"]').setValue(true)
    await remplir(page, 'form-avis', 'Travail soigné, livré à temps.')
    await vi.waitFor(() => expect(page.findAll('[data-test="un-avis"]')).toHaveLength(2))
    expect(page.find('[data-test="form-avis"]').exists()).toBe(false)
    expect(page.text()).toContain('Travail soigné, livré à temps.')
  })

  it('pas de section d’avis tant que la mission est en cours', async () => {
    const page = await monter()
    await attendre(page, '[data-test="suivi"]')
    expect(page.find('[data-test="avis"]').exists()).toBe(false)
  })
})
