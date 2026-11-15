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
}

export function etiquettesDe(theme: string): string[] {
  return ETIQUETTES[theme] ?? ['Entraide', 'Projets', 'Questions', 'Partage']
}
