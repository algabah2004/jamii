import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiInjoignable, chargee, chargerSession, moi } from '../src/session'

const reponse = (status: number, corps?: unknown) =>
  new Response(corps ? JSON.stringify(corps) : null, {
    status,
    headers: { 'Content-Type': corps ? 'application/json' : 'application/problem+json' },
  })

afterEach(() => vi.unstubAllGlobals())

describe('chargerSession', () => {
  it('garde l’utilisateur connecté', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => reponse(200, { id: '1', nom: 'Amina', roles: ['membre'], methodesConnexion: ['github'], creeLe: '2026-11-10T00:00:00Z' })))
    await chargerSession()
    expect(moi.value?.nom).toBe('Amina')
    expect(chargee.value).toBe(true)
  })
  it('un visiteur non connecté (401) reste un visiteur, sans alerte', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => reponse(401)))
    await chargerSession()
    expect(moi.value).toBeNull()
    expect(apiInjoignable.value).toBe(false)
  })
  it('si l’API ne répond pas, on prévient sans planter', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('réseau') }))
    await chargerSession()
    expect(moi.value).toBeNull()
    expect(apiInjoignable.value).toBe(true)
  })
})
