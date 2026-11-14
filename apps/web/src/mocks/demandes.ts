import { http, HttpResponse } from 'msw'
import type { Demande, NouvelleDemande, Orientation, PlanProjet, Recommandation } from '../features/demandes/demandes'

/** Durée simulée de la préparation du plan, pour voir l'attente (immédiate dans les tests). */
export const DELAI_PLAN_MS = import.meta.env.MODE === 'test' ? 0 : 2500

// Données de démo (docs/plan-final.md) : un plan plausible pour une petite boutique. Jamais de vraies données.
export function planDemo(dureeSemaines = 8): PlanProjet {
  const s = (part: number) => Math.max(1, Math.round(dureeSemaines * part))
  return {
    cahierDesCharges: {
      resume:
        "Une boutique en ligne simple pour vendre les créations de l'atelier : un catalogue avec photos et prix, des commandes reçues sur WhatsApp et un paiement par Mobile Money ou à la livraison. Pensée pour un téléphone et une connexion lente.",
      objectifs: [
        'Vendre en ligne sans passer ses journées sur WhatsApp',
        'Montrer les nouveaux modèles chaque semaine',
        'Être payée par Mobile Money ou à la livraison',
      ],
      fonctionnalites: [
        { titre: 'Catalogue avec photos et prix', description: 'La gérante ajoute un modèle depuis son téléphone, en trois champs.', priorite: 'indispensable' },
        { titre: 'Commande en un message WhatsApp', description: 'Le client choisit, le récapitulatif part tout seul sur WhatsApp.', priorite: 'indispensable' },
        { titre: 'Paiement Mobile Money', description: 'Orange Money et Wave, avec un reçu.', priorite: 'indispensable' },
        { titre: 'Suivi des commandes', description: 'Une liste « à coudre, prête, livrée ».', priorite: 'utile' },
        { titre: 'Prise de mesures guidée', description: 'Un petit formulaire illustré pour les mesures.', priorite: 'utile' },
        { titre: 'Programme de fidélité', description: 'Une remise après cinq commandes.', priorite: 'plus_tard' },
      ],
      horsPerimetre: ['Livraison hors de la région', 'Application à installer sur le téléphone'],
    },
    jalons: [
      { titre: 'Catalogue en ligne', semaine: s(0.25), livrable: 'Les 20 premiers modèles visibles sur une adresse publique.' },
      { titre: 'Commandes et paiement', semaine: s(0.65), livrable: 'Une commande payée de bout en bout, reçu compris.' },
      { titre: 'Remise à la gérante', semaine: s(1), livrable: 'La gérante gère seule son catalogue et ses commandes.' },
    ],
    taches: [
      { titre: 'Maquette du catalogue sur téléphone', jalon: 0, competences: ['design'] },
      { titre: 'Pages catalogue et modèle', jalon: 0, competences: ['vue', 'typescript'] },
      { titre: 'Ajout d’un modèle depuis le téléphone', jalon: 0, competences: ['spring', 'postgresql'] },
      { titre: 'Récapitulatif envoyé sur WhatsApp', jalon: 1, competences: ['typescript'] },
      { titre: 'Paiement Mobile Money et reçu', jalon: 1, competences: ['mobile-money', 'spring'] },
      { titre: 'Formation de la gérante (30 min)', jalon: 2, competences: ['pédagogie'] },
      { titre: 'Mise en ligne et nom de domaine', jalon: 2, competences: ['devops'] },
    ],
    rolesManquants: [
      { role: 'Designer d’interface', competences: ['design', 'figma'], raison: 'Le catalogue doit être agréable sur un petit téléphone.' },
      { role: 'Développeur paiement mobile', competences: ['mobile-money', 'spring'], raison: 'Orange Money et Wave demandent une intégration soignée.' },
    ],
    servicesSuggeres: [
      { service: 'datacloud', raison: 'Héberger la boutique près de ses clients, à Dakar.' },
      { service: 'domaine', raison: 'Une adresse simple à dire au téléphone.' },
    ],
  }
}

/**
 * L'orientation de démo (docs/priorites.md, étape 1) : un cabinet d'avocats qui perd ses dossiers. Des services du
 * catalogue (services/ia/catalogue-systalink.yaml, recopiés tels quels) pour une partie, des développeurs pour le reste.
 */
export function orientationDemo(dureeSemaines = 8): Orientation {
  return {
    resume:
      "Le cabinet veut retrouver ses dossiers en quelques secondes, les partager entre associés sans clé USB, et donner aux clients un espace pour suivre l'avancement de leur affaire.",
    secteur: 'juridique',
    services: [
      {
        id: 'systaone',
        nom: 'SystaONE (SystaDrive, SystaMail, SystaTalk, SystaForms)',
        pourCeBesoin: 'Ranger et partager les dossiers entre associés dès demain, avec une messagerie professionnelle au nom du cabinet.',
        prixIndicatif: 'Essai de 14 jours, puis 2 000 à 12 000 FCFA par utilisateur et par mois',
        lien: 'https://systalink.com',
      },
      {
        id: 'datacloud-stockage',
        nom: 'DataCloud, stockage de fichiers',
        pourCeBesoin: 'Garder les PDF et les pièces scannées à Dakar, en sécurité, pour l’espace client.',
        prixIndicatif: null,
        lien: 'https://platform.systalink.com',
      },
    ],
    developpeursNecessaires: true,
    competences: ['react', 'typescript', 'postgresql', 'ux-design'],
    plan: planDemo(dureeSemaines),
  }
}

/** Les développeurs recommandés en démo : un score calculé par l'API, et ses raisons. */
export const RECOMMANDES: Recommandation[] = [
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000001', nom: 'Kwame Mensah', avatarUrl: null, ville: 'Accra', pays: 'GH', score: 86, competencesCouvertes: ['react', 'typescript', 'postgresql'], raisons: ['React, confirmé (3 dépôts)', 'PostgreSQL (2 dépôts)', 'Disponible'] },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000003', nom: 'Ibrahima Fall', avatarUrl: null, ville: 'Saint-Louis', pays: 'SN', score: 71, competencesCouvertes: ['postgresql'], raisons: ['PostgreSQL, confirmé (4 dépôts)', 'Disponible', 'Sénégal'] },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000002', nom: 'Amina Bello', avatarUrl: null, ville: 'Lagos', pays: 'NG', score: 64, competencesCouvertes: ['ux-design'], raisons: ['Design d’interface (2 projets en ligne)', 'Disponible'] },
]

const demandes = new Map<string, { demande: Demande; pretA: number }>()
const AWA = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'

export const handlersDemandes = [
  http.post('/api/demandes', async ({ request }) => {
    const corps = (await request.json()) as NouvelleDemande
    if (!corps.titre || corps.titre.trim().length < 3 || !corps.description || corps.description.trim().length < 20) {
      return HttpResponse.json({ code: 'invalide', champs: {} }, { status: 400 })
    }
    const demande: Demande = {
      id: crypto.randomUUID(),
      titre: corps.titre,
      description: corps.description,
      dureeSemaines: corps.dureeSemaines ?? null,
      secteur: corps.secteur ?? null,
      ville: corps.ville ?? null,
      pays: null,
      offresSystalink: corps.offresSystalink === true,
      creeLe: new Date().toISOString(),
      plan: { statut: 'en_attente' },
      orientation: { statut: 'en_attente' },
      recommandations: [],
    }
    demandes.set(demande.id, { demande, pretA: Date.now() + DELAI_PLAN_MS })
    return HttpResponse.json(demande, { status: 201 })
  }),

  http.get('/api/demandes/:id', ({ params }) => {
    const trouvee = demandes.get(String(params.id))
    if (!trouvee) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    const { demande, pretA } = trouvee
    if (Date.now() >= pretA && demande.plan.statut !== 'terminee') {
      const orientation = orientationDemo(demande.dureeSemaines ?? 8)
      demande.orientation = { statut: 'terminee', origine: 'ia', resultat: orientation }
      demande.plan = { statut: 'terminee', origine: 'ia', resultat: orientation.plan ?? undefined }
      demande.recommandations = RECOMMANDES
    } else if (demande.plan.statut === 'en_attente') {
      demande.plan = { statut: 'en_cours' }
      demande.orientation = { statut: 'en_cours' }
    }
    return HttpResponse.json(demande)
  }),

  http.put('/api/demandes/:id/offres-systalink', async ({ params, request }) => {
    const trouvee = demandes.get(String(params.id))
    if (!trouvee) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    const { accepte } = (await request.json()) as { accepte: boolean }
    trouvee.demande.offresSystalink = accepte === true
    return HttpResponse.json(trouvee.demande)
  }),

  http.post('/api/projets', async ({ request }) => {
    const corps = (await request.json()) as { titre: string; demandeId?: string }
    return HttpResponse.json({ id: crypto.randomUUID(), titre: corps.titre, porteur: { id: AWA, nom: 'Awa Diop' } }, { status: 201 })
  }),
]
