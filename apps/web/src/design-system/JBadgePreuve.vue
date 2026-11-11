<script setup lang="ts">
/**
 * Badge de preuve, l'orange de la charte : projet en ligne, tâche livrée, réponse acceptée.
 * Réservé à ce qui est vérifié. Une proposition de l'IA n'en a jamais (classe .proposition-ia).
 * `celebrer` le fait apparaître une fois avec un petit rebond, jamais en boucle (et sans animation si
 * l'utilisateur réduit le mouvement).
 */
defineProps<{ celebrer?: boolean }>()
</script>

<template>
  <span class="j-preuve" :class="{ 'j-preuve--celebrer': celebrer }"><slot /></span>
</template>

<style>
.j-preuve {
  display: inline-flex; align-items: center; gap: var(--space-1);
  padding: 2px var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--proof);
  color: var(--on-proof); /* 7,4:1 */
  font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight);
}
.j-preuve--celebrer { animation: j-preuve-apparait var(--motion-celebrate) var(--ease-pop) 1; }
@keyframes j-preuve-apparait {
  from { transform: scale(0.6); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
