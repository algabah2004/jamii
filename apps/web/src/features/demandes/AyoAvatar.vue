<script setup lang="ts">
/*
 * Le logo d'Ayo, l'assistant IA de Jamii : une bulle de conversation à l'orange de Jamii, deux yeux et un sourire,
 * avec l'étincelle du badge « IA ». Dessin maison (aucune image externe), décoratif, lisible de 20 à 64 px.
 * Il cligne des yeux au repos ; quand il réfléchit, il lève le regard et l'étincelle s'anime. Tout s'arrête si la
 * personne a demandé moins de mouvement.
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
    <!-- La bulle : un carré très arrondi et sa pointe, en bas à gauche, comme quelqu'un qui parle. -->
    <path class="ayo__bulle" d="M24 5h16c10.5 0 19 8.5 19 19v9c0 10.5-8.5 19-19 19H25l-11.2 7.6c-1.2.8-2.7-.3-2.3-1.7l1.9-7.4C8.6 47.2 5 40.6 5 33v-9C5 13.5 13.5 5 24 5Z" />
    <g class="ayo__regard">
      <g class="ayo__yeux">
        <rect x="20.5" y="19" width="6.5" height="12" rx="3.25" class="ayo__oeil" />
        <rect x="37" y="19" width="6.5" height="12" rx="3.25" class="ayo__oeil" />
      </g>
      <path d="M26.5 38.5q5.5 4.5 11 0" class="ayo__sourire" />
    </g>
    <!-- L'étincelle du badge « IA ». -->
    <path class="ayo__etincelle" d="M53 1.5c.9 5.2 3.3 7.6 8.5 8.5-5.2.9-7.6 3.3-8.5 8.5-.9-5.2-3.3-7.6-8.5-8.5 5.2-.9 7.6-3.3 8.5-8.5Z" />
  </svg>
</template>

<style scoped>
.ayo { flex: none; overflow: visible; }
.ayo__bulle { fill: var(--cantaloupe, #fc734d); }
.ayo__oeil { fill: #02070a; }
.ayo__sourire { fill: none; stroke: #02070a; stroke-width: 3; stroke-linecap: round; }
.ayo__etincelle { fill: var(--action, #0297de); stroke: var(--bg, #fff); stroke-width: 2.5; paint-order: stroke; transform-origin: 53px 10px; }
.ayo__yeux { transform-origin: 32px 25px; animation: ayo-cligne 5s infinite; }
.ayo__regard { transition: transform 0.3s ease; }
.ayo--reflechit .ayo__regard { animation: ayo-regarde 2.4s ease-in-out infinite; }
.ayo--reflechit .ayo__etincelle { animation: ayo-etincelle 1.6s ease-in-out infinite; }
@keyframes ayo-cligne { 0%, 94%, 100% { transform: scaleY(1); } 97% { transform: scaleY(0.12); } }
@keyframes ayo-regarde { 0%, 100% { transform: translate(0, 0); } 30%, 60% { transform: translate(2.5px, -2.5px); } }
@keyframes ayo-etincelle { 0%, 100% { transform: scale(1) rotate(0deg); } 50% { transform: scale(1.25) rotate(45deg); } }
@media (prefers-reduced-motion: reduce) {
  .ayo__yeux, .ayo--reflechit .ayo__regard, .ayo--reflechit .ayo__etincelle { animation: none; }
}
</style>
