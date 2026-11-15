<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import EchangesMission from './EchangesMission.vue'
import type { Mission } from './missions'

/*
 * Les échanges avec un développeur, dans un panneau qui glisse sur le côté : le client compare les propositions et
 * discute sans perdre sa place dans la liste. Une vraie boîte de dialogue (`<dialog>`) : le focus y reste, Échap
 * ferme, et il revient au bouton « Écrire » à la fermeture. En plein écran sur mobile.
 */
type Personne = { id: string; nom: string; avatarUrl?: string | null }
const props = defineProps<{ mission: Mission; avec: Personne | null }>()
const emit = defineEmits<{ fermer: [] }>()
const { t } = useI18n()

const boite = ref<HTMLDialogElement | null>(null)
let retour: HTMLElement | null = null

watch(
  () => props.avec?.id,
  async (id) => {
    const d = boite.value
    if (!d) return
    if (id && !d.open) {
      retour = document.activeElement instanceof HTMLElement ? document.activeElement : null
      if (typeof d.showModal === 'function') d.showModal()
      else d.setAttribute('open', '')
      await nextTick()
      d.querySelector<HTMLTextAreaElement>('textarea')?.focus()
    } else if (!id && d.open) {
      fermer()
    }
  },
  { flush: 'post' },
)

function fermer() {
  const d = boite.value
  if (d?.open) {
    if (typeof d.close === 'function') d.close()
    else d.removeAttribute('open')
  }
  retour?.focus()
  emit('fermer')
}
</script>

<template>
  <dialog ref="boite" class="tiroir" aria-labelledby="echanges-titre" data-test="tiroir-echanges" @cancel.prevent="fermer" @click.self="fermer">
    <div class="tiroir__corps">
      <button type="button" class="tiroir__fermer" data-test="fermer-tiroir" @click="fermer">
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M5 5l10 10M15 5L5 15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        <span class="visuellement-cache">{{ t('missions.echanges.fermer') }}</span>
      </button>
      <EchangesMission v-if="avec" :mission="mission" :avec="avec" />
    </div>
  </dialog>
</template>

<style scoped>
.tiroir {
  margin: 0 0 0 auto; padding: 0; block-size: 100dvh; max-block-size: 100dvh; inline-size: min(32rem, 100vw); max-inline-size: 100vw;
  border: 0; border-inline-start: 1px solid var(--line-strong); background: var(--canvas, var(--surface)); color: var(--ink);
}
.tiroir[open] { animation: glisse 220ms ease-out; }
.tiroir::backdrop { background: rgb(2 7 10 / 0.45); backdrop-filter: blur(2px); }
.tiroir__corps { position: relative; display: grid; align-content: start; block-size: 100%; overflow-y: auto; padding: var(--space-4); }
.tiroir__corps :deep(.echanges) { box-shadow: none; }
.tiroir__corps :deep(.tete) { padding-inline-end: 3.5rem; }
.tiroir__corps :deep(.fil) { max-block-size: calc(100dvh - 18rem); }
.tiroir__fermer {
  position: absolute; inset-block-start: var(--space-6); inset-inline-end: var(--space-6); z-index: 1;
  display: grid; place-items: center; inline-size: 40px; block-size: 40px; border: 1px solid var(--line-strong); border-radius: 50%;
  background: var(--surface); color: var(--ink); cursor: pointer;
}
.tiroir__fermer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes glisse { from { transform: translateX(100%); } to { transform: none; } }
@media (prefers-reduced-motion: reduce) { .tiroir[open] { animation: none; } }
</style>
