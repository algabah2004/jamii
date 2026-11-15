<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
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
import GalerieHackathon from './GalerieHackathon.vue'
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
const routeur = useRouter()
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
/** L'onglet « Gestion » : ce que seul l'organisateur pilote (candidatures, jury, publication des résultats). */
const voitGestion = computed(() => voitJury.value || peut('confirmer_resultats') || (peut('trier_inscriptions') && hackathon.value?.modeInscription === 'candidature'))
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
    // Pour la barre mobile, où la place manque : « 16 nov. 2026 ».
    jourCourt: new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(new Date(instant)),
    etat: i < franchies - 1 ? 'passee' : i === franchies - 1 ? 'actuelle' : 'avenir',
  }))
})
/** La prochaine date à venir, mise en avant dans le résumé. */
const prochaine = computed(() => etapes.value.find((e) => e.etat === 'avenir') ?? null)
const periode = computed(() => hackathon.value ? new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short', year: 'numeric' }).formatRange(new Date(hackathon.value.debutLe), new Date(hackathon.value.finRendusLe)) : '')
const dotations = computed(() => {
  const montants = new Map<string, number>()
  for (const prix of hackathon.value?.prix ?? []) if (prix.montant) montants.set(prix.montant.devise, (montants.get(prix.montant.devise) ?? 0) + prix.montant.centimes)
  return [...montants].map(([devise, centimes]) => formaterMontant({ devise, centimes }, locale.value)).join(' · ')
})
/** Le poids de chaque critère, en part du total, pour une barre lisible d'un coup d'œil. */
const criteres = computed(() => {
  const liste = hackathon.value?.criteres ?? []
  const total = liste.reduce((s, c) => s + c.poids, 0) || 1
  return liste.map((c) => ({ ...c, part: Math.round((c.poids / total) * 100) }))
})
const participe = computed(() => peut('sinscrire') || peut('creer_equipe'))
/** Sa candidature attend l'organisateur (ou est en liste d'attente) : le résumé le dit au lieu de proposer « Participer ». */
const candidature = computed(() => {
  const s = hackathon.value?.moi.inscription
  return s === 'en_attente' || s === 'attente' ? s : null
})
/** Le juré, pendant l'évaluation : combien de projets il lui reste à noter (donné par `NotationHackathon`). */
const notation = ref<{ notes: number; total: number } | null>(null)
const texteNotation = computed(() => {
  const n = notation.value
  if (!n || !n.total) return ''
  const reste = n.total - n.notes
  return reste ? t('hackathons.detail.aNoter', { n: reste }, reste) : t('hackathons.detail.toutNote')
})

/** La barre du bas (mobile) : participer, se connecter, ou rejoindre les annonces et échanges. */
const barreMobile = computed(() => Boolean(hackathon.value && hackathon.value.statut !== 'brouillon' && hackathon.value.moi.role !== 'organisateur'))

// ---------------------------------------------------------------- les onglets

type Onglet = 'presentation' | 'projets' | 'equipes' | 'calendrier' | 'reglement' | 'discussion' | 'resultats' | 'gestion'
const nouvellesAnnonces = ref(0)
const onglets = computed<Onglet[]>(() => {
  const h = hackathon.value
  if (!h) return ['presentation']
  if (h.statut === 'brouillon') return ['presentation', 'calendrier', 'reglement']
  const liste: Onglet[] = ['presentation', 'projets']
  if (voitEquipes.value) liste.push('equipes')
  liste.push('calendrier', 'reglement', 'discussion')
  if (h.statut === 'resultats') liste.push('resultats')
  if (voitGestion.value) liste.push('gestion')
  return liste
})
const onglet = computed<Onglet>(() => onglets.value.find((o) => o === route.query.onglet) ?? (hackathon.value?.statut === 'resultats' ? 'resultats' : 'presentation'))
/** Les liens internes (#jury, #equipes…) ouvrent l'onglet qui contient leur section. */
const ONGLET_DE: Record<string, Onglet> = {
  participer: 'presentation', noter: 'presentation', candidatures: 'gestion', jury: 'gestion', equipes: 'equipes', discussion: 'discussion', calendrier: 'calendrier', criteres: 'reglement', prix: 'reglement', code: 'reglement', questions: 'reglement', projets: 'projets',
}
async function choisirOnglet(o: Onglet, ancre?: string) {
  if (!onglets.value.includes(o)) return
  await routeur.replace({ query: { ...route.query, onglet: o === 'presentation' && hackathon.value?.statut !== 'resultats' ? undefined : o }, hash: ancre ? `#${ancre}` : '' })
  // Le panneau n'est visible qu'une fois la route changée : on défile après, pas avant.
  await nextTick()
  document.getElementById(ancre ?? `panneau-${o}`)?.scrollIntoView?.({ block: 'start' })
}
function suivreAncre() {
  const ancre = window.location.hash.slice(1)
  const cible = ONGLET_DE[ancre]
  if (cible && onglets.value.includes(cible)) void choisirOnglet(cible, ancre)
}
onMounted(() => window.addEventListener('hashchange', suivreAncre))
onBeforeUnmount(() => window.removeEventListener('hashchange', suivreAncre))
watch(() => hackathon.value?.id, (id) => {
  if (id) void nextTick(suivreAncre)
})

/** Flèches gauche et droite, Début et Fin : le motif « onglets » de l'ARIA. */
function clavierOnglets(e: KeyboardEvent) {
  const liste = onglets.value
  const i = liste.indexOf(onglet.value)
  const cibles: Record<string, Onglet | undefined> = {
    ArrowRight: liste[(i + 1) % liste.length],
    ArrowLeft: liste[(i - 1 + liste.length) % liste.length],
    Home: liste[0],
    End: liste[liste.length - 1],
  }
  const cible = cibles[e.key]
  if (!cible) return
  e.preventDefault()
  void choisirOnglet(cible).then(() => document.getElementById(`onglet-${cible}`)?.focus())
}
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
  <section class="hackathon" :class="{ 'avec-barre': barreMobile }">
    <p class="retour"><RouterLink to="/hackathons">{{ t('hackathons.detail.retour') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'introuvable'" class="carte message" data-test="introuvable">{{ t('hackathons.detail.introuvable') }}</p>
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message">{{ t('hackathons.indisponible') }}</p>

    <template v-else-if="hackathon">
      <header class="evenement__banniere">
        <div class="evenement__introduction">
          <p class="evenement__surtitre">{{ t('hackathons.evenement.surtitre') }}</p>
          <p class="etiquette" :data-statut="hackathon.statut" data-test="statut">{{ t(`hackathons.statuts.${hackathon.statut}`) }}</p>
          <h1 id="titre-hackathon" data-test="titre">{{ hackathon.titre }}</h1>
          <p class="theme">{{ hackathon.theme }}</p>
          <div class="evenement__meta"><span>{{ [t(`hackathons.lieux.${hackathon.lieu}`), hackathon.ville].filter(Boolean).join(' · ') }}</span><span>{{ periode }}</span><span>{{ t('hackathons.inscrits', { n: hackathon.nombreInscrits }, hackathon.nombreInscrits) }}</span></div>
          <p class="evenement__organisateur"><JAvatar :nom="hackathon.organisateur.nom" :url="hackathon.organisateur.avatarUrl" :taille="28" />{{ t('hackathons.par', { nom: hackathon.organisateur.nom }) }}</p>
        </div>
        <div class="evenement__affiche" aria-hidden="true"><span>Jamii<span>{{ t('hackathons.titre') }}</span></span><div class="evenement__symbole">&lt;/&gt;</div><i /><b>{{ t('hackathons.evenement.affiche') }}</b></div>
      </header>

    <!-- Des onglets plutôt qu'une page de 5 000 px : chacun a son adresse (?onglet=…) pour être partagé. -->
    <div v-if="onglets.length > 1" class="onglets" role="tablist" :aria-label="t('hackathons.onglets.titre')" @keydown="clavierOnglets">
      <button
        v-for="o in onglets"
        :id="`onglet-${o}`"
        :key="o"
        type="button"
        role="tab"
        class="onglet"
        :aria-selected="onglet === o"
        :aria-controls="`panneau-${o}`"
        :tabindex="onglet === o ? 0 : -1"
        :data-test="`onglet-${o}`"
        @click="void choisirOnglet(o)"
      >
        {{ t(`hackathons.onglets.${o}`) }}
        <span v-if="o === 'discussion' && nouvellesAnnonces" class="onglet__pastille" data-test="nouvelles-annonces">
          {{ nouvellesAnnonces }}<span class="visuellement-cache"> {{ t('hackathons.onglets.nouvelles', nouvellesAnnonces) }}</span>
        </span>
      </button>
    </div>

      <div class="disposition">
        <div class="principal">


          <div v-show="onglet === 'presentation'" id="panneau-presentation" class="panneau" role="tabpanel" aria-labelledby="onglet-presentation">
            <article class="carte contenu">
              <p v-if="hackathon.statut === 'brouillon'" class="note" data-test="brouillon">{{ t('hackathons.detail.brouillon') }}</p>

              <section aria-labelledby="defi">
                <h2 id="defi">{{ t('hackathons.detail.defi') }}</h2>
                <!-- Texte brut de l'organisateur : affiché échappé, avec ses retours à la ligne. -->
                <p class="texte" data-test="description">{{ hackathon.description }}</p>
              </section>

              <div v-if="!['brouillon', 'annule'].includes(hackathon.statut)" class="evenement__parcours">
                <button type="button" @click="choisirOnglet('calendrier')"><span>01</span><strong>{{ t('hackathons.onglets.calendrier') }}</strong><small>{{ t('hackathons.evenement.preparer') }}</small></button>
                <button type="button" @click="choisirOnglet('equipes')"><span>02</span><strong>{{ t('hackathons.onglets.equipes') }}</strong><small>{{ t('hackathons.evenement.construire') }}</small></button>
                <button type="button" @click="choisirOnglet('projets')"><span>03</span><strong>{{ t('hackathons.onglets.projets') }}</strong><small>{{ t('hackathons.evenement.partager') }}</small></button>
              </div>
              <p v-if="hackathon.deploiementObligatoire" class="note" data-test="deploiement">{{ t('hackathons.detail.deploiement') }}</p>





            </article>
            <div v-if="participe" id="participer"><InscriptionHackathon :hackathon="hackathon" @inscrit="rafraichir" /></div>
            <div v-if="peut('noter')" id="noter" class="ancre"><NotationHackathon :hackathon="hackathon" @progression="notation = $event" /></div>
          </div>

          <div v-show="onglet === 'calendrier'" id="panneau-calendrier" class="panneau" role="tabpanel" aria-labelledby="onglet-calendrier"><article class="carte contenu">
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
          </article></div>

          <div v-show="onglet === 'reglement'" id="panneau-reglement" class="panneau" role="tabpanel" aria-labelledby="onglet-reglement"><article class="carte contenu">
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
          </article></div>

          <div v-if="hackathon.statut !== 'brouillon'" v-show="onglet === 'projets'" id="panneau-projets" class="panneau" role="tabpanel" aria-labelledby="onglet-projets">
            <RenduEquipe v-if="monEquipe" :hackathon="hackathon" :equipe-id="monEquipe" />
            <GalerieHackathon v-if="onglet === 'projets'" :hackathon="hackathon" />
          </div>

          <div v-if="hackathon.statut === 'resultats'" v-show="onglet === 'resultats'" id="panneau-resultats" class="panneau" role="tabpanel" aria-labelledby="onglet-resultats"><ResultatsHackathon :hackathon="hackathon" /></div>

          <div v-if="voitGestion" v-show="onglet === 'gestion'" id="panneau-gestion" class="panneau" role="tabpanel" aria-labelledby="onglet-gestion">
            <ConfirmerResultats v-if="peut('confirmer_resultats')" :hackathon="hackathon" @publie="rafraichir" />
            <div v-if="peut('trier_inscriptions') && hackathon.modeInscription === 'candidature'" id="candidatures" class="ancre"><TriCandidatures :hackathon="hackathon" /></div>
            <div v-if="voitJury" id="jury" class="ancre"><JuryHackathon :hackathon="hackathon" /></div>
          </div>

          <div v-if="voitEquipes" v-show="onglet === 'equipes'" id="panneau-equipes" class="panneau" role="tabpanel" aria-labelledby="onglet-equipes">
            <div id="equipes" class="ancre"><EquipesHackathon :hackathon="hackathon" /></div>
          </div>

          <div v-if="hackathon.statut !== 'brouillon'" v-show="onglet === 'discussion'" id="panneau-discussion" class="panneau" role="tabpanel" aria-labelledby="onglet-discussion">
            <div id="discussion" class="ancre"><DiscussionHackathon :hackathon="hackathon" @nouvelles="nouvellesAnnonces = $event" /></div>
          </div>
        </div>

        <aside class="carte resume" :aria-label="t('hackathons.detail.resume')" data-test="resume">
          <div v-if="prochaine" class="resume__prochaine" data-test="prochaine">
            <p class="texte-discret">{{ t('hackathons.detail.prochaine') }}</p>
            <p class="resume__etape">{{ t(`hackathons.calendrier.${prochaine.cle}`) }}</p>
            <p>{{ prochaine.jour }} <span class="texte-discret">· {{ prochaine.heure }}</span></p>
          </div>

          <div v-if="peut('noter')" class="resume__noter" data-test="a-noter">
            <p v-if="texteNotation" class="resume__etape">{{ texteNotation }}</p>
            <a href="#noter" class="j-bouton j-bouton--principal" data-test="aller-noter">{{ t('hackathons.detail.noter') }}</a>
          </div>
          <p v-if="candidature" class="resume__inscrit" :data-statut="candidature" data-test="candidature">
            {{ t(`hackathons.detail.candidature.${candidature}`) }} <a href="#participer">{{ t('hackathons.detail.voirCandidature') }}</a>
          </p>
          <a v-else-if="participe" href="#participer" class="j-bouton j-bouton--principal" data-test="aller-participer">{{ t('hackathons.detail.participer') }}</a>
          <RouterLink
            v-else-if="invitationConnexion"
            :to="{ name: 'connexion', query: { retour: route.fullPath } }"
            class="j-bouton j-bouton--principal"
            data-test="se-connecter"
          >{{ t('hackathons.detail.seConnecter') }}</RouterLink>
          <p v-else-if="hackathon.moi.role === 'participant'" class="resume__inscrit" data-test="inscrit">{{ t('hackathons.detail.inscrit') }}</p>
          <a v-if="hackathon.statut !== 'brouillon'" href="#discussion" class="j-bouton j-bouton--secondaire" data-test="aller-discussion">{{ t('hackathons.discussion.aller') }}</a>

          <div v-if="hackathon.prix.length" class="resume__dotations"><span>{{ t('hackathons.detail.prix') }}</span><strong>{{ dotations || t('hackathons.evenement.nombrePrix', { n: hackathon.prix.length }, hackathon.prix.length) }}</strong><button type="button" @click="choisirOnglet('reglement', 'prix')">{{ t('hackathons.evenement.voirPrix') }} ↗</button></div>

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

      <div v-if="barreMobile" class="barre-mobile" data-test="barre-mobile">
        <p v-if="peut('noter') && texteNotation"><span class="texte-discret">{{ t('hackathons.detail.prochaine') }}</span><strong>{{ texteNotation }}</strong></p>
        <p v-else-if="prochaine"><span class="texte-discret">{{ t(`hackathons.calendrier.${prochaine.cle}`) }}</span><strong>{{ prochaine.jourCourt }}</strong></p>
        <p v-else><strong>{{ hackathon.titre }}</strong></p>
        <a v-if="peut('noter')" href="#noter" class="j-bouton j-bouton--principal">{{ t('hackathons.detail.noterCourt') }}</a>
        <a v-else-if="participe && !candidature" href="#participer" class="j-bouton j-bouton--principal">{{ t('hackathons.detail.participer') }}</a>
        <RouterLink v-else-if="invitationConnexion" :to="{ name: 'connexion', query: { retour: route.fullPath } }" class="j-bouton j-bouton--principal">
          {{ t('hackathons.detail.seConnecter') }}
        </RouterLink>
        <a v-else href="#discussion" class="j-bouton j-bouton--principal">{{ t('hackathons.discussion.aller') }}</a>
      </div>
    </template>
  </section>
</template>

<style scoped>
.hackathon { display: grid; gap: var(--space-6); }
/* Sur mobile, le résumé passe sous tout le reste : l'action principale reste sous le pouce, dans une barre en bas. */
.barre-mobile {
  position: fixed; inset-inline: 0; inset-block-end: 0; z-index: 20;
  display: flex; gap: var(--space-3); align-items: center; justify-content: space-between;
  padding: var(--space-3) var(--space-4) calc(var(--space-3) + env(safe-area-inset-bottom));
  background: var(--surface); border-block-start: 1px solid var(--line-strong);
  box-shadow: 0 -8px 24px color-mix(in srgb, #000 18%, transparent);
}
.barre-mobile p { margin: 0; display: grid; line-height: 1.2; min-width: 0; }
.barre-mobile strong { font-size: 1.0625rem; font-variant-numeric: tabular-nums; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.barre-mobile .j-bouton { flex: 0 0 auto; }
@media (max-width: 959px) { .avec-barre { padding-block-end: 5.5rem; } }
@media (min-width: 960px) { .barre-mobile { display: none; } }

.retour { margin: 0; font-size: var(--type-body-sm-size); }
.message { margin: 0; padding: var(--space-6); }
.theme { margin: 0; font-size: 1.125rem; }

.disposition { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr); align-items: start; }
.principal { display: grid; gap: var(--space-6); min-width: 0; }
.panneau { display: grid; gap: var(--space-6); min-width: 0; scroll-margin-top: 160px; }
.onglets {
  display: flex; gap: var(--space-1); overflow-x: auto; scrollbar-width: none; padding: var(--space-1);
  border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line-strong);
}
.onglet {
  display: inline-flex; flex: 0 0 auto; gap: var(--space-2); align-items: center; min-height: 40px; padding: 0 var(--space-4);
  border: 0; border-radius: var(--radius-pill); background: transparent; color: var(--ink); font: inherit; font-weight: 600; cursor: pointer;
}
.onglet:hover { background: color-mix(in srgb, var(--action) 10%, transparent); }
.onglet[aria-selected='true'] { background: var(--action); color: var(--on-action); }
.onglet:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.onglet__pastille {
  min-inline-size: 1.25rem; padding: 0 6px; border-radius: var(--radius-pill); background: var(--cantaloupe); color: var(--midnight);
  font-size: 0.75rem; font-weight: 800; text-align: center; font-variant-numeric: tabular-nums;
}
@media (min-width: 960px) {
  .disposition { grid-template-columns: minmax(0, 1fr) 20rem; }
  /* Collant, mais jamais plus haut que l'écran : sinon le bas (tableau de bord, actions) resterait hors de portée. */
  .resume { position: sticky; top: 6rem; max-block-size: calc(100vh - 7rem); overflow-y: auto; overscroll-behavior: contain; }
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
.resume__noter { display: grid; gap: var(--space-3); }
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
.hackathon { padding-inline: clamp(0px, 1.5vw, 24px); min-width: 0; }
.evenement__banniere { display: grid; grid-template-columns: minmax(0, 1fr) 270px; gap: 48px; align-items: center; padding: clamp(28px, 4vw, 56px); border-radius: 28px; color: #fff; background: radial-gradient(ellipse at 95% 0, #23546c 0, transparent 60%), var(--midnight); overflow: hidden; }
.evenement__surtitre { font-size: .8125rem; letter-spacing: .14em; text-transform: uppercase; font-weight: 700; color: #d2e0e7; margin: 0 0 18px; }
.evenement__banniere h1 { color: #fff; font-size: clamp(2.5rem, 4.5vw, 4.75rem); line-height: 1.05; letter-spacing: -.045em; margin: 18px 0; max-width: 22ch; overflow-wrap: anywhere; }
.evenement__banniere .theme { color: #d2e0e7; font-size: 1.125rem; max-width: 60ch; line-height: 1.7; }
.evenement__banniere .etiquette { color: #fff; background: #ffffff14; border-color: #ffffff50; }
.evenement__meta { display: flex; flex-wrap: wrap; gap: 10px 24px; margin-top: 24px; font-size: .875rem; color: #e0e9ed; }
.evenement__meta > span { display: flex; align-items: center; gap: 8px; }
.evenement__meta > span::before { content: ''; width: 5px; height: 5px; background: var(--cantaloupe); border-radius: 50%; }
.evenement__organisateur { display: flex; align-items: center; gap: 10px; margin: 24px 0 0; color: #e0e9ed; font-size: .875rem; }
.evenement__affiche { position: relative; display: flex; flex-direction: column; justify-content: space-between; min-height: 285px; padding: 25px; color: var(--midnight); border-radius: 22px; background: var(--cantaloupe); transform: rotate(5deg); box-shadow: 14px 14px 0 #ffffff0e; }
.evenement__affiche > span { display: flex; justify-content: space-between; gap: 10px; font-weight: 700; font-size: 1.25rem; font-family: var(--font-display); }
.evenement__affiche > span > span { font-size: .8125rem; padding-top: 5px; }
.evenement__symbole { text-align: center; font: 700 4.5rem var(--font-display); letter-spacing: -.08em; padding-block: 20px; }
.evenement__affiche > b { font: 700 1.5rem/1.05 var(--font-display); letter-spacing: -.04em; white-space: pre-line; }
.evenement__affiche > i { position: absolute; width: 80px; height: 80px; border: 1px solid #051a2540; border-radius: 50%; right: -15px; bottom: -20px; }
.onglets { position: sticky; top: 80px; z-index: 5; padding: 8px; border-radius: 18px; gap: 6px; box-shadow: var(--ombre-carte); }
.onglet { min-height: 46px; border-radius: 12px; font-size: .9375rem; }
.onglet[aria-selected='true'] { background: var(--proof); color: var(--on-proof); }
.contenu { border-radius: 24px; }
.contenu h2 { font-size: 1.5rem; letter-spacing: -.025em; }
.contenu section { scroll-margin-top: 160px; }
.texte { line-height: 1.9; font-size: 1rem; overflow-wrap: anywhere; }
.evenement__parcours { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.evenement__parcours button { display: grid; gap: 10px; text-align: start; padding: 20px; border-radius: 16px; border: 1px solid var(--line-strong); background: var(--surface); cursor: pointer; }
.evenement__parcours button:hover { background: var(--action-soft); }
.evenement__parcours span { font-size: .875rem; font-weight: 700; color: var(--proof-ink); }
.evenement__parcours strong { font-size: 1rem; }
.evenement__parcours small { font-size: .875rem; color: var(--ink-muted); line-height: 1.6; }
.resume { padding: 28px; border-radius: 24px; }
.resume :deep(.j-bouton--principal), .barre-mobile .j-bouton--principal { --action: var(--proof); --on-action: var(--on-proof); }
.resume .j-bouton, .resume :deep(.j-bouton) { border-radius: 50px; }
.resume__dotations { display: grid; gap: 10px; padding: 20px 0; border-block: 1px solid var(--line-strong); }
.resume__dotations > span { font-size: .875rem; color: var(--ink-muted); }
.resume__dotations strong { font: 700 1.65rem var(--font-display); letter-spacing: -.03em; overflow-wrap: anywhere; }
.resume__dotations button { min-height: 36px; border: 0; background: transparent; color: var(--action-ink); padding: 0; font: inherit; text-align: start; cursor: pointer; font-size: .875rem; }
#participer, .ancre { scroll-margin-top: 160px; }
@media (min-width: 960px) { .disposition { grid-template-columns: minmax(0, 1fr) 330px; gap: 28px; } .resume { top: 154px; max-block-size: calc(100dvh - 170px); } }
@media (max-width: 960px) { .onglets { top: 120px; } .evenement__banniere { grid-template-columns: minmax(0, 1fr) 200px; gap: 28px; } .evenement__affiche { min-height: 240px; padding: 20px; } .evenement__parcours { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 680px) { .evenement__banniere { grid-template-columns: minmax(0, 1fr); padding: 28px 24px; } .evenement__affiche { display: none; } .onglets { top: 76px; } }
</style>
