<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { doitAfficherHaut } from './boutonHaut'

/*
 * « Revenir en haut » : seulement sur une page longue (plus de trois écrans) et une fois qu'on a bien défilé. Une page
 * courte n'en a pas besoin. En haut, le focus va au contenu principal : au clavier comme au lecteur d'écran, on repart
 * du début de la page.
 */
const { t } = useI18n()
const visible = ref(false)
let image = 0

function mesurer() {
  image = 0
  visible.value = doitAfficherHaut(window.scrollY, window.innerHeight, document.documentElement.scrollHeight)
}
function surChangement() {
  if (!image) image = requestAnimationFrame(mesurer)
}
onMounted(() => {
  window.addEventListener('scroll', surChangement, { passive: true })
  window.addEventListener('resize', surChangement, { passive: true })
  mesurer()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', surChangement)
  window.removeEventListener('resize', surChangement)
  if (image) cancelAnimationFrame(image)
})

function remonter() {
  const doux = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: doux ? 'smooth' : 'auto' })
  document.getElementById('contenu')?.focus({ preventScroll: true })
}
</script>

<template>
  <Transition name="haut">
    <button v-if="visible" type="button" class="bouton-haut" :aria-label="t('pied.haut')" :title="t('pied.haut')" data-test="bouton-haut" @click="remonter">
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M12 19V5M5.5 11.5 12 5l6.5 6.5" /></svg>
    </button>
  </Transition>
</template>

<style scoped>
.bouton-haut {
  position: fixed; z-index: 40; right: max(var(--space-4), env(safe-area-inset-right));
  bottom: max(var(--space-4), env(safe-area-inset-bottom));
  display: grid; place-items: center; width: 48px; height: 48px; padding: 0;
  border: 1px solid var(--line-strong); border-radius: 50%; background: var(--surface); color: var(--ink); cursor: pointer;
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.08), 0 10px 24px -10px rgb(2 7 10 / 0.35);
  transition: background var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.bouton-haut svg { fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.bouton-haut:hover { background: var(--surface-raised); transform: translateY(-2px); }
.bouton-haut:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; }
.haut-enter-active, .haut-leave-active { transition: opacity var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out); }
.haut-enter-from, .haut-leave-to { opacity: 0; transform: translateY(8px); }
/* Une discussion en plein écran (components/pleinEcran.ts) a sa propre zone pour écrire, en bas : pas de bouton. */
:root.discussion-plein-ecran .bouton-haut { display: none; }
@media (prefers-reduced-motion: reduce) {
  .bouton-haut, .haut-enter-active, .haut-leave-active { transition: none; }
  .bouton-haut:hover { transform: none; }
}
</style>
