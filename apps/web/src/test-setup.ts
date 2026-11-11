import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from './mocks/handlers'

export const serveur = setupServer(...handlers)
beforeAll(() => serveur.listen({ onUnhandledFrame: 'error' }))
afterEach(() => serveur.resetHandlers())
afterAll(() => serveur.close())
