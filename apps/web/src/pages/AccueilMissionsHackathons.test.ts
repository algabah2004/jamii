import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../i18n'
import { creerRouteur } from '../router'
import AccueilMissionsHackathons from './AccueilMissionsHackathons.vue'

async function monter(langue: 'fr' | 'en' = 'fr') {
  ;(i18n.global.locale as unknown as { value: string }).value = langue
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push('/')
  return mount(AccueilMissionsHackathons, { global: { plugins: [i18n, routeur] } })
}

describe('l’accueil présente les missions et les hackathons', () => {
  it('montre une carte par module, ses trois temps et ses deux actions', async () => {
    const section = await monter()
    const missions = section.get('[data-test="vedette-missions"]')
    expect(missions.get('h3').text()).toBe('Les missions')
    expect(missions.findAll('ol > li')).toHaveLength(3)
    expect(missions.text()).toContain('Livrer jalon par jalon')
    expect(missions.get('[data-test="vedette-missions-voir"]').attributes('href')).toBe('/missions')
    expect(missions.find('a[href="/missions/nouvelle"]').exists()).toBe(true)

    const hackathons = section.get('[data-test="vedette-hackathons"]')
    expect(hackathons.get('h3').text()).toBe('Les hackathons')
    expect(hackathons.findAll('ol > li')).toHaveLength(3)
    expect(hackathons.text()).toContain("liste d'attente")
    expect(hackathons.get('[data-test="vedette-hackathons-voir"]').attributes('href')).toBe('/hackathons')
    expect(hackathons.find('a[href="/hackathons/nouveau"]').exists()).toBe(true)
  })

  it('cache les aperçus décoratifs aux lecteurs d’écran et nomme chaque carte par son titre', async () => {
    const section = await monter()
    for (const cle of ['missions', 'hackathons']) {
      const carte = section.get(`[data-test="vedette-${cle}"]`)
      expect(carte.get('.carte-module__apercu').attributes('aria-hidden')).toBe('true')
      expect(carte.attributes('aria-labelledby')).toBe(`vedette-${cle}`)
      expect(carte.find(`#vedette-${cle}`).exists()).toBe(true)
    }
  })

  it('existe aussi en anglais', async () => {
    const section = await monter('en')
    expect(section.get('#vedette-titre').text()).toBe('Paid missions, hackathons open to all')
    expect(section.get('[data-test="vedette-missions-voir"]').text()).toContain('See missions')
  })
})
