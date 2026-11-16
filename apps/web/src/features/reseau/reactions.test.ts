import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { rafraichirActivite } from './activite'

enableAutoUnmount(afterEach)

const AMINA = 'd2e00000-0000-4000-8000-000000000002'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  await rafraichirActivite()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  return mount(App, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
}

describe('réagir à un message (#157)', () => {
  it('montre les réactions, en ajoute une depuis le menu, et la retire d’un clic sur la pastille', async () => {
    const page = await ouvrir(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(page.findAll('[data-test="reaction"]')).toHaveLength(1))
    // Amina a aimé mon message : ❤️ 1, et son prénom au survol.
    const coeur = page.get('[data-test="reaction"]')
    expect(coeur.text()).toBe('❤️1')
    expect(coeur.attributes('title')).toBe('Amina')
    expect(coeur.attributes('aria-pressed')).toBe('false')

    // Je réagis à son message avec 👍 depuis le menu « ⋯ ».
    const recu = page.findAll('[data-message]').find((l) => l.classes().includes('bulle--autre'))!
    await recu.get('[data-test="actions-message"]').trigger('click')
    await recu.get('[data-test="reaction-rapide"]').trigger('click')
    await vi.waitFor(() => expect(recu.find('[data-test="reaction"]').exists()).toBe(true))
    const pouce = recu.get('[data-test="reaction"]')
    expect(pouce.text()).toBe('👍1')
    expect(pouce.attributes('aria-pressed')).toBe('true')
    expect(pouce.attributes('title')).toBe('Toi')

    // Je rejoins le ❤️ d'Amina : 2, puis je retire mon 👍.
    await page.get('[data-test="reaction"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="reaction"]').some((r) => r.text() === '❤️2')).toBe(true))
    await pouce.trigger('click')
    await vi.waitFor(() => expect(recu.find('[data-test="reaction"]').exists()).toBe(false))
  })

  it('le « + » ouvre tous les emojis', async () => {
    const page = await ouvrir(`/messages/${AMINA}`)
    const recu = await vi.waitFor(() => {
      const l = page.findAll('[data-message]').find((x) => x.classes().includes('bulle--autre'))
      if (!l) throw new Error('pas encore')
      return l
    })
    await recu.get('[data-test="actions-message"]').trigger('click')
    await recu.get('[data-test="reaction-plus"]').trigger('click')
    await recu.get('[data-test="chercher-emoji"]').setValue('fusee')
    await recu.get('[data-test="emoji"]').trigger('click')
    await vi.waitFor(() => expect(recu.get('[data-test="reaction"]').text()).toBe('🚀1'))
  })
})
