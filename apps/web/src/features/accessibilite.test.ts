import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import axe from 'axe-core'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { i18n } from '../i18n'
import { routes } from '../router'
import { chargerSession } from '../session'
import { http, HttpResponse } from 'msw'
import { serveur } from '../test-setup'
import { deposerRendu, modifierHackathon, reinitialiserHackathons, type Hackathon, type Rendu } from '../mocks/hackathons'
import { MISSION_EN_COURS, reinitialiserMissions } from '../mocks/missions'

/*
 * Les pages des missions et des hackathons passent AXE (#181, F2), avec les règles WCAG 2.1 A et AA. Le contraste
 * des couleurs ne se mesure pas dans jsdom (aucun rendu) : il est vérifié sur les jetons du design system.
 */
const HACKATHON = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'

let page: VueWrapper | null = null

beforeEach(() => {
  reinitialiserHackathons()
  reinitialiserMissions()
})
afterEach(() => {
  page?.unmount()
  page = null
})

async function violations(chemin: string, attendu: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({ history: createMemoryHistory(), routes })
  await routeur.push(chemin)
  await routeur.isReady()
  // La vraie TransitionGroup : son remplaçant de test glisse une balise entre <ul> et <li>, ce qu'AXE refuse.
  page = mount(RouterView, { global: { plugins: [i18n, routeur], stubs: { 'transition-group': false } }, attachTo: document.body })
  await vi.waitFor(() => expect(page!.find(attendu).exists()).toBe(true), { timeout: 4000 })
  await flushPromises()
  const resultat = await axe.run(page.element as HTMLElement, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
  })
  return resultat.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)
}

function renduDemo(n: number, titre: string): Rendu {
  return {
    id: `r1000000-0000-4000-8000-00000000000${n}`,
    equipeId: `e1000000-0000-4000-8000-00000000000${n}`,
    titre,
    description: 'Un service pour les commerçants du quartier.',
    lienProduit: `https://projet${n}.example`,
    depot: null,
    video: null,
    reponses: [],
    lienRepond: true,
    lienVerifieLe: '2026-11-14T10:00:00Z',
    version: 1,
    modifieLe: '2026-11-14T10:00:00Z',
  }
}

const role = (statut: Hackathon['statut'], moi: Hackathon['moi']) =>
  modifierHackathon(HACKATHON, (h) => Object.assign(h, { statut, moi }))

describe('AXE, missions', () => {
  it('la liste des missions', async () => {
    expect(await violations('/missions', 'h1')).toEqual([])
  })

  it('publier une mission', async () => {
    expect(await violations('/missions/nouvelle', 'form')).toEqual([])
  })

  it('une mission en cours, avec son suivi', async () => {
    expect(await violations(`/missions/${MISSION_EN_COURS}`, '[data-test="suivi"]')).toEqual([])
  })
})

describe('AXE, hackathons', () => {
  it('la liste des hackathons', async () => {
    expect(await violations('/hackathons', 'h1')).toEqual([])
  })

  it('créer un hackathon', async () => {
    expect(await violations('/hackathons/nouveau', 'form')).toEqual([])
  })

  it('un hackathon, vu par un visiteur qui peut s’inscrire', async () => {
    expect(await violations(`/hackathons/${HACKATHON}`, '[data-test="titre"]')).toEqual([])
  })

  it('s’inscrire en répondant au questionnaire, avec chaque type de question (#257)', async () => {
    modifierHackathon(HACKATHON, (h) => {
      h.questionnaire = [
        { id: 'q1', intitule: 'Ton lien GitHub', type: 'texte_court', obligatoire: true, aide: 'Un lien public' },
        { id: 'q2', intitule: 'Pourquoi participer ?', type: 'texte_long', obligatoire: false },
        { id: 'q3', intitule: 'Ton niveau', type: 'choix_unique', obligatoire: true, choix: ['Débutant', 'Expert'] },
        { id: 'q4', intitule: 'Tes disponibilités', type: 'choix_multiples', obligatoire: false, choix: ['Soir', 'Week-end'] },
        { id: 'q5', intitule: 'Déjà livré une application ?', type: 'oui_non', obligatoire: false },
      ]
    })
    expect(await violations(`/hackathons/${HACKATHON}`, '[data-test="questions-inscription"]')).toEqual([])
  })

  it('le jury et la confirmation des gagnants, vus par l’organisateur', async () => {
    deposerRendu(renduDemo(1, 'Carnet de caisse'))
    deposerRendu(renduDemo(2, 'Stock au marché'))
    role('evaluation', { role: 'organisateur', actions: ['confirmer_resultats'] })
    expect(await violations(`/hackathons/${HACKATHON}`, '[data-test="jury"]')).toEqual([])
  })

  it('la notation, vue par un juré', async () => {
    deposerRendu(renduDemo(1, 'Carnet de caisse'))
    role('evaluation', { role: 'jure', actions: ['noter'] })
    expect(await violations(`/hackathons/${HACKATHON}`, '[data-test="form-notes"]')).toEqual([])
  })
})


describe('AXE, messagerie', () => {
  it('la liste des conversations et la recherche', async () => {
    expect(await violations('/messages', '[data-test="conversations"]')).toEqual([])
  })

  it('la discussion avec la liste des conversations', async () => {
    expect(await violations('/messages/d2e00000-0000-4000-8000-000000000002', '[data-test="form-message"]')).toEqual([])
  })
})


describe('AXE, exploration des cercles', () => {
  it('la recherche, les filtres et les cartes', async () => {
    expect(await violations('/cercles', '[data-test="cercles-ouverts"]')).toEqual([])
  })
})

describe('AXE, vitrine des projets', () => {
  it('la recherche, les filtres d’origine et les cartes des projets', async () => {
    expect(await violations('/vitrine', '[data-test="projets"]')).toEqual([])
  })
})


describe('AXE, décrire son besoin à Ayo', () => {
  it('la conversation, ses exemples et la zone de saisie', async () => {
    expect(await violations('/demandes', '[data-test="conversation-demande"]')).toEqual([])
  })
})

describe('AXE, se connecter', () => {
  it('la présentation de Jamii et les deux façons de se connecter', async () => {
    serveur.use(http.get('/api/me', () => HttpResponse.json({ code: 'non_connecte' }, { status: 401 })))
    expect(await violations('/connexion', '[data-test="connexion-google"]')).toEqual([])
  })
})
