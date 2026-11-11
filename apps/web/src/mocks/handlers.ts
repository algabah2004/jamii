import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/socle'

type Moi = components['schemas']['Moi']

// Données de démo : jamais de vraies données personnelles (docs/plan-final.md).
export const moiDemo: Moi = {
  id: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11',
  nom: 'Awa Diop',
  email: 'awa@example.com',
  avatarUrl: null,
  loginGithub: 'awa-demo',
  methodesConnexion: ['github'],
  roles: ['membre'],
  creeLe: '2026-11-01T09:00:00Z',
}

let connecte = true

export const handlers = [
  http.get('/api/me', () =>
    connecte
      ? HttpResponse.json(moiDemo)
      : HttpResponse.json(
          { type: 'urn:jamii:erreur:non_connecte', title: 'Non connecté', status: 401, code: 'non_connecte' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
  ),
  http.post('/api/auth/deconnexion', () => {
    connecte = false
    return new HttpResponse(null, { status: 204 })
  }),
]
