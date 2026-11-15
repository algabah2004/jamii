<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

const { t, tm, rt } = useI18n()
const cle = 'accueil.fonctionnalites'

/**
 * Les fonctionnalités de Jamii, une carte chacune. Les cartes n'ont pas toutes la même largeur :
 * la grille alterne une grande et une petite, pour que l'œil ne lise pas un tableau.
 * Bleu pour ce qu'on fait (l'action), orange pour ce qu'on prouve (la preuve).
 */
const FONCTIONNALITES = [
  { cle: 'communautes', lien: '/communautes', teinte: 'bleu', taille: 'grande' },
  { cle: 'cercles', lien: '/cercles', teinte: 'orange', taille: 'petite' },
  { cle: 'assistant', lien: '/besoin', teinte: 'orange', taille: 'petite' },
  { cle: 'vitrine', lien: '/vitrine', teinte: 'bleu', taille: 'grande' },
  { cle: 'profil', lien: '/profil', teinte: 'bleu', taille: 'moyenne' },
  { cle: 'messages', lien: '/messages', teinte: 'orange', taille: 'moyenne' },
] as const

const points = (f: string) => (tm(`${cle}.${f}.points`) as string[]).map((p) => rt(p))
</script>

<template>
  <section class="fonctions" aria-labelledby="fonctions-titre" data-test="fonctionnalites">
    <header class="fonctions__entete">
      <p class="fonctions__surtitre">{{ t(`${cle}.surtitre`) }}</p>
      <h2 id="fonctions-titre">{{ t(`${cle}.titre`) }}</h2>
      <p class="fonctions__intro">{{ t(`${cle}.intro`) }}</p>
    </header>

    <ul class="grille">
      <li v-for="f in FONCTIONNALITES" :key="f.cle" :class="['case', `case--${f.taille}`]">
        <RouterLink :to="f.lien" :class="['fonction', `fonction--${f.teinte}`]" :data-test="`fonction-${f.cle}`">
          <!-- Un bout d'interface abstrait par fonctionnalité : décoratif, caché aux lecteurs d'écran. -->
          <span class="fonction__apercu" aria-hidden="true">
            <span v-if="f.cle === 'communautes'" class="mini mini--fil">
              <span class="bulle"><b class="rond">KY</b><span class="traits"><i class="trait trait--fort" style="width: 80%" /><i class="trait" style="width: 55%" /></span><em class="puce">?</em></span>
              <span class="bulle bulle--reponse"><b class="rond">AD</b><span class="traits"><i class="trait trait--fort" style="width: 65%" /><i class="trait" style="width: 85%" /></span><em class="puce puce--pleine">✓</em></span>
            </span>
            <span v-else-if="f.cle === 'cercles'" class="mini mini--cercle">
              <b v-for="(a, i) in ['AD', 'KY', 'FS', 'NW', 'MT']" :key="a" :class="['rond', { 'rond--tour': i === 0 }]" :style="{ '--i': i }">{{ a }}</b>
            </span>
            <span v-else-if="f.cle === 'assistant'" class="mini mini--plan">
              <span class="traits"><i class="trait trait--fort" style="width: 90%" /><i class="trait" style="width: 70%" /><i class="trait" style="width: 80%" /></span>
              <b class="fleche">→</b>
              <span class="liste"><i v-for="n in 3" :key="n"><em class="puce puce--pleine">✓</em><i class="trait" :style="{ width: `${85 - n * 12}%` }" /></i></span>
            </span>
            <span v-else-if="f.cle === 'vitrine'" class="mini mini--fenetre">
              <span class="barre"><i /><i /><i /><em class="puce puce--pleine">{{ t(`${cle}.vitrine.enLigne`) }}</em></span>
              <span class="traits"><i class="trait trait--fort" style="width: 50%" /><i class="trait" style="width: 80%" /><i class="trait" style="width: 65%" /></span>
            </span>
            <span v-else-if="f.cle === 'profil'" class="mini mini--profil">
              <span class="bulle"><b class="rond rond--grand">AD</b><span class="traits"><i class="trait trait--fort" style="width: 60%" /><i class="trait" style="width: 40%" /></span></span>
              <span v-for="l in [78, 54, 36]" :key="l" class="jauge"><i :style="{ width: `${l}%` }" /></span>
            </span>
            <span v-else class="mini mini--messages">
              <span class="message"><i class="trait trait--fort" style="width: 70%" /><i class="trait" style="width: 45%" /></span>
              <span class="message message--moi"><i class="trait trait--fort" style="width: 60%" /></span>
              <b class="cloche">1</b>
            </span>
          </span>

          <span class="fonction__corps">
            <strong class="fonction__titre">{{ t(`${cle}.${f.cle}.titre`) }}</strong>
            <span class="fonction__texte">{{ t(`${cle}.${f.cle}.texte`) }}</span>
            <span class="fonction__points">
              <span v-for="(p, i) in points(f.cle)" :key="i" class="point">{{ p }}</span>
            </span>
            <span class="fonction__lien">{{ t(`${cle}.${f.cle}.lien`) }} <span aria-hidden="true">→</span></span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.fonctions { display: grid; gap: var(--space-8); }
.fonctions__entete { display: grid; gap: var(--space-3); justify-items: start; max-width: 44rem; }
.fonctions__surtitre {
  margin: 0; padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: var(--action-soft); color: var(--action-ink);
  font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em;
}
.fonctions__entete h2 {
  margin: 0; font-family: var(--font-display); font-size: clamp(1.75rem, 3.6vw, 2.5rem);
  line-height: 1.1; letter-spacing: -0.03em; text-wrap: balance;
}
.fonctions__intro { margin: 0; color: var(--ink-muted); font-size: 1.0625rem; line-height: 1.65; }

/* Douze colonnes : 7 + 5, puis 5 + 7, puis 6 + 6. */
.grille { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--space-4); }
.case { display: flex; }
.case--grande { grid-column: span 7; }
.case--petite { grid-column: span 5; }
.case--moyenne { grid-column: span 6; }

.fonction--bleu { --teinte: var(--action); --teinte-ink: var(--action-ink); --teinte-soft: var(--action-soft); }
.fonction--orange { --teinte: var(--proof); --teinte-ink: var(--proof-ink); --teinte-soft: var(--proof-soft); }
.fonction {
  flex: 1; display: grid; grid-template-rows: auto 1fr; overflow: hidden;
  border-radius: var(--radius-lg); border: 1px solid var(--line); background: var(--surface);
  color: var(--ink); text-decoration: none;
  transition: transform var(--motion-base) var(--ease-out), border-color var(--motion-base) var(--ease-out), box-shadow var(--motion-base) var(--ease-out);
}
.fonction:hover {
  transform: translateY(-3px); border-color: color-mix(in srgb, var(--teinte) 55%, var(--line));
  box-shadow: 0 18px 40px color-mix(in srgb, var(--teinte) 16%, transparent);
}
.fonction:focus-visible { outline: 2px solid var(--teinte-ink); outline-offset: 3px; }

.fonction__apercu {
  position: relative; display: grid; place-items: center; min-height: 14rem; padding: var(--space-6);
  background:
    radial-gradient(70% 90% at 90% 0%, color-mix(in srgb, var(--teinte) 34%, transparent), transparent 70%),
    var(--teinte-soft);
}
.fonction__apercu::before {
  content: ''; position: absolute; inset: 0;
  background-image: linear-gradient(color-mix(in srgb, var(--ink) 6%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--ink) 6%, transparent) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(80% 80% at 50% 50%, #000, transparent);
  -webkit-mask-image: radial-gradient(80% 80% at 50% 50%, #000, transparent);
}

/* Les mini interfaces : mêmes briques partout (ronds, traits, puces), assemblées différemment. */
.mini { position: relative; width: min(100%, 20rem); display: grid; gap: var(--space-2); transition: transform var(--motion-base) var(--ease-out); }
.fonction:hover .mini { transform: translateY(-4px); }
.rond {
  flex-shrink: 0; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%;
  background: var(--teinte-soft); color: var(--teinte-ink); font-size: 0.6875rem; font-weight: 700; border: 2px solid var(--surface);
}
.rond--grand { width: 40px; height: 40px; background: var(--teinte); color: var(--midnight); }
.traits { flex: 1; display: grid; gap: 6px; min-width: 0; }
.trait { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 16%, transparent); }
.trait--fort { height: 9px; background: color-mix(in srgb, var(--ink) 50%, transparent); }
.puce {
  flex-shrink: 0; display: grid; place-items: center; min-width: 22px; height: 22px; padding: 0 6px; border-radius: var(--radius-pill);
  background: var(--teinte-soft); color: var(--teinte-ink); font-size: 0.6875rem; font-weight: 700; font-style: normal;
}
.puce--pleine { background: var(--teinte); color: var(--midnight); }
.bulle {
  display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3); border-radius: var(--radius-md);
  background: var(--surface); box-shadow: 0 8px 20px color-mix(in srgb, var(--ink) 12%, transparent);
}
.bulle--reponse { margin-inline-start: var(--space-6); }

/* Le cercle : cinq membres autour, celui dont c'est le tour est plein. */
.mini--cercle { width: 9rem; height: 9rem; border-radius: 50%; border: 2px dashed color-mix(in srgb, var(--teinte) 55%, transparent); }
.mini--cercle .rond {
  position: absolute; left: 50%; top: 50%; width: 36px; height: 36px; margin: -18px;
  transform: rotate(calc(var(--i) * 72deg)) translateY(-4.5rem) rotate(calc(var(--i) * -72deg));
  box-shadow: 0 4px 10px color-mix(in srgb, var(--ink) 14%, transparent);
}
.rond--tour { background: var(--teinte); color: var(--midnight); }

/* L'assistant : un texte libre à gauche, un plan coché à droite. */
.mini--plan { grid-template-columns: 1fr auto 1fr; align-items: center; gap: var(--space-3); padding: var(--space-4); border-radius: var(--radius-md); background: var(--surface); box-shadow: 0 8px 20px color-mix(in srgb, var(--ink) 12%, transparent); }
.fleche { color: var(--teinte-ink); font-size: 1.25rem; }
.liste { display: grid; gap: 6px; }
.liste > i { display: flex; align-items: center; gap: 6px; font-style: normal; }
.liste .trait { flex: 1; }
.liste .puce { min-width: 18px; height: 18px; padding: 0; font-size: 0.625rem; }

/* La vitrine : une fenêtre de navigateur, avec la preuve qu'elle répond. */
.mini--fenetre { padding: var(--space-3) var(--space-4) var(--space-4); border-radius: var(--radius-md); background: var(--surface); box-shadow: 0 8px 20px color-mix(in srgb, var(--ink) 12%, transparent); gap: var(--space-3); }
.barre { display: flex; align-items: center; gap: 5px; }
.barre > i { width: 8px; height: 8px; border-radius: 50%; background: color-mix(in srgb, var(--ink) 20%, transparent); }
.barre .puce { margin-inline-start: auto; }

.mini--profil { padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface); box-shadow: 0 8px 20px color-mix(in srgb, var(--ink) 12%, transparent); }
.mini--profil .bulle { padding: 0; box-shadow: none; }
.jauge { display: block; height: 8px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 10%, transparent); overflow: hidden; }
.jauge i { display: block; height: 100%; border-radius: inherit; background: var(--teinte); }

.mini--messages { width: min(100%, 16rem); }
.message { display: grid; gap: 6px; width: 78%; padding: var(--space-3); border-radius: var(--radius-md) var(--radius-md) var(--radius-md) 4px; background: var(--surface); box-shadow: 0 8px 20px color-mix(in srgb, var(--ink) 12%, transparent); }
.message--moi { justify-self: end; width: 60%; border-radius: var(--radius-md) var(--radius-md) 4px var(--radius-md); background: var(--teinte); }
.message--moi .trait--fort { background: color-mix(in srgb, var(--midnight) 60%, transparent); }
.cloche {
  position: absolute; right: -6px; top: -10px; display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%;
  background: var(--teinte); color: var(--midnight); font-size: 0.75rem; box-shadow: 0 0 0 4px color-mix(in srgb, var(--teinte) 25%, transparent);
}

.fonction__corps { display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); }
.fonction__titre { font-family: var(--font-display); font-size: 1.3125rem; letter-spacing: -0.02em; }
.fonction__texte { color: var(--ink-muted); line-height: 1.6; }
.fonction__points { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-2); }
.point { padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--teinte-soft); color: var(--teinte-ink); font-size: var(--type-body-sm-size); font-weight: 600; }
.fonction__lien { margin-top: var(--space-3); color: var(--teinte-ink); font-weight: 700; }

@media (max-width: 900px) {
  .case--grande, .case--petite, .case--moyenne { grid-column: span 12; }
  .fonction__apercu { min-height: 10rem; }
}
@media (prefers-reduced-motion: reduce) {
  .fonction, .mini { transition: none; }
  .fonction:hover, .fonction:hover .mini { transform: none; }
}
</style>
