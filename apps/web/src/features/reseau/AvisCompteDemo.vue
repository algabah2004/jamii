<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { EcrireA } from './reseau'

/**
 * Le compte démo vise un vrai membre (code `compte_demo`, #157) : on dit pourquoi ça ne part pas, et à qui écrire à la
 * place pour essayer quand même. Sans ça, le visiteur croit à une panne.
 */
defineProps<{ ecrireA: EcrireA | null; appel?: boolean }>()
const { t } = useI18n()
</script>

<template>
  <p role="alert" class="avis-demo" data-test="avis-compte-demo">
    {{ t(appel ? 'appels.fin.compte_demo' : 'reseau.erreurs.compte_demo') }}
    <RouterLink v-if="ecrireA" :to="{ name: 'conversation', params: { personneId: ecrireA.id } }" data-test="ecrire-a-demo">
      {{ t('reseau.erreurs.ecrireADemo', { nom: ecrireA.nom }) }}
    </RouterLink>
  </p>
</template>

<style scoped>
.avis-demo { margin: 0; padding: 0.6rem 0.8rem; border-radius: var(--radius-md); background: var(--action-soft); color: var(--ink); }
.avis-demo a { color: var(--action-ink); font-weight: 600; }
</style>
