import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { deposerRendu, modifierHackathon, reinitialiserHackathons, type Rendu } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
const [UTILITE, TECHNIQUE, DATACLOUD] = ['c1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000003']
const EQUIPE_1 = 'e1000000-0000-4000-8000-000000000001'

function renduDemo(n: number, titre: string): Rendu {
  return {
    id: `r1000000-0000-4000-8000-00000000000${n}`,
    equipeId: `e1000000-0000-4000-8000-00000000000${n}`,
    titre,
    description: 'Un service pour les commerçants du quartier, en ligne sur DataCloud.',
    lienProduit: `https://projet${n}.example`,
    depot: null,
    video: null,
    reponses: [],
    lienRepond: true,
    lienVerifieLe: '2026-11-14T10:00:00Z',
    version: 1,
    modifieLe: '2026-11-14T10:00:00Z',
  }
}

const noter = (n: number, valeur: number, commentaire: string | null) =>
  fetch(`/api/hackathons/${ID}/rendus/r1000000-0000-4000-8000-00000000000${n}/notes`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ notes: [UTILITE, TECHNIQUE, DATACLOUD].map((critereId) => ({ critereId, valeur })), commentaire }),
  })

// Deux rendus notés pendant l'évaluation : « Stock au marché » (5) devant « Carnet de caisse » (3).
beforeEach(async () => {
  reinitialiserHackathons()
  deposerRendu(renduDemo(1, 'Carnet de caisse'))
  deposerRendu(renduDemo(2, 'Stock au marché'))
  modifierHackathon(ID, (h) => Object.assign(h, { statut: 'evaluation' }))
  await noter(1, 3, null)
  await noter(2, 5, 'Très utile au marché de Sandaga.')
  modifierHackathon(ID, (h) => Object.assign(h, { moi: { role: 'organisateur', actions: ['confirmer_resultats'] } }))
})

async function monter() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/hackathons/:id', component: HackathonPage }] })
  await routeur.push(`/hackathons/${ID}`)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

describe('confirmer les gagnants', () => {
  it('propose les gagnants du classement, publie, puis montre les résultats à tous', async () => {
    const page = await monter()
    await attendre(page, '[data-test="prix-a-attribuer"]')
    const choix = page.findAll<HTMLSelectElement>('[data-test="gagnant"]')
    expect(choix).toHaveLength(2)
    expect(choix[0]!.element.value).toBe('e1000000-0000-4000-8000-000000000002')
    expect(choix[1]!.element.value).toBe(EQUIPE_1)
    expect(page.find('[data-test="raison"]').exists()).toBe(false)

    await page.get('[data-test="form-resultats"]').trigger('submit')
    await attendre(page, '[data-test="resultats"] [data-test="gagnant-prix"]')
    expect(page.find('[data-test="confirmation"]').exists()).toBe(false)
    const gagnants = page.findAll('[data-test="gagnant-prix"]')
    expect(gagnants[0]!.text()).toContain('Prix du jury')
    expect(gagnants[0]!.text()).toContain('Équipe Stock au marché')
    expect(gagnants[1]!.text()).toContain('offert par Systalink')
    const lignes = page.findAll('[data-test="ligne-resultat"]')
    expect(lignes[0]!.text()).toContain('1er')
    expect(lignes[0]!.text()).toContain('Très utile au marché de Sandaga.')
    expect(lignes[1]!.text()).toContain('2e')
  })

  it('demande une raison quand le gagnant n’est pas celui du jury', async () => {
    let corps: unknown
    serveur.use(
      http.post('/api/hackathons/:id/resultats', async ({ request }) => {
        corps = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await attendre(page, '[data-test="prix-a-attribuer"]')
    await page.findAll('[data-test="gagnant"]')[0]!.setValue(EQUIPE_1)
    await attendre(page, '[data-test="raison"]')
    expect(page.get('[data-test="publier-resultats"]').attributes('disabled')).toBeDefined()

    await page.get('[data-test="raison"] textarea').setValue('Le seul projet déjà utilisé par un vrai commerçant.')
    expect(page.get('[data-test="publier-resultats"]').attributes('disabled')).toBeUndefined()
    await page.get('[data-test="form-resultats"]').trigger('submit')
    await attendre(page, '[data-test="resultats"]')
    expect(corps).toMatchObject({
      version: 1,
      gagnants: [
        { equipeId: EQUIPE_1, raison: 'Le seul projet déjà utilisé par un vrai commerçant.' },
        { equipeId: EQUIPE_1, raison: null },
      ],
    })
  })

  it('dit quand la publication est refusée', async () => {
    serveur.use(http.post('/api/hackathons/:id/resultats', () => HttpResponse.json({ code: 'modification_concurrente' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-resultats"]')
    await page.get('[data-test="form-resultats"]').trigger('submit')
    await attendre(page, '[data-test="erreur-resultats"]')
    expect(page.get('[data-test="erreur-resultats"]').text()).toContain('Recharge la page')
  })
})
