<script setup lang="ts">
import { inject, watch, watchEffect } from 'vue'
import type { components } from '../../api/generated/communautes'
import { CLE_ESPACE } from './espace'

type Communaute = components['schemas']['Communaute']

/**
 * Le centre d'une page de l'espace d'une communauté. Le cadre (la barre à gauche, le côté à droite) appartient à la
 * route parente (CadreCommunaute) et reste en place d'une page à l'autre ; la page lui dit seulement :
 * - `actuelle` : la communauté de la barre (absente pendant un chargement : la barre garde la sienne ; `null` : pas de barre) ;
 * - `actif` : le lien actif de la barre ; `communaute` : la fiche déjà lue ;
 * - `recharge` : un nombre qui augmente quand la barre doit relire ses salons ;
 * - son `#contexte` part dans la colonne de droite.
 */
const props = defineProps<{ actuelle?: string | null; actif?: string; communaute?: Communaute | null; recharge?: number }>()
const espace = inject(CLE_ESPACE, null)

watchEffect(() => {
  if (!espace) return
  if (props.actuelle !== undefined) espace.actuelle = props.actuelle
  espace.actif = props.actif ?? 'fil'
  if (props.communaute) espace.communaute = props.communaute
})
watch(() => props.recharge, () => {
  if (espace) espace.recharge++
})
</script>

<template>
  <div class="espace-centre">
    <slot />
    <Teleport v-if="$slots.contexte && espace?.cote" :to="espace.cote">
      <slot name="contexte" />
    </Teleport>
  </div>
</template>

<style scoped>
.espace-centre { display: grid; gap: var(--space-4); align-content: start; }
</style>
