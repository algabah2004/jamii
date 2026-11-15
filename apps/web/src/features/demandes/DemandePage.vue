<script setup lang="ts">
import LienRetour from '../../components/LienRetour.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import {
  ATTENTE_PLAN_MAX_MS,
  choisirOffres,
  creerProjet,
  dateCourte,
  estRepli,
  etatAnalyse,
  fonctionnalitesParPriorite,
  INTERVALLE_PLAN_MS,
  jalonsAvecTaches,
  lireDemande,
  listerDemandes,
  preciserDemande,
  signalerClicService,
  type Demande,
  type DemandeResumee,
} from './demandes'
import AyoAvatar from './AyoAvatar.vue'
import PublierDemande from './PublierDemande.vue'

/*
 * L'orientation d'un besoin (étape 1 de la démo) : ce que Systalink offre déjà, les développeurs qui peuvent
 * faire le reste (un score calculé par l'API, avec ses raisons) et le plan. « L'IA propose, l'humain décide » :
 * tout est une proposition, et seul un clic humain en fait un projet ou un cercle.
 * Les textes générés sont affichés comme du texte brut, jamais interprétés.
 */
const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id))

const demande = ref<Demande | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const tropLong = ref(false)
let minuterie: ReturnType<typeof setTimeout> | undefined
let debut = 0

async function charger(silencieux = false) {
  if (!silencieux) etat.value = 'chargement'
  try {
    const lue = await lireDemande(id.value)
    if (!lue) return void (etat.value = 'introuvable')
    demande.value = lue
    etat.value = 'pret'
  } catch {
    if (!silencieux) etat.value = 'indisponible'
  }
  surveiller()
}

/** Tant que le plan se prépare, on relit la demande, sans jamais dépasser une minute. */
function surveiller() {
  clearTimeout(minuterie)
  const d = demande.value
  const statut = d ? (d.orientation ?? d.plan).statut : undefined
  if (statut !== 'en_attente' && statut !== 'en_cours') return
  if (!debut) debut = Date.now()
  if (Date.now() - debut >= ATTENTE_PLAN_MAX_MS) return void (tropLong.value = true)
  minuterie = setTimeout(() => void charger(true), INTERVALLE_PLAN_MS)
}
onBeforeUnmount(() => clearTimeout(minuterie))
/** « Réessayer » : on relit et on recommence à surveiller, une minute de plus au plus. */
function relancer() {
  debut = 0
  tropLong.value = false
  void charger()
}
watch(id, () => {
  debut = 0
  tropLong.value = false
  void charger()
}, { immediate: true })

/** L'historique à côté, comme une liste de conversations : on passe d'un besoin à l'autre sans rien perdre. */
const historique = ref<DemandeResumee[]>([])
function chargerHistorique() {
  void listerDemandes().then((l) => (historique.value = l)).catch(() => undefined)
}
chargerHistorique()

const analyse = computed(() => (demande.value ? etatAnalyse(demande.value) : 'attente'))
const orientation = computed(() => (analyse.value === 'pret' ? (demande.value?.orientation?.resultat ?? null) : null))
const plan = computed(() => {
  if (analyse.value !== 'pret' || !demande.value) return null
  return orientation.value ? (orientation.value.plan ?? null) : (demande.value.plan.resultat ?? null)
})
const pret = computed(() => analyse.value === 'pret')
const enPreparation = computed(() => analyse.value === 'attente' && !tropLong.value)
const repli = computed(() => (demande.value ? estRepli(demande.value) : false))
const recommandes = computed(() => demande.value?.recommandations ?? [])

/** Où en est le besoin : décrit, analysé (la réponse est prête), publié. */
const PARCOURS = ['preparation', 'prete', 'publiee'] as const
const rangParcours = computed(() => (demande.value?.publication ? 2 : pret.value ? 1 : 0))

/** Pendant l'analyse, on montre ce que fait l'assistant : l'attente se comprend. */
const ETAPES = ['lire', 'chercher', 'preparer'] as const
const secondes = ref(0)
let horloge: ReturnType<typeof setInterval> | undefined
watch(enPreparation, (oui) => {
  clearInterval(horloge)
  secondes.value = 0
  if (oui) horloge = setInterval(() => (secondes.value += 1), 1000)
}, { immediate: true })
onBeforeUnmount(() => clearInterval(horloge))
const etapeEnCours = computed(() => Math.min(ETAPES.length - 1, Math.floor(secondes.value / 7)))

/**
 * La conversation : la personne peut dire ce qui ne lui convient pas. L'analyse repart avec son besoin et toutes ses
 * précisions ; le fil montre ce qu'elle a écrit, puis la dernière réponse de l'assistant.
 */
const precision = ref('')
const envoiPrecision = ref<'repos' | 'envoi' | 'erreur' | 'limite' | 'quota'>('repos')
async function envoyerPrecision() {
  if (!demande.value || precision.value.trim().length < 3) return
  envoiPrecision.value = 'envoi'
  try {
    const r = await preciserDemande(demande.value.id, precision.value)
    if (r === 'limite' || r === 'quota') return void (envoiPrecision.value = r)
    if (!r) return void (envoiPrecision.value = 'erreur')
    demande.value = r
    precision.value = ''
    envoiPrecision.value = 'repos'
    detailsOuverts.value = false
    precisionOuverte.value = false
    debut = 0
    tropLong.value = false
    surveiller()
    chargerHistorique()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch {
    envoiPrecision.value = 'erreur'
  }
}

/**
 * Ayo pose au plus une question, seulement si la réponse changerait son conseil et que rien n'a encore été précisé.
 * On peut répondre d'un clic, avec ses mots, ou passer : le conseil est déjà prêt, il attend juste derrière.
 */
const questionPassee = ref(false)
watch(id, () => (questionPassee.value = false))
const questionOuverte = computed(
  () => Boolean(orientation.value?.question?.texte) && !demande.value?.precisions?.length && !questionPassee.value,
)
const reponseLibre = ref('')
function repondre(texte: string) {
  if (texte.trim().length < 3) return
  precision.value = texte
  reponseLibre.value = ''
  void envoyerPrecision()
}

/** Le premier service est le conseil principal d'Ayo ; les autres restent à un clic, sans s'imposer. */
const principal = computed(() => orientation.value?.services[0] ?? null)
const autresServices = computed(() => orientation.value?.services.slice(1) ?? [])
const etapesAyo = computed(() => orientation.value?.etapes ?? [])
const autresOuverts = ref(false)

/** « Ce n'est pas tout à fait ça ? » : une raison toute prête, que la personne complète si elle veut. */
const RAISONS = ['cher', 'complique', 'autre'] as const
function pasCa(raison: (typeof RAISONS)[number]) {
  precision.value = t(`demandes.ayo.pasCa.${raison}`) + (raison === 'autre' ? ' : ' : '.')
  precisionOuverte.value = true
  requestAnimationFrame(() => document.getElementById('precision')?.focus())
}

/** Le plan technique est écrit pour les développeurs : replié par défaut, ouvert d'un clic. */
const detailsOuverts = ref(false)
const dureeSemaines = computed(() => Math.max(0, ...(plan.value?.jalons ?? []).map((j) => j.semaine)))
const indispensables = computed(
  () => (plan.value?.cahierDesCharges.fonctionnalites ?? []).filter((f) => f.priorite === 'indispensable').length,
)
/**
 * Sous les conseils, trois onglets plutôt qu'une longue page : les développeurs, le projet (le plan technique), et
 * publier. Le premier ouvert est celui qui sert le plus : les développeurs s'il en faut, sinon publier.
 */
type Onglet = 'developpeurs' | 'projet' | 'publier'
const onglet = ref<Onglet>('developpeurs')
const onglets = computed<Onglet[]>(() => [
  ...(orientation.value?.developpeursNecessaires ? (['developpeurs'] as const) : []),
  ...(plan.value ? (['projet'] as const) : []),
  'publier',
])
/** Tant que la personne n'a pas choisi, l'onglet ouvert suit la réponse (le premier qui sert). */
let ongletChoisi = false
watch(onglets, (liste) => {
  if (!ongletChoisi || !liste.includes(onglet.value)) onglet.value = liste[0]!
}, { immediate: true })
function ouvrirOnglet(o: Onglet) {
  ongletChoisi = true
  onglet.value = o
  detailsOuverts.value = o === 'projet'
  requestAnimationFrame(() => document.getElementById('onglets')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}
function voirDeveloppeurs() {
  ouvrirOnglet('developpeurs')
}
function voirDetails() {
  ouvrirOnglet('projet')
}
/** « Ce n'est pas tout à fait ça ? » : replié, une ligne ; ouvert d'un clic. */
const precisionOuverte = ref(false)

/**
 * « Former un cercle avec eux » : la création du cercle s'ouvre avec le titre et la ville du besoin. Ils passent par
 * l'état de la navigation, jamais par l'adresse : le besoin ne se retrouve ni dans l'historique ni dans les journaux.
 */
function formerCercle() {
  const d = demande.value
  if (!d) return
  // Les développeurs recommandés suivent jusqu'au cercle, pour être invités en un clic une fois qu'il existe.
  const duBesoin = recommandes.value.map((r) => ({ id: r.utilisateurId, nom: r.nom }))
  void router.push({ path: '/cercles/nouveau', state: { nom: d.titre.slice(0, 60), ville: d.ville ?? '', duBesoin } })
}

/** « Recevoir les offres Systalink » : jamais cochée d'avance, modifiable à tout moment. */
const offres = ref<'repos' | 'envoi' | 'erreur'>('repos')
async function changerOffres(accepte: boolean) {
  if (!demande.value) return
  offres.value = 'envoi'
  try {
    const a_jour = await choisirOffres(demande.value.id, accepte)
    if (!a_jour) return void (offres.value = 'erreur')
    demande.value = { ...demande.value, offresSystalink: a_jour.offresSystalink }
    offres.value = 'repos'
  } catch {
    offres.value = 'erreur'
  }
}
const fonctionnalites = computed(() => (plan.value ? fonctionnalitesParPriorite(plan.value) : []))
const jalons = computed(() => (plan.value ? jalonsAvecTaches(plan.value) : []))

const lancement = ref<{ etat: 'repos' | 'envoi' | 'fait' | 'erreur'; code?: string }>({ etat: 'repos' })
async function lancer() {
  if (!demande.value) return
  lancement.value = { etat: 'envoi' }
  try {
    const r = await creerProjet(demande.value)
    lancement.value = 'id' in r ? { etat: 'fait' } : { etat: 'erreur', code: r.erreur }
  } catch {
    lancement.value = { etat: 'erreur', code: 'autre' }
  }
}
const messageErreur = computed(() =>
  lancement.value.code === 'demande_deja_prise' ? t('demandes.plan.dejaPrise') : t('demandes.plan.erreurLancement'),
)
</script>

<template>
  <div class="page-demande">
  <aside v-if="historique.length" class="historique" :aria-label="t('demandes.mesDemandes.titre')" data-test="historique">
    <RouterLink to="/demandes/nouvelle" class="j-bouton j-bouton--principal historique__nouvelle">+ {{ t('demandes.mesDemandes.nouvelle') }}</RouterLink>
    <h2 class="historique__titre">{{ t('demandes.mesDemandes.titre') }}</h2>
    <ul>
      <li v-for="d in historique.slice(0, 12)" :key="d.id">
        <RouterLink :to="`/demandes/${d.id}`" :aria-current="d.id === id ? 'page' : undefined" class="historique__lien">
          <span class="historique__nom">{{ d.titre }}</span>
          <span class="texte-discret">{{ dateCourte(d.creeLe) }}<template v-if="d.publiee"> · {{ t('demandes.mesDemandes.publiee') }}</template></span>
        </RouterLink>
      </li>
    </ul>
    <RouterLink v-if="historique.length > 12" to="/demandes" class="historique__toutes">{{ t('demandes.mesDemandes.toutes') }}</RouterLink>
  </aside>
  <section class="demande fond-verre">
    <p class="retour"><LienRetour to="/demandes">{{ t('demandes.mesDemandes.titre') }}</LienRetour></p>

    <JSquelette v-if="etat === 'chargement' && !demande" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/demandes">{{ t('demandes.mesDemandes.toutes') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="demande">
      <header class="tete">
        <ol class="parcours" :aria-label="t('demandes.mesDemandes.parcours')" data-test="parcours">
          <li v-for="(e, i) in PARCOURS" :key="e" :class="{ fait: i < rangParcours, actuel: i === rangParcours }">
            <span aria-hidden="true">{{ i < rangParcours ? '✓' : i + 1 }}</span>{{ t(`demandes.mesDemandes.jalons.${e}`) }}
          </li>
        </ol>
        <h1>{{ demande.titre }}</h1>
        <ul class="contexte" data-test="contexte">
          <li v-if="demande.secteur">{{ te(`demandes.secteurs.${demande.secteur}`) ? t(`demandes.secteurs.${demande.secteur}`) : demande.secteur }}</li>
          <li v-if="demande.ville">{{ demande.ville }}</li>
          <li v-if="demande.dureeSemaines">{{ t('demandes.nouvelle.semaines', { n: demande.dureeSemaines }, demande.dureeSemaines) }}</li>
          <li class="texte-discret">{{ dateCourte(demande.creeLe) }}</li>
        </ul>
        <!-- Ce que la personne a écrit : replié, une ligne ; ses mots, jamais interprétés. -->
        <details class="besoin" :open="!pret">
          <summary>{{ t('demandes.reponse.relire') }}<span v-if="demande.precisions?.length" class="besoin__compte">{{ t('demandes.mesDemandes.precisions', { n: demande.precisions.length }, demande.precisions.length) }}</span></summary>
          <div class="fil" data-test="fil">
            <p class="bulle bulle--moi">{{ demande.description }}</p>
            <p v-for="p in demande.precisions ?? []" :key="p.creeLe" class="bulle bulle--moi" data-test="precision">{{ p.texte }}</p>
          </div>
        </details>
      </header>

      <div v-if="enPreparation" class="carte attente" role="status" data-test="plan-attente">
        <AyoAvatar :taille="52" reflechit />
        <div>
          <strong>{{ t('demandes.ayo.reflechit') }}</strong>
          <ol class="etapes" data-test="etapes">
            <li v-for="(e, i) in ETAPES" :key="e" :class="{ 'etapes--faite': i < etapeEnCours, 'etapes--en-cours': i === etapeEnCours }">
              <span aria-hidden="true">{{ i < etapeEnCours ? '✓' : i === etapeEnCours ? '•' : '·' }}</span> {{ t(`demandes.plan.etapes.${e}`) }}
            </li>
          </ol>
        </div>
      </div>
      <div v-else-if="!pret" role="alert" class="carte attente attente--echec" data-test="plan-echec">
        <span class="attente__icone" aria-hidden="true">!</span>
        <div>
          <strong>{{ t('demandes.plan.echec') }}</strong>
          <p>{{ t('demandes.plan.echecAide') }}</p>
        </div>
        <div class="attente__actions">
          <JBouton variante="secondaire" @click="relancer()">{{ t('etat.reessayer') }}</JBouton>
          <RouterLink to="/cercles/nouveau" class="bouton-lien bouton-lien--discret">{{ t('demandes.plan.sansAttendre') }}</RouterLink>
        </div>
      </div>

      <template v-if="pret">
        <!-- 1. La réponse d'Ayo, et sa question s'il en a une : une seule carte, une seule voix. -->
        <section class="carte reponse" aria-labelledby="compris-titre" data-test="compris">
          <div class="reponse__tete">
            <AyoAvatar :taille="44" />
            <p class="reponse__qui"><strong>{{ t('demandes.ayo.nom') }}</strong><span>{{ t('demandes.ayo.role') }}</span></p>
            <span class="proposition" data-test="plan-propose">✨ {{ t('demandes.plan.propose') }}</span>
          </div>
          <h2 id="compris-titre" class="visuellement-cache">{{ t('demandes.orientation.compris') }}</h2>
          <p class="reponse__texte">{{ orientation ? orientation.message || orientation.resume : plan?.cahierDesCharges.resume }}</p>
          <p v-if="repli" class="reponse__repli" role="status" data-test="plan-repli"><span aria-hidden="true">ⓘ</span> {{ t(orientation ? 'demandes.orientation.repli' : 'demandes.plan.repli') }}</p>

          <div v-if="orientation?.question && questionOuverte" class="question-ayo" data-test="question-ayo">
            <p class="question-ayo__texte">{{ orientation.question.texte }}</p>
            <div class="puces">
              <button v-for="sug in orientation.question.suggestions" :key="sug" type="button" class="puce" :disabled="envoiPrecision === 'envoi'" data-test="suggestion" @click="repondre(sug)">{{ sug }}</button>
            </div>
            <form class="question-ayo__libre" @submit.prevent="repondre(reponseLibre)">
              <input v-model="reponseLibre" maxlength="500" :aria-label="t('demandes.ayo.question.libre')" :placeholder="t('demandes.ayo.question.libre')" />
              <JBouton type="submit" variante="secondaire" :occupe="envoiPrecision === 'envoi'" :disabled="reponseLibre.trim().length < 3">{{ t('demandes.ayo.question.repondre') }}</JBouton>
            </form>
            <p v-if="envoiPrecision === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</p>
            <p v-else-if="envoiPrecision === 'limite'" role="alert" class="erreur">{{ t('demandes.discuter.limite') }}</p>
            <p v-else-if="envoiPrecision === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
            <button type="button" class="lien-discret" data-test="passer" @click="questionPassee = true">{{ t('demandes.ayo.question.passer') }} →</button>
          </div>
        </section>

        <!-- 2. Ses conseils : au plus deux choix côte à côte, un seul bouton chacun. -->
        <section v-if="orientation && !questionOuverte" class="conseils" aria-labelledby="options" data-test="chemins">
          <h2 id="options" class="section-titre">{{ t('demandes.reponse.conseils') }}</h2>
          <div class="conseils__liste">
            <article v-if="principal" class="carte choix choix--systalink" data-test="services-systalink">
              <span class="choix__badge">{{ t('demandes.ayo.conseil') }}</span>
              <div class="choix__corps" data-test="service">
                <h3>{{ principal.nom }}</h3>
                <p>{{ principal.pourCeBesoin }}</p>
                <p v-if="principal.prixIndicatif" class="choix__prix">{{ principal.prixIndicatif }}</p>
                <a :href="principal.lien" target="_blank" rel="noopener noreferrer" class="choix__action" data-test="voir-service" @click="signalerClicService(principal.momentId)">
                  {{ t('demandes.orientation.voirService') }}<span class="visuellement-cache"> : {{ principal.nom }}</span> ↗</a>
              </div>
              <p class="choix__note">{{ t('demandes.chemins.pret.partenaire') }}</p>
            </article>

            <article v-if="orientation.developpeursNecessaires" class="carte choix choix--devs" data-test="chemin-developpeurs">
              <span class="choix__badge choix__badge--devs">{{ principal ? t('demandes.chemins.surMesure.badge') : t('demandes.ayo.conseil') }}</span>
              <h3>{{ t('demandes.chemins.surMesure.titre') }}</h3>
              <p>{{ t('demandes.chemins.surMesure.texte') }}</p>
              <div v-if="recommandes.length" class="choix__equipe">
                <span class="pile" aria-hidden="true">
                  <JAvatar v-for="r in recommandes.slice(0, 4)" :key="r.utilisateurId" :nom="r.nom" :url="r.avatarUrl" :taille="36" />
                </span>
                <span class="texte-discret">{{ t('demandes.chemins.surMesure.devs', { n: recommandes.length }, recommandes.length) }}<template v-if="dureeSemaines"> · {{ t('demandes.chemins.surMesure.duree', { n: dureeSemaines }, dureeSemaines) }}</template></span>
              </div>
              <div class="choix__boutons">
                <button v-if="recommandes.length" type="button" class="choix__action" data-test="former-cercle" @click="formerCercle">{{ t('demandes.orientation.formerCercle') }} →</button>
                <button v-else type="button" class="choix__action" @click="voirDeveloppeurs">{{ t('demandes.chemins.surMesure.voirDevs') }}</button>
                <button v-if="plan" type="button" class="lien-discret" data-test="voir-details" @click="voirDetails">{{ t('demandes.chemins.surMesure.voirProjet') }}</button>
              </div>
            </article>

            <article v-if="!principal && !orientation.developpeursNecessaires" class="carte choix">
              <h3>{{ t('demandes.chemins.aucun.titre') }}</h3>
              <p>{{ t('demandes.chemins.aucun.texte') }}</p>
              <RouterLink to="/communautes" class="choix__action">{{ t('demandes.chemins.aucun.action') }}</RouterLink>
            </article>
          </div>

          <div v-if="etapesAyo.length" class="carte maintenant" data-test="etapes-ayo">
            <h3>{{ t('demandes.ayo.maintenant') }}</h3>
            <ol>
              <li v-for="e in etapesAyo" :key="e">{{ e }}</li>
            </ol>
          </div>

          <div v-if="autresServices.length" class="autres">
            <button type="button" class="lien-discret" :aria-expanded="autresOuverts" aria-controls="autres-services" data-test="voir-autres" @click="autresOuverts = !autresOuverts">
              {{ t('demandes.ayo.autres', { n: autresServices.length }, autresServices.length) }} {{ autresOuverts ? '▴' : '▾' }}</button>
            <ul v-show="autresOuverts" id="autres-services" class="autres__liste">
              <li v-for="sv in autresServices" :key="sv.id" class="carte autre" data-test="service">
                <strong>{{ sv.nom }}</strong>
                <p>{{ sv.pourCeBesoin }}</p>
                <p v-if="sv.prixIndicatif" class="choix__prix">{{ sv.prixIndicatif }}</p>
                <a :href="sv.lien" target="_blank" rel="noopener noreferrer" class="lien-discret" data-test="voir-service" @click="signalerClicService(sv.momentId)">
                  {{ t('demandes.orientation.voirService') }}<span class="visuellement-cache"> : {{ sv.nom }}</span> ↗</a>
              </li>
            </ul>
          </div>
          <p class="libre" data-test="libre">🤝 {{ t('demandes.ayo.libre') }}</p>
        </section>

        <!-- 3. Le reste, rangé : les développeurs, le projet, publier. -->
        <section v-if="!questionOuverte" id="onglets" class="onglets">
          <div class="onglets__barre" role="tablist" :aria-label="t('demandes.reponse.suite')">
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
              @click="ouvrirOnglet(o)"
            >{{ t(`demandes.reponse.onglets.${o}`) }}<span v-if="o === 'developpeurs' && recommandes.length" class="onglet__compte">{{ recommandes.length }}</span></button>
          </div>

          <div v-if="orientation?.developpeursNecessaires" v-show="onglet === 'developpeurs'" id="panneau-developpeurs" role="tabpanel" aria-labelledby="onglet-developpeurs" class="panneau" data-test="developpeurs">
            <p class="texte-discret">{{ t('demandes.orientation.developpeursAide') }}</p>
            <ul v-if="recommandes.length" class="recommandes">
              <li v-for="r in recommandes" :key="r.utilisateurId" class="carte recommande" data-test="recommande">
                <div class="recommande__tete">
                  <JAvatar :nom="r.nom" :url="r.avatarUrl" :taille="44" />
                  <div>
                    <strong>{{ r.nom }}</strong>
                    <span v-if="r.ville" class="texte-discret">{{ r.ville }}</span>
                  </div>
                </div>
                <ul class="raisons" :aria-label="t('demandes.orientation.pourquoi')">
                  <li v-for="raison in r.raisons" :key="raison">✓ {{ raison }}</li>
                </ul>
                <RouterLink :to="`/messages/${r.utilisateurId}`" class="recommande__ecrire">{{ t('demandes.orientation.ecrire', { nom: r.nom.split(' ')[0] }) }}</RouterLink>
              </li>
            </ul>
            <p v-else class="carte vide" data-test="aucun-recommande">{{ t('demandes.orientation.aucun') }}</p>
          </div>

          <div v-if="plan" v-show="onglet === 'projet' || !orientation" id="details-projet" role="tabpanel" aria-labelledby="onglet-projet" class="panneau details">
            <section class="carte bloc" aria-labelledby="resume">
              <h3 id="resume">{{ t('demandes.plan.resume') }}</h3>
              <p class="resume">{{ plan.cahierDesCharges.resume }}</p>
              <ul class="objectifs">
                <li v-for="o in plan.cahierDesCharges.objectifs" :key="o">{{ o }}</li>
              </ul>
            </section>

            <section class="bloc-nu" aria-labelledby="fonctionnalites">
              <h3 id="fonctionnalites">{{ t('demandes.plan.fonctionnalites') }}</h3>
              <div class="priorites">
                <div v-for="g in fonctionnalites" :key="g.priorite" class="carte priorite" :class="`priorite--${g.priorite}`" data-test="priorite">
                  <h4>{{ t(`demandes.priorites.${g.priorite}`) }}</h4>
                  <ul>
                    <li v-for="f in g.liste" :key="f.titre"><strong>{{ f.titre }}</strong><span>{{ f.description }}</span></li>
                  </ul>
                </div>
              </div>
            </section>

            <section class="bloc-nu" aria-labelledby="jalons">
              <h3 id="jalons">{{ t('demandes.plan.jalons') }}</h3>
              <ol class="jalons" data-test="jalons">
                <li v-for="j in jalons" :key="j.titre" class="carte jalon">
                  <span class="jalon__semaine">{{ t('demandes.plan.semaine', { n: j.semaine }) }}</span>
                  <h4>{{ j.titre }}</h4>
                  <p>{{ j.livrable }}</p>
                  <ul v-if="j.taches.length" class="taches">
                    <li v-for="tache in j.taches" :key="tache.titre">
                      <span>{{ tache.titre }}</span>
                      <span class="taches__competences">{{ tache.competences.join(' · ') }}</span>
                    </li>
                  </ul>
                </li>
              </ol>
            </section>

            <div class="deux">
              <section v-if="plan.rolesManquants.length" class="carte bloc" aria-labelledby="roles">
                <h3 id="roles">{{ t('demandes.plan.roles') }}</h3>
                <p class="texte-discret">{{ t('demandes.plan.rolesAide') }}</p>
                <ul class="roles" data-test="roles">
                  <li v-for="r in plan.rolesManquants" :key="r.role">
                    <strong>{{ r.role }}</strong>
                    <span class="roles__competences"><span v-for="c in r.competences" :key="c" class="etiquette">{{ c }}</span></span>
                    <span class="texte-discret">{{ r.raison }}</span>
                  </li>
                </ul>
              </section>
              <section v-if="!orientation && plan.servicesSuggeres.length" class="carte bloc" aria-labelledby="services">
                <h3 id="services">{{ t('demandes.plan.services') }}</h3>
                <p class="texte-discret">{{ t('demandes.plan.servicesAide') }}</p>
                <ul class="services">
                  <li v-for="sv in plan.servicesSuggeres" :key="sv.service">
                    <strong>{{ t(`systalink.services.${sv.service === 'domaine' ? 'domaines' : sv.service}`) }}</strong>
                    <span class="texte-discret">{{ sv.raison }}</span>
                  </li>
                </ul>
              </section>
            </div>

            <section v-if="plan.cahierDesCharges.horsPerimetre.length" class="bloc-nu" aria-labelledby="hors">
              <h3 id="hors">{{ t('demandes.plan.horsPerimetre') }}</h3>
              <ul class="hors">
                <li v-for="h in plan.cahierDesCharges.horsPerimetre" :key="h">{{ h }}</li>
              </ul>
            </section>

            <!-- Suivre ce plan seul, sans cercle : la suite discrète, pour qui en a besoin. -->
            <div class="carte lancer-seul">
              <p v-if="lancement.etat === 'fait'" role="status" class="decision__fait" data-test="projet-cree">✓ {{ t('demandes.plan.lance') }}</p>
              <template v-else>
                <p>{{ t('demandes.reponse.seul') }}</p>
                <p v-if="lancement.etat === 'erreur'" role="alert" class="erreur">{{ messageErreur }}</p>
                <div class="decision__actions">
                  <JBouton variante="secondaire" :occupe="lancement.etat === 'envoi'" data-test="lancer" @click="lancer">{{ t('demandes.plan.lancer') }}</JBouton>
                  <RouterLink :to="{ name: 'nouvelle-mission', query: { demande: demande.id } }" class="j-bouton j-bouton--secondaire" data-test="publier-mission">
                    {{ t('demandes.plan.publierMission') }}
                  </RouterLink>
                </div>
              </template>
            </div>
          </div>

          <div v-show="onglet === 'publier'" id="panneau-publier" role="tabpanel" aria-labelledby="onglet-publier" class="panneau">
            <PublierDemande :demande="demande" :resume-propose="orientation?.resume" @publiee="demande = $event" />
            <section class="carte offres" aria-labelledby="offres-titre" data-test="offres-resultat">
              <label class="offres__case">
                <input type="checkbox" :checked="demande.offresSystalink === true" :disabled="offres === 'envoi'" @change="changerOffres(($event.target as HTMLInputElement).checked)" />
                <span>
                  <strong id="offres-titre">{{ t('demandes.offres.libelle') }}</strong>
                  <span class="texte-discret">{{ t('demandes.offres.aide') }}</span>
                  <span class="texte-discret">{{ t('demandes.offres.retrait') }}</span>
                </span>
              </label>
              <p v-if="offres === 'erreur'" role="alert" class="erreur">{{ t('demandes.offres.erreur') }}</p>
            </section>
          </div>
        </section>

        <!-- 4. Une seule porte pour dire « ce n'est pas ça » : repliée, une ligne. -->
        <form v-if="orientation && !questionOuverte" class="carte preciser" :class="{ 'preciser--ouvert': precisionOuverte }" data-test="preciser" @submit.prevent="envoyerPrecision">
          <button type="button" class="preciser__ouvrir" :aria-expanded="precisionOuverte" aria-controls="preciser-corps" @click="precisionOuverte = !precisionOuverte">
            <strong>{{ t('demandes.ayo.pasCa.titre') }}</strong>
            <span>{{ precisionOuverte ? '▴' : t('demandes.reponse.preciser') + ' →' }}</span>
          </button>
          <div v-show="precisionOuverte" id="preciser-corps" class="preciser__corps">
            <span class="texte-discret">{{ t('demandes.discuter.aide') }}</span>
            <div class="puces">
              <button v-for="r in RAISONS" :key="r" type="button" class="puce" :data-test="`pas-ca-${r}`" @click="pasCa(r)">{{ t(`demandes.ayo.pasCa.${r}`) }}</button>
            </div>
            <div class="preciser__ligne">
              <label for="precision" class="visuellement-cache">{{ t('demandes.ayo.pasCa.titre') }}</label>
              <textarea id="precision" v-model="precision" maxlength="500" rows="2" :placeholder="t('demandes.discuter.exemple')" @keydown.enter.exact.prevent="envoyerPrecision" />
              <JBouton type="submit" :occupe="envoiPrecision === 'envoi'" :disabled="precision.trim().length < 3" data-test="envoyer-precision">{{ t('demandes.discuter.envoyer') }}</JBouton>
            </div>
            <p v-if="envoiPrecision === 'limite'" role="alert" class="erreur">{{ t('demandes.discuter.limite') }}</p>
            <p v-else-if="envoiPrecision === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</p>
            <p v-else-if="envoiPrecision === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
          </div>
          <p v-if="demande.conseilsRestants !== undefined" class="texte-discret preciser__restants" data-test="conseils-restants">
            {{ t('demandes.ayo.restants', { n: demande.conseilsRestants }, demande.conseilsRestants) }}</p>
        </form>
      </template>

      <!-- Une API plus ancienne, sans orientation : le plan seul, toujours visible. -->
    </template>
  </section>
  </div>
</template>

<style scoped>
.page-demande { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr); align-items: start; }
.historique { display: none; }
@media (min-width: 1100px) {
  .page-demande { grid-template-columns: 15rem minmax(0, 64rem); }
  .historique { position: sticky; top: var(--space-6); display: grid; gap: var(--space-3); max-height: calc(100vh - 2 * var(--space-6)); overflow-y: auto; }
}
.historique__nouvelle { justify-content: center; }
.historique__titre { margin: var(--space-2) 0 0; font-size: var(--type-body-sm-size); color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.04em; }
.historique ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.historique__lien { display: grid; gap: 2px; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); color: var(--ink); text-decoration: none; }
.historique__lien:hover { background: color-mix(in srgb, var(--surface) 70%, transparent); }
.historique__lien[aria-current='page'] { background: var(--action-soft); font-weight: 600; }
.historique__nom { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.historique__lien .texte-discret { font-size: var(--type-label-size); }
.historique__toutes { font-size: var(--type-body-sm-size); font-weight: 600; }
.fil { display: grid; gap: var(--space-2); justify-items: end; }
.bulle { margin: 0; max-width: min(42rem, 92%); padding: var(--space-3) var(--space-4); border-radius: var(--radius-lg); white-space: pre-wrap; line-height: 1.5; }
.bulle--moi { background: var(--action-soft); color: var(--ink); border-end-end-radius: 4px; }
.bulle--jamii { background: var(--surface); border: 1px solid var(--line); border-end-start-radius: 4px; max-width: 100%; }
.bulle--jamii .resume { margin: 0; font-size: 1.0625rem; }
.assistant { display: grid; grid-template-columns: auto 1fr; gap: var(--space-3); align-items: end; }
.assistant__nom { margin: 0 0 var(--space-1); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.assistant__nom strong { color: var(--ink); }
.question-ayo { display: grid; gap: var(--space-4); border-inline-start: 3px solid var(--action); }
.question-ayo__texte { margin: 0; font-size: 1.1875rem; }
.question-ayo__libre { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.question-ayo__libre input { flex: 1 1 16rem; min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; }
.question-ayo__passer { justify-self: start; }
.puces { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.puce {
  min-height: 40px; padding: 0 var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--surface) 70%, transparent); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer;
  transition: border-color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.puce:hover:not(:disabled) { border-color: var(--action); background: var(--action-soft); }
.puce:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.puce:disabled { opacity: 0.6; cursor: wait; }
.chemin--principal { border: 2px solid var(--action); }
.maintenant { display: grid; gap: var(--space-2); }
.maintenant h3 { margin: 0; font-size: 1.0625rem; }
.maintenant ol { margin: 0; padding-inline-start: 1.4em; display: grid; gap: var(--space-2); line-height: 1.5; }
.maintenant ::marker { font-weight: 700; color: var(--action-ink); }
.autres { display: grid; gap: var(--space-3); justify-items: start; }
.autres__liste { list-style: none; margin: 0; padding: 0; width: 100%; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr)); }
.autre { display: grid; gap: var(--space-2); align-content: start; }
.autre p { margin: 0; }
.chemins__libre { margin: 0; color: var(--ink-muted); }
.discuter__restants { margin: 0; font-size: var(--type-body-sm-size); }
.discuter { display: grid; gap: var(--space-3); }
.discuter__titre { display: grid; gap: 2px; }
.discuter__ligne { display: flex; gap: var(--space-3); align-items: flex-end; flex-wrap: wrap; }
.discuter textarea { flex: 1 1 16rem; min-height: 3.25rem; padding: var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; resize: vertical; }
.etapes { list-style: none; margin: var(--space-2) 0 0; padding: 0; display: grid; gap: var(--space-1); color: var(--ink-muted); }
.etapes--faite { color: var(--ink); }
.etapes--en-cours { color: var(--action-ink); font-weight: 600; }
.chemins { display: grid; gap: var(--space-4); }
.chemins__tete h2 { margin: 0; }
.chemins__tete p { margin: var(--space-1) 0 0; }
.chemins__liste { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr)); align-items: stretch; }
.chemin { display: grid; gap: var(--space-3); align-content: start; }
.chemin h3 { margin: 0; font-size: 1.25rem; }
.chemin p { margin: 0; }
.chemin__badge { justify-self: start; padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 700; }
.chemin__badge--dev { background: var(--proof-soft); color: var(--proof-ink); }
.chemin__services { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.chemin__services li { display: grid; gap: var(--space-1); padding-block-end: var(--space-3); border-block-end: 1px solid var(--line); }
.chemin__services li:last-child { border: 0; padding: 0; }
.chemin__prix { font-weight: 600; }
.chemin__action { justify-self: start; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-5); border-radius: var(--radius-pill); border: 0; background: var(--action); color: var(--on-action); font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
.chemin__note { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.chemin__chiffres { margin: 0; padding-inline-start: 1.2em; display: grid; gap: var(--space-1); }
.chemin__boutons { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.chemins__conseil { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-sm); background: var(--surface-raised); }
.details-bascule { display: flex; justify-content: center; }
.details { display: grid; gap: var(--space-6); }
.demande { display: grid; gap: var(--space-6); max-width: 64rem; }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.tete { display: grid; gap: var(--space-4); }
.tete h1 { margin: 0; font-size: clamp(1.875rem, 4vw, 2.5rem); line-height: 1.1; letter-spacing: -0.6px; }
.besoin { margin: 0; padding: var(--space-4) var(--space-6); white-space: pre-wrap; color: var(--ink-muted); font-style: italic; border-inline-start: 3px solid var(--line-strong); }

.attente { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); padding: var(--space-6); }
.attente strong { display: block; }
.attente p { margin: var(--space-1) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.attente--echec { justify-content: space-between; border-inline-start: 4px solid var(--danger); }
.attente--echec > div:nth-child(2) { flex: 1 1 16rem; }
.attente__icone { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: color-mix(in srgb, var(--danger) 14%, transparent); color: var(--danger); font-weight: 800; }
.attente__actions { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.bouton-lien--discret { background: transparent; color: var(--action-ink); border: 1px solid var(--line-strong); }
.parcours { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.parcours li { display: inline-flex; align-items: center; gap: var(--space-2); }
.parcours li span { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid var(--line-strong); font-size: 0.75rem; font-weight: 700; }
.parcours li.fait { color: var(--ink); font-weight: 600; }
.parcours li.fait span { background: var(--ink); border-color: var(--ink); color: var(--bg); }
.parcours li.actuel { color: var(--ink); font-weight: 700; }
.parcours li.actuel span { background: var(--cantaloupe); border-color: var(--cantaloupe); color: #02070A; }
.contexte { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.contexte li { padding: 2px 12px; border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line); font-size: var(--type-body-sm-size); font-weight: 600; }
.repli { border-inline-start: 4px solid var(--electric-blue); }
@media (prefers-reduced-motion: reduce) { .points i { animation: none; } }

/* Charte : une proposition de l'IA n'est pas une preuve, d'où le pointillé et pas d'orange. */
.proposition { justify-self: start; margin: 0; padding: var(--space-1) var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); background: color-mix(in srgb, var(--action-soft) 70%, transparent); font-size: var(--type-body-sm-size); font-weight: 600; }
.repli { margin: 0; padding: var(--space-4) var(--space-6); font-weight: 600; }

.bloc { display: grid; gap: var(--space-3); padding: var(--space-6) var(--space-8); }
.bloc-nu { display: grid; gap: var(--space-4); }
.bloc h2, .bloc-nu h2 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.resume { margin: 0; font-size: 1.0625rem; line-height: 1.65; white-space: pre-wrap; }
.objectifs { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.objectifs li { display: flex; gap: var(--space-2); }
.objectifs li::before { content: '→'; color: var(--action-ink); font-weight: 700; }

.priorites { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr)); }
.priorite { padding: var(--space-6); display: grid; gap: var(--space-3); align-content: start; }
.priorite h3 { margin: 0; font-size: 1rem; display: flex; align-items: center; gap: var(--space-2); }
.priorite h3::before { content: ''; width: 10px; height: 10px; border-radius: 50%; background: var(--line-strong); }
.priorite--indispensable h3::before { background: var(--action); }
.priorite--utile h3::before { background: color-mix(in srgb, var(--action) 45%, var(--surface)); }
.priorite ul { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.priorite li { display: grid; gap: 2px; }
.priorite li span { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

/* Les jalons : une frise, de la première semaine à la dernière. */
.jalons { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 17rem), 1fr)); counter-reset: jalon; }
.jalon { position: relative; display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); }
.jalon__semaine { justify-self: start; padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-size: var(--type-label-size); font-weight: 700; }
.jalon h3 { margin: 0; font-size: 1.0625rem; }
.jalon p { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.taches { list-style: none; margin: var(--space-2) 0 0; padding: var(--space-3) 0 0; border-top: 1px solid color-mix(in srgb, var(--ink) 10%, transparent); display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.taches li { display: grid; }
.taches__competences { color: var(--ink-muted); font-size: var(--type-label-size); }

.deux { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr)); align-items: start; }
.roles, .services { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.roles li, .services li { display: grid; gap: var(--space-1); }
.roles__competences { display: flex; flex-wrap: wrap; gap: var(--space-1); }
.hors { margin: 0; padding-inline-start: 1.25rem; color: var(--ink-muted); display: grid; gap: var(--space-1); }

.decision {
  position: sticky; bottom: var(--space-4); z-index: 2;
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3) var(--space-6);
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-8); border-radius: var(--radius-pill);
}
.decision__texte { margin: 0; font-weight: 600; }
.decision__actions { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.decision__fait { margin: 0; font-weight: 700; color: var(--ink); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

/* Systalink : le seul bloc sombre de la page, pour qu'on le voie. Le reste reste calme. */
.systalink { display: grid; gap: var(--space-6); padding: var(--space-8); border-radius: var(--radius-lg); background: var(--surface); color: var(--ink); }
.systalink__tete h2 { margin: 0; font-size: var(--type-h2-size); line-height: var(--type-h2-line); }
.systalink__tete p { margin: var(--space-2) 0 0; color: var(--ink-muted); }
.systalink__services { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr)); }
.service {
  display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--ink) 6%, transparent); border: 1px solid color-mix(in srgb, var(--ink) 14%, transparent);
}
.service h3 { margin: 0; font-size: 1.0625rem; }
.service p { margin: 0; }
.service__prix { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.service__lien {
  justify-self: start; margin-top: var(--space-2); display: inline-flex; align-items: center; min-height: 40px; padding: 0 var(--space-4);
  border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-weight: 700; text-decoration: none;
}
.service__lien:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.bloc-nu__tete { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; gap: var(--space-3); }
.bloc-nu__tete p { margin: var(--space-1) 0 0; }
.bouton-lien {
  border: 0; cursor: pointer; font: inherit;
  display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-6); border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font-weight: 700; text-decoration: none;
}
.recommandes { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr)); }
.recommande { display: grid; gap: var(--space-3); align-content: start; padding: var(--space-6); }
.recommande__tete { display: flex; align-items: center; gap: var(--space-3); }
.recommande__tete div { display: grid; }
.raisons { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-1); font-size: var(--type-body-sm-size); }
.recommande__ecrire { justify-self: start; font-weight: 600; }
.vide { margin: 0; padding: var(--space-6); color: var(--ink-muted); }
.plan-titre { margin: var(--space-4) 0 0; font-size: var(--type-h2-size); line-height: var(--type-h2-line); }

.offres { padding: var(--space-6); }
.offres__case { display: flex; gap: var(--space-3); align-items: flex-start; cursor: pointer; }
.offres__case input { width: 20px; height: 20px; margin: 2px 0 0; flex: none; accent-color: var(--action); }
.offres__case > span { display: grid; gap: var(--space-1); }

/* La page de réponse : une carte d'Ayo, deux choix, des onglets, une ligne pour préciser. */
.tete { gap: var(--space-3); }
.besoin { border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); }
.besoin summary { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-3) var(--space-4); cursor: pointer; font-weight: 600; font-size: var(--type-body-sm-size); list-style: none; }
.besoin summary::-webkit-details-marker { display: none; }
.besoin summary::before { content: '▸'; transition: transform var(--motion-fast) var(--ease-out); }
.besoin[open] summary::before { transform: rotate(90deg); }
.besoin__compte { margin-inline-start: auto; padding: 0 8px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; }
.besoin .fil { padding: 0 var(--space-4) var(--space-4); }

.reponse { display: grid; gap: var(--space-3); padding: var(--space-6); border-top: 4px solid var(--electric-blue); }
.reponse__tete { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.reponse__qui { margin: 0; display: grid; line-height: 1.2; }
.reponse__qui span { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.reponse__tete .proposition { margin-inline-start: auto; }
.reponse__texte { margin: 0; font-size: 1.125rem; line-height: 1.6; white-space: pre-wrap; }
.reponse__repli { margin: 0; padding: var(--space-2) var(--space-3); border-radius: var(--radius-sm); background: var(--surface-raised); font-size: var(--type-body-sm-size); font-weight: 600; }
.reponse .question-ayo { display: grid; gap: var(--space-3); margin-top: var(--space-2); padding-top: var(--space-4); border-top: 1px dashed var(--line-strong); border-inline-start: 0; }
.reponse .question-ayo__texte { margin: 0; font-size: 1.0625rem; font-weight: 700; }

.section-titre { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.conseils { display: grid; gap: var(--space-4); }
.conseils__liste { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr)); align-items: stretch; }
.choix { display: grid; gap: var(--space-3); align-content: start; padding: var(--space-6); }
.choix h3 { margin: 0; font-size: 1.1875rem; }
.choix p { margin: 0; }
.choix--systalink { border: 2px solid var(--action); }
.choix--devs { border: 2px solid color-mix(in srgb, var(--cantaloupe) 70%, transparent); }
.choix__badge { justify-self: start; padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
.choix__badge--devs { background: var(--proof-soft); color: var(--proof-ink); }
.choix__corps { display: grid; gap: var(--space-2); }
.choix__prix { font-weight: 700; }
.choix__note { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.choix__equipe { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.pile { display: inline-flex; }
.pile > :not(:first-child) { margin-inline-start: -10px; }
.pile > * { box-shadow: 0 0 0 2px var(--surface); border-radius: 50%; }
.choix__boutons { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); margin-top: var(--space-1); }
.choix__action { justify-self: start; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-5, 1.25rem); border-radius: var(--radius-pill); border: 0; background: var(--action); color: var(--on-action); font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
.choix__action:focus-visible, .lien-discret:focus-visible, .onglet:focus-visible, .preciser__ouvrir:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.lien-discret { justify-self: start; border: 0; padding: 0; background: none; color: var(--action-ink); font: inherit; font-weight: 600; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }
.libre { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.onglets { display: grid; gap: var(--space-4); scroll-margin-top: var(--space-6); }
.onglets__barre { display: flex; gap: var(--space-1); overflow-x: auto; border-bottom: 1px solid var(--line); }
.onglet { position: relative; display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-4); border: 0; background: none; color: var(--ink-muted); font: inherit; font-weight: 600; cursor: pointer; white-space: nowrap; }
.onglet[aria-selected='true'] { color: var(--ink); }
.onglet[aria-selected='true']::after { content: ''; position: absolute; inset: auto var(--space-2) -1px; height: 3px; border-radius: 3px 3px 0 0; background: var(--cantaloupe); }
.onglet__compte { padding: 0 8px; border-radius: var(--radius-pill); background: var(--surface-raised); font-size: 0.75rem; }
.panneau { display: grid; gap: var(--space-4); }
.panneau > p { margin: 0; }
.details h3 { margin: 0; font-size: 1.0625rem; }
.priorite h4, .jalon h4 { margin: 0; font-size: 1rem; }
.lancer-seul { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-6); }
.lancer-seul p { margin: 0; }

.preciser { display: grid; gap: var(--space-3); padding: var(--space-4) var(--space-6); }
.preciser__ouvrir { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2); width: 100%; padding: 0; border: 0; background: none; color: var(--ink); font: inherit; text-align: start; cursor: pointer; }
.preciser__ouvrir span { color: var(--action-ink); font-weight: 600; }
.preciser__corps { display: grid; gap: var(--space-3); }
.preciser__ligne { display: flex; gap: var(--space-3); align-items: flex-end; flex-wrap: wrap; }
.preciser textarea { flex: 1 1 16rem; min-height: 3.25rem; padding: var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; resize: vertical; }
.preciser__restants { margin: 0; font-size: 0.8125rem; }
@media (prefers-reduced-motion: reduce) { .besoin summary::before { transition: none; } }
</style>
