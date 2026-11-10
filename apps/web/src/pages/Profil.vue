<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { chargee, moi, seDeconnecter } from '../session'

const router = useRouter()
async function deconnexion() {
  await seDeconnecter()
  await router.push('/')
}
</script>

<template>
  <p v-if="!chargee" class="doux">Chargement…</p>
  <section v-else-if="!moi" class="carte">
    <h1>Ton profil</h1>
    <p>Connecte-toi pour voir ton profil.</p>
    <RouterLink :to="{ path: '/connexion', query: { retour: '/profil' } }" class="bouton">Se connecter</RouterLink>
  </section>
  <section v-else class="carte">
    <div class="identite">
      <img v-if="moi.avatarUrl" :src="moi.avatarUrl" alt="" width="72" height="72" />
      <div>
        <h1>{{ moi.nom }}</h1>
        <p v-if="moi.loginGithub" class="doux">@{{ moi.loginGithub }} sur GitHub</p>
      </div>
    </div>
    <p class="doux">
      Ton profil prouvé (langages, projets en ligne, contributions) se construit ici.
    </p>
    <button type="button" class="bouton secondaire" @click="deconnexion">Se déconnecter</button>
  </section>
</template>

<style scoped>
.identite { display: flex; gap: 16px; align-items: center; margin-bottom: 12px; }
.identite img { border-radius: 50%; }
.identite h1 { margin: 0; }
.identite p { margin: 0; }
</style>
