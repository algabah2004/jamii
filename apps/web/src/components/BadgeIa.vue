<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/*
 * « IA » : partout où c'est l'intelligence artificielle qui parle ou qui propose (Ayo, un plan, une répartition),
 * le même badge. On sait toujours à qui on a affaire ; une proposition de l'IA se relit avant de s'appliquer.
 */
withDefaults(defineProps<{ long?: boolean }>(), { long: false })
const { t } = useI18n()
</script>

<template>
  <span class="badge-ia" :title="t('ia.badgeAide')">
    <svg viewBox="0 0 20 20" width="12" height="12" aria-hidden="true" focusable="false">
      <path d="M10 2.5c.5 3.4 2.1 5 5.5 5.5-3.4.5-5 2.1-5.5 5.5-.5-3.4-2.1-5-5.5-5.5 3.4-.5 5-2.1 5.5-5.5ZM15.5 12.5c.2 1.3.9 2 2.2 2.2-1.3.2-2 .9-2.2 2.2-.2-1.3-.9-2-2.2-2.2 1.3-.2 2-.9 2.2-2.2Z" fill="currentColor" />
    </svg>
    {{ long ? t('ia.badgeLong') : t('ia.badge') }}
  </span>
</template>

<style>
.badge-ia {
  display: inline-flex; align-items: center; gap: 4px; padding: 1px 7px 1px 6px; border-radius: var(--radius-pill);
  background: linear-gradient(135deg, color-mix(in srgb, var(--electric-blue, var(--action)) 18%, transparent), color-mix(in srgb, var(--cantaloupe, var(--proof)) 18%, transparent));
  border: 1px solid color-mix(in srgb, var(--electric-blue, var(--action)) 35%, transparent);
  color: var(--action-ink); font-size: 0.6875rem; font-weight: 800; letter-spacing: 0.2px; white-space: nowrap; vertical-align: middle;
}
</style>
