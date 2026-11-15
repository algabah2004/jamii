<script setup lang="ts">
import { computed, useId } from 'vue'
import { teinteDe } from './identite'

/*
 * Une texture discrète posée sur le dégradé d'une communauté : de fins traits d'une seule couleur, presque
 * transparents (rayures, vagues, chevrons ou arcs), jamais de pointillés. Tirée du nom court : toujours la même pour
 * une communauté. En SVG, sans image à charger. Purement décorative.
 */
const props = defineProps<{ slug: string }>()
const id = useId()

const FORMES = ['rayures', 'vagues', 'chevrons', 'arcs'] as const
const forme = computed(() => {
  let h = 7
  for (const c of props.slug) h = (h * 33 + c.charCodeAt(0)) >>> 0
  return FORMES[h % FORMES.length]!
})
const trait = computed(() => teinteDe(props.slug).encre)
</script>

<template>
  <svg class="texture" aria-hidden="true" focusable="false">
    <defs>
      <pattern v-if="forme === 'rayures'" :id="id" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(-35)">
        <line x1="0" y1="0" x2="0" y2="14" :stroke="trait" stroke-width="1.2" />
      </pattern>
      <pattern v-else-if="forme === 'vagues'" :id="id" width="48" height="16" patternUnits="userSpaceOnUse">
        <path d="M0 8 Q 12 2 24 8 T 48 8" fill="none" :stroke="trait" stroke-width="1.2" />
      </pattern>
      <pattern v-else-if="forme === 'chevrons'" :id="id" width="28" height="16" patternUnits="userSpaceOnUse">
        <path d="M0 12 L14 4 L28 12" fill="none" :stroke="trait" stroke-width="1.2" stroke-linejoin="round" />
      </pattern>
      <pattern v-else :id="id" width="40" height="40" patternUnits="userSpaceOnUse">
        <path d="M0 40 A40 40 0 0 1 40 0 M0 40 A24 24 0 0 1 24 16" fill="none" :stroke="trait" stroke-width="1.2" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" :fill="`url(#${id})`" />
  </svg>
</template>

<style scoped>
/* Assez visible pour donner du relief, assez léger pour laisser le dégradé et le texte au premier plan. */
.texture { position: absolute; inset: 0; width: 100%; height: 100%; display: block; opacity: 0.16; pointer-events: none; }
</style>
