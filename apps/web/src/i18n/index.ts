import { createI18n } from 'vue-i18n'
import fr from './fr.json'
import en from './en.json'

export type Langue = 'fr' | 'en'
export const LANGUES: Langue[] = ['fr', 'en']

export const CLE_LANGUE = 'jamii.langue'

function langueRetenue(): string | null {
  try {
    return localStorage.getItem(CLE_LANGUE)
  } catch {
    return null
  }
}

/**
 * La langue à l'ouverture : celle choisie avec le bouton FR/EN si on l'a déjà fait dans ce navigateur, sinon celle du
 * navigateur (anglais s'il préfère l'anglais, français pour toutes les autres langues : docs/vocabulaire.md).
 */
export function langueInitiale(
  retenue: string | null = langueRetenue(),
  navigateur: readonly string[] = typeof navigator === 'undefined' ? [] : (navigator.languages ?? [navigator.language]),
): Langue {
  if (retenue === 'fr' || retenue === 'en') return retenue
  return navigateur[0]?.toLowerCase().startsWith('en') ? 'en' : 'fr'
}

export const i18n = createI18n<[typeof fr], Langue>({
  legacy: false,
  locale: langueInitiale(),
  fallbackLocale: 'fr',
  messages: { fr, en },
})

// L'attribut `lang` de la page suit la langue d'ouverture (lecteurs d'écran, césure).
if (typeof document !== 'undefined') document.documentElement.lang = langueInitiale()

/** Change la langue de toute l'interface, et l'attribut `lang` de la page pour les lecteurs d'écran. */
export function changerLangue(langue: Langue) {
  ;(i18n.global.locale as unknown as { value: Langue }).value = langue
  document.documentElement.lang = langue
  try {
    localStorage.setItem(CLE_LANGUE, langue)
  } catch {
    /* Mémoire du navigateur indisponible : la prochaine visite suivra la langue du navigateur. */
  }
}
