// Le design system de Jamii (#19) : importé une fois dans main.ts, il habille toutes les pages.
// Les polices sont servies par notre serveur (aucune requête vers Google : pas d'adresse IP envoyée à un tiers).
import '@fontsource/noto-sans/400.css'
import '@fontsource/noto-sans/600.css'
import '@fontsource/familjen-grotesk/600.css'
import '@fontsource/familjen-grotesk/700.css'
import '@fontsource/instrument-serif/latin-400.css'
import '@fontsource/instrument-serif/latin-400-italic.css'
import { ref } from 'vue'
import './tokens.css'
import './base.css'
import './marques.css'

export { default as JAvatar } from './JAvatar.vue'
export { default as JBadgePreuve } from './JBadgePreuve.vue'
export { default as JBouton } from './JBouton.vue'
export { default as JChamp } from './JChamp.vue'
export { default as JDeplier } from './JDeplier.vue'
export { default as JFiltres } from './JFiltres.vue'
export { default as JIllustration } from './JIllustration.vue'
export { default as JSquelette } from './JSquelette.vue'

export type Theme = 'clair' | 'sombre' | 'systeme'
export type Marque = 'jamii' | 'partenaire'

const CLE_THEME = 'jamii.theme'

/** Le choix est posé sur <html data-theme> ; « systeme » suit le réglage de l'appareil. */
export function appliquerTheme(theme: Theme) {
  if (theme === 'systeme') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}

/** Le thème affiché, partagé par l'en-tête et la page de démonstration. */
export const themeActuel = ref<Theme>('systeme')

/** Applique le thème et le retient dans ce navigateur (simple confort : rien n'est envoyé au serveur). */
export function choisirTheme(theme: Theme) {
  themeActuel.value = theme
  appliquerTheme(theme)
  try {
    localStorage.setItem(CLE_THEME, theme)
  } catch {
    // Navigation privée ou stockage bloqué : le choix vaut pour cette visite seulement.
  }
}

export function themeRetenu(): Theme {
  try {
    const t = localStorage.getItem(CLE_THEME)
    return t === 'clair' || t === 'sombre' ? t : 'systeme'
  } catch {
    return 'systeme'
  }
}

/** La marque vient de la configuration du déploiement (VITE_MARQUE), jamais du code. */
export function appliquerMarque(marque: Marque = import.meta.env.VITE_MARQUE === 'partenaire' ? 'partenaire' : 'jamii') {
  if (marque === 'jamii') delete document.documentElement.dataset.marque
  else document.documentElement.dataset.marque = marque
}

/** Au démarrage : le thème choisi par la personne, la marque du déploiement. */
export function demarrerDesignSystem() {
  themeActuel.value = themeRetenu()
  appliquerTheme(themeActuel.value)
  appliquerMarque()
}
