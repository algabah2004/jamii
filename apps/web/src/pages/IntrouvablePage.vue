<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('introuvable.titre') }}</h1>
    <RouterLink to="/">{{ t('introuvable.retour') }}</RouterLink>
  </section>
</template>
