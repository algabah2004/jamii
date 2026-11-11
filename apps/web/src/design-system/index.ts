// Le design system de Jamii (#19) : importé une fois dans main.ts, il habille toutes les pages.
// Les polices sont servies par notre serveur (aucune requête vers Google : pas d'adresse IP envoyée à un tiers).
import '@fontsource/noto-sans/400.css'
import '@fontsource/noto-sans/600.css'
import '@fontsource/familjen-grotesk/600.css'
import '@fontsource/familjen-grotesk/700.css'
import './tokens.css'
import './base.css'

export { default as JBadgePreuve } from './JBadgePreuve.vue'
export { default as JBouton } from './JBouton.vue'
export { default as JChamp } from './JChamp.vue'

export type Theme = 'clair' | 'sombre' | 'systeme'

/** Le choix est posé sur <html data-theme> ; « systeme » suit le réglage de l'appareil. */
export function appliquerTheme(theme: Theme) {
  if (theme === 'systeme') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}
