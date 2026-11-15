<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JChamp, JSquelette } from '../../design-system'
import {
  annulerHackathon,
  formaterMontant,
  LIMITES,
  lireHackathon,
  publierHackathon,
  type Hackathon,
  type Resultat,
} from './hackathons'
import ConfirmerResultats from './ConfirmerResultats.vue'
import EquipesHackathon from './EquipesHackathon.vue'
import InscriptionHackathon from './InscriptionHackathon.vue'
import JuryHackathon from './JuryHackathon.vue'
import NotationHackathon from './NotationHackathon.vue'
import RenduEquipe from './RenduEquipe.vue'
import ResultatsHackathon from './ResultatsHackathon.vue'
import DiscussionHackathon from './DiscussionHackathon.vue'
import TriCandidatures from './TriCandidatures.vue'
import PilotageHackathon from './PilotageHackathon.vue'
import { useSession } from '../../session'

/*
 * Un hackathon : son défi, son calendrier, ses critères et ses prix, et ce que la personne qui le lit peut en faire.
 * Les boutons viennent de `moi.actions`, calculé par l'API (qui revérifie chaque action). S'inscrire et former son
 * équipe : `InscriptionHackathon` et `EquipesHackathon` ; rendre son projet : `RenduEquipe` ;
 * le jury : `JuryHackathon` (organisateur) et `NotationHackathon` (juré) ; les gagnants : `ConfirmerResultats`
 * (organisateur) puis `ResultatsHackathon` (tout le monde) ; les candidatures : `TriCandidatures` (organisateur).
 * La mise en page : le défi à gauche, et à droite un résumé (prochaine étape, bouton pour participer, chiffres clés,
 * actions de l'organisateur) qui reste visible en défilant sur grand écran.
 */
const { t, locale } = useI18n()
const route = useRoute()
const session = useSession()
/** L'heure de la lecture de la page : elle situe le hackathon dans son calendrier. */
const maintenant = Date.now()
const id = computed(() => String(route.params.id))

const hackathon = ref<Hackathon | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const action = ref<'repos' | 'envoi'>('repos')
const codeErreur = ref<string | null>(null)
const annulation = ref(false)
const raison = ref('')

async function charger() {
  etat.value = 'chargement'
  try {
    hackathon.value = await lireHackathon(id.value)
    etat.value = hackathon.value ? 'pret' : 'introuvable'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(id, () => void charger(), { immediate: true })

/** Relire le hackathon sans repasser par l'écran de chargement (après une inscription). */
async function rafraichir() {
  try {
    hackathon.value = (await lireHackathon(id.value)) ?? hackathon.value
  } catch {
    // Le hackathon affiché reste juste ; la prochaine action le relira.
  }
}
/** Mon équipe rend son projet pendant le hackathon, et retrouve son rendu ensuite. */
const monEquipe = computed(() => {
  const h = hackathon.value
  return h?.moi.equipeId && ['en_cours', 'evaluation', 'resultats'].includes(h.statut) ? h.moi.equipeId : null
})
/** L'organisateur gère son jury dès la publication, jusqu'aux résultats. */
const voitJury = computed(() => hackathon.value?.moi.role === 'organisateur' && ['publie', 'en_cours', 'evaluation', 'resultats'].includes(hackathon.value.statut))
const voitEquipes = computed(() => !['brouillon', 'annule'].includes(hackathon.value?.statut ?? 'brouillon'))

const peut = (nom: string) => (hackathon.value?.moi.actions as string[] | undefined)?.includes(nom) ?? false
const dates = computed(() => {
  const c = hackathon.value?.calendrier
  if (!c) return []
  return [
    ['ouvertes', c.inscriptionsOuvertesLe],
    ['fermees', c.inscriptionsFermeesLe],
    ['debut', c.debutLe],
    ['fin', c.finRendusLe],
    ['resultats', c.resultatsLe],
  ] as const
})
/** Chaque date du calendrier : passée, actuelle (la dernière franchie) ou à venir. */
const etapes = computed(() => {
  const franchies = dates.value.filter(([, instant]) => Date.parse(instant) <= maintenant).length
  return dates.value.map(([cle, instant], i) => ({
    cle,
    jour: new Intl.DateTimeFormat(locale.value, { dateStyle: 'long' }).format(new Date(instant)),
    heure: new Intl.DateTimeFormat(locale.value, { timeStyle: 'short' }).format(new Date(instant)),
    etat: i < franchies - 1 ? 'passee' : i === franchies - 1 ? 'actuelle' : 'avenir',
  }))
})
/** La prochaine date à venir, mise en avant dans le résumé. */
const prochaine = computed(() => etapes.value.find((e) => e.etat === 'avenir') ?? null)
/** Le poids de chaque critère, en part du total, pour une barre lisible d'un coup d'œil. */
const criteres = computed(() => {
  const liste = hackathon.value?.criteres ?? []
  const total = liste.reduce((s, c) => s + c.poids, 0) || 1
  return liste.map((c) => ({ ...c, part: Math.round((c.poids / total) * 100) }))
})
const participe = computed(() => peut('sinscrire') || peut('creer_equipe'))
const invitationConnexion = computed(() => session.etat.value === 'non_connecte' && hackathon.value?.statut === 'publie')
const raisonValide = computed(() => raison.value.trim().length >= LIMITES.raisonMin)
const message = computed(() => {
  if (!codeErreur.value) return ''
  const connus = ['modification_concurrente', 'etat_invalide', 'limite_atteinte', 'validation']
  return t(`hackathons.detail.erreurs.${connus.includes(codeErreur.value) ? codeErreur.value : 'autre'}`)
})

async function agir(appel: () => Promise<Resultat<Hackathon>>) {
  if (!hackathon.value || action.value === 'envoi') return
  action.value = 'envoi'
  codeErreur.value = null
  try {
    const r = await appel()
    if (r.ok) {
      hackathon.value = r.valeur
      annulation.value = false
    } else codeErreur.value = r.code ?? 'autre'
  } catch {
    codeErreur.value = 'autre'
  } finally {
    action.value = 'repos'
  }
}
const publier = () => agir(() => publierHackathon(hackathon.value!.id, hackathon.value!.version))
const annuler = () => (raisonValide.value ? agir(() => annulerHackathon(hackathon.value!.id, hackathon.value!.version, raison.value.trim())) : undefined)
</script>


<template>
  <section class="hackathon">
    <p class="retour"><RouterLink to="/hackathons">{{ t('hackathons.detail.retour') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'introuvable'" class="carte message" data-test="introuvable">{{ t('hackathons.detail.introuvable') }}</p>
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message">{{ t('hackathons.indisponible') }}</p>

    <template v-else-if="hackathon">
      <header class="page-tete motif motif--reseau">
        <div class="page-tete__texte">
          <p class="etiquette" :data-statut="hackathon.statut" data-test="statut">{{ t(`hackathons.statuts.${hackathon.statut}`) }}</p>
          <h1 data-test="titre">{{ hackathon.titre }}</h1>
          <p class="theme">{{ hackathon.theme }}</p>
        </div>
      </header>

      <div class="disposition">
        <div class="principal">
          <!-- Une fois les résultats publiés, c'est ce qu'on vient voir : ils passent avant le défi. -->
          <ResultatsHackathon v-if="hackathon.statut === 'resultats'" :hackathon="hackathon" />
          <article class="carte contenu">
            <p v-if="hackathon.statut === 'brouillon'" class="note" data-test="brouillon">{{ t('hackathons.detail.brouillon') }}</p>

            <section aria-labelledby="defi">
              <h2 id="defi">{{ t('hackathons.detail.defi') }}</h2>
              <!-- Texte brut de l'organisateur : affiché échappé, avec ses retours à la ligne. -->
              <p class="texte" data-test="description">{{ hackathon.description }}</p>
            </section>

            <p v-if="hackathon.deploiementObligatoire" class="note" data-test="deploiement">{{ t('hackathons.detail.deploiement') }}</p>

            <section aria-labelledby="calendrier">
              <h2 id="calendrier">{{ t('hackathons.detail.calendrier') }}</h2>
              <ol class="frise" data-test="calendrier">
                <li v-for="e in etapes" :key="e.cle" :data-etat="e.etat" :aria-current="e.etat === 'actuelle' ? 'step' : undefined">
                  <span class="frise__point" aria-hidden="true" />
                  <span class="frise__nom">{{ t(`hackathons.calendrier.${e.cle}`) }}</span>
                  <span class="frise__date">{{ e.jour }} <span class="texte-discret">· {{ e.heure }}</span></span>
                </li>
              </ol>
            </section>

            <section aria-labelledby="criteres">
              <h2 id="criteres">{{ t('hackathons.detail.criteres') }}</h2>
              <ul class="criteres" data-test="criteres">
                <li v-for="c in criteres" :key="c.id ?? c.nom">
                  <span class="criteres__nom">{{ c.nom }}</span>
                  <span class="criteres__part">{{ t('hackathons.detail.part', { n: c.part }) }}</span>
                  <span class="criteres__barre" aria-hidden="true"><span :style="{ inlineSize: `${c.part}%` }" /></span>
                </li>
              </ul>
            </section>

            <section v-if="hackathon.prix.length" aria-labelledby="prix">
              <h2 id="prix">{{ t('hackathons.detail.prix') }}</h2>
              <ul class="prix" data-test="prix">
                <li v-for="p in hackathon.prix" :key="p.id ?? p.nom">
                  <strong>{{ p.nom }}</strong>
                  <span v-if="p.montant" class="prix__montant">{{ formaterMontant(p.montant, locale) }}</span>
                  <span v-if="p.sponsor" class="texte-discret">{{ t('hackathons.detail.offertPar', { sponsor: p.sponsor }) }}</span>
                </li>
              </ul>
            </section>

            <section v-if="hackathon.questions.length" aria-labelledby="questions">
              <h2 id="questions">{{ t('hackathons.detail.questions') }}</h2>
              <ul class="questions"><li v-for="q in hackathon.questions" :key="q">{{ q }}</li></ul>
            </section>

            <section v-if="hackathon.codeDeConduite" aria-labelledby="code">
              <h2 id="code">{{ t('hackathons.detail.code') }}</h2>
              <p class="texte">{{ hackathon.codeDeConduite }}</p>
            </section>
          </article>

          <ConfirmerResultats v-if="peut('confirmer_resultats')" :hackathon="hackathon" @publie="rafraichir" />
          <div v-if="peut('trier_inscriptions') && hackathon.modeInscription === 'candidature'" id="candidatures" class="ancre"><TriCandidatures :hackathon="hackathon" /></div>
          <div v-if="participe" id="participer"><InscriptionHackathon :hackathon="hackathon" @inscrit="rafraichir" /></div>
          <NotationHackathon v-if="peut('noter')" :hackathon="hackathon" />
          <div v-if="voitJury" id="jury" class="ancre"><JuryHackathon :hackathon="hackathon" /></div>
          <RenduEquipe v-if="monEquipe" :hackathon="hackathon" :equipe-id="monEquipe" />
          <div v-if="voitEquipes" id="equipes" class="ancre"><EquipesHackathon :hackathon="hackathon" /></div>
          <div v-if="hackathon.statut !== 'brouillon'" id="discussion" class="ancre"><DiscussionHackathon :hackathon="hackathon" /></div>
        </div>

        <aside class="carte resume" :aria-label="t('hackathons.detail.resume')" data-test="resume">
          <div v-if="prochaine" class="resume__prochaine" data-test="prochaine">
            <p class="texte-discret">{{ t('hackathons.detail.prochaine') }}</p>
            <p class="resume__etape">{{ t(`hackathons.calendrier.${prochaine.cle}`) }}</p>
            <p>{{ prochaine.jour }} <span class="texte-discret">· {{ prochaine.heure }}</span></p>
          </div>

          <a v-if="participe" href="#participer" class="j-bouton j-bouton--principal" data-test="aller-participer">{{ t('hackathons.detail.participer') }}</a>
          <RouterLink
            v-else-if="invitationConnexion"
            :to="{ name: 'connexion', query: { retour: route.fullPath } }"
            class="j-bouton j-bouton--principal"
            data-test="se-connecter"
          >{{ t('hackathons.detail.seConnecter') }}</RouterLink>
          <p v-else-if="hackathon.moi.role === 'participant'" class="resume__inscrit" data-test="inscrit">{{ t('hackathons.detail.inscrit') }}</p>
          <a v-if="hackathon.statut !== 'brouillon'" href="#discussion" class="j-bouton j-bouton--secondaire" data-test="aller-discussion">{{ t('hackathons.discussion.aller') }}</a>

          <dl class="faits">
            <div>
              <dt>{{ t('hackathons.nouveau.lieu') }}</dt>
              <dd>{{ [t(`hackathons.lieux.${hackathon.lieu}`), hackathon.ville].filter(Boolean).join(' · ') }}</dd>
            </div>
            <div>
              <dt>{{ t('hackathons.nouveau.equipes') }}</dt>
              <dd>{{ t('hackathons.detail.equipes', { min: hackathon.tailleEquipe.min, max: hackathon.tailleEquipe.max }) }}</dd>
            </div>
            <div>
              <dt>{{ t('hackathons.nouveau.mode') }}</dt>
              <dd>{{ t(`hackathons.detail.mode.${hackathon.modeInscription}`) }}</dd>
            </div>
            <div>
              <dt>{{ t('hackathons.detail.inscritsTitre') }}</dt>
              <dd data-test="nombre-inscrits">{{ hackathon.nombreInscrits }}</dd>
            </div>
          </dl>

          <p class="client"><JAvatar :nom="hackathon.organisateur.nom" :url="hackathon.organisateur.avatarUrl" :taille="32" /> {{ t('hackathons.par', { nom: hackathon.organisateur.nom }) }}</p>

          <PilotageHackathon v-if="hackathon.moi.role === 'organisateur' && !['brouillon', 'annule'].includes(hackathon.statut)" :hackathon="hackathon" />

          <div v-if="peut('publier') || peut('annuler')" class="actions">
            <div v-if="peut('publier')">
              <JBouton :occupe="action === 'envoi'" data-test="publier" @click="publier">{{ t('hackathons.detail.publier') }}</JBouton>
              <p class="texte-discret">{{ t('hackathons.detail.publierAide') }}</p>
            </div>
            <div v-if="peut('annuler')">
              <JBouton v-if="!annulation" variante="secondaire" data-test="annuler" @click="annulation = true">{{ t('hackathons.detail.annuler') }}</JBouton>
              <form v-else class="annulation" data-test="annulation" @submit.prevent="annuler">
                <JChamp v-model="raison" :libelle="t('hackathons.detail.raison')" multiligne :lignes="3" :maxlength="LIMITES.raisonMax" requis />
                <JBouton type="submit" :desactive="!raisonValide" :occupe="action === 'envoi'" data-test="confirmer-annulation">{{ t('hackathons.detail.confirmerAnnulation') }}</JBouton>
              </form>
              <p class="texte-discret">{{ t('hackathons.detail.annulerAide') }}</p>
            </div>
          </div>
          <p v-if="message" role="alert" class="erreur" data-test="erreur">{{ message }}</p>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.hackathon { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.message { margin: 0; padding: var(--space-6); }
.theme { margin: 0; font-size: 1.125rem; }

.disposition { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr); align-items: start; }
.principal { display: grid; gap: var(--space-6); min-width: 0; }
@media (min-width: 960px) {
  .disposition { grid-template-columns: minmax(0, 1fr) 20rem; }
  .resume { position: sticky; top: 6rem; }
}

.contenu { display: grid; gap: var(--space-8); padding: var(--space-8); }
@media (max-width: 600px) { .contenu { padding: var(--space-4); } }
.contenu h2 { margin: 0 0 var(--space-3); font-size: 1.0625rem; }
.texte { margin: 0; white-space: pre-line; line-height: 1.6; }

/* Le calendrier : une frise verticale, l'étape en cours mise en avant. */
.frise { list-style: none; margin: 0; padding: 0; display: grid; }
.frise li { position: relative; display: grid; grid-template-columns: 1.5rem 1fr; column-gap: var(--space-3); padding-block-end: var(--space-4); }
.frise li:last-child { padding-block-end: 0; }
.frise li::before { content: ''; position: absolute; inset-inline-start: calc(0.75rem - 1px); inset-block: 1rem 0; inline-size: 2px; background: var(--line-strong); }
.frise li:last-child::before { display: none; }
.frise li[data-etat='passee']::before { background: var(--action); }
.frise__point { grid-row: span 2; justify-self: center; margin-block-start: 0.25rem; inline-size: 0.75rem; block-size: 0.75rem; border-radius: 50%; border: 2px solid var(--line-strong); background: var(--surface); position: relative; z-index: 1; }
.frise li[data-etat='passee'] .frise__point { border-color: var(--action); background: var(--action); }
.frise li[data-etat='actuelle'] .frise__point { border-color: var(--action); background: var(--action); box-shadow: 0 0 0 4px color-mix(in srgb, var(--action) 30%, transparent); }
.frise__nom { font-weight: 700; }
.frise li[data-etat='avenir'] .frise__nom { font-weight: 600; }
.frise__date { font-size: var(--type-body-sm-size); }

/* Les critères : leur part de la note, en barre. */
.criteres { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.criteres li { display: grid; grid-template-columns: 1fr auto; gap: var(--space-2) var(--space-3); }
.criteres__nom { font-weight: 600; }
.criteres__part { font-variant-numeric: tabular-nums; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.criteres__barre { grid-column: 1 / -1; block-size: 0.5rem; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--line-strong) 60%, transparent); overflow: hidden; }
.criteres__barre span { display: block; block-size: 100%; border-radius: inherit; background: var(--action); }

.prix { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr)); }
.prix li { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); border: 1px solid var(--line-strong); border-inline-start: 3px solid var(--cantaloupe); }
.prix__montant { font-weight: 700; font-size: 1.125rem; }
.questions { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); }

/* Le résumé, à droite sur grand écran. */
.resume { display: grid; gap: var(--space-4); padding: var(--space-6); }
.resume p { margin: 0; }
.resume__prochaine { display: grid; gap: var(--space-2); padding-block-end: var(--space-4); border-block-end: 1px solid var(--line-strong); }
.resume__etape { font-weight: 700; font-size: 1.125rem; }
.resume__inscrit { font-weight: 700; }
.resume .j-bouton { justify-content: center; text-align: center; }
.faits { margin: 0; display: grid; gap: var(--space-3); }
.faits dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.faits dd { margin: 0; font-weight: 600; }
.client { display: flex; gap: var(--space-2); align-items: center; }
.etiquette { display: inline-block; margin: 0 0 var(--space-2); padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 700; }
.etiquette[data-statut='publie'], .etiquette[data-statut='en_cours'] { border-color: var(--action); }
.actions { display: grid; gap: var(--space-4); padding-block-start: var(--space-4); border-block-start: 1px solid var(--line-strong); }
.actions p { margin: var(--space-2) 0 0; }
.annulation { display: grid; gap: var(--space-3); }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
#participer, .ancre { scroll-margin-block-start: 6rem; }
</style>
