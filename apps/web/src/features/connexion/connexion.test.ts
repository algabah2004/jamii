import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import { moiDemo } from '../../mocks/handlers'
import App from '../../App.vue'
import { cheminSur, erreurGithub, minutesAvant } from './connexion'

const JETON = 'a'.repeat(40)

/** Une personne non connectée, qui le devient dès que la confirmation répond 204. */
function nonConnecte() {
  let connecte = false
  serveur.use(
    http.get('/api/me', () => (connecte ? HttpResponse.json(moiDemo) : HttpResponse.json({ code: 'non_connecte' }, { status: 401 }))),
    http.post('/api/auth/lien-email/confirmation', () => {
      connecte = true
      return new HttpResponse(null, { status: 204 })
    }),
    http.post('/api/auth/demo', () => {
      connecte = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
}

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await flushPromises()
  return { page, routeur }
}

describe('les règles de la connexion', () => {
  it("n'accepte qu'un chemin relatif comme retour, contre les redirections ouvertes", () => {
    expect(cheminSur('/besoin')).toBe('/besoin')
    expect(cheminSur('/demandes/nouvelle?x=1')).toBe('/demandes/nouvelle?x=1')
    expect(cheminSur('//evil.example')).toBe('/')
    expect(cheminSur('https://evil.example')).toBe('/')
    expect(cheminSur('/\\evil.example')).toBe('/')
    expect(cheminSur(undefined)).toBe('/')
    expect(cheminSur(['/a'])).toBe('/')
  })

  it("lit Retry-After en minutes, au moins une", () => {
    expect(minutesAvant('600')).toBe(10)
    expect(minutesAvant('61')).toBe(2)
    expect(minutesAvant(null)).toBe(1)
    expect(minutesAvant('demain')).toBe(1)
  })

  it('ramène un code GitHub inconnu à « GitHub ne répond pas »', () => {
    expect(erreurGithub('refus_github')).toBe('refus_github')
    expect(erreurGithub('<script>')).toBe('github_indisponible')
    expect(erreurGithub(undefined)).toBeNull()
  })
})

describe('se connecter', () => {
  it("envoie une page protégée vers la connexion, et l'en-tête y mène aussi", async () => {
    nonConnecte()
    const { page, routeur } = await ouvrir('/besoin')
    expect(routeur.currentRoute.value.name).toBe('connexion')
    // /besoin mène à « Tes demandes », où l'on décrit son besoin en parlant à Ayo.
    expect(routeur.currentRoute.value.query.retour).toBe('/demandes')
    expect(page.find('[data-test="connexion-github"]').attributes('href')).toBe('/api/auth/github?retour=%2Fdemandes')
    // Sur la page de connexion, le bouton de l'en-tête disparaît : il mènerait au même endroit.
    expect(page.find('[data-test="lien-connexion"]').exists()).toBe(false)
  })

  it("ne propose plus le lien par e-mail : GitHub et Google seulement", async () => {
    nonConnecte()
    const { page } = await ouvrir('/connexion')
    await vi.waitFor(() => expect(page.find('[data-test="connexion-google"]').exists()).toBe(true))
    expect(page.find('[data-test="formulaire-email"]').exists()).toBe(false)
    expect(page.find('input[type="email"]').exists()).toBe(false)
  })

  it('propose « Continuer avec Google » quand le serveur le permet, avec le chemin de retour', async () => {
    nonConnecte()
    const { page } = await ouvrir('/connexion?retour=/demandes')
    await vi.waitFor(() => expect(page.find('[data-test="connexion-google"]').exists()).toBe(true))
    expect(page.get('[data-test="connexion-google"]').attributes('href')).toBe('/api/auth/google?retour=%2Fdemandes')
  })

  it('sans Google configuré, ne montre pas le bouton', async () => {
    nonConnecte()
    serveur.use(http.get('/api/auth/fournisseurs', () => HttpResponse.json({ github: true, google: false, email: true })))
    const { page } = await ouvrir('/connexion')
    await vi.waitFor(() => expect(page.find('[data-test="connexion-github"]').exists()).toBe(true))
    await new Promise((r) => setTimeout(r, 50))
    expect(page.find('[data-test="connexion-google"]').exists()).toBe(false)
  })

  it('le compte démo : un clic ouvre la session et mène à la page demandée', async () => {
    nonConnecte()
    const { page, routeur } = await ouvrir('/connexion?retour=/cercles')
    await vi.waitFor(() => expect(page.find('[data-test="connexion-demo"]').exists()).toBe(true))
    expect(page.get('[data-test="voie-demo"]').text()).toContain('effacé après 24 heures')
    await page.get('[data-test="connexion-demo"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/cercles'))
  })

  it("sans compte démo sur le serveur, pas de bouton ; s'il ne répond pas, on le dit", async () => {
    nonConnecte()
    serveur.use(http.get('/api/auth/fournisseurs', () => HttpResponse.json({ github: true, google: true, email: true, demo: false })))
    const { page } = await ouvrir('/connexion')
    await vi.waitFor(() => expect(page.find('[data-test="connexion-google"]').exists()).toBe(true))
    expect(page.find('[data-test="connexion-demo"]').exists()).toBe(false)
    page.unmount()

    serveur.use(
      http.get('/api/auth/fournisseurs', () => HttpResponse.json({ github: true, google: true, email: true, demo: true })),
      http.post('/api/auth/demo', () => HttpResponse.json({ code: 'introuvable' }, { status: 404 })),
    )
    const { page: autre } = await ouvrir('/connexion')
    await vi.waitFor(() => expect(autre.find('[data-test="connexion-demo"]').exists()).toBe(true))
    await autre.get('[data-test="connexion-demo"]').trigger('click')
    await vi.waitFor(() => expect(autre.find('[data-test="erreur-demo"]').exists()).toBe(true))
  })

  it("explique l'échec de la connexion Google", async () => {
    nonConnecte()
    const { page } = await ouvrir('/connexion?erreur=email_non_verifie')
    expect(page.find('[data-test="erreur-github"]').text()).toContain('Google')
  })

  it("explique l'échec de la connexion GitHub renvoyé par l'API", async () => {
    nonConnecte()
    const { page } = await ouvrir('/connexion?erreur=refus_github')
    expect(page.find('[data-test="erreur-github"]').text()).toContain("rien n'a été partagé")
  })
})

describe('le lien reçu par e-mail', () => {
  it("ne connecte qu'au clic, retire le jeton de l'adresse, puis ouvre la page demandée", async () => {
    nonConnecte()
    let appels = 0
    serveur.use(
      http.post('/api/auth/lien-email/confirmation', async ({ request }) => {
        appels++
        expect(await request.json()).toEqual({ jeton: JETON })
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { page, routeur } = await ouvrir(`/connexion/email?jeton=${JETON}&retour=/demandes/nouvelle`)
    expect(appels).toBe(0)
    expect(routeur.currentRoute.value.query.jeton).toBeUndefined()

    serveur.use(http.get('/api/me', () => HttpResponse.json(moiDemo)))
    await page.find('[data-test="confirmer-lien"]').trigger('click')
    // L'ancienne adresse du formulaire mène à la conversation avec Ayo, sur « Tes demandes ».
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/demandes'), { timeout: 4000 })
    expect(appels).toBe(1)
  })

  it('propose un nouveau lien quand celui-ci a déjà servi ou a expiré', async () => {
    nonConnecte()
    serveur.use(http.post('/api/auth/lien-email/confirmation', () => HttpResponse.json({ code: 'lien_invalide' }, { status: 400 })))
    const { page } = await ouvrir(`/connexion/email?jeton=${JETON}`)
    await page.find('[data-test="confirmer-lien"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="lien-invalide"]').exists()).toBe(true))
    expect(page.find('a[href="/connexion"]').exists()).toBe(true)
  })

  it('sans jeton, dit tout de suite que le lien ne marche pas', async () => {
    nonConnecte()
    const { page } = await ouvrir('/connexion/email')
    expect(page.find('[data-test="lien-invalide"]').exists()).toBe(true)
  })
})
