import { createI18n } from 'vue-i18n'
import fr from './fr.json'
import en from './en.json'

export type Langue = 'fr' | 'en'
export const LANGUES: Langue[] = ['fr', 'en']

export const i18n = createI18n<[typeof fr], Langue>({
  legacy: false,
  // Français à l'entrée, sur tout le site (docs/vocabulaire.md), quelle que soit la langue du navigateur.
  locale: 'fr',
  fallbackLocale: 'fr',
  messages: { fr, en },
})

/** Change la langue de toute l'interface, et l'attribut `lang` de la page pour les lecteurs d'écran. */
export function changerLangue(langue: Langue) {
  ;(i18n.global.locale as unknown as { value: Langue }).value = langue
  document.documentElement.lang = langue
}
