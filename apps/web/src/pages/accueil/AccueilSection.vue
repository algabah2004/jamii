<script setup lang="ts">
/**
 * Une grande section de l'accueil qui explique un module : un texte et un aperçu côte à côte,
 * puis les étapes du parcours, puis ce que chaque module ajoute (emplacement par défaut).
 * La teinte suit la marque : bleu pour l'action (missions), orange pour la preuve (hackathons).
 */
import { vApparait } from './apparait'

defineProps<{
  id: string
  teinte: 'bleu' | 'orange'
  /** L'aperçu à gauche plutôt qu'à droite, pour alterner d'une section à l'autre. */
  inverse?: boolean
  surtitre: string
  titre: string
  intro: string
  titreEtapes: string
  etapes: readonly { titre: string; texte: string }[]
}>()
</script>

<template>
  <section :class="['section', `section--${teinte}`, { 'section--inverse': inverse }]" :aria-labelledby="`${id}-titre`" :data-test="id">
    <div v-apparait class="section__haut">
      <div class="section__texte">
        <p class="section__surtitre">{{ surtitre }}</p>
        <h2 :id="`${id}-titre`">{{ titre }}</h2>
        <p class="section__intro">{{ intro }}</p>
        <div class="section__actions"><slot name="actions" /></div>
      </div>
      <!-- Des bouts d'interface abstraits : décoratifs, cachés aux lecteurs d'écran. -->
      <div class="section__apercu" aria-hidden="true"><slot name="apercu" /></div>
    </div>

    <slot name="avant" />

    <h3 class="section__titre-etapes">{{ titreEtapes }}</h3>
    <ol class="etapes" :data-test="`${id}-etapes`">
      <li v-for="(e, i) in etapes" :key="i" v-apparait="i * 110" class="etape">
        <span class="etape__numero" aria-hidden="true">{{ i + 1 }}</span>
        <strong class="etape__titre">{{ e.titre }}</strong>
        <span class="etape__texte">{{ e.texte }}</span>
      </li>
    </ol>

    <slot />
  </section>
</template>

<style scoped>
/* --teinte pour les aplats, --teinte-ink pour le texte coloré (contraste AA), --teinte-soft pour les fonds. */
.section--bleu { --teinte: var(--action); --teinte-ink: var(--action-ink); --teinte-soft: var(--action-soft); }
.section--orange { --teinte: var(--proof); --teinte-ink: var(--proof-ink); --teinte-soft: var(--proof-soft); }

.section {
  position: relative; isolation: isolate; overflow: hidden;
  display: grid; gap: var(--space-8);
  padding: clamp(var(--space-6), 5vw, var(--space-12));
  border-radius: calc(var(--radius-lg) * 1.5);
  border: 1px solid color-mix(in srgb, var(--teinte) 22%, var(--line));
  background:
    radial-gradient(60% 50% at 100% 0%, color-mix(in srgb, var(--teinte) 22%, transparent), transparent 70%),
    radial-gradient(50% 60% at 0% 100%, color-mix(in srgb, var(--teinte) 12%, transparent), transparent 70%),
    var(--surface);
}
.section--inverse {
  background:
    radial-gradient(60% 50% at 0% 0%, color-mix(in srgb, var(--teinte) 22%, transparent), transparent 70%),
    radial-gradient(50% 60% at 100% 100%, color-mix(in srgb, var(--teinte) 12%, transparent), transparent 70%),
    var(--surface);
}

.section__haut { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr); gap: var(--space-8); align-items: center; }
.section--inverse .section__texte { order: 2; }
.section__texte { display: grid; gap: var(--space-3); justify-items: start; }
.section__surtitre {
  margin: 0; padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: var(--teinte-soft); color: var(--teinte-ink);
  font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em;
}
.section__texte h2 {
  margin: 0; font-family: var(--font-display); font-size: clamp(1.75rem, 3.6vw, 2.5rem);
  line-height: 1.1; letter-spacing: -0.03em; text-wrap: balance;
}
.section__intro { margin: 0; color: var(--ink-muted); font-size: 1.0625rem; line-height: 1.65; max-width: 34rem; }
.section__actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); margin-top: var(--space-3); }

.section__apercu {
  position: relative; display: grid; place-items: center; min-height: 18rem; padding: var(--space-6);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(70% 80% at 70% 20%, color-mix(in srgb, var(--teinte) 35%, transparent), transparent 70%),
    var(--teinte-soft);
}
/* Une grille légère derrière l'aperçu, qui s'efface vers les bords. */
.section__apercu::before {
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  background-image: linear-gradient(color-mix(in srgb, var(--ink) 7%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--ink) 7%, transparent) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(75% 75% at 50% 50%, #000, transparent);
  -webkit-mask-image: radial-gradient(75% 75% at 50% 50%, #000, transparent);
}

/* Les actions des sections : un bouton plein de la teinte, et un lien. */
.section__actions :slotted(.bouton-teinte) {
  display: inline-flex; align-items: center; min-height: 48px; padding: 0 var(--space-6);
  border-radius: var(--radius-pill); background: var(--teinte); color: var(--midnight);
  font-weight: 700; text-decoration: none;
  box-shadow: 0 8px 20px color-mix(in srgb, var(--teinte) 30%, transparent);
  transition: transform var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.section__actions :slotted(.bouton-teinte:hover) { transform: translateY(-1px); box-shadow: 0 12px 26px color-mix(in srgb, var(--teinte) 40%, transparent); }
.section__actions :slotted(.lien-teinte) { color: var(--teinte-ink); font-weight: 600; text-underline-offset: 0.25em; }
.section__actions :slotted(a:focus-visible) { outline: 2px solid var(--teinte-ink); outline-offset: 3px; }

.section__titre-etapes { margin: 0; font-size: 1.125rem; }

/* Les étapes, reliées par un fil de la teinte : en ligne sur grand écran, en colonne sur mobile. */
.etapes {
  position: relative; list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-4);
}
.etapes::before {
  content: ''; position: absolute; top: 1.25rem; left: 1.25rem; right: 1.25rem; height: 2px;
  background: linear-gradient(90deg, var(--teinte), color-mix(in srgb, var(--teinte) 15%, transparent));
}
.etape {
  position: relative; display: grid; gap: var(--space-2); align-content: start;
  padding: 0 var(--space-2) var(--space-2) 0;
}
.etape__numero {
  display: grid; place-items: center; width: 2.5rem; height: 2.5rem; border-radius: 50%;
  background: var(--teinte); color: var(--midnight); font-weight: 700; font-family: var(--font-display);
  box-shadow: 0 0 0 6px var(--surface), 0 0 0 7px color-mix(in srgb, var(--teinte) 30%, transparent);
  margin-bottom: var(--space-2);
}
.etape__titre { font-family: var(--font-display); font-size: 1.0625rem; }
.etape__texte { color: var(--ink-muted); line-height: 1.55; font-size: var(--type-body-sm-size); }

@media (max-width: 900px) {
  .section__haut { grid-template-columns: 1fr; }
  .section--inverse .section__texte { order: 0; }
  .section__apercu { min-height: 15rem; }
  .etapes { grid-template-columns: 1fr; gap: var(--space-5, 1.25rem); }
  .etapes::before { top: 1.25rem; bottom: 1.25rem; left: 1.25rem; right: auto; width: 2px; height: auto;
    background: linear-gradient(180deg, var(--teinte), color-mix(in srgb, var(--teinte) 15%, transparent)); }
  .etape { grid-template-columns: 2.5rem 1fr; column-gap: var(--space-4); row-gap: 2px; }
  .etape__numero { grid-row: span 2; margin-bottom: 0; }
}
/* Téléphone : la page n'a que 16 px de marge, la section ne rajoute pas 24 px de chaque côté (le texte gardait 240 px). */
@media (max-width: 480px) {
  .section { padding: var(--space-6) var(--space-4); gap: var(--space-6); border-radius: var(--radius-lg); }
  .section__apercu { min-height: 12rem; padding: var(--space-4); }
}
</style>
