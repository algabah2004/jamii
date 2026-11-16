<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SelecteurEmoji from './SelecteurEmoji.vue'

/* Réagir vite (#157) : six emojis courants, et « + » pour tous les autres. Sert dans le menu d'un message. */
defineProps<{ actives?: string[] }>()
const emit = defineEmits<{ choisir: [emoji: string] }>()
const { t } = useI18n()

const RAPIDES = ['👍', '❤️', '😂', '😮', '🙏', '🔥']
const tous = ref(false)

function choisir(e: string) {
  tous.value = false
  emit('choisir', e)
}
</script>

<template>
  <div class="barre" role="group" :aria-label="t('reactions.reagir')" data-test="barre-reactions">
    <button
      v-for="e in RAPIDES"
      :key="e"
      type="button"
      class="barre__emoji"
      :class="{ 'barre__emoji--actif': actives?.includes(e) }"
      :aria-pressed="actives?.includes(e) ?? false"
      :aria-label="t('reactions.avec', { emoji: e })"
      data-test="reaction-rapide"
      @click.stop="choisir(e)"
    >{{ e }}</button>
    <span class="barre__plus">
      <button type="button" class="barre__emoji" :aria-expanded="tous" :aria-label="t('reactions.autres')" :title="t('reactions.autres')" data-test="reaction-plus" @click.stop="tous = !tous">
        <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M10 4.5v11M4.5 10h11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
      </button>
      <SelecteurEmoji v-if="tous" class="barre__selecteur" @choisir="choisir" @fermer="tous = false" />
    </span>
  </div>
</template>

<style scoped>
.barre { display: flex; align-items: center; gap: 2px; padding: 4px; }
.barre__emoji { display: grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); font-size: 1.25rem; line-height: 1; cursor: pointer; transition: transform 150ms var(--ease-pop, ease-out); }
.barre__emoji:hover { background: var(--surface-raised); transform: scale(1.18); }
.barre__emoji--actif { background: var(--action-soft); }
.barre__emoji:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.barre__plus { position: relative; display: inline-flex; }
.barre__selecteur { position: absolute; top: calc(100% + 6px); right: 0; z-index: 40; }
@media (prefers-reduced-motion: reduce) { .barre__emoji { transition: none; } .barre__emoji:hover { transform: none; } }
</style>
