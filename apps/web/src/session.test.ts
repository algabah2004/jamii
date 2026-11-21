import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { serveur } from './test-setup'
import { chargerSession, seDeconnecter, useSession } from './session'
import { moiDemo } from './mocks/handlers'

describe('chargerSession', () => {
  it("connaît l'utilisateur quand la session est ouverte", async () => {
    await chargerSession()
    const { etat, moi } = useSession()
    expect(etat.value).toBe('connecte')
    expect(moi.value?.nom).toBe(moiDemo.nom)
  })

  it("affiche l'accueil non connecté sans compte", async () => {
    serveur.use(http.get('/api/auth/session', () => HttpResponse.json({ connecte: false, moi: null })))
    await chargerSession()
    expect(useSession().etat.value).toBe('non_connecte')
  })

  it("signale l'API indisponible sur une 503 ou une coupure réseau, après avoir réessayé", async () => {
    let appels = 0
    serveur.use(http.get('/api/auth/session', () => {
      appels++
      return new HttpResponse(null, { status: 503 })
    }))
    await chargerSession([1, 1])
    expect(appels).toBe(3)
    expect(useSession().etat.value).toBe('indisponible')

    serveur.use(http.get('/api/auth/session', () => HttpResponse.error()))
    await chargerSession([])
    expect(useSession().etat.value).toBe('indisponible')
  })

  it("passe un redémarrage de l'API sans afficher l'erreur", async () => {
    let appels = 0
    serveur.use(http.get('/api/auth/session', () => (++appels < 3 ? new HttpResponse(null, { status: 502 }) : HttpResponse.json({ connecte: true, moi: moiDemo }))))
    await chargerSession([1, 1, 1])
    expect(useSession().etat.value).toBe('connecte')
    expect(appels).toBe(3)
  })

  it('revient tout seul quand l’API répond de nouveau', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      let enPanne = true
      serveur.use(http.get('/api/auth/session', () => (enPanne ? HttpResponse.error() : HttpResponse.json({ connecte: true, moi: moiDemo }))))
      await chargerSession([])
      expect(useSession().etat.value).toBe('indisponible')
      enPanne = false
      await vi.advanceTimersByTimeAsync(10_000)
      await vi.waitFor(() => expect(useSession().etat.value).toBe('connecte'))
    } finally {
      vi.useRealTimers()
    }
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
