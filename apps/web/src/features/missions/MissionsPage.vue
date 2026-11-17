<script setup lang="ts">
import MotifsCoins from '../../components/MotifsCoins.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { formaterMontant, listerMissions, type MissionResume, type Vue } from './missions'
import EnteteExploration from '../../components/exploration/EnteteExploration.vue'
import RechercheExploration from '../../components/exploration/RechercheExploration.vue'
import { correspond } from '../../components/exploration/recherche'
import { ilYa } from '../vitrine/vitrine'

/*
 * Les missions : celles qui sont ouvertes aux propositions, et les miennes (client ou invité). Lisible sans connexion,
 * mais publier et « les miennes » demandent de se connecter.
 * Chaque mission est une carte : son statut (en couleur sur le bord du haut), ses compétences, son budget et son client.
 */
const { t, locale } = useI18n()
const { etat: session, moi } = useSession()
const moiId = computed(() => moi.value?.id ?? null)

const vue = ref<Vue>('publiques')
const missions = ref<MissionResume[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const plus = ref(false)
const connecte = computed(() => session.value === 'connecte')
const recherche = ref('')
const aDistance = ref(false)
const vedette = computed(() => missions.value.find((m) => m.statut === 'publiee') ?? missions.value[0])
const visibles = computed(() => missions.value.filter((m) => (!aDistance.value || m.aDistance) && correspond(recherche.value, [m.titre, m.ville, m.client.nom, ...m.competences])))
const statistiques = computed(() => [
  { valeur: missions.value.length, libelle: t('exploration.missions.statMissions', missions.value.length) },
  { valeur: new Set(missions.value.flatMap((m) => m.competences)).size, libelle: t('exploration.missions.statCompetences', new Set(missions.value.flatMap((m) => m.competences)).size) },
  { valeur: missions.value.filter((m) => m.aDistance).length, libelle: t('missions.aDistance') },
])

let dernier = 0
async function charger(curseur?: string) {
  const numero = ++dernier
  if (curseur) plus.value = true
  else etat.value = 'chargement'
  try {
    const page = await listerMissions(vue.value, curseur)
    if (numero !== dernier) return
    missions.value = curseur ? [...missions.value, ...page.elements] : page.elements
    suivant.value = page.curseurSuivant
    etat.value = 'pret'
  } catch {
    if (!curseur && numero === dernier) etat.value = 'indisponible'
  } finally {
    if (numero === dernier) plus.value = false
  }
}
watch(vue, () => void charger(), { immediate: true })

const budget = (m: MissionResume) => (m.budget ? formaterMontant(m.budget, locale.value) : t('missions.budgetADiscuter'))
</script>

<template>
  <section class="avec-motifs missions exploration">
    <!-- Des taches de dessins sur les bords de la page, à son image. -->
    <MotifsCoins planche="missions" />
    <EnteteExploration :surtitre="t('exploration.missions.surtitre')" :titre="t('exploration.missions.titre')" :accent="t('exploration.missions.accent')" :intro="t('missions.intro')" :statistiques="statistiques">
      <template #actions>
        <RouterLink v-if="connecte" to="/missions/nouvelle" class="exploration__principal" data-test="publier-une-mission"><span aria-hidden="true">＋</span>{{ t('missions.publier') }}</RouterLink>
        <a href="#missions-liste" class="exploration__secondaire">{{ t('exploration.missions.explorer') }} <span aria-hidden="true">↓</span></a>
      </template>
      <p class="exploration__surtitre">{{ t('exploration.missions.vedette') }}</p>
      <template v-if="vedette && etat === 'pret'">
        <span class="exploration__icone" aria-hidden="true">↗</span>
        <h2><RouterLink :to="`/missions/${vedette.id}`">{{ vedette.titre }}</RouterLink></h2>
        <p>{{ [vedette.ville, vedette.aDistance ? t('missions.aDistance') : null].filter(Boolean).join(' · ') }}</p>
        <ul class="exploration__etiquettes"><li v-for="c in vedette.competences.slice(0, 3)" :key="c">{{ c }}</li></ul>
        <div class="exploration__pied-vedette"><div><small>{{ t('missions.detail.budget') }}</small><strong>{{ budget(vedette) }}</strong></div><RouterLink :to="`/missions/${vedette.id}`" class="exploration__fleche" :aria-label="t('exploration.ouvrir', { titre: vedette.titre })">↗</RouterLink></div>
      </template>
      <template v-else><span class="exploration__icone" aria-hidden="true">↗</span><h2>{{ t('exploration.missions.attenteTitre') }}</h2><p>{{ t('exploration.missions.attente') }}</p></template>
    </EnteteExploration>

    <div class="exploration__outils" id="missions-liste">
      <RechercheExploration v-model="recherche" :libelle="t('exploration.missions.recherche')" />
      <div class="exploration__filtres" role="group" :aria-label="t('missions.titre')">
        <template v-if="connecte"><button type="button" :aria-pressed="vue === 'publiques'" data-test="onglet-ouvertes" @click="vue = 'publiques'">{{ t('missions.onglets.ouvertes') }}</button><button type="button" :aria-pressed="vue === 'miennes'" data-test="onglet-miennes" @click="vue = 'miennes'">{{ t('missions.onglets.miennes') }}</button></template>
        <button type="button" :aria-pressed="aDistance" data-test="filtre-distance" @click="aDistance = !aDistance">{{ t('missions.aDistance') }}</button>
        <span v-if="suivant" class="missions__portee">{{ t('exploration.dansLaListe') }}</span>
      </div>
    </div>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message" data-test="indisponible">{{ t('missions.indisponible') }}</p>
    <template v-else>
      <div class="exploration__resultats"><h2>{{ t(vue === 'miennes' ? 'missions.onglets.miennes' : 'exploration.missions.resultats') }}</h2><p aria-live="polite">{{ t('exploration.resultats', { n: visibles.length }, visibles.length) }}</p></div>
      <p v-if="!missions.length" class="carte message" data-test="aucune">{{ vue === 'miennes' ? t('missions.aucuneMiennes') : t('missions.aucune') }}</p>
      <div v-else-if="!visibles.length" class="exploration__vide" data-test="sans-resultat"><h2>{{ t('exploration.aucunResultat') }}</h2><p>{{ t('exploration.essayerAutrement') }}</p><button type="button" class="exploration__effacer" @click="recherche = ''; aDistance = false">{{ t('exploration.reinitialiser') }}</button></div>
      <ul v-else class="liste" :class="{ 'liste--unique': visibles.length === 1 }" data-test="liste">
        <li v-for="m in visibles" :key="m.id" class="carte mission" :data-statut="m.statut" data-test="mission">
          <p class="mission__haut">
            <span class="etiquette" :data-statut="m.statut"><span class="etiquette__point" aria-hidden="true"></span>{{ t(`missions.statuts.${m.statut}`) }}</span>
            <span v-if="m.aFaire" class="a-faire" data-test="a-faire">{{ t('missions.aFaire') }}</span>
            <span v-if="m.publieeLe" class="mission__date" data-test="publiee">{{ t('missions.publiee', { quand: ilYa(m.publieeLe, locale) }) }}</span>
          </p>
          <h2 class="mission__titre">
            <RouterLink :to="`/missions/${m.id}`" class="lien-carte">{{ m.titre }}</RouterLink>
          </h2>
          <p v-if="m.ville || m.aDistance" class="mission__lieu">
            <span v-if="m.ville"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21Z" /><circle cx="12" cy="9.5" r="2.5" /></svg>{{ m.ville }}</span>
            <span v-if="m.aDistance"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3Z" /></svg>{{ t('missions.aDistance') }}</span>
          </p>
          <ul class="competences" :aria-label="t('missions.detail.competences')">
            <li v-for="c in m.competences.slice(0, 4)" :key="c">{{ c }}</li>
            <li v-if="m.competences.length > 4" class="competences__plus">+{{ m.competences.length - 4 }}</li>
          </ul>
          <div class="mission__bas">
            <div class="mission__chiffres">
              <p class="mission__budget">
                <span class="texte-discret">{{ t('missions.detail.budget') }}</span>
                <strong>{{ budget(m) }}</strong>
              </p>
              <p class="compteur" :data-vide="m.nombrePropositions === 0">{{ t('missions.propositions', { n: m.nombrePropositions }, m.nombrePropositions) }}</p>
            </div>
            <div class="mission__client">
              <span class="personne"><JAvatar :nom="m.client.nom" :url="m.client.avatarUrl" :taille="32" /><span><small>{{ t('missions.detail.publieePar') }}</small>{{ m.client.nom }}</span></span>
              <RouterLink
                v-if="m.client.id !== moiId && m.statut === 'publiee'"
                :to="{ path: `/missions/${m.id}`, hash: '#echanges' }"
                class="contacter"
                :aria-label="t('missions.contacterNom', { nom: m.client.nom })"
                data-test="contacter"
              ><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16v11H8l-4 4V5Z" /></svg>{{ t('missions.contacter') }}</RouterLink>
            </div>
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
.missions { display: grid; gap: var(--space-6); min-width: 0; }
.missions__portee { margin-inline-start: auto; font-size: .8125rem; color: var(--ink-muted); }
.message { margin: 0; padding: var(--space-6); }

/* Des cartes en grille ; toute la carte mène à la mission (le lien du titre s'étend sur elle). */
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
.mission {
  --accent: var(--line-strong);
  position: relative; overflow: hidden; display: grid; grid-template-rows: auto auto auto 1fr auto; gap: var(--space-4); padding: var(--space-6);
  border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--line));
  border-block-start: 4px solid var(--accent);
  /* Une lueur de la couleur du statut dans le coin : la carte se lit d'un coup d'œil. */
  background:
    radial-gradient(120% 70% at 100% 0%, color-mix(in srgb, var(--accent) 14%, transparent), transparent 60%),
    var(--surface);
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}
.mission[data-statut='publiee'] { --accent: var(--action); }
.mission[data-statut='attribuee'], .mission[data-statut='en_cours'] { --accent: var(--cantaloupe); }
.mission[data-statut='annulee'], .mission[data-statut='fermee'] { opacity: 0.75; }
.mission:hover { transform: translateY(-3px); border-color: var(--accent); box-shadow: 0 16px 36px color-mix(in srgb, var(--accent) 22%, transparent); }
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
.competences li {
  padding: 3px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600;
  background: color-mix(in srgb, var(--action) 10%, var(--surface-raised)); border: 1px solid color-mix(in srgb, var(--action) 25%, transparent);
}
.competences__plus { color: var(--ink-muted); }
.mission__date { margin-inline-start: auto; font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.mission__lieu { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.mission__lieu span { display: inline-flex; gap: 6px; align-items: center; }
.mission svg { inline-size: 1rem; block-size: 1rem; flex: none; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.mission__bas { display: grid; gap: var(--space-4); padding-block-start: var(--space-4); border-block-start: 1px dashed var(--line-strong); }
.mission__chiffres { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: end; justify-content: space-between; }
.mission__budget { display: grid; gap: 2px; }
.mission__budget span { font-size: var(--type-body-sm-size); }
.mission__budget strong { font-size: 1.375rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.compteur {
  padding: 4px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 700;
  background: color-mix(in srgb, var(--cantaloupe) 16%, transparent); color: var(--ink);
}
.compteur[data-vide='true'] { background: var(--surface-raised); color: var(--ink-muted); }
.mission__client { display: flex; gap: var(--space-3); align-items: center; justify-content: space-between; }
.personne { display: inline-flex; gap: var(--space-3); align-items: center; min-width: 0; font-weight: 700; }
.personne > span { display: grid; line-height: 1.2; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.personne small { font-size: .75rem; font-weight: 500; color: var(--ink-muted); }
/* Au-dessus du lien qui couvre la carte : on peut écrire au client sans ouvrir la mission d'abord. */
.contacter {
  position: relative; z-index: 2; flex: none; display: inline-flex; gap: 6px; align-items: center;
  padding: 6px var(--space-4); border-radius: var(--radius-pill); border: 1px solid var(--action);
  color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 700; text-decoration: none;
  transition: background 160ms ease, color 160ms ease;
}
.contacter:hover, .contacter:focus-visible { background: var(--action); color: var(--on-action, #fff); }
.etiquette {
  display: inline-flex; gap: 6px; align-items: center;
  padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong);
  font-size: var(--type-body-sm-size); font-weight: 700;
}
.etiquette__point { inline-size: .5rem; block-size: .5rem; border-radius: 50%; background: var(--accent, var(--line-strong)); }
.etiquette[data-statut='publiee'] { border-color: var(--action); background: color-mix(in srgb, var(--action) 12%, transparent); }
.etiquette[data-statut='publiee'] .etiquette__point { animation: pouls 2s ease-in-out infinite; }
@keyframes pouls { 50% { box-shadow: 0 0 0 4px color-mix(in srgb, var(--action) 25%, transparent); } }
@media (prefers-reduced-motion: reduce) { .etiquette__point { animation: none !important; } }
.a-faire {
  padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--cantaloupe); color: var(--midnight);
  font-size: var(--type-body-sm-size); font-weight: 800;
}
.suite { display: flex; justify-content: center; }
.missions.exploration { gap: clamp(28px, 4vw, 48px); }
.mission { border-radius: 22px; padding: 28px; }
.mission__titre { font-size: 1.5rem; line-height: 1.3; letter-spacing: -.025em; }
.mission__budget strong { font-size: 1.75rem; }
.liste--unique { grid-template-columns: minmax(0, 1fr); }
@media (min-width: 850px) {
  .liste--unique .mission { grid-template-columns: minmax(0, 1fr) minmax(240px, .45fr); grid-template-rows: auto auto auto 1fr; column-gap: 40px; }
  .liste--unique .mission__haut, .liste--unique .mission__titre, .liste--unique .mission__lieu, .liste--unique .competences { grid-column: 1; }
  .liste--unique .mission__bas { grid-column: 2; grid-row: 1 / 5; border-block-start: 0; border-inline-start: 1px dashed var(--line-strong); padding: 0 0 0 32px; align-content: center; }
}
</style>
