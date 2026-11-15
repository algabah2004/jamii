import { api } from '../../api/client'
import type { Tache } from './cercles'

/*
 * Ayo propose une répartition des tâches libres d'un tour. Règle du projet : l'IA pour comprendre, des règles pour
 * décider (docs/priorites.md). Les compétences viennent du profil (déjà prouvées par GitHub ou la carte d'Ayo) ; la
 * répartition est un calcul : gratuit, instantané, explicable et testable. Rien n'est attribué sans un clic d'un membre.
 */

export interface MembreCompetent {
  id: string
  nom: string
  competences: string[]
}

export interface Suggestion {
  tache: Tache
  membre: MembreCompetent
  /** Les compétences du membre qui correspondent à la tâche ; vide si le choix vient seulement de la charge. */
  competences: string[]
  /** Les tâches qu'il porte déjà, en comptant celles que cette répartition vient de lui proposer. */
  charge: number
}

const sansAccents = (texte: string) => texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
const mots = (texte: string) => new Set(sansAccents(texte).split(/[^a-z0-9+#.]+/).filter((m) => m.length >= 2))

/*
 * Les mots de tous les jours d'une tâche, reliés aux compétences qui la font. Une tâche s'écrit « l'écran de
 * paiement », pas « vue + paiement-mobile ».
 */
const FAMILLES: { mots: string[]; competences: string[] }[] = [
  { mots: ['ecran', 'ecrans', 'page', 'pages', 'interface', 'front', 'formulaire', 'bouton', 'site', 'accueil'], competences: ['react', 'vue', 'vue.js', 'angular', 'svelte', 'javascript', 'typescript', 'html', 'css', 'flutter', 'next.js', 'nuxt'] },
  { mots: ['maquette', 'maquettes', 'design', 'logo', 'charte', 'couleurs', 'ux', 'ui'], competences: ['ux-design', 'design', 'figma', 'ui-design', 'css'] },
  { mots: ['api', 'serveur', 'backend', 'route', 'routes', 'connexion', 'authentification', 'compte', 'comptes'], competences: ['java', 'spring', 'spring-boot', 'node', 'nodejs', 'node.js', 'express', 'python', 'django', 'fastapi', 'php', 'laravel', 'go', 'kotlin'] },
  { mots: ['base', 'donnees', 'tables', 'table', 'sql', 'stockage', 'sauvegarde'], competences: ['postgresql', 'mysql', 'sql', 'mongodb', 'sqlite', 'supabase', 'firebase'] },
  { mots: ['paiement', 'paiements', 'payer', 'money', 'wave', 'orange', 'mpesa', 'caisse'], competences: ['paiement-mobile', 'mobile-money', 'stripe', 'paydunya', 'cinetpay'] },
  { mots: ['mobile', 'android', 'ios', 'application', 'appli', 'app'], competences: ['flutter', 'dart', 'react-native', 'kotlin', 'swift', 'android'] },
  { mots: ['deploiement', 'deployer', 'ligne', 'docker', 'serveur', 'datacloud', 'hebergement', 'domaine'], competences: ['docker', 'devops', 'kubernetes', 'linux', 'ci-cd', 'nginx'] },
  { mots: ['test', 'tests', 'recette', 'bug', 'bugs'], competences: ['tests', 'playwright', 'cypress', 'jest', 'vitest', 'junit', 'qa'] },
  { mots: ['ia', 'intelligence', 'chatbot', 'assistant', 'agent'], competences: ['python', 'ia', 'machine-learning', 'llm', 'openai', 'yokk'] },
  { mots: ['whatsapp', 'sms', 'notification', 'notifications', 'email', 'mail'], competences: ['whatsapp-api', 'integration-whatsapp', 'twilio', 'brevo', 'node', 'python'] },
]

/** Les compétences du membre qui servent cette tâche, dans l'ordre de son profil. */
export function competencesUtiles(titre: string, competences: string[]): string[] {
  const dansLeTitre = mots(titre)
  const parFamille = new Set(FAMILLES.filter((f) => f.mots.some((m) => dansLeTitre.has(m))).flatMap((f) => f.competences))
  return competences.filter((c) => {
    const nom = sansAccents(c)
    // La compétence écrite telle quelle dans la tâche (« React », « Flutter », « paiement mobile »)…
    if (dansLeTitre.has(nom) || nom.split('-').every((morceau) => morceau.length >= 2 && dansLeTitre.has(morceau))) return true
    // … ou reliée par une famille de mots de tous les jours.
    return parFamille.has(nom)
  })
}

/**
 * Pour chaque tâche « à faire » sans personne, le membre le plus adapté : d'abord celui dont les compétences
 * correspondent, puis le moins chargé, pour que le travail se partage. Le bénéficiaire du tour n'est jamais choisi :
 * c'est son projet que le cercle cotise.
 */
export function proposerRepartition(taches: Tache[], membres: MembreCompetent[], beneficiaireId: string | null): Suggestion[] {
  const equipe = membres.filter((m) => m.id !== beneficiaireId)
  if (!equipe.length) return []
  const charge = new Map(equipe.map((m) => [m.id, 0]))
  for (const t of taches) if (t.assignee && t.statut !== 'livree' && charge.has(t.assignee.id)) charge.set(t.assignee.id, charge.get(t.assignee.id)! + 1)

  const libres = taches.filter((t) => t.statut === 'a_faire' && !t.assignee).sort((a, b) => a.creeLe.localeCompare(b.creeLe))
  return libres.map((tache) => {
    const candidats = equipe
      .map((m) => ({ membre: m, competences: competencesUtiles(tache.titre, m.competences), charge: charge.get(m.id)! }))
      // Une compétence qui correspond compte plus qu'une tâche de moins ; à égalité, le moins chargé, puis l'ordre alphabétique.
      .sort((a, b) => b.competences.length * 2 - b.charge - (a.competences.length * 2 - a.charge) || a.charge - b.charge || a.membre.nom.localeCompare(b.membre.nom))
    const choisi = candidats[0]!
    charge.set(choisi.membre.id, choisi.charge + 1)
    return { tache, membre: choisi.membre, competences: choisi.competences.slice(0, 3), charge: choisi.charge }
  })
}

/** Les compétences publiées de chaque membre. Un profil non publié ou illisible compte comme « sans compétence ». */
export async function lireCompetences(membres: { id: string; nom: string }[]): Promise<MembreCompetent[]> {
  return Promise.all(
    membres.map(async (m) => {
      try {
        const { data } = await api.GET('/api/profils/{utilisateurId}', { params: { path: { utilisateurId: m.id } } })
        return { id: m.id, nom: m.nom, competences: [...new Set((data?.competences ?? []).map((c) => c.competence))] }
      } catch {
        return { id: m.id, nom: m.nom, competences: [] }
      }
    }),
  )
}
