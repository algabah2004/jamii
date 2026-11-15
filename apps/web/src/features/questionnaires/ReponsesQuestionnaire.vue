<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Question, Reponse } from './questionnaire'

/*
 * Les réponses d'une personne, lues par le créateur pour trier (candidatures d'un hackathon, propositions d'une
 * mission). Question par question, dans l'ordre du questionnaire ; « Sans réponse » quand rien n'a été donné.
 * Texte libre : affiché échappé, avec ses retours à la ligne.
 */
const props = defineProps<{ questions: Question[]; reponses: Reponse[] }>()
const { t } = useI18n()

const lignes = computed(() =>
  props.questions.map((q) => {
    const r = props.reponses.find((x) => x.questionId === q.id)?.valeur
    const texte = Array.isArray(r) ? r.join(' · ') : r === 'oui' || r === 'non' ? (q.type === 'oui_non' ? t(`questionnaire.remplir.${r}`) : r) : r
    return { id: q.id, intitule: q.intitule, texte: texte ?? null }
  }),
)
</script>

<template>
  <dl v-if="questions.length" class="reponses" data-test="reponses-questionnaire">
    <div v-for="l in lignes" :key="l.id" class="ligne">
      <dt>{{ l.intitule }}</dt>
      <dd :class="{ 'sans-reponse': l.texte === null }">{{ l.texte ?? t('questionnaire.reponses.aucune') }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.reponses { display: grid; gap: var(--space-2); margin: 0; padding: var(--space-3); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action) 5%, transparent); }
.ligne { display: grid; gap: 2px; }
dt { font-size: 0.8125rem; font-weight: 700; color: var(--ink-muted); }
dd { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
.sans-reponse { color: var(--ink-muted); font-style: italic; }
</style>
