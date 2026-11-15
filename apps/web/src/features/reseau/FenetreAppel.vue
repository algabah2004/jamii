<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar } from '../../design-system'
import {
  basculerCamera,
  basculerEcran,
  basculerMicro,
  decrocher,
  fermer,
  partageEcranPossible,
  raccrocher,
  reagir,
  REACTIONS,
  reduire,
  refuser,
  useAppel,
} from './appel'
import { prenom } from './reseau'

/*
 * L'appel en cours (#157), par-dessus toutes les pages : l'appel qui sonne chez moi (une carte en bas de l'écran), et
 * l'appel lui-même (plein écran, ou réduit dans un coin pour continuer à écrire). Le son de l'autre passe par un
 * élément <audio> à part : il continue même quand sa vidéo est cachée ou la fenêtre réduite.
 */
const { t } = useI18n()
const { etat, fluxLocal, fluxDistant } = useAppel()

const videoDistante = ref<HTMLVideoElement | null>(null)
const videoLocale = ref<HTMLVideoElement | null>(null)
const audioDistant = ref<HTMLAudioElement | null>(null)
const menuReactions = ref(false)

watch([fluxDistant, videoDistante, audioDistant], ([flux, video, audio]) => {
  if (video && video.srcObject !== flux) video.srcObject = flux
  if (audio && audio.srcObject !== flux) audio.srcObject = flux
}, { immediate: true, flush: 'post' })
watch([fluxLocal, videoLocale], ([flux, video]) => {
  if (video && video.srcObject !== flux) video.srcObject = flux
}, { immediate: true, flush: 'post' })

const visible = computed(() => etat.phase !== 'repos' && etat.phase !== 'sonne_chez_moi')
const nom = computed(() => (etat.personne ? prenom(etat.personne.nom) : ''))
const imageDistante = computed(() => etat.phase === 'en_cours' && etat.cameraDistante)
const imageLocale = computed(() => etat.camera || etat.ecran)
const ecranPossible = partageEcranPossible()

// La durée de l'appel, à la seconde.
const maintenant = ref(Date.now())
const horloge = setInterval(() => (maintenant.value = Date.now()), 1_000)
onBeforeUnmount(() => clearInterval(horloge))
const duree = computed(() => {
  if (!etat.debut) return ''
  const s = Math.max(0, Math.floor((maintenant.value - etat.debut) / 1000))
  const mm = String(Math.floor(s / 60) % 60).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return s >= 3600 ? `${Math.floor(s / 3600)}:${mm}:${ss}` : `${mm}:${ss}`
})

const statut = computed(() => {
  switch (etat.phase) {
    case 'preparation': return t('appels.preparation')
    case 'sonne_chez_lautre': return t('appels.sonne', { nom: nom.value })
    case 'connexion': return t('appels.connexion')
    case 'en_cours': return duree.value
    case 'fini': return t(`appels.fin.${etat.fin ?? 'termine'}`, { nom: nom.value })
    default: return ''
  }
})

function choisirReaction(emoji: string) {
  reagir(emoji)
  menuReactions.value = false
}

// Échap réduit l'appel plutôt que de le couper : raccrocher reste un geste volontaire.
function clavier(e: KeyboardEvent) {
  if (e.key === 'Escape' && menuReactions.value) menuReactions.value = false
  else if (e.key === 'Escape' && visible.value && !etat.reduit) reduire(true)
}
</script>

<template>
  <Teleport to="body">
    <!-- L'appel qui sonne chez moi. -->
    <div v-if="etat.phase === 'sonne_chez_moi' && etat.personne" class="entrant" role="alertdialog" aria-live="assertive" :aria-label="t('appels.entrant', { nom: etat.personne.nom })" data-test="appel-entrant">
      <JAvatar :nom="etat.personne.nom" :url="etat.personne.avatarUrl" :taille="52" class="entrant__avatar" />
      <div class="entrant__texte">
        <strong>{{ etat.personne.nom }}</strong>
        <span>{{ etat.video ? t('appels.entrantVideo') : t('appels.entrantVocal') }}</span>
      </div>
      <div class="entrant__actions">
        <button type="button" class="rond rond--refus" :aria-label="t('appels.refuser')" :title="t('appels.refuser')" data-test="refuser-appel" @click="refuser">
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M12 9c-2.3 0-4.5.4-6.5 1.2-.7.3-1.1 1-1 1.7l.3 2a1.5 1.5 0 0 0 1.8 1.2l2.3-.5c.6-.1 1-.6 1.1-1.2l.2-1.4a9 9 0 0 1 3.6 0l.2 1.4c.1.6.5 1.1 1.1 1.2l2.3.5a1.5 1.5 0 0 0 1.8-1.2l.3-2c.1-.7-.3-1.4-1-1.7A17 17 0 0 0 12 9Z" fill="currentColor" /></svg>
        </button>
        <button v-if="etat.video" type="button" class="rond rond--accepter-vocal" :aria-label="t('appels.decrocherVocal')" :title="t('appels.decrocherVocal')" data-test="decrocher-vocal" @click="decrocher(false)">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
        </button>
        <button type="button" class="rond rond--accepter" :aria-label="t('appels.decrocher')" :title="t('appels.decrocher')" data-test="decrocher" @click="decrocher()">
          <svg v-if="etat.video" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M4 7a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2.5l3.4-2.3A1 1 0 0 1 21 8v8a1 1 0 0 1-1.6.8L16 14.5V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z" fill="currentColor" /></svg>
          <svg v-else viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
        </button>
      </div>
    </div>

    <!-- L'appel : plein écran, ou réduit dans un coin. -->
    <section
      v-if="visible && etat.personne"
      class="appel"
      :class="{ 'appel--reduit': etat.reduit }"
      role="dialog"
      :aria-label="t('appels.titre', { nom: etat.personne.nom })"
      data-test="fenetre-appel"
      @keydown="clavier"
    >
      <audio ref="audioDistant" autoplay />
      <div class="scene" @click="etat.reduit && reduire(false)">
        <video v-show="imageDistante" ref="videoDistante" class="scene__video" autoplay playsinline muted data-test="video-distante" />
        <div v-if="!imageDistante" class="scene__personne">
          <JAvatar :nom="etat.personne.nom" :url="etat.personne.avatarUrl" :taille="etat.reduit ? 56 : 112" class="scene__avatar" :class="{ 'scene__avatar--sonne': etat.phase === 'sonne_chez_lautre' }" />
          <strong v-if="!etat.reduit">{{ etat.personne.nom }}</strong>
        </div>
        <p class="scene__statut" :class="{ 'scene__statut--fin': etat.phase === 'fini' }" aria-live="polite" data-test="statut-appel">
          <span v-if="etat.phase === 'en_cours' && !etat.microDistant" class="scene__muet" data-test="micro-distant-coupe">
            <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true"><path d="M10 2.5a2.5 2.5 0 0 0-2.5 2.5v4.6l5 5V5A2.5 2.5 0 0 0 10 2.5ZM3 3l14 14M5.5 9.5a4.5 4.5 0 0 0 7 3.7M10 14v3.5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
            {{ t('appels.microCoupeDe', { nom }) }} ·
          </span>
          {{ statut }}
        </p>
        <video v-show="imageLocale && etat.phase !== 'fini'" ref="videoLocale" class="scene__moi" :class="{ 'scene__moi--ecran': etat.ecran }" autoplay playsinline muted data-test="video-locale" />

        <ul class="reactions" aria-live="polite">
          <li v-for="r in etat.reactions" :key="r.id" class="reaction" :class="{ 'reaction--moi': r.deMoi }">
            <span aria-hidden="true">{{ r.emoji }}</span><span class="visuellement-cache">{{ r.deMoi ? t('appels.tuReagis') : t('appels.reagit', { nom }) }} {{ r.emoji }}</span>
          </li>
        </ul>
      </div>

      <div v-if="etat.phase !== 'fini'" class="barre" :aria-label="t('appels.commandes')" role="toolbar">
        <button type="button" class="commande" :class="{ 'commande--coupee': !etat.micro }" :aria-pressed="!etat.micro" :aria-label="etat.micro ? t('appels.couperMicro') : t('appels.activerMicro')" :title="etat.micro ? t('appels.couperMicro') : t('appels.activerMicro')" data-test="basculer-micro" @click="basculerMicro">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true">
            <path d="M10 2.5A2.5 2.5 0 0 0 7.5 5v4.5a2.5 2.5 0 0 0 5 0V5A2.5 2.5 0 0 0 10 2.5ZM5 9.5a5 5 0 0 0 10 0M10 14.5v3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            <path v-if="!etat.micro" d="M3 3l14 14" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
          </svg>
        </button>
        <button type="button" class="commande" :class="{ 'commande--coupee': !etat.camera }" :aria-pressed="etat.camera" :aria-label="etat.camera ? t('appels.eteindreCamera') : t('appels.allumerCamera')" :title="etat.camera ? t('appels.eteindreCamera') : t('appels.allumerCamera')" data-test="basculer-camera" @click="basculerCamera">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true">
            <path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h7A1.5 1.5 0 0 1 13 6.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 3 13.5ZM13 8.5l4-2.3v7.6l-4-2.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
            <path v-if="!etat.camera" d="M3 3l14 14" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
          </svg>
        </button>
        <button v-if="ecranPossible && !etat.reduit" type="button" class="commande" :class="{ 'commande--active': etat.ecran }" :aria-pressed="etat.ecran" :aria-label="etat.ecran ? t('appels.arreterEcran') : t('appels.partagerEcran')" :title="etat.ecran ? t('appels.arreterEcran') : t('appels.partagerEcran')" data-test="basculer-ecran" @click="basculerEcran">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M2.5 4.5A1.5 1.5 0 0 1 4 3h12a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 16 14H4a1.5 1.5 0 0 1-1.5-1.5ZM7 17h6M10 14v3M10 11V6.5M7.8 8.5 10 6.3l2.2 2.2" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <div v-if="!etat.reduit" class="reagir">
          <button type="button" class="commande" :aria-expanded="menuReactions" :aria-label="t('appels.reagir')" :title="t('appels.reagir')" :disabled="etat.phase !== 'en_cours'" data-test="ouvrir-reactions" @click="menuReactions = !menuReactions">
            <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><circle cx="10" cy="10" r="7.5" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="M7 11.8a3.6 3.6 0 0 0 6 0" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /><circle cx="7.5" cy="8" r="1" fill="currentColor" /><circle cx="12.5" cy="8" r="1" fill="currentColor" /></svg>
          </button>
          <div v-if="menuReactions" class="reagir__menu" data-test="reactions">
            <button v-for="e in REACTIONS" :key="e" type="button" class="reagir__choix" :aria-label="t('appels.envoyerReaction', { emoji: e })" @click="choisirReaction(e)">{{ e }}</button>
          </div>
        </div>
        <button type="button" class="commande" :aria-label="etat.reduit ? t('appels.agrandir') : t('appels.reduire')" :title="etat.reduit ? t('appels.agrandir') : t('appels.reduire')" data-test="reduire-appel" @click="reduire(!etat.reduit)">
          <svg v-if="etat.reduit" viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M12 3h5v5M8 17H3v-5M17 3l-5.5 5.5M3 17l5.5-5.5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <svg v-else viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M16 9h-5V4M4 11h5v5M11 9l6-6M9 11l-6 6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <button type="button" class="commande commande--raccrocher" :aria-label="t('appels.raccrocher')" :title="t('appels.raccrocher')" data-test="raccrocher" @click="raccrocher">
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M12 9c-2.3 0-4.5.4-6.5 1.2-.7.3-1.1 1-1 1.7l.3 2a1.5 1.5 0 0 0 1.8 1.2l2.3-.5c.6-.1 1-.6 1.1-1.2l.2-1.4a9 9 0 0 1 3.6 0l.2 1.4c.1.6.5 1.1 1.1 1.2l2.3.5a1.5 1.5 0 0 0 1.8-1.2l.3-2c.1-.7-.3-1.4-1-1.7A17 17 0 0 0 12 9Z" fill="currentColor" /></svg>
        </button>
      </div>
      <div v-else class="barre">
        <button type="button" class="commande commande--texte" data-test="fermer-appel" @click="fermer">{{ t('appels.fermer') }}</button>
      </div>
    </section>
  </Teleport>
</template>

<style scoped>
/* L'appel entrant : une carte en bas de l'écran, au-dessus de tout. */
.entrant {
  position: fixed; z-index: 90; left: 50%; bottom: max(1rem, env(safe-area-inset-bottom)); transform: translateX(-50%);
  display: flex; align-items: center; gap: var(--space-3); width: min(30rem, calc(100vw - 2rem));
  padding: var(--space-3) var(--space-4); border-radius: 22px; background: #14161c; color: #fff;
  box-shadow: 0 18px 50px rgb(0 0 0 / 0.35); animation: arrivee 0.25s ease-out;
}
.entrant__avatar { animation: pulsation 1.6s ease-in-out infinite; border-radius: 50%; }
.entrant__texte { display: grid; flex: 1; min-width: 0; line-height: 1.3; }
.entrant__texte strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.entrant__texte span { color: rgb(255 255 255 / 0.7); font-size: 0.9rem; }
.entrant__actions { display: flex; gap: var(--space-2); }

.rond { display: grid; place-items: center; width: 48px; height: 48px; border: 0; border-radius: 50%; color: #fff; cursor: pointer; }
.rond:focus-visible, .commande:focus-visible { outline: 3px solid #fff; outline-offset: 2px; }
.rond--refus { background: #e5484d; }
.rond--accepter { background: #1f9d55; animation: pulsation 1.6s ease-in-out infinite; }
.rond--accepter-vocal { background: rgb(255 255 255 / 0.16); }

/* L'appel. */
.appel { position: fixed; inset: 0; z-index: 80; display: grid; grid-template-rows: 1fr auto; background: #0d0f14; color: #fff; }
.scene { position: relative; overflow: hidden; display: grid; place-items: center; }
.scene__video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; background: #000; }
.scene__personne { display: grid; justify-items: center; gap: var(--space-3); font-size: 1.35rem; }
.scene__avatar { border-radius: 50%; }
.scene__avatar--sonne { animation: pulsation 1.6s ease-in-out infinite; }
.scene__statut { position: absolute; left: 0; right: 0; bottom: var(--space-4); margin: 0; text-align: center; font-variant-numeric: tabular-nums; color: rgb(255 255 255 / 0.85); text-shadow: 0 1px 6px rgb(0 0 0 / 0.6); }
.scene__statut--fin { color: #fff; font-weight: 600; }
.scene__muet { display: inline-flex; align-items: center; gap: 4px; }
.scene__moi {
  position: absolute; top: var(--space-4); right: var(--space-4); width: min(28vw, 220px); aspect-ratio: 4 / 3; object-fit: cover;
  border-radius: 14px; background: #222; box-shadow: 0 8px 24px rgb(0 0 0 / 0.45); transform: scaleX(-1);
}
.scene__moi--ecran { transform: none; object-fit: contain; }

.reactions { position: absolute; left: 0; right: 0; bottom: 3.5rem; height: 60%; margin: 0; padding: 0; list-style: none; pointer-events: none; }
.reaction { position: absolute; bottom: 0; left: 38%; font-size: 2.6rem; animation: envol 2.6s ease-out forwards; }
.reaction--moi { left: 58%; }

.barre { display: flex; justify-content: center; align-items: center; gap: var(--space-3); padding: var(--space-4) var(--space-4) max(var(--space-4), env(safe-area-inset-bottom)); flex-wrap: wrap; }
.commande { display: grid; place-items: center; width: 52px; height: 52px; border: 0; border-radius: 50%; background: rgb(255 255 255 / 0.14); color: #fff; cursor: pointer; transition: background 0.15s; }
.commande:hover:not(:disabled) { background: rgb(255 255 255 / 0.24); }
.commande:disabled { opacity: 0.45; cursor: default; }
.commande--coupee { background: #fff; color: #14161c; }
.commande--coupee:hover:not(:disabled) { background: #e8e8e8; }
.commande--active { background: #3b82f6; }
.commande--raccrocher { width: 64px; background: #e5484d; border-radius: 26px; }
.commande--raccrocher:hover { background: #d13438 !important; }
.commande--texte { width: auto; padding: 0 var(--space-5); border-radius: 26px; font: inherit; font-weight: 600; }

.reagir { position: relative; }
.reagir__menu { position: absolute; bottom: calc(100% + 10px); left: 50%; transform: translateX(-50%); display: flex; gap: 2px; padding: 6px; border-radius: 30px; background: #1f2229; box-shadow: 0 10px 30px rgb(0 0 0 / 0.4); }
.reagir__choix { width: 44px; height: 44px; border: 0; border-radius: 50%; background: transparent; font-size: 1.5rem; cursor: pointer; transition: transform 0.12s; }
.reagir__choix:hover, .reagir__choix:focus-visible { transform: scale(1.25); background: rgb(255 255 255 / 0.1); }

/* Réduit : une vignette dans un coin, au-dessus de la page, pour continuer à écrire. */
.appel--reduit {
  inset: auto var(--space-4) var(--space-4) auto; width: min(20rem, calc(100vw - 2rem)); height: auto; border-radius: 20px;
  box-shadow: 0 18px 50px rgb(0 0 0 / 0.4); overflow: hidden;
}
.appel--reduit .scene { aspect-ratio: 16 / 10; cursor: pointer; }
.appel--reduit .scene__statut { bottom: var(--space-2); font-size: 0.85rem; }
.appel--reduit .scene__moi { width: 30%; top: var(--space-2); right: var(--space-2); border-radius: 10px; }
.appel--reduit .barre { gap: var(--space-2); padding: var(--space-2) var(--space-2) var(--space-3); }
.appel--reduit .commande { width: 42px; height: 42px; }
.appel--reduit .commande--raccrocher { width: 52px; }

@keyframes pulsation { 0%, 100% { box-shadow: 0 0 0 0 rgb(31 157 85 / 0.55); } 50% { box-shadow: 0 0 0 12px rgb(31 157 85 / 0); } }
@keyframes arrivee { from { opacity: 0; transform: translate(-50%, 20px); } to { opacity: 1; transform: translate(-50%, 0); } }
@keyframes envol { 0% { opacity: 0; transform: translateY(0) scale(0.6); } 15% { opacity: 1; transform: translateY(-20px) scale(1.1); } 100% { opacity: 0; transform: translateY(-260px) scale(1); } }
@media (prefers-reduced-motion: reduce) {
  .entrant, .entrant__avatar, .rond--accepter, .scene__avatar--sonne { animation: none; }
  .reaction { animation: envol 2.6s linear forwards; }
}
@media (max-width: 600px) {
  .scene__moi { width: 32vw; }
  .commande { width: 48px; height: 48px; }
}
</style>
