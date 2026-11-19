import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { orientationDemo, planDemo, RECOMMANDES } from '../../mocks/demandes'
import { dateCourte, demandeValide, etatAnalyse, filDe, fonctionnalitesParPriorite, jalonsAvecTaches, titreDe, type Demande } from './demandes'

async function ouvrir(adresse: string, langue: 'fr' | 'en' = 'fr') {
  ;(i18n.global.locale as unknown as { value: string }).value = langue
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

type Page = Awaited<ReturnType<typeof ouvrir>>['page']

/** Écrit à Ayo dans la zone de saisie, comme dans un chat (Entrée envoie). */
async function ecrire(page: Page, texte: string) {
  await vi.waitFor(() => expect(page.find('[data-test="saisie-ayo"]').exists()).toBe(true))
  await page.get('[data-test="saisie-ayo"]').setValue(texte)
  await page.get('[data-test="saisie-ayo"]').trigger('keydown', { key: 'Enter' })
}

/** Un exemple dans la zone de saisie, puis on l'envoie : la discussion s'ouvre sur la page de la demande. */
async function exempleJusquAuBout(page: Page) {
  await vi.waitFor(() => expect(page.find('[data-test="exemple"]').exists()).toBe(true))
  await page.get('[data-test="exemple"]').trigger('click')
  await page.get('[data-test="envoyer"]').trigger('submit')
}

/** Répond à Ayo sur la page de la demande. */
async function repondreAyo(page: Page, texte: string) {
  await vi.waitFor(() => expect(page.find('[data-test="message-ayo"]').exists()).toBe(true))
  await page.get('[data-test="message-ayo"]').setValue(texte)
  await page.get('[data-test="message-ayo"]').trigger('keydown', { key: 'Enter' })
}

describe('le plan proposé', () => {
  it('range les fonctionnalités par priorité, sans groupe vide', () => {
    const plan = planDemo()
    plan.cahierDesCharges.fonctionnalites = plan.cahierDesCharges.fonctionnalites.filter((f) => f.priorite !== 'utile')
    expect(fonctionnalitesParPriorite(plan).map((g) => g.priorite)).toEqual(['indispensable', 'plus_tard'])
  })

  it('donne à chaque jalon ses tâches, dans l’ordre des semaines', () => {
    const plan = planDemo(8)
    plan.jalons.reverse()
    plan.taches = [{ titre: 'A', jalon: 2, competences: [] }, { titre: 'perdue', jalon: 9, competences: [] }]
    const jalons = jalonsAvecTaches(plan)
    expect(jalons.map((j) => j.semaine)).toEqual([2, 5, 8])
    expect(jalons[0]!.taches.map((t) => t.titre)).toEqual(['A'])
    expect(jalons.flatMap((j) => j.taches).map((t) => t.titre)).not.toContain('perdue')
  })

  it('refuse une demande trop courte', () => {
    expect(demandeValide({ titre: 'Ok', description: 'Une description assez longue ici.' })).toBe(false)
    expect(demandeValide({ titre: 'Une boutique', description: ' x ' })).toBe(false)
    expect(demandeValide({ titre: 'Une boutique', description: 'Je vends des tissus et je veux un catalogue.' })).toBe(true)
  })
})

describe('Une PME décrit son besoin', () => {
  it('reprend un exemple, obtient un plan, puis lance le projet', async () => {
    const { page, routeur } = await ouvrir('/demandes/nouvelle')
    // Tout se passe sur « Tes demandes », en parlant à Ayo ; on ne répond pas sans rien dire.
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/demandes'))
    await vi.waitFor(() => expect(page.find('[data-test="envoyer"]').exists()).toBe(true))
    expect(page.get('[data-test="envoyer"]').attributes('disabled')).toBeDefined()
    await page.get('[data-test="exemple"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="envoyer"]').attributes('disabled')).toBeUndefined())
    await page.get('[data-test="envoyer"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('demande'), { timeout: 4000 })
    await vi.waitFor(() => expect(page.find('[data-test="plan-propose"]').exists()).toBe(true), { timeout: 4000 })
    expect(page.findAll('[data-test="priorite"]')).toHaveLength(3)
    expect(page.findAll('[data-test="jalons"] > li')).toHaveLength(3)
    expect(page.text()).toContain('Designer d’interface')
    await page.get('[data-test="lancer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="projet-cree"]').exists()).toBe(true))
  })

  it('dit clairement quand le plan est un modèle de repli', async () => {
    serveur.use(
      http.get('/api/demandes/:id', ({ params }) =>
        HttpResponse.json({
          id: params.id, titre: 'Une boutique', description: 'Je vends des tissus et je veux un catalogue.',
          dureeSemaines: null, creeLe: new Date().toISOString(),
          plan: { statut: 'terminee', origine: 'repli', resultat: planDemo() },
        }),
      ),
    )
    const { page } = await ouvrir('/demandes/0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b')
    await vi.waitFor(() => expect(page.find('[data-test="plan-repli"]').exists()).toBe(true))
  })

  it('affiche le texte de la PME sans jamais l’exécuter', async () => {
    serveur.use(
      http.get('/api/demandes/:id', ({ params }) =>
        HttpResponse.json({
          id: params.id, titre: '<img src=x onerror="alert(1)">', description: '<img src=x onerror="alert(1)"> une description longue',
          dureeSemaines: null, creeLe: new Date().toISOString(), plan: { statut: 'echouee', erreurCode: 'delai_depasse' },
        }),
      ),
    )
    const { page } = await ouvrir('/demandes/0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b')
    await vi.waitFor(() => expect(page.find('[data-test="plan-echec"]').exists()).toBe(true))
    expect(page.find('main img').exists()).toBe(false)
    expect(page.text()).toContain('<img src=x onerror="alert(1)">')
  })

  it('propose de publier le plan comme mission, à côté du lancement du projet', async () => {
    const { page, routeur } = await ouvrir('/demandes/nouvelle')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="publier-mission"]').exists()).toBe(true), { timeout: 4000 })
    const id = String(routeur.currentRoute.value.params.id)
    const lien = page.get('[data-test="publier-mission"]')
    expect(lien.text()).toBe('Publier comme mission')
    expect(lien.attributes('href')).toBe(`/missions/nouvelle?demande=${id}`)
    expect(page.find('[data-test="lancer"]').exists()).toBe(true)
  })

  it('signale qu’une autre équipe a déjà pris la demande', async () => {
    serveur.use(http.post('/api/projets', () => HttpResponse.json({ code: 'demande_deja_prise' }, { status: 409 })))
    const { page } = await ouvrir('/demandes/nouvelle')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="lancer"]').exists()).toBe(true), { timeout: 4000 })
    await page.get('[data-test="lancer"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Une équipe a déjà lancé un projet sur cette demande'))
  })
})

const ID = '0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b'
const base = { id: ID, titre: 'Nos dossiers', description: 'Le cabinet perd ses dossiers entre associés.', dureeSemaines: null, creeLe: '2026-11-14T10:00:00Z' }

describe('Une discussion libre avec Ayo', () => {
  it('le premier message part tel quel, avec un titre tiré de sa première ligne, et rien de coché d’avance', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page } = await ouvrir('/demandes')
    await page.get('[data-test="saisie-ayo"]').setValue(' x ')
    expect(page.get('[data-test="envoyer"]').attributes('disabled')).toBeDefined()
    await ecrire(page, 'Nos dossiers se perdent\nLe cabinet perd ses dossiers entre associés.')
    await vi.waitFor(() => expect(corps.titre).toBe('Nos dossiers se perdent'), { timeout: 4000 })
    expect(corps.description).toBe('Nos dossiers se perdent\nLe cabinet perd ses dossiers entre associés.')
    expect(corps).toMatchObject({ langue: 'fr' })
    expect(corps.offresSystalink).not.toBe(true)
  })

  it('Ayo demande une précision sur un message court, la réponse s’affiche dans le fil, puis il conseille', async () => {
    const { page, routeur } = await ouvrir('/demandes')
    await ecrire(page, 'Je veux vendre mes tissus en ligne.')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('demande'), { timeout: 4000 })
    await vi.waitFor(() => expect(page.find('[data-test="question-ayo"]').exists()).toBe(true), { timeout: 4000 })
    // Ayo demande avant de conseiller : rien d'autre n'est proposé pour l'instant.
    expect(page.find('[data-test="services-systalink"]').exists()).toBe(false)
    expect(page.find('#onglets').exists()).toBe(false)
    expect(page.get('[data-test="fil"]').text()).toContain('Je veux vendre mes tissus en ligne.')
    await page.findAll('[data-test="suggestion"]')[0]!.trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="bulle-ayo"]')).toHaveLength(1))
    expect(page.get('[data-test="bulle-ayo"]').text()).toContain('Qui sont tes clients')
    expect(page.get('[data-test="fil"]').text()).toContain('Sur WhatsApp')
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true), { timeout: 4000 })
    expect(page.find('[data-test="question-ayo"]').exists()).toBe(false)
  })

  it('on supprime une discussion après confirmation, et on revient à la liste', async () => {
    const { page, routeur } = await ouvrir('/demandes')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="supprimer-discussion"]').exists()).toBe(true), { timeout: 4000 })
    const id = String(routeur.currentRoute.value.params.id)
    await page.get('[data-test="supprimer-discussion"]').trigger('click')
    // La boîte de Jamii s'ouvre (pas la fenêtre du navigateur) : on confirme.
    await vi.waitFor(() => expect(page.get('[data-test="confirmer-suppression"]').attributes('open')).toBeDefined())
    expect(page.get('[data-test="confirmer-suppression"]').text()).toContain('Supprimer cette discussion ?')
    await page.get('[data-test="confirmer-suppression"] [data-test="confirmer"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/demandes'))
    const reponse = await fetch(`/api/demandes/${id}`)
    expect(reponse.status).toBe(404)
  })
})

describe('l’orientation d’un besoin', () => {
  it('est prête même sans plan, quand Systalink suffit', () => {
    const sansDev = { ...orientationDemo(), developpeursNecessaires: false, competences: [], plan: null }
    const d = { ...base, plan: { statut: 'terminee' }, orientation: { statut: 'terminee', origine: 'ia', resultat: sansDev } } as Demande
    expect(etatAnalyse(d)).toBe('pret')
    expect(etatAnalyse({ ...d, orientation: { statut: 'en_cours' } })).toBe('attente')
    expect(etatAnalyse({ ...d, orientation: { statut: 'echouee', erreurCode: 'delai_depasse' } })).toBe('echec')
  })

  it('montre les services Systalink, les développeurs et leurs raisons, puis enregistre l’accord', async () => {
    const { page } = await ouvrir('/demandes/nouvelle')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true), { timeout: 4000 })
    const services = page.findAll('[data-test="service"]')
    expect(services).toHaveLength(2)
    expect(services[0]!.get('a').attributes()).toMatchObject({ href: 'https://systalink.com/fr/stockage-objets', rel: 'noopener noreferrer' })
    // L'hébergement est annoncé comme tel : Systalink héberge, il ne développe pas.
    expect(services[0]!.get('[data-test="genre-service"]').text()).toBe('Hébergement uniquement')
    expect(services[0]!.get('[data-test="hebergement-seul"]').text()).toContain('Il ne développe rien')
    expect(services[1]!.get('[data-test="genre-service"]').text()).toBe('Outil prêt à l’emploi')
    expect(services[1]!.find('[data-test="hebergement-seul"]').exists()).toBe(false)
    // Le clic sur « Voir le service » est compté pour le tableau de bord Systalink (#65).
    const clics: string[] = []
    serveur.use(http.post('/api/systalink/moments/:momentId/accepter', ({ params }) => {
      clics.push(String(params.momentId))
      return new HttpResponse(null, { status: 204 })
    }))
    await services[0]!.get('[data-test="voir-service"]').trigger('click')
    await vi.waitFor(() => expect(clics).toEqual(['b0a10000-0000-4000-8000-000000000001']))
    const recommandes = page.findAll('[data-test="recommande"]')
    expect(recommandes).toHaveLength(3)
    // Avec des développeurs proposés, jamais le message « aucun développeur » en même temps.
    expect(page.find('[data-test="aucun-recommande"]').exists()).toBe(false)
    expect(recommandes[0]!.text()).toContain('React, confirmé (3 dépôts)')
    expect(recommandes[0]!.get('a').attributes('href')).toBe('/messages/d2e00000-0000-4000-8000-000000000001')
    // Le conseil propose de publier le besoin en mission, plus de former un cercle.
    expect(page.get('[data-test="publier-mission-conseil"]').attributes('href')).toMatch(/^\/missions\/nouvelle\?demande=[0-9a-f-]{36}$/)
    expect(page.find('[data-test="former-cercle"]').exists()).toBe(false)

    // Les deux chemins côte à côte ; le projet technique reste replié jusqu'au clic.
    expect(page.find('[data-test="chemin-developpeurs"]').exists()).toBe(true)
    const details = page.get('#details-projet')
    expect(details.attributes('style')).toContain('display: none')
    await page.get('[data-test="voir-details"]').trigger('click')
    await vi.waitFor(() => expect(details.attributes('style') ?? '').not.toContain('display: none'))

    // Ayo parle d'abord, puis la personne réagit dans la zone en bas : Ayo relit toute la conversation.
    expect(page.get('[data-test="compris"]').text()).toContain('Ton cabinet perd du temps')
    await repondreAyo(page, 'Je veux seulement un espace client, pas de messagerie.')
    await vi.waitFor(() => expect(page.find('[data-test="plan-attente"]').exists()).toBe(true))
    // Le fil garde l'échange : ce qu'Ayo avait dit, puis la réponse de la personne.
    expect(page.get('[data-test="fil"]').text()).toContain('Je veux seulement un espace client, pas de messagerie.')
    expect(page.get('[data-test="bulle-ayo"]').text()).toContain('Ton cabinet perd du temps')
    await vi.waitFor(() => expect(page.find('[data-test="compris"]').exists()).toBe(true), { timeout: 4000 })

    const caseOffres = page.get('[data-test="offres-resultat"] input')
    expect((caseOffres.element as HTMLInputElement).checked).toBe(false)
    await caseOffres.setValue(true)
    await vi.waitFor(() => expect((caseOffres.element as HTMLInputElement).checked).toBe(true))
  })

  it('Ayo discute simplement : sa réponse est une bulle, sans conseil ni onglets', async () => {
    const discussion = { ...orientationDemo(), message: 'Ça va très bien, merci ! Et toi, tu as une activité que tu aimerais améliorer ?', services: [], etapes: [], competences: [], developpeursNecessaires: false, plan: null, question: null }
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, description: 'ça va ?', plan: { statut: 'terminee', origine: 'ia' }, orientation: { statut: 'terminee', origine: 'ia', resultat: discussion }, recommandations: [] }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="ayo-message"]').exists()).toBe(true))
    expect(page.get('[data-test="ayo-message"]').text()).toContain('Ça va très bien')
    expect(page.find('[data-test="plan-propose"]').exists()).toBe(false)
    expect(page.find('[data-test="chemins"]').exists()).toBe(false)
    expect(page.find('#onglets').exists()).toBe(false)
    expect(page.text()).not.toContain('Voici ce que j’ai compris')
    expect(page.find('[data-test="message-ayo"]').exists()).toBe(true)
  })

  it('un simple bonjour suffit pour ouvrir la discussion', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page } = await ouvrir('/demandes')
    await ecrire(page, 'yo')
    await vi.waitFor(() => expect(corps.description).toBe('yo'), { timeout: 4000 })
    expect(corps.titre).toBe('yo…')
  })

  it('quand Ayo pose une question, il ne conseille pas encore ; la zone en bas reste là pour répondre', async () => {
    const question = { ...orientationDemo(), services: [], etapes: [], competences: [], developpeursNecessaires: false, plan: null, question: { texte: 'Combien de personnes travaillent au cabinet ?', suggestions: ['Moins de 5', 'Plus de 5'] } }
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, conseilsRestants: 4, plan: { statut: 'terminee', origine: 'ia' }, orientation: { statut: 'terminee', origine: 'ia', resultat: question }, recommandations: [] }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="question-ayo"]').exists()).toBe(true))
    expect(page.get('[data-test="question-ayo"]').text()).toContain('Combien de personnes')
    expect(page.find('[data-test="services-systalink"]').exists()).toBe(false)
    expect(page.find('[data-test="etapes-ayo"]').exists()).toBe(false)
    expect(page.find('[data-test="passer"]').exists()).toBe(false)
    expect(page.find('[data-test="message-ayo"]').exists()).toBe(true)
    expect(page.get('[data-test="conseils-restants"]').text()).toContain('Il te reste 4 conseils')
  })

  it('une suggestion de la question d’Ayo part comme une précision', async () => {
    const avecQuestion = { ...orientationDemo(), question: { texte: 'Combien de personnes travaillent au cabinet ?', suggestions: ['Moins de 5', 'Plus de 5'] } }
    const envoyees: string[] = []
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, plan: { statut: 'terminee', origine: 'ia', resultat: planDemo() }, orientation: { statut: 'terminee', origine: 'ia', resultat: avecQuestion }, recommandations: [] }),
      ),
      http.post('/api/demandes/:id/precisions', async ({ request }) => {
        envoyees.push(((await request.json()) as { texte: string }).texte)
        return HttpResponse.json({ ...base, precisions: [{ texte: envoyees.at(-1), creeLe: '2026-11-15T10:00:00Z' }], plan: { statut: 'en_attente' }, orientation: { statut: 'en_attente' } })
      }),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="suggestion"]').exists()).toBe(true))
    await page.findAll('[data-test="suggestion"]')[0]!.trigger('click')
    await vi.waitFor(() => expect(envoyees).toEqual(['Moins de 5']))
    await vi.waitFor(() => expect(page.find('[data-test="question-ayo"]').exists()).toBe(false))
  })

  it('range la suite en onglets : les développeurs d’abord, puis le projet et la publication', async () => {
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, plan: { statut: 'terminee', origine: 'ia', resultat: planDemo() }, orientation: { statut: 'terminee', origine: 'ia', resultat: orientationDemo() }, recommandations: RECOMMANDES }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="onglet-developpeurs"]').exists()).toBe(true))
    expect(page.get('[data-test="onglet-developpeurs"]').attributes('aria-selected')).toBe('true')
    expect(page.get('[data-test="developpeurs"]').isVisible()).toBe(true)
    // Le conseil propose de publier en mission ; la barre collée en bas a disparu.
    expect(page.findAll('[data-test="publier-mission-conseil"]')).toHaveLength(1)
    await page.get('[data-test="onglet-publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="onglet-publier"]').attributes('aria-selected')).toBe('true'))
    expect(page.get('[data-test="developpeurs"]').attributes('style')).toContain('display: none')
    expect(page.find('[data-test="publication-formulaire"]').exists()).toBe(true)
    // Pour réagir au conseil, la zone de saisie reste en bas.
    expect(page.find('[data-test="message-ayo"]').exists()).toBe(true)
  })

  it('n’affiche ni développeurs ni plan quand Systalink suffit, et dit quand l’IA était absente', async () => {
    const sansDev = { ...orientationDemo(), developpeursNecessaires: false, competences: [], plan: null }
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, plan: { statut: 'terminee', origine: 'repli' }, orientation: { statut: 'terminee', origine: 'repli', resultat: sansDev }, recommandations: [] }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true))
    expect(page.text()).toContain('Analyse automatique indisponible')
    expect(page.find('[data-test="developpeurs"]').exists()).toBe(false)
    expect(page.find('[data-test="lancer"]').exists()).toBe(false)
    expect(page.find('[data-test="plan-echec"]').exists()).toBe(false)
  })
})

describe('Tes demandes (#199)', () => {
  const resumees = [
    { id: 'a1', titre: 'Boutique de pagnes', creeLe: '2026-11-14T10:00:00Z', resume: 'Vendre en ligne sans perdre ses commandes.', precisions: 2, publiee: true },
    { id: 'b2', titre: 'Cabinet sans clé USB', creeLe: '2026-11-13T10:00:00Z', resume: null, precisions: 0, publiee: false },
  ]

  it('le menu ouvre la liste de toutes mes discussions, avec leurs messages et ce qui est publié', async () => {
    serveur.use(http.get('/api/demandes', () => HttpResponse.json(resumees)))
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="liste-demandes"]').exists()).toBe(true))
    const cartes = page.findAll('[data-test="demande-resumee"]')
    expect(cartes).toHaveLength(2)
    expect(cartes[0]!.attributes('href')).toBe('/demandes/a1')
    expect(cartes[0]!.text()).toContain('Publiée')
    expect(cartes[0]!.text()).toContain('3 messages')
    expect(cartes[1]!.text()).toContain('Discussion en cours avec Ayo')
    // On décrit un nouveau besoin ici même, en parlant à Ayo.
    expect(page.find('[data-test="conversation-demande"]').exists()).toBe(true)
  })

  it('supprime une discussion depuis la liste, seulement après confirmation', async () => {
    const supprimees: string[] = []
    serveur.use(
      http.get('/api/demandes', () => HttpResponse.json(resumees)),
      http.delete('/api/demandes/:id', ({ params }) => {
        supprimees.push(String(params.id))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.findAll('[data-test="supprimer-demande"]')).toHaveLength(2))
    // Annuler : rien n'est supprimé.
    await page.findAll('[data-test="supprimer-demande"]')[1]!.trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="confirmer-suppression"]').attributes('open')).toBeDefined())
    await page.get('[data-test="annuler-suppression"]').trigger('click')
    await flushPromises()
    expect(supprimees).toEqual([])
    // Confirmer : la discussion disparaît de la liste.
    await page.findAll('[data-test="supprimer-demande"]')[1]!.trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="confirmer-suppression"]').attributes('open')).toBeDefined())
    await page.get('[data-test="confirmer-suppression"] [data-test="confirmer"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="demande-resumee"]')).toHaveLength(1))
    expect(supprimees).toEqual(['b2'])
  })

  it('filtre les demandes par état : en préparation, réponse prête, publiée', async () => {
    serveur.use(http.get('/api/demandes', () => HttpResponse.json(resumees)))
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="liste-demandes"]').exists()).toBe(true))
    expect(page.get('[data-test="filtre-prete"]').attributes('disabled')).toBeDefined()
    await page.get('[data-test="filtre-preparation"]').trigger('click')
    const cartes = page.findAll('[data-test="demande-resumee"]')
    expect(cartes).toHaveLength(1)
    expect(cartes[0]!.text()).toContain('Cabinet sans clé USB')
    await page.get('[data-test="filtre-toutes"]').trigger('click')
    expect(page.findAll('[data-test="demande-resumee"]')).toHaveLength(2)
  })

  it('sans demande, invite à décrire un premier besoin', async () => {
    serveur.use(http.get('/api/demandes', () => HttpResponse.json([])))
    const { page } = await ouvrir('/demandes')
    // Pas de liste vide : l'accueil d'Ayo est l'invitation à commencer.
    await vi.waitFor(() => expect(page.find('[data-test="conversation-demande"]').exists()).toBe(true))
    expect(page.find('[data-test="liste-demandes"]').exists()).toBe(false)
  })

  it('sur la page d’un besoin, les autres restent à côté pour passer de l’un à l’autre', async () => {
    serveur.use(http.get('/api/demandes', () => HttpResponse.json(resumees)))
    const { page } = await ouvrir('/demandes/nouvelle')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="historique"]').exists()).toBe(true))
    expect(page.get('[data-test="historique"]').text()).toContain('Cabinet sans clé USB')
  })
})

describe('la conversation', () => {
  it('le titre vient de la première ligne, raccourci', () => {
    expect(titreDe('  Une boutique\nEt le reste')).toBe('Une boutique')
    expect(titreDe('ok\nJe vends des tissus')).toBe('ok Je vends des tissus')
    expect(titreDe('x'.repeat(200))).toHaveLength(80)
  })

  it('le fil alterne le premier message, puis ce qu’Ayo avait dit et chaque réponse', () => {
    const d = { ...base, plan: { statut: 'terminee' }, precisions: [
      { texte: 'Sur WhatsApp', creeLe: '2026-11-16T10:00:00Z', ayo: 'Qui sont tes clients ?', ayoQuestion: true },
      { texte: 'Et sans smartphone ?', creeLe: '2026-11-16T10:01:00Z', ayo: null, ayoQuestion: false },
    ] } as Demande
    expect(filDe(d)).toEqual([
      { qui: 'moi', texte: base.description },
      { qui: 'ayo', texte: 'Qui sont tes clients ?', question: true },
      { qui: 'moi', texte: 'Sur WhatsApp' },
      { qui: 'moi', texte: 'Et sans smartphone ?' },
    ])
  })
})

describe('dateCourte', () => {
  it('suit la langue de Jamii, pas celle du navigateur', () => {
    expect(dateCourte('2026-11-15T10:00:00Z', 'fr')).toBe('15 nov.')
    expect(dateCourte('2026-11-15T10:00:00Z', 'en')).toBe('Nov 15')
  })
})

describe('la refonte : on parle à Ayo, une IA, sur la page même', () => {
  it('Ayo ouvre la conversation, marqué « Assistant IA »', async () => {
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="conversation-demande"]').exists()).toBe(true))
    expect(page.get('[data-test="conversation-demande"] .accueil').text()).toContain('Ayo')
    expect(page.find('[data-test="conversation-demande"] .badge-ia').exists()).toBe(true)
  })

  it('la réponse dit qu’on parle à une IA, classe les profils par correspondance et permet de les comparer', async () => {
    const { page } = await ouvrir('/demandes')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="plan-propose"]').exists()).toBe(true), { timeout: 4000 })
    // Le premier message, puis la réponse d'Ayo, marquée comme venant d'une IA.
    expect(page.findAll('[data-test="bulle-moi"]')).toHaveLength(1)
    expect(page.find('[data-test="parcours"]').exists()).toBe(false)
    expect(page.find('[data-test="compris"] .badge-ia').exists()).toBe(true)
    await vi.waitFor(() => expect(page.findAll('[data-test="correspondance"]').length).toBeGreaterThan(1))
    const scores = page.findAll('[data-test="correspondance"]').map((c) => Number.parseInt(c.text(), 10))
    expect(scores).toEqual([...scores].sort((a, b) => b - a))
    expect(page.find('[data-test="comparaison"]').exists()).toBe(false)
    const cases = page.findAll('[data-test="comparer"]')
    await cases[0]!.trigger('change')
    await cases[1]!.trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="comparaison"]').exists()).toBe(true))
    expect(page.get('[data-test="comparaison"]').findAll('thead th')).toHaveLength(3)
  })

  it('un lien avec un début de besoin : il est déjà dans la zone de saisie, rien n’est envoyé', async () => {
    const { page, routeur } = await ouvrir('/besoin?titre=Un%20site%20pour%20ma%20boulangerie')
    expect(routeur.currentRoute.value.path).toBe('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="saisie-ayo"]').exists()).toBe(true))
    expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).value).toBe('Un site pour ma boulangerie')
    expect(routeur.currentRoute.value.name).toBe('mes-demandes')
  })

  it('en anglais, la demande part avec la langue de l’écran et Ayo répond en anglais', async () => {
    let corps: Record<string, unknown> = {}
    serveur.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && new URL(request.url).pathname === '/api/demandes') corps = (await request.clone().json()) as Record<string, unknown>
    })
    try {
      const { page, routeur } = await ouvrir('/demandes', 'en')
      await exempleJusquAuBout(page)
      await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('demande'), { timeout: 4000 })
      expect(corps.langue).toBe('en')
      await vi.waitFor(() => expect(page.text()).toContain('Your firm loses time'), { timeout: 4000 })
      expect(page.text()).not.toContain('Ton cabinet')
    } finally {
      serveur.events.removeAllListeners()
      ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    }
  })

  it('les données de démo existent dans les deux langues', () => {
    const en = JSON.stringify(orientationDemo(8, 'en'))
    expect(en).not.toMatch(/\b(ton|tes|les|des|une)\b/)
    expect(JSON.stringify(orientationDemo(8, 'fr'))).toContain('Ton cabinet')
    expect(planDemo(8, 'en').jalons.map((j) => j.titre)).toEqual(['Catalogue online', 'Orders and payment', 'Handover to the manager'])
  })

  it('un exemple se met dans la zone de saisie, sans rien envoyer à ma place', async () => {
    const envoyees: unknown[] = []
    serveur.use(http.post('/api/demandes', async ({ request }) => {
      envoyees.push(await request.json())
      return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
    }))
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="exemple"]').exists()).toBe(true))
    await page.get('[data-test="exemple"]').trigger('click')
    await vi.waitFor(() => expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).value).toContain('Thiès'))
    expect(envoyees).toEqual([])
  })

})
