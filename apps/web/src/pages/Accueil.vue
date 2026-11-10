<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { moi } from '../session'

const espaces = [
  { titre: 'Se rencontrer', texte: 'Des développeurs de Dakar, Abidjan, Lagos, Nairobi ou Douala, et ce qu’ils savent vraiment faire.', lien: '/communautes' },
  { titre: 'Échanger et s’entraider', texte: 'Des communautés par thème, des questions et des réponses validées par les pairs.', lien: '/communautes' },
  { titre: 'Répondre à de vrais besoins', texte: 'Des PME et des associations décrivent leur besoin. Des équipes se forment pour y répondre.', lien: '/demandes' },
  { titre: 'Construire et mettre en ligne', texte: 'Un projet d’équipe, un plan proposé par l’assistant, puis la mise en ligne sur DataCloud.', lien: '/projets' },
]
</script>

<template>
  <section class="intro">
    <h1>Le lieu où les développeurs africains se rencontrent, échangent et construisent ensemble.</h1>
    <p class="doux">
      Trouve ton équipe. Construis un vrai projet. Mets-le en ligne. Et prouve-le.
      Ouvert à tous, gratuit, sans compte Systalink.
    </p>
    <div class="actions">
      <RouterLink v-if="!moi" to="/connexion" class="bouton">Rejoindre Jamii</RouterLink>
      <RouterLink v-else to="/profil" class="bouton">Voir mon profil</RouterLink>
      <RouterLink to="/communautes" class="bouton secondaire">Découvrir les communautés</RouterLink>
    </div>
  </section>
  <section class="grille" aria-label="Ce qu’on fait sur Jamii">
    <RouterLink v-for="e in espaces" :key="e.titre" :to="e.lien" class="carte espace">
      <h2>{{ e.titre }}</h2>
      <p class="doux">{{ e.texte }}</p>
    </RouterLink>
  </section>
</template>

<style scoped>
.intro { padding: 32px 0; }
.actions { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 20px; }
.espace { text-decoration: none; color: inherit; }
.espace h2 { font-size: 1.1rem; margin: 0 0 6px; }
.espace p { margin: 0; }
</style>
