<script setup lang="ts">
/*
 * Les icônes du profil : le même dessin que celles du cercle (au trait, 1,6 px sur une grille de 20, couleur du
 * texte). Elles accompagnent toujours un libellé : décoratives pour les lecteurs d'écran.
 */
export type NomIconeProfil =
  | 'lieu' | 'horloge' | 'langue' | 'github' | 'linkedin' | 'site' | 'crayon' | 'partager' | 'oeil' | 'rafraichir'
  | 'cadenas' | 'monde' | 'plus' | 'fermer' | 'coche' | 'etincelle' | 'valise' | 'code' | 'fusee' | 'bouclier'
  | 'etoile' | 'personne' | 'lien' | 'telecharger' | 'copier' | 'fleche'

withDefaults(defineProps<{ nom: NomIconeProfil; taille?: number }>(), { taille: 18 })

const TRACES: Record<NomIconeProfil, string> = {
  lieu: 'M10 17s5.5-5 5.5-9.2A5.5 5.5 0 0 0 4.5 7.8C4.5 12 10 17 10 17zM10 9.8a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  horloge: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM10 6.5V10l2.5 1.5',
  langue: 'M3.5 5h8M7.5 3.5V5M5 5c.6 2.6 2.4 4.7 5 6M10 5c-.6 2.6-2.4 4.7-5 6M10.5 16.5l3-7 3 7M11.5 14.5h4',
  github: 'M7.5 16.5v-2.3c-2.6.6-3.2-1.2-3.2-1.2M12.5 16.5v-2.6c0-.8-.3-1.3-.7-1.6 2.3-.3 4.2-1.1 4.2-4.6 0-1-.3-1.8-.9-2.4.1-.3.4-1.2-.1-2.4 0 0-.8-.2-2.5.9a8.5 8.5 0 0 0-4.6 0C6.2 1.4 5.4 1.6 5.4 1.6c-.5 1.2-.2 2.1-.1 2.4-.6.6-.9 1.4-.9 2.4 0 3.5 1.9 4.3 4.2 4.6-.4.3-.6.8-.7 1.4',
  linkedin: 'M4 4h12v12H4zM7 9v4.5M7 6.6v.1M10 13.5V9M10 11c0-1.3.8-2 1.9-2s1.6.7 1.6 2v2.5',
  site: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM3.5 10h13M10 3.5c1.8 1.8 2.6 4 2.6 6.5S11.8 14.7 10 16.5M10 3.5C8.2 5.3 7.4 7.5 7.4 10s.8 4.7 2.6 6.5',
  crayon: 'M12.5 4.5l3 3L7.5 15.5H4.5v-3zM11 6l3 3',
  partager: 'M10 12.5V3.5M6.5 7 10 3.5 13.5 7M4.5 11v4.5h11V11',
  oeil: 'M2.5 10s2.7-5 7.5-5 7.5 5 7.5 5-2.7 5-7.5 5-7.5-5-7.5-5zM10 12.3a2.3 2.3 0 1 0 0-4.6 2.3 2.3 0 0 0 0 4.6z',
  rafraichir: 'M15.5 8.5A5.7 5.7 0 0 0 5 6.5M4.5 3.5v3h3M4.5 11.5A5.7 5.7 0 0 0 15 13.5M15.5 16.5v-3h-3',
  cadenas: 'M5.5 9h9v7.5h-9zM7.5 9V6.5a2.5 2.5 0 0 1 5 0V9',
  monde: 'M10 3.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM4 8h12M4 12h12M10 3.5c-2.4 3.9-2.4 9.1 0 13M10 3.5c2.4 3.9 2.4 9.1 0 13',
  plus: 'M10 4.5v11M4.5 10h11',
  fermer: 'M5.5 5.5l9 9M14.5 5.5l-9 9',
  coche: 'M5 10.5l3.2 3.2L15 6.5',
  etincelle: 'M10 3c.6 3.4 2.6 5.4 6 6-3.4.6-5.4 2.6-6 6-.6-3.4-2.6-5.4-6-6 3.4-.6 5.4-2.6 6-6zM15.5 2.5v3M14 4h3',
  valise: 'M3.5 7h13v8.5h-13zM7.5 7V5.5A1.5 1.5 0 0 1 9 4h2a1.5 1.5 0 0 1 1.5 1.5V7M3.5 11h13',
  code: 'M7 6.5 3.5 10 7 13.5M13 6.5l3.5 3.5-3.5 3.5M11 4.5l-2 11',
  fusee: 'M11.5 12.5 7.5 8.5c1.4-3.5 4-5.3 8-5.5-.2 4-2 6.6-5.5 8zM7.5 8.5 4.5 8 3 9.5l3.5 1M11.5 12.5l.5 3 1.5-1.5-.5-3.5M6 14c-1 .3-1.6 1-2 2.5 1.5-.4 2.2-1 2.5-2',
  bouclier: 'M10 3.5 15.5 5.5v4.2c0 3.3-2.3 5.9-5.5 6.8-3.2-.9-5.5-3.5-5.5-6.8V5.5zM7.5 10l1.7 1.7 3.3-3.4',
  etoile: 'M10 3.5l1.9 4 4.3.5-3.2 3 .9 4.3L10 13.1l-3.9 2.2.9-4.3-3.2-3 4.3-.5z',
  personne: 'M10 9.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM4.5 16.5c.7-3 2.8-4.7 5.5-4.7s4.8 1.7 5.5 4.7',
  lien: 'M8.5 11.5a3 3 0 0 0 4.3 0l2.2-2.2a3 3 0 0 0-4.3-4.3l-.9.9M11.5 8.5a3 3 0 0 0-4.3 0L5 10.7A3 3 0 0 0 9.3 15l.9-.9',
  telecharger: 'M10 3.5v9M6.5 9 10 12.5 13.5 9M4.5 15.5h11',
  copier: 'M7 7h8.5v8.5H7zM4.5 13V4.5H13',
  fleche: 'M5.5 14.5l9-9M7.5 5.5h7v7',
}
</script>

<template>
  <svg class="icone-profil" viewBox="0 0 20 20" :width="taille" :height="taille" aria-hidden="true" focusable="false">
    <path :d="TRACES[nom]" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>

<style scoped>
.icone-profil { flex: none; display: inline-block; vertical-align: -0.2em; }
</style>
