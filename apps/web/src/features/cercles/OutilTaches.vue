<script setup lang="ts">
import EnTeteOutil from './EnTeteOutil.vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import CollineDuTour from './CollineDuTour.vue'
import TachesDuTour from './TachesDuTour.vue'
import type { Cercle, Tache } from './cercles'

/*
 * Les tâches du tour (les « to-dos » de Basecamp) : la colline en haut pour voir où en est vraiment le projet, la liste
 * en dessous pour agir (ajouter, prendre, cocher, et la répartition proposée par Ayo).
 */
const props = defineProps<{ cercle: Cercle; moiId: string | null }>()
const emit = defineEmits<{ maj: [taches: Tache[]] }>()
const { t } = useI18n()

const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)
const taches = ref<Tache[]>([])
function maj(liste: Tache[]) {
  taches.value = liste
  emit('maj', liste)
}
</script>

<template>
  <section class="outil-taches" aria-labelledby="taches-outil-titre" data-test="outil-taches-page">
    <EnTeteOutil id="taches-outil-titre" icone="taches" :titre="t('cercles.qg.outils.taches')" :aide="t('cercles.qg.tachesAide')" />

    <p v-if="!tour?.projetId" class="texte-discret">{{ t('cercles.tableau.pasDeTour') }}</p>
    <div v-else class="grille">
      <section class="carte" aria-labelledby="colline-titre">
        <h3 id="colline-titre">{{ t('cercles.qg.colline') }}</h3>
        <CollineDuTour v-if="taches.length" :taches="taches" />
        <p v-else class="texte-discret">{{ t('cercles.qg.aucuneTache') }}</p>
        <p class="texte-discret legende">{{ t('cercles.qg.collineAide') }}</p>
      </section>
      <section class="carte">
        <TachesDuTour :key="tour.projetId" :projet-id="tour.projetId" :moi-id="moiId" :membres="cercle.membres.map((m) => m.personne)" :beneficiaire-id="tour.beneficiaire.id" @maj="maj" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.outil-taches { display: grid; gap: var(--space-4); }
.grille { display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); align-items: start; }
.grille h3 { margin: 0 0 var(--space-3); font-size: var(--type-body-size); }
.legende { margin: var(--space-3) 0 0; font-size: var(--type-body-sm-size); }
@media (max-width: 900px) { .grille { grid-template-columns: 1fr; } }
</style>
