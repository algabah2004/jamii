<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { formaterMontant, listerMissions, type MissionResume, type Vue } from './missions'

/*
 * Les missions : celles qui sont ouvertes aux propositions, et les miennes (client ou invité). Lisible sans connexion,
 * mais publier et « les miennes » demandent de se connecter.
 * Chaque mission est une carte : son statut (en couleur sur le bord du haut), ses compétences, son budget et son client.
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
        <li v-for="m in missions" :key="m.id" class="carte mission" :data-statut="m.statut" data-test="mission">
          <p class="mission__haut">
            <span class="etiquette" :data-statut="m.statut">{{ t(`missions.statuts.${m.statut}`) }}</span>
            <span v-if="m.aFaire" class="a-faire" data-test="a-faire">{{ t('missions.aFaire') }}</span>
            <span class="texte-discret">{{ [m.ville, m.aDistance ? t('missions.aDistance') : null].filter(Boolean).join(' · ') }}</span>
          </p>
          <h2 class="mission__titre">
            <RouterLink :to="`/missions/${m.id}`" class="lien-carte">{{ m.titre }}</RouterLink>
          </h2>
          <ul class="competences" :aria-label="t('missions.detail.competences')">
            <li v-for="c in m.competences.slice(0, 4)" :key="c">{{ c }}</li>
            <li v-if="m.competences.length > 4" class="competences__plus">+{{ m.competences.length - 4 }}</li>
          </ul>
          <div class="mission__bas">
            <p class="mission__budget">
              <span class="texte-discret">{{ t('missions.detail.budget') }}</span>
              <strong>{{ budget(m) }}</strong>
            </p>
            <p class="mission__meta">
              <span class="personne"><JAvatar :nom="m.client.nom" :url="m.client.avatarUrl" :taille="24" /> {{ t('missions.par', { nom: m.client.nom }) }}</span>
              <span class="compteur">{{ t('missions.propositions', { n: m.nombrePropositions }, m.nombrePropositions) }}</span>
            </p>
          </div>
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

/* Des cartes en grille ; toute la carte mène à la mission (le lien du titre s'étend sur elle). */
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
.mission {
  --accent: var(--line-strong);
  position: relative; display: grid; grid-template-rows: auto auto 1fr auto; gap: var(--space-4); padding: var(--space-6);
  border-block-start: 4px solid var(--accent);
  transition: transform 160ms ease, box-shadow 160ms ease;
}
.mission[data-statut='publiee'] { --accent: var(--action); }
.mission[data-statut='attribuee'], .mission[data-statut='en_cours'] { --accent: var(--cantaloupe); }
.mission[data-statut='annulee'], .mission[data-statut='fermee'] { opacity: 0.75; }
.mission:hover { transform: translateY(-3px); box-shadow: 0 12px 28px color-mix(in srgb, #000 28%, transparent); }
.mission:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .mission { transition: none; } .mission:hover { transform: none; } }
.mission p { margin: 0; }
.mission__haut { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.mission__titre { margin: 0; font-size: 1.25rem; line-height: 1.25; }
.lien-carte { color: inherit; text-decoration: none; }
.lien-carte::after { content: ''; position: absolute; inset: 0; z-index: 1; }
.lien-carte:focus-visible { outline: none; }
.mission:hover .lien-carte { color: var(--action-ink); }
.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); align-content: start; }
.competences li { padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 600; }
.competences__plus { color: var(--ink-muted); }
.mission__bas { display: grid; gap: var(--space-3); padding-block-start: var(--space-4); border-block-start: 1px solid var(--line-strong); }
.mission__budget { display: grid; gap: 2px; }
.mission__budget span { font-size: var(--type-body-sm-size); }
.mission__budget strong { font-size: 1.375rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.mission__meta { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; font-size: var(--type-body-sm-size); }
.personne { display: inline-flex; gap: var(--space-2); align-items: center; }
.compteur { font-weight: 700; }
.etiquette {
  padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong);
  font-size: var(--type-body-sm-size); font-weight: 700;
}
.etiquette[data-statut='publiee'] { border-color: var(--action); }
.a-faire {
  padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--cantaloupe); color: var(--midnight);
  font-size: var(--type-body-sm-size); font-weight: 800;
}
.suite { display: flex; justify-content: center; }
</style>
