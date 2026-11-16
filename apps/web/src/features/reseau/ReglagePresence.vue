<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { changerMaVisibilite, lireMaVisibilite } from './presence'

/* « Montrer quand je suis en ligne » (#157). Réciproque : si je me cache, je ne vois plus la présence des autres. */
const { t } = useI18n()
const visible = ref<boolean | null>(null)
const envoi = ref(false)
const erreur = ref(false)

onMounted(async () => {
  try { visible.value = await lireMaVisibilite() } catch { erreur.value = true }
})

async function basculer() {
  if (visible.value === null || envoi.value) return
  envoi.value = true
  erreur.value = false
  try { visible.value = await changerMaVisibilite(!visible.value) } catch { erreur.value = true } finally { envoi.value = false }
}
</script>

<template>
  <section v-if="visible !== null || erreur" class="reglage carte" data-test="reglage-presence">
    <div class="reglage__texte">
      <h2 id="reglage-presence-titre">{{ t('presence.reglage.titre') }}</h2>
      <p id="reglage-presence-aide">{{ t('presence.reglage.aide') }}</p>
      <p v-if="erreur" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
    </div>
    <button
      v-if="visible !== null"
      type="button"
      role="switch"
      class="interrupteur"
      :aria-checked="visible"
      aria-labelledby="reglage-presence-titre"
      aria-describedby="reglage-presence-aide"
      :aria-busy="envoi || undefined"
      :disabled="envoi"
      data-test="basculer-presence"
      @click="basculer"
    >
      <span class="interrupteur__curseur" aria-hidden="true" />
    </button>
  </section>
</template>

<style scoped>
.reglage { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); padding: var(--space-4) var(--space-5); border: 1px solid var(--line); border-radius: 20px; background: var(--surface); }
.reglage__texte { display: grid; gap: var(--space-1); min-width: 0; }
.reglage h2 { margin: 0; font-size: 1rem; }
.reglage p { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); line-height: 1.5; }
.reglage .erreur { color: var(--danger); font-weight: 600; }
.interrupteur { flex: none; position: relative; width: 52px; height: 30px; padding: 0; border: 0; border-radius: 999px; background: var(--line); cursor: pointer; transition: background 0.2s; }
.interrupteur[aria-checked='true'] { background: var(--action); }
.interrupteur__curseur { position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; border-radius: 50%; background: var(--surface); box-shadow: 0 1px 3px rgb(0 0 0 / 0.25); transition: transform 0.2s; }
.interrupteur[aria-checked='true'] .interrupteur__curseur { transform: translateX(22px); }
.interrupteur:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.interrupteur:disabled { opacity: 0.6; cursor: wait; }
@media (prefers-reduced-motion: reduce) { .interrupteur, .interrupteur__curseur { transition: none; } }
</style>
