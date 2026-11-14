<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { monogramme } from './communautes'
import BarreCommunaute from './BarreCommunaute.vue'

type Communaute = components['schemas']['Communaute']

/**
 * Le cadre de l'espace communautés, comme une messagerie d'équipe :
 * - la barre d'icônes de mes communautés (on passe de l'une à l'autre en un clic) ;
 * - la barre de la communauté ouverte : ses salons et son forum de questions ;
 * - au centre, la conversation, sur toute la hauteur ;
 * - à droite, le contexte, seulement sur grand écran.
 * Sur téléphone, tout passe en une colonne : les icônes, puis les salons, en rangées qui défilent.
 */
const props = defineProps<{ actuelle?: string; actif?: string; communaute?: Communaute | null }>()
const { t } = useI18n()

const communautes = ref<Communaute[]>([])
async function charger() {
  try {
    const { data } = await api.GET('/api/communautes', { params: { query: { limite: 50 } } })
    communautes.value = data?.elements ?? []
  } catch {
    // La barre d'icônes est un raccourci : si elle ne charge pas, la conversation reste utilisable.
    communautes.value = []
  }
}
void charger()

const miennes = computed(() => communautes.value.filter((c) => c.membre || c.slug === props.actuelle))
</script>

<template>
  <div class="espace" :class="{ 'espace--barre': actuelle, 'espace--contexte': $slots.contexte }">
    <nav class="rail" :aria-label="t('communautes.espace.mesCommunautes')">
      <ul>
        <li v-for="c in miennes" :key="c.id">
          <RouterLink
            :to="`/communautes/${c.slug}`"
            class="rail__icone"
            :class="{ 'rail__icone--antenne': c.pays }"
            :aria-current="c.slug === actuelle ? 'page' : undefined"
            :aria-label="c.nom"
            :title="c.nom"
            data-test="rail-communaute"
          >
            {{ monogramme(c.nom) }}
          </RouterLink>
        </li>
        <li class="rail__separateur" aria-hidden="true" />
        <li>
          <RouterLink to="/communautes" class="rail__icone rail__icone--ajouter" :aria-label="t('communautes.espace.decouvrir')" :title="t('communautes.espace.decouvrir')">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
          </RouterLink>
        </li>
      </ul>
    </nav>

    <BarreCommunaute v-if="actuelle" :slug="actuelle" :actif="actif ?? 'questions'" :communaute="communaute" class="espace__barre" />

    <div class="espace__principal"><slot /></div>
    <aside v-if="$slots.contexte" class="espace__contexte"><slot name="contexte" /></aside>
  </div>
</template>

<style scoped>
/* Un seul panneau, découpé en colonnes par de fines lignes : une application, pas une pile de cartes. */
.espace {
  display: grid; grid-template-columns: 4.5rem minmax(0, 1fr);
  height: max(34rem, calc(100dvh - 9rem));
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg);
  box-shadow: var(--ombre-carte); overflow: hidden;
}
.espace--barre { grid-template-columns: 4.5rem 15.5rem minmax(0, 1fr); }
.espace--barre.espace--contexte { grid-template-columns: 4.5rem 15.5rem minmax(0, 1fr) 17rem; }
.espace > * { min-width: 0; min-height: 0; }

.rail { background: var(--surface-raised); border-inline-end: 1px solid var(--line); overflow-y: auto; scrollbar-width: none; }
.rail ul { list-style: none; margin: 0; padding: var(--space-3) 0; display: grid; justify-items: center; gap: var(--space-2); }
.rail__icone {
  position: relative; display: grid; place-items: center; width: 44px; height: 44px;
  border-radius: 50%; background: var(--surface); color: var(--ink);
  font-family: var(--font-display); font-weight: 700; font-size: 0.875rem; text-decoration: none;
  transition: border-radius var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.rail__icone:hover, .rail__icone[aria-current='page'] { border-radius: 14px; background: var(--action); color: var(--on-action); }
.rail__icone--antenne { background: var(--ink); color: var(--bg); }
/* Le repère de la communauté ouverte : une petite barre sur le bord. */
.rail__icone::before {
  content: ''; position: absolute; inset-inline-start: -14px; width: 4px; height: 0; border-radius: 0 4px 4px 0;
  background: var(--ink); transition: height var(--motion-fast) var(--ease-out);
}
.rail__icone:hover::before { height: 16px; }
.rail__icone[aria-current='page']::before { height: 32px; }
.rail__icone:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.rail__icone--ajouter { background: transparent; border: 1px dashed var(--line-strong); color: var(--ink-muted); }
.rail__icone--ajouter:hover { border-style: solid; border-color: var(--action); background: var(--action-soft); color: var(--action-ink); }
.rail__separateur { width: 24px; height: 2px; border-radius: 2px; background: var(--line); }

.espace__barre { border-inline-end: 1px solid var(--line); }
.espace__principal { display: flex; flex-direction: column; overflow-y: auto; }
.espace__contexte { border-inline-start: 1px solid var(--line); overflow-y: auto; padding: var(--space-4); }

@media (max-width: 1280px) {
  .espace--barre.espace--contexte { grid-template-columns: 4.5rem 15.5rem minmax(0, 1fr); }
  .espace__contexte { display: none; }
}
@media (max-width: 760px) {
  /* Téléphone : une colonne. Les icônes et les salons deviennent des rangées qui défilent. */
  .espace, .espace--barre, .espace--barre.espace--contexte {
    grid-template-columns: 1fr; grid-template-rows: auto auto minmax(0, 1fr);
    height: max(30rem, calc(100dvh - 7rem)); border-radius: var(--radius-md);
  }
  .rail { border-inline-end: 0; border-bottom: 1px solid var(--line); }
  .rail ul { display: flex; padding: var(--space-2) var(--space-3); overflow-x: auto; scrollbar-width: none; }
  .rail__icone { width: 38px; height: 38px; flex-shrink: 0; }
  .rail__icone::before { display: none; }
  .rail__separateur { width: 2px; height: 24px; flex-shrink: 0; }
  .espace__barre { border-inline-end: 0; border-bottom: 1px solid var(--line); }
}
</style>
