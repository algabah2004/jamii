<script setup lang="ts">
import { JBouton, JFiltres, JIllustration, JSquelette } from '../../design-system'
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
    <header class="page-tete">
      <div class="page-tete__texte">
        <h1>{{ t('systalink.titre') }}</h1>
        <p>{{ t('systalink.intro') }}</p>
        <JFiltres
          class="periodes"
          :model-value="periode"
          :libelle="t('systalink.periode')"
          :options="PERIODES.map((p) => ({ valeur: p, texte: t(`systalink.periodes.${p}`) }))"
          @update:model-value="choisirPeriode"
        />
      </div>
      <JIllustration nom="nuage" class="page-tete__illustration" />
    </header>

    <JSquelette v-if="etat === 'chargement' && !tableau" forme="cartes" :nombre="4" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="tableau">
      <div class="chiffres" :aria-busy="etat === 'chargement' || undefined">
        <div class="principal zone-sombre" data-test="datacloud">
          <p class="principal__libelle">{{ t('systalink.surDatacloud') }}</p>
          <p class="chiffre">{{ tableau.projets.surDatacloud }}</p>
          <template v-if="partDatacloud !== null">
            <span class="jauge jauge--preuve" aria-hidden="true"><span :style="{ width: `${partDatacloud}%` }" /></span>
            <p class="principal__part">{{ t('systalink.partDatacloud', { part: partDatacloud }) }}</p>
          </template>
        </div>

        <dl class="tuiles">
          <div>
            <dt>{{ t('systalink.inscrits') }}</dt>
            <dd class="tuiles__nombre">{{ tableau.inscrits.total }}</dd>
            <dd class="tuiles__evolution">{{ t('systalink.nouveaux', { n: tableau.inscrits.nouveaux }) }}</dd>
          </div>
          <div>
            <dt>{{ t('systalink.projetsCrees') }}</dt>
            <dd class="tuiles__nombre">{{ tableau.projets.crees }}</dd>
            <dd class="tuiles__evolution">{{ t('systalink.nouveaux', { n: tableau.projets.nouveaux }) }}</dd>
          </div>
          <div>
            <dt>{{ t('systalink.projetsEnLigne') }}</dt>
            <dd class="tuiles__nombre">{{ tableau.projets.enLigne }}</dd>
          </div>
        </dl>
      </div>

      <div class="details">
        <section class="carte bloc" aria-labelledby="moments">
          <h2 id="moments">{{ t('systalink.moments') }}</h2>
          <p class="texte-discret">{{ t('systalink.momentsIntro') }}</p>
          <table v-if="moments.length" data-test="moments">
            <thead>
              <tr>
                <th scope="col">{{ t('systalink.service') }}</th>
                <th scope="col" class="nombre">{{ t('systalink.proposes') }}</th>
                <th scope="col" class="nombre">{{ t('systalink.acceptes') }}</th>
                <th scope="col">{{ t('systalink.taux') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in moments" :key="m.service">
                <th scope="row">{{ t(`systalink.services.${m.service}`) }}</th>
                <td class="nombre">{{ m.proposes }}</td>
                <td class="nombre">{{ m.acceptes }}</td>
                <td class="avec-jauge">
                  <span class="taux">{{ m.taux === null ? '—' : `${m.taux} %` }}</span>
                  <span class="jauge" aria-hidden="true"><span :style="{ width: `${m.taux ?? 0}%` }" /></span>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else>{{ t('systalink.momentsVide') }}</p>
          <p class="note">{{ t('systalink.nonMesure') }}</p>
        </section>

        <section class="carte bloc" aria-labelledby="par-pays">
          <h2 id="par-pays">{{ t('systalink.parPays') }}</h2>
          <ul class="pays" data-test="pays">
            <li v-for="p in pays" :key="p.nom" :class="{ regroupe: p.regroupe }">
              <span>{{ p.nom }}</span>
              <span class="jauge" aria-hidden="true"><span :style="{ width: `${(p.nombre / maxPays) * 100}%` }" /></span>
              <span class="nombre">{{ p.nombre }}</span>
            </li>
          </ul>
          <p class="note">{{ t('systalink.seuilPays') }}</p>
        </section>
      </div>

      <p class="note calcul">
        {{ t('systalink.calculeLe', { date: d(new Date(tableau.calculeLe), { dateStyle: 'medium', timeStyle: 'short' }) }) }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.chiffres { display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.4fr); transition: opacity var(--motion-base) var(--ease-out); }
.chiffres[aria-busy] { opacity: 0.6; }
@media (max-width: 860px) { .chiffres { grid-template-columns: 1fr; } }

/* Le chiffre qui compte pour Systalink, sur fond sombre ; l'orange de la jauge dit « en ligne, vérifié ». */
.principal { display: grid; gap: var(--space-2); align-content: center; padding: var(--space-8); border-radius: var(--radius-lg); }
.principal p { margin: 0; }
.principal__libelle { color: var(--ink-muted); font-weight: 600; }
.chiffre { font-family: var(--font-display); font-size: clamp(3.5rem, 8vw, 5.5rem); font-weight: 700; line-height: 1; letter-spacing: -0.04em; font-variant-numeric: tabular-nums; }
.principal__part { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.principal .jauge { margin-top: var(--space-3); max-width: 22rem; }

.tuiles { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); margin: 0; }
.tuiles > div { display: flex; flex-direction: column; justify-content: space-between; gap: var(--space-1); padding: var(--space-6); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.tuiles dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.tuiles dd { margin: 0; }
.tuiles__nombre { font-family: var(--font-display); font-size: 2.25rem; font-weight: 700; line-height: 1.1; font-variant-numeric: tabular-nums; }
.tuiles__evolution { color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; }

.details { display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); align-items: start; margin-top: var(--space-6); }
@media (max-width: 860px) { .details { grid-template-columns: 1fr; } }
.bloc { display: grid; gap: var(--space-3); }
.bloc h2 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.bloc > p { margin: 0; }
.note { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.calcul { margin-top: var(--space-6); }

table { width: 100%; border-collapse: collapse; }
th, td { text-align: start; padding: var(--space-3) var(--space-2); border-bottom: 1px solid var(--line); font-variant-numeric: tabular-nums; }
thead th { color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.nombre { text-align: end; }
.avec-jauge { display: flex; align-items: center; gap: var(--space-2); }
.taux { min-width: 3rem; }
.jauge { display: block; flex: 1; height: 8px; background: var(--surface-raised); border-radius: var(--radius-pill); overflow: hidden; }
.jauge > span { display: block; height: 100%; background: var(--action); border-radius: var(--radius-pill); }
/* Orange = la preuve (tokens.json) : un projet en ligne sur DataCloud. */
.jauge--preuve > span { background: var(--proof); }
.pays { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-3); }
.pays li { display: grid; grid-template-columns: minmax(6rem, 9rem) 1fr 2.5rem; align-items: center; gap: var(--space-3); }
/* « Autres pays » est un regroupement, pas un pays : barre neutre. */
.regroupe .jauge > span { background: var(--line-strong); }
/* Sur téléphone, la jauge du taux serait illisible : le pourcentage écrit suffit. */
@media (max-width: 30rem) { .avec-jauge .jauge { display: none; } }
</style>
