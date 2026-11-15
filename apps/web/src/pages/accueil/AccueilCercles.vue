<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import AccueilSection from './AccueilSection.vue'
import { vApparait } from './apparait'

const { t } = useI18n()
const cle = 'accueil.sectionCercles'

const etapes = computed(() => ['1', '2', '3', '4'].map((n) => ({ titre: t(`${cle}.etapes.${n}.titre`), texte: t(`${cle}.etapes.${n}.texte`) })))

/** Ce que le cercle garde de la tontine : trois idées, chacune dite des deux côtés. */
const PARALLELES = ['part', 'tour', 'ensemble'] as const
/** Les membres du cercle de l'aperçu : le tour passe de l'un à l'autre. */
const MEMBRES = ['AD', 'KY', 'FS', 'NW', 'MT'] as const
</script>

<template>
  <AccueilSection
    id="section-cercles"
    teinte="orange"
    :surtitre="t(`${cle}.surtitre`)"
    :titre="t(`${cle}.titre`)"
    :intro="t(`${cle}.intro`)"
    :titre-etapes="t(`${cle}.titreEtapes`)"
    :etapes="etapes"
  >
    <template #actions>
      <RouterLink to="/cercles" class="bouton-teinte" data-test="section-cercles-voir">{{ t(`${cle}.voir`) }} →</RouterLink>
      <RouterLink to="/cercles/nouveau" class="lien-teinte">{{ t(`${cle}.creer`) }}</RouterLink>
    </template>

    <!-- L'aperçu : cinq membres en cercle, et le tour qui passe de l'un à l'autre. -->
    <template #apercu>
      <div class="anneau">
        <b v-for="(m, i) in MEMBRES" :key="m" class="membre" :style="{ '--i': i }">{{ m }}</b>
        <span class="anneau__centre">
          <i class="trait trait--fort" style="width: 70%" /><i class="trait" style="width: 90%" /><i class="trait" style="width: 55%" />
        </span>
      </div>
    </template>

    <template #avant>
      <!-- L'histoire : d'où vient l'idée, et pourquoi elle va plus loin que l'argent. -->
      <div v-apparait class="histoire" data-test="section-cercles-histoire">
        <p class="histoire__accroche">{{ t(`${cle}.histoire.accroche`) }}</p>
        <div class="histoire__textes">
          <p>{{ t(`${cle}.histoire.tradition`) }}</p>
          <p>{{ t(`${cle}.histoire.esprit`) }}</p>
          <p>{{ t(`${cle}.histoire.jamii`) }}</p>
        </div>
      </div>

      <ul class="paralleles">
        <li v-for="(p, i) in PARALLELES" :key="p" v-apparait="i * 110" class="parallele" :data-test="`parallele-${p}`">
          <h4>{{ t(`${cle}.paralleles.${p}.titre`) }}</h4>
          <p class="parallele__cote">
            <span class="parallele__ou">{{ t(`${cle}.paralleles.tontine`) }}</span>
            {{ t(`${cle}.paralleles.${p}.tontine`) }}
          </p>
          <p class="parallele__cote parallele__cote--cercle">
            <span class="parallele__ou">{{ t(`${cle}.paralleles.cercle`) }}</span>
            {{ t(`${cle}.paralleles.${p}.cercle`) }}
          </p>
        </li>
      </ul>
    </template>
  </AccueilSection>
</template>

<style scoped>
/* L'anneau : un cercle pointillé, cinq membres autour, le projet du tour au centre. */
.anneau {
  position: relative; width: 15rem; height: 15rem; border-radius: 50%;
  border: 2px dashed color-mix(in srgb, var(--proof) 60%, transparent);
  display: grid; place-items: center;
}
.membre {
  position: absolute; left: 50%; top: 50%; width: 52px; height: 52px; margin: -26px;
  display: grid; place-items: center; border-radius: 50%;
  background: var(--surface); color: var(--proof-ink); font-size: 0.875rem; font-weight: 700;
  border: 2px solid color-mix(in srgb, var(--proof) 45%, transparent);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--ink) 14%, transparent);
  transform: rotate(calc(var(--i) * 72deg)) translateY(-7.5rem) rotate(calc(var(--i) * -72deg));
  /* Le tour passe d'un membre au suivant : deux secondes chacun, dix secondes le cercle entier. */
  animation: tour 10s linear infinite; animation-delay: calc(var(--i) * 2s);
}
@keyframes tour {
  0%, 19% { background: var(--proof); color: var(--midnight); border-color: var(--proof); box-shadow: 0 0 0 8px color-mix(in srgb, var(--proof) 25%, transparent); }
  20%, 100% { background: var(--surface); color: var(--proof-ink); }
}
.anneau__centre {
  width: 7rem; display: grid; gap: 7px; padding: var(--space-4); border-radius: var(--radius-md);
  background: var(--surface); box-shadow: 0 12px 28px color-mix(in srgb, var(--ink) 14%, transparent);
}
.trait { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 16%, transparent); }
.trait--fort { height: 9px; background: color-mix(in srgb, var(--ink) 50%, transparent); }

.histoire {
  display: grid; grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr); gap: var(--space-8); align-items: start;
  padding: clamp(var(--space-6), 4vw, var(--space-8)); border-radius: var(--radius-lg);
  background: var(--proof-soft); border-inline-start: 4px solid var(--proof);
}
.histoire__accroche {
  margin: 0; font-family: var(--font-display); font-size: clamp(1.375rem, 2.6vw, 1.875rem);
  line-height: 1.2; letter-spacing: -0.02em; color: var(--ink); text-wrap: balance;
}
.histoire__textes { display: grid; gap: var(--space-3); }
.histoire__textes p { margin: 0; color: var(--ink); line-height: 1.7; }

.paralleles { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
.parallele {
  display: grid; gap: var(--space-3); align-content: start; padding: var(--space-6);
  border-radius: var(--radius-lg); background: var(--surface); border: 1px solid var(--line);
}
.parallele h4 { margin: 0; font-family: var(--font-display); font-size: 1.1875rem; }
.parallele__cote { margin: 0; display: grid; gap: 2px; color: var(--ink-muted); line-height: 1.55; }
.parallele__cote--cercle { padding: var(--space-3); border-radius: var(--radius-md); background: var(--proof-soft); color: var(--ink); }
.parallele__ou { color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em; text-transform: uppercase; }

@media (max-width: 900px) {
  .histoire, .paralleles { grid-template-columns: 1fr; }
  .anneau { width: 12rem; height: 12rem; }
  .membre { width: 44px; height: 44px; margin: -22px; transform: rotate(calc(var(--i) * 72deg)) translateY(-6rem) rotate(calc(var(--i) * -72deg)); }
}
/* Sans animation, le premier membre garde le tour. */
@media (prefers-reduced-motion: reduce) {
  .membre { animation: none; }
  .membre:first-child { background: var(--proof); color: var(--midnight); border-color: var(--proof); }
}
</style>
