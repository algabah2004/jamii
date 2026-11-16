<script setup lang="ts">
import MotifsCoins from '../../components/MotifsCoins.vue'
import { JBouton, JFiltres, JIllustration, JSquelette } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import CarteProjet from './CarteProjet.vue'
import type { ProjetEnVitrine } from './vitrine'
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
      <div class="exploration__browser" aria-hidden="true"><div class="exploration__browser-barre"><i /><i /><i /><span>jamii / showcase</span></div><div class="exploration__browser-corps"><i /><i /><i /><i /></div></div>
      <template v-if="vedette && etat === 'pret'"><h2>{{ vedette.titre }}</h2><p>{{ vedette.accroche }}</p><div class="exploration__pied-vedette"><div><small>{{ t('exploration.vitrine.equipe') }}</small><strong>{{ t('exploration.vitrine.membres', { n: vedette.equipe.length }, vedette.equipe.length) }}</strong></div><a href="#projets-vitrine" class="exploration__fleche" :aria-label="t('exploration.vitrine.explorer')">↓</a></div></template>
      <template v-else><h2>{{ t('exploration.vitrine.attenteTitre') }}</h2><p>{{ t('exploration.vitrine.attente') }}</p></template>
    </EnteteExploration>

    <div class="exploration__outils" id="projets-vitrine">
      <RechercheExploration v-model="recherche" :libelle="t('exploration.vitrine.recherche')" />
      <div class="vitrine__filtres">
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
.grille-cartes { gap: 24px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr)); }
.grille-cartes > li { min-width: 0; }
.suite { display: flex; margin: var(--space-8) auto 0; }
</style>
