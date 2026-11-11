import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/vitrine'

type ProjetEnVitrine = components['schemas']['ProjetEnVitrine']

const personne = (id: string, nom: string) => ({ id, nom, avatarUrl: null })

/** Dates relatives à maintenant : la démo reste crédible quel que soit le jour où on la lance. */
const ilYaHeures = (heures: number) => new Date(Date.now() - heures * 3_600_000).toISOString()

// Données de démo : plusieurs pays, des PME, jamais de vraies personnes (docs/plan-final.md).
// Les adresses en .example.com n'appartiennent à personne (RFC 2606).
export const vitrineDemo: ProjetEnVitrine[] = [
  {
    projetId: '3f0e6c1a-2b4d-4e8f-9a7c-1d2e3f4a5b6c',
    titre: 'Boutique en ligne pour une couturière de Thiès',
    accroche: 'Catalogue, commandes par WhatsApp et paiement à la livraison, pour une PME de 3 personnes.',
    origine: 'demande',
    url: 'https://couture-thies.example.com',
    hebergeur: 'datacloud',
    misEnLigneLe: ilYaHeures(17),
    verifieLe: ilYaHeures(1),
    paysEquipe: ['SN', 'CI'],
    equipe: [personne('6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11', 'Awa Diop'), personne('0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b', 'Koffi Yao')],
  },
  {
    projetId: '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
    titre: 'Suivi des tontines d’un groupement de femmes',
    accroche: 'Les cotisations et les tours de chacune, consultables depuis un téléphone simple.',
    origine: 'idee',
    url: 'https://tontine.example.com',
    hebergeur: 'datacloud',
    misEnLigneLe: ilYaHeures(70),
    verifieLe: ilYaHeures(2),
    paysEquipe: ['CM', 'SN'],
    equipe: [personne('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', 'Mbarga Essomba'), personne('2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a', 'Fatou Ndiaye')],
  },
  {
    projetId: '4b5c6d7e-8f9a-4b0c-8d1e-2f3a4b5c6d7e',
    titre: 'Prise de rendez-vous pour un cabinet dentaire',
    accroche: 'Les patients réservent un créneau et reçoivent un rappel par SMS la veille.',
    origine: 'demande',
    url: 'https://dentiste-abidjan.example.com',
    hebergeur: 'autre',
    misEnLigneLe: ilYaHeures(120),
    verifieLe: ilYaHeures(3),
    paysEquipe: ['CI', 'KE'],
    equipe: [personne('3e4f5a6b-7c8d-4e9f-8a0b-1c2d3e4f5a6b', 'Aya Kouassi'), personne('4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c', 'Wanjiru Kamau')],
  },
  {
    projetId: '5c6d7e8f-9a0b-4c1d-9e2f-3a4b5c6d7e8f',
    titre: 'Prix du marché pour les producteurs de mil',
    accroche: 'Les prix relevés chaque semaine sur les marchés de la région, pour vendre au bon moment.',
    origine: 'idee',
    url: 'https://prix-mil.example.com',
    hebergeur: 'datacloud',
    misEnLigneLe: ilYaHeures(190),
    verifieLe: ilYaHeures(5),
    paysEquipe: ['NG', 'GH', 'SN'],
    equipe: [
      personne('5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d', 'Chidi Okafor'),
      personne('6b7c8d9e-0f1a-4b2c-9d3e-4f5a6b7c8d9e', 'Kwame Mensah'),
      personne('7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f', 'Moussa Sow'),
    ],
  },
]

export const handlersVitrine = [
  http.get('/api/vitrine', ({ request }) => {
    const parametres = new URL(request.url).searchParams
    const hebergeur = parametres.get('hebergeur')
    const pays = parametres.get('pays')
    const limite = Number(parametres.get('limite') ?? 20)
    const debut = Number(parametres.get('curseur') ?? 0)
    const filtres = vitrineDemo.filter(
      (p) => (!hebergeur || p.hebergeur === hebergeur) && (!pays || p.paysEquipe.includes(pays)),
    )
    const elements = filtres.slice(debut, debut + limite)
    const suivant = debut + limite < filtres.length ? String(debut + limite) : null
    return HttpResponse.json({ elements, curseurSuivant: suivant })
  }),
]
