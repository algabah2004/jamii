<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar } from '../../design-system'
import { useSession } from '../../session'
import { monogramme } from './communautes'
import { teinteDe } from './identite'
import { aDuNouveau } from './lus'

type Communaute = components['schemas']['Communaute']
type Salon = components['schemas']['Salon']

/*
 * La barre d'une communauté : passer d'une communauté à l'autre, le fil, les membres, « À propos », les salons en direct
 * (un point quand il y a du nouveau, relu toutes les 20 s) et des raccourcis.
 */
const props = defineProps<{ slug: string; actif: string; communaute?: Communaute | null }>()
const { t } = useI18n()
const { moi } = useSession()

const chargee = ref<Communaute | null>(null)
const fiche = computed(() => props.communaute ?? chargee.value)
const miennes = ref<Communaute[]>([])
const salons = ref<Salon[]>([])
const derniers = ref<Record<string, number>>({})

async function charger() {
  try {
    const [c, s, m] = await Promise.all([
      props.communaute ? Promise.resolve(null) : api.GET('/api/communautes/{slug}', { params: { path: { slug: props.slug } } }),
      api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } }),
      api.GET('/api/communautes', { params: { query: { limite: 50, membre: 'moi' } } }),
    ])
    if (c?.data) chargee.value = c.data
    salons.value = s.data?.elements ?? []
    miennes.value = m.data?.elements ?? []
    await relireDerniers()
  } catch {
    // La barre aide à naviguer ; sans elle, le centre reste utilisable.
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
const teinte = computed(() => teinteDe(props.slug))
const autres = computed(() => miennes.value.filter((c) => c.slug !== props.slug))
</script>

<template>
  <nav class="barre" :aria-label="fiche?.nom ?? t('communautes.titre')">
    <details class="choix">
      <summary class="choix__actuelle" data-test="choix-communaute">
        <span class="choix__monogramme" :style="{ background: teinte.fond, color: teinte.encre }" aria-hidden="true">{{ fiche ? monogramme(fiche.nom) : '…' }}</span>
        <span class="choix__nom">{{ fiche?.nom ?? '…' }}</span>
        <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="m6 8 4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
      </summary>
      <ul class="choix__liste">
        <li v-for="c in autres" :key="c.id">
          <RouterLink :to="`/communautes/${c.slug}`" class="lien">
            <span class="choix__mini" :style="{ background: teinteDe(c.slug).fond, color: teinteDe(c.slug).encre }" aria-hidden="true">{{ monogramme(c.nom) }}</span>
            <span class="lien__nom">{{ c.nom }}</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/communautes" class="lien lien--decouvrir">{{ t('communautes.espace.decouvrir') }}</RouterLink>
        </li>
        <li>
          <RouterLink to="/communautes/nouvelle" class="lien lien--decouvrir">＋ {{ t('communautes.liste.creer') }}</RouterLink>
        </li>
      </ul>
    </details>

    <div class="barre__corps">
      <ul class="groupe">
        <li>
          <RouterLink :to="{ path: `/communautes/${slug}` }" class="lien" :aria-current="actif === 'fil' ? 'page' : undefined" data-test="barre-fil">
            <svg class="lien__icone" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M4 5h12M4 10h12M4 15h7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
            <span class="lien__nom">{{ t('communautes.barre.fil') }}</span>
            <span v-if="fiche?.questionsOuvertes" class="lien__compte" :title="t('communautes.chiffres.questionsOuvertes')">{{ fiche.questionsOuvertes }}</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink :to="{ path: `/communautes/${slug}`, query: { voir: 'membres' } }" class="lien" :aria-current="actif === 'membres' ? 'page' : undefined" data-test="barre-membres">
            <svg class="lien__icone" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="8" cy="7" r="3" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="M2.5 16c.8-2.6 3-4 5.5-4s4.7 1.4 5.5 4M13.5 4.2a3 3 0 0 1 0 5.6M15 12.4c1.3.6 2.2 1.8 2.5 3.6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
            <span class="lien__nom">{{ t('communautes.barre.membres') }}</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink :to="{ path: `/communautes/${slug}`, query: { voir: 'apropos' } }" class="lien" :aria-current="actif === 'apropos' ? 'page' : undefined" data-test="barre-apropos">
            <svg class="lien__icone" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="M10 9v5M10 6.2v.1" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg>
            <span class="lien__nom">{{ t('communautes.espace.apropos') }}</span>
          </RouterLink>
        </li>
      </ul>

      <p class="barre__section barre__section--action">
        <span>{{ t('communautes.salons.titre') }}</span>
        <RouterLink
          v-if="fiche?.moderateur"
          :to="`/communautes/${slug}/salons/nouveau`"
          class="ajouter"
          :aria-current="actif === 'nouveau-salon' ? 'page' : undefined"
          :title="t('communautes.nouveauSalon.titre')"
          data-test="ajouter-salon"
        >
          <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
          <span class="visuellement-cache">{{ t('communautes.nouveauSalon.titre') }}</span>
        </RouterLink>
      </p>
      <ul class="groupe">
        <li v-for="s in salons" :key="s.id">
          <RouterLink
            :to="{ path: `/communautes/${slug}`, query: { salon: s.slug } }"
            class="lien"
            :class="{ 'lien--nouveau': nouveau(s) }"
            :aria-current="actif === s.slug ? 'page' : undefined"
            :data-test="`barre-salon-${s.slug}`"
          >
            <span class="lien__icone lien__diese" aria-hidden="true">#</span>
            <span class="lien__nom">{{ s.nom }}</span>
            <span v-if="nouveau(s)" class="lien__point"><span class="visuellement-cache">{{ t('communautes.salons.nouveaux') }}</span></span>
          </RouterLink>
        </li>
      </ul>

      <p class="barre__section">{{ t('communautes.barre.raccourcis') }}</p>
      <ul class="groupe">
        <li><RouterLink to="/cercles" class="lien"><span class="lien__icone" aria-hidden="true">◎</span><span class="lien__nom">{{ t('menu.cercles') }}</span><span class="lien__fleche" aria-hidden="true">↗</span></RouterLink></li>
        <li><RouterLink to="/vitrine" class="lien"><span class="lien__icone" aria-hidden="true">▣</span><span class="lien__nom">{{ t('menu.vitrine') }}</span><span class="lien__fleche" aria-hidden="true">↗</span></RouterLink></li>
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
.barre {
  display: grid; grid-template-rows: auto minmax(0, 1fr) auto;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); overflow: hidden;
}

.choix { position: relative; border-bottom: 1px solid var(--line); }
.choix__actuelle {
  display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4); cursor: pointer; list-style: none;
  font-weight: 700;
}
.choix__actuelle::-webkit-details-marker { display: none; }
.choix__actuelle:hover { background: color-mix(in srgb, var(--ink) 4%, transparent); }
.choix__actuelle:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.choix__actuelle svg { color: var(--ink-muted); transition: transform var(--motion-fast) var(--ease-out); }
.choix[open] .choix__actuelle svg { transform: rotate(180deg); }
.choix__monogramme {
  flex-shrink: 0; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px;
  font-family: var(--font-display); font-weight: 700; font-size: 0.9375rem; transform: rotate(-4deg);
}
.choix__nom { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--font-display); font-size: 1.0625rem; }
.choix__liste { list-style: none; margin: 0; padding: var(--space-2); display: grid; gap: 1px; border-top: 1px solid var(--line); background: var(--surface-raised); }
.choix__mini { flex-shrink: 0; display: grid; place-items: center; width: 24px; height: 24px; border-radius: 7px; font-size: 0.6875rem; font-weight: 700; font-family: var(--font-display); }

.barre__corps { overflow-y: auto; padding: var(--space-3) var(--space-3) var(--space-4); scrollbar-width: thin; }
.barre__section { margin: var(--space-4) var(--space-2) var(--space-1); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 700; }
.barre__section--action { display: flex; align-items: center; justify-content: space-between; }
.ajouter { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 8px; color: var(--ink-muted); }
.ajouter:hover, .ajouter[aria-current='page'] { background: var(--ink); color: var(--bg); }
.ajouter:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.groupe { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.lien {
  display: flex; align-items: center; gap: var(--space-2); min-height: 38px; padding: 0 var(--space-3);
  border-radius: var(--radius-md); color: var(--ink-muted); text-decoration: none; font-weight: 500;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.lien:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.lien[aria-current='page'] { background: var(--ink); color: var(--bg); font-weight: 600; }
.lien--nouveau { color: var(--ink); font-weight: 700; }
.lien:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.lien__icone { flex-shrink: 0; width: 18px; display: grid; place-items: center; text-align: center; }
.lien__diese { font-size: 1.0625rem; opacity: 0.75; }
.lien__nom { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lien__point { width: 8px; height: 8px; border-radius: 50%; background: var(--cantaloupe); flex-shrink: 0; }
.lien__compte {
  min-width: 22px; height: 22px; padding: 0 7px; border-radius: var(--radius-pill); display: grid; place-items: center;
  background: var(--cantaloupe); color: var(--midnight); font-size: 0.75rem; font-weight: 700; font-variant-numeric: tabular-nums;
}
.lien__fleche { opacity: 0.6; font-size: 0.8125rem; }
.lien--decouvrir { color: var(--action-ink); font-weight: 600; }

.barre__moi {
  display: flex; align-items: center; gap: var(--space-2); padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--line); color: var(--ink); text-decoration: none;
}
.barre__moi:hover { background: color-mix(in srgb, var(--ink) 4%, transparent); }
.barre__avatar { position: relative; flex-shrink: 0; display: grid; }
.barre__en-ligne { position: absolute; inset-inline-end: -2px; bottom: -2px; width: 12px; height: 12px; border-radius: 50%; background: #23a55a; border: 2px solid var(--surface); }
.barre__qui { display: grid; min-width: 0; line-height: 1.2; }
.barre__qui strong { font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.barre__qui span { color: var(--ink-muted); font-size: 0.75rem; }

@media (max-width: 860px) {
  /* Téléphone : la communauté, puis une rangée de liens qui défile. Le reste est sur la page. */
  .barre { grid-template-rows: auto auto; }
  .barre__corps { display: flex; align-items: center; gap: var(--space-1); overflow-x: auto; overflow-y: hidden; padding: var(--space-2) var(--space-6) var(--space-2) var(--space-2); scrollbar-width: none;
    /* Un fondu sur le bord droit : il y a d'autres liens plus loin (docs/responsive.md, point 2). */
    -webkit-mask-image: linear-gradient(to right, #000 calc(100% - var(--space-8)), transparent);
    mask-image: linear-gradient(to right, #000 calc(100% - var(--space-8)), transparent); }
  .barre__corps :focus-visible { outline-offset: -2px; }
  .barre__section, .barre__moi { display: none; }
  .groupe { display: flex; gap: var(--space-1); }
  .groupe:last-of-type { display: none; }
  .lien { white-space: nowrap; border: 1px solid var(--line); border-radius: var(--radius-pill); }
  .lien__nom { overflow: visible; }
}
</style>
