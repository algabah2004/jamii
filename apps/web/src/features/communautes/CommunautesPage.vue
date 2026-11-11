<script setup lang="ts">
import { JBouton } from '../../design-system'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'

type Communaute = components['schemas']['Communaute']

const { t, locale } = useI18n()
const communautes = ref<Communaute[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes', { params: { query: { limite: 50 } } })
    if (!data) throw new Error('communautés indisponibles')
    communautes.value = data.elements ?? []
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()
</script>

<template>
  <section>
    <h1>{{ t('communautes.titre') }}</h1>
    <p>{{ t('communautes.intro') }}</p>

    <p v-if="etat === 'chargement'" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <ul v-else class="cartes" data-test="communautes">
      <li v-for="c in communautes" :key="c.id" class="carte">
        <h2>
          <RouterLink :to="`/communautes/${c.slug}`">{{ c.nom }}</RouterLink>
        </h2>
        <p v-if="c.pays" class="discret">{{ t('communautes.antenne', { pays: nomPays(c.pays, locale) }) }}</p>
        <p>{{ c.description }}</p>
        <p class="discret">
          {{ t('communautes.membres', { n: c.nombreMembres }, c.nombreMembres) }} ·
          {{ t('communautes.questionsOuvertes', { n: c.questionsOuvertes }, c.questionsOuvertes) }}
        </p>
        <p v-if="c.membre" class="discret" data-test="deja-membre">{{ t('communautes.tuEnFaisPartie') }}</p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.cartes { list-style: none; padding: 0; margin: 1.5rem 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); gap: 1rem; }
.carte { background: var(--surface); border: 1px solid var(--line); border-radius: 0.5rem; padding: 1rem; }
.carte h2 { font-size: 1.125rem; margin: 0 0 0.25rem; }
.carte p { margin: 0.375rem 0; }
.discret { color: var(--ink-muted); font-size: 0.875rem; }
</style>
