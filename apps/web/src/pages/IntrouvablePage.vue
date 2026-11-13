<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { JIllustration } from '../design-system'

const { t } = useI18n()
</script>

<template>
  <section class="etat-vide introuvable">
    <JIllustration nom="en-ligne" />
    <h1>{{ t('introuvable.titre') }}</h1>
    <p>{{ t('introuvable.texte') }}</p>
    <RouterLink to="/" class="j-bouton j-bouton--principal">{{ t('introuvable.retour') }}</RouterLink>
  </section>
</template>

<style scoped>
.introuvable { border-style: solid; margin-top: var(--space-8); }
.introuvable h1 { margin: 0; }
</style>
