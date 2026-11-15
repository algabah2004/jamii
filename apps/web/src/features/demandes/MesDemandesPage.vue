<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JBouton, JIllustration, JSquelette } from '../../design-system'
import { dateCourte, listerDemandes, type DemandeResumee } from './demandes'

/*
 * « Tes demandes » (#199) : tous les besoins déjà décrits, comme sur un profil. On y revient pour relire une réponse,
 * la préciser ou la publier, et on en décrit un nouveau d'un clic. Le menu « Demandes » ouvre cette page.
 */
const { t } = useI18n()
const demandes = ref<DemandeResumee[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    demandes.value = await listerDemandes()
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

/** Où en est chaque demande : décrite, puis analysée (son résumé est là), puis publiée. */
type Etat = 'preparation' | 'prete' | 'publiee'
const etatDe = (d: DemandeResumee): Etat => (d.publiee ? 'publiee' : d.resume ? 'prete' : 'preparation')
const ETATS: Etat[] = ['preparation', 'prete', 'publiee']
const filtre = ref<Etat | 'toutes'>('toutes')
const compte = (e: Etat) => demandes.value.filter((d) => etatDe(d) === e).length
const affichees = computed(() => (filtre.value === 'toutes' ? demandes.value : demandes.value.filter((d) => etatDe(d) === filtre.value)))
</script>

<template>
  <section class="mes-demandes fond-verre">
    <header class="page-tete motif motif--croix">
      <div class="page-tete__texte">
        <h1>{{ t('demandes.mesDemandes.titre') }}</h1>
        <p>{{ t('demandes.mesDemandes.intro') }}</p>
        <RouterLink to="/demandes/nouvelle" class="j-bouton j-bouton--principal nouveau" data-test="nouvelle">+ {{ t('demandes.mesDemandes.nouvelle') }}</RouterLink>
      </div>
      <JIllustration nom="question" class="page-tete__illustration" />
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="carte vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <div v-else-if="!demandes.length" class="carte vide" data-test="aucune-demande">
      <h2>{{ t('demandes.mesDemandes.vide') }}</h2>
      <p class="texte-discret">{{ t('demandes.mesDemandes.videAide') }}</p>
      <RouterLink to="/demandes/nouvelle" class="j-bouton j-bouton--principal">{{ t('demandes.mesDemandes.nouvelle') }}</RouterLink>
    </div>
    <template v-else>
      <div class="filtres" role="group" :aria-label="t('demandes.mesDemandes.filtrer')">
        <button type="button" class="filtre" :aria-pressed="filtre === 'toutes'" data-test="filtre-toutes" @click="filtre = 'toutes'">
          {{ t('demandes.mesDemandes.etats.toutes') }} <span>{{ demandes.length }}</span>
        </button>
        <button v-for="e in ETATS" :key="e" type="button" class="filtre" :aria-pressed="filtre === e" :disabled="!compte(e)" :data-test="`filtre-${e}`" @click="filtre = e">
          {{ t(`demandes.mesDemandes.etats.${e}`) }} <span>{{ compte(e) }}</span>
        </button>
      </div>
      <ul class="liste" data-test="liste-demandes">
        <li v-for="(d, i) in affichees" :key="d.id" :style="{ '--i': i }">
          <RouterLink :to="`/demandes/${d.id}`" class="carte demande" :class="`demande--${etatDe(d)}`" data-test="demande-resumee">
            <span class="demande__tete">
              <span class="etat" :class="`etat--${etatDe(d)}`">{{ t(`demandes.mesDemandes.etats.${etatDe(d)}`) }}</span>
              <span class="texte-discret">{{ dateCourte(d.creeLe) }}</span>
            </span>
            <strong class="demande__titre">{{ d.titre }}</strong>
            <!-- Texte brut, affiché échappé. -->
            <span v-if="d.resume" class="demande__resume">{{ d.resume }}</span>
            <span v-else class="demande__resume texte-discret">{{ t('demandes.mesDemandes.enCours') }}</span>
            <ol class="parcours" :aria-label="t('demandes.mesDemandes.parcours')">
              <li v-for="(e, j) in ETATS" :key="e" :class="{ fait: j <= ETATS.indexOf(etatDe(d)) }">{{ t(`demandes.mesDemandes.jalons.${e}`) }}</li>
            </ol>
            <span class="demande__etiquettes">
              <span v-if="d.publiee" class="etiquette etiquette--publiee">{{ t('demandes.mesDemandes.publiee') }}</span>
              <span v-if="d.precisions" class="etiquette">{{ t('demandes.mesDemandes.precisions', { n: d.precisions }, d.precisions) }}</span>
              <span class="demande__ouvrir">{{ t('demandes.mesDemandes.ouvrir') }} →</span>
            </span>
          </RouterLink>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.mes-demandes { display: grid; gap: var(--space-6); min-width: 0; }
.page-tete.motif { margin-inline: 0; }
.nouveau { justify-self: start; margin-top: var(--space-2); }
.vide { display: grid; gap: var(--space-3); justify-items: start; padding: var(--space-8); }
.vide h2, .vide p { margin: 0; }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
.demande {
  display: grid; gap: var(--space-3); height: 100%; padding: var(--space-6); color: var(--ink); text-decoration: none;
  border: 1px solid transparent; transition: border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.demande:hover { border-color: var(--action); transform: translateY(-1px); }
.demande:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.demande__tete { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
.liste > li { animation: arrive 360ms var(--ease-out) both; animation-delay: calc(var(--i) * 40ms); }
.demande { border-top: 4px solid var(--line); }
.demande--prete { border-top-color: var(--electric-blue); }
.demande--publiee { border-top-color: var(--cantaloupe); }
.etat { padding: 2px 10px; border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; background: var(--surface-raised); color: var(--ink-muted); }
.etat--prete { background: var(--action-soft); color: var(--action-ink); }
.etat--publiee { background: var(--proof-soft); color: var(--proof-ink); }
.parcours { list-style: none; margin: 0; padding: 0; display: flex; gap: var(--space-2); font-size: 0.75rem; color: var(--ink-muted); }
.parcours li { display: flex; align-items: center; gap: 6px; }
.parcours li::before { content: ''; width: 8px; height: 8px; border-radius: 50%; border: 1.5px solid var(--line-strong); }
.parcours li.fait { color: var(--ink); font-weight: 600; }
.parcours li.fait::before { background: var(--cantaloupe); border-color: var(--cantaloupe); }
.parcours li + li::after { content: none; }
.filtres { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.filtre {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 40px; padding: 0 var(--space-4); cursor: pointer;
  border: 1px solid var(--line-strong); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600;
}
.filtre span { padding: 0 8px; border-radius: var(--radius-pill); background: var(--surface-raised); font-variant-numeric: tabular-nums; }
.filtre[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
.filtre[aria-pressed='true'] span { background: color-mix(in srgb, var(--on-action) 20%, transparent); }
.filtre:disabled { opacity: 0.5; cursor: default; }
.filtre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes arrive { from { opacity: 0; transform: translateY(8px); } }
@media (prefers-reduced-motion: reduce) { .liste > li { animation: none; } }
.demande__titre { font-size: 1.0625rem; }
.demande__resume { display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.5; }
.demande__etiquettes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); align-self: end; }
.etiquette--publiee { background: var(--proof-soft); color: var(--proof-ink); }
.demande__ouvrir { margin-inline-start: auto; color: var(--action-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
</style>
