<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import AccueilSection from './AccueilSection.vue'
import { vApparait } from './apparait'

const { t } = useI18n()
const cle = 'accueil.sectionCercles'

const etapes = computed(() => ['1', '2', '3', '4'].map((n) => ({ titre: t(`${cle}.etapes.${n}.titre`), texte: t(`${cle}.etapes.${n}.texte`) })))

/** L'histoire en trois chapitres : la tradition, ce qu'elle porte vraiment, et ce que Jamii en garde. */
const CHAPITRES = ['tradition', 'esprit', 'jamii'] as const
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
      <div class="histoire" data-test="section-cercles-histoire">
        <div v-apparait class="histoire__entete">
          <p class="histoire__surtitre">{{ t(`${cle}.histoire.surtitre`) }}</p>
          <h3 class="histoire__accroche">{{ t(`${cle}.histoire.accroche`) }}</h3>
        </div>

        <ol class="chapitres">
          <li v-for="(c, i) in CHAPITRES" :key="c" v-apparait="i * 120" class="chapitre">
            <span class="chapitre__numero" aria-hidden="true">{{ String(i + 1).padStart(2, '0') }}</span>
            <strong class="chapitre__titre">{{ t(`${cle}.histoire.chapitres.${c}.titre`) }}</strong>
            <span class="chapitre__texte">{{ t(`${cle}.histoire.chapitres.${c}.texte`) }}</span>
          </li>
        </ol>

        <!-- La phrase à retenir, sur une bande sombre : c'est le cœur de l'idée. -->
        <blockquote v-apparait class="citation" data-test="section-cercles-citation">
          <span class="citation__anneaux" aria-hidden="true"><i /><i /><i /></span>
          <p>{{ t(`${cle}.histoire.citation`) }}</p>
        </blockquote>
      </div>

      <!-- Le tableau des deux côtés : ce qu'on fait dans une tontine, ce que ça devient dans un cercle. -->
      <div class="comparaison" role="table" :aria-label="t(`${cle}.paralleles.titre`)">
        <div class="comparaison__entete" role="row">
          <span role="columnheader" class="visuellement-cache">{{ t(`${cle}.paralleles.idee`) }}</span>
          <span role="columnheader" class="comparaison__colonne">{{ t(`${cle}.paralleles.tontine`) }}</span>
          <span role="columnheader" class="comparaison__colonne comparaison__colonne--cercle">{{ t(`${cle}.paralleles.cercle`) }}</span>
        </div>
        <div v-for="(p, i) in PARALLELES" :key="p" v-apparait="i * 110" class="ligne" role="row" :data-test="`parallele-${p}`">
          <strong role="rowheader" class="ligne__idee">{{ t(`${cle}.paralleles.${p}.titre`) }}</strong>
          <span role="cell" class="ligne__tontine">
            <span class="ligne__ou">{{ t(`${cle}.paralleles.tontine`) }}</span>
            {{ t(`${cle}.paralleles.${p}.tontine`) }}
          </span>
          <span class="ligne__fleche" aria-hidden="true">→</span>
          <span role="cell" class="ligne__cercle">
            <span class="ligne__ou">{{ t(`${cle}.paralleles.cercle`) }}</span>
            {{ t(`${cle}.paralleles.${p}.cercle`) }}
          </span>
        </div>
      </div>
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

/* L'histoire : un titre, trois chapitres côte à côte, puis la phrase à retenir. */
.histoire {
  display: grid; gap: var(--space-8); overflow: hidden;
  border-radius: var(--radius-lg); border: 1px solid color-mix(in srgb, var(--proof) 30%, var(--line));
  background: var(--surface);
}
.histoire__entete { display: grid; gap: var(--space-2); padding: clamp(var(--space-6), 4vw, var(--space-8)) clamp(var(--space-6), 4vw, var(--space-8)) 0; }
.histoire__surtitre { margin: 0; color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.06em; text-transform: uppercase; }
.histoire__accroche {
  margin: 0; max-width: 24ch; font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.125rem);
  line-height: 1.15; letter-spacing: -0.025em; text-wrap: balance;
}

.chapitres {
  list-style: none; margin: 0; padding: 0 clamp(var(--space-6), 4vw, var(--space-8));
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-8);
}
.chapitre { position: relative; display: grid; gap: var(--space-2); align-content: start; padding-top: var(--space-4); }
/* Un trait de la teinte au-dessus de chaque chapitre, plein sur le dernier : c'est là qu'on arrive. */
.chapitre::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; border-radius: 2px; background: color-mix(in srgb, var(--proof) 30%, transparent); }
.chapitre:last-child::before { background: var(--proof); }
.chapitre__numero { font-family: var(--font-display); font-weight: 700; font-size: 1.75rem; line-height: 1; color: var(--proof-ink); font-variant-numeric: tabular-nums; }
.chapitre__titre { font-family: var(--font-display); font-size: 1.1875rem; }
.chapitre__texte { color: var(--ink-muted); line-height: 1.65; }

/* La citation : toujours sombre, quel que soit le thème, avec trois anneaux qui se croisent en filigrane. */
.citation {
  position: relative; isolation: isolate; overflow: hidden; margin: 0;
  padding: clamp(var(--space-6), 5vw, var(--space-12)) clamp(var(--space-6), 4vw, var(--space-8));
  background: var(--midnight);
}
.citation p {
  margin: 0; max-width: 26ch; color: #fff; text-wrap: balance;
  font-family: var(--font-display); font-size: clamp(1.5rem, 3.4vw, 2.375rem); line-height: 1.15; letter-spacing: -0.025em;
}
.citation p::before { content: '« '; color: var(--cantaloupe); }
.citation p::after { content: ' »'; color: var(--cantaloupe); }
.citation__anneaux { position: absolute; inset: 0; z-index: -1; }
.citation__anneaux i {
  position: absolute; top: 50%; right: -4rem; width: 20rem; height: 20rem; margin-top: -10rem; border-radius: 50%;
  border: 2px solid color-mix(in srgb, var(--cantaloupe) 55%, transparent);
  animation: respire 7s ease-in-out infinite;
}
.citation__anneaux i:nth-child(2) { right: 4rem; border-color: color-mix(in srgb, var(--cantaloupe) 32%, transparent); animation-delay: -2.3s; }
.citation__anneaux i:nth-child(3) { right: 12rem; border-color: color-mix(in srgb, var(--cantaloupe) 16%, transparent); animation-delay: -4.6s; }
@keyframes respire { 50% { transform: scale(1.06); } }

/* La comparaison : une ligne par idée, la tontine à gauche, le cercle à droite, une flèche entre les deux. */
.comparaison { display: grid; gap: var(--space-3); }
.comparaison__entete, .ligne { display: grid; grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr) 2.5rem minmax(0, 1.2fr); gap: var(--space-4); align-items: center; }
.comparaison__entete { padding: 0 var(--space-6); grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr) 2.5rem minmax(0, 1.2fr); }
.comparaison__colonne { grid-column: 2; color: var(--ink-muted); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.06em; text-transform: uppercase; }
.comparaison__colonne--cercle { grid-column: 4; color: var(--proof-ink); }
.ligne {
  padding: var(--space-4) var(--space-6); border-radius: var(--radius-lg);
  background: var(--surface); border: 1px solid var(--line);
  transition: border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.ligne:hover { border-color: color-mix(in srgb, var(--proof) 50%, var(--line)); box-shadow: 0 12px 28px color-mix(in srgb, var(--proof) 14%, transparent); }
.ligne__idee { font-family: var(--font-display); font-size: 1.125rem; }
.ligne__tontine { color: var(--ink-muted); line-height: 1.55; }
.ligne__cercle { padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--proof-soft); color: var(--ink); line-height: 1.55; font-weight: 500; }
.ligne__fleche {
  display: grid; place-items: center; width: 2.5rem; height: 2.5rem; border-radius: 50%;
  background: var(--proof); color: var(--midnight); font-weight: 700;
  transition: transform var(--motion-base) var(--ease-out);
}
.ligne:hover .ligne__fleche { transform: translateX(4px); }
/* Sur grand écran, les en-têtes de colonnes suffisent ; sur mobile, chaque cellule redit de quel côté elle est. */
.ligne__ou { display: none; color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em; text-transform: uppercase; }
.visuellement-cache { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }

@media (max-width: 900px) {
  .chapitres { grid-template-columns: minmax(0, 1fr); gap: var(--space-6); }
  .comparaison__entete { display: none; }
  .ligne { grid-template-columns: minmax(0, 1fr); gap: var(--space-3); padding: var(--space-6); }
  .ligne__ou { display: block; margin-bottom: 2px; }
  .ligne__fleche { transform: rotate(90deg); width: 2rem; height: 2rem; }
  .ligne:hover .ligne__fleche { transform: rotate(90deg); }
  .anneau { width: 12rem; height: 12rem; }
  .membre { width: 44px; height: 44px; margin: -22px; transform: rotate(calc(var(--i) * 72deg)) translateY(-6rem) rotate(calc(var(--i) * -72deg)); }
  .citation__anneaux i { opacity: 0.5; }
}
/* Sans animation, le premier membre garde le tour. */
@media (prefers-reduced-motion: reduce) {
  .membre, .citation__anneaux i { animation: none; }
  .membre:first-child { background: var(--proof); color: var(--midnight); border-color: var(--proof); }
  .ligne, .ligne__fleche { transition: none; }
}
</style>
