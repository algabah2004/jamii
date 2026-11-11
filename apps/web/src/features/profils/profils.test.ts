import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { lireCompetences, pourcentage } from './profils'

async function ouvrir() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push('/profil')
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.text()).not.toContain('Chargement'))
  return page
}

const probleme = (status: number, code: string, erreurs?: { champ: string; code: string }[]) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code, erreurs }, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('Mon profil', () => {
  it('sans profil, propose de le générer depuis GitHub, puis l’affiche, toujours privé', async () => {
    const page = await ouvrir()
    expect(page.find('[data-test="sans-profil"]').text()).toContain('Rien de privé n\'est lu')
    await page.get('[data-test="generer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="langages"]').exists()).toBe(true), { timeout: 5000 })

    expect(page.get('[data-test="langages"]').text()).toContain('Dart')
    expect(page.get('[data-test="langages"]').text()).toMatch(/75\s?%/)
    expect(page.get('[data-test="depots"]').text()).toContain('marche-facile')
    expect(page.get('[data-test="visibilite"]').text()).toContain('privé')
    expect(page.find('[data-test="projets-vide"]').exists()).toBe(true)
  })

  it('publier puis rendre privé', async () => {
    const page = await profilPret()
    await page.get('[data-test="publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="visibilite"]').text()).toContain('publié'))
    await page.get('[data-test="publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="visibilite"]').text()).toContain('privé'))
  })

  it('modifier ce que je déclare : la ville et les compétences s’ajoutent au profil', async () => {
    const page = await profilPret()
    await page.get('[data-test="modifier"]').trigger('click')
    const formulaire = page.get('[data-test="formulaire"]')
    await formulaire.findAll('input')[0]!.setValue('Thiès')
    await formulaire.findAll('input').at(-1)!.setValue('UX design, Mobile Money')
    await formulaire.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="message"]').text()).toBe('Ton profil est enregistré.'))
    expect(page.text()).toContain('Thiès')
    expect(page.get('[data-test="competences"]').text()).toContain('Mobile Money')
  })

  it('si le profil a changé ailleurs (409), recharge la dernière version et le dit', async () => {
    const page = await profilPret()
    serveur.use(http.patch('/api/me/profil', () => probleme(409, 'modification_concurrente')))
    await page.get('[data-test="modifier"]').trigger('click')
    await page.get('[data-test="formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="message"]').text()).toContain('autre onglet'))
    expect(page.find('[data-test="formulaire"]').exists()).toBe(false)
  })

  it('une valeur refusée par l’API est signalée sur son champ', async () => {
    const page = await profilPret()
    serveur.use(http.patch('/api/me/profil', () => probleme(400, 'validation', [{ champ: 'ville', code: 'trop_long' }])))
    await page.get('[data-test="modifier"]').trigger('click')
    await page.get('[data-test="formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[aria-invalid="true"]').exists()).toBe(true))
  })

  it('si GitHub ne répond pas, le dit et garde le profil précédent', async () => {
    serveur.use(
      http.get('/api/me/profil', () =>
        HttpResponse.json({
          utilisateurId: 'x', nom: 'Awa Diop', disponibilite: 'disponible', langages: [{ nom: 'Dart', part: 1 }],
          competences: [], projets: [], depotsGithub: [], fiabilite: [], misAJourLe: '2026-11-11T10:00:00Z',
          publie: false, afficherResumeIa: true, version: 3,
          generation: { statut: 'echec', erreur: 'github_indisponible' },
        }),
      ),
    )
    const page = await ouvrir()
    expect(page.text()).toContain('GitHub ne répond pas')
    expect(page.get('[data-test="langages"]').text()).toContain('Dart')
  })
})

describe('outils', () => {
  it('la part d’un langage s’écrit en pourcentage arrondi', () => {
    expect(pourcentage(0.745, 'en')).toBe('75%')
  })
  it('les compétences saisies : sans vides ni doublons', () => {
    expect(lireCompetences(' Flutter, ,firebase,Flutter ')).toEqual(['Flutter', 'firebase'])
  })
})

async function profilPret() {
  const page = await ouvrir()
  await page.get('[data-test="generer"]').trigger('click')
  await vi.waitFor(() => expect(page.find('[data-test="langages"]').exists()).toBe(true), { timeout: 5000 })
  return page
}
