<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'

/*
 * Enregistrer un message vocal (#157), dans le navigateur, avec MediaRecorder : un clic sur le micro démarre, puis
 * « Envoyer » ou « Annuler ». 3 minutes au plus (envoyé tout seul au bout), 1 seconde au moins. La voix est compressée
 * en Opus à 32 kbit/s : environ 240 Ko la minute, léger même en 3G. Safari (iPhone) enregistre en MP4 : l'API accepte
 * les deux (contracts/fichiers.yaml, `vocal`).
 */
const props = withDefaults(defineProps<{ desactive?: boolean }>(), { desactive: false })
const emit = defineEmits<{ envoyer: [fichier: File]; erreur: [code: 'micro_refuse' | 'trop_court'] }>()
const { t } = useI18n()

type EtatVocal = 'repos' | 'preparation' | 'enregistre'
const DUREE_MAX_S = 180
const DUREE_MIN_MS = 1_000
const FORMATS = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm']

const possible = typeof window !== 'undefined' && typeof window.MediaRecorder === 'function' && !!navigator.mediaDevices?.getUserMedia
const etat = ref<EtatVocal>('repos')
const secondes = ref(0)
let enregistreur: MediaRecorder | null = null
let flux: MediaStream | null = null
let morceaux: Blob[] = []
let debut = 0
let minuteur: ReturnType<typeof setInterval> | null = null
let aEnvoyer = false

const duree = computed(() => `${Math.floor(secondes.value / 60)}:${String(secondes.value % 60).padStart(2, '0')}`)

async function commencer() {
  if (!possible || etat.value !== 'repos' || props.desactive) return
  etat.value = 'preparation'
  try {
    flux = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
  } catch {
    etat.value = 'repos'
    emit('erreur', 'micro_refuse')
    return
  }
  const format = FORMATS.find((f) => MediaRecorder.isTypeSupported?.(f))
  enregistreur = new MediaRecorder(flux, { ...(format ? { mimeType: format } : {}), audioBitsPerSecond: 32_000 })
  morceaux = []
  aEnvoyer = false
  enregistreur.ondataavailable = (e) => {
    if (e.data.size) morceaux.push(e.data)
  }
  enregistreur.onstop = terminer
  enregistreur.start(1_000)
  debut = Date.now()
  secondes.value = 0
  etat.value = 'enregistre'
  minuteur = setInterval(() => {
    secondes.value = Math.floor((Date.now() - debut) / 1000)
    if (secondes.value >= DUREE_MAX_S) envoyer()
  }, 250)
}

function arreter(envoi: boolean) {
  aEnvoyer = envoi
  if (minuteur) clearInterval(minuteur)
  minuteur = null
  if (enregistreur && enregistreur.state !== 'inactive') enregistreur.stop()
  else terminer()
}

const envoyer = () => arreter(true)
const annuler = () => arreter(false)

function terminer() {
  const type = enregistreur?.mimeType || morceaux[0]?.type || 'audio/webm'
  const assezLong = Date.now() - debut >= DUREE_MIN_MS
  flux?.getTracks().forEach((p) => p.stop())
  flux = null
  enregistreur = null
  etat.value = 'repos'
  if (!aEnvoyer) return
  aEnvoyer = false
  if (!assezLong || !morceaux.length) return emit('erreur', 'trop_court')
  const extension = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm'
  emit('envoyer', new File(morceaux, `message-vocal.${extension}`, { type: type.split(';')[0] }))
  morceaux = []
}

onBeforeUnmount(() => {
  aEnvoyer = false
  if (minuteur) clearInterval(minuteur)
  if (enregistreur && enregistreur.state !== 'inactive') enregistreur.stop()
  flux?.getTracks().forEach((p) => p.stop())
})
</script>

<template>
  <template v-if="possible">
    <button
      v-if="etat !== 'enregistre'"
      type="button"
      class="outil"
      :aria-label="t('vocal.enregistrer')"
      :title="t('vocal.enregistrer')"
      :disabled="desactive || etat === 'preparation'"
      data-test="enregistrer-vocal"
      @click="commencer"
    >
      <svg viewBox="0 0 20 20" width="17" height="17" aria-hidden="true"><path d="M10 2.5A2.5 2.5 0 0 0 7.5 5v4.5a2.5 2.5 0 0 0 5 0V5A2.5 2.5 0 0 0 10 2.5ZM5 9.5a5 5 0 0 0 10 0M10 14.5v3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
    </button>
    <span v-else class="vocal" role="status" data-test="vocal-en-cours">
      <span class="vocal__point" aria-hidden="true" />
      <span class="vocal__duree">{{ t('vocal.enCours', { duree }) }}</span>
      <button type="button" class="vocal__bouton" data-test="annuler-vocal" @click="annuler">{{ t('vocal.annuler') }}</button>
      <button type="button" class="vocal__bouton vocal__bouton--envoyer" data-test="envoyer-vocal" @click="envoyer">{{ t('vocal.envoyer') }}</button>
    </span>
  </template>
</template>

<style scoped>
.outil { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--ink-muted); cursor: pointer; }
.outil:hover:not(:disabled) { background: var(--surface-raised); color: var(--ink); }
.outil:disabled { opacity: 0.5; cursor: default; }
.outil:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.vocal { display: inline-flex; align-items: center; gap: var(--space-2); padding-left: var(--space-1); }
.vocal__point { width: 10px; height: 10px; border-radius: 50%; background: #e5484d; animation: battement 1.2s ease-in-out infinite; }
.vocal__duree { font-variant-numeric: tabular-nums; font-size: var(--type-body-sm-size); color: var(--ink); }
.vocal__bouton { padding: 4px 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--surface); color: var(--ink); font: inherit; font-size: 0.8125rem; cursor: pointer; }
.vocal__bouton--envoyer { border-color: var(--action); background: var(--action); color: var(--on-action); font-weight: 600; }
.vocal__bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes battement { 50% { opacity: 0.35; } }
@media (prefers-reduced-motion: reduce) { .vocal__point { animation: none; } }
</style>
