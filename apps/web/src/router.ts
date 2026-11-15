import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSession } from './session'
import AccueilPage from './pages/AccueilPage.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'accueil', component: AccueilPage },
  // Se connecter avec GitHub ou par un lien e-mail (contracts/socle.yaml). L'API renvoie aussi ici en cas d'échec.
  { path: '/connexion', name: 'connexion', component: () => import('./features/connexion/ConnexionPage.vue') },
  { path: '/connexion/email', name: 'connexion-email', component: () => import('./features/connexion/ConfirmationEmailPage.vue') },
  {
    path: '/profil',
    name: 'profil',
    component: () => import('./features/profils/MonProfilPage.vue'),
    meta: { connexionRequise: true },
  },
  // Le profil publié de quelqu'un : la page du lien partagé et du QR code, lisible sans compte (#159).
  { path: '/p/:id', name: 'profil-public', component: () => import('./features/profils/ProfilPublicPage.vue') },
  // Communautés : connexion requise (contracts/communautes.yaml).
  {
    path: '/communautes',
    name: 'communautes',
    component: () => import('./features/communautes/CommunautesPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  // Créer une communauté, et un salon dans une communauté (#155) : des écrans dédiés.
  {
    path: '/communautes/nouvelle',
    name: 'nouvelle-communaute',
    component: () => import('./features/communautes/NouvelleCommunautePage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/communautes/:slug/salons/nouveau',
    name: 'nouveau-salon',
    component: () => import('./features/communautes/NouveauSalonPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/communautes/:slug',
    name: 'communaute',
    component: () => import('./features/communautes/CommunautePage.vue'),
    // L'espace d'une communauté remplit l'écran (#155).
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/publications/:id',
    name: 'publication',
    component: () => import('./features/communautes/PublicationPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  // La Tech Tontine (docs/priorites.md) : les cercles et leurs tours. Connexion requise.
  {
    path: '/cercles',
    name: 'cercles',
    component: () => import('./features/cercles/CerclesPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/cercles/nouveau',
    name: 'nouveau-cercle',
    component: () => import('./features/cercles/NouveauCerclePage.vue'),
    meta: { connexionRequise: true },
  },
  {
    path: '/cercles/:id',
    name: 'cercle',
    component: () => import('./features/cercles/CerclePage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  // Le réseau (contracts/reseau.yaml) : écrire à un développeur, recevoir une invitation à un cercle.
  {
    path: '/messages',
    name: 'messages',
    component: () => import('./features/reseau/MessagesPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true, messagerie: true },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('./features/reseau/NotificationsPage.vue'),
    meta: { connexionRequise: true },
  },
  {
    path: '/contacts',
    name: 'contacts',
    component: () => import('./features/reseau/ContactsPage.vue'),
    meta: { connexionRequise: true },
  },
  {
    path: '/messages/:personneId',
    name: 'conversation',
    component: () => import('./features/reseau/MessagesPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true, messagerie: true },
  },
  // Une personne ou une PME décrit son besoin, Jamii l'oriente (étape 1 de la démo). Connexion requise
  // (GitHub ou e-mail : une PME n'a pas besoin de compte GitHub). `/besoin` est l'adresse à donner.
  { path: '/besoin', redirect: '/demandes/nouvelle' },
  // « Tes demandes » (#199) : tous ses besoins, comme sur un profil. C'est ce qu'ouvre le menu.
  {
    path: '/demandes',
    name: 'mes-demandes',
    component: () => import('./features/demandes/MesDemandesPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/demandes/nouvelle',
    name: 'nouvelle-demande',
    component: () => import('./features/demandes/NouvelleDemandePage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  {
    path: '/demandes/:id',
    name: 'demande',
    component: () => import('./features/demandes/DemandePage.vue'),
    meta: { connexionRequise: true },
  },
  // Missions et hackathons (#166, #191) : la liste et le détail sont publics (le serveur décide de ce que chacun
  // voit) ; publier une mission ou créer un hackathon demande une connexion. `nouvelle`/`nouveau` avant `:id`.
  { path: '/missions', name: 'missions', component: () => import('./features/missions/MissionsPage.vue'), meta: { pleineLargeur: true } },
  {
    path: '/missions/nouvelle',
    name: 'nouvelle-mission',
    component: () => import('./features/missions/NouvelleMissionPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  { path: '/missions/:id', name: 'mission', component: () => import('./features/missions/MissionPage.vue') },
  { path: '/hackathons', name: 'hackathons', component: () => import('./features/hackathons/HackathonsPage.vue'), meta: { pleineLargeur: true } },
  {
    path: '/hackathons/nouveau',
    name: 'nouveau-hackathon',
    component: () => import('./features/hackathons/NouveauHackathonPage.vue'),
    meta: { connexionRequise: true, pleineLargeur: true },
  },
  { path: '/hackathons/:id', name: 'hackathon', component: () => import('./features/hackathons/HackathonPage.vue') },
  {
    // Public : seulement ce que chaque projet montre déjà à tous (contracts/vitrine.yaml).
    path: '/vitrine',
    name: 'vitrine',
    component: () => import('./features/vitrine/VitrinePage.vue'),
  },
  {
    // Réservé à l'équipe Systalink (contracts/systalink.yaml v0.3) : la page dit « réservé » aux autres.
    path: '/systalink',
    name: 'systalink',
    meta: { connexionRequise: true },
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
      return { name: 'connexion', query: { retour: vers.fullPath } }
    }
  })
  return routeur
}
