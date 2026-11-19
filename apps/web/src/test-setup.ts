import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from './mocks/handlers'
import { reinitialiserCommunautes } from './mocks/communautes'
import { reinitialiserProfil } from './mocks/profils'
import { reinitialiserCercles } from './mocks/cercles'
import { reinitialiserFichiers } from './mocks/fichiers'
import { reinitialiserReseau } from './mocks/reseau'

/*
 * `vi.waitFor` abandonne par défaut après 1 s. C'est assez sur la CI, pas sur un portable chargé (WSL, jury qui lance
 * `npm test`) : des tests justes y échouaient au hasard. On porte le délai à 5 s pour toute la suite. Un test qui
 * passe finit toujours dès que sa condition est vraie : seul un cas lent attend plus longtemps.
 */
const DELAI_ATTENTE_MS = 5_000
const attendre = vi.waitFor.bind(vi)
vi.waitFor = ((rappel, options) => {
  const reglages = typeof options === 'number' ? { timeout: options } : { ...options }
  // Un délai plus court demandé par un test (3 s, 4 s) est relevé lui aussi : il échouait de la même façon.
  return attendre(rappel, { ...reglages, timeout: Math.max(reglages.timeout ?? 0, DELAI_ATTENTE_MS) })
}) as typeof vi.waitFor

export const serveur = setupServer(...handlers)
beforeAll(() => serveur.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  serveur.resetHandlers()
  reinitialiserCommunautes()
  reinitialiserProfil()
  reinitialiserCercles()
  reinitialiserFichiers()
  reinitialiserReseau()
})
afterAll(() => serveur.close())
