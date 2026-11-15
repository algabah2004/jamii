<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

const { t } = useI18n()
const cle = 'accueil.sectionSystalink'

/** Les moments où Jamii propose un service Systalink : chacun répond à un besoin précis, jamais imposé. */
const MOMENTS = [
  { cle: 'enLigne', service: 'DataCloud' },
  { cle: 'site', service: 'Orion' },
  { cle: 'outils', service: 'Domaines · E-mail · SystaTalk' },
] as const
</script>

<template>
  <section class="systalink zone-sombre" aria-labelledby="section-systalink-titre" data-test="section-systalink">
    <div class="systalink__lumiere" aria-hidden="true" />
    <div class="systalink__haut">
      <p class="systalink__surtitre">{{ t(`${cle}.surtitre`) }}</p>
      <h2 id="section-systalink-titre">{{ t(`${cle}.titre`) }}</h2>
      <p class="systalink__intro">{{ t(`${cle}.intro`) }}</p>
    </div>

    <ul class="moments">
      <li v-for="(m, i) in MOMENTS" :key="m.cle" class="moment" :data-test="`systalink-${m.cle}`">
        <span class="moment__numero" aria-hidden="true">{{ String(i + 1).padStart(2, '0') }}</span>
        <h3>{{ t(`${cle}.moments.${m.cle}.titre`) }}</h3>
        <p>{{ t(`${cle}.moments.${m.cle}.texte`) }}</p>
        <span class="moment__service">{{ m.service }}</span>
      </li>
    </ul>

    <div class="systalink__bas">
      <p class="promesse" data-test="systalink-promesse">
        <strong>{{ t(`${cle}.promesse.titre`) }}</strong> {{ t(`${cle}.promesse.texte`) }}
      </p>
      <RouterLink :to="{ path: '/vitrine', query: { hebergeur: 'datacloud' } }" class="systalink__bouton" data-test="systalink-vitrine">
        {{ t(`${cle}.voir`) }} →
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
/* Un bandeau sombre aux couleurs de la scène : Midnight, et la lumière Electric Blue. */
.systalink {
  position: relative; isolation: isolate; overflow: hidden;
  display: grid; gap: var(--space-8);
  padding: clamp(var(--space-6), 5vw, var(--space-12));
  border-radius: calc(var(--radius-lg) * 1.5);
  background: var(--midnight); color: var(--ink);
}
.systalink__lumiere {
  position: absolute; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(45% 60% at 85% 0%, color-mix(in srgb, var(--electric-blue) 55%, transparent), transparent 75%),
    radial-gradient(40% 50% at 0% 100%, color-mix(in srgb, var(--electric-blue) 30%, transparent), transparent 75%);
}
/* Une grille fine, comme un plan de baie de serveurs, qui s'efface vers le bas. */
.systalink__lumiere::after {
  content: ''; position: absolute; inset: 0;
  background-image: linear-gradient(color-mix(in srgb, #fff 6%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, #fff 6%, transparent) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: linear-gradient(180deg, #000, transparent 80%);
  -webkit-mask-image: linear-gradient(180deg, #000, transparent 80%);
}

.systalink__haut { display: grid; gap: var(--space-3); justify-items: start; max-width: 44rem; }
.systalink__surtitre {
  margin: 0; padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--electric-blue) 25%, transparent); color: var(--ink);
  border: 1px solid color-mix(in srgb, var(--electric-blue) 60%, transparent);
  font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em;
}
.systalink__haut h2 {
  margin: 0; font-family: var(--font-display); font-size: clamp(1.75rem, 3.6vw, 2.5rem);
  line-height: 1.1; letter-spacing: -0.03em; text-wrap: balance;
}
.systalink__intro { margin: 0; color: var(--ink-muted); font-size: 1.0625rem; line-height: 1.65; }

.moments { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
.moment {
  display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, #fff 5%, transparent);
  border: 1px solid color-mix(in srgb, #fff 12%, transparent);
  backdrop-filter: blur(8px);
  transition: border-color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.moment:hover { border-color: color-mix(in srgb, var(--electric-blue) 70%, transparent); background: color-mix(in srgb, var(--electric-blue) 10%, transparent); }
.moment__numero { font-family: var(--font-display); font-weight: 700; color: var(--electric-blue); font-variant-numeric: tabular-nums; }
.moment h3 { margin: 0; font-family: var(--font-display); font-size: 1.1875rem; }
.moment p { margin: 0; color: var(--ink-muted); line-height: 1.55; }
.moment__service {
  justify-self: start; margin-top: var(--space-2); padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: color-mix(in srgb, #fff 10%, transparent); color: var(--ink);
  font-size: var(--type-body-sm-size); font-weight: 600;
}

.systalink__bas { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-6); }
.promesse {
  flex: 1 1 28rem; margin: 0; padding: var(--space-4) var(--space-6); border-radius: var(--radius-md);
  background: color-mix(in srgb, #fff 6%, transparent); line-height: 1.6;
  border-inline-start: 4px solid var(--electric-blue);
}
.systalink__bouton {
  display: inline-flex; align-items: center; min-height: 48px; padding: 0 var(--space-6);
  border-radius: var(--radius-pill); background: #fff; color: var(--midnight);
  font-weight: 700; text-decoration: none; white-space: nowrap;
  transition: transform var(--motion-fast) var(--ease-out);
}
.systalink__bouton:hover { transform: translateY(-1px); }
.systalink__bouton:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }

@media (max-width: 900px) { .moments { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .moment, .systalink__bouton { transition: none; } .systalink__bouton:hover { transform: none; } }
</style>
