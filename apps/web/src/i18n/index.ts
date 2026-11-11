import { createI18n } from 'vue-i18n'
import fr from './fr.json'
import en from './en.json'

export type Langue = 'fr' | 'en'
export const LANGUES: Langue[] = ['fr', 'en']

/** Français d'abord (docs/vocabulaire.md), anglais si le navigateur le préfère. */
export function langueInitiale(preferees: readonly string[] = navigator.languages ?? []): Langue {
  const premiere = preferees[0]?.slice(0, 2)
  return premiere === 'en' ? 'en' : 'fr'
}

export const i18n = createI18n<[typeof fr], Langue>({
  legacy: false,
  locale: langueInitiale(),
  fallbackLocale: 'fr',
  messages: { fr, en },
})
