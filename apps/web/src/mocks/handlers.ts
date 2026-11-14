import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/socle'
import type { components as ComposantsSystalink } from '../api/generated/systalink'
import { handlersVitrine } from './vitrine'
import { handlersCommunautes } from './communautes'
import { handlersProfils } from './profils'
import { handlersDemandes } from './demandes'
import { handlersCercles } from './cercles'
import { handlersReseau } from './reseau'

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
type Systalink = import('../api/generated/systalink').components['schemas']

// Les tendances de démo : des chiffres agrégés, aucune personne derrière.
export const tendancesDemo: Systalink['Tendances'] = {
  periode: 'mois',
  besoins: 47,
  secteurs: [{ secteur: 'commerce', besoins: 16 }, { secteur: 'juridique', besoins: 9 }, { secteur: 'sante', besoins: 8 }, { secteur: 'education', besoins: 6 }, { secteur: 'autres', besoins: 8 }],
  services: [{ service: 'orion', suggestions: 21 }, { service: 'datacloud-application', suggestions: 17 }, { service: 'systaone', suggestions: 12 }, { service: 'datacloud-stockage', suggestions: 9 }],
  competencesManquantes: [
    { competence: 'paiement-mobile', demandes: 14, profils: 3 },
    { competence: 'flutter', demandes: 11, profils: 9 },
    { competence: 'postgresql', demandes: 10, profils: 12 },
    { competence: 'ux-design', demandes: 8, profils: 2 },
  ],
  pays: [{ pays: 'SN', besoins: 21 }, { pays: 'CI', besoins: 9 }, { pays: 'NG', besoins: 7 }, { pays: 'KE', besoins: 4 }, { pays: 'autres', besoins: 6 }],
}

// Des clients suivis fictifs : uniquement des personnes qui ont coché la case, avec un e-mail d'exemple.
export const clientsDemo: Systalink['ClientSuivi'][] = [
  { demandeId: 'b7a10000-0000-4000-8000-000000000001', creeLe: '2026-11-14T09:12:00Z', resume: 'Un cabinet d’avocats veut retrouver et partager ses dossiers, et un espace client pour suivre les affaires.', secteur: 'juridique', ville: 'Dakar', pays: 'SN', services: ['systaone', 'datacloud-stockage'], developpeursNecessaires: true, contact: { nom: 'Cabinet Ndiaye', email: 'contact@exemple.sn' }, statut: 'nouveau' },
  { demandeId: 'b7a10000-0000-4000-8000-000000000002', creeLe: '2026-11-13T16:40:00Z', resume: 'Une boulangerie veut un site avec ses horaires et la commande de gâteaux.', secteur: 'commerce', ville: 'Abidjan', pays: 'CI', services: ['orion', 'nom-de-domaine'], developpeursNecessaires: false, contact: { nom: 'Boulangerie du Plateau', email: null }, statut: 'contacte' },
]

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
  ...handlersReseau,
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
  http.get('/api/systalink/tendances', ({ request }) => {
    const periode = new URL(request.url).searchParams.get('periode') ?? 'mois'
    return HttpResponse.json({ ...tendancesDemo, periode })
  }),
  http.get('/api/systalink/clients-suivis', ({ request }) => {
    const adresse = new URL(request.url)
    const statut = adresse.searchParams.get('statut')
    const elements = clientsDemo.filter((c) => !statut || c.statut === statut)
    if (request.headers.get('Accept') === 'text/csv') {
      const lignes = elements.map((c) => [c.creeLe, c.contact.nom, c.contact.email ?? '', c.secteur ?? '', c.statut].join(';'))
      return new HttpResponse(['date;nom;email;secteur;statut', ...lignes].join('\n'), { headers: { 'Content-Type': 'text/csv' } })
    }
    return HttpResponse.json({ elements, curseurSuivant: null })
  }),
  http.patch('/api/systalink/clients-suivis/:demandeId', async ({ params, request }) => {
    const client = clientsDemo.find((c) => c.demandeId === params.demandeId)
    if (!client) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    client.statut = ((await request.json()) as { statut: typeof client.statut }).statut
    return HttpResponse.json(client)
  }),
  http.post('/api/systalink/moments/:momentId/accepter', () => new HttpResponse(null, { status: 204 })),
  http.post('/api/auth/deconnexion', () => {
    connecte = false
    return new HttpResponse(null, { status: 204 })
  }),
]
