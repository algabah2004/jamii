<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/**
 * Pendant un chargement : la forme de ce qui arrive (des lignes ou des cartes grisées), au lieu d'un
 * texte seul. Annoncé une fois aux lecteurs d'écran (role="status"). Sans animation si l'utilisateur
 * réduit le mouvement.
 */
withDefaults(defineProps<{ forme?: 'lignes' | 'cartes'; nombre?: number }>(), { forme: 'lignes', nombre: 3 })
const { t } = useI18n()
</script>

<template>
  <div class="j-squelette" :class="`j-squelette--${forme}`" role="status">
    <span class="visuellement-cache">{{ t('etat.chargement') }}</span>
    <div v-for="n in nombre" :key="n" class="j-squelette__bloc" aria-hidden="true">
      <span class="j-squelette__trait j-squelette__trait--court" />
      <span class="j-squelette__trait" />
      <span class="j-squelette__trait j-squelette__trait--moyen" />
    </div>
  </div>
</template>

<style>
.j-squelette { display: grid; gap: var(--space-3); }
.j-squelette--cartes { grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr)); gap: var(--space-4); }
.j-squelette__bloc {
  display: grid; gap: var(--space-3);
  padding: var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: var(--ombre-carte);
}
.j-squelette--cartes .j-squelette__bloc { min-height: 12rem; align-content: start; }
.j-squelette__trait {
  height: 0.9rem; border-radius: var(--radius-sm);
  background: var(--surface-raised);
  animation: j-squelette-pulse 1.4s var(--ease-out) infinite alternate;
}
.j-squelette__trait--court { width: 35%; }
.j-squelette__trait--moyen { width: 70%; }
@keyframes j-squelette-pulse { to { opacity: 0.45; } }
@media (prefers-reduced-motion: reduce) { .j-squelette__trait { animation: none; } }
</style>
