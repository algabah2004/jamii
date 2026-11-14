<script setup lang="ts">
import { ref } from 'vue'
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
    <ul v-else class="liste" data-test="liste-demandes">
      <li v-for="d in demandes" :key="d.id">
        <RouterLink :to="`/demandes/${d.id}`" class="carte demande" data-test="demande-resumee">
          <span class="demande__tete">
            <strong class="demande__titre">{{ d.titre }}</strong>
            <span class="texte-discret">{{ dateCourte(d.creeLe) }}</span>
          </span>
          <!-- Texte brut, affiché échappé. -->
          <span v-if="d.resume" class="demande__resume">{{ d.resume }}</span>
          <span v-else class="demande__resume texte-discret">{{ t('demandes.mesDemandes.enCours') }}</span>
          <span class="demande__etiquettes">
            <span v-if="d.publiee" class="etiquette etiquette--publiee">{{ t('demandes.mesDemandes.publiee') }}</span>
            <span v-if="d.precisions" class="etiquette">{{ t('demandes.mesDemandes.precisions', { n: d.precisions }, d.precisions) }}</span>
            <span class="demande__ouvrir">{{ t('demandes.mesDemandes.ouvrir') }} →</span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.mes-demandes { display: grid; gap: var(--space-6); max-width: 64rem; }
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
.demande__tete { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-3); }
.demande__titre { font-size: 1.0625rem; }
.demande__resume { display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.5; }
.demande__etiquettes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); align-self: end; }
.etiquette--publiee { background: var(--proof-soft); color: var(--proof-ink); }
.demande__ouvrir { margin-inline-start: auto; color: var(--action-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
</style>
