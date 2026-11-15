<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/*
 * Revenir en arrière, partout dans Jamii, de la même façon : un chevron et le nom de l'endroit où l'on revient, discrets,
 * sans soulignement ; une pastille légère au survol. Ce n'est pas une action de la page : il ne doit pas attirer l'œil.
 */
defineProps<{ to: RouteLocationRaw }>()
</script>

<template>
  <RouterLink :to="to" class="lien-retour" data-test="lien-retour">
    <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true" focusable="false"><path d="M12 4.5 6.5 10l5.5 5.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
    <span><slot /></span>
  </RouterLink>
</template>

<style>
.lien-retour {
  display: inline-flex; align-items: center; gap: 4px; min-height: 34px; padding: 0 var(--space-3) 0 var(--space-2);
  margin-inline-start: calc(-1 * var(--space-2)); border-radius: var(--radius-pill);
  color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; text-decoration: none;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.lien-retour:hover { color: var(--ink); background: color-mix(in srgb, var(--ink) 7%, transparent); text-decoration: none; }
.lien-retour:hover svg { transform: translateX(-2px); }
.lien-retour svg { transition: transform var(--motion-fast) var(--ease-out); }
.lien-retour:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .lien-retour:hover svg { transform: none; } }
</style>
