import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { orientationDemo, planDemo, RECOMMANDES } from '../../mocks/demandes'
import { dateCourte, demandeValide, etatAnalyse, fonctionnalitesParPriorite, jalonsAvecTaches, type Demande } from './demandes'

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

/** Répond à Ayo dans la zone de saisie, comme dans un chat (Entrée envoie). */
async function repondre(page: Page, texte: string) {
  await vi.waitFor(() => expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).disabled).toBe(false))
  await page.get('[data-test="saisie-ayo"]').setValue(texte)
  await page.get('[data-test="saisie-ayo"]').trigger('keydown', { key: 'Enter' })
}

/** Passe les questions guidées, puis les précisions, jusqu'au récapitulatif d'Ayo. */
async function jusquAuRecap(page: Page, avantDeContinuer?: () => Promise<void>) {
  for (let i = 0; i < 3; i++) {
    await vi.waitFor(() => expect(page.find('[data-test="passer-question"]').exists() || page.find('[data-test="precisions"]').exists()).toBe(true))
    if (!page.find('[data-test="passer-question"]').exists()) break
    await page.get('[data-test="passer-question"]').trigger('click')
  }
  await vi.waitFor(() => expect(page.find('[data-test="continuer-precisions"]').exists()).toBe(true))
  if (avantDeContinuer) await avantDeContinuer()
  await page.get('[data-test="continuer-precisions"]').trigger('click')
  await vi.waitFor(() => expect(page.find('[data-test="envoyer"]').exists()).toBe(true))
}

const DETAILS = 'Je couds des vêtements à Thiès et je veux vendre en ligne, avec les commandes sur WhatsApp.'

/** Un exemple dans la zone de saisie, on l'envoie, on raconte les détails : Ayo enchaîne ses questions. */
async function exempleEtDetails(page: Page) {
  await vi.waitFor(() => expect(page.find('[data-test="exemple"]').exists()).toBe(true))
  await page.get('[data-test="exemple"]').trigger('click')
  await page.get('[data-test="saisie-ayo"]').trigger('keydown', { key: 'Enter' })
  await repondre(page, DETAILS)
}

/** Un exemple, puis tout droit jusqu'à l'envoi. */
async function exempleJusquAuBout(page: Page) {
  await exempleEtDetails(page)
  await jusquAuRecap(page)
  await page.get('[data-test="envoyer"]').trigger('click')
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
    expect(demandeValide({ titre: 'Une boutique', description: 'Trop court' })).toBe(false)
    expect(demandeValide({ titre: 'Une boutique', description: 'Je vends des tissus et je veux un catalogue.' })).toBe(true)
  })
})

describe('Une PME décrit son besoin', () => {
  it('reprend un exemple, obtient un plan, puis lance le projet', async () => {
    const { page, routeur } = await ouvrir('/demandes/nouvelle')
    // Tout se passe sur « Tes demandes », en parlant à Ayo ; on ne répond pas sans rien dire.
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/demandes'))
    await vi.waitFor(() => expect(page.find('[data-test="envoyer-reponse"]').exists()).toBe(true))
    expect(page.get('[data-test="envoyer-reponse"]').attributes('disabled')).toBeDefined()
    await exempleEtDetails(page)
    await jusquAuRecap(page)
    expect(page.get('[data-test="envoyer"]').attributes('disabled')).toBeUndefined()
    await page.get('[data-test="envoyer"]').trigger('click')
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

describe('Décris ton besoin, pas à pas', () => {
  it('compose le besoin avec les réponses guidées, et chaque réponse s’affiche dans la conversation', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page } = await ouvrir('/demandes')
    await repondre(page, 'Nos dossiers')
    await vi.waitFor(() => expect(page.get('[data-test="moi-besoin"]').text()).toContain('Nos dossiers'))
    await page.get('[data-test="saisie-ayo"]').setValue('court')
    expect(page.get('[data-test="envoyer-reponse"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Encore')
    await repondre(page, 'Le cabinet perd ses dossiers entre associés.')
    await vi.waitFor(() => expect(page.find('[data-test="moi-details"]').exists()).toBe(true))
    // Chaque question vient avec un exemple : on sait quel genre de réponse donner.
    expect(page.get('[data-test="exemple-question"]').text()).toContain('Par exemple')
    await repondre(page, 'Les quatre associés')
    await vi.waitFor(() => expect(page.get('[data-test="moi-qui"]').text()).toContain('Les quatre associés'))
    await jusquAuRecap(page)
    await page.get('[data-test="envoyer"]').trigger('click')
    await vi.waitFor(() => expect(corps.titre).toBe('Nos dossiers'), { timeout: 4000 })
    expect(String(corps.description)).toContain("Qui va s'en servir : Les quatre associés")
  })

  it('on revient sur une réponse sans perdre ce qui est écrit', async () => {
    const { page } = await ouvrir('/demandes')
    await repondre(page, 'Une boutique')
    await vi.waitFor(() => expect(page.find('[data-test="modifier-besoin"]').exists()).toBe(true))
    await page.get('[data-test="modifier-besoin"]').trigger('click')
    await vi.waitFor(() => expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).value).toBe('Une boutique'))
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

  it('envoie le secteur et la ville, et jamais la case Systalink cochée d’avance', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page, routeur } = await ouvrir('/besoin')
    expect(routeur.currentRoute.value.name).toBe('mes-demandes')
    await exempleEtDetails(page)
    expect(page.find('[data-test="prudence"]').exists()).toBe(true)
    await jusquAuRecap(page, async () => {
      await page.get('[data-test="secteur"][value="juridique"]').setValue(true)
    })
    expect((page.get('[data-test="offres"] input').element as HTMLInputElement).checked).toBe(false)
    await page.get('[data-test="envoyer"]').trigger('click')
    await vi.waitFor(() => expect(corps.titre).toBeTruthy())
    expect(corps).toMatchObject({ secteur: 'juridique', offresSystalink: false, langue: 'fr' })
    expect(corps).not.toHaveProperty('ville')
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

    // L'assistant parle d'abord, puis la personne peut préciser : l'analyse repart avec sa précision.
    expect(page.get('[data-test="compris"]').text()).toContain('Ton cabinet perd du temps')
    await page.get('#precision').setValue('Je veux seulement un espace client, pas de messagerie.')
    await page.get('[data-test="preciser"]').trigger('submit')
    // Seule la réponse d'Ayo s'affiche : sa nouvelle réponse arrive, sans recopier ce que la personne a écrit.
    await vi.waitFor(() => expect(page.find('[data-test="plan-attente"]').exists()).toBe(true))
    expect(page.text()).not.toContain('Je veux seulement un espace client, pas de messagerie.')
    await vi.waitFor(() => expect(page.find('[data-test="compris"]').exists()).toBe(true), { timeout: 4000 })

    const caseOffres = page.get('[data-test="offres-resultat"] input')
    expect((caseOffres.element as HTMLInputElement).checked).toBe(false)
    await caseOffres.setValue(true)
    await vi.waitFor(() => expect((caseOffres.element as HTMLInputElement).checked).toBe(true))
  })

  it('Ayo pose sa question d’abord : une réponse d’un clic part comme une précision, ou on passe à son conseil', async () => {
    const avecQuestion = { ...orientationDemo(), question: { texte: 'Combien de personnes travaillent au cabinet ?', suggestions: ['Moins de 5', 'Plus de 5'] } }
    serveur.use(
      http.get('/api/demandes/:id', () =>
        HttpResponse.json({ ...base, conseilsRestants: 4, plan: { statut: 'terminee', origine: 'ia', resultat: planDemo() }, orientation: { statut: 'terminee', origine: 'ia', resultat: avecQuestion }, recommandations: RECOMMANDES }),
      ),
    )
    const { page } = await ouvrir(`/demandes/${ID}`)
    await vi.waitFor(() => expect(page.find('[data-test="question-ayo"]').exists()).toBe(true))
    // Tant que la question est ouverte, le conseil attend derrière.
    expect(page.find('[data-test="services-systalink"]').exists()).toBe(false)
    await page.get('[data-test="passer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="services-systalink"]').exists()).toBe(true))
    expect(page.get('[data-test="etapes-ayo"]').findAll('li')).toHaveLength(3)
    expect(page.get('[data-test="conseils-restants"]').text()).toContain('Il te reste 4 conseils')
    expect(page.find('[data-test="libre"]').exists()).toBe(true)
    // « Ce n'est pas tout à fait ça ? » prépare une raison que la personne peut compléter.
    await page.get('[data-test="pas-ca-cher"]').trigger('click')
    expect((page.get('#precision').element as HTMLTextAreaElement).value).toBe('C’est trop cher pour moi.')
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
    // « Ce n'est pas tout à fait ça ? » reste replié jusqu'au clic.
    expect(page.get('#preciser-corps').attributes('style')).toContain('display: none')
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

  it('le menu ouvre la liste de tous mes besoins, avec leurs précisions et ce qui est publié', async () => {
    serveur.use(http.get('/api/demandes', () => HttpResponse.json(resumees)))
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="liste-demandes"]').exists()).toBe(true))
    const cartes = page.findAll('[data-test="demande-resumee"]')
    expect(cartes).toHaveLength(2)
    expect(cartes[0]!.attributes('href')).toBe('/demandes/a1')
    expect(cartes[0]!.text()).toContain('Publiée')
    expect(cartes[0]!.text()).toContain('2 précisions')
    expect(cartes[1]!.text()).toContain('Réponse en préparation')
    // On décrit un nouveau besoin ici même, en parlant à Ayo.
    expect(page.find('[data-test="conversation-demande"]').exists()).toBe(true)
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

describe('dateCourte', () => {
  it('suit la langue de Jamii, pas celle du navigateur', () => {
    expect(dateCourte('2026-11-15T10:00:00Z', 'fr')).toBe('15 nov.')
    expect(dateCourte('2026-11-15T10:00:00Z', 'en')).toBe('Nov 15')
  })
})

describe('la refonte : on parle à Ayo, une IA, sur la page même', () => {
  it('Ayo ouvre la conversation, marqué « Assistant IA », et le budget part avec le besoin', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/demandes', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...base, plan: { statut: 'en_attente' } }, { status: 201 })
      }),
    )
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="conversation-demande"]').exists()).toBe(true))
    expect(page.get('[data-test="conversation-demande"] .accueil').text()).toContain('Ayo')
    expect(page.find('[data-test="conversation-demande"] .badge-ia').exists()).toBe(true)
    await exempleEtDetails(page)
    await jusquAuRecap(page, async () => {
      await page.get('[data-test="budget-de_500k_a_2m"]').setValue(true)
    })
    expect(page.get('[data-test="moi-precisions"]').text()).toContain('500 000 à 2 millions F CFA')
    await page.get('[data-test="envoyer"]').trigger('click')
    await vi.waitFor(() => expect(String(corps.description)).toContain('Budget envisagé\u202f: 500 000 à 2 millions F CFA'), { timeout: 4000 })
  })

  it('la réponse dit qu’on parle à une IA, classe les profils par correspondance et permet de les comparer', async () => {
    const { page } = await ouvrir('/demandes')
    await exempleJusquAuBout(page)
    await vi.waitFor(() => expect(page.find('[data-test="plan-propose"]').exists()).toBe(true), { timeout: 4000 })
    // Seule la réponse d'Ayo : ni la demande recopiée, ni le parcours, ni le bandeau en double.
    expect(page.find('[data-test="fil"]').exists()).toBe(false)
    expect(page.find('[data-test="parcours"]').exists()).toBe(false)
    expect(page.find('[data-test="compris"] .badge-ia').exists()).toBe(true)
    if (page.find('[data-test="passer"]').exists()) await page.get('[data-test="passer"]').trigger('click')
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

  it('un lien avec un début de besoin : Ayo le reprend et passe aux détails', async () => {
    const { page, routeur } = await ouvrir('/besoin?titre=Un%20site%20pour%20ma%20boulangerie')
    expect(routeur.currentRoute.value.path).toBe('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="moi-besoin"]').exists()).toBe(true))
    expect(page.get('[data-test="moi-besoin"]').text()).toContain('Un site pour ma boulangerie')
    expect(page.find('[data-test="prudence"]').exists()).toBe(true)
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

  it('un exemple se met dans la zone de saisie, sans rien envoyer ni ajouter à ma place', async () => {
    const { page } = await ouvrir('/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="exemple"]').exists()).toBe(true))
    const titre = page.get('[data-test="exemple"]').text()
    await page.get('[data-test="exemple"]').trigger('click')
    expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).value).toBe(titre)
    expect(page.find('[data-test="moi-besoin"]').exists()).toBe(false)
    await page.get('[data-test="saisie-ayo"]').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.get('[data-test="moi-besoin"]').text()).toContain(titre))
    // Les détails, c'est la personne qui les écrit : rien n'est rempli d'avance.
    expect(page.find('[data-test="moi-details"]').exists()).toBe(false)
    expect((page.get('[data-test="saisie-ayo"]').element as HTMLTextAreaElement).value).toBe('')
  })

})
