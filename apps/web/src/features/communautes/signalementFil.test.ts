import { afterEach, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import axe from 'axe-core'
import App from '../../App.vue'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import { serveur } from '../../test-setup'

enableAutoUnmount(afterEach)
async function ouvrir() {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push('/communautes/flutter')
  const page = mount(App, { global: { plugins: [routeur, i18n] }, attachTo: document.body })
  await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(4))
  return { page, routeur }
}

it('signale la publication directement dans le fil sans navigation et reste accessible', async () => {
  let recu: unknown
  serveur.use(http.post('/api/signalements', async ({ request }) => {
    recu = await request.json()
    return new HttpResponse(null, { status: 202 })
  }))
  const { page, routeur } = await ouvrir()
  const carte = page.get('[data-test="publication"]')
  const cibleId = carte.attributes('aria-labelledby')!.replace('auteur-', '')
  await carte.get('[data-test="signaler"]').trigger('click')
  expect(carte.get('.signaler').classes()).toContain('signaler--integre')
  expect(carte.find('.signaler__panneau').exists()).toBe(true)
  const resultat = await axe.run(carte.element as HTMLElement, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }, rules: { 'color-contrast': { enabled: false } },
  })
  expect(resultat.violations.map((v) => v.id)).toEqual([])
  await carte.get('input[value="hors_sujet"]').setValue(true)
  await carte.get('.signaler__panneau').trigger('submit')
  await vi.waitFor(() => expect(carte.find('[data-test="signale"]').exists()).toBe(true))
  expect(recu).toEqual({ cibleType: 'publication', cibleId, motif: 'hors_sujet' })
  expect(routeur.currentRoute.value.fullPath).toBe('/communautes/flutter')
  expect(page.findAll('[data-test="publication"]')).toHaveLength(4)
})

it('annule avec Échap sans envoyer et rend le focus au bouton', async () => {
  let envois = 0
  serveur.use(http.post('/api/signalements', () => { envois++; return new HttpResponse(null, { status: 202 }) }))
  const { page } = await ouvrir()
  const carte = page.get('[data-test="publication"]')
  const bouton = carte.get('[data-test="signaler"]')
  await bouton.trigger('click')
  await carte.get('input[value="spam"]').setValue(true)
  await carte.get('.signaler__panneau').trigger('keydown', { key: 'Escape' })
  expect(carte.find('.signaler__panneau').exists()).toBe(false)
  expect(document.activeElement).toBe(bouton.element)
  expect(envois).toBe(0)
})

it('garde le motif en cas d’échec et permet de réessayer dans le fil', async () => {
  let envois = 0
  serveur.use(http.post('/api/signalements', () => ++envois === 1 ? HttpResponse.json({ code: 'indisponible' }, { status: 503 }) : new HttpResponse(null, { status: 202 })))
  const { page, routeur } = await ouvrir()
  const carte = page.get('[data-test="publication"]')
  await carte.get('[data-test="signaler"]').trigger('click')
  await carte.get('input[value="spam"]').setValue(true)
  await carte.get('.signaler__panneau').trigger('submit')
  await vi.waitFor(() => expect(carte.find('[role="alert"]').exists()).toBe(true))
  expect((carte.get('input[value="spam"]').element as HTMLInputElement).checked).toBe(true)
  await carte.get('.signaler__panneau').trigger('submit')
  await vi.waitFor(() => expect(carte.find('[data-test="signale"]').exists()).toBe(true))
  expect(routeur.currentRoute.value.path).toBe('/communautes/flutter')
})

it('signale aussi une réponse humaine en ciblant son identifiant', async () => {
  let recu: unknown
  serveur.use(http.post('/api/signalements', async ({ request }) => { recu = await request.json(); return new HttpResponse(null, { status: 202 }) }))
  const { page, routeur } = await ouvrir()
  const carte = page.get('[aria-labelledby="auteur-8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e"]')
  await carte.get('[data-test^="reponses-"]').trigger('click')
  await vi.waitFor(() => expect(carte.find('[data-test="reponse"]').exists()).toBe(true))
  const reponse = carte.get('[data-test="reponse"]')
  await reponse.get('[data-test="signaler"]').trigger('click')
  await reponse.get('input[value="irrespectueux"]').setValue(true)
  await reponse.get('.signaler__panneau').trigger('submit')
  await vi.waitFor(() => expect(reponse.find('[data-test="signale"]').exists()).toBe(true))
  expect(recu).toEqual({ cibleType: 'reponse', cibleId: 'b2b2b2b2-c3c3-4d4d-8e5e-f6f6f6f6f6f6', motif: 'irrespectueux' })
  expect(routeur.currentRoute.value.path).toBe('/communautes/flutter')
})
