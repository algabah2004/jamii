<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton } from '../../design-system'

/**
 * « Signaler » une publication ou une réponse à un modérateur (POST /api/signalements).
 * Un petit panneau s'ouvre sous le bouton : on choisit le motif, on envoie, on reçoit un merci.
 */
const props = defineProps<{ cibleType: 'publication' | 'reponse'; cibleId: string }>()
const { t } = useI18n()

const MOTIFS = ['spam', 'irrespectueux', 'hors_sujet', 'autre'] as const
type Motif = (typeof MOTIFS)[number]

const ouvert = ref(false)
const motif = ref<Motif | null>(null)
const etat = ref<'repos' | 'envoi' | 'envoye' | 'erreur'>('repos')

async function envoyer() {
  if (!motif.value) return
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
  <div class="signaler" @keydown.esc="ouvert = false">
    <p v-if="etat === 'envoye'" role="status" class="signaler__merci" data-test="signale">{{ t('signaler.merci') }}</p>
    <template v-else>
      <button type="button" class="signaler__bouton" :aria-expanded="ouvert" data-test="signaler" @click="ouvert = !ouvert">
        {{ t('signaler.bouton') }}
      </button>
      <form v-if="ouvert" class="signaler__panneau" @submit.prevent="envoyer">
        <fieldset>
          <legend>{{ t('signaler.pourquoi') }}</legend>
          <label v-for="m in MOTIFS" :key="m">
            <input v-model="motif" type="radio" :name="`motif-${cibleId}`" :value="m" /> {{ t(`signaler.motifs.${m}`) }}
          </label>
        </fieldset>
        <p v-if="etat === 'erreur'" role="alert" class="signaler__erreur">{{ t('signaler.erreur') }}</p>
        <div class="signaler__commandes">
          <JBouton variante="discret" @click="ouvert = false">{{ t('communautes.annuler') }}</JBouton>
          <JBouton type="submit" variante="secondaire" :desactive="!motif" :occupe="etat === 'envoi'">{{ t('signaler.envoyer') }}</JBouton>
        </div>
      </form>
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
.signaler__commandes { display: flex; justify-content: flex-end; gap: var(--space-2); }
.signaler__merci { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.signaler__erreur { margin: 0; color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }
</style>
