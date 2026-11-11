<script setup lang="ts">
import { computed } from 'vue'
import enLigne from '../../../../docs/design/illustrations/en-ligne.svg?raw'
import equipe from '../../../../docs/design/illustrations/equipe.svg?raw'
import profilProuve from '../../../../docs/design/illustrations/profil-prouve.svg?raw'

/**
 * Les illustrations de la charte (docs/design/illustrations, source unique). Les deux couleurs de fond suivent le
 * thème, pour ne pas faire une tache claire en sombre. Fichiers du dépôt uniquement : jamais de contenu utilisateur.
 * `decorative` (par défaut) les cache aux lecteurs d'écran : le texte à côté dit déjà la même chose.
 */
const props = withDefaults(defineProps<{ nom: 'equipe' | 'profil-prouve' | 'en-ligne'; decorative?: boolean }>(), {
  decorative: true,
})

const SOURCES = { equipe, 'profil-prouve': profilProuve, 'en-ligne': enLigne }

const svg = computed(() => {
  let s = SOURCES[props.nom].replaceAll('#DDF0FA', 'var(--action-soft)').replaceAll('#C9E3F1', 'var(--surface-raised)')
  if (props.decorative) s = s.replace('<svg ', '<svg aria-hidden="true" focusable="false" ')
  return s
})
</script>

<template>
  <!-- v-html : fichier statique du dépôt, jamais une donnée -->
  <div class="j-illustration" v-html="svg" />
</template>

<style>
.j-illustration svg { display: block; width: 100%; height: auto; }
</style>
