<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/systalink'
import { nomPays } from './calculs'

/*
 * Les tendances du marché (contracts/systalink.yaml, `/api/systalink/tendances`) : des chiffres agrégés et anonymes,
 * calculés à partir des besoins décrits sur Jamii. Aucune donnée personnelle, donc aucun accord à demander.
 */
type Tendances = components['schemas']['Tendances']
type Periode = components['schemas']['Periode']

const props = defineProps<{ periode: Periode }>()
const { t, locale } = useI18n()

const tendances = ref<Tendances | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

watch(
  () => props.periode,
  async (periode) => {
    etat.value = 'chargement'
    try {
      const { data } = await api.GET('/api/systalink/tendances', { params: { query: { periode } } })
      tendances.value = data ?? null
      etat.value = data ? 'pret' : 'indisponible'
    } catch {
      etat.value = 'indisponible'
    }
  },
  { immediate: true },
)

/** Une barre par ligne, proportionnelle à la plus grande valeur du bloc. */
function barres<T>(lignes: T[], valeur: (l: T) => number) {
  const max = Math.max(1, ...lignes.map(valeur))
  return lignes.map((l) => ({ ligne: l, part: valeur(l) / max }))
}
const secteurs = computed(() => barres(tendances.value?.secteurs ?? [], (s) => s.besoins))
const services = computed(() => barres(tendances.value?.services ?? [], (s) => s.suggestions))
const pays = computed(() => barres(tendances.value?.pays ?? [], (p) => p.besoins))
const manquantes = computed(() => tendances.value?.competencesManquantes ?? [])

const libelleSecteur = (s: string) => (s === 'autres' ? t('systalink.tendances.autres') : s.charAt(0).toUpperCase() + s.slice(1))
const libellePays = (p: string) => (p === 'autres' ? t('systalink.autresPays') : nomPays(p, locale.value))
</script>

<template>
  <section class="carte bloc tendances" aria-labelledby="tendances" data-test="tendances">
    <div>
      <h2 id="tendances">{{ t('systalink.tendances.titre') }}</h2>
      <p class="texte-discret">{{ t('systalink.tendances.intro') }}</p>
    </div>

    <p v-if="etat === 'indisponible'" class="texte-discret" role="status">{{ t('etat.indisponible') }}</p>
    <p v-else-if="etat === 'pret' && tendances && tendances.besoins === 0" class="texte-discret" data-test="tendances-vide">{{ t('systalink.tendances.vide') }}</p>

    <template v-else-if="tendances">
      <p class="total"><strong>{{ tendances.besoins }}</strong> {{ t('systalink.tendances.besoins', tendances.besoins) }}</p>
      <div class="grille">
        <div>
          <h3>{{ t('systalink.tendances.secteurs') }}</h3>
          <ul class="barres">
            <li v-for="{ ligne, part } in secteurs" :key="ligne.secteur">
              <span>{{ libelleSecteur(ligne.secteur) }}</span>
              <span class="barre" aria-hidden="true"><i :style="{ width: `${Math.max(part * 100, 3)}%` }" /></span>
              <span class="nombre">{{ ligne.besoins }}</span>
            </li>
          </ul>
        </div>
        <div>
          <h3>{{ t('systalink.tendances.services') }}</h3>
          <ul class="barres" data-test="tendances-services">
            <li v-for="{ ligne, part } in services" :key="ligne.service">
              <span>{{ ligne.service }}</span>
              <span class="barre barre--systalink" aria-hidden="true"><i :style="{ width: `${Math.max(part * 100, 3)}%` }" /></span>
              <span class="nombre">{{ ligne.suggestions }}</span>
            </li>
          </ul>
        </div>
        <div>
          <h3>{{ t('systalink.tendances.pays') }}</h3>
          <ul class="barres">
            <li v-for="{ ligne, part } in pays" :key="ligne.pays">
              <span>{{ libellePays(ligne.pays) }}</span>
              <span class="barre" aria-hidden="true"><i :style="{ width: `${Math.max(part * 100, 3)}%` }" /></span>
              <span class="nombre">{{ ligne.besoins }}</span>
            </li>
          </ul>
        </div>
      </div>

      <div v-if="manquantes.length">
        <h3>{{ t('systalink.tendances.manquantes') }}</h3>
        <p class="texte-discret">{{ t('systalink.tendances.manquantesAide') }}</p>
        <table data-test="competences-manquantes">
          <thead>
            <tr>
              <th scope="col">{{ t('systalink.tendances.competence') }}</th>
              <th scope="col">{{ t('systalink.tendances.demandes') }}</th>
              <th scope="col">{{ t('systalink.tendances.profils') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in manquantes" :key="c.competence" :class="{ manque: c.profils < c.demandes }">
              <th scope="row">{{ c.competence }}</th>
              <td>{{ c.demandes }}</td>
              <td>{{ c.profils }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.tendances { display: grid; gap: var(--space-6); }
.tendances h2 { margin: 0; }
.tendances h3 { margin: 0 0 var(--space-3); font-size: 1rem; }
.total { margin: 0; color: var(--ink-muted); }
.total strong { font-size: var(--type-h2-size); color: var(--ink); margin-inline-end: var(--space-1); }
.grille { display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr)); }
.barres { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.barres li { display: grid; grid-template-columns: minmax(0, 7.5rem) 1fr auto; align-items: center; gap: var(--space-3); }
.barres li > span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.barre { height: 8px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 8%, transparent); overflow: hidden; }
.barre i { display: block; height: 100%; border-radius: inherit; background: var(--action); }
.barre--systalink i { background: var(--cantaloupe); }
.nombre { font-variant-numeric: tabular-nums; font-weight: 600; }
table { width: 100%; border-collapse: collapse; font-size: var(--type-body-sm-size); }
th, td { padding: var(--space-2) var(--space-3); text-align: start; border-bottom: 1px solid var(--line); }
td { font-variant-numeric: tabular-nums; }
thead th { color: var(--ink-muted); font-weight: 600; }
.manque td:last-child { color: var(--danger); font-weight: 700; }
</style>
