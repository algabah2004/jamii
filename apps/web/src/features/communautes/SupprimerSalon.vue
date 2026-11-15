<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton } from '../../design-system'

/*
 * Supprimer un salon (modérateurs, contracts/communautes.yaml v0.5). C'est définitif : on le dit, avec le nombre de
 * messages perdus, dans une vraie boîte de dialogue (`<dialog>` : le focus y reste, Échap ferme), et « Annuler » a le
 * focus par défaut, pour qu'un Entrée réflexe ne supprime rien.
 */
const props = defineProps<{ slug: string; salon: string; nom: string; messages: number }>()
const emit = defineEmits<{ supprime: [salon: string] }>()
const { t } = useI18n()

const boite = ref<HTMLDialogElement | null>(null)
const annuler = ref<HTMLButtonElement | null>(null)
const etat = ref<'repos' | 'envoi' | 'erreur'>('repos')

function ouvrir() {
  etat.value = 'repos'
  const d = boite.value
  if (!d) return
  if (typeof d.showModal === 'function') d.showModal()
  else d.setAttribute('open', '')
  void nextTick(() => annuler.value?.focus())
}
function fermer() {
  const d = boite.value
  if (!d) return
  if (typeof d.close === 'function') d.close()
  else d.removeAttribute('open')
}

async function supprimer() {
  etat.value = 'envoi'
  try {
    const { response } = await api.DELETE('/api/communautes/{slug}/salons/{salon}', {
      params: { path: { slug: props.slug, salon: props.salon } },
    })
    if (!response.ok) return void (etat.value = 'erreur')
    fermer()
    emit('supprime', props.salon)
  } catch {
    etat.value = 'erreur'
  }
}
</script>

<template>
  <button type="button" class="ouvrir" :title="t('communautes.supprimerSalon.bouton')" data-test="supprimer-salon" @click="ouvrir">
    <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M4 6h12M8 6V4.5A1.5 1.5 0 0 1 9.5 3h1A1.5 1.5 0 0 1 12 4.5V6m-6.5 0 .7 9.6A1.5 1.5 0 0 0 7.7 17h4.6a1.5 1.5 0 0 0 1.5-1.4L14.5 6" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
    <span class="visuellement-cache">{{ t('communautes.supprimerSalon.bouton') }}</span>
  </button>

  <dialog ref="boite" class="boite" aria-labelledby="titre-suppression" data-test="confirmer-suppression" @cancel="fermer">
    <span class="boite__signe" aria-hidden="true">#</span>
    <h2 id="titre-suppression">{{ t('communautes.supprimerSalon.titre', { salon: nom }) }}</h2>
    <p>{{ t('communautes.supprimerSalon.texte', { n: messages }, messages) }}</p>
    <p v-if="etat === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
    <div class="boite__commandes">
      <button ref="annuler" type="button" class="j-bouton j-bouton--secondaire" data-test="annuler-suppression" @click="fermer">{{ t('communautes.annuler') }}</button>
      <JBouton :occupe="etat === 'envoi'" class="danger" data-test="confirmer" @click="supprimer">{{ t('communautes.supprimerSalon.confirmer') }}</JBouton>
    </div>
  </dialog>
</template>

<style scoped>
.ouvrir {
  display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; border: 1px solid var(--line);
  background: transparent; color: var(--ink-muted); cursor: pointer;
}
.ouvrir:hover { color: var(--danger); border-color: var(--danger); }
.ouvrir:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.boite {
  width: min(28rem, calc(100vw - 2rem)); padding: var(--space-8); border: 1px solid var(--line); border-radius: var(--radius-lg);
  background: var(--surface); color: var(--ink); box-shadow: 0 24px 60px rgb(2 7 10 / 0.35);
}
.boite[open] { display: grid; gap: var(--space-3); justify-items: start; animation: entre 220ms var(--ease-pop); }
.boite::backdrop { background: rgb(2 7 10 / 0.55); backdrop-filter: blur(3px); }
.boite h2 { margin: 0; font-size: 1.375rem; }
.boite p { margin: 0; color: var(--ink-muted); }
.boite__signe { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 16px; background: color-mix(in srgb, var(--danger) 14%, transparent); color: var(--danger); font-size: 1.75rem; font-weight: 700; }
.boite__commandes { display: flex; gap: var(--space-2); justify-content: flex-end; width: 100%; margin-top: var(--space-3); }
.danger { background: var(--danger); border-color: var(--danger); color: #fff; }
.erreur { color: var(--danger) !important; font-weight: 600; }
@keyframes entre { from { opacity: 0; transform: translateY(8px) scale(0.98); } }
@media (prefers-reduced-motion: reduce) { .boite[open] { animation: none; } }
</style>
