<script setup lang="ts">
import { JBadgePreuve, JBouton, JFiltres } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import { nomPays } from '../systalink/calculs'
import { ilYa, lienSur, type ProjetEnVitrine } from './vitrine'

/** Une page de 12 cartes : 4 rangées de 3 sur ordinateur. */
const LIMITE = 12

const { t, locale } = useI18n()
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
    <header>
      <h1>{{ t('vitrine.titre') }}</h1>
      <p>{{ t('vitrine.intro') }}</p>
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

    <p v-if="etat === 'chargement'" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <p v-else-if="projets.length === 0" data-test="vide">{{ t('vitrine.vide') }}</p>

    <template v-else>
      <ul class="cartes" data-test="projets">
        <li v-for="p in projets" :key="p.projetId" class="carte">
          <p v-if="p.hebergeur === 'datacloud'" class="badge" data-test="badge-systalink">
            <JBadgePreuve>✓ {{ t('vitrine.hebergeSurSystalink') }}</JBadgePreuve>
          </p>
          <h2>{{ p.titre }}</h2>
          <p v-if="p.origine === 'demande'" class="discret">{{ t('vitrine.pourUnePme') }}</p>
          <p>{{ p.accroche }}</p>
          <p class="discret">
            {{ t('vitrine.equipe', { noms: p.equipe.map((m) => m.nom).join(', ') }) }}
            <br />
            {{ p.paysEquipe.map((code) => nomPays(code, locale)).join(' · ') }}
          </p>
          <p class="pied">
            <a
              v-if="lienSur(p.url)"
              :href="lienSur(p.url)!"
              target="_blank"
              rel="noopener noreferrer nofollow ugc"
              class="j-bouton j-bouton--principal"
            >
              {{ t('vitrine.voir') }}
            </a>
            <span class="discret">{{ t('vitrine.verifie', { quand: ilYa(p.verifieLe, locale) }) }}</span>
          </p>
        </li>
      </ul>
      <JBouton v-if="curseur" variante="secondaire" class="suite" :occupe="chargeLaSuite" @click="charger(true)">
        {{ chargeLaSuite ? t('etat.chargement') : t('vitrine.plus') }}
      </JBouton>
    </template>
  </section>
</template>

<style scoped>
.cartes { list-style: none; padding: 0; margin: 1.5rem 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); gap: 1rem; }
.carte { display: flex; flex-direction: column; background: var(--surface); border: 1px solid var(--ligne); border-radius: 0.5rem; padding: 1rem; }
.carte h2 { font-size: 1.125rem; margin: 0.25rem 0; }
.carte p { margin: 0.375rem 0; }
/* Orange = la preuve (tokens.json) : le projet tourne sur DataCloud. Texte midnight sur orange (7,4:1). */
.badge { align-self: flex-start; margin: 0; }
.pied { margin-top: auto !important; padding-top: 0.5rem; display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; }
.discret { color: var(--discret); font-size: 0.875rem; }
.suite { display: flex; margin: 0 auto; }
</style>
