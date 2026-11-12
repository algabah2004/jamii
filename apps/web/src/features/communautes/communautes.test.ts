import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { monogramme } from './communautes'

// Questions des données de démo (src/mocks/communautes.ts).
const QUESTION_DE_KOFFI = '/publications/7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d' // l'IA a proposé, personne n'a validé
const QUESTION_D_AWA = '/publications/8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e' // une réponse humaine à accepter

async function ouvrir(adresse: string) {
  // Mode composition : la langue est une ref à l'exécution (le type de createI18n ne le dit pas).
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

describe('Communautés', () => {
  it('liste les communautés, avec les antennes locales et celles dont je fais partie', async () => {
    const { page } = await ouvrir('/communautes')
    const cartes = page.findAll('[data-test="communautes"] > li')
    expect(cartes).toHaveLength(4)
    expect(page.text()).toContain('Antenne locale · Sénégal')
    expect(cartes[0]!.find('[data-test="deja-membre"]').exists()).toBe(true)
  })

  it('rejoindre une communauté en un clic ouvre le formulaire de question', async () => {
    const { page } = await ouvrir('/communautes/fintech')
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
    await page.get('[data-test="adhesion"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="adhesion"]').text()).toBe('Quitter'))
    expect(page.find('[data-test="publier"]').exists()).toBe(true)
  })

  it('le filtre « questions sans réponse acceptée » reste dans l’adresse', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    expect(page.findAll('[data-test="publications"] > li')).toHaveLength(4)
    await page.findAll('.filtres button')[1]!.trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="publications"] > li')).toHaveLength(2))
    expect(routeur.currentRoute.value.query.filtre).toBe('ouvertes')
  })

  it('une communauté inconnue affiche « introuvable »', async () => {
    const { page } = await ouvrir('/communautes/inconnue')
    expect(page.find('[data-test="publications"]').exists()).toBe(false)
    expect(page.text()).toContain('Toutes les communautés')
  })

  it('poser une question mène à la question, où l’IA prépare une réponse', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
    await page.get('[data-test="ouvrir-formulaire"]').trigger('click')
    await page.get('[data-test="publier"] input').setValue('Comment tester un provider Riverpod ?')
    await page.get('[data-test="publier"] textarea').setValue('Je ne sais pas comment simuler le réseau dans mes tests.')
    await page.get('[data-test="publier"]').trigger('submit')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('publication'))
    await vi.waitFor(() => expect(page.find('[data-test="ia-attente"]').exists()).toBe(true))
    expect(page.get('[data-test="ia-attente"]').text()).toContain("L'IA prépare une réponse")
  })
})

describe('Question : l’IA propose, un pair valide', () => {
  it('relit la question jusqu’à ce que la réponse de l’IA arrive', async () => {
    let appels = 0
    serveur.use(
      http.get('/api/publications/:id', () => {
        appels += 1
        return HttpResponse.json({
          id: 'q', communaute: 'flutter', type: 'question', titre: 'Une question', contenu: 'Le détail.',
          auteur: null, nombreReponses: appels > 1 ? 1 : 0, accepteeLe: null, creeLe: new Date().toISOString(),
          projetId: null, reponseAccepteeId: null, moi: { estAuteur: false, estMembre: true },
          propositionIa: { statut: appels > 1 ? 'prete' : 'en_attente' },
          reponses: appels > 1
            ? [{ id: 'r', contenu: 'Voici une piste.', auteur: null, proposeeParIa: true, valideePar: null, acceptee: false, creeLe: new Date().toISOString() }]
            : [],
        })
      }),
    )
    const { page } = await ouvrir('/publications/q')
    expect(page.find('[data-test="ia-attente"]').exists()).toBe(true)
    // Vraie minuterie de 3 s (contrat) : pas d'horloge simulée, qui se mélange mal avec MSW.
    await vi.waitFor(() => expect(page.find('[data-test="etiquette-ia"]').exists()).toBe(true), { timeout: 5000 })
    expect(page.get('[data-test="etiquette-ia"]').text()).toContain("Proposée par l'IA · à vérifier par un pair")
    expect(page.find('[data-test="ia-attente"]').exists()).toBe(false)
  })

  it('un pair valide la réponse de l’IA, qui devient la réponse acceptée', async () => {
    const { page } = await ouvrir(QUESTION_DE_KOFFI)
    await page.get('[data-test="valider"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="acceptee"]').exists()).toBe(true))
    expect(page.get('[data-test="acceptee"]').text()).toContain('Réponse acceptée · validée par Awa Diop')
    expect(page.find('[data-test="valider"]').exists()).toBe(false)
    expect(page.get('[data-test="etiquette-ia"]').text()).not.toContain('à vérifier')
  })

  it("l'auteur d'une question accepte une réponse humaine, mais ne voit jamais « Valider »", async () => {
    const { page } = await ouvrir(QUESTION_D_AWA)
    expect(page.find('[data-test="valider"]').exists()).toBe(false)
    await page.get('[data-test="accepter"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Elle apparaît maintenant sur le profil de son auteur'))
    expect(page.find('[data-test="acceptee"]').exists()).toBe(true)
  })

  it("l'auteur d'une question ne voit pas « Accepter » sur sa propre réponse", async () => {
    serveur.use(
      http.get('/api/publications/:id', () =>
        HttpResponse.json({
          id: 'q', communaute: 'flutter', type: 'question', titre: 'Une question', contenu: 'Le détail.',
          auteur: { id: 'awa', nom: 'Awa Diop', avatarUrl: null }, nombreReponses: 1, accepteeLe: null,
          creeLe: new Date().toISOString(), projetId: null, reponseAccepteeId: null, propositionIa: { statut: 'indisponible' },
          moi: { estAuteur: true, estMembre: true },
          reponses: [{ id: 'r', contenu: 'Trouvé tout seul.', auteur: { id: 'awa', nom: 'Awa Diop', avatarUrl: null },
            proposeeParIa: false, valideePar: null, acceptee: false, creeLe: new Date().toISOString() }],
        }),
      ),
    )
    const { page } = await ouvrir('/publications/q')
    expect(page.find('[data-test="accepter"]').exists()).toBe(false)
  })

  it('explique un refus du serveur avec des mots simples', async () => {
    serveur.use(
      http.post('/api/reponses/:id/valider', () =>
        HttpResponse.json({ code: 'propre_question', status: 409 }, { status: 409, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )
    const { page } = await ouvrir(QUESTION_DE_KOFFI)
    await page.get('[data-test="valider"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Tu ne peux pas valider la réponse à ta propre question'))
  })

  it("affiche le contenu d'un utilisateur comme du texte, sans jamais l'exécuter", async () => {
    serveur.use(
      http.get('/api/publications/:id', () =>
        HttpResponse.json({
          id: 'q', communaute: 'flutter', type: 'discussion', titre: 'Piège', contenu: '<img src=x onerror="alert(1)">',
          auteur: null, nombreReponses: 0, accepteeLe: null, creeLe: new Date().toISOString(), projetId: null,
          reponseAccepteeId: null, propositionIa: null, moi: { estAuteur: false, estMembre: true }, reponses: [],
        }),
      ),
    )
    const { page } = await ouvrir('/publications/q')
    expect(page.find('main img').exists()).toBe(false)
    expect(page.text()).toContain('<img src=x onerror="alert(1)">')
  })
})

describe('Signaler', () => {
  it('envoie le motif choisi au modérateur et remercie', async () => {
    let recu: unknown = null
    serveur.use(
      http.post('/api/signalements', async ({ request }) => {
        recu = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    const { page } = await ouvrir(QUESTION_DE_KOFFI)
    await vi.waitFor(() => expect(page.find('[data-test="signaler"]').exists()).toBe(true))
    await page.get('[data-test="signaler"]').trigger('click')
    await page.get('input[value="hors_sujet"]').setValue(true)
    await page.get('.signaler__panneau').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="signale"]').exists()).toBe(true))
    expect(recu).toEqual({ cibleType: 'publication', cibleId: QUESTION_DE_KOFFI.split('/').pop(), motif: 'hors_sujet' })
  })
})

describe('monogramme', () => {
  it('prend l’initiale des deux premiers mots utiles', () => {
    expect(monogramme('Flutter et Dart')).toBe('FD')
    expect(monogramme('Fintech et paiement mobile')).toBe('FP')
    expect(monogramme('IA appliquée')).toBe('IA')
  })

  it('garde deux lettres pour un nom d’un seul mot, et ne plante pas sur un nom vide', () => {
    expect(monogramme('Kotlin')).toBe('KO')
    expect(monogramme('')).toBe('')
  })
})
