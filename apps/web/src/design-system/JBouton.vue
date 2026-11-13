<script setup lang="ts">
/**
 * Bouton de la charte. `principal` : la seule action bleue d'un écran. `secondaire` : bord line-strong.
 * `discret` : texte d'action sans fond. Avec `href`, c'est un lien qui a l'air d'un bouton (navigation).
 * `occupe` désactive le bouton et l'annonce (aria-busy) pendant un envoi.
 */
withDefaults(
  defineProps<{
    variante?: 'principal' | 'secondaire' | 'discret'
    type?: 'button' | 'submit' | 'reset'
    href?: string
    occupe?: boolean
    desactive?: boolean
  }>(),
  { variante: 'principal', type: 'button', href: undefined, occupe: false, desactive: false },
)
</script>

<template>
  <a v-if="href" :href="href" class="j-bouton" :class="`j-bouton--${variante}`"><slot /></a>
  <button
    v-else
    :type="type"
    class="j-bouton"
    :class="`j-bouton--${variante}`"
    :disabled="desactive || occupe"
    :aria-busy="occupe || undefined"
  >
    <slot />
  </button>
</template>

<style>
.j-bouton {
  display: inline-flex; align-items: center; justify-content: center; gap: var(--space-2);
  min-height: 44px; /* cible tactile WCAG 2.5.5 */
  padding: var(--space-2) var(--space-4);
  border: 1px solid transparent;
  border-radius: var(--radius-pill); /* capsule, comme les commandes du Liquid Glass */
  font-family: var(--font-text);
  font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight);
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out),
    box-shadow var(--motion-base) var(--ease-out), transform var(--motion-base) var(--ease-pop), opacity var(--motion-base) var(--ease-out);
}
/* L'appui répond : le bouton s'enfonce un instant, puis revient avec un petit rebond. */
.j-bouton:active:not(:disabled) { transform: scale(0.96); transition-duration: var(--motion-fast); }
.j-bouton--principal:hover:not(:disabled) { box-shadow: 0 6px 18px color-mix(in srgb, var(--action) 35%, transparent); }
.j-bouton:disabled { cursor: not-allowed; opacity: 0.55; }
.j-bouton[aria-busy='true'] { cursor: progress; }

/* Bleu en fond, texte midnight : 6,3:1. */
.j-bouton--principal { background: var(--action); color: var(--on-action); }
.j-bouton--principal:hover:not(:disabled) { background: color-mix(in srgb, var(--action) 88%, var(--ink)); }

.j-bouton--secondaire { background: var(--surface); color: var(--ink); border-color: var(--line-strong); }
.j-bouton--secondaire:hover:not(:disabled) { background: var(--surface-raised); }

.j-bouton--discret { background: transparent; color: var(--action-ink); padding-inline: var(--space-2); }
.j-bouton--discret:hover:not(:disabled) { background: var(--action-soft); }
</style>
