import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { installerMemoireEspaces } from './memoireEspaces'

const vide = { template: '<div />' }
function routeur() {
  const r = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'accueil', component: vide },
      { path: '/missions', name: 'missions', component: vide },
      { path: '/cercles', name: 'cercles', component: vide },
      { path: '/cercles/:id', name: 'cercle', component: vide },
      { path: '/communautes', name: 'communautes', component: vide },
      { path: '/communautes/:slug', name: 'communaute', component: vide },
    ],
  })
  installerMemoireEspaces(r)
  return r
}

describe('la mémoire des espaces', () => {
  beforeEach(() => sessionStorage.clear())

  it('ramène dans le cercle et sa sous-page quand on revient par la barre', async () => {
    const r = routeur()
    await r.push('/cercles/c1?outil=taches')
    await r.push('/missions')
    await r.push('/cercles')
    expect(r.currentRoute.value.fullPath).toBe('/cercles/c1?outil=taches')
  })

  it('laisse la liste accessible depuis l’intérieur, puis l’oublie', async () => {
    const r = routeur()
    await r.push('/cercles/c1')
    await r.push('/cercles')
    expect(r.currentRoute.value.fullPath).toBe('/cercles')
    await r.push('/missions')
    await r.push('/cercles')
    expect(r.currentRoute.value.fullPath).toBe('/cercles')
  })

  it('fait de même pour les communautés, sans mélanger les espaces', async () => {
    const r = routeur()
    await r.push('/communautes/flutter')
    await r.push('/cercles/c1?outil=salon')
    await r.push('/communautes')
    expect(r.currentRoute.value.fullPath).toBe('/communautes/flutter')
    await r.push('/cercles')
    expect(r.currentRoute.value.fullPath).toBe('/cercles/c1?outil=salon')
  })

  it('n’intervient pas à l’ouverture de l’onglet', async () => {
    sessionStorage.setItem('jamii.espace.cercles', '/cercles/c1')
    const r = routeur()
    await r.push('/cercles')
    expect(r.currentRoute.value.fullPath).toBe('/cercles')
  })
})
