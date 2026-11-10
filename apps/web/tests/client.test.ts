import { describe, expect, it } from 'vitest'
import { adresseConnexionGithub, jetonCsrf } from '../src/api/client'

describe('jetonCsrf', () => {
  it('lit le cookie XSRF-TOKEN', () => {
    expect(jetonCsrf('a=1; XSRF-TOKEN=abc%3D%3D; b=2')).toBe('abc==')
  })
  it('renvoie undefined sans cookie', () => {
    expect(jetonCsrf('a=1')).toBeUndefined()
  })
})

describe('adresseConnexionGithub', () => {
  it('encode le chemin de retour', () => {
    expect(adresseConnexionGithub('/projets?id=1')).toBe(`${location.origin}/api/auth/github?retour=%2Fprojets%3Fid%3D1`)
  })
})
