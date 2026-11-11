<script setup lang="ts">
import { computed } from 'vue'

/** Photo de la personne, ou ses initiales quand elle n'en a pas (comptes e-mail, GitHub sans photo). */
const props = withDefaults(defineProps<{ nom: string; url?: string | null; taille?: number }>(), {
  url: null,
  taille: 48,
})

const initiales = computed(() =>
  props.nom
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((mot) => mot[0]!.toUpperCase())
    .join(''),
)
</script>

<template>
  <img v-if="url" class="j-avatar" :src="url" alt="" :width="taille" :height="taille" />
  <span v-else class="j-avatar j-avatar--initiales" :style="{ width: `${taille}px`, height: `${taille}px`, fontSize: `${taille * 0.38}px` }" aria-hidden="true">
    {{ initiales }}
  </span>
</template>

<style>
.j-avatar { border-radius: 50%; flex: none; object-fit: cover; }
.j-avatar--initiales {
  display: inline-grid; place-items: center;
  background: var(--action-soft); color: var(--ink);
  font-family: var(--font-display); font-weight: 700;
}
</style>
