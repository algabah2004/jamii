import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import axe from 'axe-core'
import { http } from 'msw'
import { nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { modifierHackathon, reinitialiserHackathons } from '../../mocks/hackathons'
import { modifierMission, reinitialiserMissions } from '../../mocks/missions'
import HackathonPage from '../hackathons/HackathonPage.vue'
import MissionPage from '../missions/MissionPage.vue'
import EditeurQuestionnaire from './EditeurQuestionnaire.vue'
import { erreursParQuestion, identifiantLibre, manquantes, nettoyer, problemes, versReponses, type Question } from './questionnaire'

const QUESTIONS: Question[] = [
  { id: 'q1', intitule: 'Ton lien GitHub ou ton portfolio', type: 'texte_court', obligatoire: true },
  { id: 'q2', intitule: 'Ton niveau', type: 'choix_unique', obligatoire: true, choix: ['Débutant', 'Expert'] },
  { id: 'q3', intitule: 'Tes disponibilités', type: 'choix_multiples', obligatoire: false, choix: ['Soir', 'Week-end'] },
]

describe('les transformations du questionnaire', () => {
  it('ne garde que les réponses données, dans l’ordre des questions', () => {
    const valeurs = { q3: ['Week-end'], q1: '  https://github.com/kofi ', q2: '' }
    expect(versReponses(QUESTIONS, valeurs)).toEqual([
      { questionId: 'q1', valeur: 'https://github.com/kofi' },
      { questionId: 'q3', valeur: ['Week-end'] },
    ])
    expect(manquantes(QUESTIONS, valeurs)).toEqual(['q2'])
  })

  it('nettoie le questionnaire avant l’envoi et signale ce qui empêche de l’enregistrer', () => {
    const brouillon: Question[] = [
      { id: 'q1', intitule: '  Ton lien  ', type: 'texte_court', obligatoire: false, aide: '  ', choix: ['reste'] },
      { id: 'q2', intitule: 'Ton niveau', type: 'choix_unique', obligatoire: true, choix: [' A ', '', 'A'] },
      { id: 'q3', intitule: 'x', type: 'oui_non', obligatoire: false },
    ]
    expect(nettoyer(brouillon)[0]).toEqual({ id: 'q1', intitule: 'Ton lien', type: 'texte_court', obligatoire: false })
    expect(nettoyer(brouillon)[1]!.choix).toEqual(['A', 'A'])
    expect(problemes(brouillon)).toEqual({ q2: 'doublon', q3: 'intitule' })
    expect(identifiantLibre(brouillon)).toBe('q4')
  })

  it('ramène les erreurs de l’API à leur question', () => {
    expect(erreursParQuestion({ 'reponses.q2': 'obligatoire', 'reponses[0].questionId': 'valeur_inconnue', message: 'trop_court' })).toEqual({ q2: 'obligatoire' })
  })
})

describe('composer son questionnaire', () => {
  it('part d’un modèle, change le type, ajoute un choix et range les questions', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    const questions = ref<Question[]>([])
    const editeur = mount(EditeurQuestionnaire, {
      props: { modelValue: questions.value, 'onUpdate:modelValue': (v: Question[]) => (questions.value = v), contexte: 'hackathon' },
      global: { plugins: [i18n] },
    })
    const suivre = async () => {
      await editeur.setProps({ modelValue: questions.value })
      await nextTick()
    }
    await editeur.get('[data-test="modele-niveau"]').trigger('click')
    await suivre()
    expect(questions.value[0]).toMatchObject({ id: 'q1', type: 'choix_unique', obligatoire: true, choix: ['Débutant', "À l'aise", 'Expert'] })

    await editeur.get('[data-test="ajouter-question"]').trigger('click')
    await suivre()
    await editeur.findAll('[data-test="type"]')[1]!.setValue('choix_multiples')
    await suivre()
    expect(questions.value[1]).toMatchObject({ id: 'q2', type: 'choix_multiples', choix: ['', ''] })
    // Une question vide et des choix vides empêchent d'enregistrer, et c'est dit sur la question.
    expect(editeur.findAll('[data-test="question-editee"]')[1]!.text()).toContain('Écrivez la question')

    await editeur.findAll('[data-test="type"]')[1]!.setValue('oui_non')
    await suivre()
    expect(questions.value[1]!.choix).toBeUndefined()

    await editeur.findAll('[data-test="question-editee"]')[1]!.findAll('button.icone')[0]!.trigger('click')
    await suivre()
    expect(questions.value.map((q) => q.id)).toEqual(['q2', 'q1'])
  })
})

describe('composer vite', () => {
  it('deux clics rapides ajoutent bien deux questions', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    const questions = ref<Question[]>([])
    const editeur = mount(EditeurQuestionnaire, {
      props: { modelValue: questions.value, 'onUpdate:modelValue': (v: Question[]) => (questions.value = v), contexte: 'hackathon' },
      global: { plugins: [i18n] },
    })
    // Sans attendre le rendu entre les deux : le parent n'a pas encore renvoyé la liste.
    void editeur.get('[data-test="modele-lien"]').trigger('click')
    void editeur.get('[data-test="modele-niveau"]').trigger('click')
    await nextTick()
    expect(questions.value.map((q) => q.id)).toEqual(['q1', 'q2'])
  })
})

describe('l’éditeur passe AXE', () => {
  it('avec une question de chaque type', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    const questions: Question[] = [...QUESTIONS, { id: 'q4', intitule: 'Déjà livré ?', type: 'oui_non', obligatoire: false }, { id: 'q5', intitule: '', type: 'texte_long', obligatoire: false }]
    const editeur = mount(EditeurQuestionnaire, { props: { modelValue: questions, contexte: 'mission' }, global: { plugins: [i18n] }, attachTo: document.body })
    const resultat = await axe.run(editeur.element as HTMLElement, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(resultat.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
    editeur.unmount()
  })
})

describe('répondre au questionnaire d’un hackathon', () => {
  const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
  beforeEach(() => {
    reinitialiserHackathons()
    modifierHackathon(ID, (h) => (h.questionnaire = QUESTIONS))
  })

  async function monter() {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    await chargerSession()
    const routeur = createRouter({ history: createMemoryHistory(), routes: [{ path: '/hackathons/:id', component: HackathonPage }] })
    await routeur.push(`/hackathons/${ID}`)
    await routeur.isReady()
    return mount(RouterView, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
  }

  it('signale les réponses obligatoires sans appeler l’API, puis envoie les réponses avec l’inscription', async () => {
    let envoye: unknown = null
    let appels = 0
    serveur.use(
      http.post('/api/hackathons/:id/inscriptions', async ({ request }) => {
        appels++
        envoye = await request.clone().json()
        return undefined
      }),
    )
    const page = await monter()
    await vi.waitFor(() => expect(page.find('[data-test="questions-inscription"]').exists()).toBe(true), { timeout: 4000 })

    await page.get('[data-test="sinscrire"]').trigger('click')
    expect(appels).toBe(0)
    expect(page.get('[data-test="erreur-inscription"]').text()).toBe('Il manque 2 réponses obligatoires.')
    expect(page.findAll('[data-test="erreur-question"]')).toHaveLength(2)
    expect(page.get('[data-test="question-q1"] input').attributes('aria-invalid')).toBe('true')

    // Le focus va à la première question sans réponse.
    expect(document.activeElement).toBe(page.get('[data-test="question-q1"] input').element)

    // Une question remplie perd son erreur tout de suite, sans attendre le prochain envoi.
    await page.get('[data-test="question-q1"] input').setValue('https://github.com/kofi')
    expect(page.find('[data-test="question-q1"] [data-test="erreur-question"]').exists()).toBe(false)
    expect(page.get('[data-test="question-q1"] input').attributes('aria-invalid')).toBeUndefined()
    expect(page.findAll('[data-test="erreur-question"]')).toHaveLength(1)
    await page.findAll('[data-test="question-q2"] input[type="radio"]')[1]!.setValue(true)
    await page.findAll('[data-test="question-q3"] input[type="checkbox"]')[0]!.setValue(true)
    await page.get('[data-test="sinscrire"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="inscription-seul"]').exists()).toBe(false), { timeout: 4000 })
    expect(envoye).toEqual({
      chercheEquipe: true,
      reponses: [
        { questionId: 'q1', valeur: 'https://github.com/kofi' },
        { questionId: 'q2', valeur: 'Expert' },
        { questionId: 'q3', valeur: ['Soir'] },
      ],
    })
    page.unmount()
  })
})

describe('répondre au questionnaire d’une mission', () => {
  const ID = 'a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91'
  beforeEach(() => {
    reinitialiserMissions()
    modifierMission(ID, (m) => {
      m.moi = { role: 'visiteur', actions: ['proposer'] }
      m.questionnaire = [{ id: 'q1', intitule: 'As-tu déjà fait une boutique en ligne ?', type: 'oui_non', obligatoire: true }]
    })
  })

  it('envoie la réponse avec la proposition', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    await chargerSession()
    let envoye: { reponses?: unknown } | null = null
    serveur.use(
      http.post('/api/missions/:id/propositions', async ({ request }) => {
        envoye = (await request.clone().json()) as { reponses?: unknown }
        return undefined
      }),
    )
    const routes = [{ path: '/missions/:id', component: MissionPage }, { path: '/:autre(.*)*', component: { render: () => null } }]
    const routeur = createRouter({ history: createMemoryHistory(), routes })
    await routeur.push(`/missions/${ID}`)
    await routeur.isReady()
    const page = mount(RouterView, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
    await vi.waitFor(() => expect(page.find('[data-test="questions-mission"]').exists()).toBe(true), { timeout: 4000 })
    const champs = page.findAll('[data-test="form-proposer"] input')
    await page.get('[data-test="form-proposer"] textarea').setValue('Je fais ce genre de boutique depuis deux ans, avec Wave.')
    await champs[0]!.setValue('350 000')
    await champs[1]!.setValue('20')

    await page.get('[data-test="form-proposer"]').trigger('submit')
    expect(envoye).toBeNull()
    expect(page.get('[data-test="erreur-proposer"]').text()).toBe('Il manque une réponse obligatoire.')
    // Comme à l'inscription d'un hackathon : le focus va à la question sans réponse.
    expect(document.activeElement).toBe(page.findAll('[data-test="question-q1"] input[type="radio"]')[0]!.element)

    await page.findAll('[data-test="question-q1"] input[type="radio"]')[0]!.setValue(true)
    await page.get('[data-test="form-proposer"]').trigger('submit')
    await vi.waitFor(() => expect(envoye).not.toBeNull(), { timeout: 4000 })
    expect(envoye!.reponses).toEqual([{ questionId: 'q1', valeur: 'oui' }])
    page.unmount()
  })
})
