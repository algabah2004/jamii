import { http, HttpResponse } from 'msw'
import type { Demande, NouvelleDemande, Orientation, PlanProjet, Recommandation } from '../features/demandes/demandes'

/** Durée simulée de la préparation du plan, pour voir l'attente (immédiate dans les tests). */
export const DELAI_PLAN_MS = import.meta.env.MODE === 'test' ? 0 : 2500

type Langue = 'fr' | 'en'

// Données de démo (docs/plan-final.md) : un plan plausible pour une petite boutique. Jamais de vraies données.
// Dans la langue de la demande, comme le vrai service.
export function planDemo(dureeSemaines = 8, langue: Langue = 'fr'): PlanProjet {
  const s = (part: number) => Math.max(1, Math.round(dureeSemaines * part))
  const en = langue === 'en'
  return {
    cahierDesCharges: {
      resume: en
        ? 'A simple online shop to sell the workshop’s creations: a catalogue with photos and prices, orders received on WhatsApp and payment by Mobile Money or on delivery. Built for a phone and a slow connection.'
        : "Une boutique en ligne simple pour vendre les créations de l'atelier : un catalogue avec photos et prix, des commandes reçues sur WhatsApp et un paiement par Mobile Money ou à la livraison. Pensée pour un téléphone et une connexion lente.",
      objectifs: en
        ? ['Sell online without spending all day on WhatsApp', 'Show the new designs every week', 'Get paid by Mobile Money or on delivery']
        : ['Vendre en ligne sans passer ses journées sur WhatsApp', 'Montrer les nouveaux modèles chaque semaine', 'Être payée par Mobile Money ou à la livraison'],
      fonctionnalites: en
        ? [
            { titre: 'Catalogue with photos and prices', description: 'The manager adds a design from her phone, in three fields.', priorite: 'indispensable' },
            { titre: 'Order in one WhatsApp message', description: 'The customer picks, the summary is sent on WhatsApp automatically.', priorite: 'indispensable' },
            { titre: 'Mobile Money payment', description: 'Orange Money and Wave, with a receipt.', priorite: 'indispensable' },
            { titre: 'Order tracking', description: 'A “to sew, ready, delivered” list.', priorite: 'utile' },
            { titre: 'Guided measurements', description: 'A small illustrated form for measurements.', priorite: 'utile' },
            { titre: 'Loyalty programme', description: 'A discount after five orders.', priorite: 'plus_tard' },
          ]
        : [
            { titre: 'Catalogue avec photos et prix', description: 'La gérante ajoute un modèle depuis son téléphone, en trois champs.', priorite: 'indispensable' },
            { titre: 'Commande en un message WhatsApp', description: 'Le client choisit, le récapitulatif part tout seul sur WhatsApp.', priorite: 'indispensable' },
            { titre: 'Paiement Mobile Money', description: 'Orange Money et Wave, avec un reçu.', priorite: 'indispensable' },
            { titre: 'Suivi des commandes', description: 'Une liste « à coudre, prête, livrée ».', priorite: 'utile' },
            { titre: 'Prise de mesures guidée', description: 'Un petit formulaire illustré pour les mesures.', priorite: 'utile' },
            { titre: 'Programme de fidélité', description: 'Une remise après cinq commandes.', priorite: 'plus_tard' },
          ],
      horsPerimetre: en
        ? ['Delivery outside the region', 'An app to install on the phone']
        : ['Livraison hors de la région', 'Application à installer sur le téléphone'],
    },
    jalons: en
      ? [
          { titre: 'Catalogue online', semaine: s(0.25), livrable: 'The first 20 designs visible at a public address.' },
          { titre: 'Orders and payment', semaine: s(0.65), livrable: 'One paid order end to end, receipt included.' },
          { titre: 'Handover to the manager', semaine: s(1), livrable: 'The manager runs her catalogue and orders on her own.' },
        ]
      : [
          { titre: 'Catalogue en ligne', semaine: s(0.25), livrable: 'Les 20 premiers modèles visibles sur une adresse publique.' },
          { titre: 'Commandes et paiement', semaine: s(0.65), livrable: 'Une commande payée de bout en bout, reçu compris.' },
          { titre: 'Remise à la gérante', semaine: s(1), livrable: 'La gérante gère seule son catalogue et ses commandes.' },
        ],
    taches: en
      ? [
          { titre: 'Catalogue mock-up on a phone', jalon: 0, competences: ['design'] },
          { titre: 'Catalogue and design pages', jalon: 0, competences: ['vue', 'typescript'] },
          { titre: 'Add a design from the phone', jalon: 0, competences: ['spring', 'postgresql'] },
          { titre: 'Summary sent on WhatsApp', jalon: 1, competences: ['typescript'] },
          { titre: 'Mobile Money payment and receipt', jalon: 1, competences: ['mobile-money', 'spring'] },
          { titre: 'Train the manager (30 min)', jalon: 2, competences: ['teaching'] },
          { titre: 'Go live and domain name', jalon: 2, competences: ['devops'] },
        ]
      : [
          { titre: 'Maquette du catalogue sur téléphone', jalon: 0, competences: ['design'] },
          { titre: 'Pages catalogue et modèle', jalon: 0, competences: ['vue', 'typescript'] },
          { titre: 'Ajout d’un modèle depuis le téléphone', jalon: 0, competences: ['spring', 'postgresql'] },
          { titre: 'Récapitulatif envoyé sur WhatsApp', jalon: 1, competences: ['typescript'] },
          { titre: 'Paiement Mobile Money et reçu', jalon: 1, competences: ['mobile-money', 'spring'] },
          { titre: 'Formation de la gérante (30 min)', jalon: 2, competences: ['pédagogie'] },
          { titre: 'Mise en ligne et nom de domaine', jalon: 2, competences: ['devops'] },
        ],
    rolesManquants: en
      ? [
          { role: 'Interface designer', competences: ['design', 'figma'], raison: 'The catalogue must be pleasant on a small phone.' },
          { role: 'Mobile payment developer', competences: ['mobile-money', 'spring'], raison: 'Orange Money and Wave need a careful integration.' },
        ]
      : [
          { role: 'Designer d’interface', competences: ['design', 'figma'], raison: 'Le catalogue doit être agréable sur un petit téléphone.' },
          { role: 'Développeur paiement mobile', competences: ['mobile-money', 'spring'], raison: 'Orange Money et Wave demandent une intégration soignée.' },
        ],
    servicesSuggeres: en
      ? [
          { service: 'datacloud', raison: 'Host the shop close to its customers, in Dakar.' },
          { service: 'domaine', raison: 'An address that is easy to say on the phone.' },
        ]
      : [
          { service: 'datacloud', raison: 'Héberger la boutique près de ses clients, à Dakar.' },
          { service: 'domaine', raison: 'Une adresse simple à dire au téléphone.' },
        ],
  }
}

/**
 * L'orientation de démo (docs/priorites.md, étape 1) : un cabinet d'avocats qui perd ses dossiers. Des services du
 * catalogue (services/ia/catalogue-systalink.yaml) pour une partie, des développeurs pour le reste. Dans la langue de la
 * demande, comme le vrai service.
 */
export function orientationDemo(dureeSemaines = 8, langue: Langue = 'fr'): Orientation {
  const en = langue === 'en'
  return {
    message: en
      ? 'Your firm loses time looking for its files and passing them around on USB sticks. Part of this can be solved right away with tools that already exist; for the space where your clients follow their case, you will need developers.'
      : "Ton cabinet perd du temps à chercher ses dossiers et à les faire circuler sur clé USB. Une partie se règle tout de suite avec des outils qui existent déjà ; pour l'espace où tes clients suivent leur affaire, il faudra des développeurs.",
    resume: en
      ? 'The firm wants to find its files in seconds, share them between partners without USB sticks, and give clients a space to follow the progress of their case.'
      : "Le cabinet veut retrouver ses dossiers en quelques secondes, les partager entre associés sans clé USB, et donner aux clients un espace pour suivre l'avancement de leur affaire.",
    secteur: 'juridique',
    etapes: en
      ? [
          'List the files your partners open most often.',
          'Write down what your clients ask you most often on the phone about their case.',
          'Message one of the developers suggested below to talk about it.',
        ]
      : [
          'Fais la liste des dossiers que tes associés ouvrent le plus souvent.',
          'Note ce que tes clients te demandent le plus au téléphone sur leur affaire.',
          'Écris à un des développeurs proposés plus bas pour en parler.',
        ],
    question: null,
    services: [
      {
        id: 'datacloud-stockage',
        nom: en ? 'DataCloud, file storage' : 'DataCloud, stockage de fichiers',
        pourCeBesoin: en
          ? 'Keep the firm’s PDFs and scanned documents safe, with version history, for the client space the developers will build.'
          : 'Garder les PDF et les pièces scannées du cabinet en sécurité, avec l’historique des versions, pour l’espace client que les développeurs construiront.',
        prixIndicatif: en ? 'From 3,280 CFA francs a month (250 GB)' : 'Dès 3 280 FCFA par mois (250 Go)',
        lien: 'https://systalink.com/fr/stockage-objets',
        momentId: 'b0a10000-0000-4000-8000-000000000001',
      },
      {
        id: 'email-professionnel',
        nom: en ? 'Professional email address' : 'Adresse e-mail professionnelle',
        pourCeBesoin: en
          ? 'Write to clients from an address in the firm’s name, which inspires trust.'
          : 'Écrire aux clients depuis une adresse au nom du cabinet, qui inspire confiance.',
        prixIndicatif: en ? 'From 499 CFA francs a month per address' : 'Dès 499 FCFA par mois et par adresse',
        lien: 'https://systalink.com/fr/email-professionnel',
        momentId: 'b0a10000-0000-4000-8000-000000000002',
      },
    ],
    developpeursNecessaires: true,
    competences: ['react', 'typescript', 'postgresql', 'ux-design'],
    plan: planDemo(dureeSemaines, langue),
  }
}

/** La question d'Ayo en démo : il ne conseille pas encore (contracts/ia.yaml). */
export function questionDemo(langue: Langue = 'fr'): Orientation {
  const en = langue === 'en'
  return {
    message: en ? 'Happy to help you with that.' : 'Avec plaisir, je vais t’aider.',
    resume: en ? 'A need to clarify.' : 'Un besoin à préciser.',
    secteur: 'autre',
    etapes: [],
    question: {
      texte: en ? 'Who are your customers, and how do they reach you today?' : 'Qui sont tes clients, et comment te contactent-ils aujourd’hui ?',
      suggestions: en ? ['On WhatsApp', 'They come to the shop', 'By phone'] : ['Sur WhatsApp', 'Ils viennent à la boutique', 'Par téléphone'],
    },
    services: [],
    developpeursNecessaires: false,
    competences: [],
    plan: null,
  }
}

/** Les développeurs recommandés en démo : un score calculé par l'API, et ses raisons. */
export const RECOMMANDES: Recommandation[] = [
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000001', nom: 'Kwame Mensah', avatarUrl: null, ville: 'Accra', pays: 'GH', score: 86, competencesCouvertes: ['react', 'typescript', 'postgresql'], raisons: ['React, confirmé (3 dépôts)', 'PostgreSQL (2 dépôts)', 'Disponible'] },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000003', nom: 'Ibrahima Fall', avatarUrl: null, ville: 'Saint-Louis', pays: 'SN', score: 71, competencesCouvertes: ['postgresql'], raisons: ['PostgreSQL, confirmé (4 dépôts)', 'Disponible', 'Sénégal'] },
  { utilisateurId: 'd2e00000-0000-4000-8000-000000000002', nom: 'Amina Bello', avatarUrl: null, ville: 'Lagos', pays: 'NG', score: 64, competencesCouvertes: ['ux-design'], raisons: ['Design d’interface (2 projets en ligne)', 'Disponible'] },
]

const demandes = new Map<string, { demande: Demande; pretA: number; langue: Langue }>()
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
    demandes.set(demande.id, { demande, pretA: Date.now() + DELAI_PLAN_MS, langue: corps.langue === 'en' ? 'en' : 'fr' })
    return HttpResponse.json(demande, { status: 201 })
  }),

  http.get('/api/demandes', () =>
    HttpResponse.json(
      [...demandes.values()]
        .map(({ demande }) => ({
          id: demande.id,
          titre: demande.titre,
          creeLe: demande.creeLe,
          resume: demande.orientation?.resultat?.resume ?? null,
          precisions: demande.precisions?.length ?? 0,
          publiee: Boolean(demande.publication),
        }))
        .reverse(),
    ),
  ),

  http.post('/api/demandes/:id/precisions', async ({ params, request }) => {
    const trouvee = demandes.get(String(params.id))
    if (!trouvee) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    const { texte } = (await request.json()) as { texte: string }
    const precisions = trouvee.demande.precisions ?? []
    if (precisions.length >= 12) return HttpResponse.json({ code: 'trop_de_precisions' }, { status: 409 })
    // Comme l'API : la réponse garde ce qu'Ayo venait de dire.
    const avant = trouvee.demande.orientation?.resultat
    const ayo = avant ? [avant.message ?? avant.resume, avant.question?.texte].filter(Boolean).join('\n\n') : null
    trouvee.demande.precisions = [...precisions, { texte: texte.trim(), creeLe: new Date().toISOString(), ayo, ayoQuestion: Boolean(avant?.question) }]
    trouvee.demande.plan = { statut: 'en_attente' }
    trouvee.demande.orientation = { statut: 'en_attente' }
    trouvee.pretA = Date.now() + DELAI_PLAN_MS
    return HttpResponse.json(trouvee.demande)
  }),

  http.get('/api/demandes/:id', ({ params }) => {
    const trouvee = demandes.get(String(params.id))
    if (!trouvee) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    const { demande, pretA, langue } = trouvee
    if (Date.now() >= pretA && demande.plan.statut !== 'terminee') {
      // Un premier message très court (moins de 40 caractères) : Ayo demande d’abord une précision, sans conseiller.
      const orientation = !demande.precisions?.length && demande.description.length < 40 ? questionDemo(langue) : orientationDemo(demande.dureeSemaines ?? 8, langue)
      demande.orientation = { statut: 'terminee', origine: 'ia', resultat: orientation }
      demande.plan = { statut: 'terminee', origine: 'ia', resultat: orientation.plan ?? undefined }
      demande.recommandations = orientation.developpeursNecessaires ? RECOMMANDES : []
    } else if (demande.plan.statut === 'en_attente') {
      demande.plan = { statut: 'en_cours' }
      demande.orientation = { statut: 'en_cours' }
    }
    return HttpResponse.json(demande)
  }),

  http.delete('/api/demandes/:id', ({ params }) =>
    demandes.delete(String(params.id)) ? new HttpResponse(null, { status: 204 }) : HttpResponse.json({ code: 'introuvable' }, { status: 404 }),
  ),

  http.post('/api/demandes/:id/publication', async ({ params, request }) => {
    const trouvee = demandes.get(String(params.id))
    if (!trouvee) return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
    const { resume } = (await request.json()) as { resume?: string }
    const propre = (resume ?? '').trim()
    if (propre.length < 20 || propre.length > 600) {
      return HttpResponse.json({ code: 'validation', erreurs: [{ champ: 'resume', code: 'longueur_invalide' }] }, { status: 400 })
    }
    if (trouvee.demande.plan.statut !== 'terminee') return HttpResponse.json({ code: 'plan_non_pret' }, { status: 409 })
    if (trouvee.demande.publication) return HttpResponse.json({ code: 'deja_publiee' }, { status: 409 })
    trouvee.demande.publication = { publieeLe: new Date().toISOString(), resume: propre }
    return HttpResponse.json(trouvee.demande)
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
