import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { CLASSE_PLEIN_ECRAN, usePleinEcranTelephone } from './pleinEcran'

const actif = ref(true)
const Discussion = defineComponent({
  setup() {
    usePleinEcranTelephone(() => actif.value)
    return () => h('div')
  },
})
const plein = () => document.documentElement.classList.contains(CLASSE_PLEIN_ECRAN)

describe('usePleinEcranTelephone', () => {
  afterEach(() => {
    actif.value = true
    document.documentElement.classList.remove(CLASSE_PLEIN_ECRAN)
  })

  it('pose la classe tant que la discussion est ouverte, et la retire quand on la quitte', async () => {
    const vue = mount(Discussion)
    expect(plein()).toBe(true)
    actif.value = false
    await nextTick()
    expect(plein()).toBe(false)
    actif.value = true
    await nextTick()
    expect(plein()).toBe(true)
    vue.unmount()
    // En quittant la page, l'en-tête et le pied du site doivent revenir.
    expect(plein()).toBe(false)
  })
})
