<script setup lang="ts">
/*
 * Les icônes du cercle : un seul jeu, au trait (1,6 px sur une grille de 20), dans la couleur du texte. Elles
 * accompagnent toujours un libellé : décoratives pour les lecteurs d'écran.
 */
export type NomIcone =
  | 'qg' | 'salon' | 'taches' | 'points' | 'decisions' | 'calendrier' | 'cercle'
  | 'creation' | 'arrivee' | 'tirage' | 'depart' | 'livre' | 'fin' | 'coche' | 'appel' | 'priorite' | 'fermer' | 'plus'

withDefaults(defineProps<{ nom: NomIcone; taille?: number }>(), { taille: 18 })

const TRACES: Record<NomIcone, string> = {
  qg: 'M3.5 9 10 3.5 16.5 9v7a1 1 0 0 1-1 1h-3.5v-5h-4v5H4.5a1 1 0 0 1-1-1z',
  salon: 'M4 5.5A1.5 1.5 0 0 1 5.5 4h9A1.5 1.5 0 0 1 16 5.5v6a1.5 1.5 0 0 1-1.5 1.5H9l-3.5 3v-3h0A1.5 1.5 0 0 1 4 11.5z',
  taches: 'M4 4.5h12v11H4zM7 10l2 2 4-4.5',
  points: 'M15.5 12.5A6 6 0 0 1 7.5 4.5a6 6 0 1 0 8 8z',
  decisions: 'M7.5 3.5h5l-1 4 3 3h-9l3-3zM10 10.5V17',
  calendrier: 'M4 5.5h12v10.5H4zM4 9h12M7.5 3.5v3M12.5 3.5v3',
  cercle: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM10 3.5v2.5M16.5 10H14M10 16.5V14M3.5 10H6',
  creation: 'M10 3v4M10 13v4M3 10h4M13 10h4M5.5 5.5l2 2M12.5 12.5l2 2M14.5 5.5l-2 2M7.5 12.5l-2 2',
  arrivee: 'M8 9.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 16.5c.6-2.8 2.6-4.5 5-4.5s4.4 1.7 5 4.5M15 6.5v5M12.5 9h5',
  tirage: 'M4 6h3l6 8h3M4 14h3l1.5-2M11.5 8 13 6h3M14.5 4.5 16 6l-1.5 1.5M14.5 12.5 16 14l-1.5 1.5',
  depart: 'M5 17V3.5M5 4h9l-2 3 2 3H5',
  livre: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM7 10l2 2 4-4.5',
  fin: 'M5 17V3.5M5 4h10v6H5M8.5 4v6M12 4v6M5 7h10',
  coche: 'M5 10.5l3.2 3.2L15 6.5',
  priorite: 'M10 16V4.5M5.5 9 10 4.5 14.5 9',
  fermer: 'M5.5 5.5l9 9M14.5 5.5l-9 9',
  plus: 'M10 4.5v11M4.5 10h11',
  appel: 'M3 6.5A1.5 1.5 0 0 1 4.5 5h7A1.5 1.5 0 0 1 13 6.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 3 13.5zM13 9l4-2.5v7L13 11',
}
</script>

<template>
  <svg class="icone-cercle" viewBox="0 0 20 20" :width="taille" :height="taille" aria-hidden="true" focusable="false">
    <path :d="TRACES[nom]" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>

<style scoped>
.icone-cercle { flex: none; display: inline-block; vertical-align: -0.2em; }
</style>
