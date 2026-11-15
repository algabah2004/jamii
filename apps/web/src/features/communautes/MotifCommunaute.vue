<script setup lang="ts">
import { computed, useId } from 'vue'
import { teinteDe } from './identite'

/*
 * Le motif d'une communauté : des traits de couleur (rayures, vagues, chevrons, arcs, traits croisés), jamais de
 * pointillés. Tiré de son nom court, il reste le même d'une visite à l'autre, et deux communautés voisines diffèrent.
 * Dessiné en SVG (aucune image à charger), il remplit son conteneur. Purement décoratif.
 */
const props = defineProps<{ slug: string }>()
const id = useId()

const FORMES = ['rayures', 'vagues', 'chevrons', 'arcs', 'croises'] as const
function empreinte(texte: string) {
  let h = 7
  for (const c of texte) h = (h * 33 + c.charCodeAt(0)) >>> 0
  return h
}
const forme = computed(() => FORMES[empreinte(props.slug) % FORMES.length]!)
const teinte = computed(() => teinteDe(props.slug))
/** Une troisième couleur de trait, pour que le motif ait de la profondeur. */
const troisieme = computed(() => ['#F2C14E', '#7CC8EE', '#FFFFFF', '#FC734D', '#0297DE'][empreinte(`${props.slug}!`) % 5]!)
</script>

<template>
  <svg class="motif-communaute" aria-hidden="true" focusable="false" preserveAspectRatio="xMidYMid slice">
    <defs>
      <pattern v-if="forme === 'rayures'" :id="id" width="36" height="36" patternUnits="userSpaceOnUse" patternTransform="rotate(-35)">
        <line x1="0" y1="0" x2="0" y2="36" :stroke="teinte.accent" stroke-width="10" />
        <line x1="17" y1="0" x2="17" y2="36" :stroke="troisieme" stroke-width="3" />
        <line x1="25" y1="0" x2="25" y2="36" :stroke="teinte.encre" stroke-opacity="0.35" stroke-width="2" />
      </pattern>
      <pattern v-else-if="forme === 'vagues'" :id="id" width="64" height="28" patternUnits="userSpaceOnUse">
        <path d="M0 9 Q 16 0 32 9 T 64 9" fill="none" :stroke="teinte.accent" stroke-width="5" stroke-linecap="round" />
        <path d="M0 22 Q 16 13 32 22 T 64 22" fill="none" :stroke="troisieme" stroke-width="2.5" stroke-linecap="round" />
      </pattern>
      <pattern v-else-if="forme === 'chevrons'" :id="id" width="40" height="30" patternUnits="userSpaceOnUse">
        <path d="M0 22 L20 6 L40 22" fill="none" :stroke="teinte.accent" stroke-width="6" stroke-linejoin="round" />
        <path d="M0 30 L20 14 L40 30" fill="none" :stroke="troisieme" stroke-width="2.5" stroke-linejoin="round" />
      </pattern>
      <pattern v-else-if="forme === 'arcs'" :id="id" width="48" height="48" patternUnits="userSpaceOnUse">
        <path d="M0 48 A48 48 0 0 1 48 0" fill="none" :stroke="teinte.accent" stroke-width="6" />
        <path d="M0 48 A32 32 0 0 1 32 16" fill="none" :stroke="troisieme" stroke-width="3" />
        <path d="M0 48 A16 16 0 0 1 16 32" fill="none" :stroke="teinte.encre" stroke-opacity="0.4" stroke-width="2.5" />
      </pattern>
      <pattern v-else :id="id" width="30" height="30" patternUnits="userSpaceOnUse" patternTransform="rotate(20)">
        <line x1="0" y1="0" x2="0" y2="30" :stroke="teinte.accent" stroke-width="5" />
        <line x1="0" y1="15" x2="30" y2="15" :stroke="troisieme" stroke-width="2.5" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" :fill="teinte.fond" />
    <rect width="100%" height="100%" :fill="`url(#${id})`" />
  </svg>
</template>

<style scoped>
.motif-communaute { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
</style>
