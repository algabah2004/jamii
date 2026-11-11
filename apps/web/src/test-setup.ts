import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from './mocks/handlers'
import { reinitialiserCommunautes } from './mocks/communautes'
import { reinitialiserProfil } from './mocks/profils'

export const serveur = setupServer(...handlers)
beforeAll(() => serveur.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  serveur.resetHandlers()
  reinitialiserCommunautes()
  reinitialiserProfil()
})
afterAll(() => serveur.close())
