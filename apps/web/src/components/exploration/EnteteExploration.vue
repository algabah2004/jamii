<script setup lang="ts">
defineProps<{
  surtitre: string
  titre: string
  accent: string
  intro: string
  statistiques: { valeur: number | string; libelle: string }[]
}>()
</script>

<template>
  <header class="exploration__hero">
    <div class="exploration__presentation">
      <p class="exploration__surtitre"><i aria-hidden="true" />{{ surtitre }}</p>
      <h1>{{ titre }}<span>{{ accent }}</span></h1>
      <p class="exploration__intro">{{ intro }}</p>
      <div class="exploration__actions"><slot name="actions" /></div>
      <dl class="exploration__statistiques">
        <div v-for="s in statistiques" :key="s.libelle"><dt>{{ s.libelle }}</dt><dd>{{ s.valeur }}</dd></div>
      </dl>
    </div>
    <div class="exploration__vedette"><slot /></div>
  </header>
</template>

<style src="./exploration.css"></style>
