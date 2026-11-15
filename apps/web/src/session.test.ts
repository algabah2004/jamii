import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { serveur } from './test-setup'
import { chargerSession, seDeconnecter, useSession } from './session'
import { moiDemo } from './mocks/handlers'

describe('chargerSession', () => {
  it("connaît l'utilisateur quand /api/me répond 200", async () => {
    await chargerSession()
    const { etat, moi } = useSession()
    expect(etat.value).toBe('connecte')
    expect(moi.value?.nom).toBe(moiDemo.nom)
  })

  it("affiche l'accueil non connecté sur un 401", async () => {
    serveur.use(http.get('/api/me', () => HttpResponse.json({ code: 'non_connecte' }, { status: 401 })))
    await chargerSession()
    expect(useSession().etat.value).toBe('non_connecte')
  })

  it("signale l'API indisponible sur une 503 ou une coupure réseau", async () => {
    serveur.use(http.get('/api/me', () => new HttpResponse(null, { status: 503 })))
    await chargerSession()
    expect(useSession().etat.value).toBe('indisponible')

    serveur.use(http.get('/api/me', () => HttpResponse.error()))
    await chargerSession()
    expect(useSession().etat.value).toBe('indisponible')
  })
})

describe('l’accès à l’espace Systalink', () => {
  it('ne montre l’espace qu’à son équipe, et l’oublie si l’API ne répond pas', async () => {
    await chargerSession()
    expect(useSession().accesSystalink.value).toBe(true)

    serveur.use(http.get('/api/systalink/acces', () => HttpResponse.json({ acces: false })))
    await chargerSession()
    expect(useSession().accesSystalink.value).toBe(false)

    serveur.use(http.get('/api/systalink/acces', () => HttpResponse.error()))
    await chargerSession()
    expect(useSession().etat.value).toBe('connecte')
    expect(useSession().accesSystalink.value).toBe(false)
  })
})

describe('seDeconnecter', () => {
  it('envoie le jeton CSRF et oublie l’utilisateur', async () => {
    document.cookie = 'XSRF-TOKEN=jeton-de-test'
    let enTete: string | null = null
    serveur.use(
      http.post('/api/auth/deconnexion', ({ request }) => {
        enTete = request.headers.get('X-XSRF-TOKEN')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await chargerSession()
    await seDeconnecter()
    expect(enTete).toBe('jeton-de-test')
    expect(useSession().moi.value).toBeNull()
    expect(useSession().etat.value).toBe('non_connecte')
  })
})
