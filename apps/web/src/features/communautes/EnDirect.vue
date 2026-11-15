<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar } from '../../design-system'
import { ilYa } from '../vitrine/vitrine'

type Salon = components['schemas']['Salon']
type Message = components['schemas']['MessageSalon']
type Membre = components['schemas']['MembreCommunaute']

/*
 * Ce qui se passe en ce moment dans la communauté, sur le côté : le dernier message de chaque salon (relu toutes les
 * 20 s), l'appel de # général pour les membres, et les membres actifs cette semaine.
 */
const props = defineProps<{ slug: string }>()
const emit = defineEmits<{ actifs: [n: number] }>()
const { t, locale } = useI18n()

const salons = ref<Salon[]>([])
const derniers = ref<Record<string, Message | undefined>>({})
const actifs = ref<Membre[]>([])

async function charger() {
  try {
    const [s, m] = await Promise.all([
      api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } }),
      api.GET('/api/communautes/{slug}/membres', { params: { path: { slug: props.slug }, query: { limite: 100 } } }),
    ])
    salons.value = s.data?.elements ?? []
    actifs.value = (m.data?.elements ?? []).filter((x) => x.actif)
    emit('actifs', actifs.value.length)
    await relire()
  } catch {
    // Un complément : sans lui, la page reste entière.
  }
}
async function relire() {
  const lus = await Promise.all(salons.value.map(async (s) => {
    try {
      const { data } = await api.GET('/api/communautes/{slug}/salons/{salon}/messages', {
        params: { path: { slug: props.slug, salon: s.slug }, query: { limite: 1 } },
      })
      return [s.slug, data?.elements.at(-1)] as const
    } catch {
      return [s.slug, undefined] as const
    }
  }))
  derniers.value = Object.fromEntries(lus)
}
watch(() => props.slug, () => void charger(), { immediate: true })
const minuterie = setInterval(() => {
  if (typeof document === 'undefined' || document.visibilityState !== 'hidden') void relire()
}, 20_000)
onBeforeUnmount(() => clearInterval(minuterie))

const appel = computed(() => salons.value.find((s) => s.appelUrl))
</script>

<template>
  <section class="cote-carte" aria-labelledby="en-direct" data-test="en-direct">
    <h2 id="en-direct"><span class="direct" aria-hidden="true" />{{ t('communautes.direct.titre') }}</h2>
    <ul class="salons">
      <li v-for="s in salons" :key="s.id">
        <RouterLink :to="{ path: `/communautes/${slug}`, query: { salon: s.slug } }" class="salon">
          <span class="salon__nom"># {{ s.nom }}</span>
          <template v-if="derniers[s.slug]">
            <span class="salon__extrait"><strong>{{ derniers[s.slug]!.auteur.nom.split(' ')[0] }}</strong> {{ derniers[s.slug]!.texte }}</span>
            <span class="salon__quand">{{ ilYa(derniers[s.slug]!.envoyeLe, locale) }}</span>
          </template>
          <span v-else class="salon__extrait">{{ t('communautes.direct.calme') }}</span>
        </RouterLink>
      </li>
    </ul>
    <a v-if="appel?.appelUrl" :href="appel.appelUrl" target="_blank" rel="noopener noreferrer" class="appel">
      <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
      {{ t('communautes.direct.appel', { salon: appel.nom }) }}<span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
    </a>
  </section>

  <section v-if="actifs.length" class="cote-carte" aria-labelledby="actifs">
    <h2 id="actifs">{{ t('communautes.direct.actifs') }}</h2>
    <ul class="avatars">
      <li v-for="m in actifs.slice(0, 12)" :key="m.personne.id" :title="m.personne.nom">
        <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="36" />
      </li>
    </ul>
    <RouterLink :to="{ path: `/communautes/${slug}`, query: { voir: 'membres' } }" class="tous">{{ t('communautes.direct.tousLesMembres') }} →</RouterLink>
  </section>
</template>

<style scoped>
.cote-carte { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); padding: var(--space-4); display: grid; gap: var(--space-3); }
.cote-carte h2 { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: 1rem; }
.direct { width: 10px; height: 10px; border-radius: 50%; background: var(--cantaloupe); box-shadow: 0 0 0 0 color-mix(in srgb, var(--cantaloupe) 60%, transparent); animation: pouls 2s infinite; }
@keyframes pouls { 70% { box-shadow: 0 0 0 8px transparent; } 100% { box-shadow: 0 0 0 0 transparent; } }
@media (prefers-reduced-motion: reduce) { .direct { animation: none; } }
.salons { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-1); }
.salon { display: grid; gap: 2px; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); color: var(--ink); text-decoration: none; }
.salon:hover { background: var(--surface-raised); }
.salon__nom { font-weight: 700; font-size: var(--type-body-sm-size); }
.salon__extrait { color: var(--ink-muted); font-size: 0.8125rem; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.salon__extrait strong { color: var(--ink); font-weight: 600; }
.salon__quand { color: var(--ink-muted); font-size: 0.75rem; }
.appel {
  display: flex; align-items: center; justify-content: center; gap: var(--space-2); min-height: 40px; border-radius: var(--radius-pill);
  background: color-mix(in srgb, #23a55a 14%, transparent); color: #1a7f45; font-weight: 700; font-size: var(--type-body-sm-size); text-decoration: none;
}
.appel:hover { background: color-mix(in srgb, #23a55a 24%, transparent); }
.avatars { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.tous { font-size: var(--type-body-sm-size); font-weight: 600; color: var(--action-ink); text-decoration: none; }
.tous:hover { text-decoration: underline; }
:root[data-theme='sombre'] .appel { color: #57d68d; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .appel { color: #57d68d; } }
</style>
