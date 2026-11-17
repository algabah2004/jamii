<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { vApparait } from './apparait'

const { t } = useI18n()
const cle = 'accueil.faq'

/** Les questions sur ce que Jamii permet de faire, dans l'ordre où un visiteur se les pose. */
const QUESTIONS = ['quoi', 'connexion', 'pasDev', 'cercle', 'paiement', 'hackathon', 'vitrine', 'langues', 'donnees'] as const
</script>

<template>
  <section class="faq" aria-labelledby="faq-titre" data-test="faq">
    <div v-apparait class="faq__entete">
      <p class="faq__surtitre">{{ t(`${cle}.surtitre`) }}</p>
      <h2 id="faq-titre">{{ t(`${cle}.titre`) }}</h2>
      <p class="faq__intro">{{ t(`${cle}.intro`) }}</p>
    </div>
    <!-- Des <details> natifs : ouverts au clavier comme à la souris, lus correctement par les lecteurs d'écran, sans script. -->
    <div class="faq__liste">
      <details v-for="(q, i) in QUESTIONS" :key="q" v-apparait="i * 50" class="question" :data-test="`faq-${q}`">
        <summary>
          <span>{{ t(`${cle}.questions.${q}.q`) }}</span>
          <span class="question__signe" aria-hidden="true" />
        </summary>
        <p>{{ t(`${cle}.questions.${q}.r`) }}</p>
      </details>
    </div>
  </section>
</template>

<style scoped>
.faq { display: grid; grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr); gap: var(--space-8); align-items: start; }
.faq__entete { display: grid; gap: var(--space-3); justify-items: start; position: sticky; top: calc(68px + var(--space-6)); }
.faq__surtitre {
  margin: 0; padding: 2px var(--space-3); border-radius: var(--radius-pill);
  background: var(--action-soft); color: var(--action-ink);
  font-weight: 700; font-size: var(--type-body-sm-size); letter-spacing: 0.04em;
}
.faq__entete h2 { margin: 0; font-family: var(--font-display); font-size: clamp(1.75rem, 3.6vw, 2.5rem); line-height: 1.1; letter-spacing: -0.03em; }
.faq__intro { margin: 0; color: var(--ink-muted); line-height: 1.65; }

.faq__liste { display: grid; gap: var(--space-3); }
.question {
  border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface);
  transition: border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.question[open] { border-color: color-mix(in srgb, var(--action) 50%, var(--line)); box-shadow: 0 10px 24px color-mix(in srgb, var(--action) 10%, transparent); }
.question summary {
  display: flex; align-items: center; justify-content: space-between; gap: var(--space-4);
  min-height: 48px; padding: var(--space-4) var(--space-6);
  cursor: pointer; list-style: none; font-weight: 600; color: var(--ink);
}
.question summary::-webkit-details-marker { display: none; }
.question summary:focus-visible { outline: 2px solid var(--action-ink); outline-offset: 2px; border-radius: var(--radius-md); }
/* Un « + » qui devient « − » quand la réponse est ouverte. */
.question__signe { position: relative; flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; background: var(--action-soft); }
.question__signe::before, .question__signe::after {
  content: ''; position: absolute; left: 50%; top: 50%; width: 12px; height: 2px; border-radius: 1px;
  background: var(--action-ink); transform: translate(-50%, -50%);
  transition: transform var(--motion-fast) var(--ease-out);
}
.question__signe::after { transform: translate(-50%, -50%) rotate(90deg); }
.question[open] .question__signe::after { transform: translate(-50%, -50%) rotate(0deg); }
@keyframes ouvre { from { opacity: 0; transform: translateY(-6px); } }
.question[open] p { animation: ouvre var(--motion-base) var(--ease-out); }
.question p { margin: 0; padding: 0 var(--space-6) var(--space-6); color: var(--ink-muted); line-height: 1.65; }

@media (max-width: 860px) {
  .faq { grid-template-columns: minmax(0, 1fr); }
  .faq__entete { position: static; }
}
@media (prefers-reduced-motion: reduce) { .question, .question__signe::before, .question__signe::after { transition: none; } }
</style>
