<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { changerLangue } from '../i18n'

/*
 * Français ou anglais en un clic, dans l'en-tête, à côté du bouton de thème. Comme lui, le bouton montre ce qu'on
 * obtient au clic : « EN » quand la page est en français, « FR » quand elle est en anglais.
 */
const { t, locale } = useI18n()
const cible = computed(() => (locale.value === 'fr' ? 'en' : 'fr'))
</script>

<template>
  <button
    type="button"
    class="bouton-langue"
    :lang="cible"
    :aria-label="cible === 'en' ? t('langues.passerEn') : t('langues.passerFr')"
    :title="cible === 'en' ? t('langues.passerEn') : t('langues.passerFr')"
    data-test="bouton-langue"
    @click="changerLangue(cible)"
  >
    {{ cible.toUpperCase() }}
  </button>
</template>

<style scoped>
.bouton-langue {
  display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%;
  border: 1px solid var(--line); background: transparent; color: var(--ink); cursor: pointer;
  font: inherit; font-size: 0.8125rem; font-weight: 700; letter-spacing: 0.04em;
  transition: background var(--motion-fast) var(--ease-out);
}
.bouton-langue:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.bouton-langue:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
