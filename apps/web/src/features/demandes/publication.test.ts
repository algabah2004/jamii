import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { orientationDemo } from '../../mocks/demandes'
import { LIMITES, resumeValide, type Demande } from './demandes'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

/** Une demande dont le plan est prêt, créée par la maquette de l'API (comme le fait l'écran « Décris ton besoin »). */
async function demandePrete(): Promise<string> {
  const reponse = await fetch('/api/demandes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ titre: 'Nos dossiers', description: 'Le cabinet perd ses dossiers entre associés.' }),
  })
  return ((await reponse.json()) as Demande).id
}

describe('publier sa demande', () => {
  it('valide le résumé : ni trop court, ni trop long', () => {
    expect(resumeValide('trop court')).toBe(false)
    expect(resumeValide('Une boutique en ligne pour un atelier de couture.')).toBe(true)
    expect(resumeValide('x'.repeat(LIMITES.resumeMax + 1))).toBe(false)
    expect(resumeValide('   ')).toBe(false)
  })

  it('propose le résumé de l’orientation, que la personne peut modifier, puis publie', async () => {
    const id = await demandePrete()
    let corps = ''
    serveur.use(
      http.post('/api/demandes/:id/publication', async ({ request }) => {
        corps = ((await request.json()) as { resume: string }).resume
        return HttpResponse.json({
          ...((await (await fetch(`/api/demandes/${id}`)).json()) as Demande),
          publication: { publieeLe: '2026-11-14T19:00:00Z', resume: corps },
        })
      }),
    )
    const { page } = await ouvrir(`/demandes/${id}`)
    await vi.waitFor(() => expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(true), { timeout: 4000 })

    const champ = page.get('[data-test="publication"] textarea')
    expect((champ.element as HTMLTextAreaElement).value).toBe(orientationDemo().resume.slice(0, LIMITES.resumeMax))
    await champ.setValue('Un cabinet cherche à retrouver ses dossiers sans clé USB.')
    await page.get('[data-test="publication-formulaire"]').trigger('submit')

    await vi.waitFor(() => expect(page.find('[data-test="publiee"]').exists()).toBe(true))
    expect(corps).toBe('Un cabinet cherche à retrouver ses dossiers sans clé USB.')
    expect(page.get('[data-test="resume-publie"]').text()).toBe('Un cabinet cherche à retrouver ses dossiers sans clé USB.')
    expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(false)
  })

  it('ne laisse pas publier un résumé trop court', async () => {
    const id = await demandePrete()
    const { page } = await ouvrir(`/demandes/${id}`)
    await vi.waitFor(() => expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(true), { timeout: 4000 })
    await page.get('[data-test="publication"] textarea').setValue('trop court')
    expect(page.get('[data-test="publier"]').attributes('disabled')).toBeDefined()
    expect(page.get('[data-test="publication"]').text()).toContain('entre 20 et 600 caractères')
  })

  it('dit quand le cahier des charges n’est pas prêt, sans rien publier', async () => {
    const id = await demandePrete()
    serveur.use(http.post('/api/demandes/:id/publication', () => HttpResponse.json({ code: 'plan_non_pret' }, { status: 409 })))
    const { page } = await ouvrir(`/demandes/${id}`)
    await vi.waitFor(() => expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(true), { timeout: 4000 })
    await page.get('[data-test="publication-formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="publication-erreur"]').exists()).toBe(true))
    expect(page.get('[data-test="publication-erreur"]').text()).toContain('Tu pourras le publier')
    expect(page.find('[data-test="publiee"]').exists()).toBe(false)
  })

  it('montre une demande déjà publiée, sans formulaire', async () => {
    const id = await demandePrete()
    await fetch(`/api/demandes/${id}`)
    await fetch(`/api/demandes/${id}/publication`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resume: 'Un cabinet veut retrouver ses dossiers en quelques secondes.' }),
    })
    const { page } = await ouvrir(`/demandes/${id}`)
    await vi.waitFor(() => expect(page.find('[data-test="publiee"]').exists()).toBe(true), { timeout: 4000 })
    expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(false)
  })
})
