<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { useActivite } from './activite'

/* Notifications, messages et contacts : trois pages, un seul endroit dans l'en-tête (la cloche). */
const { t } = useI18n()
const activite = useActivite()
const onglets = computed(() => [
  { vers: '/notifications', cle: 'notifications', n: activite.value?.notificationsNonLues ?? 0 },
  { vers: '/messages', cle: 'messages', n: activite.value?.messagesNonLus ?? 0 },
  { vers: '/contacts', cle: 'contacts', n: activite.value?.demandesContact ?? 0 },
])
</script>

<template>
  <nav class="onglets" :aria-label="t('reseau.onglets.titre')">
    <RouterLink v-for="o in onglets" :key="o.cle" :to="o.vers" class="onglet" :data-test="`onglet-${o.cle}`">
      {{ t(`reseau.onglets.${o.cle}`) }}
      <span v-if="o.n" class="onglet__n">{{ o.n }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.onglets { display: flex; gap: var(--space-1); padding: 4px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 6%, transparent); width: fit-content; max-width: 100%; overflow-x: auto; }
.onglet { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 40px; padding: 0 var(--space-4); border-radius: var(--radius-pill); color: var(--ink-muted); font-weight: 600; text-decoration: none; white-space: nowrap; }
.onglet:hover { color: var(--ink); }
.onglet.router-link-active { background: var(--surface); color: var(--ink); box-shadow: 0 1px 3px rgb(0 0 0 / 0.12); }
.onglet__n { min-width: 20px; height: 20px; padding: 0 6px; border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.75rem; font-weight: 700; display: grid; place-items: center; }
</style>
