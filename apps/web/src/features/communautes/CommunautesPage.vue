<script setup lang="ts">
import { JBouton, JSquelette } from '../../design-system'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'
import { monogramme } from './communautes'
import { THEMES, etiquettesDe, teinteDe } from './identite'

type Communaute = components['schemas']['Communaute']

/*
 * Toutes les communautés : en créer une, chercher, filtrer par thème. Les miennes d'abord, puis celles à découvrir,
 * chacune avec sa mini-bannière. Le thème choisi reste dans l'adresse (`?theme=fintech`).
 */
const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const communautes = ref<Communaute[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes', { params: { query: { limite: 100 } } })
    if (!data) throw new Error('communautés indisponibles')
    communautes.value = data.elements ?? []
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

const recherche = ref('')
const theme = computed(() => (typeof route.query.theme === 'string' && (THEMES as readonly string[]).includes(route.query.theme) ? route.query.theme : null))
function choisirTheme(th: string | null) {
  void router.replace({ query: th ? { theme: th } : {} })
}
/** Seulement les thèmes qui ont au moins une communauté, pour ne pas proposer de filtre vide. */
const themesPresents = computed(() => THEMES.filter((th) => communautes.value.some((c) => c.theme === th)))

const filtrees = computed(() => {
  const q = recherche.value.trim().toLocaleLowerCase(locale.value)
  return communautes.value.filter((c) =>
    (!theme.value || c.theme === theme.value)
    && (!q || `${c.nom} ${c.description}`.toLocaleLowerCase(locale.value).includes(q)))
})
const groupes = computed(() =>
  [
    { cle: 'miennes', liste: filtrees.value.filter((c) => c.membre) },
    { cle: 'aDecouvrir', liste: filtrees.value.filter((c) => !c.membre) },
  ].filter((g) => g.liste.length > 0),
)

const rejointes = ref<Set<string>>(new Set())
const enCours = ref<string | null>(null)
async function rejoindre(c: Communaute) {
  enCours.value = c.slug
  try {
    const { response } = await api.POST('/api/communautes/{slug}/adhesion', { params: { path: { slug: c.slug } } })
    if (response.ok) await router.push(`/communautes/${c.slug}`)
  } finally {
    enCours.value = null
  }
}
</script>

<template>
  <section class="page">
    <header class="tete">
      <div class="tete__texte">
        <h1>{{ t('communautes.titre') }}</h1>
        <p>{{ t('communautes.liste.intro') }}</p>
      </div>
      <RouterLink to="/communautes/nouvelle" class="j-bouton j-bouton--principal" data-test="creer-communaute">＋ {{ t('communautes.liste.creer') }}</RouterLink>
    </header>

    <div class="outils">
      <label class="recherche">
        <span class="visuellement-cache">{{ t('communautes.liste.chercher') }}</span>
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="m13.5 13.5 3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        <input v-model="recherche" type="search" :placeholder="t('communautes.liste.chercher')" data-test="chercher-communaute" />
      </label>
      <nav class="themes" :aria-label="t('communautes.liste.themes')">
        <button type="button" class="theme" :aria-pressed="!theme" @click="choisirTheme(null)">{{ t('communautes.liste.toutes') }}</button>
        <button
          v-for="th in themesPresents"
          :key="th"
          type="button"
          class="theme"
          :aria-pressed="theme === th"
          :data-test="`theme-${th}`"
          @click="choisirTheme(th)"
        >
          {{ t(`communautes.themes.${th}`) }}
        </button>
      </nav>
    </div>

    <JSquelette v-if="etat === 'chargement'" forme="cartes" :nombre="6" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <div v-else-if="groupes.length === 0" class="vide" data-test="aucune">
      <p>{{ t('communautes.liste.aucune') }}</p>
      <RouterLink to="/communautes/nouvelle" class="j-bouton j-bouton--principal">＋ {{ t('communautes.liste.creerLaTienne') }}</RouterLink>
    </div>
    <template v-else>
      <section v-for="g in groupes" :key="g.cle" class="groupe" :aria-labelledby="`groupe-${g.cle}`">
        <h2 :id="`groupe-${g.cle}`">{{ t(`communautes.groupes.${g.cle}`) }} <span class="groupe__nombre">{{ g.liste.length }}</span></h2>
        <ul class="grille" data-test="communautes">
          <!-- Une carte sobre : la couleur de la communauté ne vit que dans son monogramme. -->
          <li v-for="c in g.liste" :key="c.id" class="carte" :style="{ '--fond': teinteDe(c.slug).fond, '--encre': teinteDe(c.slug).encre }">
            <div class="carte__corps">
              <div class="carte__tete">
                <span class="carte__monogramme" aria-hidden="true">{{ monogramme(c.nom) }}</span>
                <div>
                  <h3><RouterLink :to="`/communautes/${c.slug}`" class="carte__lien">{{ c.nom }}</RouterLink></h3>
                  <p class="carte__meta">
                    {{ t(`communautes.themes.${c.theme}`, c.theme) }}<template v-if="c.pays"> · {{ nomPays(c.pays, locale) }}</template>
                  </p>
                </div>
              </div>
              <p class="carte__description">{{ c.description }}</p>
              <ul class="carte__mots" :aria-label="t('communautes.liste.motsCles')">
                <li v-for="m in etiquettesDe(c.theme).slice(0, 2)" :key="m">{{ m }}</li>
              </ul>
              <div class="carte__pied">
                <span><strong>{{ c.nombreMembres.toLocaleString(locale) }}</strong> {{ t('communautes.chiffres.membres') }}</span>
                <span v-if="c.questionsOuvertes" class="a-aider"><strong>{{ c.questionsOuvertes }}</strong> {{ t('communautes.chiffres.questionsOuvertes') }}</span>
                <span v-if="c.membre" class="carte__deja" data-test="deja-membre">✓ {{ t('communautes.tuEnFaisPartie') }}</span>
                <JBouton
                  v-else-if="!rejointes.has(c.slug)"
                  variante="secondaire"
                  class="carte__rejoindre"
                  :occupe="enCours === c.slug"
                  :data-test="`rejoindre-${c.slug}`"
                  @click.stop="rejoindre(c)"
                >
                  {{ t('communautes.rejoindre') }}
                </JBouton>
              </div>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.page { display: grid; gap: var(--space-6); padding-top: var(--space-4); }
/* Un en-tête simple, sur le fond de la page : le titre, une phrase, l'action. */
.tete { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--space-4); }
.tete h1 { margin: 0; font-size: clamp(2rem, 4vw, 2.75rem); line-height: 1.05; letter-spacing: -0.03em; }
.tete p { margin: var(--space-2) 0 0; max-width: 40rem; color: var(--ink-muted); }

.outils { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.recherche {
  display: flex; align-items: center; gap: var(--space-2); flex: 1 1 18rem; min-height: 48px; padding: 0 var(--space-4);
  border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line); color: var(--ink-muted);
}
.recherche input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; }
.recherche:focus-within { outline: 2px solid var(--focus); outline-offset: 1px; }
.themes { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.theme {
  min-height: 36px; padding: 0 var(--space-4); border-radius: var(--radius-pill); border: 1px solid var(--line);
  background: var(--surface); color: var(--ink-muted); font: inherit; font-weight: 600; font-size: var(--type-body-sm-size); cursor: pointer;
}
.theme:hover { color: var(--ink); border-color: var(--line-strong); }
.theme[aria-pressed='true'] { background: var(--ink); color: var(--bg); border-color: var(--ink); }
.theme:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.groupe h2 { display: flex; align-items: center; gap: var(--space-2); margin: 0 0 var(--space-4); font-size: var(--type-h3-size); }
.groupe__nombre { padding: 0 10px; border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted); font-size: 0.875rem; }
.grille { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr)); }

.carte {
  position: relative; display: flex; flex-direction: column; overflow: hidden;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.carte:hover { transform: translateY(-3px); border-color: var(--line-strong); }
.carte:has(.carte__lien:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .carte:hover { transform: none; } }
.carte__corps { display: flex; flex-direction: column; gap: var(--space-3); flex: 1; padding: var(--space-5, 1.25rem); }
.carte__tete { display: flex; align-items: center; gap: var(--space-3); }
.carte__monogramme {
  flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px;
  background: var(--fond); color: var(--encre); font-family: var(--font-display); font-weight: 700; font-size: 1rem;
  /* Un contour fin : une teinte sombre reste visible sur une carte sombre. */
  box-shadow: inset 0 0 0 1px var(--line-strong);
}
.carte__mots { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.carte__mots li { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted); font-size: 0.75rem; font-weight: 600; }
.carte h3 { margin: 0; font-size: 1.125rem; line-height: 1.25; }
.carte__lien { color: var(--ink); text-decoration: none; }
.carte__lien::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.carte__lien:focus-visible { outline: none; }
.carte__meta { margin: 2px 0 0; color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.carte__description { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.carte__pied { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); margin-top: auto; padding-top: var(--space-3); min-height: 44px; border-top: 1px solid var(--line); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.carte__pied strong { color: var(--ink); font-variant-numeric: tabular-nums; }
.a-aider strong { color: var(--action-ink); }
.carte__deja { margin-inline-start: auto; color: var(--ink); font-weight: 600; }
/* Le bouton passe au-dessus du lien qui couvre la carte. */
.carte__rejoindre { position: relative; z-index: 1; margin-inline-start: auto; }

.vide { display: grid; justify-items: center; gap: var(--space-4); padding: var(--space-12) var(--space-6); text-align: center; color: var(--ink-muted); border: 1px dashed var(--line-strong); border-radius: var(--radius-lg); }
.vide p { margin: 0; }
</style>
