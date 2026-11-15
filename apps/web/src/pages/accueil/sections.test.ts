import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import type { Component } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import AccueilHackathons from './AccueilHackathons.vue'
import AccueilMissions from './AccueilMissions.vue'

async function monter(composant: Component, langue: 'fr' | 'en' = 'fr'): Promise<VueWrapper> {
  ;(i18n.global.locale as unknown as { value: string }).value = langue
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push('/')
  return mount(composant, { global: { plugins: [i18n, routeur] } })
}

describe('la section des missions, sur l’accueil', () => {
  it('explique le parcours en quatre étapes, puis les deux côtés d’une mission', async () => {
    const s = await monter(AccueilMissions)
    expect(s.get('#section-missions-titre').text()).toContain('embaucher un développeur')
    const etapes = s.findAll('[data-test="section-missions-etapes"] > li')
    expect(etapes.map((e) => e.get('strong').text())).toEqual([
      'Publier la mission', 'Recevoir des propositions', 'Avancer jalon par jalon', 'Payer et donner son avis',
    ])
    expect(s.get('[data-test="cote-client"]').findAll('li')).toHaveLength(3)
    expect(s.get('[data-test="cote-client"] a').attributes('href')).toBe('/missions/nouvelle')
    expect(s.get('[data-test="cote-developpeur"] a').attributes('href')).toBe('/missions')
    expect(s.get('[data-test="section-missions-voir"]').attributes('href')).toBe('/missions')
  })

  it('dit clairement que Jamii n’encaisse pas l’argent', async () => {
    const s = await monter(AccueilMissions)
    expect(s.get('[data-test="section-missions-argent"]').text()).toContain("Jamii n'encaisse pas le paiement")
  })

  it('cache l’aperçu décoratif aux lecteurs d’écran', async () => {
    const s = await monter(AccueilMissions)
    expect(s.get('.section__apercu').attributes('aria-hidden')).toBe('true')
    expect(s.get('[data-test="section-missions"]').attributes('aria-labelledby')).toBe('section-missions-titre')
  })
})

describe('la section des hackathons, sur l’accueil', () => {
  it('présente les trois formats et les quatre étapes', async () => {
    const s = await monter(AccueilHackathons)
    expect(s.get('#section-hackathons-titre').text()).toBe('Organiser un défi, du premier inscrit aux gagnants')
    for (const [cle, titre] of [['hackathon', 'Hackathon'], ['challenge', 'Challenge'], ['concours', 'Concours d\'innovation']]) {
      expect(s.get(`[data-test="format-${cle}"] h4`).text()).toBe(titre)
    }
    expect(s.findAll('[data-test="section-hackathons-etapes"] > li')).toHaveLength(4)
    expect(s.text()).toContain('jamais le projet de sa propre équipe')
    expect(s.get('[data-test="section-hackathons-voir"]').attributes('href')).toBe('/hackathons')
    expect(s.find('a[href="/hackathons/nouveau"]').exists()).toBe(true)
  })

  it('existe aussi en anglais', async () => {
    const s = await monter(AccueilHackathons, 'en')
    expect(s.get('[data-test="format-concours"] h4').text()).toBe('Innovation contest')
    expect(s.get('[data-test="section-hackathons-voir"]').text()).toContain('See hackathons')
  })
})
