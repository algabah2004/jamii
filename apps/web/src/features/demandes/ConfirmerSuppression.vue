<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'

/*
 * Confirmer la suppression d'une discussion avec Ayo, dans une vraie boîte de dialogue de Jamii (`<dialog>` : le focus
 * y reste, Échap ferme), au lieu de la fenêtre grise du navigateur. « Annuler » a le focus à l'ouverture, pour qu'un
 * Entrée réflexe ne supprime rien. `demander()` rend vrai si la personne confirme.
 */
const { t } = useI18n()
const boite = ref<HTMLDialogElement | null>(null)
const annuler = ref<HTMLButtonElement | null>(null)
let repondre: ((oui: boolean) => void) | null = null

function fermer(oui: boolean) {
  const d = boite.value
  if (d) {
    if (typeof d.close === 'function' && d.open) d.close()
    else d.removeAttribute('open')
  }
  repondre?.(oui)
  repondre = null
}

function demander(): Promise<boolean> {
  repondre?.(false)
  const d = boite.value
  if (!d) return Promise.resolve(false)
  if (typeof d.showModal === 'function') d.showModal()
  else d.setAttribute('open', '')
  void nextTick(() => annuler.value?.focus())
  return new Promise((fini) => (repondre = fini))
}

defineExpose({ demander })
</script>

<template>
  <dialog ref="boite" class="boite" aria-labelledby="titre-suppression-discussion" data-test="confirmer-suppression" @cancel.prevent="fermer(false)" @click.self="fermer(false)">
    <span class="boite__signe" aria-hidden="true">
      <svg viewBox="0 0 20 20" width="22" height="22"><path d="M4 6h12M8 6V4.5A1.5 1.5 0 0 1 9.5 3h1A1.5 1.5 0 0 1 12 4.5V6m-6.5 0 .7 9.6A1.5 1.5 0 0 0 7.7 17h4.6a1.5 1.5 0 0 0 1.5-1.4L14.5 6" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
    </span>
    <h2 id="titre-suppression-discussion">{{ t('demandes.discussion.suppression.titre') }}</h2>
    <p>{{ t('demandes.discussion.suppression.texte') }}</p>
    <div class="boite__commandes">
      <button ref="annuler" type="button" class="j-bouton j-bouton--secondaire" data-test="annuler-suppression" @click="fermer(false)">
        {{ t('demandes.discussion.suppression.annuler') }}
      </button>
      <button type="button" class="supprimer" data-test="confirmer" @click="fermer(true)">
        {{ t('demandes.discussion.suppression.confirmer') }}
      </button>
    </div>
  </dialog>
</template>

<style scoped>
.boite {
  width: min(28rem, calc(100vw - 2rem)); height: fit-content; max-height: calc(100dvh - 2rem); margin: auto; overflow: auto;
  padding: var(--space-8); border: 1px solid var(--line); border-radius: var(--radius-lg);
  background: var(--surface); color: var(--ink); box-shadow: 0 24px 60px rgb(2 7 10 / 0.35);
}
.boite[open] { display: grid; gap: var(--space-3); justify-items: start; align-content: start; animation: entre 220ms var(--ease-pop); }
.boite::backdrop { background: rgb(2 7 10 / 0.55); backdrop-filter: blur(3px); }
.boite h2 { margin: 0; font-size: 1.375rem; }
.boite p { margin: 0; color: var(--ink-muted); }
.boite__signe { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; background: color-mix(in srgb, #C62828 12%, transparent); color: #C62828; }
.boite__commandes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); justify-content: flex-end; width: 100%; margin-top: var(--space-3); }
.boite__commandes > * { flex: 0 0 auto; min-height: 44px; }
/* Une seule couleur forte : le rouge de l'action définitive, le même en clair et en sombre (blanc dessus : contraste 5,6). */
.supprimer {
  min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill);
  background: #C62828; color: #fff; font: inherit; font-weight: 600; cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out);
}
.supprimer:hover { background: #A61E1E; }
.boite__commandes :focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; box-shadow: none; }
@keyframes entre { from { opacity: 0; transform: translateY(8px) scale(0.98); } }
@media (prefers-reduced-motion: reduce) { .boite[open] { animation: none; } }
</style>
