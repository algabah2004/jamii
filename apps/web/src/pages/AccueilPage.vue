<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useSession } from '../session'

const { t } = useI18n()
const { moi } = useSession()
</script>

<template>
  <section>
    <h1>{{ t('accueil.titre') }}</h1>
    <p v-if="moi">{{ t('accueil.bienvenue', { nom: moi.nom }) }}</p>
    <p>{{ t('accueil.texte') }}</p>
  </section>
</template>
