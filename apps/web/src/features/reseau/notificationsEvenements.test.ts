import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { rafraichirActivite } from './activite'

/** Les notifications des missions et des hackathons (demande #201) : une phrase claire pour chaque type, qui mène à la bonne page. */
const MISSION = 'd8f4b2a6-1c3e-4a5b-9d7f-6e2c0a8b4f13'
const HACKATHON = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
const KOFI = { id: 'c1a00000-0000-4000-8000-000000000002', nom: 'Kofi Yao', avatarUrl: null }

const notification = (type: string, lien: string, personne: typeof KOFI | null) => ({
  id: crypto.randomUUID(),
  type,
  personne,
  cercle: null,
  lien,
  creeLe: new Date().toISOString(),
  lue: false,
})

describe('les notifications des missions et des hackathons', () => {
  it.each([
    ['fr', 'proposition_recue', `/missions/${MISSION}`, KOFI, 'Kofi Yao propose ses services pour ta mission.'],
    ['fr', 'devis_recu', `/missions/${MISSION}`, KOFI, "Kofi Yao t'a envoyé un devis."],
    ['fr', 'mission_attribuee', `/missions/${MISSION}`, KOFI, "Kofi Yao t'a choisi pour sa mission."],
    ['fr', 'candidature_recue', `/hackathons/${HACKATHON}`, KOFI, 'Kofi Yao a candidaté à ton hackathon.'],
    ['fr', 'candidature_acceptee', `/hackathons/${HACKATHON}`, null, 'Ta candidature à un hackathon est acceptée.'],
    ['fr', 'candidature_refusee', `/hackathons/${HACKATHON}`, null, "Ta candidature à un hackathon n'a pas été retenue."],
    ['fr', 'candidature_attente', `/hackathons/${HACKATHON}`, null, "Tu es sur la liste d'attente d'un hackathon."],
    ['fr', 'invitation_equipe', `/hackathons/${HACKATHON}`, KOFI, "Kofi Yao t'a ajouté à son équipe de hackathon."],
    ['en', 'candidature_acceptee', `/hackathons/${HACKATHON}`, null, 'Your application to a hackathon is accepted.'],
  ] as const)('%s · %s : écrit la phrase et mène à la page', async (langue, type, lien, personne, phrase) => {
    serveur.use(http.get('/api/me/notifications', () => HttpResponse.json({ elements: [notification(type, lien, personne)], curseurSuivant: null })))
    ;(i18n.global.locale as unknown as { value: string }).value = langue
    await chargerSession()
    await rafraichirActivite()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/notifications')
    const page = mount(App, { global: { plugins: [i18n, routeur] } })

    await vi.waitFor(() => expect(page.findAll('[data-test="notifications"] li')).toHaveLength(1))
    const ligne = page.get('[data-test="notifications"] li')
    expect(ligne.text()).toContain(phrase)
    expect(ligne.find('a').attributes('href')).toBe(lien)
  })
})
