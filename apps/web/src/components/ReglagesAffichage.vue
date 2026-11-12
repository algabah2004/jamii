<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LANGUES, type Langue } from '../i18n'
import { choisirTheme, themeActuel, type Theme } from '../design-system'

/** La langue et le thème : dans l'en-tête sur ordinateur, dans le pied de page sur téléphone. */
const { t, locale } = useI18n()

function changerLangue(langue: Langue) {
  locale.value = langue
  document.documentElement.lang = langue
}
</script>

<template>
  <div class="reglages">
    <label>
      <span class="visuellement-cache">{{ t('langue') }}</span>
      <select :value="locale" @change="changerLangue(($event.target as HTMLSelectElement).value as Langue)">
        <option v-for="l in LANGUES" :key="l" :value="l">{{ l.toUpperCase() }}</option>
      </select>
    </label>
    <label>
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
select {
  min-height: 36px; padding: 0 var(--space-2);
  background: transparent; border: 1px solid var(--line); border-radius: var(--radius-sm); cursor: pointer;
  font-size: var(--type-body-sm-size);
}
select:hover { border-color: var(--line-strong); }
</style>
