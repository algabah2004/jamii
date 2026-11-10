<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { apiInjoignable, chargee, moi } from './session'
</script>

<template>
  <header class="entete">
    <RouterLink to="/" class="marque">Jamii</RouterLink>
    <nav aria-label="Menu principal">
      <RouterLink to="/communautes">Communautés</RouterLink>
      <RouterLink to="/demandes">Demandes</RouterLink>
      <RouterLink to="/projets">Projets</RouterLink>
    </nav>
    <div class="compte">
      <template v-if="chargee">
        <RouterLink v-if="moi" to="/profil" class="moi">
          <img v-if="moi.avatarUrl" :src="moi.avatarUrl" alt="" width="28" height="28" />
          {{ moi.nom }}
        </RouterLink>
        <RouterLink v-else to="/connexion" class="bouton">Se connecter</RouterLink>
      </template>
    </div>
  </header>
  <p v-if="apiInjoignable" class="alerte" role="status">
    Le service est momentanément indisponible. Tu peux continuer à visiter, réessaie dans un instant.
  </p>
  <main>
    <RouterView />
  </main>
  <footer class="pied">
    Jamii · Coupe d'Afrique des Développeurs 2026 · Hébergé sur Systalink DataCloud, à Dakar
  </footer>
</template>
