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

describe('créer une communauté, une étape à la fois', () => {
  it('le nom, le thème (qui fait avancer), la description, l’aperçu, puis on la salue et on entre dans # général', async () => {
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
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    expect(page.text()).toContain('Étape 1 sur 4')
    expect(page.get('[data-test="suivant"]').attributes('disabled')).toBeDefined()

    await page.get('[data-test="champ-nom"]').setValue('  Devs du Sahel ')
    expect(page.text()).toContain('jamii/communautes/devs-du-sahel')
    await page.get('[data-test="champ-nom"]').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.find('[data-test="theme-web"]').exists()).toBe(true))
    expect(page.text()).toContain('Étape 2 sur 4')

    // Choisir un thème fait avancer tout seul.
    await page.get('[data-test="theme-web"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="champ-description"]').exists()).toBe(true), { timeout: 3000 })
    expect(page.text()).toContain('À quoi sert Devs du Sahel')
    await page.get('[data-test="champ-description"]').setValue('Des développeurs de Niamey à Nouakchott.')
    await page.get('[data-test="suivant"]').trigger('click')

    await vi.waitFor(() => expect(page.find('[data-test="banniere"]').exists()).toBe(true))
    expect(page.get('[data-test="banniere"]').text()).toContain('Devs du Sahel')
    await page.get('[data-test="suivant"]').trigger('click')

    await vi.waitFor(() => expect(page.find('[data-test="succes"]').exists()).toBe(true))
    expect(page.get('[data-test="succes"]').text()).toContain('Devs du Sahel est en ligne')
    expect(recu).toEqual({ nom: 'Devs du Sahel', description: 'Des développeurs de Niamey à Nouakchott.', theme: 'web' })
    await page.get('[data-test="entrer"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.fullPath).toBe('/communautes/devs-du-sahel?salon=general'))
  })

  it('on peut revenir en arrière ; une antenne demande son pays ; au-delà de trois par jour, on le dit', async () => {
    serveur.use(http.post('/api/communautes', () => HttpResponse.json({ code: 'trop_de_requetes' }, { status: 429, headers: { 'Retry-After': '3600' } })))
    const { page } = await ouvrir('/communautes/nouvelle')
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    await page.get('[data-test="champ-nom"]').setValue('Antenne Bamako')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="theme-antenne"]').exists()).toBe(true))

    await page.get('[data-test="precedent"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    expect((page.get('[data-test="champ-nom"]').element as HTMLInputElement).value).toBe('Antenne Bamako')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="theme-antenne"]').exists()).toBe(true))

    // Une antenne n'avance pas toute seule : elle demande d'abord son pays.
    await page.get('[data-test="theme-antenne"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="pays"]').exists()).toBe(true))
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="champ-description"]').exists()).toBe(true))
    await page.get('[data-test="champ-description"]').setValue('Les devs de Bamako se retrouvent ici.')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="banniere"]').exists()).toBe(true))
    await page.get('[data-test="suivant"]').trigger('click')
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

describe('supprimer un salon', () => {
  it('un modérateur supprime un salon après confirmation, et revient dans # général ; # général ne se supprime pas', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter?salon=projets')
    await vi.waitFor(() => expect(page.find('[data-test="supprimer-salon"]').exists()).toBe(true))
    expect(page.get('[data-test="confirmer-suppression"]').text()).toContain('Supprimer #projets')
    expect(page.get('[data-test="confirmer-suppression"]').text()).toContain("C'est définitif")

    await page.get('[data-test="supprimer-salon"]').trigger('click')
    await page.get('[data-test="confirmer"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.query.salon).toBe('general'))
    // La barre reste en place et relit ses salons après la suppression : on attend que #projets en soit parti.
    await vi.waitFor(() => expect(page.find('[data-test="barre-salon-projets"]').exists()).toBe(false))
    expect(page.find('[data-test="barre-salon-entraide"]').exists()).toBe(true)
    // Dans # général, pas de bouton de suppression.
    await vi.waitFor(() => expect(page.find('[data-test="salons"] h1').text()).toBe('# général'))
    expect(page.find('[data-test="supprimer-salon"]').exists()).toBe(false)
  })

  it('un membre qui ne modère pas ne voit pas le bouton', async () => {
    const { page } = await ouvrir('/communautes/ia?salon=projets')
    await vi.waitFor(() => expect(page.find('[data-test="salons"] h1').exists()).toBe(true))
    expect(page.find('[data-test="supprimer-salon"]').exists()).toBe(false)
  })
})
