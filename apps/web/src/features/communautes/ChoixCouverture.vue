<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { envoyerFichier, type PieceJointe } from '../../components/texte/fichiers'

/*
 * Choisir la couverture d'une communauté : une image à soi, ou le motif de la communauté (par défaut). L'image est
 * envoyée tout de suite (`POST /api/fichiers`, sans conversation ni cercle) ; le parent décide quoi en faire.
 */
defineProps<{ couverture: string | null; discret?: boolean }>()
const emit = defineEmits<{ image: [piece: PieceJointe]; motif: [] }>()
const { t } = useI18n()

const entree = ref<HTMLInputElement | null>(null)
const etat = ref<'repos' | 'envoi' | 'trop_gros' | 'type_refuse' | 'erreur'>('repos')

async function choisir(e: Event) {
  const fichier = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!fichier) return
  if (!fichier.type.startsWith('image/')) return void (etat.value = 'type_refuse')
  etat.value = 'envoi'
  const r = await envoyerFichier(fichier)
  if (!r.ok) return void (etat.value = r.code === 'trop_gros' || r.code === 'type_refuse' ? r.code : 'erreur')
  if (r.piece.sorte !== 'image') return void (etat.value = 'type_refuse')
  etat.value = 'repos'
  emit('image', r.piece)
}
</script>

<template>
  <div class="choix-couverture" :class="{ 'choix-couverture--discret': discret }" data-test="choix-couverture">
    <input ref="entree" type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="visuellement-cache" tabindex="-1" data-test="fichier-couverture" @change="choisir" />
    <button type="button" class="bouton" :disabled="etat === 'envoi'" data-test="mettre-image" @click="entree?.click()">
      <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><rect x="2.5" y="4" width="15" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.6" /><circle cx="7.5" cy="8.5" r="1.5" fill="currentColor" /><path d="m3 14 4.5-4 3.5 3 2.5-2 3.5 3" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
      {{ etat === 'envoi' ? t('communautes.couverture.envoi') : couverture ? t('communautes.couverture.changer') : t('communautes.couverture.mettre') }}
    </button>
    <button v-if="couverture" type="button" class="bouton" data-test="garder-motif" @click="emit('motif')">{{ t('communautes.couverture.motif') }}</button>
    <p v-if="etat === 'trop_gros' || etat === 'type_refuse' || etat === 'erreur'" role="alert" class="erreur">{{ t(`communautes.couverture.${etat}`) }}</p>
  </div>
</template>

<style scoped>
.choix-couverture { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.bouton {
  display: inline-flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 var(--space-3); border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer;
}
.bouton:hover:not(:disabled) { border-color: var(--ink); }
.bouton:disabled { opacity: 0.6; cursor: progress; }
.bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
/* Posé sur la bannière : un verre sombre, lisible sur une photo comme sur un motif. */
.choix-couverture--discret .bouton { border-color: transparent; background: rgb(2 7 10 / 0.62); color: #fff; backdrop-filter: blur(6px); }
.choix-couverture--discret .bouton:hover:not(:disabled) { background: rgb(2 7 10 / 0.8); }
.erreur { flex-basis: 100%; margin: 0; color: var(--danger, #c0392b); font-size: var(--type-body-sm-size); font-weight: 600; }
.choix-couverture--discret .erreur { padding: 4px 10px; border-radius: var(--radius-sm); background: rgb(2 7 10 / 0.75); color: #fff; }
</style>
