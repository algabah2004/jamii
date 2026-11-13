<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { monogramme } from './communautes'

type Communaute = components['schemas']['Communaute']

/**
 * Le cadre de l'espace communautés, comme un réseau social : à gauche les communautés dont on fait
 * partie (on passe de l'une à l'autre en un clic), au centre le fil, à droite le contexte.
 * Sur téléphone, les communautés deviennent une rangée de pastilles qui défile, et le contexte passe sous le fil.
 */
const props = defineProps<{ actuelle?: string }>()
const { t } = useI18n()

const communautes = ref<Communaute[]>([])
async function charger() {
  try {
    const { data } = await api.GET('/api/communautes', { params: { query: { limite: 50 } } })
    communautes.value = data?.elements ?? []
  } catch {
    // La colonne de gauche est un raccourci : si elle ne charge pas, le fil reste utilisable.
    communautes.value = []
  }
}
void charger()

const miennes = computed(() => communautes.value.filter((c) => c.membre || c.slug === props.actuelle))
</script>

<template>
  <div class="espace">
    <nav class="espace__communautes" :aria-label="t('communautes.espace.mesCommunautes')">
      <p class="espace__titre">{{ t('communautes.espace.mesCommunautes') }}</p>
      <ul>
        <li v-for="c in miennes" :key="c.id">
          <RouterLink :to="`/communautes/${c.slug}`" :aria-current="c.slug === actuelle ? 'page' : undefined" class="raccourci">
            <span class="raccourci__monogramme" :class="{ 'raccourci__monogramme--antenne': c.pays }" aria-hidden="true">{{ monogramme(c.nom) }}</span>
            <span class="raccourci__nom">{{ c.nom }}</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/communautes" class="raccourci raccourci--toutes">
            <span class="raccourci__monogramme" aria-hidden="true">＋</span>
            <span class="raccourci__nom">{{ t('communautes.espace.decouvrir') }}</span>
          </RouterLink>
        </li>
      </ul>
    </nav>

    <div class="espace__fil"><slot /></div>
    <aside v-if="$slots.contexte" class="espace__contexte"><slot name="contexte" /></aside>
  </div>
</template>

<style scoped>
.espace { display: grid; gap: var(--space-6); grid-template-columns: 13.5rem minmax(0, 1fr) 17rem; align-items: start; }
.espace__fil { min-width: 0; display: grid; gap: var(--space-4); }
.espace__communautes, .espace__contexte { position: sticky; top: calc(64px + var(--space-6)); display: grid; gap: var(--space-4); min-width: 0; }
.espace__communautes li { min-width: 0; }

.espace__titre { margin: 0 0 var(--space-2); padding-inline: var(--space-3); color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.espace__communautes ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.raccourci {
  min-width: 0;
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-2) var(--space-3); border-radius: var(--radius-md);
  color: var(--ink); text-decoration: none; font-weight: 600; font-size: var(--type-body-sm-size);
  transition: background var(--motion-fast) var(--ease-out);
}
.raccourci:hover { background: var(--surface-raised); }
.raccourci[aria-current='page'] { background: var(--action-soft); }
.raccourci__monogramme {
  flex-shrink: 0; display: grid; place-items: center; width: 32px; height: 32px;
  border-radius: var(--radius-sm); background: var(--action-soft); color: var(--action-ink);
  font-family: var(--font-display); font-weight: 700; font-size: 0.8125rem;
}
.raccourci[aria-current='page'] .raccourci__monogramme { background: var(--action); color: var(--on-action); }
.raccourci__monogramme--antenne { background: var(--surface-raised); color: var(--ink); }
.raccourci--toutes { color: var(--ink-muted); }
.raccourci--toutes .raccourci__monogramme { background: transparent; border: 1px dashed var(--line-strong); color: var(--ink-muted); }
.raccourci__nom { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 1080px) {
  .espace { grid-template-columns: 12rem minmax(0, 1fr); }
  .espace__contexte { grid-column: 2; position: static; }
}
@media (max-width: 760px) {
  .espace { grid-template-columns: 1fr; gap: var(--space-4); }
  .espace__contexte { grid-column: auto; }
  .espace__communautes { position: static; }
  .espace__titre { display: none; }
  /* Sur téléphone : une rangée de pastilles qui défile, comme des « stories ». */
  .espace__communautes ul { display: flex; gap: var(--space-2); overflow-x: auto; scrollbar-width: none; padding-bottom: 2px; }
  .raccourci { flex-shrink: 0; border: 1px solid var(--line); border-radius: var(--radius-pill); padding: var(--space-1) var(--space-3) var(--space-1) var(--space-1); }
  .raccourci__monogramme { width: 28px; height: 28px; border-radius: var(--radius-pill); }
}
</style>
