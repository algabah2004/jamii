<script setup lang="ts" generic="T extends string">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * Choix d'un filtre parmi quelques-uns (2 à 4) : des pastilles, une seule active à la fois.
 * `onglets` : la même chose soulignée, pour filtrer un fil (moins de couleur, plus calme).
 * Chaque pastille est un bouton à aria-pressed, dans un groupe nommé pour les lecteurs d'écran.
 * Le repère du choix actif (pastille ou soulignement) glisse d'une option à l'autre.
 */
const props = withDefaults(
  defineProps<{ libelle: string; options: { valeur: T; texte: string }[]; variante?: 'pastilles' | 'onglets' }>(),
  { variante: 'pastilles' },
)
const valeur = defineModel<T>({ required: true })

const groupe = ref<HTMLElement | null>(null)
const repere = ref<{ x: number; y: number; largeur: number; hauteur: number } | null>(null)

/** Place le repère sur le bouton actif (positions lues dans la page, après rendu). */
function placer() {
  const b = groupe.value?.querySelector<HTMLElement>('button[aria-pressed="true"]')
  repere.value = b && b.offsetWidth ? { x: b.offsetLeft, y: b.offsetTop, largeur: b.offsetWidth, hauteur: b.offsetHeight } : null
}
watch([valeur, () => props.options.map((o) => o.texte).join()], () => nextTick(placer))
let observateur: ResizeObserver | undefined
onMounted(() => {
  void nextTick(placer)
  if (typeof ResizeObserver === 'function' && groupe.value) {
    observateur = new ResizeObserver(placer)
    observateur.observe(groupe.value)
  }
})
onBeforeUnmount(() => observateur?.disconnect())
</script>

<template>
  <div ref="groupe" class="j-filtres filtres" :class="[`j-filtres--${variante}`, { 'j-filtres--glisse': repere }]" role="group" :aria-label="libelle">
    <span
      v-if="repere"
      class="j-filtres__repere"
      aria-hidden="true"
      :style="{ transform: `translate(${repere.x}px, ${repere.y}px)`, width: `${repere.largeur}px`, height: variante === 'pastilles' ? `${repere.hauteur}px` : undefined }"
    />
    <button
      v-for="o in options"
      :key="o.valeur"
      type="button"
      :aria-pressed="o.valeur === valeur"
      @click="valeur = o.valeur"
    >
      {{ o.texte }}
    </button>
  </div>
</template>

<style>
.j-filtres { position: relative; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.j-filtres button {
  position: relative; z-index: 1;
  min-height: 36px;
  padding: 0 var(--space-4);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--ink);
  font-size: var(--type-label-size); font-weight: var(--type-label-weight);
  cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-base) var(--ease-out), border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.j-filtres button:hover { border-color: var(--action); }
.j-filtres button:active { transform: scale(0.96); }
.j-filtres button[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }

/* Le repère qui glisse porte la couleur active ; le bouton actif devient transparent au-dessus. */
.j-filtres__repere {
  position: absolute; z-index: 0; left: 0; top: 0;
  border-radius: var(--radius-pill); background: var(--action);
  transition: transform var(--motion-base) var(--ease-pop), width var(--motion-base) var(--ease-pop);
  pointer-events: none;
}
.j-filtres--glisse.j-filtres--pastilles button[aria-pressed='true'] { background: transparent; }

.j-filtres--onglets { gap: var(--space-6); border-bottom: 1px solid var(--line); }
.j-filtres--onglets button {
  min-height: 44px; padding: 0 var(--space-1); margin-bottom: -1px;
  border: 0; border-bottom: 2px solid transparent; border-radius: 0;
  background: none; color: var(--ink-muted);
}
.j-filtres--onglets button:hover { border-color: transparent; color: var(--ink); }
.j-filtres--onglets button[aria-pressed='true'] { background: none; color: var(--ink); border-bottom-color: var(--action); }
/* Onglets : le repère est le soulignement, collé au bas du bouton actif. */
.j-filtres--onglets .j-filtres__repere { top: auto; bottom: -1px; height: 2px; border-radius: 2px; }
.j-filtres--glisse.j-filtres--onglets button[aria-pressed='true'] { border-bottom-color: transparent; }
</style>
