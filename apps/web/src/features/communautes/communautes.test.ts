import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { monogramme, motifDe, MOTIFS_COMMUNAUTE } from './communautes'

// Questions des données de démo (src/mocks/communautes.ts).
const QUESTION_DE_KOFFI = '/publications/7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d' // Wanjiru a répondu, rien n'est accepté
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

  it('rejoindre une communauté depuis sa bannière ouvre « Quoi de neuf ? »', async () => {
    const { page } = await ouvrir('/communautes/fintech')
    await vi.waitFor(() => expect(page.find('[data-test="adhesion"]').exists()).toBe(true))
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
    await page.get('[data-test="adhesion"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="adhesion"]').text()).toBe('Membre ✓'))
    await vi.waitFor(() => expect(page.find('[data-test="publier"]').exists()).toBe(true))
  })

  it('le filtre « questions sans réponse acceptée » reste dans l’adresse', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(4))
    await page.get('[data-test="onglet-ouvertes"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(2))
    expect(routeur.currentRoute.value.query).toEqual({ onglet: 'ouvertes' })
    expect(page.get('[data-test="onglet-ouvertes"]').attributes('aria-pressed')).toBe('true')
  })

  it('une communauté inconnue affiche « introuvable »', async () => {
    const { page } = await ouvrir('/communautes/inconnue')
    expect(page.find('[data-test="publications"]').exists()).toBe(false)
    expect(page.text()).toContain('Toutes les communautés')
  })

  it('publier dans le fil ne change pas de page : la question arrive en haut', async () => {
    let recu: { type?: string; titre?: string; contenu?: string } = {}
    serveur.use(
      http.post('/api/communautes/:slug/publications', async ({ request }) => {
        recu = (await request.json()) as typeof recu
        return HttpResponse.json({
          id: 'nouvelle', communaute: 'flutter', type: recu.type, titre: recu.titre, contenu: recu.contenu, auteur: { id: 'a', nom: 'Awa Diop', avatarUrl: null },
          nombreReponses: 0, accepteeLe: null, creeLe: new Date().toISOString(), projetId: null, reponseAccepteeId: null,
          reponses: [], moi: { estAuteur: true, estMembre: true },
        }, { status: 201 })
      }),
    )
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(4))
    await page.get('[data-test="genre-question"]').trigger('click')
    await page.get('[data-test="zone-publier"]').setValue('Comment tester un provider Riverpod ?\nJe ne sais pas comment simuler le réseau.')
    await page.get('[data-test="publier"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(5))
    expect(routeur.currentRoute.value.path).toBe('/communautes/flutter')
    // Le titre, c'est la première ligne ; le texte, tout ce qu'on a écrit.
    expect(recu).toEqual({ type: 'question', titre: 'Comment tester un provider Riverpod ?', contenu: 'Comment tester un provider Riverpod ?\nJe ne sais pas comment simuler le réseau.' })
    expect(page.findAll('[data-test="publication"]')[0]!.text()).not.toContain('IA')
    expect((page.get('[data-test="zone-publier"]').element as HTMLTextAreaElement).value).toBe('')
  })

  it('les réponses s’ouvrent sous la publication, et on y répond sans quitter le fil', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(4))
    const premiere = page.findAll('[data-test="publication"]')[0]!
    await premiere.get('[data-test^="reponses-"]').trigger('click')
    await vi.waitFor(() => expect(premiere.findAll('[data-test="reponse"]').length).toBeGreaterThan(0))
    const avant = premiere.findAll('[data-test="reponse"]').length
    await premiere.get('form textarea').setValue('Merci, je teste Hive de mon côté.')
    await premiere.get('form').trigger('submit')
    await vi.waitFor(() => expect(premiere.findAll('[data-test="reponse"]')).toHaveLength(avant + 1))
    expect(routeur.currentRoute.value.path).toBe('/communautes/flutter')
  })
})

describe('Question : les membres répondent, l’auteur accepte', () => {
  it('une question ne montre que des réponses de membres, sans bouton « Valider »', async () => {
    const { page } = await ouvrir(QUESTION_DE_KOFFI)
    await vi.waitFor(() => expect(page.text()).toContain('Wanjiru Kamau'))
    expect(page.find('[data-test="valider"]').exists()).toBe(false)
    expect(page.get('[data-test="reponses"]').text()).not.toMatch(/\bIA\b|l'IA/)
  })

  it("l'auteur d'une question accepte une réponse", async () => {
    const { page } = await ouvrir(QUESTION_D_AWA)
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
          creeLe: new Date().toISOString(), projetId: null, reponseAccepteeId: null,
          moi: { estAuteur: true, estMembre: true },
          reponses: [{ id: 'r', contenu: 'Trouvé tout seul.', auteur: { id: 'awa', nom: 'Awa Diop', avatarUrl: null },
            acceptee: false, creeLe: new Date().toISOString() }],
        }),
      ),
    )
    const { page } = await ouvrir('/publications/q')
    expect(page.find('[data-test="accepter"]').exists()).toBe(false)
  })

  it('explique un refus du serveur avec des mots simples', async () => {
    serveur.use(
      http.post('/api/reponses/:id/accepter', () =>
        HttpResponse.json({ code: 'deja_acceptee', status: 409 }, { status: 409, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )
    const { page } = await ouvrir(QUESTION_D_AWA)
    await page.get('[data-test="accepter"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Cette question a déjà une réponse acceptée.'))
  })

  it("affiche le contenu d'un utilisateur comme du texte, sans jamais l'exécuter", async () => {
    serveur.use(
      http.get('/api/publications/:id', () =>
        HttpResponse.json({
          id: 'q', communaute: 'flutter', type: 'discussion', titre: 'Piège', contenu: '<img src=x onerror="alert(1)">',
          auteur: null, nombreReponses: 0, accepteeLe: null, creeLe: new Date().toISOString(), projetId: null,
          reponseAccepteeId: null, moi: { estAuteur: false, estMembre: true }, reponses: [],
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

describe('motifDe', () => {
  it('donne toujours le même motif à une communauté, parmi ceux de la charte', () => {
    expect(motifDe('flutter')).toBe(motifDe('flutter'))
    expect(MOTIFS_COMMUNAUTE).toContain(motifDe('antenne-dakar'))
    expect(new Set(['flutter', 'fintech', 'ia', 'antenne-dakar'].map(motifDe)).size).toBeGreaterThan(1)
  })
})
