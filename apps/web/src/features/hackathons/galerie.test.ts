import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { i18n } from '../../i18n'
import { serveur } from '../../test-setup'
import { modifierHackathon, reinitialiserHackathons } from '../../mocks/hackathons'
import { lireHackathon, listerRendus, type Rendu, type Resultats } from './hackathons'
import GalerieHackathon from './GalerieHackathon.vue'

const ID = 'd7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e'
const rendu = (id: string, titre: string): Rendu => ({ id, titre, equipeId: 'equipe-1', description: 'Un outil pour les commerçants du quartier.', lienProduit: 'https://produit.example.com', depot: null, video: null, reponses: [], lienRepond: true, lienVerifieLe: '2026-11-15T10:00:00Z', version: 1, modifieLe: '2026-11-15T10:00:00Z' })

beforeEach(() => {
  reinitialiserHackathons()
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
})
enableAutoUnmount(afterEach)
async function monter() {
  const hackathon = await lireHackathon(ID)
  if (!hackathon) throw new Error('Hackathon de test absent')
  return mount(GalerieHackathon, { props: { hackathon }, global: { plugins: [i18n] } })
}

describe('la galerie des projets d’un hackathon', () => {
  it.each(['visiteur', 'participant', 'jure'] as const)('ne demande aucun rendu privé pour le rôle %s sans droit de notation', async (role) => {
    modifierHackathon(ID, (h) => { h.moi = { role, actions: [] } })
    const appels = vi.fn()
    serveur.use(http.get('/api/hackathons/:id/rendus', () => { appels(); return new HttpResponse(null, { status: 403 }) }))
    const page = await monter()
    await vi.waitFor(() => expect(page.find('[data-test="galerie-privee"]').exists()).toBe(true))
    expect(appels).not.toHaveBeenCalled()
    expect(page.text()).toContain('dévoilés au public avec les résultats')
  })

  it('permet à l’organisateur de rechercher parmi tous les rendus paginés', async () => {
    modifierHackathon(ID, (h) => { h.moi = { role: 'organisateur', actions: [] } })
    const curseurs: (string | null)[] = []
    serveur.use(http.get('/api/hackathons/:id/rendus', ({ request }) => {
      const curseur = new URL(request.url).searchParams.get('curseur')
      curseurs.push(curseur)
      return HttpResponse.json(curseur ? { elements: [rendu('p2', 'Suivi des récoltes')], curseurSuivant: null } : { elements: [rendu('p1', 'Carnet de caisse')], curseurSuivant: 'suite' })
    }))
    const page = await monter()
    await vi.waitFor(() => expect(page.findAll('[data-test="projet-hackathon"]')).toHaveLength(2))
    expect(curseurs).toEqual([null, 'suite'])
    await page.get('[data-test="recherche-exploration"]').setValue('RECOLTES')
    expect(page.findAll('[data-test="projet-hackathon"]')).toHaveLength(1)
    expect(page.get('[data-test="projet-hackathon"]').text()).toContain('Suivi des récoltes')
  })

  it('utilise les résultats publics après publication et refuse les liens non https', async () => {
    modifierHackathon(ID, (h) => { h.statut = 'resultats'; h.moi = { role: 'visiteur', actions: [] } })
    const projet = { ...rendu('p1', 'Carnet de caisse'), description: '<img src=x onerror="alert(1)"> Un projet de caisse.', lienProduit: 'javascript:alert(1)', video: 'javascript:alert(1)', depot: 'https://code.example.com/projet' }
    const personne = { id: 'membre', nom: 'Fatou', avatarUrl: null }
    const resultats: Resultats = { publieLe: '2026-11-15T10:00:00Z', gagnants: [], classement: [{ rang: 1, scorePondere: 4.5, equipe: { id: 'equipe-1', nom: 'Les bâtisseurs', chef: personne, membres: [personne], complete: false, tailleMax: 5, creeLe: '2026-11-15T10:00:00Z' }, rendu: projet, retourDuJury: [] }] }
    const prive = vi.fn()
    serveur.use(
      http.get('/api/hackathons/:id/resultats', () => HttpResponse.json(resultats)),
      http.get('/api/hackathons/:id/rendus', () => { prive(); return new HttpResponse(null, { status: 403 }) }),
    )
    const page = await monter()
    await vi.waitFor(() => expect(page.find('[data-test="projet-hackathon"]').exists()).toBe(true))
    expect(prive).not.toHaveBeenCalled()
    expect(page.text()).toContain('Les bâtisseurs')
    expect(page.find('img').exists()).toBe(false)
    const liens = page.findAll('a')
    expect(liens).toHaveLength(1)
    expect(liens[0]!.attributes('href')).toBe('https://code.example.com/projet')
    expect(liens[0]!.attributes('rel')).toContain('noopener')
  })

  it('permet de réessayer si les projets ne répondent pas', async () => {
    modifierHackathon(ID, (h) => { h.moi = { role: 'organisateur', actions: [] } })
    serveur.use(http.get('/api/hackathons/:id/rendus', () => new HttpResponse(null, { status: 503 })))
    const page = await monter()
    await vi.waitFor(() => expect(page.find('[role="alert"]').exists()).toBe(true))
    serveur.use(http.get('/api/hackathons/:id/rendus', () => HttpResponse.json({ elements: [], curseurSuivant: null })))
    await page.get('[role="alert"] button').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="galerie-vide"]').exists()).toBe(true))
  })

  it('arrête la pagination si le serveur répète un curseur', async () => {
    serveur.use(http.get('/api/hackathons/:id/rendus', () => HttpResponse.json({ elements: [], curseurSuivant: 'boucle' })))
    await expect(listerRendus(ID)).rejects.toThrow('curseur de rendus répété')
  })
})
