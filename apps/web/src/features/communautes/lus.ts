import { reactive } from 'vue'

/*
 * Ce que j'ai déjà lu dans chaque salon : le `numero` du dernier message vu, par « communauté/salon ».
 * Simple confort gardé dans ce navigateur (rien n'est envoyé au serveur) : sans stockage, tout paraît lu.
 */
const CLE = 'jamii.salons.lus'

function lire(): Record<string, number> {
  try {
    const brut = JSON.parse(localStorage.getItem(CLE) ?? '{}') as unknown
    return brut && typeof brut === 'object' ? (brut as Record<string, number>) : {}
  } catch {
    return {}
  }
}

export const derniersLus = reactive<Record<string, number>>(lire())

export const cleSalon = (communaute: string, salon: string) => `${communaute}/${salon}`

/** Retient le dernier message vu (jamais en arrière). */
export function marquerLu(communaute: string, salon: string, numero: number) {
  const cle = cleSalon(communaute, salon)
  if ((derniersLus[cle] ?? -1) >= numero) return
  derniersLus[cle] = numero
  try {
    localStorage.setItem(CLE, JSON.stringify(derniersLus))
  } catch {
    // Navigation privée ou stockage plein : le repère vaut pour cette visite.
  }
}

/** Un salon a du nouveau si son dernier message est plus récent que le dernier vu. Jamais vu : rien de nouveau. */
export function aDuNouveau(communaute: string, salon: string, dernier: number | undefined) {
  const vu = derniersLus[cleSalon(communaute, salon)]
  return dernier !== undefined && vu !== undefined && dernier > vu
}
