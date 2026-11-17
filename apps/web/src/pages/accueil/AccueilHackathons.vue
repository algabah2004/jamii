<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import AccueilSection from './AccueilSection.vue'
import { vApparait } from './apparait'

const { t } = useI18n()
const cle = 'accueil.sectionHackathons'

const etapes = computed(() => ['1', '2', '3', '4'].map((n) => ({ titre: t(`${cle}.etapes.${n}.titre`), texte: t(`${cle}.etapes.${n}.texte`) })))

/** Le même outil sert à trois formats : seuls le calendrier, le lieu et les prix changent. */
const FORMATS = ['hackathon', 'challenge', 'concours'] as const

/** L'aperçu : les critères pondérés que le jury note, et le podium des résultats. */
const CRITERES = [82, 64, 48] as const
</script>

<template>
  <AccueilSection
    id="section-hackathons"
    teinte="orange"
    inverse
    :surtitre="t(`${cle}.surtitre`)"
    :titre="t(`${cle}.titre`)"
    :intro="t(`${cle}.intro`)"
    :titre-etapes="t(`${cle}.titreEtapes`)"
    :etapes="etapes"
  >
    <template #actions>
      <RouterLink to="/hackathons" class="bouton-teinte" data-test="section-hackathons-voir">{{ t(`${cle}.voir`) }} →</RouterLink>
      <RouterLink to="/hackathons/nouveau" class="lien-teinte">{{ t(`${cle}.organiser`) }}</RouterLink>
    </template>

    <template #apercu>
      <div class="tableau">
        <span class="tableau__entete">
          <span class="equipe"><span v-for="a in ['KY', 'FS', 'CO']" :key="a" class="pastille-avatar">{{ a }}</span></span>
          <b class="puce">{{ t(`${cle}.apercu.equipe`) }}</b>
        </span>
        <span class="podium">
          <span v-for="(h, i) in [64, 92, 46]" :key="i" class="podium__marche" :style="{ height: `${h}%` }"><b>{{ [2, 1, 3][i] }}</b></span>
        </span>
      </div>
      <span class="jury">
        <b class="jury__titre">{{ t(`${cle}.apercu.jury`) }}</b>
        <span v-for="(c, i) in CRITERES" :key="i" class="jury__critere"><i :style="{ width: `${c}%` }" /></span>
      </span>
    </template>

    <ul class="formats" :aria-label="t(`${cle}.formats.titre`)">
      <li v-for="(f, i) in FORMATS" :key="f" v-apparait="i * 110" class="format" :data-test="`format-${f}`">
        <span class="format__signe" aria-hidden="true">{{ t(`${cle}.formats.${f}.signe`) }}</span>
        <h4>{{ t(`${cle}.formats.${f}.titre`) }}</h4>
        <p>{{ t(`${cle}.formats.${f}.texte`) }}</p>
      </li>
    </ul>

    <p v-apparait class="prix" data-test="section-hackathons-prix">
      <strong>{{ t(`${cle}.prix.titre`) }}</strong> {{ t(`${cle}.prix.texte`) }}
    </p>
  </AccueilSection>
</template>

<style scoped>
.tableau {
  position: relative; width: min(100%, 22rem); display: grid; gap: var(--space-4); padding: var(--space-4);
  border-radius: var(--radius-md); background: var(--surface);
  box-shadow: 0 18px 40px color-mix(in srgb, var(--ink) 16%, transparent);
  transform: rotate(1.5deg);
}
.tableau__entete { display: flex; align-items: center; justify-content: space-between; }
.equipe { display: flex; }
.pastille-avatar {
  display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%;
  background: var(--proof-soft); color: var(--proof-ink); font-size: 0.75rem; font-weight: 700; border: 2px solid var(--surface);
}
.pastille-avatar + .pastille-avatar { margin-inline-start: -10px; }
.puce { padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--proof); color: var(--midnight); font-size: 0.75rem; font-weight: 700; }
.podium { display: grid; grid-template-columns: repeat(3, 1fr); align-items: end; gap: var(--space-2); height: 7rem; }
.podium__marche {
  display: grid; place-items: start center; padding-top: var(--space-1); border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  background: color-mix(in srgb, var(--proof) 28%, var(--surface)); color: var(--proof-ink); font-size: 0.8125rem;
}
.podium__marche:nth-child(2) { background: var(--proof); color: var(--midnight); }
.podium__marche { transform-origin: bottom; animation: monte-marche 900ms var(--ease-out) both; }
.podium__marche:nth-child(1) { animation-delay: 150ms; }
.podium__marche:nth-child(3) { animation-delay: 300ms; }
@keyframes monte-marche { from { transform: scaleY(0.15); opacity: 0; } }
@keyframes flotte { 50% { transform: rotate(-3deg) translateY(-5px); } }
.jury { animation: flotte 4.5s ease-in-out infinite; }
/* Une petite carte posée sur le tableau : les critères que le jury note. */
.jury {
  position: relative; z-index: 1; justify-self: end; margin: calc(-1 * var(--space-6)) 4% 0 0; width: 9.5rem; display: grid; gap: 6px;
  padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--ink) 16%, transparent);
  transform: rotate(-3deg);
}
.jury__titre { font-size: 0.75rem; color: var(--ink); }
.jury__critere { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 12%, transparent); overflow: hidden; }
.jury__critere i { display: block; height: 100%; border-radius: inherit; background: var(--proof); }

.formats { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
.format {
  display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6);
  border-radius: var(--radius-lg); background: var(--surface); border: 1px solid var(--line);
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.format:hover { transform: translateY(-2px); border-color: color-mix(in srgb, var(--proof) 50%, var(--line)); box-shadow: 0 12px 28px color-mix(in srgb, var(--proof) 14%, transparent); }
.format__signe {
  justify-self: start; min-width: 3rem; padding: var(--space-1) var(--space-3); border-radius: var(--radius-md);
  background: var(--proof-soft); color: var(--proof-ink); font-family: var(--font-display); font-weight: 700; text-align: center;
}
.format h4 { margin: 0; font-family: var(--font-display); font-size: 1.1875rem; }
.format p { margin: 0; color: var(--ink-muted); line-height: 1.55; }

.prix {
  margin: 0; padding: var(--space-4) var(--space-6); border-radius: var(--radius-md);
  background: var(--proof-soft); color: var(--ink); line-height: 1.6;
  border-inline-start: 4px solid var(--proof);
}

@media (max-width: 900px) { .formats { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 560px) {
  .tableau { transform: none; }
}
@media (prefers-reduced-motion: reduce) { .format { transition: none; } .format:hover { transform: none; } .podium__marche, .jury { animation: none; } }
</style>
