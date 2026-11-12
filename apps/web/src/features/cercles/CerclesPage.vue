<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { lireCercles, type CercleResume } from './cercles'

/* Mes cercles et ceux qui cherchent encore des membres. */
const { t, locale } = useI18n()
const cercles = ref<CercleResume[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    cercles.value = await lireCercles()
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()
</script>

<template>
  <section class="cercles">
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('cercles.titre') }}</h1>
        <p>{{ t('cercles.intro') }}</p>
      </div>
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <p v-else-if="!cercles.length" class="etat-vide" data-test="vide">{{ t('cercles.aucun') }}</p>
    <ul v-else class="grille-cartes" data-test="cercles">
      <li v-for="c in cercles" :key="c.id">
        <RouterLink :to="`/cercles/${c.id}`" class="carte carte-cliquable resume">
          <span class="resume__haut">
            <strong>{{ c.nom }}</strong>
            <span class="resume__statut" :class="`resume__statut--${c.statut}`">{{ t(`cercles.statut.${c.statut}`) }}</span>
          </span>
          <span class="texte-discret">{{ c.ville }}, {{ nomPays(c.pays, locale) }} · {{ t('cercles.membres', { n: c.membres.length, taille: c.taille }) }}</span>
          <span class="resume__visages" aria-hidden="true">
            <JAvatar v-for="m in c.membres" :key="m.utilisateurId" :nom="m.nom" :url="m.avatarUrl" :taille="34" />
          </span>
          <span v-if="c.tourEnCours" class="resume__tour">{{ t('cercles.tourDe', { n: c.tourEnCours.numero, nom: c.tourEnCours.beneficiaire.split(' ')[0] }) }}</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.cercles { display: grid; gap: var(--space-6); }
.cercles ul { list-style: none; margin: 0; padding: 0; }
.resume { display: grid; gap: var(--space-2); padding: var(--space-6); color: var(--ink); text-decoration: none; height: 100%; }
.resume__haut { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); font-size: 1.125rem; }
.resume__statut { padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600; background: var(--action-soft); color: var(--action-ink); white-space: nowrap; }
.resume__statut--en_cours { background: var(--proof); color: var(--on-proof); }
.resume__visages { display: flex; margin-top: var(--space-2); }
.resume__visages :deep(.j-avatar) { box-shadow: 0 0 0 2px var(--surface); }
.resume__visages :deep(.j-avatar + .j-avatar) { margin-left: -8px; }
.resume__tour { color: var(--proof-ink); font-weight: 600; font-size: var(--type-body-sm-size); }
</style>
