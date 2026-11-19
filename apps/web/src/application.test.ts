// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { peutHydrater } from './application'
import { rendreAccueil } from './entree-prerendu'

describe("pré-rendu de l'accueil (docs/adr/0011)", () => {
  it("rend l'accueil d'un visiteur sans compte, en français, sans navigateur ni API", async () => {
    const html = await rendreAccueil()
    expect(html).toContain('Trouve ton équipe.')
    expect(html).toContain('Se connecter')
    // Rien de ce qui suppose une session : ni profil, ni déconnexion.
    expect(html).not.toContain('data-test="mon-profil"')
    expect(html).not.toContain('data-test="deconnexion-menu"')
  })

  it('donne le même HTML à chaque build : le rendu ne dépend ni de l’heure ni du hasard', async () => {
    expect(await rendreAccueil()).toBe(await rendreAccueil())
  })
})

describe('peutHydrater', () => {
  const base = { prerendu: true, chemin: '/', etat: 'non_connecte', langue: 'fr' }

  it('reprend le HTML dans le seul cas pré-rendu : accueil, sans compte, en français', () => {
    expect(peutHydrater(base)).toBe(true)
  })

  it.each([
    ['page servie sans pré-rendu (app.html)', { prerendu: false }],
    ['autre page', { chemin: '/missions' }],
    ['personne connectée', { etat: 'connecte' }],
    ['API injoignable', { etat: 'indisponible' }],
    ['interface en anglais', { langue: 'en' }],
  ])('redessine la page à neuf : %s', (_cas, ecart) => {
    expect(peutHydrater({ ...base, ...ecart })).toBe(false)
  })
})
