import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import VitrinePage from './VitrinePage.vue'
import { vitrineDemo } from '../../mocks/vitrine'

async function monter(adresse = '/vitrine') {
  // Mode composition : la langue est une ref à l'exécution (le type de createI18n ne le dit pas).
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(VitrinePage, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('[role="status"]').exists()).toBe(false))
  return { page, routeur }
}

describe('Vitrine', () => {
  it('affiche chaque projet en ligne, avec son équipe et ses pays', async () => {
    const { page } = await monter()
    const cartes = page.findAll('[data-test="projets"] > li')
    expect(cartes).toHaveLength(vitrineDemo.length)
    expect(cartes[0]!.text()).toContain('Boutique en ligne pour une couturière de Thiès')
    expect(cartes[0]!.text()).toContain('Awa Diop, Koffi Yao')
    expect(cartes[0]!.text()).toContain('Sénégal · Côte d’Ivoire')
    expect(cartes[0]!.text()).toContain('Réalisé pour une PME')
  })

  it('met « Hébergé sur Systalink » seulement sur les projets DataCloud', async () => {
    const { page } = await monter()
    const surDatacloud = vitrineDemo.filter((p) => p.hebergeur === 'datacloud').length
    expect(page.findAll('[data-test="badge-systalink"]')).toHaveLength(surDatacloud)
  })

  it('ouvre le lien du projet sans donner la main à la page ouverte', async () => {
    const { page } = await monter()
    const lien = page.get('[data-test="projets"] a')
    expect(lien.attributes('href')).toBe('https://couture-thies.example.com')
    expect(lien.attributes('target')).toBe('_blank')
    expect(lien.attributes('rel')).toBe('noopener noreferrer nofollow ugc')
  })

  it("n'affiche pas un lien qui n'est pas en https", async () => {
    serveur.use(
      http.get('/api/vitrine', () =>
        HttpResponse.json({ elements: [{ ...vitrineDemo[0]!, url: 'javascript:alert(1)' }], curseurSuivant: null }),
      ),
    )
    const { page } = await monter()
    expect(page.find('[data-test="projets"] a').exists()).toBe(false)
  })

  it('filtre sur Systalink et garde le filtre dans l’adresse', async () => {
    let demande: string | null = null
    serveur.use(
      http.get('/api/vitrine', ({ request }) => {
        demande = new URL(request.url).searchParams.get('hebergeur')
        return HttpResponse.json({ elements: [vitrineDemo[0]], curseurSuivant: null })
      }),
    )
    const { page, routeur } = await monter()
    expect(demande).toBeNull()
    await page.findAll('.filtres button')[1]!.trigger('click')
    await vi.waitFor(() => expect(demande).toBe('datacloud'))
    expect(routeur.currentRoute.value.query.hebergeur).toBe('datacloud')
  })

  it('ajoute la page suivante sous les projets déjà affichés', async () => {
    serveur.use(
      http.get('/api/vitrine', ({ request }) => {
        const curseur = new URL(request.url).searchParams.get('curseur')
        return curseur
          ? HttpResponse.json({ elements: [vitrineDemo[1]], curseurSuivant: null })
          : HttpResponse.json({ elements: [vitrineDemo[0]], curseurSuivant: 'page-2' })
      }),
    )
    const { page } = await monter()
    await page.get('button.suite').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="projets"] > li')).toHaveLength(2))
    expect(page.find('button.suite').exists()).toBe(false)
  })

  it('le dit simplement quand aucun projet n’est en ligne', async () => {
    serveur.use(http.get('/api/vitrine', () => HttpResponse.json({ elements: [], curseurSuivant: null })))
    const { page } = await monter()
    expect(page.get('[data-test="vide"]').text()).toBe('Aucun projet en ligne pour le moment.')
  })

  it("propose de réessayer quand l'API ne répond pas", async () => {
    serveur.use(http.get('/api/vitrine', () => new HttpResponse(null, { status: 503 })))
    const { page } = await monter()
    expect(page.get('[role="alert"]').text()).toContain('Réessayer')
  })
})
