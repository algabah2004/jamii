<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import { PERIODES, lirePeriode, momentsTries, nomPays, pourcentage, type Periode, type TableauDeBord } from './calculs'

const { t, locale, d } = useI18n()
const route = useRoute()
const router = useRouter()

const periode = computed(() => lirePeriode(route.query.periode))
const tableau = ref<TableauDeBord | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/systalink/tableau-de-bord', { params: { query: { periode: periode.value } } })
    tableau.value = data ?? null
    etat.value = data ? 'pret' : 'indisponible'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(periode, charger, { immediate: true })

function choisirPeriode(p: Periode) {
  // La période vit dans l'adresse : un lien partagé montre la même vue.
  void router.replace({ query: { ...route.query, periode: p } })
}

const partDatacloud = computed(() =>
  tableau.value ? pourcentage(tableau.value.projets.surDatacloud, tableau.value.projets.enLigne) : null,
)
const moments = computed(() => (tableau.value ? momentsTries(tableau.value) : []))
const pays = computed(() => {
  if (!tableau.value) return []
  const { parPays, autres } = tableau.value.inscrits
  const lignes = parPays.map((p) => ({ nom: nomPays(p.pays, locale.value), nombre: p.nombre, regroupe: false }))
  if (autres > 0) lignes.push({ nom: t('systalink.autresPays'), nombre: autres, regroupe: true })
  return lignes
})
const maxPays = computed(() => Math.max(1, ...pays.value.map((p) => p.nombre)))
</script>

<template>
  <section class="tableau">
    <header>
      <h1>{{ t('systalink.titre') }}</h1>
      <p>{{ t('systalink.intro') }}</p>
      <div class="periodes" role="group" :aria-label="t('systalink.periode')">
        <button
          v-for="p in PERIODES"
          :key="p"
          type="button"
          :aria-pressed="p === periode"
          @click="choisirPeriode(p)"
        >
          {{ t(`systalink.periodes.${p}`) }}
        </button>
      </div>
    </header>

    <p v-if="etat === 'chargement' && !tableau" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <button type="button" @click="charger">{{ t('etat.reessayer') }}</button>
    </div>

    <template v-else-if="tableau">
      <div class="principal" data-test="datacloud">
        <p class="chiffre">{{ tableau.projets.surDatacloud }}</p>
        <p>{{ t('systalink.surDatacloud') }}</p>
        <template v-if="partDatacloud !== null">
          <span class="jauge" aria-hidden="true"><span :style="{ width: `${partDatacloud}%` }" /></span>
          <p class="discret">{{ t('systalink.partDatacloud', { part: partDatacloud }) }}</p>
        </template>
      </div>

      <dl class="tuiles">
        <div>
          <dt>{{ t('systalink.inscrits') }}</dt>
          <dd>{{ tableau.inscrits.total }}</dd>
          <dd class="discret">{{ t('systalink.nouveaux', { n: tableau.inscrits.nouveaux }) }}</dd>
        </div>
        <div>
          <dt>{{ t('systalink.projetsCrees') }}</dt>
          <dd>{{ tableau.projets.crees }}</dd>
          <dd class="discret">{{ t('systalink.nouveaux', { n: tableau.projets.nouveaux }) }}</dd>
        </div>
        <div>
          <dt>{{ t('systalink.projetsEnLigne') }}</dt>
          <dd>{{ tableau.projets.enLigne }}</dd>
        </div>
      </dl>

      <h2>{{ t('systalink.moments') }}</h2>
      <p>{{ t('systalink.momentsIntro') }}</p>
      <table v-if="moments.length" data-test="moments">
        <thead>
          <tr>
            <th scope="col">{{ t('systalink.service') }}</th>
            <th scope="col">{{ t('systalink.proposes') }}</th>
            <th scope="col">{{ t('systalink.acceptes') }}</th>
            <th scope="col">{{ t('systalink.taux') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in moments" :key="m.service">
            <th scope="row">{{ t(`systalink.services.${m.service}`) }}</th>
            <td>{{ m.proposes }}</td>
            <td>{{ m.acceptes }}</td>
            <td class="avec-jauge">
              <span class="taux">{{ m.taux === null ? '—' : `${m.taux} %` }}</span>
              <span class="jauge" aria-hidden="true"><span :style="{ width: `${m.taux ?? 0}%` }" /></span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else>{{ t('systalink.momentsVide') }}</p>
      <p class="discret">{{ t('systalink.nonMesure') }}</p>

      <h2>{{ t('systalink.parPays') }}</h2>
      <ul class="pays" data-test="pays">
        <li v-for="p in pays" :key="p.nom" :class="{ regroupe: p.regroupe }">
          <span>{{ p.nom }}</span>
          <span class="jauge" aria-hidden="true"><span :style="{ width: `${(p.nombre / maxPays) * 100}%` }" /></span>
          <span class="nombre">{{ p.nombre }}</span>
        </li>
      </ul>
      <p class="discret">{{ t('systalink.seuilPays') }}</p>

      <p class="discret">
        {{ t('systalink.calculeLe', { date: d(new Date(tableau.calculeLe), { dateStyle: 'medium', timeStyle: 'short' }) }) }}
      </p>
    </template>
  </section>
</template>

<style scoped>
/* Valeurs de docs/design/tokens.json (thème clair). Le design system (#19) les fournira en variables. */
.tableau { --surface: #fff; --relief: #eaf1f5; --ligne: #d3dee5; --action: #0297de; --preuve: #fc734d; --discret: #4a5a65; }
.periodes { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.periodes button { border: 1px solid var(--ligne); background: var(--surface); color: inherit; border-radius: 999px; padding: 0.375rem 0.75rem; cursor: pointer; }
.periodes button[aria-pressed='true'] { background: var(--action); border-color: var(--action); font-weight: 600; }
.principal, .tuiles > div { background: var(--surface); border: 1px solid var(--ligne); border-radius: 0.5rem; padding: 1rem; }
.principal { margin: 1.5rem 0 1rem; }
.principal p { margin: 0.25rem 0; }
.principal .jauge { max-width: 24rem; margin-top: 0.75rem; }
/* Orange = la preuve (tokens.json) : un projet en ligne sur DataCloud. */
.principal .jauge > span { background: var(--preuve); }
.chiffre { font-size: 3.5rem; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; }
.tuiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 1rem; margin: 0; }
.tuiles dt { color: var(--discret); }
.tuiles dd { margin: 0.25rem 0 0; font-size: 1.75rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.tuiles dd.discret { font-size: 0.875rem; font-weight: 400; }
.discret { color: var(--discret); font-size: 0.875rem; }
h2 { margin-top: 2rem; }
table { width: 100%; border-collapse: collapse; background: var(--surface); }
th, td { text-align: left; padding: 0.5rem; border-bottom: 1px solid var(--ligne); font-variant-numeric: tabular-nums; }
.avec-jauge { display: flex; align-items: center; gap: 0.5rem; }
.taux { min-width: 3rem; }
.jauge { display: block; flex: 1; height: 0.5rem; background: var(--relief); border-radius: 4px; overflow: hidden; }
.jauge > span { display: block; height: 100%; background: var(--action); border-radius: 0 4px 4px 0; }
.pays { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.5rem; }
.pays li { display: grid; grid-template-columns: minmax(6rem, 10rem) 1fr 2.5rem; align-items: center; gap: 0.75rem; }
.nombre { text-align: right; font-variant-numeric: tabular-nums; }
/* « Autres pays » est un regroupement, pas un pays : barre neutre. */
.regroupe .jauge > span { background: var(--discret); }
/* Sur téléphone, la jauge du taux serait illisible : le pourcentage écrit suffit. */
@media (max-width: 30rem) { .avec-jauge .jauge { display: none; } }
</style>
