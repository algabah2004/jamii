import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from './test-setup'
import { creerRouteur } from './router'
import { chargerSession } from './session'

describe('routeur', () => {
  it("renvoie vers la page de connexion, avec le chemin de retour, si on n'est pas connecté", async () => {
    serveur.use(http.get('/api/me', () => new HttpResponse(null, { status: 401 })))
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/profil')
    expect(routeur.currentRoute.value.name).toBe('connexion')
    expect(routeur.currentRoute.value.query.retour).toBe('/profil')
  })

  it('ouvre le profil quand on est connecté', async () => {
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/profil')
    expect(routeur.currentRoute.value.name).toBe('profil')
  })

  it('ouvre les missions et les hackathons sans compte, mais pas leur création', async () => {
    serveur.use(http.get('/api/me', () => new HttpResponse(null, { status: 401 })))
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/missions')
    expect(routeur.currentRoute.value.name).toBe('missions')
    await routeur.push('/hackathons/d7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e')
    expect(routeur.currentRoute.value.name).toBe('hackathon')
    await routeur.push('/missions/nouvelle')
    expect(routeur.currentRoute.value.name).toBe('connexion')
    await routeur.push('/hackathons/nouveau')
    expect(routeur.currentRoute.value.query.retour).toBe('/hackathons/nouveau')
  })

  it('affiche la page introuvable pour une adresse inconnue', async () => {
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/nimporte/quoi')
    expect(routeur.currentRoute.value.name).toBe('introuvable')
  })
})
