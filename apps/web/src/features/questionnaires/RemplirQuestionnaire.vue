<script setup lang="ts">
import { computed, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { erreursRestantes, LIMITES, type Question, type Valeurs } from './questionnaire'

/*
 * Répondre au questionnaire du créateur : à l'inscription d'un hackathon, ou en proposant ses services. Chaque type a
 * son champ natif (texte, boutons radio, cases à cocher), relié à son libellé, son aide et son erreur pour les
 * lecteurs d'écran. `erreurs` : par identifiant de question (`obligatoire`, `valeur_inconnue`, `trop_long`…).
 */
const props = defineProps<{ questions: Question[]; erreurs?: Record<string, string> }>()
const valeurs = defineModel<Valeurs>({ required: true })
const { t } = useI18n()
const id = useId()

// Une copie locale, à jour tout de suite : deux réponses données dans le même instant ne s'écrasent pas
// (le parent ne renvoie la nouvelle valeur qu'au rendu suivant).
let courant: Valeurs = valeurs.value
watch(valeurs, (v) => (courant = v))

const texte = (q: Question) => (typeof valeurs.value[q.id] === 'string' ? (valeurs.value[q.id] as string) : '')
const coches = (q: Question) => (Array.isArray(courant[q.id]) ? (courant[q.id] as string[]) : [])

function poser(q: Question, v: string | string[]) {
  courant = { ...courant, [q.id]: v }
  valeurs.value = courant
}
function basculer(q: Question, choix: string, coche: boolean) {
  const actuels = coches(q)
  // L'ordre des choix du créateur, pas l'ordre des clics.
  poser(q, (q.choix ?? []).filter((c) => (c === choix ? coche : actuels.includes(c))))
}
// « Obligatoire » disparaît dès que la question a une réponse : on ne laisse pas une erreur sous un champ rempli.
const visibles = computed(() => erreursRestantes(props.questions, valeurs.value, props.erreurs ?? {}))
const erreurDe = (q: Question) => visibles.value[q.id]
const decrit = (q: Question, erreur: string | undefined) =>
  [q.aide && `${id}-${q.id}-aide`, erreur && `${id}-${q.id}-erreur`].filter(Boolean).join(' ') || undefined
</script>

<template>
  <div class="remplir" data-test="remplir-questionnaire">
    <div v-for="(q, i) in questions" :key="q.id" class="question" :class="{ 'question--erreur': erreurDe(q) }" :data-test="`question-${q.id}`">
      <!-- Texte : un libellé ; choix : un groupe avec sa légende. -->
      <template v-if="q.type === 'texte_court' || q.type === 'texte_long'">
        <label :for="`${id}-${q.id}`" class="intitule">
          <span class="numero" aria-hidden="true">{{ i + 1 }}</span>
          <span class="intitule__texte">{{ q.intitule }} <span v-if="q.obligatoire" class="requis">{{ t('questionnaire.remplir.obligatoire') }}</span></span>
        </label>
        <p v-if="q.aide" :id="`${id}-${q.id}-aide`" class="aide">{{ q.aide }}</p>
        <textarea
          v-if="q.type === 'texte_long'"
          :id="`${id}-${q.id}`"
          :value="texte(q)"
          rows="4"
          :maxlength="LIMITES.texteLongMax"
          :required="q.obligatoire"
          :aria-describedby="decrit(q, erreurDe(q))"
          :aria-invalid="erreurDe(q) ? true : undefined"
          @input="poser(q, ($event.target as HTMLTextAreaElement).value)"
        />
        <input
          v-else
          :id="`${id}-${q.id}`"
          :value="texte(q)"
          :maxlength="LIMITES.texteCourtMax"
          :required="q.obligatoire"
          :aria-describedby="decrit(q, erreurDe(q))"
          :aria-invalid="erreurDe(q) ? true : undefined"
          @input="poser(q, ($event.target as HTMLInputElement).value)"
        />
      </template>

      <!-- Un seul choix : un vrai groupe radio, qui peut porter aria-required ; plusieurs choix : « obligatoire » reste écrit. -->
      <fieldset
        v-else
        class="groupe"
        :role="q.type === 'choix_multiples' ? undefined : 'radiogroup'"
        :aria-describedby="decrit(q, erreurDe(q))"
        :aria-required="q.type !== 'choix_multiples' && q.obligatoire ? true : undefined"
        :aria-invalid="q.type !== 'choix_multiples' && erreurDe(q) ? true : undefined"
      >
        <legend class="intitule">
          <span class="numero" aria-hidden="true">{{ i + 1 }}</span>
          <span class="intitule__texte">{{ q.intitule }} <span v-if="q.obligatoire" class="requis">{{ t('questionnaire.remplir.obligatoire') }}</span></span>
        </legend>
        <p v-if="q.aide" :id="`${id}-${q.id}-aide`" class="aide">{{ q.aide }}</p>
        <div class="options" :class="{ 'options--ligne': q.type === 'oui_non' }">
          <template v-if="q.type === 'oui_non'">
            <label v-for="v in ['oui', 'non']" :key="v" class="option">
              <input type="radio" :name="`${id}-${q.id}`" :value="v" :checked="texte(q) === v" @change="poser(q, v)" />
              {{ t(`questionnaire.remplir.${v}`) }}
            </label>
          </template>
          <template v-else-if="q.type === 'choix_unique'">
            <label v-for="c in q.choix ?? []" :key="c" class="option">
              <input type="radio" :name="`${id}-${q.id}`" :value="c" :checked="texte(q) === c" @change="poser(q, c)" />
              {{ c }}
            </label>
          </template>
          <template v-else>
            <label v-for="c in q.choix ?? []" :key="c" class="option">
              <input type="checkbox" :value="c" :checked="coches(q).includes(c)" @change="basculer(q, c, ($event.target as HTMLInputElement).checked)" />
              {{ c }}
            </label>
          </template>
        </div>
      </fieldset>

      <p v-if="erreurDe(q)" :id="`${id}-${q.id}-erreur`" class="erreur" data-test="erreur-question">
        {{ t(`questionnaire.remplir.erreurs.${['obligatoire', 'trop_long', 'valeur_inconnue'].includes(erreurDe(q)!) ? erreurDe(q) : 'autre'}`) }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.remplir { display: grid; gap: var(--space-4); }
.question { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); border: 1px solid var(--line-strong); background: var(--surface); }
.question--erreur { border-color: var(--danger); }
.question p { margin: 0; }
.groupe { display: grid; gap: var(--space-2); margin: 0; padding: 0; border: 0; min-inline-size: 0; }
/* Le numéro reste à gauche, l'intitulé et « obligatoire » coulent à côté, même sur un petit écran. */
.intitule { display: flex; gap: var(--space-2); align-items: flex-start; padding: 0; font-weight: 600; }
.intitule__texte { flex: 1; min-inline-size: 0; }
.numero {
  display: grid; place-items: center; flex: none; inline-size: 24px; block-size: 24px; border-radius: 50%;
  background: color-mix(in srgb, var(--action) 15%, transparent); font-size: 0.8125rem; font-weight: 800;
}
.requis { display: inline-block; vertical-align: 0.1em; white-space: nowrap; padding: 0 var(--space-2); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--cantaloupe) 25%, transparent); font-size: 0.75rem; font-weight: 700; }
.aide { color: var(--ink-muted); font-size: 0.875rem; }
input:not([type]), textarea {
  inline-size: 100%; min-block-size: 44px; padding: var(--space-2) var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--canvas, var(--surface)); color: var(--ink); font: inherit; resize: vertical;
}
input:focus-visible, textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.options { display: grid; gap: var(--space-2); }
.options--ligne { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.option {
  display: flex; gap: var(--space-2); align-items: center; min-block-size: 44px; padding: 0 var(--space-3);
  border: 1px solid var(--line-strong); border-radius: var(--radius-md); cursor: pointer;
}
.option:has(input:checked) { border-color: var(--action); background: color-mix(in srgb, var(--action) 8%, transparent); font-weight: 600; }
.option input { inline-size: 18px; block-size: 18px; accent-color: var(--action); }
.erreur { color: var(--danger); font-weight: 600; }
</style>
