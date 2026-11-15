<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useId } from 'vue'
defineProps<{ libelle: string }>()
const recherche = defineModel<string>({ required: true })
const { t } = useI18n()
const identifiant = useId()
</script>

<template>
  <div class="exploration__recherche">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
    <label :for="identifiant" class="visuellement-cache">{{ libelle }}</label>
    <input :id="identifiant" v-model="recherche" type="search" :placeholder="libelle" data-test="recherche-exploration" />
    <button v-if="recherche" type="button" :aria-label="t('exploration.effacer')" @click="recherche = ''">×</button>
  </div>
</template>
