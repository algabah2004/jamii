<script setup lang="ts">
import IconeCercle, { type NomIcone } from './IconeCercle.vue'

/** L'en-tête de chaque outil du cercle : la même forme partout (icône, titre, une ligne qui dit à quoi il sert). */
defineProps<{ icone: NomIcone; titre: string; aide: string; id: string }>()
</script>

<template>
  <header class="en-tete-outil">
    <span class="en-tete-outil__icone"><IconeCercle :nom="icone" :taille="20" /></span>
    <div>
      <h2 :id="id">{{ titre }}</h2>
      <p>{{ aide }}</p>
    </div>
  </header>
</template>

<style scoped>
.en-tete-outil { display: flex; gap: var(--space-3); align-items: flex-start; }
.en-tete-outil__icone { display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: var(--radius-md); border: 1px solid var(--line); color: var(--ink); }
.en-tete-outil h2 { margin: 0; font-size: 1.375rem; line-height: 1.2; }
.en-tete-outil p { margin: var(--space-1) 0 0; color: var(--ink-muted); max-width: 44rem; }
</style>
