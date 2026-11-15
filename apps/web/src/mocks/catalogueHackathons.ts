import type { Equipe, Hackathon, Inscription, Rendu, Resultats } from '../features/hackathons/hackathons'

/** Même catalogue fictif que DemoDecouverteHackathons côté serveur, avec les mêmes adresses d’événement. */
export const DEFIS_DEMO = [
  ['Sahel Mobile Challenge', 'Des services mobiles accessibles sans connexion permanente', 'Bamako', 'en_ligne', 'publie', 4, 2, 500000, 'XOF', 'Carnet des coopératives', 'Marché par SMS'],
  ['Lagos Fintech Builders', 'Des paiements simples pour les petits commerces', 'Lagos', 'hybride', 'publie', 7, 3, 25000000, 'NGN', 'Caisse des artisans', 'Reçus mobiles'],
  ['Nairobi Open Source Days', 'Des outils libres pour les associations de quartier', 'Nairobi', 'sur_place', 'publie', 10, 2, 10000000, 'KES', 'Annuaire associatif', 'Prêt de matériel'],
  ['Accra Design for Impact', 'Des interfaces inclusives pour les services essentiels', 'Accra', 'en_ligne', 'publie', 13, 5, 500000, 'GHS', 'Services accessibles', 'Parcours citoyens'],
  ['Casablanca Green Tech', 'Réduire les déchets et mieux partager les ressources', 'Casablanca', 'hybride', 'publie', 16, 10, 1000000, 'MAD', 'Collecte de quartier', 'Réemploi local'],
  ['Douala Education Lab', 'Des outils pédagogiques adaptés aux connexions limitées', 'Douala', 'sur_place', 'publie', 20, 3, 400000, 'XAF', 'Cours hors ligne', 'Bibliothèque partagée'],
  ['Kigali Health Innovation', 'Faciliter le quotidien des centres de santé', 'Kigali', 'en_ligne', 'publie', 23, 14, 100000, 'USD', 'Rendez-vous santé', 'Suivi des consultations'],
  ['Tunis Data Weekend', 'Ouvrir les données et les rendre utiles aux habitants', 'Tunis', 'hybride', 'publie', 27, 2, 80000, 'USD', 'Données du quartier', 'Mobilité ouverte'],
  ['Dakar AI for Good', 'Des assistants utiles aux petites entreprises', 'Dakar', 'hybride', 'en_cours', -1, 3, 700000, 'XOF', 'Assistant démarches', 'Conseiller de quartier'],
  ['Cape Town Climate Code', 'Construire des services pour la résilience climatique', 'Cape Town', 'en_ligne', 'en_cours', -4, 10, 1500000, 'ZAR', 'Eau partagée', 'Alertes chaleur'],
  ['Cotonou Civic Tech', 'Simplifier les démarches des associations locales', 'Cotonou', 'sur_place', 'evaluation', -3, 2, 350000, 'XOF', 'Démarches associatives', 'Budget participatif'],
  ['Thiès Makers Festival', 'Faire connaître les métiers et ateliers de proximité', 'Thiès', 'hybride', 'resultats', -16, 3, 600000, 'XOF', 'Ateliers ouverts', 'Carnet des réparateurs'],
] as const

const AWA = { id: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11', nom: 'Awa Diop', avatarUrl: null }
const NIA = { id: 'c1a00000-0000-4000-8000-000000000005', nom: 'Nia Wanjiru', avatarUrl: null }
const MEMBRES = [
  { id: '0b9f3c1d-2e4a-4f6b-8c7d-9e0a1b2c3d4e', nom: 'Kofi Mensah', avatarUrl: null },
  { id: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', nom: 'Fatou Ndiaye', avatarUrl: null },
  { id: '9e8d7c6b-5a4f-4e3d-9c2b-1a0f9e8d7c6b', nom: 'Moussa Traoré', avatarUrl: null },
  { id: 'c1a00000-0000-4000-8000-000000000006', nom: 'Yann Kouassi', avatarUrl: null },
]

export function catalogueHackathons(maintenant = Date.now()) {
  const date = (jours: number) => new Date(maintenant + jours * 86_400_000).toISOString()
  return DEFIS_DEMO.map(([titre, theme, ville, lieu, statut, debut, duree, montant, devise, projet1, projet2], index) => {
    const enfant = (type: string, numero: number) => `${type}2026115-${String(index + 1).padStart(4, '0')}-4000-8000-${String(numero).padStart(12, '0')}`
    const id = `e2026115-0000-4000-8000-${String(index + 1).padStart(12, '0')}`
    const equipes: Equipe[] = [0, 1].map((i) => ({ id: enfant('c', i + 1), nom: i === 0 ? 'Les Baobabs' : 'Collectif Horizon', chef: MEMBRES[2 * i]!, membres: MEMBRES.slice(2 * i, 2 * i + 2), tailleMax: 4, complete: false, creeLe: date(Math.min(debut - 7, -3)) }))
    const inscriptions: Inscription[] = MEMBRES.map((personne, i) => ({ id: enfant('a', i + 1), personne, statut: 'acceptee', chercheEquipe: false, equipeId: equipes[Math.floor(i / 2)]!.id, competences: ['vue', 'ux-design'], reponses: [], creeLe: date(Math.min(debut - 7, -3)) }))
    const hackathon: Hackathon = {
      id, titre: `${titre} · Démo`, theme, lieu, ville, statut,
      description: `Événement fictif de démonstration. ${theme}. Les équipes construisent un prototype, le présentent et partagent leur code. Aucun événement réel ni inscription payante.`,
      organisateur: index === 0 ? AWA : NIA, nombreInscrits: inscriptions.length,
      debutLe: date(debut), finRendusLe: date(debut + duree),
      calendrier: { inscriptionsOuvertesLe: date(debut - 40), inscriptionsFermeesLe: date(debut - 1), debutLe: date(debut), finRendusLe: date(debut + duree), resultatsLe: date(debut + duree + 2) },
      tailleEquipe: { min: 2, max: 4 }, modeInscription: 'ouvert', deploiementObligatoire: false,
      codeDeConduite: 'Démo fictive : respect de chacun, aucun harcèlement, le code reste celui de chaque équipe.',
      questions: ['Quel problème concret résout votre projet ?'], questionnaire: [],
      criteres: [{ id: enfant('a', 10), nom: 'Utilité', poids: 50 }, { id: enfant('a', 11), nom: 'Qualité technique', poids: 30 }, { id: enfant('a', 12), nom: 'Présentation', poids: 20 }],
      prix: [{ id: enfant('b', 1), nom: 'Prix du jury · Démo', montant: { centimes: montant, devise }, sponsor: null }],
      creeLe: date(debut - 42), version: 1,
      moi: index === 0 ? { role: 'organisateur', actions: ['annuler', 'inviter_jure'] } : { role: 'visiteur', actions: statut === 'publie' ? ['sinscrire', 'creer_equipe'] : [] },
    }
    const rendus: Rendu[] = statut === 'publie' ? [] : equipes.map((equipe, i) => ({ id: enfant('d', i + 1), equipeId: equipe.id, titre: i === 0 ? projet1 : projet2, description: `Prototype fictif pour la démonstration de « ${titre} ». ${theme}.`, lienProduit: `https://hackathon-${index + 1}-projet-${i + 1}.demo.jamii.test`, depot: null, video: null, reponses: [theme], lienRepond: null, lienVerifieLe: null, version: 1, modifieLe: date(Math.min(debut + duree - 1, 0)) }))
    const resultats: Resultats | null = statut !== 'resultats' ? null : {
      publieLe: date(debut + duree + 2),
      gagnants: [{ prix: hackathon.prix[0]!, equipe: equipes[0]! }],
      classement: rendus.map((rendu, i) => ({ rang: i + 1, equipe: equipes[i]!, rendu, scorePondere: i === 0 ? 4.7 : 3.8, retourDuJury: ['Projet fictif : une idée claire et un prototype à poursuivre.'] })),
    }
    return { hackathon, equipes, inscriptions, rendus, resultats }
  })
}
