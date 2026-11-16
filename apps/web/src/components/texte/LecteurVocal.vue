<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BARRES, formeDOnde, jouerSeul, minutes, VITESSES } from './lecteurVocal'

/*
 * Le lecteur d'un message vocal (#157), à la place du lecteur du navigateur : lecture/pause, la forme d'onde du son
 * qui se remplit pendant l'écoute (on clique ou on glisse dessus pour avancer), la durée, la vitesse.
 * Ses couleurs suivent la bulle (currentColor) : il va aussi bien dans ma bulle que dans celle de l'autre.
 * Le son n'est téléchargé qu'une fois, quand le message apparaît à l'écran : le même fichier sert au décodage (forme
 * d'onde, durée) et à la lecture.
 */
const props = defineProps<{ url: string; nom: string }>()
const { t } = useI18n()

const audio = ref<HTMLAudioElement | null>(null)
const racine = ref<HTMLElement | null>(null)
const barres = ref<number[]>(Array.from({ length: BARRES }, (_, i) => 0.25 + 0.2 * Math.abs(Math.sin(i * 1.7))))
const pret = ref(false)
const source = ref<string | null>(null)
const lecture = ref(false)
const duree = ref(0)
const position = ref(0)
const vitesse = ref<(typeof VITESSES)[number]>(1)
const erreur = ref(false)
let adresseLocale: string | null = null
let observateur: IntersectionObserver | null = null
let image = 0

const avancement = computed(() => (duree.value > 0 ? Math.min(1, position.value / duree.value) : 0))
const temps = computed(() => minutes(lecture.value || position.value > 0 ? position.value : duree.value))

async function charger() {
  if (source.value || erreur.value) return
  try {
    const reponse = await fetch(props.url, { credentials: 'same-origin' })
    if (!reponse.ok) throw new Error(String(reponse.status))
    const contenu = await reponse.blob()
    adresseLocale = URL.createObjectURL(contenu)
    source.value = adresseLocale
    try {
      const Contexte = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
      if (Contexte) {
        const contexte = new Contexte()
        const son = await contexte.decodeAudioData(await contenu.arrayBuffer())
        barres.value = formeDOnde(son.getChannelData(0))
        duree.value = son.duration
        void contexte.close()
      }
    } catch {
      /* forme d'onde indisponible : on garde les barres neutres, la lecture marche quand même */
    }
  } catch {
    // Pas de téléchargement (réseau) : le navigateur lira directement l'adresse.
    source.value = props.url
  }
  pret.value = true
}

onMounted(() => {
  if (typeof IntersectionObserver === 'function' && racine.value) {
    observateur = new IntersectionObserver((entrees) => {
      if (entrees.some((e) => e.isIntersecting)) {
        observateur?.disconnect()
        void charger()
      }
    }, { rootMargin: '200px' })
    observateur.observe(racine.value)
  } else void charger()
})

onBeforeUnmount(() => {
  observateur?.disconnect()
  cancelAnimationFrame(image)
  audio.value?.pause()
  if (adresseLocale) URL.revokeObjectURL(adresseLocale)
})

async function basculer() {
  if (!source.value) await charger()
  const a = audio.value
  if (!a) return
  if (!a.paused) return a.pause()
  jouerSeul(a)
  a.playbackRate = vitesse.value
  try {
    await a.play()
  } catch {
    erreur.value = true
  }
}

function suivre() {
  const a = audio.value
  if (!a) return
  position.value = a.currentTime
  if (!a.paused) image = requestAnimationFrame(suivre)
}
function quandJoue() {
  lecture.value = true
  erreur.value = false
  suivre()
}
function quandPause() {
  lecture.value = false
  cancelAnimationFrame(image)
}
function quandFin() {
  quandPause()
  position.value = 0
  if (audio.value) audio.value.currentTime = 0
}
function quandMeta() {
  const d = audio.value?.duration ?? 0
  if (Number.isFinite(d) && d > 0 && !duree.value) duree.value = d
}

function allerA(fraction: number) {
  const a = audio.value
  if (!a || !duree.value) return
  a.currentTime = Math.max(0, Math.min(duree.value, fraction * duree.value))
  position.value = a.currentTime
}
let glisse = false
function cliquer(e: PointerEvent) {
  const zone = (e.currentTarget as HTMLElement).getBoundingClientRect()
  if (zone.width) allerA((e.clientX - zone.left) / zone.width)
}
function appuyer(e: PointerEvent) {
  glisse = true
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
  cliquer(e)
}
function deplacer(e: PointerEvent) {
  if (glisse) cliquer(e)
}
function clavier(e: KeyboardEvent) {
  const pas = e.key === 'ArrowRight' ? 5 : e.key === 'ArrowLeft' ? -5 : 0
  if (e.key === 'Home') allerA(0)
  else if (e.key === 'End') allerA(1)
  else if (pas && duree.value) allerA((position.value + pas) / duree.value)
  else return
  e.preventDefault()
}

function changerVitesse() {
  vitesse.value = VITESSES[(VITESSES.indexOf(vitesse.value) + 1) % VITESSES.length]!
  if (audio.value) audio.value.playbackRate = vitesse.value
}
</script>

<template>
  <div ref="racine" class="vocal" :class="{ 'vocal--lecture': lecture }" data-test="message-vocal">
    <audio
      ref="audio"
      :src="source ?? undefined"
      preload="metadata"
      @play="quandJoue"
      @pause="quandPause"
      @ended="quandFin"
      @loadedmetadata="quandMeta"
    />
    <button type="button" class="vocal__jouer" :aria-label="lecture ? t('vocal.pause') : t('vocal.ecouter')" data-test="jouer-vocal" @click="basculer">
      <svg v-if="lecture" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><rect x="5" y="4" width="3.5" height="12" rx="1.2" fill="currentColor" /><rect x="11.5" y="4" width="3.5" height="12" rx="1.2" fill="currentColor" /></svg>
      <svg v-else viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M6.5 4.3v11.4a.8.8 0 0 0 1.2.7l9-5.7a.8.8 0 0 0 0-1.4l-9-5.7a.8.8 0 0 0-1.2.7Z" fill="currentColor" /></svg>
    </button>
    <div
      class="vocal__onde"
      role="slider"
      tabindex="0"
      :aria-label="t('vocal.position', { nom })"
      aria-valuemin="0"
      :aria-valuemax="Math.round(duree)"
      :aria-valuenow="Math.round(position)"
      :aria-valuetext="`${minutes(position)} / ${minutes(duree)}`"
      @pointerdown="appuyer"
      @pointermove="deplacer"
      @pointerup="glisse = false"
      @pointercancel="glisse = false"
      @keydown="clavier"
    >
      <span
        v-for="(h, i) in barres"
        :key="i"
        class="vocal__barre"
        :class="{ 'vocal__barre--jouee': (i + 0.5) / barres.length <= avancement, 'vocal__barre--attente': !pret }"
        :style="{ height: `${Math.round(h * 100)}%` }"
      />
    </div>
    <span class="vocal__temps" aria-hidden="true">{{ temps }}</span>
    <button type="button" class="vocal__vitesse" :aria-label="t('vocal.vitesse', { v: vitesse })" data-test="vitesse-vocal" @click="changerVitesse">×{{ String(vitesse).replace('.', ',') }}</button>
    <span v-if="erreur" class="visuellement-cache" role="alert">{{ t('vocal.illisible') }}</span>
  </div>
</template>

<style scoped>
.vocal { display: flex; align-items: center; gap: 10px; width: min(19rem, 100%); min-width: 14rem; padding: 2px 0; color: inherit; }
.vocal__jouer {
  display: grid; place-items: center; flex: none; width: 38px; height: 38px; padding: 0 0 0 1px; border: 0; border-radius: 50%;
  background: color-mix(in srgb, currentColor 16%, transparent); color: inherit; cursor: pointer;
  transition: transform var(--motion-fast, 120ms) ease-out, background var(--motion-fast, 120ms) ease-out;
}
.vocal--lecture .vocal__jouer { padding: 0; }
.vocal__jouer:hover { background: color-mix(in srgb, currentColor 24%, transparent); }
.vocal__jouer:active { transform: scale(0.94); }
.vocal__jouer:focus-visible, .vocal__onde:focus-visible, .vocal__vitesse:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
.vocal__onde { display: flex; align-items: center; gap: 2px; flex: 1; height: 30px; min-width: 0; cursor: pointer; touch-action: none; border-radius: 4px; }
.vocal__barre { flex: 1; min-width: 2px; max-width: 4px; border-radius: 2px; background: currentColor; opacity: 0.32; transition: opacity 80ms linear, height 240ms ease-out; }
.vocal__barre--jouee { opacity: 1; }
.vocal__barre--attente { opacity: 0.18; }
.vocal__temps { flex: none; min-width: 2.4em; font-size: 0.8125rem; font-variant-numeric: tabular-nums; opacity: 0.85; }
.vocal__vitesse {
  flex: none; min-width: 34px; padding: 2px 6px; border: 0; border-radius: 999px; background: color-mix(in srgb, currentColor 14%, transparent);
  color: inherit; font: inherit; font-size: 0.75rem; font-weight: 700; font-variant-numeric: tabular-nums; cursor: pointer;
}
@media (prefers-reduced-motion: reduce) { .vocal__barre, .vocal__jouer { transition: none; } }
</style>
