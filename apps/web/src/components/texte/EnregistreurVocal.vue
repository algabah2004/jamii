<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/*
 * Enregistrer un message vocal (#157), dans le navigateur, avec MediaRecorder : un clic sur le micro démarre, puis
 * « Envoyer » ou « Annuler ». 3 minutes au plus (envoyé tout seul au bout), 1 seconde au moins. La voix est compressée
 * en Opus à 32 kbit/s : environ 240 Ko la minute, léger même en 3G. Safari (iPhone) enregistre en MP4 : l'API accepte
 * les deux (contracts/fichiers.yaml, `vocal`).
 */
const props = withDefaults(defineProps<{ desactive?: boolean }>(), { desactive: false })
const emit = defineEmits<{ envoyer: [fichier: File]; erreur: [code: 'micro_refuse' | 'trop_court']; enCours: [actif: boolean] }>()
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

/** Le composeur cache son bouton d'envoi pendant l'enregistrement : celui de l'enregistreur prend sa place. */
watch(etat, (e, avant) => { if ((e === 'enregistre') !== (avant === 'enregistre')) emit('enCours', e === 'enregistre') }, { flush: 'sync' })

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
  if (etat.value === 'enregistre') emit('enCours', false)
  aEnvoyer = false
  if (minuteur) clearInterval(minuteur)
  // Rien n'est envoyé : on coupe l'enregistrement sans repasser par terminer().
  if (enregistreur) enregistreur.onstop = null
  if (enregistreur && enregistreur.state !== 'inactive') enregistreur.stop()
  flux?.getTracks().forEach((p) => p.stop())
})
</script>

<template>
  <template v-if="possible">
    <button
      v-if="etat !== 'enregistre'"
      type="button"
      class="micro"
      :class="{ 'micro--preparation': etat === 'preparation' }"
      :aria-label="t('vocal.enregistrer')"
      :title="t('vocal.enregistrer')"
      :disabled="desactive || etat === 'preparation'"
      data-test="enregistrer-vocal"
      @click="commencer"
    >
      <svg viewBox="0 0 20 20" width="19" height="19" aria-hidden="true"><rect x="7.25" y="2.5" width="5.5" height="9.5" rx="2.75" fill="currentColor" /><path d="M4.75 9.5a5.25 5.25 0 0 0 10.5 0M10 14.75v2.75" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
    </button>
    <span v-else class="vocal" role="status" data-test="vocal-en-cours">
      <button type="button" class="vocal__annuler" :aria-label="t('vocal.annuler')" :title="t('vocal.annuler')" data-test="annuler-vocal" @click="annuler">
        <svg viewBox="0 0 20 20" width="17" height="17" aria-hidden="true"><path d="M4 6h12M8 6V4.5h4V6M6 6l.7 9.2c.1.8.7 1.3 1.5 1.3h3.6c.8 0 1.4-.5 1.5-1.3L14 6M8.5 9v4.5M11.5 9v4.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
      </button>
      <span class="vocal__point" aria-hidden="true" />
      <span class="vocal__ondes" aria-hidden="true"><i v-for="n in 5" :key="n" :style="{ animationDelay: `${n * -0.17}s` }" /></span>
      <span class="vocal__duree">{{ t('vocal.enCours', { duree }) }}</span>
      <button type="button" class="vocal__envoyer" data-test="envoyer-vocal" @click="envoyer">
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3.4 9.2 15.6 3.6c.8-.4 1.6.4 1.2 1.2l-5.6 12.2c-.4.8-1.5.7-1.7-.1L8.4 12a.8.8 0 0 0-.5-.5L3.5 10.9c-.8-.2-.9-1.3-.1-1.7ZM8.3 11.7l3.4-3.4" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        <span class="vocal__envoyer-texte">{{ t('vocal.envoyer') }}</span>
      </button>
    </span>
  </template>
</template>

<style scoped>
/* Le micro : un rond à côté de l'envoi, pas une icône de plus dans la barre de mise en forme. */
.micro {
  display: grid; place-items: center; width: 40px; height: 40px; padding: 0; border: 1px solid var(--line); border-radius: 50%;
  background: transparent; color: var(--ink); cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.micro:hover:not(:disabled) { border-color: var(--action); background: var(--action-soft); color: var(--action-ink); }
.micro:active:not(:disabled) { transform: scale(0.94); }
.micro:disabled { opacity: 0.45; cursor: not-allowed; }
.micro--preparation { cursor: progress; animation: battement 1s ease-in-out infinite; }
.micro:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

/* Pendant l'enregistrement : jeter, la durée qui court, puis envoyer. */
.vocal { display: inline-flex; align-items: center; gap: 10px; height: 40px; padding: 0 3px; border: 1px solid color-mix(in srgb, #e5484d 45%, var(--line)); border-radius: var(--radius-pill); background: color-mix(in srgb, #e5484d 9%, var(--surface)); }
.vocal__annuler { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; }
.vocal__annuler:hover { background: color-mix(in srgb, #e5484d 16%, transparent); color: #e5484d; }
.vocal__point { width: 9px; height: 9px; border-radius: 50%; background: #e5484d; box-shadow: 0 0 0 0 #e5484d80; animation: pulsation 1.4s ease-out infinite; }
.vocal__ondes { display: inline-flex; align-items: center; gap: 2px; height: 18px; }
.vocal__ondes i { width: 3px; height: 100%; border-radius: 2px; background: #e5484d; transform-origin: center; animation: onde 0.9s ease-in-out infinite; }
.vocal__duree { min-width: 6.5ch; font-variant-numeric: tabular-nums; font-size: var(--type-body-sm-size); font-weight: 600; color: var(--ink); }
.vocal__envoyer { display: inline-flex; align-items: center; gap: 8px; height: 32px; padding: 0 14px 0 11px; border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-size: var(--type-body-sm-size); font-weight: 700; cursor: pointer; }
.vocal__envoyer:hover { filter: brightness(1.08); }
.vocal__envoyer:active { transform: scale(0.96); }
.vocal__annuler:focus-visible, .vocal__envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes battement { 50% { opacity: 0.4; } }
@keyframes pulsation { 70% { box-shadow: 0 0 0 7px #e5484d00; } 100% { box-shadow: 0 0 0 0 #e5484d00; } }
@keyframes onde { 0%, 100% { transform: scaleY(0.3); } 50% { transform: scaleY(1); } }
@media (max-width: 720px) {
  .vocal__ondes { display: none; }
  .vocal__envoyer { width: 32px; padding: 0; justify-content: center; }
  .vocal__envoyer-texte { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
}
@media (prefers-reduced-motion: reduce) { .micro--preparation, .vocal__point, .vocal__ondes i { animation: none; } }
</style>
