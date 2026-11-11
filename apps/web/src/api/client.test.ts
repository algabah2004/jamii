import { describe, expect, it } from 'vitest'
import { adresseConnexionGithub, lireJetonCsrf } from './client'

describe('lireJetonCsrf', () => {
  it('lit et décode le cookie XSRF-TOKEN', () => {
    expect(lireJetonCsrf('a=1; XSRF-TOKEN=ab%2Bcd; b=2')).toBe('ab+cd')
  })
  it("renvoie null quand l'API n'a pas encore posé le cookie", () => {
    expect(lireJetonCsrf('a=1')).toBeNull()
  })
})

describe('adresseConnexionGithub', () => {
  it('garde un chemin relatif du front', () => {
    expect(adresseConnexionGithub('/profil?onglet=projets')).toBe(
      '/api/auth/github?retour=%2Fprofil%3Fonglet%3Dprojets',
    )
  })
  it.each(['//pirate.example', 'https://pirate.example', 'profil'])(
    'refuse une redirection hors du front (%s)',
    (retour) => {
      expect(adresseConnexionGithub(retour)).toBe('/api/auth/github?retour=%2F')
    },
  )
})
