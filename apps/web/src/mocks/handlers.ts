import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/socle'
import type { components as ComposantsSystalink } from '../api/generated/systalink'
import { handlersVitrine } from './vitrine'
import { handlersCommunautes } from './communautes'
import { handlersProfils } from './profils'
import { handlersDemandes } from './demandes'
import { handlersCercles } from './cercles'

type Moi = components['schemas']['Moi']
type TableauDeBord = ComposantsSystalink['schemas']['TableauDeBord']

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

// Reprend l'exemple « demo » de contracts/systalink.yaml.
export const tableauDemo: TableauDeBord = {
  periode: 'mois',
  calculeLe: '2026-11-13T10:00:00Z',
  inscrits: {
    total: 64,
    nouveaux: 41,
    parPays: [
      { pays: 'SN', nombre: 22 },
      { pays: 'CI', nombre: 11 },
      { pays: 'KE', nombre: 9 },
      { pays: 'NG', nombre: 8 },
      { pays: 'CM', nombre: 6 },
    ],
    autres: 8,
  },
  projets: { crees: 23, nouveaux: 17, enLigne: 9, surDatacloud: 7 },
  // Les 3 cercles de la démo (dont celui de Dakar), plus un cercle déjà terminé.
  cercles: {
    actifs: 3,
    termines: 1,
    toursLivres: 7,
    nouveauxToursLivres: 4,
    membres: { total: 22, parPays: [{ pays: 'SN', nombre: 9 }, { pays: 'CI', nombre: 6 }], autres: 7 },
  },
  moments: [
    { service: 'datacloud', proposes: 12, acceptes: 7 },
    { service: 'orion', proposes: 5, acceptes: 2 },
    { service: 'yokk', proposes: 8, acceptes: 3 },
  ],
}

let connecte = true

export const handlers = [
  ...handlersVitrine,
  ...handlersCommunautes,
  ...handlersProfils,
  ...handlersDemandes,
  ...handlersCercles,
  http.get('/api/me', () =>
    connecte
      ? HttpResponse.json(moiDemo)
      : HttpResponse.json(
          { type: 'urn:jamii:erreur:non_connecte', title: 'Non connecté', status: 401, code: 'non_connecte' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
  ),
  http.get('/api/systalink/tableau-de-bord', ({ request }) => {
    const periode = new URL(request.url).searchParams.get('periode') ?? 'mois'
    return HttpResponse.json({ ...tableauDemo, periode } as TableauDeBord)
  }),
  http.post('/api/systalink/moments/:momentId/accepter', () => new HttpResponse(null, { status: 204 })),
  http.post('/api/auth/deconnexion', () => {
    connecte = false
    return new HttpResponse(null, { status: 204 })
  }),
]
