import { createApp, createSSRApp, type App as AppVue } from 'vue'
import type { Router, RouterHistory } from 'vue-router'
import App from './App.vue'
import { i18n } from './i18n'
import { creerRouteur } from './router'

/**
 * L'application, montée par le navigateur (main.ts) et rendue en HTML au build pour l'accueil (entree-prerendu.ts) :
 * une seule fabrique, pour que les deux rendus produisent le même arbre et que l'hydratation retombe juste.
 * `hydratable` : l'application reprend un HTML déjà présent dans la page au lieu de le redessiner (docs/adr/0011).
 */
export function creerApplication(options: { hydratable: boolean; historique?: RouterHistory }): { app: AppVue; routeur: Router } {
  const app = options.hydratable ? createSSRApp(App) : createApp(App)
  const routeur = creerRouteur(options.historique)
  app.use(i18n).use(routeur)
  return { app, routeur }
}

/** Marque posée par le pré-rendu sur le conteneur de l'accueil (scripts/prerendre.mjs). */
export const ATTRIBUT_PRERENDU = 'data-prerendu'

/**
 * Le HTML pré-rendu représente l'accueil d'un visiteur sans compte, en français. On le reprend (hydratation) seulement
 * dans ce cas exact ; sinon (connecté, autre langue, autre page) on dessine la page à neuf, sans erreur d'hydratation.
 */
export function peutHydrater(contexte: { prerendu: boolean; chemin: string; etat: string; langue: string }): boolean {
  return contexte.prerendu && contexte.chemin === '/' && contexte.etat === 'non_connecte' && contexte.langue === 'fr'
}
