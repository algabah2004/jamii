/*
 * Entrée du pré-rendu (scripts/prerendre.mjs, docs/adr/0011) : construite par Vite en mode SSR, exécutée par Node au
 * build. Elle rend l'accueil tel qu'un visiteur sans compte le voit en français, sans appeler l'API.
 */
import { createMemoryHistory } from 'vue-router'
import { renderToString } from 'vue/server-renderer'
import { creerApplication } from './application'
import { i18n } from './i18n'
import { supposerNonConnecte } from './session'

export async function rendreAccueil(): Promise<string> {
  supposerNonConnecte()
  // Node expose un `navigator` (en-US) : la langue du rendu est fixée, c'est celle que main.ts accepte d'hydrater.
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  const { app, routeur } = creerApplication({ hydratable: true, historique: createMemoryHistory() })
  // Une erreur pendant le rendu fait échouer le build, au lieu de publier un accueil à moitié dessiné.
  const erreurs: unknown[] = []
  app.config.errorHandler = (erreur) => void erreurs.push(erreur)
  await routeur.push('/')
  await routeur.isReady()
  const html = await renderToString(app)
  if (erreurs.length) throw new AggregateError(erreurs, `pré-rendu de l'accueil : ${erreurs.length} erreur(s)`)
  return html
}
