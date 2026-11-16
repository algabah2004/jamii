<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { tailleLisible, type PieceJointe } from './fichiers'

/*
 * Les pièces jointes d'un message ou d'une publication : les images en grille (une seule : en grand), les vidéos avec
 * leur lecteur, les documents en petites cartes à ouvrir ou télécharger. Les fichiers sont servis par l'API avec la
 * session (même origine) ; une image s'ouvre en grand dans un nouvel onglet.
 */
const props = withDefaults(defineProps<{ pieces: PieceJointe[]; compact?: boolean }>(), { compact: false })
const { t, locale } = useI18n()

const images = computed(() => props.pieces.filter((p) => p.sorte === 'image'))
const videos = computed(() => props.pieces.filter((p) => p.sorte === 'video'))
const documents = computed(() => props.pieces.filter((p) => p.sorte === 'document'))
const sons = computed(() => props.pieces.filter((p) => p.sorte === 'audio'))
const extension = (nom: string) => (nom.includes('.') ? nom.split('.').pop()!.slice(0, 4).toUpperCase() : 'FICHIER')
</script>

<template>
  <div v-if="pieces.length" class="pieces" :class="{ 'pieces--compact': compact }" data-test="pieces-jointes">
    <div v-if="images.length" class="pieces__images" :class="`pieces__images--${Math.min(images.length, 4)}`">
      <a v-for="p in images.slice(0, 4)" :key="p.id" :href="p.url" target="_blank" rel="noopener" class="pieces__image" :title="p.nom">
        <img :src="p.url" :alt="p.nom" loading="lazy" decoding="async" />
        <span v-if="images.length > 4 && p === images[3]" class="pieces__plus" aria-hidden="true">+{{ images.length - 4 }}</span>
      </a>
    </div>
    <div v-for="p in sons" :key="p.id" class="pieces__son" data-test="message-vocal">
      <span class="pieces__micro" aria-hidden="true">
        <svg viewBox="0 0 20 20" width="16" height="16"><path d="M10 2.5A2.5 2.5 0 0 0 7.5 5v4.5a2.5 2.5 0 0 0 5 0V5A2.5 2.5 0 0 0 10 2.5ZM5 9.5a5 5 0 0 0 10 0M10 14.5v3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
      </span>
      <audio :src="p.url" controls preload="metadata" :aria-label="t('vocal.message')" />
    </div>
    <video v-for="p in videos" :key="p.id" class="pieces__video" :src="p.url" controls preload="metadata" playsinline :aria-label="p.nom" />
    <ul v-if="documents.length" class="pieces__documents">
      <li v-for="p in documents" :key="p.id">
        <a :href="p.url" target="_blank" rel="noopener" class="document" :download="p.type.startsWith('application/pdf') || p.type.startsWith('text/') ? undefined : p.nom">
          <span class="document__type" aria-hidden="true">{{ extension(p.nom) }}</span>
          <span class="document__texte">
            <span class="document__nom">{{ p.nom }}</span>
            <span class="document__taille">{{ tailleLisible(p.taille, locale) }} · {{ t('fichiers.ouvrir') }}</span>
          </span>
        </a>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.pieces { display: grid; gap: var(--space-2); max-width: 36rem; }
.pieces__images { display: grid; gap: 4px; border-radius: var(--radius-md); overflow: hidden; }
.pieces__images--2 { grid-template-columns: 1fr 1fr; }
.pieces__images--3 { grid-template-columns: 2fr 1fr; grid-template-rows: 1fr 1fr; }
.pieces__images--3 .pieces__image:first-child { grid-row: span 2; }
.pieces__images--4 { grid-template-columns: 1fr 1fr; }
.pieces__image { position: relative; display: block; background: var(--surface-raised); min-height: 0; }
.pieces__image img { display: block; width: 100%; height: 100%; max-height: 22rem; object-fit: cover; }
.pieces__images--1 .pieces__image img { height: auto; max-height: 26rem; object-fit: contain; background: var(--surface-raised); }
.pieces__images--2 .pieces__image img, .pieces__images--4 .pieces__image img { aspect-ratio: 1; }
.pieces__image:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.pieces__plus { position: absolute; inset: 0; display: grid; place-items: center; background: rgb(2 7 10 / 0.55); color: #fff; font-size: 1.5rem; font-weight: 700; }
.pieces__son { display: flex; align-items: center; gap: var(--space-2); max-width: 22rem; }
.pieces__micro { display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 50%; background: var(--action-soft); color: var(--action); }
.pieces__son audio { width: 100%; min-width: 12rem; height: 40px; }
.pieces__video { width: 100%; max-height: 26rem; border-radius: var(--radius-md); background: #02070A; }
.pieces__documents { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.document { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); text-decoration: none; max-width: 24rem; }
.document:hover { border-color: var(--ink-muted); }
.document:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.document__type { display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: var(--radius-sm); background: var(--surface-raised); color: var(--ink-muted); font-size: 0.6875rem; font-weight: 800; letter-spacing: 0.3px; }
.document__texte { display: grid; min-width: 0; }
.document__nom { font-weight: 600; font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.document__taille { color: var(--ink-muted); font-size: 0.75rem; }
.pieces--compact .pieces__image img { max-height: 12rem; }
</style>
