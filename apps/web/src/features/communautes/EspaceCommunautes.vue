<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { components } from '../../api/generated/communautes'
import BarreCommunaute from './BarreCommunaute.vue'

type Communaute = components['schemas']['Communaute']

/**
 * Le cadre de l'espace d'une communauté, sur toute la largeur de l'écran :
 * - à gauche, la barre de la communauté (changer de communauté, le fil, les membres, les salons, « bien démarrer ») ;
 * - au centre, ce qu'on regarde ;
 * - à droite, sur grand écran, ce qui se passe en direct.
 * Sur téléphone : une colonne, la barre devient une rangée qui défile en haut.
 */
defineProps<{ actuelle?: string; actif?: string; communaute?: Communaute | null }>()
const { t } = useI18n()
</script>

<template>
  <div class="espace" :class="{ 'espace--cote': $slots.contexte }">
    <BarreCommunaute v-if="actuelle" :slug="actuelle" :actif="actif ?? 'fil'" :communaute="communaute" class="espace__barre" />
    <div class="espace__principal" :aria-label="t('communautes.espace.fil')"><slot /></div>
    <aside v-if="$slots.contexte" class="espace__cote"><slot name="contexte" /></aside>
  </div>
</template>

<style scoped>
.espace {
  --haut-espace: calc(64px + var(--space-6));
  display: grid; gap: var(--space-4); align-items: start;
  grid-template-columns: 17rem minmax(0, 1fr);
  margin-top: var(--space-4);
}
.espace--cote { grid-template-columns: 17rem minmax(0, 1fr) 20rem; }
.espace > * { min-width: 0; }
.espace__barre { position: sticky; top: var(--haut-espace); height: calc(100dvh - var(--haut-espace) - var(--space-4)); }
.espace__principal { display: grid; gap: var(--space-4); min-height: calc(100dvh - var(--haut-espace) - var(--space-4)); align-content: start; }
.espace__cote { position: sticky; top: var(--haut-espace); display: grid; gap: var(--space-4); max-height: calc(100dvh - var(--haut-espace) - var(--space-4)); overflow-y: auto; scrollbar-width: thin; }

@media (max-width: 1360px) {
  .espace--cote { grid-template-columns: 17rem minmax(0, 1fr); }
  .espace__cote { display: none; }
}
@media (max-width: 860px) {
  .espace, .espace--cote { grid-template-columns: 1fr; margin-top: var(--space-2); }
  .espace__barre { position: static; height: auto; }
  .espace__principal { min-height: 0; }
}
</style>
