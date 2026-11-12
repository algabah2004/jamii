<script setup lang="ts" generic="T extends string">
/**
 * Choix d'un filtre parmi quelques-uns (2 à 4) : des pastilles, une seule active à la fois.
 * Chaque pastille est un bouton à aria-pressed, dans un groupe nommé pour les lecteurs d'écran.
 */
defineProps<{ libelle: string; options: { valeur: T; texte: string }[] }>()
const valeur = defineModel<T>({ required: true })
</script>

<template>
  <div class="j-filtres filtres" role="group" :aria-label="libelle">
    <button
      v-for="o in options"
      :key="o.valeur"
      type="button"
      :aria-pressed="o.valeur === valeur"
      @click="valeur = o.valeur"
    >
      {{ o.texte }}
    </button>
  </div>
</template>

<style>
.j-filtres { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.j-filtres button {
  min-height: 36px;
  padding: 0 var(--space-4);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--ink);
  font-size: var(--type-label-size); font-weight: var(--type-label-weight);
  cursor: pointer;
}
.j-filtres button:hover { border-color: var(--action); }
.j-filtres button[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
</style>
