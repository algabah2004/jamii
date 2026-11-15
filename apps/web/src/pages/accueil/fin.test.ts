import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import type { Component } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import AccueilAppel from './AccueilAppel.vue'
import AccueilFaq from './AccueilFaq.vue'
import AccueilSystalink from './AccueilSystalink.vue'

async function monter(composant: Component, langue: 'fr' | 'en' = 'fr'): Promise<VueWrapper> {
  ;(i18n.global.locale as unknown as { value: string }).value = langue
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push('/')
  return mount(composant, { global: { plugins: [i18n, routeur] } })
}

describe('la section Systalink, sur l’accueil', () => {
  it('montre les trois moments où Jamii propose un service, et dit que rien n’est imposé', async () => {
    const s = await monter(AccueilSystalink)
    expect(s.get('#section-systalink-titre').text()).toBe('Jamii tourne sur Systalink, à Dakar')
    for (const [cle, service] of [['enLigne', 'DataCloud'], ['site', 'Orion'], ['outils', 'SystaTalk']] as const) {
      expect(s.get(`[data-test="systalink-${cle}"]`).text()).toContain(service)
    }
    expect(s.get('[data-test="systalink-promesse"]').text()).toContain("Tu n'as besoin d'aucun compte Systalink")
    expect(s.get('[data-test="systalink-vitrine"]').attributes('href')).toBe('/vitrine?hebergeur=datacloud')
  })
})

describe('la foire aux questions', () => {
  it('pose neuf questions sur les fonctionnalités, fermées au départ', async () => {
    const s = await monter(AccueilFaq)
    const questions = s.findAll('details')
    expect(questions).toHaveLength(9)
    expect(questions.every((q) => q.attributes('open') === undefined)).toBe(true)
    expect(s.get('[data-test="faq-connexion"] summary').text()).toBe('Comment je me connecte ?')
    expect(s.get('[data-test="faq-paiement"] p').text()).toContain("Jamii n'encaisse pas l'argent")
    expect(s.get('[data-test="faq-donnees"] p').text()).toContain('supprimer ton compte')
  })

  it('ne parle pas de Systalink : elle décrit Jamii', async () => {
    const s = await monter(AccueilFaq)
    expect(s.text()).not.toContain('Systalink')
  })

  it('existe aussi en anglais', async () => {
    const s = await monter(AccueilFaq, 'en')
    expect(s.get('#faq-titre').text()).toBe('Everything you can do on Jamii')
  })
})

describe('l’appel de fin de page', () => {
  it('ouvre deux portes : se connecter pour un développeur, décrire un besoin pour une PME', async () => {
    const s = await monter(AccueilAppel)
    expect(s.get('[data-test="appel-dev"]').attributes('href')).toBe('/connexion')
    expect(s.get('[data-test="appel-dev"]').text()).toContain('Se connecter')
    expect(s.get('[data-test="appel-besoin"]').attributes('href')).toBe('/besoin')
    expect(s.get('[data-test="appel-besoin"]').text()).toContain('Décrire mon besoin')
  })
})
