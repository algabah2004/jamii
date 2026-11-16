<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/*
 * Les réactions sous un message (#157) : « 👍 3 ». Un clic ajoute ou retire la mienne ; le survol dit qui a réagi.
 */
export type Reaction = { emoji: string; nombre: number; deMoi: boolean; noms: string[] }

defineProps<{ reactions: Reaction[] }>()
const emit = defineEmits<{ basculer: [emoji: string] }>()
const { t } = useI18n()

function qui(r: Reaction): string {
  const noms = r.noms.map((n) => (n === 'moi' ? t('reactions.toi') : n.split(' ')[0] ?? n))
  const reste = r.nombre - noms.length
  return reste > 0 ? t('reactions.quiEtAutres', { noms: noms.join(', '), n: reste }) : noms.join(', ')
}
</script>

<template>
  <ul v-if="reactions.length" class="pastilles" :aria-label="t('reactions.titre')" data-test="reactions">
    <li v-for="r in reactions" :key="r.emoji">
      <button
        type="button"
        class="pastille"
        :class="{ 'pastille--moi': r.deMoi }"
        :aria-pressed="r.deMoi"
        :title="qui(r)"
        :aria-label="t('reactions.pastille', { emoji: r.emoji, n: r.nombre, qui: qui(r) }, r.nombre)"
        data-test="reaction"
        @click.stop="emit('basculer', r.emoji)"
      >
        <span class="pastille__emoji" aria-hidden="true">{{ r.emoji }}</span>
        <span class="pastille__nombre" aria-hidden="true">{{ r.nombre }}</span>
      </button>
    </li>
  </ul>
</template>

<style scoped>
.pastilles { list-style: none; display: flex; flex-wrap: wrap; gap: 4px; margin: 2px 0 0; padding: 0; }
.pastille { display: inline-flex; align-items: center; gap: 4px; min-height: 26px; padding: 0 8px; border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer; animation: apparait 220ms var(--ease-pop, ease-out); }
.pastille:hover { border-color: var(--ink-muted); }
.pastille--moi { border-color: var(--action); background: var(--action-soft); color: var(--action-ink); }
.pastille:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.pastille__emoji { font-size: 0.9375rem; line-height: 1; }
.pastille__nombre { font-variant-numeric: tabular-nums; }
@keyframes apparait { from { transform: scale(0.6); opacity: 0; } }
@media (prefers-reduced-motion: reduce) { .pastille { animation: none; } }
</style>
