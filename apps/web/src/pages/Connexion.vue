<script setup lang="ts">
import { useRoute } from 'vue-router'
import { adresseConnexionGithub } from '../api/client'

const route = useRoute()
// Chemin relatif uniquement : l'API refuse tout autre retour (pas de redirection ouverte).
const retour = typeof route.query.retour === 'string' && route.query.retour.startsWith('/') ? route.query.retour : '/profil'
</script>

<template>
  <section class="carte connexion">
    <h1>Se connecter</h1>
    <p class="doux">Développeur ? Connecte-toi avec GitHub : ton profil se prépare à partir de tes projets publics.</p>
    <a :href="adresseConnexionGithub(retour)" class="bouton">Continuer avec GitHub</a>
    <p class="doux petit">
      Jamii ne lit que tes informations publiques et n’écrit jamais sur ton compte GitHub.
      Ton profil reste privé tant que tu ne le publies pas.
    </p>
    <hr />
    <p class="doux">PME, association, non-développeur ? La connexion par e-mail, sans mot de passe, arrive bientôt.</p>
  </section>
</template>

<style scoped>
.connexion { max-width: 460px; margin: 32px auto; }
.petit { font-size: 0.9rem; }
hr { border: 0; border-top: 1px solid var(--bordure); margin: 20px 0; }
</style>
