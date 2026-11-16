<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CATEGORIES, chercherEmojis, type Categorie } from './emojis'

/*
 * Le choix d'un emoji (#157) : une recherche (« rire », « feu », « merci »), les récents, puis les catégories.
 * Échap ou un clic à côté le ferme. Sert à écrire (Composeur) et à réagir à un message.
 */
const emit = defineEmits<{ choisir: [emoji: string]; fermer: [] }>()
const { t } = useI18n()

const CLE_RECENTS = 'jamii.emojis.recents'
const RECENTS_MAX = 16

function lireRecents(): string[] {
  try {
    const brut = JSON.parse(localStorage.getItem(CLE_RECENTS) ?? '[]')
    return Array.isArray(brut) ? brut.filter((x): x is string => typeof x === 'string').slice(0, RECENTS_MAX) : []
  } catch { return [] }
}

const recherche = ref('')
const recents = ref(lireRecents())
const categorie = ref<Categorie['cle'] | 'recents'>(recents.value.length ? 'recents' : 'visages')
const champ = ref<HTMLInputElement | null>(null)
const racine = ref<HTMLElement | null>(null)

const affiches = computed(() => {
  if (recherche.value.trim()) return chercherEmojis(recherche.value).map((x) => x.e)
  if (categorie.value === 'recents') return recents.value
  return CATEGORIES.find((c) => c.cle === categorie.value)?.emojis.map((x) => x.e) ?? []
})

function choisir(e: string) {
  recents.value = [e, ...recents.value.filter((x) => x !== e)].slice(0, RECENTS_MAX)
  try { localStorage.setItem(CLE_RECENTS, JSON.stringify(recents.value)) } catch { /* Mémoire du navigateur indisponible : tant pis. */ }
  emit('choisir', e)
}

function dehors(e: PointerEvent) {
  if (racine.value && !racine.value.contains(e.target as Node)) emit('fermer')
}
onMounted(() => {
  void nextTick(() => champ.value?.focus())
  // Après le clic qui l'a ouvert.
  setTimeout(() => document.addEventListener('pointerdown', dehors), 0)
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', dehors))
</script>

<template>
  <div ref="racine" class="selecteur" role="dialog" :aria-label="t('emojis.titre')" data-test="selecteur-emoji" @keydown.esc.stop.prevent="emit('fermer')">
    <input
      ref="champ"
      v-model="recherche"
      type="search"
      class="selecteur__recherche"
      :placeholder="t('emojis.chercher')"
      :aria-label="t('emojis.chercher')"
      data-test="chercher-emoji"
    />
    <div v-if="!recherche.trim()" class="selecteur__onglets" role="tablist" :aria-label="t('emojis.categories')">
      <button
        v-if="recents.length"
        type="button"
        role="tab"
        :aria-selected="categorie === 'recents'"
        :title="t('emojis.cat.recents')"
        :aria-label="t('emojis.cat.recents')"
        @click="categorie = 'recents'"
      >🕘</button>
      <button
        v-for="c in CATEGORIES"
        :key="c.cle"
        type="button"
        role="tab"
        :aria-selected="categorie === c.cle"
        :title="t(`emojis.cat.${c.cle}`)"
        :aria-label="t(`emojis.cat.${c.cle}`)"
        @click="categorie = c.cle"
      >{{ c.icone }}</button>
    </div>
    <p v-if="recherche.trim() && !affiches.length" class="selecteur__vide">{{ t('emojis.aucun') }}</p>
    <div v-else class="selecteur__grille" role="listbox" :aria-label="recherche.trim() ? t('emojis.resultats') : t(`emojis.cat.${categorie}`)">
      <button v-for="e in affiches" :key="e" type="button" role="option" :aria-selected="false" class="emoji" :aria-label="e" data-test="emoji" @click="choisir(e)">{{ e }}</button>
    </div>
  </div>
</template>

<style scoped>
.selecteur { display: grid; gap: var(--space-2); width: min(20rem, calc(100vw - 32px)); padding: var(--space-3); border: 1px solid var(--line); border-radius: 18px; background: var(--surface); color: var(--ink); box-shadow: 0 18px 44px rgb(0 0 0 / 0.22); }
.selecteur__recherche { width: 100%; box-sizing: border-box; min-height: 38px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--bg); color: var(--ink); font: inherit; font-size: 0.9375rem; }
.selecteur__onglets { display: flex; gap: 2px; justify-content: space-between; }
.selecteur__onglets button { flex: 1; height: 34px; padding: 0; border: 0; border-bottom: 2px solid transparent; background: transparent; font-size: 1.125rem; cursor: pointer; filter: grayscale(0.6); }
.selecteur__onglets button[aria-selected='true'] { border-bottom-color: var(--action); filter: none; }
.selecteur__grille { display: grid; grid-template-columns: repeat(8, 1fr); gap: 2px; max-height: 13.5rem; overflow-y: auto; overscroll-behavior: contain; }
.emoji { display: grid; place-items: center; aspect-ratio: 1; padding: 0; border: 0; border-radius: 10px; background: transparent; font-size: 1.375rem; line-height: 1; cursor: pointer; }
.emoji:hover { background: var(--surface-raised); }
.emoji:focus-visible, .selecteur__onglets button:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.selecteur__vide { margin: 0; padding: var(--space-4); color: var(--ink-muted); text-align: center; }
</style>
