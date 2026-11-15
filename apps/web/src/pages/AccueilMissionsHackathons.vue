<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

const { t } = useI18n()

/**
 * Les deux modules où l'on travaille avec d'autres : une carte chacun, avec un aperçu abstrait,
 * les trois temps du parcours et deux actions. Missions en bleu (l'action), hackathons en orange (la preuve).
 */
const MODULES = [
  { cle: 'missions', teinte: 'bleu', lien: '/missions', creer: '/missions/nouvelle' },
  { cle: 'hackathons', teinte: 'orange', lien: '/hackathons', creer: '/hackathons/nouveau' },
] as const
const ETAPES = ['1', '2', '3'] as const
</script>

<template>
  <section class="vedette" aria-labelledby="vedette-titre" data-test="missions-hackathons">
    <header class="vedette__entete">
      <p class="vedette__surtitre">{{ t('accueil.vedette.surtitre') }}</p>
      <h2 id="vedette-titre">{{ t('accueil.vedette.titre') }}</h2>
      <p class="vedette__intro">{{ t('accueil.vedette.intro') }}</p>
    </header>

    <ul class="vedette__cartes">
      <li v-for="m in MODULES" :key="m.cle">
        <article :class="['carte-module', `carte-module--${m.teinte}`]" :aria-labelledby="`vedette-${m.cle}`" :data-test="`vedette-${m.cle}`">
          <!-- L'aperçu : des bouts d'interface abstraits, sans faux contenu. -->
          <div class="carte-module__apercu" aria-hidden="true">
            <div v-if="m.cle === 'missions'" class="mini">
              <span class="mini__ligne">
                <span class="mini__avatar">AD</span>
                <span class="mini__textes"><i class="trait trait--fort" style="width: 70%" /><i class="trait" style="width: 45%" /></span>
                <span class="mini__pastille">{{ t('accueil.vedette.missions.pastille') }}</span>
              </span>
              <span class="jalons">
                <span v-for="(etat, i) in ['fait', 'fait', 'en-cours', 'a-venir']" :key="i" :class="['jalon', `jalon--${etat}`]">
                  <b>{{ etat === 'fait' ? '✓' : i + 1 }}</b><i class="trait" :style="{ width: `${60 + (i % 2) * 20}%` }" />
                </span>
              </span>
            </div>
            <div v-else class="mini">
              <span class="equipe">
                <span v-for="a in ['KY', 'FS', 'CO', '+2']" :key="a" class="mini__avatar">{{ a }}</span>
                <span class="mini__pastille">{{ t('accueil.vedette.hackathons.pastille') }}</span>
              </span>
              <span class="podium">
                <span v-for="(h, i) in [62, 88, 46]" :key="i" class="podium__marche" :style="{ height: `${h}%` }"><b>{{ [2, 1, 3][i] }}</b></span>
              </span>
            </div>
          </div>

          <div class="carte-module__corps">
            <p class="carte-module__pour">{{ t(`accueil.vedette.${m.cle}.pour`) }}</p>
            <h3 :id="`vedette-${m.cle}`">{{ t(`accueil.vedette.${m.cle}.titre`) }}</h3>
            <p class="carte-module__texte">{{ t(`accueil.vedette.${m.cle}.texte`) }}</p>
            <ol class="carte-module__etapes">
              <li v-for="e in ETAPES" :key="e">
                <strong>{{ t(`accueil.vedette.${m.cle}.etapes.${e}.titre`) }}</strong>
                <span>{{ t(`accueil.vedette.${m.cle}.etapes.${e}.texte`) }}</span>
              </li>
            </ol>
            <div class="carte-module__actions">
              <RouterLink :to="m.lien" class="carte-module__bouton" :data-test="`vedette-${m.cle}-voir`">{{ t(`accueil.vedette.${m.cle}.voir`) }} →</RouterLink>
              <RouterLink :to="m.creer" class="carte-module__lien">{{ t(`accueil.vedette.${m.cle}.creer`) }}</RouterLink>
            </div>
          </div>
        </article>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.vedette { display: grid; gap: var(--space-8); }
.vedette__entete { display: grid; gap: var(--space-2); max-width: 44rem; }
.vedette__entete h2 { margin: 0; }
.vedette__surtitre { margin: 0; color: var(--action-ink); font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.06em; text-transform: uppercase; }
.vedette__intro { margin: 0; color: var(--ink-muted); font-size: 1.0625rem; line-height: 1.6; }

.vedette__cartes { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-6); grid-template-columns: repeat(2, minmax(0, 1fr)); }
.vedette__cartes > li { display: flex; }

/* Chaque carte a sa teinte : --teinte pour les accents, --teinte-ink pour le texte (contraste AA), --teinte-soft pour le fond. */
.carte-module--bleu { --teinte: var(--action); --teinte-ink: var(--action-ink); --teinte-soft: var(--action-soft); }
.carte-module--orange { --teinte: var(--proof); --teinte-ink: var(--proof-ink); --teinte-soft: var(--proof-soft); }

.carte-module {
  position: relative; flex: 1; display: grid; grid-template-rows: auto 1fr; overflow: hidden;
  border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface);
  transition: transform var(--motion-base) var(--ease-out), box-shadow var(--motion-base) var(--ease-out), border-color var(--motion-base) var(--ease-out);
}
.carte-module:hover, .carte-module:focus-within {
  transform: translateY(-3px); border-color: color-mix(in srgb, var(--teinte) 55%, var(--line));
  box-shadow: 0 18px 40px color-mix(in srgb, var(--teinte) 18%, transparent);
}

/* L'aperçu : un halo de la teinte, une grille légère, et la mini interface posée dessus. */
.carte-module__apercu {
  position: relative; display: grid; place-items: center; min-height: 13rem; padding: var(--space-6);
  background:
    radial-gradient(70% 90% at 85% 0%, color-mix(in srgb, var(--teinte) 40%, transparent), transparent 70%),
    radial-gradient(60% 80% at 0% 100%, color-mix(in srgb, var(--teinte) 22%, transparent), transparent 70%),
    var(--teinte-soft);
}
.carte-module__apercu::before {
  content: ''; position: absolute; inset: 0;
  background-image: linear-gradient(color-mix(in srgb, var(--ink) 7%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--ink) 7%, transparent) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(80% 80% at 50% 50%, #000, transparent);
  -webkit-mask-image: radial-gradient(80% 80% at 50% 50%, #000, transparent);
}
.mini {
  position: relative; width: min(100%, 22rem); display: grid; gap: var(--space-3); padding: var(--space-4);
  border-radius: var(--radius-md); background: var(--surface);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--ink) 14%, transparent);
  transform: rotate(-1.5deg); transition: transform var(--motion-base) var(--ease-out);
}
.carte-module:hover .mini { transform: rotate(0) translateY(-4px); }
.mini__ligne, .equipe { display: flex; align-items: center; gap: var(--space-2); }
.mini__avatar {
  flex-shrink: 0; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%;
  background: var(--teinte-soft); color: var(--teinte-ink); font-size: 0.75rem; font-weight: 700;
  border: 2px solid var(--surface);
}
.equipe .mini__avatar + .mini__avatar { margin-inline-start: -12px; }
.mini__textes { flex: 1; display: grid; gap: 6px; }
.mini__pastille {
  margin-inline-start: auto; padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: var(--teinte); color: var(--midnight); font-size: 0.75rem; font-weight: 700; white-space: nowrap;
}
.trait { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 18%, transparent); }
.trait--fort { height: 9px; background: color-mix(in srgb, var(--ink) 55%, transparent); }

/* Les jalons d'une mission : faits, en cours, à venir, reliés par un fil. */
.jalons { position: relative; display: grid; gap: var(--space-2); padding-inline-start: 2px; }
.jalons::before { content: ''; position: absolute; inset: 12px auto 12px 13px; width: 2px; background: color-mix(in srgb, var(--ink) 12%, transparent); }
.jalon { position: relative; display: flex; align-items: center; gap: var(--space-3); }
.jalon b {
  display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%;
  font-size: 0.6875rem; background: var(--surface); color: var(--ink-muted);
  border: 2px solid color-mix(in srgb, var(--ink) 18%, transparent);
}
.jalon i { flex: 1; }
.jalon--fait b { background: var(--teinte); border-color: var(--teinte); color: var(--midnight); }
.jalon--en-cours b { border-color: var(--teinte); color: var(--teinte-ink); box-shadow: 0 0 0 4px color-mix(in srgb, var(--teinte) 20%, transparent); }
.jalon--a-venir i { opacity: 0.5; }

/* Le podium d'un hackathon : trois marches, la plus haute au centre. */
.podium { display: grid; grid-template-columns: repeat(3, 1fr); align-items: end; gap: var(--space-2); height: 6rem; }
.podium__marche {
  display: grid; place-items: start center; padding-top: var(--space-1); border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  background: color-mix(in srgb, var(--teinte) 30%, var(--surface)); color: var(--teinte-ink); font-size: 0.75rem;
}
.podium__marche:nth-child(2) { background: var(--teinte); color: var(--midnight); }

.carte-module__corps { display: grid; gap: var(--space-3); align-content: start; padding: var(--space-6); }
.carte-module__pour { margin: 0; color: var(--teinte-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
.carte-module__corps h3 { margin: 0; font-family: var(--font-display); font-size: 1.5rem; letter-spacing: -0.02em; }
.carte-module__texte { margin: 0; color: var(--ink-muted); line-height: 1.6; }

/* Les trois temps du parcours, numérotés dans une pastille de la teinte. */
.carte-module__etapes { list-style: none; margin: var(--space-2) 0 0; padding: 0; display: grid; gap: var(--space-3); counter-reset: temps; }
.carte-module__etapes li { position: relative; display: grid; gap: 2px; padding-inline-start: 2.5rem; counter-increment: temps; }
.carte-module__etapes li::before {
  content: counter(temps); position: absolute; inset-inline-start: 0; top: 0;
  display: grid; place-items: center; width: 1.75rem; height: 1.75rem; border-radius: 50%;
  background: var(--teinte-soft); color: var(--teinte-ink); font-weight: 700; font-size: 0.875rem;
}
.carte-module__etapes span { color: var(--ink-muted); line-height: 1.5; }

.carte-module__actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); margin-top: var(--space-3); }
.carte-module__bouton {
  display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-6);
  border-radius: var(--radius-pill); background: var(--teinte); color: var(--midnight);
  font-weight: 700; text-decoration: none;
  transition: transform var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.carte-module__bouton:hover { transform: translateY(-1px); box-shadow: 0 8px 20px color-mix(in srgb, var(--teinte) 35%, transparent); }
.carte-module__bouton:focus-visible, .carte-module__lien:focus-visible { outline: 2px solid var(--teinte-ink); outline-offset: 3px; }
.carte-module__lien { color: var(--teinte-ink); font-weight: 600; text-underline-offset: 0.25em; }

@media (max-width: 860px) { .vedette__cartes { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) {
  .carte-module, .mini, .carte-module__bouton { transition: none; }
  .carte-module:hover, .carte-module:focus-within, .carte-module:hover .mini, .carte-module__bouton:hover { transform: none; }
}
</style>
