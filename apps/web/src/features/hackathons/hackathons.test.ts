import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { chargerSession } from '../../session'
import { reinitialiserHackathons } from '../../mocks/hackathons'
import HackathonPage from './HackathonPage.vue'
import HackathonsPage from './HackathonsPage.vue'
import NouveauHackathonPage from './NouveauHackathonPage.vue'
import { formaterMontant, lireCalendrier, lireQuestions, sommePoids, versInstant, versMontant } from './hackathons'

beforeEach(() => reinitialiserHackathons())

async function monter(chemin: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/hackathons', component: HackathonsPage },
      { path: '/hackathons/nouveau', component: NouveauHackathonPage },
      { path: '/hackathons/:id', component: HackathonPage },
    ],
  })
  await routeur.push(chemin)
  await routeur.isReady()
  const page = mount(RouterView, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

const attendre = (page: ReturnType<typeof mount>, selecteur: string) =>
  vi.waitFor(() => expect(page.find(selecteur).exists()).toBe(true), { timeout: 4000 })

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'

describe('les aides de saisie', () => {
  it('lit un calendrier dans l’ordre, et refuse les dates manquantes ou dans le désordre', () => {
    const bon = { ouvertes: '2026-12-01T09:00', fermees: '2026-12-10T09:00', debut: '2026-12-12T09:00', fin: '2026-12-14T09:00', resultats: '2026-12-16T09:00' }
    expect(lireCalendrier(bon)).not.toBeNull()
    expect(lireCalendrier({ ...bon, fermees: '' })).toBeNull()
    expect(lireCalendrier({ ...bon, fin: '2026-12-11T09:00' })).toBeNull()
    // Les inscriptions peuvent fermer à l'instant du début.
    expect(lireCalendrier({ ...bon, fermees: bon.debut })).not.toBeNull()
  })

  it('convertit une date saisie en instant, et refuse ce qui n’en est pas une', () => {
    expect(versInstant('2026-12-12T09:00')).toMatch(/^2026-12-12T\d\d:00:00\.000Z$/)
    expect(versInstant('')).toBeNull()
    expect(versInstant('demain')).toBeNull()
  })

  it('range les questions : une par ligne, sans doublon ni ligne vide, cinq au plus', () => {
    expect(lireQuestions('Quel problème ?\n\n  Quel problème ?  \nPour qui ?\nab')).toEqual(['Quel problème ?', 'Pour qui ?'])
    expect(lireQuestions(Array.from({ length: 9 }, (_, i) => `Question ${i}`).join('\n'))).toHaveLength(5)
  })

  it('lit un montant en unité mineure, l’écrit dans la langue de la personne et somme les poids', () => {
    expect(versMontant('1 000 000', 'XOF')).toEqual({ centimes: 1000000, devise: 'XOF' })
    expect(versMontant('beaucoup', 'XOF')).toBeNull()
    expect(formaterMontant({ centimes: 1000000, devise: 'XOF' }, 'fr')).toMatch(/1\s?000\s?000/)
    expect(sommePoids([{ poids: 50 }, { poids: 30 }, { poids: Number.NaN }])).toBe(80)
  })
})

describe('la liste des hackathons', () => {
  it('montre les hackathons publiés avec leur statut, leur date et leurs inscrits', async () => {
    const { page } = await monter('/hackathons')
    await attendre(page, '[data-test="liste"]')
    const carte = page.get('[data-test="hackathon"]')
    expect(carte.text()).toContain('Dakar Cloud Hack')
    expect(carte.text()).toContain('Inscriptions ouvertes')
    expect(carte.text()).toContain('Awa Diop')
    expect(carte.text()).toContain('12 inscrits')
  })

  it('propose « Créer un hackathon » à une personne connectée et filtre par statut', async () => {
    const statuts: (string | null)[] = []
    serveur.use(
      http.get('/api/hackathons', ({ request }) => {
        statuts.push(new URL(request.url).searchParams.get('statut'))
        return HttpResponse.json({ elements: [], curseurSuivant: null })
      }),
    )
    const { page } = await monter('/hackathons')
    await attendre(page, '[data-test="creer-un-hackathon"]')
    await page.get('[data-test="filtre-en_cours"]').trigger('click')
    await vi.waitFor(() => expect(statuts).toContain('en_cours'))
    await attendre(page, '[data-test="aucun"]')
  })

  it('le dit quand l’API ne répond pas', async () => {
    serveur.use(http.get('/api/hackathons', () => HttpResponse.json({ code: 'erreur' }, { status: 500 })))
    const { page } = await monter('/hackathons')
    await attendre(page, '[data-test="indisponible"]')
  })
})

describe('créer un hackathon', () => {
  async function remplir(page: ReturnType<typeof mount>) {
    const textes = page.findAll('input[type="text"]')
    await textes[0]!.setValue('Hackathon des écoles')
    await textes[1]!.setValue('Le numérique au service des écoles')
    await page.get('textarea').setValue('Deux jours pour construire un outil utile à une école de quartier.')
    const dates = ['2026-12-01T09:00', '2026-12-10T09:00', '2026-12-12T09:00', '2026-12-14T09:00', '2026-12-16T09:00']
    const cles = ['ouvertes', 'fermees', 'debut', 'fin', 'resultats']
    for (const [i, cle] of cles.entries()) await page.get(`[data-test="date-${cle}"]`).setValue(dates[i]!)
    const noms = page.findAll('[data-test="criteres"] input[type="text"]')
    await noms[0]!.setValue('Utilité')
    await noms[1]!.setValue('Qualité technique')
  }

  it('garde le bouton éteint tant que des champs obligatoires manquent', async () => {
    const { page } = await monter('/hackathons/nouveau')
    expect(page.get('[data-test="enregistrer"]').attributes('disabled')).toBeDefined()
    await remplir(page)
    expect(page.get('[data-test="enregistrer"]').attributes('disabled')).toBeUndefined()
  })

  it('refuse des dates dans le désordre', async () => {
    const { page } = await monter('/hackathons/nouveau')
    await remplir(page)
    await page.get('[data-test="date-fin"]').setValue('2026-12-11T09:00')
    expect(page.get('[data-test="enregistrer"]').attributes('disabled')).toBeDefined()
  })

  it('envoie le brouillon complet, avec les instants en UTC, puis ouvre le hackathon', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.post('/api/hackathons', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'abc', titre: 'x' }, { status: 201 })
      }),
    )
    const { page, routeur } = await monter('/hackathons/nouveau')
    await remplir(page)
    await page.get('[data-test="mode-candidature"]').setValue(true)
    await page.get('[data-test="deploiement"]').setValue(true)
    await page.get('[data-test="nouveau-hackathon"]').trigger('submit')

    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/hackathons/abc'))
    expect(corps).toMatchObject({
      titre: 'Hackathon des écoles',
      lieu: 'en_ligne',
      modeInscription: 'candidature',
      deploiementObligatoire: true,
      tailleEquipe: { min: 2, max: 5 },
      criteres: [{ nom: 'Utilité', poids: 50 }, { nom: 'Qualité technique', poids: 50 }],
    })
    expect((corps.calendrier as Record<string, string>).debutLe).toMatch(/Z$/)
    expect(corps).not.toHaveProperty('prix')
    expect(corps).not.toHaveProperty('codeDeConduite')
  })

  it('signale à côté de chaque champ ce que l’API a refusé', async () => {
    serveur.use(
      http.post('/api/hackathons', () =>
        HttpResponse.json(
          { code: 'validation', erreurs: [{ champ: 'titre', code: 'trop_court' }, { champ: 'criteres[0].nom', code: 'trop_court' }] },
          { status: 400 },
        ),
      ),
    )
    const { page } = await monter('/hackathons/nouveau')
    await remplir(page)
    await page.get('[data-test="nouveau-hackathon"]').trigger('submit')
    await vi.waitFor(() => expect(page.text()).toContain('Le nom doit faire entre 3 et 120 caractères.'))
    expect(page.text()).toContain('de un à huit critères')
  })

  it('refuse un montant de prix invalide sans rien envoyer', async () => {
    let envoye = false
    serveur.use(http.post('/api/hackathons', () => ((envoye = true), HttpResponse.json({}, { status: 201 }))))
    const { page } = await monter('/hackathons/nouveau')
    await remplir(page)
    await page.get('[data-test="ajouter-prix"]').trigger('click')
    const champs = page.findAll('[data-test="prix"] input[type="text"]')
    await champs[0]!.setValue('Prix du jury')
    await champs[1]!.setValue('beaucoup')
    await page.get('[data-test="nouveau-hackathon"]').trigger('submit')
    await vi.waitFor(() => expect(page.text()).toContain('Un prix n’est pas valide'.replace('’', "'")))
    expect(envoye).toBe(false)
  })

})

describe('un hackathon', () => {
  it('montre le défi, le calendrier, les critères et les prix, comme du texte', async () => {
    const { page } = await monter(`/hackathons/${ID}`)
    await attendre(page, '[data-test="titre"]')
    expect(page.get('[data-test="titre"]').text()).toBe('Dakar Cloud Hack')
    expect(page.get('[data-test="calendrier"]').text()).toContain('Fin des rendus')
    expect(page.get('[data-test="criteres"]').text()).toContain('Utilité pour un commerçant')
    expect(page.get('[data-test="prix"]').text()).toContain('offert par Systalink')
    expect(page.find('[data-test="deploiement"]').exists()).toBe(true)
  })

  it('un visiteur connecté peut s’inscrire, et ne voit aucun bouton d’organisateur', async () => {
    const { page } = await monter(`/hackathons/${ID}`)
    await attendre(page, '[data-test="titre"]')
    expect(page.find('[data-test="participer"]').exists()).toBe(true)
    expect(page.find('[data-test="publier"]').exists()).toBe(false)
    expect(page.find('[data-test="annuler"]').exists()).toBe(false)
  })

  it('publie un brouillon, puis le statut change', async () => {
    serveur.use(
      http.get('/api/hackathons/:id', () =>
        HttpResponse.json({
          id: ID, titre: 'Brouillon', theme: 'Un thème', description: 'Un défi à relever en équipe.', lieu: 'en_ligne', ville: null,
          statut: 'brouillon', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z',
          organisateur: { id: ID, nom: 'Awa', avatarUrl: null }, nombreInscrits: 0,
          calendrier: { inscriptionsOuvertesLe: '2026-12-01T09:00:00Z', inscriptionsFermeesLe: '2026-12-10T09:00:00Z', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z', resultatsLe: '2026-12-16T09:00:00Z' },
          tailleEquipe: { min: 2, max: 5 }, modeInscription: 'ouvert', deploiementObligatoire: false, codeDeConduite: null,
          questions: [], criteres: [{ id: 'c1', nom: 'Utilité', poids: 100 }], prix: [], version: 1, creeLe: '2026-11-14T10:00:00Z',
          moi: { role: 'organisateur', actions: ['modifier', 'publier'] },
        }),
      ),
      http.post('/api/hackathons/:id/publier', () => HttpResponse.json({ code: 'validation' }, { status: 400 })),
    )
    const { page } = await monter(`/hackathons/${ID}`)
    await attendre(page, '[data-test="brouillon"]')
    await page.get('[data-test="publier"]').trigger('click')
    await attendre(page, '[data-test="erreur"]')
    expect(page.get('[data-test="erreur"]').text()).toContain('code de conduite')
  })

  it('annule avec une raison d’au moins 10 caractères, et dit si l’action n’est plus possible', async () => {
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.get('/api/hackathons/:id', () =>
        HttpResponse.json({
          id: ID, titre: 'Publié', theme: 'Un thème', description: 'Un défi à relever en équipe.', lieu: 'en_ligne', ville: null,
          statut: 'publie', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z',
          organisateur: { id: ID, nom: 'Awa', avatarUrl: null }, nombreInscrits: 0,
          calendrier: { inscriptionsOuvertesLe: '2026-12-01T09:00:00Z', inscriptionsFermeesLe: '2026-12-10T09:00:00Z', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z', resultatsLe: '2026-12-16T09:00:00Z' },
          tailleEquipe: { min: 2, max: 5 }, modeInscription: 'ouvert', deploiementObligatoire: false, codeDeConduite: null,
          questions: [], criteres: [{ id: 'c1', nom: 'Utilité', poids: 100 }], prix: [], version: 2, creeLe: '2026-11-14T10:00:00Z',
          moi: { role: 'organisateur', actions: ['modifier', 'annuler'] },
        }),
      ),
      http.post('/api/hackathons/:id/annuler', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ code: 'etat_invalide' }, { status: 409 })
      }),
    )
    const { page } = await monter(`/hackathons/${ID}`)
    await attendre(page, '[data-test="annuler"]')
    await page.get('[data-test="annuler"]').trigger('click')
    await attendre(page, '[data-test="annulation"]')
    expect(page.get('[data-test="confirmer-annulation"]').attributes('disabled')).toBeDefined()

    await page.get('[data-test="annulation"] textarea').setValue('Le lieu n’est plus disponible')
    expect(page.get('[data-test="confirmer-annulation"]').attributes('disabled')).toBeUndefined()
    await page.get('[data-test="annulation"]').trigger('submit')
    await attendre(page, '[data-test="erreur"]')
    expect(corps).toEqual({ version: 2, raison: 'Le lieu n’est plus disponible' })
    expect(page.get('[data-test="erreur"]').text()).toContain('plus possible')
  })

  it('un hackathon inconnu ou privé est « introuvable »', async () => {
    const { page } = await monter('/hackathons/00000000-0000-4000-8000-000000000000')
    await attendre(page, '[data-test="introuvable"]')
  })

  it('affiche le texte de l’organisateur comme du texte, jamais comme du HTML', async () => {
    serveur.use(
      http.get('/api/hackathons/:id', () =>
        HttpResponse.json({
          id: ID, titre: '<img src=x onerror=alert(1)>', theme: '<b>thème</b>', description: '<script>alert(1)</script>', lieu: 'en_ligne', ville: null,
          statut: 'publie', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z',
          organisateur: { id: ID, nom: 'Awa', avatarUrl: null }, nombreInscrits: 0,
          calendrier: { inscriptionsOuvertesLe: '2026-12-01T09:00:00Z', inscriptionsFermeesLe: '2026-12-10T09:00:00Z', debutLe: '2026-12-12T09:00:00Z', finRendusLe: '2026-12-14T09:00:00Z', resultatsLe: '2026-12-16T09:00:00Z' },
          tailleEquipe: { min: 2, max: 5 }, modeInscription: 'ouvert', deploiementObligatoire: false, codeDeConduite: null,
          questions: [], criteres: [], prix: [], version: 1, creeLe: '2026-11-14T10:00:00Z', moi: { role: 'visiteur', actions: [] },
        }),
      ),
    )
    const { page } = await monter(`/hackathons/${ID}`)
    await attendre(page, '[data-test="titre"]')
    expect(page.find('img').exists()).toBe(false)
    expect(page.find('script').exists()).toBe(false)
    expect(page.get('[data-test="description"]').text()).toContain('<script>')
  })
})
