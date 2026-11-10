import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import Accueil from '../src/pages/Accueil.vue'
import { routes } from '../src/router'

describe('Accueil', () => {
  it('présente Jamii comme un lieu d’échange et invite à rejoindre', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/')
    const page = mount(Accueil, { global: { plugins: [router] } })
    expect(page.text()).toContain('se rencontrent, échangent et construisent ensemble')
    expect(page.text()).toContain('Rejoindre Jamii')
  })
})
