<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

/*
 * Mène au profil d'une personne (/p/:id), partout où l'on voit son nom ou son avatar. Sans identifiant (compte
 * supprimé, IA), le contenu reste affiché sans lien.
 * `doublon` : un avatar posé à côté du nom, qui mène au même endroit. On peut le cliquer, mais il est retiré de la
 * tabulation et des lecteurs d'écran pour ne pas annoncer deux fois le même lien.
 * `nom` : nom accessible, pour un lien qui ne contient qu'un avatar.
 */
const props = defineProps<{ id?: string | null; nom?: string; doublon?: boolean }>()
const { t } = useI18n()
</script>

<template>
  <RouterLink
    v-if="props.id"
    :to="`/p/${props.id}`"
    class="lien-personne"
    :tabindex="doublon ? -1 : undefined"
    :aria-hidden="doublon || undefined"
    :aria-label="!doublon && nom ? t('reseau.profil.voir', { nom }) : undefined"
    data-test="lien-personne"
  ><slot /></RouterLink>
  <span v-else class="lien-personne lien-personne--inerte"><slot /></span>
</template>

<style>
.lien-personne { color: inherit; text-decoration: none; border-radius: var(--radius-sm); }
a.lien-personne:hover { text-decoration: underline; text-underline-offset: 2px; }
a.lien-personne:has(> .j-avatar):hover { text-decoration: none; filter: brightness(1.08); }
a.lien-personne:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
