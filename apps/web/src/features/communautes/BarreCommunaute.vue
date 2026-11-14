<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import { aDuNouveau } from './lus'

type Communaute = components['schemas']['Communaute']
type Salon = components['schemas']['Salon']

/*
 * La barre d'une communauté : son nom, ses salons en direct, son forum de questions, et moi en bas.
 * Un point signale un salon où des messages sont arrivés depuis ma dernière visite (relu toutes les 20 s).
 */
const props = defineProps<{ slug: string; actif: string; communaute?: Communaute | null }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const chargee = ref<Communaute | null>(null)
const fiche = computed(() => props.communaute ?? chargee.value)
const salons = ref<Salon[]>([])
const derniers = ref<Record<string, number>>({})

async function charger() {
  try {
    const [c, s] = await Promise.all([
      props.communaute ? Promise.resolve(null) : api.GET('/api/communautes/{slug}', { params: { path: { slug: props.slug } } }),
      api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } }),
    ])
    if (c?.data) chargee.value = c.data
    salons.value = s.data?.elements ?? []
    await relireDerniers()
  } catch {
    // La barre aide à naviguer ; sans elle, la conversation reste lisible.
  }
}

/** Le numéro du dernier message de chaque salon, pour les points « du nouveau ». */
async function relireDerniers() {
  const lus = await Promise.all(
    salons.value.map(async (s) => {
      try {
        const { data } = await api.GET('/api/communautes/{slug}/salons/{salon}/messages', {
          params: { path: { slug: props.slug, salon: s.slug }, query: { limite: 1 } },
        })
        return [s.slug, data?.elements.at(-1)?.numero] as const
      } catch {
        return [s.slug, undefined] as const
      }
    }),
  )
  derniers.value = Object.fromEntries(lus.filter(([, n]) => n !== undefined)) as Record<string, number>
}

let minuterie: ReturnType<typeof setInterval> | null = null
watch(() => props.slug, () => {
  chargee.value = null
  salons.value = []
  void charger()
}, { immediate: true })
minuterie = setInterval(() => {
  if (typeof document === 'undefined' || document.visibilityState !== 'hidden') void relireDerniers()
}, 20_000)
onBeforeUnmount(() => {
  if (minuterie) clearInterval(minuterie)
})

const nouveau = (s: Salon) => s.slug !== props.actif && aDuNouveau(props.slug, s.slug, derniers.value[s.slug])
</script>

<template>
  <nav class="barre" :aria-label="fiche?.nom ?? t('communautes.salons.liste')">
    <header class="barre__tete">
      <h2 class="barre__nom" :title="fiche?.nom">{{ fiche?.nom ?? '…' }}</h2>
      <p v-if="fiche" class="barre__meta">
        {{ t('communautes.membres', { n: fiche.nombreMembres }, fiche.nombreMembres) }}<template v-if="fiche.pays"> · {{ nomPays(fiche.pays, locale) }}</template>
      </p>
    </header>

    <div class="barre__corps">
      <p class="barre__section">{{ t('communautes.salons.titre') }}</p>
      <ul>
        <li v-for="s in salons" :key="s.id">
          <RouterLink
            :to="{ path: `/communautes/${slug}`, query: { salon: s.slug } }"
            class="lien"
            :class="{ 'lien--nouveau': nouveau(s) }"
            :aria-current="actif === s.slug ? 'page' : undefined"
            :data-test="`barre-salon-${s.slug}`"
          >
            <span class="lien__signe" aria-hidden="true">#</span>
            <span class="lien__nom">{{ s.nom }}</span>
            <span v-if="nouveau(s)" class="lien__point"><span class="visuellement-cache">{{ t('communautes.salons.nouveaux') }}</span></span>
          </RouterLink>
        </li>
      </ul>

      <p class="barre__section">{{ t('communautes.salons.entraide') }}</p>
      <ul>
        <li>
          <RouterLink
            :to="{ path: `/communautes/${slug}`, query: { voir: 'questions' } }"
            class="lien"
            :aria-current="actif === 'questions' ? 'page' : undefined"
            data-test="barre-questions"
          >
            <svg class="lien__signe" viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
            <span class="lien__nom">{{ t('communautes.salons.questions') }}</span>
            <span v-if="fiche?.questionsOuvertes" class="lien__compte" :title="t('communautes.chiffres.questionsOuvertes')">{{ fiche.questionsOuvertes }}</span>
          </RouterLink>
        </li>
      </ul>
    </div>

    <RouterLink v-if="moi" to="/profil" class="barre__moi" :title="t('menu.profil')">
      <span class="barre__avatar"><JAvatar :nom="moi.nom" :url="moi.avatarUrl" :taille="32" /><span class="barre__en-ligne" aria-hidden="true" /></span>
      <span class="barre__qui">
        <strong>{{ moi.nom }}</strong>
        <span>{{ t('communautes.salons.enLigne') }}</span>
      </span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.barre { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; background: color-mix(in srgb, var(--surface-raised) 55%, var(--surface)); }
.barre__tete { padding: var(--space-4) var(--space-4) var(--space-3); border-bottom: 1px solid var(--line); }
.barre__nom { margin: 0; font-size: 1rem; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.barre__meta { margin: 2px 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.barre__corps { overflow-y: auto; padding: var(--space-2) var(--space-2) var(--space-4); }
.barre__section { margin: var(--space-4) var(--space-2) var(--space-1); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.barre ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 1px; }
.lien {
  display: flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-2);
  border-radius: var(--radius-sm); color: var(--ink-muted); text-decoration: none; font-weight: 500;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.lien:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.lien[aria-current='page'] { background: color-mix(in srgb, var(--ink) 10%, transparent); color: var(--ink); font-weight: 600; }
.lien--nouveau { color: var(--ink); font-weight: 700; }
.lien:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.lien__signe { flex-shrink: 0; width: 18px; text-align: center; font-size: 1.125rem; font-weight: 500; opacity: 0.7; }
.lien__nom { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lien__point { width: 8px; height: 8px; border-radius: 50%; background: var(--ink); flex-shrink: 0; }
.lien__compte {
  min-width: 20px; height: 20px; padding: 0 6px; border-radius: var(--radius-pill); display: grid; place-items: center;
  background: var(--danger); color: #fff; font-size: 0.75rem; font-weight: 700; font-variant-numeric: tabular-nums;
}
.barre__moi {
  display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--line); background: var(--surface-raised); color: var(--ink); text-decoration: none;
}
.barre__moi:hover { background: color-mix(in srgb, var(--ink) 6%, var(--surface-raised)); }
.barre__avatar { position: relative; flex-shrink: 0; display: grid; }
.barre__en-ligne { position: absolute; inset-inline-end: -2px; bottom: -2px; width: 12px; height: 12px; border-radius: 50%; background: #23a55a; border: 2px solid var(--surface-raised); }
.barre__qui { display: grid; min-width: 0; line-height: 1.2; }
.barre__qui strong { font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.barre__qui span { color: var(--ink-muted); font-size: 0.75rem; }

@media (max-width: 760px) {
  /* Téléphone : les salons en une rangée qui défile, sans le pied « moi » (déjà dans l'en-tête du site). */
  .barre { grid-template-rows: auto auto; }
  .barre__tete { display: none; }
  .barre__corps { display: flex; align-items: center; gap: var(--space-1); overflow-x: auto; overflow-y: hidden; padding: var(--space-2); scrollbar-width: none; }
  .barre__section { display: none; }
  .barre ul { display: flex; gap: var(--space-1); }
  .lien { white-space: nowrap; border: 1px solid var(--line); border-radius: var(--radius-pill); padding: 0 var(--space-3); }
  .barre__moi { display: none; }
}
</style>
