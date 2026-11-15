import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

describe('toutes les communautés', () => {
  it('cherche par nom ou description, et filtre par thème (gardé dans l’adresse)', async () => {
    const { page, routeur } = await ouvrir('/communautes')
    await vi.waitFor(() => expect(page.findAll('[data-test="communautes"] > li')).toHaveLength(4))
    expect(page.find('[data-test="creer-communaute"]').attributes('href')).toBe('/communautes/nouvelle')

    await page.get('[data-test="chercher-communaute"]').setValue('mobile money')
    expect(page.findAll('[data-test="communautes"] > li')).toHaveLength(1)
    expect(page.text()).toContain('Fintech et paiement mobile')

    await page.get('[data-test="chercher-communaute"]').setValue('')
    await page.get('[data-test="theme-antenne"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.query.theme).toBe('antenne'))
    expect(page.findAll('[data-test="communautes"] > li')).toHaveLength(1)

    await page.get('[data-test="chercher-communaute"]').setValue('rien du tout')
    expect(page.find('[data-test="aucune"]').exists()).toBe(true)
  })
})

describe('créer une communauté', () => {
  it('envoie le nom, la description et le thème, puis ouvre # général de la nouvelle communauté', async () => {
    let recu: unknown = null
    serveur.use(
      http.post('/api/communautes', async ({ request }) => {
        recu = await request.json()
        return HttpResponse.json(
          { id: 'n', slug: 'devs-du-sahel', nom: 'Devs du Sahel', description: 'Des devs.', theme: 'web', pays: null, nombreMembres: 1, questionsOuvertes: 0, membre: true, moderateur: true },
          { status: 201 },
        )
      }),
    )
    const { page, routeur } = await ouvrir('/communautes/nouvelle')
    await vi.waitFor(() => expect(page.find('[data-test="nouvelle-communaute"]').exists()).toBe(true))
    const champs = page.findAll('[data-test="nouvelle-communaute"] input:not([type="radio"]), [data-test="nouvelle-communaute"] textarea')
    await champs[0]!.setValue('  Devs du Sahel ')
    await champs[1]!.setValue('Des développeurs de Niamey à Nouakchott.')
    // L'aperçu de la bannière suit la saisie.
    expect(page.find('[data-test="banniere"]').text()).toContain('Devs du Sahel')
    await page.get('[data-test="nouvelle-communaute"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.fullPath).toBe('/communautes/devs-du-sahel?salon=general'))
    expect(recu).toEqual({ nom: 'Devs du Sahel', description: 'Des développeurs de Niamey à Nouakchott.', theme: 'web' })
  })

  it('une antenne demande son pays ; au-delà de trois par jour, on le dit', async () => {
    serveur.use(http.post('/api/communautes', () => HttpResponse.json({ code: 'trop_de_requetes' }, { status: 429, headers: { 'Retry-After': '3600' } })))
    const { page } = await ouvrir('/communautes/nouvelle')
    await vi.waitFor(() => expect(page.find('[data-test="nouvelle-communaute"]').exists()).toBe(true))
    expect(page.find('[data-test="pays"]').exists()).toBe(false)
    await page.get('input[type="radio"][value="antenne"]').setValue(true)
    expect(page.find('[data-test="pays"]').exists()).toBe(true)
    const champs = page.findAll('[data-test="nouvelle-communaute"] input:not([type="radio"]), [data-test="nouvelle-communaute"] textarea')
    await champs[0]!.setValue('Antenne Bamako')
    await champs[1]!.setValue('Les devs de Bamako se retrouvent ici.')
    await page.get('[data-test="nouvelle-communaute"]').trigger('submit')
    await vi.waitFor(() => expect(page.text()).toContain("Tu as déjà créé trois communautés aujourd'hui"))
  })
})

describe('créer un salon', () => {
  it('un modérateur voit « + » à côté des salons, crée le salon, et y arrive', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.find('[data-test="ajouter-salon"]').exists()).toBe(true))
    await page.get('[data-test="ajouter-salon"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="nouveau-salon"]').exists()).toBe(true))
    const champs = page.findAll('[data-test="nouveau-salon"] input')
    await champs[0]!.setValue('Offres d’emploi')
    expect(page.get('[data-test="apercu-salon"]').text()).toBe('offres-d-emploi')
    await champs[1]!.setValue('Les offres de la région, sans arnaque.')
    await page.get('[data-test="nouveau-salon"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.query.salon).toBe('offres-d-emploi'))
    await vi.waitFor(() => expect(page.find('[data-test="barre-salon-offres-d-emploi"]').exists()).toBe(true))
  })

  it('un salon du même nom est refusé, et un non-modérateur ne voit ni « + » ni le formulaire', async () => {
    const flutter = await ouvrir('/communautes/flutter/salons/nouveau')
    await vi.waitFor(() => expect(flutter.page.find('[data-test="nouveau-salon"]').exists()).toBe(true))
    const champs = flutter.page.findAll('[data-test="nouveau-salon"] input')
    await champs[0]!.setValue('entraide')
    await champs[1]!.setValue('Un deuxième salon d’entraide.')
    await flutter.page.get('[data-test="nouveau-salon"]').trigger('submit')
    await vi.waitFor(() => expect(flutter.page.text()).toContain('Un salon porte déjà ce nom.'))

    const ia = await ouvrir('/communautes/ia/salons/nouveau')
    await vi.waitFor(() => expect(ia.page.find('[data-test="reserve-moderation"]').exists()).toBe(true))
    expect(ia.page.find('[data-test="nouveau-salon"]').exists()).toBe(false)
    expect(ia.page.find('[data-test="ajouter-salon"]').exists()).toBe(false)
  })
})
