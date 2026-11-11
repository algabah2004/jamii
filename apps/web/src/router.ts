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
  // Communautés : connexion requise (contracts/communautes.yaml).
  {
    path: '/communautes',
    name: 'communautes',
    component: () => import('./features/communautes/CommunautesPage.vue'),
    meta: { connexionRequise: true },
  },
  {
    path: '/communautes/:slug',
    name: 'communaute',
    component: () => import('./features/communautes/CommunautePage.vue'),
    meta: { connexionRequise: true },
  },
  {
    path: '/publications/:id',
    name: 'publication',
    component: () => import('./features/communautes/PublicationPage.vue'),
    meta: { connexionRequise: true },
  },
  {
    // Public : seulement ce que chaque projet montre déjà à tous (contracts/vitrine.yaml).
    path: '/vitrine',
    name: 'vitrine',
    component: () => import('./features/vitrine/VitrinePage.vue'),
  },
  {
    // Public : uniquement des chiffres agrégés (contracts/systalink.yaml).
    path: '/systalink',
    name: 'systalink',
    component: () => import('./features/systalink/TableauDeBordPage.vue'),
  },
  {
    // Public : la charte et ses composants, pour l'équipe et le jury (#19).
    path: '/design-system',
    name: 'design-system',
    component: () => import('./design-system/DemonstrationPage.vue'),
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
