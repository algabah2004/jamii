<script setup lang="ts">
import { JBouton, JFiltres, JIllustration, JSquelette } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import CarteProjet from './CarteProjet.vue'
import type { ProjetEnVitrine } from './vitrine'

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
  <section class="vitrine">
    <header class="page-tete">
      <div class="page-tete__texte">
        <h1>{{ t('vitrine.titre') }}</h1>
        <p>{{ t('vitrine.intro') }}</p>
      </div>
      <JFiltres
        :model-value="seulementSystalink ? 'systalink' : 'tous'"
        :libelle="t('vitrine.filtre')"
        :options="[
          { valeur: 'tous', texte: t('vitrine.tous') },
          { valeur: 'systalink', texte: t('vitrine.surSystalink') },
        ]"
        @update:model-value="(v) => filtrer(v === 'systalink')"
      />
    </header>

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
      <ul class="grille-cartes" data-test="projets">
        <li v-for="p in projets" :key="p.projetId">
          <CarteProjet :projet="p" />
        </li>
      </ul>
      <JBouton v-if="curseur" variante="secondaire" class="suite" :occupe="chargeLaSuite" @click="charger(true)">
        {{ chargeLaSuite ? t('etat.chargement') : t('vitrine.plus') }}
      </JBouton>
    </template>
  </section>
</template>

<style scoped>
.suite { display: flex; margin: var(--space-8) auto 0; }
</style>
