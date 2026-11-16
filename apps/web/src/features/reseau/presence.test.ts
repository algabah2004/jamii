import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import ReglagePresence from './ReglagePresence.vue'
import { rafraichirActivite } from './activite'
import { textePresence } from './presence'

enableAutoUnmount(afterEach)

const KOFI = 'c1a00000-0000-4000-8000-000000000002'
const AMINA = 'd2e00000-0000-4000-8000-000000000002'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  await rafraichirActivite()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  return mount(App, { global: { plugins: [i18n, routeur] } })
}

describe('la présence (#157)', () => {
  const t = (cle: string, valeurs?: Record<string, unknown>) => i18n.global.t(cle, valeurs ?? {})
  const maintenant = new Date(2026, 10, 16, 15, 0)
  const etat = (vu: Date | null, enLigne = false) => ({ personneId: KOFI, enLigne, vuLe: vu?.toISOString() ?? null })

  it('dit « En ligne », puis depuis quand on ne l’a pas vu', () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    expect(textePresence(etat(null, true), t, 'fr', maintenant)).toBe('En ligne')
    expect(textePresence(etat(new Date(2026, 10, 16, 14, 59, 30)), t, 'fr', maintenant)).toBe("Vu à l'instant")
    expect(textePresence(etat(new Date(2026, 10, 16, 14, 55)), t, 'fr', maintenant)).toBe('Vu il y a 5 min')
    expect(textePresence(etat(new Date(2026, 10, 16, 9, 5)), t, 'fr', maintenant)).toBe("Vu aujourd'hui à 09:05")
    expect(textePresence(etat(new Date(2026, 10, 15, 22, 40)), t, 'fr', maintenant)).toBe('Vu hier à 22:40')
    expect(textePresence(etat(new Date(2026, 10, 12, 8, 0)), t, 'fr', maintenant)).toBe('Vu le 12 nov.')
    // Caché, jamais vu ou plus d'une semaine : on n'affiche rien.
    expect(textePresence(etat(null), t, 'fr', maintenant)).toBe('')
    expect(textePresence(undefined, t, 'fr', maintenant)).toBe('')
  })

  it('montre qui est en ligne dans la liste et dans la discussion', async () => {
    const page = await ouvrir(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(page.find('[data-test="presence"]').text()).toBe('En ligne'))
    // Kofi est en ligne, Amina non : un seul point vert.
    await vi.waitFor(() => expect(page.findAll('[data-test="conversations"] [data-test="en-ligne"]')).toHaveLength(1))
    await page.vm.$router.push(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(page.find('[data-test="presence"]').text()).toBe('Vu il y a 25 min'))
  })

  it('je peux cacher ma présence, et je ne vois plus celle des autres', async () => {
    const reglage = mount(ReglagePresence, { global: { plugins: [i18n] } })
    const bouton = await vi.waitFor(() => reglage.get('[data-test="basculer-presence"]'))
    expect(bouton.attributes('aria-checked')).toBe('true')
    await bouton.trigger('click')
    await vi.waitFor(() => expect(reglage.get('[data-test="basculer-presence"]').attributes('aria-checked')).toBe('false'))

    const page = await ouvrir(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(page.find('[data-test="fil"]').exists()).toBe(true))
    await new Promise((r) => setTimeout(r, 50))
    expect(page.find('[data-test="presence"]').exists()).toBe(false)
    expect(page.find('[data-test="en-ligne"]').exists()).toBe(false)
  })
})
