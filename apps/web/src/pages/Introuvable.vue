<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="carte">
    <h1>Page introuvable</h1>
    <p class="doux">Cette page n’existe pas ou plus.</p>
    <RouterLink to="/" class="bouton">Retour à l’accueil</RouterLink>
  </section>
</template>
