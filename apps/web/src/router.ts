import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSession } from './session'
import AccueilPage from './pages/AccueilPage.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'accueil', component: AccueilPage },
  {
    path: '/profil',
    name: 'profil',
    component: () => import('./pages/ProfilPage.vue'),
    meta: { connexionRequise: true },
  },
  { path: '/:chemin(.*)*', name: 'introuvable', component: () => import('./pages/IntrouvablePage.vue') },
]

export function creerRouteur(historique = createWebHistory()) {
  const routeur = createRouter({ history: historique, routes })
  // Le serveur vérifie les droits ; ce garde évite seulement d'afficher un écran vide.
  routeur.beforeEach((vers) => {
    const { etat } = useSession()
    if (vers.meta.connexionRequise && etat.value === 'non_connecte') {
      return { name: 'accueil', query: { retour: vers.fullPath } }
    }
  })
  return routeur
}
