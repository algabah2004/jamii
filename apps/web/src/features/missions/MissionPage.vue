<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { fermerMission, formaterMontant, lireMission, publierMission, type Mission, type Resultat } from './missions'
import DevisPropose from './DevisPropose.vue'
import EnvoyerDevis from './EnvoyerDevis.vue'
import PropositionsMission from './PropositionsMission.vue'
import ProposerMission from './ProposerMission.vue'
import SuiviMission from './SuiviMission.vue'
import { useSession } from '../../session'

/*
 * Une mission : son annonce, et ce que la personne qui la lit peut en faire. Les boutons viennent de `moi.actions`,
 * calculé par l'API (qui revérifie chaque action). Les propositions sont dans `ProposerMission` et `PropositionsMission`,
 * le devis dans `EnvoyerDevis` et `DevisPropose`, le suivi du travail dans `SuiviMission`.
 * La mise en page : l'annonce à gauche, et à droite un résumé (budget, bouton pour proposer ses services, chiffres
 * clés, actions du client) qui reste visible en défilant sur grand écran.
 */
const { t, locale } = useI18n()
const route = useRoute()
const session = useSession()

/** Les étapes d'une mission qui avance normalement. Fermée, annulée ou en désaccord : pas de frise. */
const ETAPES = ['publiee', 'attribuee', 'en_cours', 'terminee'] as const
const id = computed(() => String(route.params.id))

const mission = ref<Mission | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const action = ref<'repos' | 'envoi'>('repos')
const codeErreur = ref<string | null>(null)

async function charger() {
  etat.value = 'chargement'
  try {
    mission.value = await lireMission(id.value)
    etat.value = mission.value ? 'pret' : 'introuvable'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(id, () => void charger(), { immediate: true })

/** Relire la mission sans repasser par l'écran de chargement (après une proposition envoyée ou retirée). */
async function rafraichir() {
  try {
    mission.value = (await lireMission(id.value)) ?? mission.value
  } catch {
    // La mission affichée reste juste ; la prochaine action la relira.
  }
}
const devisAccepte = computed(() => Boolean(mission.value?.travail?.devis.accepteLe))
/** Le client choisit parmi les propositions tant que la mission n'est pas attribuée ; un candidat voit toujours la sienne. */
const voitPropositions = computed(() => {
  const m = mission.value
  if (!m) return false
  return m.moi.role === 'candidat' || (m.moi.role === 'client' && ['publiee', 'fermee'].includes(m.statut))
})

const etapeActuelle = computed(() => ETAPES.indexOf(mission.value?.statut as (typeof ETAPES)[number]))
const invitationConnexion = computed(() => session.etat.value === 'non_connecte' && mission.value?.statut === 'publiee')
const peut = (nom: string) => (mission.value?.moi.actions as string[] | undefined)?.includes(nom) ?? false
const budget = computed(() => (mission.value?.budget ? formaterMontant(mission.value.budget, locale.value) : t('missions.budgetADiscuter')))
const dateSouhaitee = computed(() =>
  mission.value?.dateSouhaitee ? new Intl.DateTimeFormat(locale.value, { dateStyle: 'long' }).format(new Date(mission.value.dateSouhaitee)) : null,
)
const message = computed(() => {
  if (!codeErreur.value) return ''
  const connus = ['modification_concurrente', 'etat_invalide', 'limite_atteinte']
  return t(`missions.detail.erreurs.${connus.includes(codeErreur.value) ? codeErreur.value : 'autre'}`)
})

async function agir(appel: (id: string, version: number) => Promise<Resultat<Mission>>) {
  if (!mission.value || action.value === 'envoi') return
  action.value = 'envoi'
  codeErreur.value = null
  try {
    const r = await appel(mission.value.id, mission.value.version)
    if (r.ok) mission.value = r.valeur
    else codeErreur.value = r.code ?? 'autre'
  } catch {
    codeErreur.value = 'autre'
  } finally {
    action.value = 'repos'
  }
}
</script>

<template>
  <section class="mission">
    <p class="retour"><RouterLink to="/missions">{{ t('missions.detail.retour') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'introuvable'" class="carte message" data-test="introuvable">{{ t('missions.detail.introuvable') }}</p>
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message">{{ t('missions.indisponible') }}</p>

    <template v-else-if="mission">
      <header class="page-tete motif motif--reseau">
        <div class="page-tete__texte">
          <p class="etiquette" :data-statut="mission.statut" data-test="statut">{{ t(`missions.statuts.${mission.statut}`) }}</p>
          <h1 data-test="titre">{{ mission.titre }}</h1>
          <p v-if="mission.ville || mission.aDistance" class="lieu">{{ [mission.ville, mission.aDistance ? t('missions.aDistance') : null].filter(Boolean).join(' · ') }}</p>
        </div>
      </header>

      <div class="disposition">
        <div class="principal">
          <article class="carte contenu">
            <p v-if="mission.statut === 'brouillon'" class="note" data-test="brouillon">{{ t('missions.detail.brouillon') }}</p>
            <p v-if="mission.moi.role === 'invite'" class="note" data-test="invite">{{ t('missions.detail.invite') }}</p>

            <section v-if="etapeActuelle >= 0" aria-labelledby="etapes">
              <h2 id="etapes">{{ t('missions.detail.etapes') }}</h2>
              <ol class="etapes" data-test="etapes">
                <li
                  v-for="(e, i) in ETAPES"
                  :key="e"
                  :data-etat="i < etapeActuelle ? 'passee' : i === etapeActuelle ? 'actuelle' : 'avenir'"
                  :aria-current="i === etapeActuelle ? 'step' : undefined"
                >
                  <span class="etapes__point" aria-hidden="true">{{ i + 1 }}</span>
                  <span>{{ t(`missions.detail.etape.${e}`) }}</span>
                </li>
              </ol>
            </section>

            <section aria-labelledby="besoin">
              <h2 id="besoin">{{ t('missions.detail.besoin') }}</h2>
              <!-- Texte brut du client : affiché échappé, avec ses retours à la ligne. -->
              <p class="besoin" data-test="besoin">{{ mission.besoin }}</p>
            </section>

            <section aria-labelledby="competences">
              <h2 id="competences">{{ t('missions.detail.competences') }}</h2>
              <ul class="competences">
                <li v-for="c in mission.competences" :key="c">{{ c }}</li>
              </ul>
            </section>
          </article>

          <div v-if="peut('proposer')" id="proposer"><ProposerMission :mission="mission" @envoyee="rafraichir" /></div>
          <PropositionsMission v-if="voitPropositions" :mission="mission" @maj="mission = $event" @recharger="rafraichir" />
          <DevisPropose v-if="mission.travail && !devisAccepte" :mission="mission" :travail="mission.travail" @maj="mission = $event" />
          <EnvoyerDevis v-if="peut('envoyer_devis')" :mission="mission" :remplace="Boolean(mission.travail)" @envoye="rafraichir" />
          <SuiviMission v-if="mission.travail && devisAccepte" :mission="mission" :travail="mission.travail" @maj="mission = $event" />
        </div>

        <aside class="carte resume" :aria-label="t('missions.detail.resume')" data-test="resume">
          <div class="resume__budget">
            <p class="texte-discret">{{ t('missions.detail.budget') }}</p>
            <p class="resume__montant" data-test="budget">{{ budget }}</p>
          </div>

          <a v-if="peut('proposer')" href="#proposer" class="j-bouton j-bouton--principal" data-test="aller-proposer">{{ t('missions.detail.proposer') }}</a>
          <RouterLink
            v-else-if="invitationConnexion"
            :to="{ name: 'connexion', query: { retour: route.fullPath } }"
            class="j-bouton j-bouton--principal"
            data-test="se-connecter"
          >{{ t('missions.detail.seConnecter') }}</RouterLink>

          <dl class="faits">
            <div v-if="dateSouhaitee">
              <dt>{{ t('missions.detail.date') }}</dt>
              <dd>{{ dateSouhaitee }}</dd>
            </div>
            <div>
              <dt>{{ t('missions.detail.propositionsTitre') }}</dt>
              <dd data-test="nombre-propositions">{{ mission.nombrePropositions }}</dd>
            </div>
            <div>
              <dt>{{ t('missions.detail.client') }}</dt>
              <dd class="client"><JAvatar :nom="mission.client.nom" :url="mission.client.avatarUrl" :taille="32" /> {{ mission.client.nom }}</dd>
            </div>
          </dl>

          <div v-if="peut('publier') || peut('fermer')" class="actions">
            <div v-if="peut('publier')">
              <JBouton :occupe="action === 'envoi'" data-test="publier" @click="agir(publierMission)">{{ t('missions.detail.publier') }}</JBouton>
              <p class="texte-discret">{{ t('missions.detail.publierAide') }}</p>
            </div>
            <div v-if="peut('fermer')">
              <JBouton variante="secondaire" :occupe="action === 'envoi'" data-test="fermer" @click="agir(fermerMission)">{{ t('missions.detail.fermer') }}</JBouton>
              <p class="texte-discret">{{ t('missions.detail.fermerAide') }}</p>
            </div>
          </div>
          <p v-if="message" role="alert" class="erreur" data-test="erreur">{{ message }}</p>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.mission { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.message { margin: 0; padding: var(--space-6); }
.lieu { margin: 0; font-size: 1.125rem; }

.disposition { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr); align-items: start; }
.principal { display: grid; gap: var(--space-6); min-width: 0; }
@media (min-width: 960px) {
  .disposition { grid-template-columns: minmax(0, 1fr) 20rem; }
  .resume { position: sticky; top: 6rem; }
}

.contenu { display: grid; gap: var(--space-8); padding: var(--space-8); }
@media (max-width: 600px) { .contenu { padding: var(--space-4); } }
.contenu h2 { margin: 0 0 var(--space-3); font-size: 1.0625rem; }
.besoin { margin: 0; white-space: pre-line; line-height: 1.6; }

/* Où en est la mission : quatre étapes, l'actuelle mise en avant. */
.etapes { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-2); }
@media (max-width: 600px) { .etapes { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.etapes li { display: grid; gap: var(--space-2); justify-items: start; padding-block-start: var(--space-3); border-block-start: 3px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 600; }
.etapes li[data-etat='passee'], .etapes li[data-etat='actuelle'] { border-color: var(--action); }
.etapes li[data-etat='actuelle'] { font-weight: 800; }
.etapes__point { display: grid; place-items: center; inline-size: 1.5rem; block-size: 1.5rem; border-radius: 50%; border: 2px solid var(--line-strong); font-size: 0.75rem; font-weight: 700; }
.etapes li[data-etat='passee'] .etapes__point, .etapes li[data-etat='actuelle'] .etapes__point { border-color: var(--action); background: var(--action); color: var(--on-action, #fff); }

.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.competences li { padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 600; }

.resume { display: grid; gap: var(--space-4); padding: var(--space-6); }
.resume p { margin: 0; }
.resume__budget { display: grid; gap: var(--space-1); padding-block-end: var(--space-4); border-block-end: 1px solid var(--line-strong); }
.resume__montant { font-weight: 800; font-size: 1.5rem; font-variant-numeric: tabular-nums; }
.resume .j-bouton { justify-content: center; text-align: center; }
.faits { margin: 0; display: grid; gap: var(--space-3); }
.faits dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.faits dd { margin: 0; font-weight: 600; }
.client { display: flex; gap: var(--space-2); align-items: center; }
.etiquette {
  display: inline-block; margin: 0 0 var(--space-2); padding: 2px var(--space-3); border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 700;
}
.etiquette[data-statut='publiee'] { border-color: var(--action); }
.actions { display: grid; gap: var(--space-4); padding-block-start: var(--space-4); border-block-start: 1px solid var(--line-strong); }
.actions p { margin: var(--space-2) 0 0; }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
#proposer { scroll-margin-block-start: 6rem; }
</style>
