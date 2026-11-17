<script setup lang="ts">
/*
 * Les icônes au trait des écrans de missions, dans une pastille teintée (classe `m-icone`, voir missions.css).
 * Décoratives : le titre à côté dit tout.
 */
export type NomIcone = 'besoin' | 'etapes' | 'proposer' | 'propositions' | 'devis' | 'suivi' | 'echanges' | 'avis' | 'livrer'
defineProps<{ nom: NomIcone }>()
</script>

<template>
  <span class="m-icone" aria-hidden="true">
    <svg viewBox="0 0 24 24">
      <template v-if="nom === 'besoin'"><path d="M7 3h7l5 5v13H7z" /><path d="M14 3v5h5M10 13h6M10 17h4" /></template>
      <template v-else-if="nom === 'etapes'"><circle cx="5" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="19" cy="12" r="2" /><path d="M7 12h3M14 12h3" /></template>
      <template v-else-if="nom === 'proposer'"><path d="M21 3 10 14" /><path d="m21 3-7 18-4-7-7-4z" /></template>
      <template v-else-if="nom === 'propositions'"><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5" /></template>
      <template v-else-if="nom === 'devis'"><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" /><path d="M9 8h6M9 12h6M9 16h3" /></template>
      <template v-else-if="nom === 'suivi'"><path d="M5 21V4" /><path d="M5 4h12l-2 4 2 4H5" /></template>
      <template v-else-if="nom === 'echanges'"><path d="M4 5h12v9H8l-4 3.5z" /><path d="M16 9h4v9.5L17 16h-6v-2" /></template>
      <template v-else-if="nom === 'avis'"><path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" /></template>
      <template v-else><path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z" /><path d="m3 7.5 9 4.5 9-4.5M12 12v9" /></template>
    </svg>
  </span>
</template>
