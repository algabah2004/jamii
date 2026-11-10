import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Accueil from './pages/Accueil.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'accueil', component: Accueil },
  { path: '/communautes', name: 'communautes', component: () => import('./pages/Communautes.vue') },
  { path: '/projets', name: 'projets', component: () => import('./pages/Projets.vue') },
  { path: '/demandes', name: 'demandes', component: () => import('./pages/Demandes.vue') },
  { path: '/connexion', name: 'connexion', component: () => import('./pages/Connexion.vue') },
  { path: '/profil', name: 'profil', component: () => import('./pages/Profil.vue') },
  { path: '/:chemin(.*)*', name: 'introuvable', component: () => import('./pages/Introuvable.vue') },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})
