<script setup lang="ts">
import { computed } from 'vue'

/** Une répartition par pays en barres. La barre la plus longue est le plus grand pays ; « Autres pays » est neutre. */
const props = defineProps<{ lignes: { nom: string; nombre: number; regroupe: boolean }[] }>()

const max = computed(() => Math.max(1, ...props.lignes.map((l) => l.nombre)))
</script>

<template>
  <ul class="pays">
    <li v-for="l in lignes" :key="l.nom" :class="{ regroupe: l.regroupe }">
      <span>{{ l.nom }}</span>
      <span class="jauge" aria-hidden="true"><span :style="{ width: `${(l.nombre / max) * 100}%` }" /></span>
      <span class="nombre">{{ l.nombre }}</span>
    </li>
  </ul>
</template>

<style scoped>
.pays { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-3); }
.pays li { display: grid; grid-template-columns: minmax(6rem, 9rem) 1fr 2.5rem; align-items: center; gap: var(--space-3); font-variant-numeric: tabular-nums; }
.nombre { text-align: end; }
.jauge { display: block; height: 8px; background: var(--surface-raised); border-radius: var(--radius-pill); overflow: hidden; }
.jauge > span { display: block; height: 100%; background: var(--action); border-radius: var(--radius-pill); }
/* « Autres pays » est un regroupement, pas un pays : barre neutre. */
.regroupe .jauge > span { background: var(--line-strong); }
</style>
