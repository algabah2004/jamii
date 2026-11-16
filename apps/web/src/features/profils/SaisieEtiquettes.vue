<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import IconeProfil from './IconeProfil.vue'

/**
 * Une liste de mots courts (compétences, technologies) saisie comme des étiquettes : Entrée ou virgule ajoute,
 * Retour arrière sur un champ vide retire la dernière, coller « a, b, c » en ajoute trois. Le texte encore en
 * cours de saisie compte aussi : `valider()` l'ajoute (appelé avant d'enregistrer).
 */
const props = withDefaults(
  defineProps<{ libelle: string; aide?: string; erreur?: string; max?: number; suggestions?: string[]; dataTest?: string }>(),
  { aide: undefined, erreur: undefined, max: 20, suggestions: () => [], dataTest: undefined },
)
const valeurs = defineModel<string[]>({ required: true })
const { t } = useI18n()
const id = useId()
const saisie = ref('')
const champ = ref<HTMLInputElement | null>(null)

const plein = computed(() => valeurs.value.length >= props.max)
const propositions = computed(() => {
  const deja = new Set(valeurs.value.map((v) => v.toLowerCase()))
  const debut = saisie.value.trim().toLowerCase()
  return props.suggestions.filter((s) => !deja.has(s.toLowerCase()) && (!debut || s.toLowerCase().includes(debut))).slice(0, 8)
})

function ajouter(texte: string) {
  const nouvelles = texte.split(',').map((m) => m.trim()).filter(Boolean)
  const liste = [...valeurs.value]
  for (const m of nouvelles) {
    if (liste.length >= props.max) break
    if (!liste.some((v) => v.toLowerCase() === m.toLowerCase())) liste.push(m)
  }
  valeurs.value = liste
}

function valider() {
  if (saisie.value.trim()) ajouter(saisie.value)
  saisie.value = ''
}

function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ',') {
    if (saisie.value.trim()) {
      e.preventDefault()
      valider()
    } else if (e.key === ',') e.preventDefault()
  } else if (e.key === 'Backspace' && !saisie.value && valeurs.value.length) {
    valeurs.value = valeurs.value.slice(0, -1)
  }
}

function retirer(v: string) {
  valeurs.value = valeurs.value.filter((x) => x !== v)
  champ.value?.focus()
}

defineExpose({ valider })
</script>

<template>
  <div class="etiquettes-saisie" :class="{ 'etiquettes-saisie--erreur': erreur }">
    <label :for="id" class="etiquettes-saisie__libelle">{{ libelle }}</label>
    <div class="etiquettes-saisie__zone" @click="champ?.focus()">
      <span v-for="v in valeurs" :key="v" class="etiquette-saisie">
        {{ v }}
        <button type="button" :aria-label="t('profil.editeur.retirerEtiquette', { nom: v })" @click.stop="retirer(v)"><IconeProfil nom="fermer" :taille="12" /></button>
      </span>
      <input
        :id="id"
        ref="champ"
        v-model="saisie"
        type="text"
        :disabled="plein"
        :placeholder="plein ? t('profil.editeur.plein', { n: max }) : t('profil.editeur.ajouterEtiquette')"
        :aria-invalid="erreur ? 'true' : undefined"
        :aria-describedby="[aide && `${id}-aide`, erreur && `${id}-erreur`].filter(Boolean).join(' ') || undefined"
        :data-test="dataTest"
        @keydown="touche"
        @blur="valider"
        @paste.prevent="ajouter(($event.clipboardData?.getData('text') ?? '').replace(/\n/g, ','))"
      />
    </div>
    <div v-if="propositions.length && !plein" class="etiquettes-saisie__suggestions">
      <span class="texte-discret">{{ t('profil.editeur.suggestions') }}</span>
      <button v-for="s in propositions" :key="s" type="button" @click="ajouter(s)"><IconeProfil nom="plus" :taille="12" />{{ s }}</button>
    </div>
    <p v-if="aide" :id="`${id}-aide`" class="texte-discret etiquettes-saisie__aide">{{ aide }}</p>
    <p v-if="erreur" :id="`${id}-erreur`" class="etiquettes-saisie__erreur" role="alert">{{ erreur }}</p>
  </div>
</template>

<style scoped>
.etiquettes-saisie { display: grid; gap: 8px; }
.etiquettes-saisie__libelle { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.etiquettes-saisie__zone { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; min-height: 48px; padding: 6px 8px; border: 1px solid var(--line-strong); border-radius: 14px; background: var(--surface); cursor: text; }
.etiquettes-saisie__zone:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
.etiquettes-saisie--erreur .etiquettes-saisie__zone { border-color: var(--danger); }
.etiquettes-saisie__zone input { flex: 1 1 8rem; min-width: 8rem; min-height: 34px !important; border: 0 !important; padding: 4px 6px !important; background: transparent !important; outline: none; }
.etiquette-saisie { display: inline-flex; align-items: center; gap: 4px; padding: 4px 4px 4px 12px; border-radius: 50px; background: var(--action-soft); color: var(--action-ink); font-weight: 600; font-size: .875rem; }
.etiquette-saisie button { display: grid; place-items: center; width: 24px; height: 24px; border: 0; border-radius: 50%; background: transparent; color: inherit; cursor: pointer; }
.etiquette-saisie button:hover { background: color-mix(in srgb, var(--action) 20%, transparent); }
.etiquettes-saisie__suggestions { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.etiquettes-saisie__suggestions button { display: inline-flex; align-items: center; gap: 4px; min-height: 30px; padding: 0 10px; border: 1px dashed var(--line-strong); border-radius: 50px; background: transparent; font-size: .8125rem; cursor: pointer; }
.etiquettes-saisie__suggestions button:hover { border-color: var(--action); color: var(--action-ink); }
.etiquettes-saisie__aide, .etiquettes-saisie__erreur { margin: 0; }
.etiquettes-saisie__erreur { color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }
</style>
