<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Tache } from './cercles'
import { pointsDeLaColline } from './qg'

/*
 * La colline du tour (inspirée des « hill charts » de Basecamp) : chaque tâche est un point sur une colline.
 * En montée, ce qui reste à découvrir (à faire) ; au sommet, ce qu'on est en train de faire ; en descente, ce qui est
 * livré. Une barre dit « combien » ; la colline dit « où on en est vraiment ». Décorative pour les lecteurs d'écran :
 * le texte qui suit donne les mêmes chiffres.
 */
const props = withDefaults(defineProps<{ taches: Tache[]; compacte?: boolean }>(), { compacte: false })
const { t } = useI18n()

const points = computed(() => pointsDeLaColline(props.taches))
const compte = computed(() => ({
  aFaire: props.taches.filter((x) => x.statut === 'a_faire').length,
  enCours: props.taches.filter((x) => x.statut === 'en_cours').length,
  livrees: props.taches.filter((x) => x.statut === 'livree').length,
}))
</script>

<template>
  <figure class="colline" :class="{ 'colline--compacte': compacte }" data-test="colline">
    <svg viewBox="0 0 200 80" role="img" :aria-label="t('cercles.qg.collineAria', compte)">
      <line x1="100" y1="6" x2="100" y2="74" class="colline__milieu" />
      <path d="M4 74 C 50 74, 60 10, 100 10 S 150 74, 196 74" class="colline__trace" />
      <circle v-for="p in points" :key="p.id" :cx="p.x" :cy="p.y" r="4.2" :class="`colline__point colline__point--${p.statut}`">
        <title>{{ p.titre }}</title>
      </circle>
    </svg>
    <figcaption v-if="!compacte" class="colline__legende">
      <span>{{ t('cercles.qg.monter') }}</span>
      <span>{{ t('cercles.qg.descendre') }}</span>
    </figcaption>
  </figure>
</template>

<style scoped>
.colline { margin: 0; display: grid; gap: var(--space-1); }
.colline svg { width: 100%; height: auto; display: block; overflow: visible; }
.colline__trace { fill: none; stroke: var(--line-strong, var(--line)); stroke-width: 1.4; }
.colline__milieu { stroke: var(--line); stroke-width: 0.8; stroke-dasharray: 2 2; }
.colline__point { stroke: var(--surface); stroke-width: 1.4; }
.colline__point--a_faire { fill: var(--ink-muted); }
.colline__point--en_cours { fill: var(--action); }
.colline__point--livree { fill: var(--proof); }
.colline__legende { display: flex; justify-content: space-between; color: var(--ink-muted); font-size: 0.75rem; font-weight: 600; }
.colline--compacte svg { max-height: 72px; }
</style>
