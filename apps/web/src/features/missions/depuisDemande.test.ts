import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { reinitialiserMissions } from '../../mocks/missions'
import type { Demande } from '../demandes/demandes'
import NouvelleMissionPage from './NouvelleMissionPage.vue'
import { besoinDuPlan, competencesDuPlan, planDe, prefillDepuisDemande, type Libelles } from './depuisDemande'

const LIBELLES: Libelles = { objectifs: 'Objectifs', indispensables: 'Indispensables', utiles: 'Utiles', horsPerimetre: 'Hors périmètre' }
const ID = 'a1b2c3d4-0000-4000-8000-00000000d3a4'

/** Une demande dont l'IA a rendu un plan : un résumé, trois fonctionnalités, un rôle manquant et une tâche. */
function demandeDemo(): Demande {
  const plan = {
    cahierDesCharges: {
      resume: 'Une boutique en ligne pour un atelier de couture, avec commande par WhatsApp.',
      objectifs: ['Montrer les pagnes', 'Recevoir les commandes'],
      fonctionnalites: [
        { titre: 'Catalogue', description: 'Photos et prix', priorite: 'indispensable' },
        { titre: 'Avis clients', description: 'Notes et commentaires', priorite: 'utile' },
        { titre: 'Programme de fidélité', description: 'Points par achat', priorite: 'plus_tard' },
      ],
      horsPerimetre: ['Application mobile'],
    },
    jalons: [{ titre: 'Catalogue', semaine: 2, livrable: 'Le catalogue en ligne' }],
    taches: [{ titre: 'Écrans', jalon: 0, competences: ['Vue', 'UX Design'] }],
    rolesManquants: [{ role: 'Développeur front', competences: ['vue', 'paiement-mobile'], raison: "Aucun profil dans l'équipe" }],
    servicesSuggeres: [],
  }
  return {
    id: ID,
    titre: 'Boutique de pagnes',
    description: "Mon texte d'origine, écrit pour moi.",
    dureeSemaines: 6,
    creeLe: '2026-11-15T10:00:00Z',
    ville: 'Dakar',
    plan: { statut: 'terminee', origine: 'ia', resultat: plan },
  } as unknown as Demande
}

beforeEach(() => reinitialiserMissions())

describe('préparer une mission à partir d’une demande', () => {
  it('compose le besoin : le résumé, les objectifs, puis les fonctionnalités indispensables et utiles', () => {
    const besoin = besoinDuPlan(planDe(demandeDemo())!, LIBELLES)
    expect(besoin).toContain('Une boutique en ligne pour un atelier de couture')
    expect(besoin).toContain('Objectifs\n- Montrer les pagnes\n- Recevoir les commandes')
    expect(besoin).toContain('Indispensables\n- Catalogue : Photos et prix')
    expect(besoin).toContain('Utiles\n- Avis clients : Notes et commentaires')
    expect(besoin).toContain('Hors périmètre\n- Application mobile')
    // « Plus tard » n'entre pas dans le besoin d'une mission.
    expect(besoin).not.toContain('fidélité')
  })

  it('range les compétences du plan, sans doublon', () => {
    expect(competencesDuPlan(planDe(demandeDemo())!)).toEqual(['vue', 'paiement-mobile', 'ux-design'])
  })

  it('reprend la ville et le titre, et le texte d’origine seulement sans plan', () => {
    const avec = prefillDepuisDemande(demandeDemo(), LIBELLES)
    expect(avec).toMatchObject({ titre: 'Boutique de pagnes', ville: 'Dakar', competences: 'vue, paiement-mobile, ux-design' })
    expect(avec.besoin).not.toContain("Mon texte d'origine")

    const sans = demandeDemo()
    sans.plan = { statut: 'echouee' } as Demande['plan']
    const repli = prefillDepuisDemande(sans, LIBELLES)
    expect(repli.besoin).toBe("Mon texte d'origine, écrit pour moi.")
    expect(repli.competences).toBe('')
  })
})

async function monter(chemin: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/missions/nouvelle', component: NouvelleMissionPage },
      { path: '/missions/:id', component: { template: '<p />' } },
    ],
  })
  await routeur.push(chemin)
  await routeur.isReady()
  return mount(RouterView, { global: { plugins: [i18n, routeur] } })
}

describe('l’écran d’une nouvelle mission, venu d’une demande', () => {
  it('préremplit le brouillon, le dit, et rattache la mission à la demande', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.get('/api/demandes/:id', () => HttpResponse.json(demandeDemo())),
      http.post('/api/missions', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'abc', titre: 'x' }, { status: 201 })
      }),
    )
    const page = await monter(`/missions/nouvelle?demande=${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="depuis-demande"]').exists()).toBe(true))
    expect(page.get('[data-test="depuis-demande"]').text()).toContain('« Boutique de pagnes »')
    const champs = page.findAll('input[type="text"], textarea')
    expect((champs[0]!.element as HTMLInputElement).value).toBe('Boutique de pagnes')
    expect((champs[1]!.element as HTMLTextAreaElement).value).toContain('Catalogue : Photos et prix')
    expect((champs[2]!.element as HTMLInputElement).value).toBe('vue, paiement-mobile, ux-design')

    await page.get('[data-test="nouvelle-mission"]').trigger('submit')
    await vi.waitFor(() => expect(corps.titre).toBe('Boutique de pagnes'))
    expect(corps.demandeId).toBe(ID)
    expect(corps.competences).toEqual(['vue', 'paiement-mobile', 'ux-design'])
  })

  it('reste un formulaire vide, sans demande rattachée, si la demande est introuvable', async () => {
    serveur.use(http.get('/api/demandes/:id', () => HttpResponse.json({ code: 'introuvable' }, { status: 404 })))
    const page = await monter(`/missions/nouvelle?demande=${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="nouvelle-mission"]').exists()).toBe(true))
    await new Promise((r) => setTimeout(r, 50))
    expect(page.find('[data-test="depuis-demande"]').exists()).toBe(false)
    expect((page.findAll('input[type="text"]')[0]!.element as HTMLInputElement).value).toBe('')
  })
})
