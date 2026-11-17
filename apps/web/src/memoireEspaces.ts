import type { RouteLocationNormalized, Router } from 'vue-router'

/*
 * Rester dans son espace : quand on est entré dans un cercle (ou une communauté, un hackathon), qu'on va ailleurs puis
 * qu'on revient par la barre, on retombe là où on était, sous-page comprise. La liste reste à un clic depuis
 * l'intérieur : un passage par la liste efface le souvenir. Mémoire par onglet (sessionStorage), rien côté serveur.
 */
export const ESPACES = [
  { liste: 'cercles', interieur: 'cercle', prefixe: '/cercles' },
  { liste: 'communautes', interieur: 'communaute', prefixe: '/communautes' },
  { liste: 'hackathons', interieur: 'hackathon', prefixe: '/hackathons' },
] as const

const cle = (liste: string) => `jamii.espace.${liste}`

function lire(liste: string): string | null {
  try {
    return sessionStorage.getItem(cle(liste))
  } catch {
    return null
  }
}
function ecrire(liste: string, chemin: string | null) {
  try {
    if (chemin) sessionStorage.setItem(cle(liste), chemin)
    else sessionStorage.removeItem(cle(liste))
  } catch {
    // Stockage indisponible (navigation privée) : on retombe simplement sur la liste.
  }
}

/** Où envoyer quelqu'un qui arrive sur la liste d'un espace : l'intérieur retenu, s'il vient d'ailleurs. */
export function destinationRetenue(vers: RouteLocationNormalized, depuis: RouteLocationNormalized): string | null {
  const espace = ESPACES.find((e) => e.liste === vers.name)
  if (!espace) return null
  // Première page de l'onglet, ou un passage à l'intérieur du même espace (« Tous les cercles ») : la liste.
  if (!depuis.matched.length || depuis.path.startsWith(espace.prefixe)) return null
  // Une liste filtrée ou demandée explicitement reste une liste.
  if (Object.keys(vers.query).length) return null
  const chemin = lire(espace.liste)
  return chemin && chemin !== vers.fullPath ? chemin : null
}

/** Retient la dernière page de chaque espace ; un passage par la liste l'oublie. */
export function retenir(vers: RouteLocationNormalized) {
  for (const e of ESPACES) {
    if (vers.name === e.interieur) ecrire(e.liste, vers.fullPath)
    else if (vers.name === e.liste) ecrire(e.liste, null)
  }
}

export function installerMemoireEspaces(routeur: Router) {
  routeur.beforeEach((vers, depuis) => destinationRetenue(vers, depuis) ?? undefined)
  routeur.afterEach((vers, _depuis, echec) => {
    if (!echec) retenir(vers)
  })
}
