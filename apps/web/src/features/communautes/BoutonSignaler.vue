<script setup lang="ts">
import { nextTick, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton } from '../../design-system'

/**
 * « Signaler » une publication ou une réponse à un modérateur (POST /api/signalements).
 * Un petit panneau s'ouvre sous le bouton : on choisit le motif, on envoie, on reçoit un merci.
 */
const props = defineProps<{ cibleType: 'publication' | 'reponse'; cibleId: string; integre?: boolean }>()
const { t } = useI18n()

const MOTIFS = ['spam', 'irrespectueux', 'hors_sujet', 'autre'] as const
type Motif = (typeof MOTIFS)[number]

const ouvert = ref(false)
const bouton = ref<HTMLButtonElement | null>(null)
const panneau = ref<HTMLFormElement | null>(null)
const panneauId = `signalement-${useId()}`

function fermer() {
  ouvert.value = false
  void nextTick(() => bouton.value?.focus())
}
async function basculer() {
  if (ouvert.value) return fermer()
  ouvert.value = true
  await nextTick()
  panneau.value?.querySelector<HTMLInputElement>('input:checked, input')?.focus()
}
const motif = ref<Motif | null>(null)
const etat = ref<'repos' | 'envoi' | 'envoye' | 'erreur'>('repos')

async function envoyer() {
  if (!motif.value || etat.value === 'envoi') return
  etat.value = 'envoi'
  try {
    const { response } = await api.POST('/api/signalements', {
      body: { cibleType: props.cibleType, cibleId: props.cibleId, motif: motif.value },
    })
    // 409 : déjà signalé par cette personne ; pour elle, le résultat est le même.
    etat.value = response.ok || response.status === 409 ? 'envoye' : 'erreur'
  } catch {
    etat.value = 'erreur'
  }
  if (etat.value === 'envoye') ouvert.value = false
}
</script>

<template>
  <div class="signaler" :class="{ 'signaler--integre': integre }" @keydown.esc.prevent.stop="fermer">
    <p v-if="etat === 'envoye'" role="status" class="signaler__merci" data-test="signale">{{ t('signaler.merci') }}</p>
    <template v-else>
      <button ref="bouton" type="button" class="signaler__bouton" :aria-expanded="ouvert" :aria-controls="ouvert ? panneauId : undefined" :disabled="etat === 'envoi'" data-test="signaler" @click="basculer">
        <svg v-if="integre" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M4 17V3m0 1c4-3 8 3 12 0v8c-4 3-8-3-12 0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        {{ t('signaler.bouton') }}
      </button>
      <Transition name="panneau">
      <form v-if="ouvert" :id="panneauId" ref="panneau" class="signaler__panneau" :aria-label="t('signaler.pourquoi')" :aria-busy="etat === 'envoi'" @submit.prevent="envoyer">
        <fieldset :disabled="etat === 'envoi'">
          <legend>{{ t('signaler.pourquoi') }}</legend>
          <label v-for="m in MOTIFS" :key="m">
            <input v-model="motif" type="radio" :name="panneauId" :value="m" /> {{ t(`signaler.motifs.${m}`) }}
          </label>
        </fieldset>
        <p v-if="etat === 'erreur'" role="alert" class="signaler__erreur">{{ t('signaler.erreur') }}</p>
        <div class="signaler__commandes">
          <JBouton variante="discret" @click="fermer">{{ t('communautes.annuler') }}</JBouton>
          <JBouton type="submit" variante="secondaire" :desactive="!motif" :occupe="etat === 'envoi'">{{ t('signaler.envoyer') }}</JBouton>
        </div>
      </form>
      </Transition>
    </template>
  </div>
</template>

<style scoped>
.signaler { position: relative; }
.signaler__bouton { background: none; border: 0; padding: var(--space-1) var(--space-2); border-radius: var(--radius-sm); color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }
.signaler__bouton:hover, .signaler__bouton[aria-expanded='true'] { color: var(--ink); background: var(--surface-raised); }
.signaler__panneau {
  position: absolute; z-index: 5; inset-inline-end: 0; top: calc(100% + var(--space-1));
  width: min(18rem, 80vw); display: grid; gap: var(--space-3); padding: var(--space-4);
  background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  box-shadow: 0 12px 32px color-mix(in srgb, var(--ink) 14%, transparent);
}
fieldset { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
legend { font-weight: 600; margin-bottom: var(--space-2); }
label { display: flex; gap: var(--space-2); align-items: center; cursor: pointer; }
.panneau-enter-active { transition: opacity var(--motion-fast) var(--ease-out), transform var(--motion-base) var(--ease-pop); }
.panneau-leave-active { transition: opacity var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out); }
.panneau-enter-from, .panneau-leave-to { opacity: 0; transform: translateY(-4px) scale(0.96); }
.signaler__panneau { transform-origin: top right; }
.signaler__commandes { display: flex; justify-content: flex-end; gap: var(--space-2); }
.signaler__merci { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.signaler--integre { display: contents; }
.signaler--integre .signaler__bouton { display: inline-flex; align-items: center; gap: 6px; min-height: 34px; margin-inline-start: calc(-1 * var(--space-2)); border-radius: var(--radius-pill); font: inherit; font-size: .875rem; }
.signaler--integre .signaler__panneau { position: static; flex: 0 0 100%; box-sizing: border-box; width: 100%; min-width: 0; margin-top: var(--space-2); background: var(--surface-raised); box-shadow: none; transform-origin: top left; }
.signaler__bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.signaler__commandes { flex-wrap: wrap; }
@media (prefers-reduced-motion: reduce) { .panneau-enter-active, .panneau-leave-active { transition: none; } }
.signaler__erreur { margin: 0; color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }
</style>
