<script setup lang="ts">
import { computed, provide, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import BarreCommunaute from './BarreCommunaute.vue'
import { CLE_ESPACE, type EtatEspace } from './espace'

/**
 * Le cadre de l'espace d'une communauté, sur toute la largeur de l'écran (route parente) :
 * - à gauche, la barre de la communauté (changer de communauté, le fil, les membres, les salons, « bien démarrer ») ;
 * - au centre, la page qu'on regarde (fil, salon, publication, nouveau salon) ;
 * - à droite, sur grand écran, ce qui se passe en direct (le `#contexte` de la page).
 * La barre reste montée d'une page à l'autre : elle ne bouge pas, ne se recharge pas et garde son défilement.
 * Sur téléphone : une colonne, la barre devient une rangée qui défile en haut.
 */
const { t } = useI18n()
const espace = reactive<EtatEspace>({ actuelle: null, actif: 'fil', communaute: null, recharge: 0, cote: null })
provide(CLE_ESPACE, espace)

// La fiche d'une autre communauté (juste après en avoir changé) ne doit pas s'afficher dans la barre.
const fiche = computed(() => (espace.communaute?.slug === espace.actuelle ? espace.communaute : null))
</script>

<template>
  <div class="espace">
    <BarreCommunaute
      v-if="espace.actuelle"
      :slug="espace.actuelle"
      :actif="espace.actif"
      :communaute="fiche"
      :recharge="espace.recharge"
      class="espace__barre"
    />
    <div class="espace__principal" :aria-label="t('communautes.espace.fil')">
      <RouterView v-slot="{ Component, route }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </div>
    <aside :ref="(el) => (espace.cote = el as HTMLElement | null)" class="espace__cote" />
  </div>
</template>

<style scoped>
.espace {
  --haut-espace: calc(64px + var(--space-6));
  display: grid; gap: var(--space-4); align-items: start;
  grid-template-columns: 17rem minmax(0, 1fr);
  margin-top: var(--space-4);
}
/* Une troisième colonne seulement quand la page y a posé quelque chose. */
.espace:has(> .espace__cote > *) { grid-template-columns: 17rem minmax(0, 1fr) 20rem; }
.espace > * { min-width: 0; }
.espace__barre { position: sticky; top: var(--haut-espace); height: calc(100dvh - var(--haut-espace) - var(--space-4)); }
.espace__principal { min-height: calc(100dvh - var(--haut-espace) - var(--space-4)); }
.espace__cote { position: sticky; top: var(--haut-espace); display: grid; gap: var(--space-4); max-height: calc(100dvh - var(--haut-espace) - var(--space-4)); overflow-y: auto; scrollbar-width: thin; }
.espace__cote:not(:has(> *)) { display: none; }

@media (max-width: 1360px) {
  .espace:has(> .espace__cote > *) { grid-template-columns: 17rem minmax(0, 1fr); }
  .espace__cote { display: none; }
}
@media (max-width: 860px) {
  .espace, .espace:has(> .espace__cote > *) { grid-template-columns: minmax(0, 1fr); margin-top: var(--space-2); }
  .espace__barre { position: static; height: auto; }
  .espace__principal { min-height: 0; }
}
/* Salon en plein écran sur téléphone : la barre de la communauté s'efface, la flèche du salon y ramène. */
@media (max-width: 720px) {
  :root.discussion-plein-ecran .espace { margin-top: 0; gap: 0; }
  :root.discussion-plein-ecran .espace__barre { display: none; }
  /* La barre de la communauté va bord à bord, comme le fil. */
  .espace__barre { margin-inline: calc(-1 * var(--space-4)); border-radius: 0; border-width: 1px 0; box-shadow: none; }
}
</style>
