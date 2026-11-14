<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { formaterMontant, listerMissions, type MissionResume, type Vue } from './missions'

/*
 * Les missions : celles qui sont ouvertes aux propositions, et les miennes (client ou invité). Lisible sans connexion,
 * mais publier et « les miennes » demandent de se connecter.
 * DESIGN PROVISOIRE : l'écran montre le parcours avec les composants actuels ; son design sera repris avec le design system.
 */
const { t, locale } = useI18n()
const { etat: session } = useSession()

const vue = ref<Vue>('publiques')
const missions = ref<MissionResume[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const plus = ref(false)
const connecte = computed(() => session.value === 'connecte')

async function charger(curseur?: string) {
  if (curseur) plus.value = true
  else etat.value = 'chargement'
  try {
    const page = await listerMissions(vue.value, curseur)
    missions.value = curseur ? [...missions.value, ...page.elements] : page.elements
    suivant.value = page.curseurSuivant
    etat.value = 'pret'
  } catch {
    if (!curseur) etat.value = 'indisponible'
  } finally {
    plus.value = false
  }
}
watch(vue, () => void charger(), { immediate: true })

const budget = (m: MissionResume) => (m.budget ? formaterMontant(m.budget, locale.value) : t('missions.budgetADiscuter'))
</script>

<template>
  <section class="missions">
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('missions.titre') }}</h1>
        <p>{{ t('missions.intro') }}</p>
      </div>
      <RouterLink v-if="connecte" to="/missions/nouvelle" class="publier" data-test="publier-une-mission">{{ t('missions.publier') }}</RouterLink>
    </header>

    <div v-if="connecte" class="onglets" role="group" :aria-label="t('missions.titre')">
      <button type="button" class="onglet" :aria-pressed="vue === 'publiques'" data-test="onglet-ouvertes" @click="vue = 'publiques'">{{ t('missions.onglets.ouvertes') }}</button>
      <button type="button" class="onglet" :aria-pressed="vue === 'miennes'" data-test="onglet-miennes" @click="vue = 'miennes'">{{ t('missions.onglets.miennes') }}</button>
    </div>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message" data-test="indisponible">{{ t('missions.indisponible') }}</p>
    <template v-else>
      <p v-if="!missions.length" class="carte message" data-test="aucune">{{ vue === 'miennes' ? t('missions.aucuneMiennes') : t('missions.aucune') }}</p>
      <ul v-else class="liste" data-test="liste">
        <li v-for="m in missions" :key="m.id" class="carte mission" data-test="mission">
          <div class="mission__haut">
            <span class="etiquette" :data-statut="m.statut">{{ t(`missions.statuts.${m.statut}`) }}</span>
            <span v-if="m.aDistance" class="texte-discret">{{ t('missions.aDistance') }}</span>
            <span v-if="m.ville" class="texte-discret">{{ m.ville }}</span>
          </div>
          <h2 class="mission__titre">
            <RouterLink :to="`/missions/${m.id}`">{{ m.titre }}</RouterLink>
          </h2>
          <ul class="competences" :aria-label="t('missions.detail.competences')">
            <li v-for="c in m.competences" :key="c">{{ c }}</li>
          </ul>
          <p class="mission__bas">
            <strong>{{ budget(m) }}</strong>
            <span class="texte-discret">{{ t('missions.par', { nom: m.client.nom }) }}</span>
            <span class="texte-discret">{{ t('missions.propositions', { n: m.nombrePropositions }, m.nombrePropositions) }}</span>
          </p>
        </li>
      </ul>
      <div v-if="suivant" class="suite">
        <JBouton :occupe="plus" data-test="plus" @click="charger(suivant ?? undefined)">{{ t('missions.plus') }}</JBouton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.missions { display: grid; gap: var(--space-6); }
.page-tete a.publier {
  align-self: center; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-6);
  border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-weight: 700; text-decoration: none;
}
.page-tete a.publier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.onglets { display: flex; gap: var(--space-2); }
.onglet {
  min-height: 40px; padding: 0 var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-pill);
  background: var(--surface); font-weight: 600; cursor: pointer;
}
.onglet[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
.onglet:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.message { margin: 0; padding: var(--space-6); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.mission { display: grid; gap: var(--space-3); padding: var(--space-6); }
.mission__haut { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.mission__titre { margin: 0; font-size: 1.125rem; }
.mission__titre a { color: inherit; }
.mission__bas { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: baseline; }
.etiquette {
  padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong);
  font-size: var(--type-body-sm-size); font-weight: 700;
}
.etiquette[data-statut='publiee'] { border-color: var(--action); }
.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.competences li { padding: 2px var(--space-3); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 60%, transparent); font-size: var(--type-body-sm-size); }
.suite { display: flex; justify-content: center; }
</style>
