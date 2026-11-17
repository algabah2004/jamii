<script setup lang="ts">
import MotifsCoins from '../../components/MotifsCoins.vue'
import { JAvatar, JBadgePreuve, JBouton, JFiltres, JIllustration, JSquelette } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import CarteProjet from './CarteProjet.vue'
import { initiales, lienSur, teinte, type ProjetEnVitrine } from './vitrine'
import EnteteExploration from '../../components/exploration/EnteteExploration.vue'
import RechercheExploration from '../../components/exploration/RechercheExploration.vue'
import { correspond } from '../../components/exploration/recherche'

/** Une page de 12 cartes : 4 rangées de 3 sur ordinateur. */
const LIMITE = 12

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const seulementSystalink = computed(() => route.query.hebergeur === 'datacloud')
const projets = ref<ProjetEnVitrine[]>([])
const curseur = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const chargeLaSuite = ref(false)
const recherche = ref('')
const origine = ref<'tous' | 'cercle' | 'demande' | 'idee'>('tous')
/** Téléphone : les filtres sont repliés au départ (sur ordinateur, ils restent toujours visibles). */
const filtresOuverts = ref(false)
const nombreFiltres = computed(() => (seulementSystalink.value ? 1 : 0) + (origine.value === 'tous' ? 0 : 1))
const vedette = computed(() => projets.value[0])
const visibles = computed(() => projets.value.filter((p) => (origine.value === 'tous' || p.origine === origine.value) && correspond(recherche.value, [p.titre, p.accroche, ...p.equipe.map((m) => m.nom)])))
const statistiques = computed(() => [
  { valeur: projets.value.length, libelle: t('exploration.vitrine.statProjets', projets.value.length) },
  { valeur: new Set(projets.value.flatMap((p) => p.equipe.map((m) => m.id))).size, libelle: t('exploration.vitrine.statCreateurs', new Set(projets.value.flatMap((p) => p.equipe.map((m) => m.id))).size) },
  { valeur: new Set(projets.value.flatMap((p) => p.paysEquipe)).size, libelle: t('exploration.vitrine.statPays', new Set(projets.value.flatMap((p) => p.paysEquipe)).size) },
])

async function charger(suite = false) {
  if (suite) chargeLaSuite.value = true
  else etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/vitrine', {
      params: {
        query: {
          limite: LIMITE,
          curseur: suite ? (curseur.value ?? undefined) : undefined,
          hebergeur: seulementSystalink.value ? 'datacloud' : undefined,
        },
      },
    })
    if (!data) throw new Error('vitrine indisponible')
    projets.value = suite ? [...projets.value, ...(data.elements ?? [])] : (data.elements ?? [])
    curseur.value = data.curseurSuivant
    etat.value = 'pret'
  } catch {
    // Si la suite échoue, on garde les cartes déjà affichées.
    if (!suite) etat.value = 'indisponible'
  } finally {
    chargeLaSuite.value = false
  }
}
watch(seulementSystalink, () => charger(), { immediate: true })

function filtrer(datacloud: boolean) {
  // Le filtre vit dans l'adresse : un lien partagé montre la même vitrine.
  const { hebergeur: _ancien, ...reste } = route.query
  void router.replace({ query: datacloud ? { ...reste, hebergeur: 'datacloud' } : reste })
}
</script>

<template>
  <section class="avec-motifs vitrine exploration">
    <!-- Des taches de dessins sur les bords de la page, à son image. -->
    <MotifsCoins planche="vitrine" />
    <EnteteExploration :surtitre="t('exploration.vitrine.surtitre')" :titre="t('exploration.vitrine.titre')" :accent="t('exploration.vitrine.accent')" :intro="t('vitrine.intro')" :statistiques="statistiques">
      <template #actions><a href="#projets-vitrine" class="exploration__principal">{{ t('exploration.vitrine.explorer') }} <span aria-hidden="true">↗</span></a><RouterLink to="/cercles" class="exploration__secondaire">{{ t('exploration.vitrine.creer') }}</RouterLink></template>
      <p class="exploration__surtitre">{{ t('exploration.vitrine.vedette') }}</p>
      <template v-if="vedette && etat === 'pret'">
        <div class="vitrine__vedette-tete">
          <span class="vitrine__monogramme" :class="`vitrine__monogramme--${teinte(vedette.projetId)}`" aria-hidden="true">{{ initiales(vedette.titre) }}</span>
          <JBadgePreuve>✓ {{ vedette.hebergeur === 'datacloud' ? t('vitrine.hebergeSurSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
        </div>
        <h2>{{ vedette.titre }}</h2>
        <p>{{ vedette.accroche }}</p>
        <div class="exploration__pied-vedette">
          <div class="vitrine__vedette-equipe">
            <span class="vitrine__visages" aria-hidden="true"><JAvatar v-for="m in vedette.equipe.slice(0, 4)" :key="m.id" :nom="m.nom" :url="m.avatarUrl" :taille="32" /></span>
            <div><small>{{ t('exploration.vitrine.equipe') }}</small><strong>{{ t('exploration.vitrine.membres', { n: vedette.equipe.length }, vedette.equipe.length) }}</strong></div>
          </div>
          <a v-if="lienSur(vedette.url)" :href="lienSur(vedette.url)!" target="_blank" rel="noopener noreferrer nofollow ugc" class="exploration__fleche" :aria-label="`${t('vitrine.voir')} (${t('vitrine.nouvelOnglet')})`">↗</a>
        </div>
      </template>
      <template v-else><h2>{{ t('exploration.vitrine.attenteTitre') }}</h2><p>{{ t('exploration.vitrine.attente') }}</p></template>
    </EnteteExploration>

    <div class="exploration__outils" id="projets-vitrine">
      <RechercheExploration v-model="recherche" :libelle="t('exploration.vitrine.recherche')" />
      <!-- Téléphone : les filtres se replient derrière ce bouton pour laisser la place aux projets. -->
      <button type="button" class="vitrine__deplier" :aria-expanded="filtresOuverts" aria-controls="filtres-vitrine" data-test="deplier-filtres" @click="filtresOuverts = !filtresOuverts">
        {{ t('exploration.filtres') }}<span v-if="nombreFiltres" class="vitrine__nombre">{{ nombreFiltres }}</span>
        <svg aria-hidden="true" viewBox="0 0 16 16"><path d="M4 6l4 4 4-4" /></svg>
      </button>
      <div id="filtres-vitrine" class="vitrine__filtres" :class="{ 'vitrine__filtres--ouvert': filtresOuverts }">
        <JFiltres
          :model-value="seulementSystalink ? 'systalink' : 'tous'"
          :libelle="t('vitrine.filtre')"
          :options="[
            { valeur: 'tous', texte: t('vitrine.tous') },
            { valeur: 'systalink', texte: t('vitrine.surSystalink') },
          ]"
          @update:model-value="(v) => filtrer(v === 'systalink')"
        />
        <div class="exploration__filtres" role="group" :aria-label="t('exploration.vitrine.origine')"><button v-for="o in (['tous', 'cercle', 'demande', 'idee'] as const)" :key="o" type="button" :aria-pressed="origine === o" :data-test="`origine-${o}`" @click="origine = o">{{ t(`exploration.vitrine.origines.${o}`) }}</button></div>
      </div>
      <span v-if="curseur" class="texte-discret">{{ t('exploration.dansLaListe') }}</span>
    </div>

    <JSquelette v-if="etat === 'chargement'" forme="cartes" :nombre="6" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <div v-else-if="projets.length === 0" class="etat-vide">
      <JIllustration nom="en-ligne" />
      <p data-test="vide">{{ t('vitrine.vide') }}</p>
    </div>

    <template v-else>
      <div class="exploration__resultats"><h2>{{ t('exploration.vitrine.resultats') }}</h2><p aria-live="polite">{{ t('exploration.resultats', { n: visibles.length }, visibles.length) }}</p></div>
      <div v-if="!visibles.length" class="exploration__vide" data-test="sans-resultat"><h2>{{ t('exploration.aucunResultat') }}</h2><p>{{ t('exploration.essayerAutrement') }}</p><button type="button" class="exploration__effacer" @click="recherche = ''; origine = 'tous'">{{ t('exploration.reinitialiser') }}</button></div>
      <ul v-else class="grille-cartes" data-test="projets">
        <li v-for="p in visibles" :key="p.projetId">
          <CarteProjet :projet="p" illustration />
        </li>
      </ul>
      <JBouton v-if="curseur" variante="secondaire" class="suite" :occupe="chargeLaSuite" @click="charger(true)">
        {{ chargeLaSuite ? t('etat.chargement') : t('vitrine.plus') }}
      </JBouton>
    </template>
  </section>
</template>

<style scoped>
.vitrine__filtres { display: flex; flex-wrap: wrap; gap: 16px 24px; justify-content: space-between; align-items: center; }
.vitrine__deplier { display: none; }
/* Téléphone : les filtres se replient derrière un bouton ; dépliés, chaque groupe tient sur une ligne qui défile. */
@media (max-width: 680px) {
  .vitrine__deplier { display: inline-flex; align-items: center; gap: 8px; justify-self: start; min-height: 44px; padding: 0 4px; border: 0; background: transparent; color: var(--ink); font: inherit; font-weight: 700; cursor: pointer; }
  .vitrine__deplier svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; transition: transform 150ms ease; }
  .vitrine__deplier[aria-expanded='true'] svg { transform: rotate(180deg); }
  .vitrine__deplier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: 8px; }
  .vitrine__nombre { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 7px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: .75rem; font-weight: 700; }
  .vitrine__filtres { display: none; flex-direction: column; align-items: stretch; gap: 12px; }
  .vitrine__filtres--ouvert { display: flex; }
  .vitrine__filtres > * { min-width: 0; max-width: 100%; }
  .vitrine__filtres :deep(.j-filtres) { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; }
  .vitrine__filtres :deep(.j-filtres button) { flex: none; white-space: nowrap; }
}
@media (max-width: 680px) and (prefers-reduced-motion: reduce) { .vitrine__deplier svg { transition: none; } }
.grille-cartes { gap: 24px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr)); }
.grille-cartes > li { min-width: 0; }
.suite { display: flex; margin: var(--space-8) auto 0; }
/* La vedette montre le premier projet pour de vrai : son monogramme, sa preuve, son équipe et son lien. */
.vitrine__vedette-tete { display: flex; align-items: center; gap: var(--space-4); }
.vitrine__monogramme { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; font: 700 1.5rem var(--font-display); letter-spacing: -.02em; transform: rotate(-6deg); box-shadow: var(--ombre-carte); }
.vitrine__monogramme--action { background: var(--action-soft); color: var(--action-ink); }
.vitrine__monogramme--preuve { background: var(--proof-soft); color: var(--proof-ink); }
.vitrine__vedette-equipe { display: flex; align-items: center; gap: var(--space-3); }
.vitrine__vedette-equipe > div { display: grid; }
.vitrine__visages { display: flex; }
.vitrine__visages > :deep(*) { box-shadow: 0 0 0 2px var(--surface); border-radius: 50%; }
.vitrine__visages > :deep(* + *) { margin-inline-start: -8px; }
</style>
