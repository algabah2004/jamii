<script setup lang="ts">
import LienRetour from '../../components/LienRetour.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import {
  ATTENTE_PLAN_MAX_MS,
  choisirOffres,
  CONVERSATION,
  creerProjet,
  dateCourte,
  estRepli,
  etatAnalyse,
  filDe,
  fonctionnalitesParPriorite,
  INTERVALLE_PLAN_MS,
  jalonsAvecTaches,
  lireDemande,
  listerDemandes,
  preciserDemande,
  signalerClicService,
  supprimerDemande,
  type Demande,
  type DemandeResumee,
} from './demandes'
import AyoAvatar from './AyoAvatar.vue'
import BadgeIa from '../../components/BadgeIa.vue'
import PublierDemande from './PublierDemande.vue'
import { ajusterZone } from '../../design-system/zonesDeTexte'

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
// Les plus proches du besoin d'abord, avec leur correspondance affichée (comme sur Malt), et les raisons qui l'expliquent.
const recommandesTries = computed(() => [...recommandes.value].sort((a, b) => b.score - a.score))
const aComparer = ref<string[]>([])
function basculerComparaison(id: string) {
  aComparer.value = aComparer.value.includes(id) ? aComparer.value.filter((x) => x !== id) : [...aComparer.value, id].slice(0, 3)
}
const compares = computed(() => recommandesTries.value.filter((r) => aComparer.value.includes(r.utilisateurId)))

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
 * La conversation avec Ayo : la personne lui répond, ou réagit à son conseil, dans la zone en bas. Ayo relit toute la
 * conversation, puis pose une autre question ou conseille. Le fil montre ce qui a déjà été dit.
 */
const fil = computed(() => (demande.value ? filDe(demande.value) : []))
const message = ref('')
const zoneMessage = ref<HTMLTextAreaElement | null>(null)
const envoiPrecision = ref<'repos' | 'envoi' | 'erreur' | 'limite' | 'quota'>('repos')
const messageValide = computed(() => message.value.trim().length >= CONVERSATION.reponseMin && !enPreparation.value && envoiPrecision.value !== 'envoi')
async function envoyerMessage(texte = message.value) {
  if (!demande.value || texte.trim().length < CONVERSATION.reponseMin || enPreparation.value || envoiPrecision.value === 'envoi') return
  envoiPrecision.value = 'envoi'
  try {
    const r = await preciserDemande(demande.value.id, texte.trim())
    if (r === 'limite' || r === 'quota') return void (envoiPrecision.value = r)
    if (!r) return void (envoiPrecision.value = 'erreur')
    demande.value = r
    message.value = ''
    if (zoneMessage.value) ajusterZone(zoneMessage.value)
    envoiPrecision.value = 'repos'
    detailsOuverts.value = false
    debut = 0
    tropLong.value = false
    surveiller()
    chargerHistorique()
    requestAnimationFrame(() => document.getElementById('ayo-maintenant')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }))
  } catch {
    envoiPrecision.value = 'erreur'
  }
}
function toucheMessage(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void envoyerMessage()
  }
}
/** Une réponse toute prête à la question d'Ayo : elle part d'un clic, comme si la personne l'avait écrite. */
const repondre = (texte: string) => void envoyerMessage(texte)

/** Quand Ayo pose une question, il ne conseille pas encore : sa question attend la réponse. */
const questionOuverte = computed(() => Boolean(orientation.value?.question?.texte))

/** Supprimer la conversation : la demande et tout ce qui a été dit. Une mission déjà publiée reste en ligne. */
const suppression = ref<'repos' | 'envoi' | 'erreur'>('repos')
async function supprimer() {
  if (!demande.value || !window.confirm(t('demandes.discussion.confirmerSuppression'))) return
  suppression.value = 'envoi'
  try {
    if (!(await supprimerDemande(demande.value.id))) return void (suppression.value = 'erreur')
    suppression.value = 'repos'
    await router.push('/demandes')
  } catch {
    suppression.value = 'erreur'
  }
}

/**
 * Les services Systalink proposés, chacun avec ce qu'il est : de l'hébergement (DataCloud : mettre en ligne, stocker),
 * ou un outil prêt à l'emploi. Systalink n'écrit pas le logiciel : la page le dit pour chaque hébergement.
 */
const services = computed(() =>
  (orientation.value?.services ?? []).map((sv) => ({ ...sv, genre: sv.id.startsWith('datacloud-') ? ('hebergement' as const) : ('outil' as const) })),
)
const etapesAyo = computed(() => orientation.value?.etapes ?? [])

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
    <RouterLink to="/demandes" class="j-bouton j-bouton--principal historique__nouvelle" data-test="nouvelle-discussion">+ {{ t('demandes.discussion.nouvelle') }}</RouterLink>
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
    <div class="entete">
      <p class="retour"><LienRetour to="/demandes">{{ t('demandes.mesDemandes.titre') }}</LienRetour></p>
      <button v-if="demande" type="button" class="lien-discret supprimer" :disabled="suppression === 'envoi'" data-test="supprimer-discussion" @click="supprimer">
        {{ t('demandes.discussion.supprimer') }}
      </button>
    </div>
    <p v-if="suppression === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>

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
      <!-- Seule la réponse d'Ayo occupe la page : le titre reste pour les lecteurs d'écran et l'historique à côté. -->
      <h1 class="visuellement-cache">{{ demande.titre }}</h1>

      <!-- Ce qui a déjà été dit : le premier message, puis chaque échange. Texte brut, affiché échappé. -->
      <ol class="fil" :aria-label="t('demandes.ayo.conversation')" data-test="fil">
        <li v-for="(b, i) in fil" :key="i" :class="b.qui === 'moi' ? 'bulle-moi' : 'ayo-message'" :data-test="`bulle-${b.qui}`">
          <p v-if="b.qui === 'moi'" class="bulle-moi__texte">{{ b.texte }}</p>
          <template v-else>
            <AyoAvatar :taille="32" class="ayo-message__avatar" />
            <p class="ayo-message__bulle bulle-ayo">{{ b.texte }}</p>
          </template>
        </li>
      </ol>
      <span id="ayo-maintenant" />

      <div v-if="enPreparation" class="ayo-message" role="status" data-test="plan-attente">
        <AyoAvatar :taille="44" reflechit class="ayo-message__avatar" />
        <div class="ayo-message__bulle">
          <p class="ayo-message__qui"><strong>{{ t('demandes.ayo.nom') }}</strong> <BadgeIa long /></p>
          <p class="ecrit"><span class="points" aria-hidden="true"><i /><i /><i /></span><strong>{{ t('demandes.ayo.reflechit') }}</strong></p>
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
        <!-- La réponse d'Ayo, en une seule carte rangée en sections : ce qu'il a compris, son conseil, la suite. -->
        <section class="ayo-message reponse" aria-labelledby="compris-titre" data-test="compris">
          <AyoAvatar :taille="44" class="ayo-message__avatar" />
          <div class="ayo-message__bulle">
            <div class="reponse__tete">
              <p class="ayo-message__qui"><strong>{{ t('demandes.ayo.nom') }}</strong> <BadgeIa long /></p>
              <span class="proposition" data-test="plan-propose">{{ t('demandes.plan.propose') }}</span>
            </div>
            <h2 id="compris-titre" :class="questionOuverte ? 'visuellement-cache' : 'reponse__titre'">{{ t(questionOuverte ? 'demandes.discussion.question' : 'demandes.orientation.compris') }}</h2>
            <p class="reponse__texte" data-test="ayo-message">{{ orientation ? orientation.message || orientation.resume : plan?.cahierDesCharges.resume }}</p>
            <p v-if="repli" class="reponse__repli" role="status" data-test="plan-repli"><span aria-hidden="true">ⓘ</span> {{ t(orientation ? 'demandes.orientation.repli' : 'demandes.plan.repli') }}</p>

            <div v-if="orientation?.question" class="question-ayo" data-test="question-ayo">
              <p class="question-ayo__texte">{{ orientation.question.texte }}</p>
              <div class="puces">
                <button v-for="sug in orientation.question.suggestions" :key="sug" type="button" class="puce" :disabled="envoiPrecision === 'envoi'" data-test="suggestion" @click="repondre(sug)">{{ sug }}</button>
              </div>
              <p class="texte-discret">{{ t('demandes.discussion.ouEcris') }}</p>
            </div>

            <template v-if="orientation && !questionOuverte">
              <section class="reponse__partie" aria-labelledby="options" data-test="chemins">
                <h2 id="options" class="reponse__titre">{{ t('demandes.reponse.conseils') }}</h2>

                <!-- Le développement : ce que Systalink ne fait pas. -->
                <article v-if="orientation.developpeursNecessaires" class="piste" data-test="chemin-developpeurs">
                  <h3 class="piste__titre">{{ t('demandes.chemins.surMesure.titre') }}</h3>
                  <p>{{ t('demandes.chemins.surMesure.texte') }}</p>
                  <div v-if="recommandes.length" class="piste__equipe">
                    <span class="pile" aria-hidden="true">
                      <JAvatar v-for="r in recommandes.slice(0, 4)" :key="r.utilisateurId" :nom="r.nom" :url="r.avatarUrl" :taille="32" />
                    </span>
                    <span class="texte-discret">{{ t('demandes.chemins.surMesure.devs', { n: recommandes.length }, recommandes.length) }}<template v-if="dureeSemaines"> · {{ t('demandes.chemins.surMesure.duree', { n: dureeSemaines }, dureeSemaines) }}</template></span>
                  </div>
                  <div class="piste__boutons">
                    <!-- Le besoin part en mission : les développeurs de Jamii se proposent, la personne choisit. -->
                    <RouterLink :to="{ name: 'nouvelle-mission', query: { demande: demande.id } }" class="choix__action" data-test="publier-mission-conseil">{{ t('demandes.plan.publierMission') }}</RouterLink>
                    <button v-if="recommandes.length" type="button" class="lien-discret" @click="voirDeveloppeurs">{{ t('demandes.chemins.surMesure.voirDevs') }}</button>
                    <button v-if="plan" type="button" class="lien-discret" data-test="voir-details" @click="voirDetails">{{ t('demandes.chemins.surMesure.voirProjet') }}</button>
                  </div>
                </article>

                <!-- Systalink : chaque service dit ce qu'il est. L'hébergement est annoncé comme hébergement, rien de plus. -->
                <div v-if="services.length" class="services-ayo" data-test="services-systalink">
                  <article v-for="sv in services" :key="sv.id" class="piste piste--systalink" data-test="service">
                    <div class="piste__entete">
                      <h3 class="piste__titre">{{ sv.nom }}</h3>
                      <span class="genre" :class="`genre--${sv.genre}`" data-test="genre-service">{{ t(`demandes.reponse.genre.${sv.genre}`) }}</span>
                    </div>
                    <p>{{ sv.pourCeBesoin }}</p>
                    <p v-if="sv.genre === 'hebergement'" class="piste__limite" data-test="hebergement-seul">
                      {{ t(orientation.developpeursNecessaires ? 'demandes.reponse.hebergementSeulDevs' : 'demandes.reponse.hebergementSeul') }}
                    </p>
                    <p class="piste__pied">
                      <span v-if="sv.prixIndicatif" class="choix__prix">{{ sv.prixIndicatif }}</span>
                      <a :href="sv.lien" target="_blank" rel="noopener noreferrer" class="lien-discret" data-test="voir-service" @click="signalerClicService(sv.momentId)">
                        {{ t('demandes.orientation.voirService') }}<span class="visuellement-cache"> : {{ sv.nom }}</span> ↗</a>
                    </p>
                  </article>
                  <p class="texte-discret services-ayo__note" data-test="libre">{{ t('demandes.chemins.pret.partenaire') }}</p>
                </div>

                <article v-if="!services.length && !orientation.developpeursNecessaires" class="piste">
                  <h3 class="piste__titre">{{ t('demandes.chemins.aucun.titre') }}</h3>
                  <p>{{ t('demandes.chemins.aucun.texte') }}</p>
                  <RouterLink to="/communautes" class="choix__action">{{ t('demandes.chemins.aucun.action') }}</RouterLink>
                </article>
              </section>

              <section v-if="etapesAyo.length" class="reponse__partie" aria-labelledby="maintenant" data-test="etapes-ayo">
                <h2 id="maintenant" class="reponse__titre">{{ t('demandes.ayo.maintenant') }}</h2>
                <ol class="reponse__etapes">
                  <li v-for="e in etapesAyo" :key="e">{{ e }}</li>
                </ol>
              </section>
            </template>
          </div>
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
              <li v-for="r in recommandesTries" :key="r.utilisateurId" class="carte recommande" :class="{ 'recommande--compare': aComparer.includes(r.utilisateurId) }" data-test="recommande">
                <div class="recommande__tete">
                  <JAvatar :nom="r.nom" :url="r.avatarUrl" :taille="44" />
                  <div>
                    <strong>{{ r.nom }}</strong>
                    <span v-if="r.ville" class="texte-discret">{{ r.ville }}</span>
                  </div>
                </div>
                <div class="correspondance" data-test="correspondance">
                  <span class="correspondance__chiffre">{{ r.score }} %</span>
                  <span class="correspondance__barre" aria-hidden="true"><i :style="{ width: `${r.score}%` }" /></span>
                  <span class="correspondance__libelle">{{ t('demandes.match.libelle') }} <BadgeIa /></span>
                </div>
                <ul v-if="r.competencesCouvertes.length" class="competences-couvertes" :aria-label="t('demandes.match.competences')">
                  <li v-for="c in r.competencesCouvertes" :key="c">{{ c }}</li>
                </ul>
                <ul class="raisons" :aria-label="t('demandes.orientation.pourquoi')">
                  <li v-for="raison in r.raisons" :key="raison">✓ {{ raison }}</li>
                </ul>
                <div class="recommande__actions">
                  <RouterLink :to="`/messages/${r.utilisateurId}`" class="recommande__ecrire">{{ t('demandes.orientation.ecrire', { nom: r.nom.split(' ')[0] }) }}</RouterLink>
                  <label class="comparer">
                    <input type="checkbox" :checked="aComparer.includes(r.utilisateurId)" :disabled="!aComparer.includes(r.utilisateurId) && aComparer.length >= 3" data-test="comparer" @change="basculerComparaison(r.utilisateurId)" />
                    {{ t('demandes.match.comparer') }}
                  </label>
                </div>
              </li>
            </ul>
            <!-- Comparer côte à côte, comme sur Malt : 2 ou 3 profils, les mêmes lignes pour chacun. -->
            <div v-if="compares.length >= 2" class="comparaison" data-test="comparaison">
              <table>
                <caption>{{ t('demandes.match.comparaison') }}</caption>
                <thead>
                  <tr><th scope="col"><span class="visuellement-cache">{{ t('demandes.match.critere') }}</span></th><th v-for="r in compares" :key="r.utilisateurId" scope="col"><JAvatar :nom="r.nom" :url="r.avatarUrl" :taille="28" /> {{ r.nom }}</th></tr>
                </thead>
                <tbody>
                  <tr><th scope="row">{{ t('demandes.match.libelle') }}</th><td v-for="r in compares" :key="r.utilisateurId"><strong>{{ r.score }} %</strong></td></tr>
                  <tr><th scope="row">{{ t('demandes.match.ville') }}</th><td v-for="r in compares" :key="r.utilisateurId">{{ r.ville ?? '—' }}</td></tr>
                  <tr><th scope="row">{{ t('demandes.match.competences') }}</th><td v-for="r in compares" :key="r.utilisateurId">{{ r.competencesCouvertes.join(', ') || '—' }}</td></tr>
                  <tr><th scope="row">{{ t('demandes.orientation.pourquoi') }}</th><td v-for="r in compares" :key="r.utilisateurId"><ul><li v-for="raison in r.raisons" :key="raison">{{ raison }}</li></ul></td></tr>
                  <tr><th scope="row"><span class="visuellement-cache">{{ t('demandes.match.contacter') }}</span></th><td v-for="r in compares" :key="r.utilisateurId"><RouterLink :to="`/messages/${r.utilisateurId}`">{{ t('demandes.orientation.ecrire', { nom: r.nom.split(' ')[0] }) }}</RouterLink></td></tr>
                </tbody>
              </table>
            </div>
            <p v-if="!recommandes.length" class="carte vide" data-test="aucun-recommande">{{ t('demandes.orientation.aucun') }}</p>
          </div>

          <div v-if="plan" v-show="onglet === 'projet' || !orientation" id="details-projet" role="tabpanel" aria-labelledby="onglet-projet" class="panneau details brief" data-test="brief">
            <!-- Le brief, à la façon de Malt : un document clair, préparé par l'IA, que la personne relit avant de lancer. -->
            <header class="brief__tete">
              <AyoAvatar :taille="32" />
              <div>
                <h2 class="brief__titre">{{ t('demandes.brief.titre') }}</h2>
                <p class="texte-discret">{{ t('demandes.brief.aide') }}</p>
              </div>
              <BadgeIa long />
            </header>
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

      </template>

      <!-- La zone de saisie, toujours en bas : répondre à Ayo, ou réagir à son conseil. -->
      <form v-if="etat === 'pret'" class="saisie-ia" data-test="composer" @submit.prevent="envoyerMessage()">
        <label for="message-ayo" class="visuellement-cache">{{ t('demandes.nouvelle.repondreAyo') }}</label>
        <textarea
          id="message-ayo"
          ref="zoneMessage"
          v-model="message"
          rows="1"
          :maxlength="CONVERSATION.reponseMax"
          :placeholder="t(questionOuverte ? 'demandes.discussion.repondre' : 'demandes.discussion.reagir')"
          data-test="message-ayo"
          @input="zoneMessage && ajusterZone(zoneMessage)"
          @keydown="toucheMessage"
        />
        <div class="saisie-ia__bas">
          <span class="saisie-ia__aide">
            <span v-if="envoiPrecision === 'limite'" role="alert" class="erreur">{{ t('demandes.discussion.limite') }}</span>
            <span v-else-if="envoiPrecision === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</span>
            <span v-else-if="envoiPrecision === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</span>
            <span v-else-if="demande.conseilsRestants !== undefined" data-test="conseils-restants">{{ t('demandes.ayo.restants', { n: demande.conseilsRestants }, demande.conseilsRestants) }}</span>
          </span>
          <button type="submit" class="saisie-ia__envoyer" :disabled="!messageValide" :aria-busy="envoiPrecision === 'envoi' || undefined" :aria-label="t('demandes.nouvelle.repondreAyo')" data-test="envoyer-message">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M10 15.5v-11M5.5 9 10 4.5 14.5 9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
      </form>

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
.assistant { display: grid; grid-template-columns: auto 1fr; gap: var(--space-3); align-items: end; }
.assistant__nom { margin: 0 0 var(--space-1); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.assistant__nom strong { color: var(--ink); }
.question-ayo { display: grid; gap: var(--space-4); border-inline-start: 3px solid var(--action); }
.question-ayo__texte { margin: 0; font-size: 1.1875rem; }
.entete { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
.entete .retour { margin: 0; }
.supprimer { color: var(--ink-muted); }
.supprimer:hover { color: var(--danger); }
.fil { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.bulle-moi { display: grid; justify-items: end; }
.bulle-moi__texte { margin: 0; max-width: min(36rem, 88%); padding: var(--space-3) var(--space-4); border-radius: 18px 18px 4px 18px; background: var(--surface-raised); border: 1px solid var(--line); white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; }
.bulle-ayo { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; color: var(--ink-muted); }
.saisie-ia {
  position: sticky; bottom: var(--space-4); z-index: 2;
  display: grid; gap: var(--space-1); padding: var(--space-3) var(--space-3) var(--space-2) var(--space-4);
  border: 1px solid var(--line-strong); border-radius: 22px; background: var(--surface-raised);
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.08), 0 10px 30px rgb(2 7 10 / 0.16);
}
.saisie-ia:focus-within { border-color: color-mix(in srgb, var(--ink) 35%, var(--line-strong)); }
.saisie-ia textarea { width: 100%; min-height: 0; max-height: 40vh; padding: var(--space-1) 0; border: 0; border-radius: 0; background: transparent; color: var(--ink); font: inherit; font-size: 1.0625rem; line-height: 1.5; resize: none; outline: none; box-shadow: none; }
.saisie-ia textarea::placeholder { color: color-mix(in srgb, var(--ink) 40%, transparent); }
.saisie-ia__bas { display: flex; align-items: center; gap: var(--space-3); }
.saisie-ia__aide { flex: 1; min-width: 0; color: var(--ink-muted); font-size: 0.8125rem; }
.saisie-ia__envoyer { display: grid; place-items: center; flex: none; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: var(--ink); color: var(--surface); cursor: pointer; }
.saisie-ia__envoyer:disabled { opacity: 0.25; cursor: not-allowed; }
.saisie-ia__envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
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

.attente { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); padding: var(--space-6); }
.attente strong { display: block; }
.attente p { margin: var(--space-1) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.attente--echec { justify-content: space-between; border-inline-start: 4px solid var(--danger); }
.attente--echec > div:nth-child(2) { flex: 1 1 16rem; }
.attente__icone { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: color-mix(in srgb, var(--danger) 14%, transparent); color: var(--danger); font-weight: 800; }
.attente__actions { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.bouton-lien--discret { background: transparent; color: var(--action-ink); border: 1px solid var(--line-strong); }
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
.besoin__compte { margin-inline-start: auto; padding: 0 8px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; }

.reponse .ayo-message__bulle { gap: var(--space-3); }
.reponse__tete { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.reponse__tete .proposition { margin-inline-start: auto; }
.reponse__texte { margin: 0; font-size: 1.125rem; line-height: 1.6; white-space: pre-wrap; }
.reponse__repli { margin: 0; padding: var(--space-2) var(--space-3); border-radius: var(--radius-sm); background: var(--surface-raised); font-size: var(--type-body-sm-size); font-weight: 600; }
.reponse .question-ayo { display: grid; gap: var(--space-3); margin-top: var(--space-2); padding-top: var(--space-4); border-top: 1px dashed var(--line-strong); border-inline-start: 0; }
.reponse .question-ayo__texte { margin: 0; font-size: 1.0625rem; font-weight: 700; }

.choix__prix { font-weight: 700; }
.pile { display: inline-flex; }
.pile > :not(:first-child) { margin-inline-start: -10px; }
.pile > * { box-shadow: 0 0 0 2px var(--surface); border-radius: 50%; }
.choix__action { justify-self: start; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-5, 1.25rem); border-radius: var(--radius-pill); border: 0; background: var(--action); color: var(--on-action); font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
.choix__action:focus-visible, .lien-discret:focus-visible, .onglet:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.lien-discret { justify-self: start; border: 0; padding: 0; background: none; color: var(--action-ink); font: inherit; font-weight: 600; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }

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

@media (prefers-reduced-motion: reduce) { .besoin summary::before { transition: none; } }

/* ---------------------------------------------------------------- la conversation avec Ayo, l'assistant IA */
.ayo-message { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-3); align-items: start; }
.ayo-message__bulle {
  display: grid; gap: var(--space-2); min-width: 0; padding: var(--space-4) var(--space-6) var(--space-6);
  border-radius: 4px var(--radius-lg) var(--radius-lg) var(--radius-lg); border: 1px solid color-mix(in srgb, var(--action) 22%, var(--line));
  background: linear-gradient(180deg, color-mix(in srgb, var(--action-soft) 45%, var(--surface)), var(--surface) 70%);
}
.ayo-message__qui { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.ecrit { margin: 0; display: flex; align-items: center; gap: var(--space-3); }
/* Les trois points d'une IA qui écrit. */
.points { display: inline-flex; gap: 4px; padding: 8px 10px; border-radius: var(--radius-pill); background: var(--surface-raised); }
.points i { width: 6px; height: 6px; border-radius: 50%; background: var(--action); opacity: 0.35; animation: point 1.2s infinite ease-in-out; }
.points i:nth-child(2) { animation-delay: 0.15s; }
.points i:nth-child(3) { animation-delay: 0.3s; }
@keyframes point { 30% { opacity: 1; transform: translateY(-3px); } 60% { opacity: 0.35; transform: none; } }
@media (prefers-reduced-motion: reduce) { .points i { animation: none; opacity: 0.7; } }

/* ---------------------------------------------------------------- le brief, préparé par l'IA */
.brief { padding: var(--space-6); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); }
.brief__tete { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; padding-bottom: var(--space-4); border-bottom: 1px solid var(--line); }
.brief__titre { margin: 0; font-size: 1.25rem; }
.brief__tete p { margin: 2px 0 0; font-size: var(--type-body-sm-size); }
.brief__tete .badge-ia { justify-self: start; }

/* ---------------------------------------------------------------- les profils : correspondance, comparaison */
.correspondance { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 2px var(--space-3); }
.correspondance__chiffre { grid-row: span 2; font-family: var(--font-display); font-size: 1.5rem; font-weight: 800; color: var(--action-ink); }
.correspondance__barre { height: 6px; border-radius: var(--radius-pill); background: var(--surface-raised); overflow: hidden; }
.correspondance__barre i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--action), var(--proof)); }
.correspondance__libelle { display: flex; align-items: center; gap: var(--space-1); color: var(--ink-muted); font-size: 0.75rem; font-weight: 600; }
.competences-couvertes { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 4px; }
.competences-couvertes li { padding: 1px 8px; border-radius: var(--radius-pill); background: var(--surface-raised); border: 1px solid var(--line); font-size: 0.75rem; font-weight: 600; }
.recommande--compare { border-color: var(--action); box-shadow: 0 0 0 1px var(--action); }
.recommande__actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2); }
.comparer { display: inline-flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: var(--type-body-sm-size); cursor: pointer; }
.comparer input { width: 16px; height: 16px; accent-color: var(--action); }
.comparaison { overflow-x: auto; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); }
.comparaison table { width: 100%; border-collapse: collapse; font-size: var(--type-body-sm-size); }
.comparaison caption { padding: var(--space-3) var(--space-4); text-align: left; font-weight: 700; }
.comparaison th, .comparaison td { padding: var(--space-2) var(--space-4); border-top: 1px solid var(--line); text-align: left; vertical-align: top; }
.comparaison thead th { white-space: nowrap; }
.comparaison thead th :deep(.j-avatar) { vertical-align: middle; margin-inline-end: 6px; }
.comparaison tbody th { color: var(--ink-muted); font-weight: 600; white-space: nowrap; }
.comparaison ul { margin: 0; padding-inline-start: 1em; }
@media (max-width: 640px) { .ayo-message { grid-template-columns: 1fr; } .ayo-message__avatar { display: none; } .brief__tete { grid-template-columns: minmax(0, 1fr); } }

/* La réponse d'Ayo, rangée en sections comme une réponse écrite. */
.reponse__titre { margin: 0; font-size: 1.0625rem; font-weight: 700; }
.reponse__partie { display: grid; gap: var(--space-3); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.reponse__etapes { margin: 0; padding-inline-start: 1.4em; display: grid; gap: var(--space-2); line-height: 1.5; }
.reponse__etapes ::marker { font-weight: 700; color: var(--action-ink); }
.piste { display: grid; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); }
.piste p { margin: 0; line-height: 1.5; }
.piste__titre { margin: 0; font-size: 1rem; }
.piste__entete { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-3); }
.piste__equipe { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.piste__boutons { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); margin-top: var(--space-1); }
.piste__limite { padding: var(--space-2) var(--space-3); border-inline-start: 3px solid var(--line-strong); background: var(--surface); border-radius: var(--radius-sm); font-size: var(--type-body-sm-size); font-weight: 600; }
.piste__pied { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); }
.genre { padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; white-space: nowrap; }
.genre--hebergement { background: var(--action-soft); color: var(--action-ink); }
.genre--outil { background: var(--proof-soft); color: var(--proof-ink); }
.services-ayo { display: grid; gap: var(--space-3); }
.services-ayo__note { margin: 0; font-size: var(--type-body-sm-size); }
</style>
