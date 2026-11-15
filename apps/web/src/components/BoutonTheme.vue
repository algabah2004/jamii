<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { choisirTheme, themeActuel } from '../design-system'

/*
 * Clair ou sombre en un clic, dans l'en-tête. Le choix « Système » et la langue restent dans le pied de page.
 * Quand le thème suit l'appareil, le bouton propose l'inverse de ce qu'on voit.
 */
const { t } = useI18n()

const requete = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null
const systemeSombre = ref(requete?.matches ?? false)
const suivre = (e: MediaQueryListEvent) => (systemeSombre.value = e.matches)
requete?.addEventListener?.('change', suivre)
onBeforeUnmount(() => requete?.removeEventListener?.('change', suivre))

const sombre = computed(() => themeActuel.value === 'sombre' || (themeActuel.value === 'systeme' && systemeSombre.value))
function basculer() {
  choisirTheme(sombre.value ? 'clair' : 'sombre')
}
</script>

<template>
  <button
    type="button"
    class="bouton-theme"
    :aria-label="sombre ? t('themes.passerClair') : t('themes.passerSombre')"
    :title="sombre ? t('themes.passerClair') : t('themes.passerSombre')"
    data-test="bouton-theme"
    @click="basculer"
  >
    <!-- Le soleil en thème sombre (pour revenir au clair), la lune en thème clair. -->
    <svg v-if="sombre" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <circle cx="12" cy="12" r="4.5" fill="none" stroke="currentColor" stroke-width="1.8" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    </svg>
    <svg v-else viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
    </svg>
  </button>
</template>

<style scoped>
.bouton-theme {
  display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%;
  border: 1px solid var(--line); background: transparent; color: var(--ink); cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out), transform var(--motion-base) var(--ease-pop);
}
.bouton-theme:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); transform: rotate(-15deg); }
.bouton-theme:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .bouton-theme:hover { transform: none; } }
</style>
