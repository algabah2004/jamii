<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar } from '../../design-system'
import { useSession } from '../../session'
import { partageEcranPossible } from '../reseau/appel'
import {
  basculerCamera,
  basculerEcran,
  basculerMicro,
  fermer,
  quitter,
  reagir,
  REACTIONS,
  reduire,
  useAppelCercle,
} from './appelCercle'

/*
 * L'appel du cercle (#157), par-dessus toutes les pages : une mosaïque de 2 à 6 vignettes (moi et chaque membre
 * présent), et les mêmes commandes que l'appel d'une discussion privée. Le son de chacun passe par son propre <audio>.
 */
const { t } = useI18n()
const { etat, fluxLocal, fluxDistants } = useAppelCercle()
const { moi } = useSession()

const visible = computed(() => etat.phase !== 'repos')
const menuReactions = ref(false)
const ecranPossible = partageEcranPossible()

// Brancher chaque flux sur son élément, sans recréer les éléments à chaque relecture.
const videos = new Map<string, HTMLVideoElement>()
const audios = new Map<string, HTMLAudioElement>()
const videoLocale = ref<HTMLVideoElement | null>(null)
function brancher(carte: Map<string, HTMLMediaElement>, id: string) {
  return (el: Element | ComponentPublicInstance | null) => {
    if (el instanceof HTMLMediaElement) {
      carte.set(id, el)
      const flux = fluxDistants.value[id] ?? null
      if (el.srcObject !== flux) el.srcObject = flux
    } else carte.delete(id)
  }
}
watch(fluxDistants, (tous) => {
  for (const [id, el] of [...videos, ...audios]) {
    const flux = tous[id] ?? null
    if (el.srcObject !== flux) el.srcObject = flux
  }
}, { flush: 'post' })
watch([fluxLocal, videoLocale], ([flux, el]) => {
  if (el && el.srcObject !== flux) el.srcObject = flux
}, { immediate: true, flush: 'post' })

// La durée, à la seconde.
const maintenant = ref(Date.now())
const horloge = setInterval(() => (maintenant.value = Date.now()), 1_000)
onBeforeUnmount(() => clearInterval(horloge))
const duree = computed(() => {
  if (!etat.debut) return ''
  const s = Math.max(0, Math.floor((maintenant.value - etat.debut) / 1000))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})
const nombre = computed(() => etat.participants.length + 1)
const imageLocale = computed(() => etat.camera || etat.ecran)

function choisirReaction(emoji: string) {
  reagir(emoji)
  menuReactions.value = false
}
function clavier(e: KeyboardEvent) {
  if (e.key === 'Escape' && menuReactions.value) menuReactions.value = false
  else if (e.key === 'Escape' && !etat.reduit) reduire(true)
}
</script>

<template>
  <Teleport to="body">
    <section
      v-if="visible"
      class="appel"
      :class="{ 'appel--reduit': etat.reduit }"
      role="dialog"
      :aria-label="t('cercles.appel.titre', { nom: etat.cercleNom })"
      data-test="fenetre-appel-cercle"
      @keydown="clavier"
    >
      <header class="tete">
        <strong>{{ etat.cercleNom }}</strong>
        <span v-if="etat.phase === 'en_cours'" class="tete__infos" aria-live="polite">{{ t('cercles.appel.personnes', { n: nombre }, nombre) }} · {{ duree }}</span>
      </header>

      <p v-if="etat.phase === 'fini'" class="fin" role="status" data-test="fin-appel-cercle">{{ t(`cercles.appel.fin.${etat.fin ?? 'termine'}`) }}</p>
      <p v-else-if="etat.phase === 'preparation'" class="fin" role="status">{{ t('cercles.appel.connexion') }}</p>

      <ul v-else class="mosaique" :class="`mosaique--${Math.min(nombre, 6)}`" @click="etat.reduit && reduire(false)">
        <li class="vignette" data-test="vignette-moi">
          <video v-show="imageLocale" ref="videoLocale" class="vignette__video" :class="{ 'vignette__video--miroir': !etat.ecran }" autoplay playsinline muted />
          <JAvatar v-if="!imageLocale" :nom="moi?.nom ?? ''" :url="moi?.avatarUrl ?? null" :taille="etat.reduit ? 36 : 72" />
          <span class="vignette__nom">{{ t('cercles.appel.moi') }}<span v-if="!etat.micro" class="vignette__muet" :aria-label="t('appels.couperMicro')">🔇</span></span>
        </li>
        <li v-for="p in etat.participants" :key="p.id" class="vignette" :class="{ 'vignette--attente': !p.connecte }" data-test="vignette">
          <audio :ref="brancher(audios, p.id)" autoplay />
          <video v-show="p.camera && p.connecte" :ref="brancher(videos, p.id)" class="vignette__video" autoplay playsinline muted />
          <JAvatar v-if="!(p.camera && p.connecte)" :nom="p.nom" :url="p.avatarUrl" :taille="etat.reduit ? 36 : 72" />
          <span class="vignette__nom">{{ p.nom }}<span v-if="!p.micro" class="vignette__muet" :aria-label="t('appels.microCoupeDe', { nom: p.nom })">🔇</span><span v-if="!p.connecte" class="vignette__etat"> · {{ t('cercles.appel.connexion') }}</span></span>
        </li>
        <li v-if="!etat.participants.length && !etat.reduit" class="seul" data-test="seul">{{ t('cercles.appel.seul') }}</li>
      </ul>

      <ul class="reactions" aria-live="polite">
        <li v-for="r in etat.reactions" :key="r.id" class="reaction" :class="{ 'reaction--moi': r.deMoi }">
          <span aria-hidden="true">{{ r.emoji }}</span>
          <small v-if="!r.deMoi">{{ r.nom }}</small>
          <span class="visuellement-cache">{{ r.deMoi ? t('appels.tuReagis') : t('cercles.appel.reagit', { nom: r.nom }) }} {{ r.emoji }}</span>
        </li>
      </ul>

      <div v-if="etat.phase === 'en_cours'" class="barre" role="toolbar" :aria-label="t('appels.commandes')">
        <button type="button" class="commande" :class="{ 'commande--coupee': !etat.micro }" :aria-pressed="!etat.micro" :aria-label="etat.micro ? t('appels.couperMicro') : t('appels.activerMicro')" :title="etat.micro ? t('appels.couperMicro') : t('appels.activerMicro')" data-test="micro-cercle" @click="basculerMicro">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M10 2.5A2.5 2.5 0 0 0 7.5 5v4.5a2.5 2.5 0 0 0 5 0V5A2.5 2.5 0 0 0 10 2.5ZM5 9.5a5 5 0 0 0 10 0M10 14.5v3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /><path v-if="!etat.micro" d="M3 3l14 14" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg>
        </button>
        <button type="button" class="commande" :class="{ 'commande--coupee': !etat.camera }" :aria-pressed="etat.camera" :aria-label="etat.camera ? t('appels.eteindreCamera') : t('appels.allumerCamera')" :title="etat.camera ? t('appels.eteindreCamera') : t('appels.allumerCamera')" data-test="camera-cercle" @click="basculerCamera">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h7A1.5 1.5 0 0 1 13 6.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 3 13.5ZM13 8.5l4-2.3v7.6l-4-2.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /><path v-if="!etat.camera" d="M3 3l14 14" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg>
        </button>
        <button v-if="ecranPossible && !etat.reduit" type="button" class="commande" :class="{ 'commande--active': etat.ecran }" :aria-pressed="etat.ecran" :aria-label="etat.ecran ? t('appels.arreterEcran') : t('appels.partagerEcran')" :title="etat.ecran ? t('appels.arreterEcran') : t('appels.partagerEcran')" @click="basculerEcran">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M2.5 4.5A1.5 1.5 0 0 1 4 3h12a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 16 14H4a1.5 1.5 0 0 1-1.5-1.5ZM7 17h6M10 14v3M10 11V6.5M7.8 8.5 10 6.3l2.2 2.2" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <div v-if="!etat.reduit" class="reagir">
          <button type="button" class="commande" :aria-expanded="menuReactions" :aria-label="t('appels.reagir')" :title="t('appels.reagir')" :disabled="!etat.participants.length" @click="menuReactions = !menuReactions">
            <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><circle cx="10" cy="10" r="7.5" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="M7 11.8a3.6 3.6 0 0 0 6 0" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /><circle cx="7.5" cy="8" r="1" fill="currentColor" /><circle cx="12.5" cy="8" r="1" fill="currentColor" /></svg>
          </button>
          <div v-if="menuReactions" class="reagir__menu">
            <button v-for="e in REACTIONS" :key="e" type="button" class="reagir__choix" :aria-label="t('appels.envoyerReaction', { emoji: e })" @click="choisirReaction(e)">{{ e }}</button>
          </div>
        </div>
        <button type="button" class="commande" :aria-label="etat.reduit ? t('appels.agrandir') : t('appels.reduire')" :title="etat.reduit ? t('appels.agrandir') : t('appels.reduire')" data-test="reduire-cercle" @click="reduire(!etat.reduit)">
          <svg v-if="etat.reduit" viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M12 3h5v5M8 17H3v-5M17 3l-5.5 5.5M3 17l5.5-5.5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <svg v-else viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M16 9h-5V4M4 11h5v5M11 9l6-6M9 11l-6 6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <button type="button" class="commande commande--raccrocher" :aria-label="t('cercles.appel.quitter')" :title="t('cercles.appel.quitter')" data-test="quitter-appel-cercle" @click="quitter">
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M12 9c-2.3 0-4.5.4-6.5 1.2-.7.3-1.1 1-1 1.7l.3 2a1.5 1.5 0 0 0 1.8 1.2l2.3-.5c.6-.1 1-.6 1.1-1.2l.2-1.4a9 9 0 0 1 3.6 0l.2 1.4c.1.6.5 1.1 1.1 1.2l2.3.5a1.5 1.5 0 0 0 1.8-1.2l.3-2c.1-.7-.3-1.4-1-1.7A17 17 0 0 0 12 9Z" fill="currentColor" /></svg>
        </button>
      </div>
      <div v-else-if="etat.phase === 'fini'" class="barre">
        <button type="button" class="commande commande--texte" @click="fermer">{{ t('appels.fermer') }}</button>
      </div>
    </section>
  </Teleport>
</template>

<style scoped>
.appel { position: fixed; inset: 0; z-index: 80; display: grid; grid-template-rows: auto 1fr auto; background: #0d0f14; color: #fff; }
.tete { display: flex; justify-content: center; align-items: baseline; gap: var(--space-3); padding: var(--space-3) var(--space-4) 0; }
.tete__infos { color: rgb(255 255 255 / 0.7); font-variant-numeric: tabular-nums; font-size: 0.9rem; }
.fin { align-self: center; justify-self: center; margin: 0; padding: var(--space-4); text-align: center; font-weight: 600; }

.mosaique { display: grid; gap: var(--space-2); margin: 0; padding: var(--space-3); list-style: none; min-height: 0; grid-template-columns: minmax(0, 1fr); grid-auto-rows: 1fr; }
.mosaique--2 { grid-template-columns: repeat(2, 1fr); }
.mosaique--3, .mosaique--4 { grid-template-columns: repeat(2, 1fr); }
.mosaique--5, .mosaique--6 { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 640px) {
  .mosaique--2 { grid-template-columns: minmax(0, 1fr); }
  .mosaique--5, .mosaique--6 { grid-template-columns: repeat(2, 1fr); }
}
.vignette { position: relative; display: grid; place-items: center; min-height: 0; overflow: hidden; border-radius: 16px; background: #1b1e26; }
.vignette--attente { opacity: 0.7; }
.vignette__video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; background: #000; }
.vignette__video--miroir { transform: scaleX(-1); }
.vignette__nom { position: absolute; left: var(--space-2); bottom: var(--space-2); max-width: calc(100% - 1rem); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 2px 8px; border-radius: 8px; background: rgb(0 0 0 / 0.55); font-size: 0.85rem; }
.vignette__muet { margin-left: 4px; }
.vignette__etat { color: rgb(255 255 255 / 0.7); }
.seul { display: grid; place-items: center; padding: var(--space-4); color: rgb(255 255 255 / 0.75); text-align: center; }
.mosaique--1 { grid-template-columns: minmax(0, 1fr); grid-template-rows: 1fr auto; }

.reactions { position: fixed; left: 0; right: 0; bottom: 5rem; height: 55%; margin: 0; padding: 0; list-style: none; pointer-events: none; z-index: 81; }
.reaction { position: absolute; bottom: 0; left: 35%; display: grid; justify-items: center; font-size: 2.4rem; animation: envol 2.6s ease-out forwards; }
.reaction small { font-size: 0.75rem; padding: 1px 6px; border-radius: 6px; background: rgb(0 0 0 / 0.5); }
.reaction--moi { left: 60%; }

.barre { display: flex; justify-content: center; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4) max(var(--space-4), env(safe-area-inset-bottom)); flex-wrap: wrap; }
.commande { display: grid; place-items: center; width: 52px; height: 52px; border: 0; border-radius: 50%; background: rgb(255 255 255 / 0.14); color: #fff; cursor: pointer; }
.commande:hover:not(:disabled) { background: rgb(255 255 255 / 0.24); }
.commande:disabled { opacity: 0.45; cursor: default; }
.commande:focus-visible { outline: 3px solid #fff; outline-offset: 2px; }
.commande--coupee { background: #fff; color: #14161c; }
.commande--active { background: #3b82f6; }
.commande--raccrocher { width: 64px; border-radius: 26px; background: #e5484d; }
.commande--texte { width: auto; padding: 0 var(--space-5); border-radius: 26px; font: inherit; font-weight: 600; }
.reagir { position: relative; }
.reagir__menu { position: absolute; bottom: calc(100% + 10px); left: 50%; transform: translateX(-50%); display: flex; gap: 2px; padding: 6px; border-radius: 30px; background: #1f2229; box-shadow: 0 10px 30px rgb(0 0 0 / 0.4); }
.reagir__choix { width: 44px; height: 44px; border: 0; border-radius: 50%; background: transparent; font-size: 1.5rem; cursor: pointer; }
.reagir__choix:hover, .reagir__choix:focus-visible { background: rgb(255 255 255 / 0.1); }

/* Réduit : une vignette dans un coin, pour continuer à lire le salon. */
.appel--reduit { inset: auto var(--space-4) var(--space-4) auto; width: min(22rem, calc(100vw - 2rem)); height: auto; max-height: 60vh; border-radius: 20px; box-shadow: 0 18px 50px rgb(0 0 0 / 0.4); overflow: hidden; }
.appel--reduit .mosaique { cursor: pointer; grid-auto-rows: 90px; }
.appel--reduit .commande { width: 42px; height: 42px; }
.appel--reduit .commande--raccrocher { width: 52px; }
.appel--reduit .barre { gap: var(--space-2); padding: var(--space-2); }

@keyframes envol { 0% { opacity: 0; transform: translateY(0) scale(0.6); } 15% { opacity: 1; transform: translateY(-20px) scale(1.1); } 100% { opacity: 0; transform: translateY(-260px); } }
@media (prefers-reduced-motion: reduce) { .reaction { animation: envol 2.6s linear forwards; } }
</style>
