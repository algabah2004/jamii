<script setup lang="ts">
/*
 * Le visage d'Ayo, le conseiller de Jamii : un dessin maison (aucune image externe), décoratif. Il cligne des yeux au
 * repos et lève le regard quand il réfléchit. Les animations s'arrêtent si la personne a demandé moins de mouvement.
 */
withDefaults(defineProps<{ taille?: number; reflechit?: boolean }>(), { taille: 48, reflechit: false })
</script>

<template>
  <svg
    class="ayo"
    :class="{ 'ayo--reflechit': reflechit }"
    :width="taille"
    :height="taille"
    viewBox="0 0 64 64"
    aria-hidden="true"
    focusable="false"
  >
    <circle cx="32" cy="32" r="30" class="ayo__fond" />
    <path d="M10 26 Q14 8 32 8 Q50 8 54 26 Q46 16 32 17 Q18 16 10 26 Z" class="ayo__cheveux" />
    <g class="ayo__yeux">
      <ellipse cx="23" cy="31" rx="3" ry="3.6" class="ayo__oeil" />
      <ellipse cx="41" cy="31" rx="3" ry="3.6" class="ayo__oeil" />
    </g>
    <circle cx="16" cy="39" r="3.2" class="ayo__joue" />
    <circle cx="48" cy="39" r="3.2" class="ayo__joue" />
    <path d="M24 42 Q32 49 40 42" class="ayo__sourire" />
    <g class="ayo__pensee">
      <circle cx="50" cy="12" r="2.4" />
      <circle cx="56" cy="6" r="1.6" />
    </g>
  </svg>
</template>

<style scoped>
.ayo { flex: none; overflow: visible; }
.ayo__fond { fill: var(--cantaloupe, #f6b26b); }
.ayo__cheveux { fill: var(--ink, #1d1d1f); }
.ayo__oeil { fill: var(--ink, #1d1d1f); }
.ayo__joue { fill: #fff; opacity: 0.35; }
.ayo__sourire { fill: none; stroke: var(--ink, #1d1d1f); stroke-width: 3; stroke-linecap: round; }
.ayo__pensee { fill: var(--action, #2f6fed); opacity: 0; }
.ayo__yeux { transform-origin: 32px 31px; animation: ayo-cligne 5s infinite; }
.ayo--reflechit .ayo__yeux { animation: ayo-regarde 2.4s ease-in-out infinite; }
.ayo--reflechit .ayo__pensee { animation: ayo-pense 1.6s ease-in-out infinite; }
@keyframes ayo-cligne { 0%, 94%, 100% { transform: scaleY(1); } 97% { transform: scaleY(0.1); } }
@keyframes ayo-regarde { 0%, 100% { transform: translate(0, 0); } 30%, 60% { transform: translate(2px, -3px); } }
@keyframes ayo-pense { 0%, 100% { opacity: 0.2; } 50% { opacity: 1; } }
@media (prefers-reduced-motion: reduce) {
  .ayo__yeux, .ayo--reflechit .ayo__yeux { animation: none; }
  .ayo--reflechit .ayo__pensee { animation: none; opacity: 1; }
}
</style>
