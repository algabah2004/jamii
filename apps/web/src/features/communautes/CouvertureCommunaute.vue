<script setup lang="ts">
import { ref, watch } from 'vue'
import { teinteDe } from './identite'
import TextureCommunaute from './TextureCommunaute.vue'

/*
 * La couverture d'une communauté sur sa carte : son image si elle en a une, sinon son motif (sa couleur et sa texture,
 * dessinées en SVG, sans rien à charger). Une image qui ne se charge pas laisse place au motif. Décorative.
 */
const props = defineProps<{ slug: string; couverture: string | null }>()
const echec = ref(false)
watch(() => props.couverture, () => (echec.value = false))
</script>

<template>
  <div class="couverture" :style="{ '--fond': teinteDe(slug).fond }" aria-hidden="true">
    <img v-if="couverture && !echec" :src="couverture" alt="" loading="lazy" decoding="async" class="couverture__image" data-test="couverture-image" @error="echec = true" />
    <TextureCommunaute v-else :slug="slug" />
  </div>
</template>

<style scoped>
.couverture { position: relative; overflow: hidden; background: var(--fond); }
.couverture__image { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
</style>
