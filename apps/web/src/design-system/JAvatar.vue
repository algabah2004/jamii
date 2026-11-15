<script setup lang="ts">
import { computed } from 'vue'

/** Photo de la personne, ou ses initiales quand elle n'en a pas (comptes e-mail, GitHub sans photo). */
const props = withDefaults(defineProps<{ nom: string; url?: string | null; taille?: number }>(), {
  url: null,
  taille: 48,
})

/**
 * Sans photo, chaque personne a sa couleur, toujours la même (tirée de son nom) : on la reconnaît d'un coup d'œil
 * dans un fil ou un salon. Dix couleurs pleines, chacune avec le texte qui lui donne un contraste d'au moins 4,5:1,
 * en thème clair comme en sombre.
 */
const COULEURS = [
  { fond: '#0297DE', encre: '#02070A' },
  { fond: '#FC734D', encre: '#02070A' },
  { fond: '#F2C14E', encre: '#02070A' },
  { fond: '#7CC8EE', encre: '#02070A' },
  { fond: '#1F7A5C', encre: '#FFFFFF' },
  { fond: '#7A4FD1', encre: '#FFFFFF' },
  { fond: '#C2185B', encre: '#FFFFFF' },
  { fond: '#2E5E8C', encre: '#FFFFFF' },
  { fond: '#4E7D2B', encre: '#FFFFFF' },
  { fond: '#B4431F', encre: '#FFFFFF' },
] as const

const couleur = computed(() => {
  let h = 0
  for (const c of props.nom.trim().toLowerCase()) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return COULEURS[h % COULEURS.length]!
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
  <span v-else class="j-avatar j-avatar--initiales" :style="{ width: `${taille}px`, height: `${taille}px`, fontSize: `${taille * 0.38}px`, background: couleur.fond, color: couleur.encre }" aria-hidden="true">
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
