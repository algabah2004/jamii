<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { FILTRES, formaterDate, listerHackathons, type Filtre, type HackathonResume } from './hackathons'

/*
 * Les hackathons publiés : à venir, en cours, en évaluation ou terminés. Lisible sans connexion ; en créer un demande
 * de se connecter.
 * DESIGN PROVISOIRE : l'écran montre le parcours avec les composants actuels ; son design sera repris avec le design system.
 */
const { t, locale } = useI18n()
const { etat: session } = useSession()

const filtre = ref<Filtre>('tous')
const hackathons = ref<HackathonResume[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const plus = ref(false)
const connecte = computed(() => session.value === 'connecte')

async function charger(curseur?: string) {
  if (curseur) plus.value = true
  else etat.value = 'chargement'
  try {
    const page = await listerHackathons(filtre.value, curseur)
    hackathons.value = curseur ? [...hackathons.value, ...page.elements] : page.elements
    suivant.value = page.curseurSuivant
    etat.value = 'pret'
  } catch {
    if (!curseur) etat.value = 'indisponible'
  } finally {
    plus.value = false
  }
}
watch(filtre, () => void charger(), { immediate: true })
</script>

<template>
  <section class="hackathons">
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('hackathons.titre') }}</h1>
        <p>{{ t('hackathons.intro') }}</p>
      </div>
      <RouterLink v-if="connecte" to="/hackathons/nouveau" class="creer" data-test="creer-un-hackathon">{{ t('hackathons.creer') }}</RouterLink>
    </header>

    <div class="filtres" role="group" :aria-label="t('hackathons.titre')">
      <button
        v-for="f in FILTRES"
        :key="f"
        type="button"
        class="filtre"
        :aria-pressed="filtre === f"
        :data-test="`filtre-${f}`"
        @click="filtre = f"
      >{{ t(`hackathons.filtres.${f}`) }}</button>
    </div>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message" data-test="indisponible">{{ t('hackathons.indisponible') }}</p>
    <template v-else>
      <p v-if="!hackathons.length" class="carte message" data-test="aucun">{{ t('hackathons.aucun') }}</p>
      <ul v-else class="liste" data-test="liste">
        <li v-for="h in hackathons" :key="h.id" class="carte hackathon" data-test="hackathon">
          <div class="hackathon__haut">
            <span class="etiquette" :data-statut="h.statut">{{ t(`hackathons.statuts.${h.statut}`) }}</span>
            <span class="texte-discret">{{ t(`hackathons.lieux.${h.lieu}`) }}</span>
            <span v-if="h.ville" class="texte-discret">{{ h.ville }}</span>
          </div>
          <h2 class="hackathon__titre"><RouterLink :to="`/hackathons/${h.id}`">{{ h.titre }}</RouterLink></h2>
          <p class="hackathon__theme">{{ h.theme }}</p>
          <p class="hackathon__bas">
            <strong>{{ formaterDate(h.debutLe, locale) }}</strong>
            <span class="texte-discret">{{ t('hackathons.par', { nom: h.organisateur.nom }) }}</span>
            <span class="texte-discret">{{ t('hackathons.inscrits', { n: h.nombreInscrits }, h.nombreInscrits) }}</span>
          </p>
        </li>
      </ul>
      <div v-if="suivant" class="suite">
        <JBouton :occupe="plus" data-test="plus" @click="charger(suivant ?? undefined)">{{ t('hackathons.plus') }}</JBouton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.hackathons { display: grid; gap: var(--space-6); }
.page-tete a.creer {
  align-self: center; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-6);
  border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-weight: 700; text-decoration: none;
}
.page-tete a.creer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.filtres { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.filtre {
  min-height: 40px; padding: 0 var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-pill);
  background: var(--surface); font-weight: 600; cursor: pointer;
}
.filtre[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
.filtre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.message { margin: 0; padding: var(--space-6); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.hackathon { display: grid; gap: var(--space-3); padding: var(--space-6); }
.hackathon__haut { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.hackathon__titre { margin: 0; font-size: 1.125rem; }
.hackathon__titre a { color: inherit; }
.hackathon__theme { margin: 0; }
.hackathon__bas { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: baseline; }
.etiquette { padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 700; }
.etiquette[data-statut='publie'], .etiquette[data-statut='en_cours'] { border-color: var(--action); }
.suite { display: flex; justify-content: center; }
</style>
