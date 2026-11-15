<script setup lang="ts">
import { computed, nextTick, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton } from '../../design-system'
import { aDesChoix, LIMITES, nouvelleQuestion, problemes, TYPES, type Question, type TypeQuestion } from './questionnaire'

/*
 * Composer son questionnaire : le créateur d'un hackathon ou d'une mission ajoute ses questions (des modèles pour
 * démarrer vite), choisit leur type, les rend obligatoires, les range. Le même éditeur sert aux deux.
 * `fige` : déjà des réponses, on ne change plus les questions (l'API répond `409 questionnaire_fige`).
 */
const questions = defineModel<Question[]>({ required: true })
const props = withDefaults(defineProps<{ fige?: boolean; contexte: 'hackathon' | 'mission' }>(), { fige: false })
const { t } = useI18n()
const id = useId()

const erreurs = computed(() => problemes(questions.value))
const plein = computed(() => questions.value.length >= LIMITES.questionsMax)
const MODELES = computed(() =>
  props.contexte === 'hackathon' ? ['lien', 'niveau', 'disponibilites', 'motivation'] : ['lien', 'experience', 'disponibilites', 'deplacement'],
)
const MODELE_DE: Record<string, Partial<Question>> = {
  lien: { type: 'texte_court', obligatoire: true },
  niveau: { type: 'choix_unique', obligatoire: true },
  disponibilites: { type: 'choix_multiples' },
  motivation: { type: 'texte_long' },
  experience: { type: 'oui_non', obligatoire: true },
  deplacement: { type: 'oui_non' },
}

/*
 * Une copie tenue à jour tout de suite : le parent n'applique un changement qu'au rendu suivant, et deux clics rapides
 * liraient sinon la même liste (le second effacerait le premier).
 */
let courant: Question[] = questions.value
watch(questions, (v) => (courant = v))
function poser(liste: Question[]) {
  courant = liste
  questions.value = liste
}

function modifier(i: number, changement: Partial<Question>) {
  poser(courant.map((q, j) => (j === i ? { ...q, ...changement } : q)))
}

/** Les choix se gardent quand on passe d'un type à choix à l'autre, et disparaissent sinon. */
function changerType(i: number, type: TypeQuestion) {
  const { choix, ...reste } = courant[i]!
  const q: Question = aDesChoix(type) ? { ...reste, type, choix: choix?.length ? choix : ['', ''] } : { ...reste, type }
  poser(courant.map((x, j) => (j === i ? q : x)))
}

async function ajouter(modele?: string) {
  if (courant.length >= LIMITES.questionsMax) return
  const base: Partial<Question> = modele ? { ...MODELE_DE[modele], intitule: t(`questionnaire.modeles.${modele}.intitule`) } : {}
  // Les choix d'un modèle sont séparés par « ; » : « | » marque le pluriel dans vue-i18n.
  const choix = modele && aDesChoix(base.type ?? 'texte_court') ? t(`questionnaire.modeles.${modele}.choix`).split(';') : undefined
  poser([...courant, nouvelleQuestion(courant, { ...base, ...(choix ? { choix } : {}) })])
  await nextTick()
  document.getElementById(`${id}-intitule-${courant.length - 1}`)?.focus()
}

function retirer(i: number) {
  poser(courant.filter((_, j) => j !== i))
}

function deplacer(i: number, sens: -1 | 1) {
  const cible = i + sens
  if (cible < 0 || cible >= courant.length) return
  const liste = [...courant]
  ;[liste[i], liste[cible]] = [liste[cible]!, liste[i]!]
  poser(liste)
}

function changerChoix(i: number, k: number, texte: string) {
  modifier(i, { choix: (courant[i]!.choix ?? []).map((c, j) => (j === k ? texte : c)) })
}
function ajouterChoix(i: number) {
  const choix = courant[i]!.choix ?? []
  if (choix.length < LIMITES.choixMax) modifier(i, { choix: [...choix, ''] })
}
function retirerChoix(i: number, k: number) {
  modifier(i, { choix: (courant[i]!.choix ?? []).filter((_, j) => j !== k) })
}
</script>

<template>
  <fieldset class="editeur" :disabled="fige" data-test="editeur-questionnaire">
    <legend class="editeur__titre">{{ t('questionnaire.editeur.titre') }}</legend>
    <p class="texte-discret">{{ t(`questionnaire.editeur.aide.${contexte}`) }}</p>
    <p v-if="fige" class="fige" role="status" data-test="questionnaire-fige">{{ t('questionnaire.editeur.fige') }}</p>

    <ol v-if="questions.length" class="questions">
      <li v-for="(q, i) in questions" :key="q.id" class="question" :class="{ 'question--erreur': erreurs[q.id] }" data-test="question-editee">
        <div class="question__tete">
          <span class="numero" aria-hidden="true">{{ i + 1 }}</span>
          <label class="visuellement-cache" :for="`${id}-intitule-${i}`">{{ t('questionnaire.editeur.intitule', { n: i + 1 }) }}</label>
          <input
            :id="`${id}-intitule-${i}`"
            class="question__intitule"
            :value="q.intitule"
            :maxlength="LIMITES.intituleMax"
            :placeholder="t('questionnaire.editeur.intitulePlaceholder')"
            :aria-invalid="erreurs[q.id] === 'intitule' ? true : undefined"
            :aria-describedby="erreurs[q.id] ? `${id}-erreur-${i}` : undefined"
            data-test="intitule"
            @input="modifier(i, { intitule: ($event.target as HTMLInputElement).value })"
          />
        </div>

        <div class="question__reglages">
          <label class="reglage">
            <span>{{ t('questionnaire.editeur.type') }}</span>
            <select :value="q.type" data-test="type" @change="changerType(i, ($event.target as HTMLSelectElement).value as TypeQuestion)">
              <option v-for="ty in TYPES" :key="ty" :value="ty">{{ t(`questionnaire.types.${ty}`) }}</option>
            </select>
          </label>
          <label class="reglage reglage--case">
            <input type="checkbox" :checked="q.obligatoire" data-test="obligatoire" @change="modifier(i, { obligatoire: ($event.target as HTMLInputElement).checked })" />
            {{ t('questionnaire.editeur.obligatoire') }}
          </label>
        </div>

        <label class="reglage">
          <span>{{ t('questionnaire.editeur.aideQuestion') }}</span>
          <input :value="q.aide ?? ''" :maxlength="LIMITES.aideMax" :placeholder="t(`questionnaire.editeur.aidePlaceholder.${q.type}`)" @input="modifier(i, { aide: ($event.target as HTMLInputElement).value })" />
        </label>

        <fieldset v-if="aDesChoix(q.type)" class="choix" data-test="choix">
          <legend class="reglage">{{ t('questionnaire.editeur.choix', { min: LIMITES.choixMin, max: LIMITES.choixMax }) }}</legend>
          <div v-for="(c, k) in q.choix ?? []" :key="k" class="choix__ligne">
            <span class="puce" :class="{ 'puce--carre': q.type === 'choix_multiples' }" aria-hidden="true" />
            <input
              :value="c"
              :maxlength="LIMITES.choixLongueurMax"
              :aria-label="t('questionnaire.editeur.choixN', { n: k + 1 })"
              :placeholder="t('questionnaire.editeur.choixN', { n: k + 1 })"
              data-test="choix-texte"
              @input="changerChoix(i, k, ($event.target as HTMLInputElement).value)"
            />
            <button v-if="(q.choix?.length ?? 0) > LIMITES.choixMin" type="button" class="icone" :aria-label="t('questionnaire.editeur.retirerChoix', { n: k + 1 })" @click="retirerChoix(i, k)">
              <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5 5l10 10M15 5L5 15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
            </button>
          </div>
          <div v-if="(q.choix?.length ?? 0) < LIMITES.choixMax">
            <JBouton variante="discret" data-test="ajouter-choix" @click="ajouterChoix(i)">{{ t('questionnaire.editeur.ajouterChoix') }}</JBouton>
          </div>
        </fieldset>

        <p v-if="erreurs[q.id]" :id="`${id}-erreur-${i}`" class="erreur">{{ t(`questionnaire.erreurs.${erreurs[q.id]}`) }}</p>

        <div class="question__actions">
          <button type="button" class="icone" :disabled="i === 0" :aria-label="t('questionnaire.editeur.monter', { n: i + 1 })" @click="deplacer(i, -1)">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5 12l5-5 5 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
          <button type="button" class="icone" :disabled="i === questions.length - 1" :aria-label="t('questionnaire.editeur.descendre', { n: i + 1 })" @click="deplacer(i, 1)">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5 8l5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
          <JBouton variante="discret" data-test="retirer-question" @click="retirer(i)">{{ t('questionnaire.editeur.retirer') }}</JBouton>
        </div>
      </li>
    </ol>
    <p v-else class="vide">{{ t('questionnaire.editeur.vide') }}</p>

    <div v-if="!plein" class="ajouts">
      <span class="texte-discret">{{ t('questionnaire.editeur.modeles') }}</span>
      <div class="ajouts__liste">
        <button
          v-for="m in MODELES"
          :key="m"
          type="button"
          class="modele"
          :data-test="`modele-${m}`"
          @click="ajouter(m)"
        >
          + {{ t(`questionnaire.modeles.${m}.court`) }}
        </button>
        <JBouton variante="secondaire" data-test="ajouter-question" @click="ajouter()">{{ t('questionnaire.editeur.ajouter') }}</JBouton>
      </div>
    </div>
    <p v-else class="texte-discret">{{ t('questionnaire.editeur.plein', { max: LIMITES.questionsMax }) }}</p>
  </fieldset>
</template>

<style scoped>
.editeur { display: grid; gap: var(--space-4); margin: 0; padding: 0; border: 0; min-inline-size: 0; }
.editeur__titre { padding: 0; font-weight: 700; font-size: 1.0625rem; }
.editeur p { margin: 0; }
.fige { padding: var(--space-3); border-radius: var(--radius-md); background: color-mix(in srgb, var(--cantaloupe) 18%, transparent); }
.questions { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.question {
  display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--surface); border-inline-start: 4px solid var(--action);
}
.question--erreur { border-inline-start-color: var(--danger); }
.question__tete { display: flex; gap: var(--space-3); align-items: center; }
.numero {
  display: grid; place-items: center; flex: none; inline-size: 28px; block-size: 28px; border-radius: 50%;
  background: var(--action); color: var(--on-action); font-weight: 800; font-size: 0.875rem;
}
input, select {
  inline-size: 100%; min-block-size: 40px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--canvas, var(--surface)); color: var(--ink); font: inherit;
}
input[type='checkbox'] { inline-size: 20px; min-block-size: 20px; accent-color: var(--action); }
input:focus-visible, select:focus-visible, button:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.question__intitule { font-weight: 600; font-size: 1.0625rem; }
.question__reglages { display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); align-items: end; }
.question__reglages .reglage:first-child { flex: 1 1 14rem; }
.reglage { display: grid; gap: var(--space-1); font-size: var(--type-body-sm-size, 0.875rem); color: var(--ink-muted); font-weight: 600; }
.reglage--case { display: flex; gap: var(--space-2); align-items: center; min-block-size: 40px; color: var(--ink); }
.choix { display: grid; gap: var(--space-2); margin: 0; padding: 0; border: 0; min-inline-size: 0; }
.choix legend { padding: 0; margin-block-end: var(--space-1); }
.choix__ligne { display: flex; gap: var(--space-2); align-items: center; }
.puce { flex: none; inline-size: 16px; block-size: 16px; border: 2px solid var(--line-strong); border-radius: 50%; }
.puce--carre { border-radius: 4px; }
.icone {
  display: grid; place-items: center; flex: none; inline-size: 36px; block-size: 36px; border: 1px solid var(--line-strong);
  border-radius: var(--radius-md); background: var(--surface); color: var(--ink); cursor: pointer;
}
.icone:disabled { opacity: 0.4; cursor: default; }
.question__actions { display: flex; gap: var(--space-2); align-items: center; justify-content: flex-end; }
.erreur { color: var(--danger); font-weight: 600; }
.vide { padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); color: var(--ink-muted); text-align: center; }
.ajouts { display: grid; gap: var(--space-2); }
.ajouts__liste { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.modele {
  min-block-size: 36px; padding: 0 var(--space-3); border: 1px dashed var(--action); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--action) 6%, transparent); color: var(--ink); font: inherit; font-weight: 600; cursor: pointer;
}
.modele:hover { background: color-mix(in srgb, var(--action) 14%, transparent); }
</style>
