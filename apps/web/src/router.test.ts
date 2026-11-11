import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from './test-setup'
import { creerRouteur } from './router'
import { chargerSession } from './session'

describe('routeur', () => {
  it("renvoie vers l'accueil, avec le chemin de retour, si on n'est pas connecté", async () => {
    serveur.use(http.get('/api/me', () => new HttpResponse(null, { status: 401 })))
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/profil')
    expect(routeur.currentRoute.value.name).toBe('accueil')
    expect(routeur.currentRoute.value.query.retour).toBe('/profil')
  })

  it('ouvre le profil quand on est connecté', async () => {
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/profil')
    expect(routeur.currentRoute.value.name).toBe('profil')
  })

  it('affiche la page introuvable pour une adresse inconnue', async () => {
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/nimporte/quoi')
    expect(routeur.currentRoute.value.name).toBe('introuvable')
  })
})
