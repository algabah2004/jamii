import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { deposerRendu, modifierHackathon, reinitialiserHackathons, type Hackathon, type Rendu } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'
import { lireIdentifiant } from './hackathons'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
const FATOU = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f'
const [UTILITE, TECHNIQUE, DATACLOUD] = ['c1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000003']

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

beforeEach(() => {
  reinitialiserHackathons()
  deposerRendu(renduDemo(1, 'Carnet de caisse'))
  deposerRendu(renduDemo(2, 'Stock au marché'))
})

const role = (statut: Hackathon['statut'], moi: Hackathon['moi']) => modifierHackathon(ID, (h) => Object.assign(h, { statut, moi }))

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

const json = (corps: unknown) => ({ method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corps) })

describe('le lien d’un profil', () => {
  it('en tire l’identifiant du compte, et refuse le reste', () => {
    expect(lireIdentifiant(`https://jamii.example/p/${FATOU}`)).toBe(FATOU)
    expect(lireIdentifiant(` ${FATOU.toUpperCase()} `)).toBe(FATOU)
    expect(lireIdentifiant('https://jamii.example/p/fatou')).toBeNull()
    expect(lireIdentifiant('')).toBeNull()
  })
})

describe('le jury, côté organisateur', () => {
  it('invite un juré par le lien de son profil, et dit quand le compte n’existe pas', async () => {
    role('en_cours', { role: 'organisateur', actions: ['inviter_jure'] })
    const page = await monter()
    await attendre(page, '[data-test="aucun-jure"]')
    const champ = page.get('[data-test="form-jure"] input')
    await champ.setValue('https://jamii.example/p/fatou')
    expect(page.get('[data-test="inviter-jure"]').attributes('disabled')).toBeDefined()

    await champ.setValue('https://jamii.example/p/00000000-0000-4000-8000-000000000000')
    await page.get('[data-test="form-jure"]').trigger('submit')
    await attendre(page, '[data-test="erreur-jury"]')

    await champ.setValue(`https://jamii.example/p/${FATOU}`)
    await page.get('[data-test="form-jure"]').trigger('submit')
    await attendre(page, '[data-test="jure"]')
    expect(page.get('[data-test="jure"]').text()).toContain('Fatou Ndiaye')
    expect(page.get('[data-test="avancement-jure"]').text()).toBe('Notés : 0 sur 2')
    expect(page.find('[data-test="classement"]').exists()).toBe(false)
  })

  it('pendant l’évaluation, lit le classement proposé et exporte les notes', async () => {
    role('evaluation', { role: 'organisateur', actions: [] })
    await fetch(`/api/hackathons/${ID}/rendus/r1000000-0000-4000-8000-000000000001/notes`, json({ notes: [{ critereId: UTILITE, valeur: 3 }, { critereId: TECHNIQUE, valeur: 3 }, { critereId: DATACLOUD, valeur: 3 }] }))
    await fetch(`/api/hackathons/${ID}/rendus/r1000000-0000-4000-8000-000000000002/notes`, json({ notes: [{ critereId: UTILITE, valeur: 5 }, { critereId: TECHNIQUE, valeur: 4 }, { critereId: DATACLOUD, valeur: 4 }] }))
    const page = await monter()
    await attendre(page, '[data-test="ligne-classement"]')
    const lignes = page.findAll('[data-test="ligne-classement"]')
    expect(lignes[0]!.text()).toContain('Stock au marché')
    expect(lignes[0]!.text()).toContain('4,50 / 5')
    expect(lignes[1]!.text()).toContain('Carnet de caisse')
    expect(page.get('[data-test="export-notes"]').attributes('href')).toBe(`/api/hackathons/${ID}/notes.csv`)
    expect(page.find('[data-test="form-jure"]').exists()).toBe(false)
  })
})

describe('noter, côté juré', () => {
  beforeEach(() => role('evaluation', { role: 'jure', actions: ['noter'] }))

  it('note chaque critère de 1 à 5 et voit le score pondéré', async () => {
    const page = await monter()
    await attendre(page, '[data-test="rendu-a-noter"]')
    expect(page.findAll('[data-test="rendu-a-noter"]')).toHaveLength(2)
    const premier = page.findAll('[data-test="form-notes"]')[0]!
    expect(premier.findAll('[data-test="critere"]')).toHaveLength(3)
    expect(premier.get('[data-test="noter"]').attributes('disabled')).toBeDefined()

    const criteres = premier.findAll('[data-test="critere"]')
    await criteres[0]!.get('input[value="5"]').setValue(true)
    await criteres[1]!.get('input[value="3"]').setValue(true)
    expect(premier.get('[data-test="noter"]').attributes('disabled')).toBeDefined()
    await criteres[2]!.get('input[value="1"]').setValue(true)
    await premier.trigger('submit')
    await attendre(page, '[data-test="score"]')
    expect(page.get('[data-test="score"]').text()).toContain('3,60')
    expect(premier.get('[data-test="noter"]').text()).toBe('Modifier mes notes')
  })

  it('dit quand on ne peut plus noter', async () => {
    serveur.use(http.put('/api/hackathons/:id/rendus/:renduId/notes', () => HttpResponse.json({ code: 'etat_invalide' }, { status: 409 })))
    const page = await monter()
    await attendre(page, '[data-test="form-notes"]')
    const premier = page.findAll('[data-test="form-notes"]')[0]!
    for (const c of premier.findAll('[data-test="critere"]')) await c.get('input[value="4"]').setValue(true)
    await premier.trigger('submit')
    await attendre(page, '[data-test="erreur-notes"]')
    expect(page.get('[data-test="erreur-notes"]').text()).toContain('plus possible')
  })
})
