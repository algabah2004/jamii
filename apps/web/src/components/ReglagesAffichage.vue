<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LANGUES, type Langue } from '../i18n'
import { choisirTheme, themeActuel, type Theme } from '../design-system'

/** La langue et le thème : dans l'en-tête sur ordinateur, dans le pied de page sur téléphone. */
const { t, locale } = useI18n()

/*
 * Un clic sur un menu déroulant lui laisse le focus, et Chrome dessine alors l'anneau bleu comme au clavier : on ne
 * le montre que si la dernière action vient du clavier.
 */
const souris = ref(false)

function changerLangue(langue: Langue) {
  locale.value = langue
  document.documentElement.lang = langue
}
</script>

<template>
  <div class="reglages" :class="{ 'reglages--souris': souris }" @pointerdown="souris = true" @keydown="souris = false">
    <label class="choix">
      <span class="visuellement-cache">{{ t('langue') }}</span>
      <select :value="locale" @change="changerLangue(($event.target as HTMLSelectElement).value as Langue)">
        <option v-for="l in LANGUES" :key="l" :value="l">{{ l.toUpperCase() }}</option>
      </select>
    </label>
    <label class="choix">
      <span class="visuellement-cache">{{ t('theme') }}</span>
      <select :value="themeActuel" data-test="theme" @change="choisirTheme(($event.target as HTMLSelectElement).value as Theme)">
        <option value="systeme">◐ {{ t('themeCourt.systeme') }}</option>
        <option value="clair">☀ {{ t('themeCourt.clair') }}</option>
        <option value="sombre">☾ {{ t('themeCourt.sombre') }}</option>
      </select>
    </label>
  </div>
</template>

<style scoped>
:where(.reglages) { display: flex; gap: var(--space-2); }
/* Des pastilles comme la capsule, avec un chevron dessiné à la place de celui du système. */
.choix { position: relative; display: inline-flex; align-items: center; }
.choix::after {
  content: ''; position: absolute; right: 12px; width: 7px; height: 7px; margin-top: -3px; pointer-events: none;
  border-right: 1.5px solid currentColor; border-bottom: 1.5px solid currentColor; transform: rotate(45deg); opacity: 0.7;
}
select {
  appearance: none; -webkit-appearance: none;
  min-height: 36px; padding: 0 30px 0 12px;
  border: 1px solid color-mix(in srgb, var(--ink) 16%, transparent); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); cursor: pointer;
  font: inherit; font-size: var(--type-body-sm-size); font-weight: 600;
  transition: background var(--motion-fast, 120ms) var(--ease-out, ease-out), border-color var(--motion-fast, 120ms) var(--ease-out, ease-out);
}
select:hover { background: color-mix(in srgb, var(--ink) 11%, transparent); border-color: color-mix(in srgb, var(--ink) 28%, transparent); }
select option { color: initial; background: initial; }
.reglages--souris select:focus-visible { outline: none; }
@media (prefers-reduced-motion: reduce) { select { transition: none; } }
</style>
