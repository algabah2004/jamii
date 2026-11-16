import { createI18n } from 'vue-i18n'
import fr from './fr.json'
import en from './en.json'

export type Langue = 'fr' | 'en'
export const LANGUES: Langue[] = ['fr', 'en']

export const CLE_LANGUE = 'jamii.langue'

/** La langue choisie avec le sélecteur, gardée dans ce navigateur. */
function langueRetenue(): string | null {
  try {
    return localStorage.getItem(CLE_LANGUE)
  } catch {
    return null
  }
}

/** Retient le choix de langue, pour la prochaine visite. */
export function retenirLangue(langue: Langue) {
  try {
    localStorage.setItem(CLE_LANGUE, langue)
  } catch {
    /* Mémoire du navigateur indisponible : la langue reviendra au français. */
  }
}

/** Français par défaut (docs/vocabulaire.md), quelle que soit la langue du navigateur ; l'anglais seulement si on l'a choisi. */
export function langueInitiale(retenue: string | null = langueRetenue()): Langue {
  return retenue === 'en' ? 'en' : 'fr'
}

export const i18n = createI18n<[typeof fr], Langue>({
  legacy: false,
  locale: langueInitiale(),
  fallbackLocale: 'fr',
  messages: { fr, en },
})
