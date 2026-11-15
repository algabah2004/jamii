/*
 * L'identité visuelle d'une communauté, tirée de son nom court : toujours la même pour une communauté donnée, et des
 * communautés voisines différentes. Couleurs prises dans la charte (bleu électrique, cantaloup, minuit), et des
 * étiquettes qui parlent du sujet, pour la bannière. Purement décoratif : rien de tout ça n'est une donnée.
 */
export type Teinte = { fond: string; accent: string; encre: string }

const TEINTES: Teinte[] = [
  { fond: '#0297DE', accent: '#FC734D', encre: '#FFFFFF' },
  { fond: '#FC734D', accent: '#0297DE', encre: '#02070A' },
  { fond: '#02070A', accent: '#FC734D', encre: '#FFFFFF' },
  { fond: '#7CC8EE', accent: '#02070A', encre: '#02070A' },
  { fond: '#FFC2AE', accent: '#0297DE', encre: '#02070A' },
]

function empreinte(slug: string) {
  let h = 0
  for (const c of slug) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return h
}

export function teinteDe(slug: string): Teinte {
  return TEINTES[empreinte(slug) % TEINTES.length]!
}

/** Les étiquettes de la bannière, par thème ; une antenne parle de sa ville. */
const ETIQUETTES: Record<string, string[]> = {
  flutter: ['Hors ligne', 'Widgets', 'Dart', 'Android d’entrée de gamme'],
  fintech: ['Mobile Money', 'USSD', 'Paiements', 'Webhooks signés'],
  ia: ['Wolof', 'Swahili', 'Modèles ouverts', 'Données locales'],
  web: ['Sites rapides', 'API', '3G', 'Accessibilité'],
  cloud: ['Systalink', 'Docker', 'Mise en ligne', 'Sauvegardes'],
  antenne: ['Rencontres', 'Entraide', 'Projets locaux', 'Ateliers'],
  design: ['Interfaces simples', 'Tests utilisateurs', 'Icônes', 'Accessibilité'],
  data: ['Données ouvertes', 'Cartes', 'Nettoyage', 'Visualisation'],
  jeux: ['Godot', 'Contes', 'Game jam', 'Téléphones légers'],
  securite: ['Mots de passe', 'Arnaques', 'Sauvegardes', 'Bonnes pratiques'],
}

/** Les thèmes qu'on peut choisir en créant une communauté (contracts/communautes.yaml, NouvelleCommunaute). */
export const THEMES = ['web', 'flutter', 'fintech', 'ia', 'data', 'cloud', 'design', 'jeux', 'securite', 'antenne', 'autre'] as const
export type Theme = (typeof THEMES)[number]

/** Les pays proposés pour une antenne locale ; la liste s'allongera avec les antennes. */
export const PAYS_ANTENNES = ['SN', 'CI', 'ML', 'BF', 'GN', 'CM', 'NG', 'GH', 'KE', 'RW', 'TZ', 'ZA', 'MA', 'TN', 'BJ', 'TG', 'NE'] as const

export function etiquettesDe(theme: string): string[] {
  return ETIQUETTES[theme] ?? ['Entraide', 'Projets', 'Questions', 'Partage']
}
