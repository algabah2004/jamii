<script setup lang="ts">
import { computed, useId } from 'vue'

/**
 * Champ de formulaire avec son libellé, son aide et son erreur, reliés pour les lecteurs d'écran
 * (label for, aria-describedby, aria-invalid). `multiligne` donne une zone de texte.
 */
const props = withDefaults(
  defineProps<{
    libelle: string
    aide?: string
    erreur?: string
    type?: string
    multiligne?: boolean
    requis?: boolean
    maxlength?: number
  }>(),
  { aide: undefined, erreur: undefined, type: 'text', multiligne: false, requis: false, maxlength: undefined },
)
const valeur = defineModel<string>({ default: '' })

const id = useId()
const decrit = computed(
  () => [props.aide && `${id}-aide`, props.erreur && `${id}-erreur`].filter(Boolean).join(' ') || undefined,
)
</script>

<template>
  <div class="j-champ" :class="{ 'j-champ--erreur': erreur }">
    <label :for="id">{{ libelle }}</label>
    <textarea
      v-if="multiligne"
      :id="id"
      v-model="valeur"
      rows="4"
      :required="requis"
      :maxlength="maxlength"
      :aria-describedby="decrit"
      :aria-invalid="erreur ? true : undefined"
    />
    <input
      v-else
      :id="id"
      v-model="valeur"
      :type="type"
      :required="requis"
      :maxlength="maxlength"
      :aria-describedby="decrit"
      :aria-invalid="erreur ? true : undefined"
    />
    <small v-if="aide" :id="`${id}-aide`">{{ aide }}</small>
    <small v-if="erreur" :id="`${id}-erreur`" class="j-champ__erreur">{{ erreur }}</small>
  </div>
</template>

<style>
.j-champ { display: grid; gap: var(--space-1); }
.j-champ label { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.j-champ input, .j-champ textarea {
  width: 100%;
  min-height: 44px;
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: 1px solid var(--line-strong); /* 3:1 minimum : le bord délimite le contrôle */
  border-radius: var(--radius-sm);
}
.j-champ textarea { resize: vertical; }
.j-champ--erreur input, .j-champ--erreur textarea { border-color: var(--danger); border-width: 2px; }
.j-champ__erreur { color: var(--danger); font-weight: 600; }
</style>
