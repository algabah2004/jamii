<script setup lang="ts">
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
import PublierDemande from './PublierDemande.vue'

/*
 * L'orientation d'un besoin (étape 1 de la démo) : ce que Systalink offre déjà, les développeurs qui peuvent
 * faire le reste (un score calculé par l'API, avec ses raisons) et le plan. « L'IA propose, l'humain décide » :
 * tout est une proposition, et seul un clic humain en fait un projet ou un cercle.
 * Les textes générés sont affichés comme du texte brut, jamais interprétés.
 */
const { t } = useI18n()
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
const envoiPrecision = ref<'repos' | 'envoi' | 'erreur' | 'limite'>('repos')
async function envoyerPrecision() {
  if (!demande.value || precision.value.trim().length < 3) return
  envoiPrecision.value = 'envoi'
  try {
    const r = await preciserDemande(demande.value.id, precision.value)
    if (r === 'limite') return void (envoiPrecision.value = 'limite')
    if (!r) return void (envoiPrecision.value = 'erreur')
    demande.value = r
    precision.value = ''
    envoiPrecision.value = 'repos'
    detailsOuverts.value = false
    debut = 0
    tropLong.value = false
    surveiller()
    chargerHistorique()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch {
    envoiPrecision.value = 'erreur'
  }
}

/** Le plan technique est écrit pour les développeurs : replié par défaut, ouvert d'un clic. */
const detailsOuverts = ref(false)
const dureeSemaines = computed(() => Math.max(0, ...(plan.value?.jalons ?? []).map((j) => j.semaine)))
const indispensables = computed(
  () => (plan.value?.cahierDesCharges.fonctionnalites ?? []).filter((f) => f.priorite === 'indispensable').length,
)
function voirDeveloppeurs() {
  document.getElementById('developpeurs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function voirDetails() {
  detailsOuverts.value = true
  requestAnimationFrame(() => document.getElementById('details-projet')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

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
    <p class="retour"><RouterLink to="/demandes">← {{ t('demandes.mesDemandes.titre') }}</RouterLink></p>

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
      <header class="tete motif motif--croix">
        <h1>{{ demande.titre }}</h1>
        <!-- Texte brut, affiché échappé : les mots de la personne, jamais interprétés. -->
        <div class="fil" data-test="fil">
          <p class="bulle bulle--moi">{{ demande.description }}</p>
          <p v-for="p in demande.precisions ?? []" :key="p.creeLe" class="bulle bulle--moi" data-test="precision">{{ p.texte }}</p>
        </div>
      </header>

      <div v-if="enPreparation" class="carte attente" role="status" data-test="plan-attente">
        <span class="points" aria-hidden="true"><i /><i /><i /></span>
        <div>
          <strong>{{ t('demandes.plan.prepare') }}</strong>
          <ol class="etapes" data-test="etapes">
            <li v-for="(e, i) in ETAPES" :key="e" :class="{ 'etapes--faite': i < etapeEnCours, 'etapes--en-cours': i === etapeEnCours }">
              <span aria-hidden="true">{{ i < etapeEnCours ? '✓' : i === etapeEnCours ? '•' : '·' }}</span> {{ t(`demandes.plan.etapes.${e}`) }}
            </li>
          </ol>
        </div>
      </div>
      <div v-else-if="!pret" role="alert" class="carte attente attente--echec" data-test="plan-echec">
        <div>
          <strong>{{ t('demandes.plan.echec') }}</strong>
          <p>{{ t('demandes.plan.echecAide') }}</p>
        </div>
        <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
      </div>

      <template v-if="pret">
        <p class="proposition" data-test="plan-propose">✨ {{ t('demandes.plan.propose') }}</p>
        <p v-if="repli" class="carte repli" role="status" data-test="plan-repli">{{ t(orientation ? 'demandes.orientation.repli' : 'demandes.plan.repli') }}</p>

        <!-- Ce que l'assistant dit d'abord : il a compris, il explique simplement ce qui peut aider. -->
        <section v-if="orientation" class="assistant" aria-labelledby="compris" data-test="compris">
          <span class="assistant__avatar" aria-hidden="true">J</span>
          <div class="bulle bulle--jamii">
            <h2 id="compris" class="visuellement-cache">{{ t('demandes.orientation.compris') }}</h2>
            <p class="resume">{{ orientation.message || orientation.resume }}</p>
          </div>
        </section>

        <!-- Les chemins possibles, côte à côte : la personne choisit, Jamii ne vend rien. Aucune donnée ne sort de Jamii. -->
        <section v-if="orientation" class="chemins" aria-labelledby="options" data-test="chemins">
          <div class="chemins__tete">
            <h2 id="options">{{ t('demandes.chemins.titre') }}</h2>
            <p class="texte-discret">{{ t('demandes.chemins.aide') }}</p>
          </div>
          <div class="chemins__liste">
            <article v-if="orientation.services.length" class="carte chemin" data-test="services-systalink">
              <span class="chemin__badge">{{ orientation.developpeursNecessaires ? t('demandes.chemins.pret.badgeAvecDev') : t('demandes.chemins.pret.badge') }}</span>
              <h3>{{ orientation.developpeursNecessaires ? t('demandes.chemins.pret.titreAvecDev') : t('demandes.chemins.pret.titre') }}</h3>
              <ul class="chemin__services">
                <li v-for="s in orientation.services" :key="s.id" data-test="service">
                  <strong>{{ s.nom }}</strong>
                  <p>{{ s.pourCeBesoin }}</p>
                  <p v-if="s.prixIndicatif" class="chemin__prix">{{ s.prixIndicatif }}</p>
                  <a :href="s.lien" target="_blank" rel="noopener noreferrer" class="chemin__action" data-test="voir-service" @click="signalerClicService(s.momentId)">
                    {{ t('demandes.orientation.voirService') }}<span class="visuellement-cache"> : {{ s.nom }}</span> ↗</a>
                </li>
              </ul>
              <p class="chemin__note">{{ t('demandes.chemins.pret.partenaire') }}</p>
            </article>

            <article v-if="orientation.developpeursNecessaires" class="carte chemin" data-test="chemin-developpeurs">
              <span class="chemin__badge chemin__badge--dev">{{ t('demandes.chemins.surMesure.badge') }}</span>
              <h3>{{ t('demandes.chemins.surMesure.titre') }}</h3>
              <p>{{ t('demandes.chemins.surMesure.texte') }}</p>
              <ul v-if="plan" class="chemin__chiffres">
                <li v-if="dureeSemaines">{{ t('demandes.chemins.surMesure.duree', { n: dureeSemaines }, dureeSemaines) }}</li>
                <li v-if="indispensables">{{ t('demandes.chemins.surMesure.fonctions', { n: indispensables }, indispensables) }}</li>
                <li v-if="recommandes.length">{{ t('demandes.chemins.surMesure.devs', { n: recommandes.length }, recommandes.length) }}</li>
              </ul>
              <div class="chemin__boutons">
                <button type="button" class="chemin__action" @click="voirDeveloppeurs">{{ t('demandes.chemins.surMesure.voirDevs') }}</button>
                <button v-if="plan" type="button" class="bouton-lien" data-test="voir-details" @click="voirDetails">{{ t('demandes.chemins.surMesure.voirProjet') }}</button>
              </div>
            </article>

            <article v-if="!orientation.services.length && !orientation.developpeursNecessaires" class="carte chemin">
              <h3>{{ t('demandes.chemins.aucun.titre') }}</h3>
              <p>{{ t('demandes.chemins.aucun.texte') }}</p>
              <RouterLink to="/communautes" class="chemin__action">{{ t('demandes.chemins.aucun.action') }}</RouterLink>
            </article>
          </div>
          <p v-if="orientation.services.length && orientation.developpeursNecessaires" class="chemins__conseil">💡 {{ t('demandes.chemins.lesDeux') }}</p>
        </section>

        <form v-if="orientation" class="carte discuter" data-test="preciser" @submit.prevent="envoyerPrecision">
          <label for="precision" class="discuter__titre">
            <strong>{{ t('demandes.discuter.titre') }}</strong>
            <span class="texte-discret">{{ t('demandes.discuter.aide') }}</span>
          </label>
          <div class="discuter__ligne">
            <textarea id="precision" v-model="precision" maxlength="500" rows="2" :placeholder="t('demandes.discuter.exemple')" @keydown.enter.exact.prevent="envoyerPrecision" />
            <JBouton type="submit" :occupe="envoiPrecision === 'envoi'" :disabled="precision.trim().length < 3" data-test="envoyer-precision">{{ t('demandes.discuter.envoyer') }}</JBouton>
          </div>
          <p v-if="envoiPrecision === 'limite'" role="alert" class="erreur">{{ t('demandes.discuter.limite') }}</p>
          <p v-else-if="envoiPrecision === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
        </form>

        <section v-if="orientation?.developpeursNecessaires" class="bloc-nu" aria-labelledby="developpeurs" data-test="developpeurs">
          <div class="bloc-nu__tete">
            <div>
              <h2 id="developpeurs">{{ t('demandes.orientation.developpeurs') }}</h2>
              <p class="texte-discret">{{ t('demandes.orientation.developpeursAide') }}</p>
            </div>
            <button v-if="recommandes.length" type="button" class="bouton-lien" data-test="former-cercle" @click="formerCercle">{{ t('demandes.orientation.formerCercle') }}</button>
          </div>
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
        </section>
      </template>

      <div v-if="plan && orientation" class="details-bascule">
        <button type="button" class="bouton-lien" :aria-expanded="detailsOuverts" aria-controls="details-projet" @click="detailsOuverts = !detailsOuverts">
          {{ detailsOuverts ? t('demandes.chemins.masquerProjet') : t('demandes.chemins.surMesure.voirProjet') }}</button>
      </div>
      <div v-if="plan" v-show="detailsOuverts || !orientation" id="details-projet" class="details">
        <h2 v-if="orientation" class="plan-titre">{{ t('demandes.orientation.plan') }}</h2>

        <section class="carte bloc" aria-labelledby="resume">
          <h2 id="resume">{{ t('demandes.plan.resume') }}</h2>
          <p class="resume">{{ plan.cahierDesCharges.resume }}</p>
          <ul class="objectifs">
            <li v-for="o in plan.cahierDesCharges.objectifs" :key="o">{{ o }}</li>
          </ul>
        </section>

        <section class="bloc-nu" aria-labelledby="fonctionnalites">
          <h2 id="fonctionnalites">{{ t('demandes.plan.fonctionnalites') }}</h2>
          <div class="priorites">
            <div v-for="g in fonctionnalites" :key="g.priorite" class="carte priorite" :class="`priorite--${g.priorite}`" data-test="priorite">
              <h3>{{ t(`demandes.priorites.${g.priorite}`) }}</h3>
              <ul>
                <li v-for="f in g.liste" :key="f.titre"><strong>{{ f.titre }}</strong><span>{{ f.description }}</span></li>
              </ul>
            </div>
          </div>
        </section>

        <section class="bloc-nu" aria-labelledby="jalons">
          <h2 id="jalons">{{ t('demandes.plan.jalons') }}</h2>
          <ol class="jalons" data-test="jalons">
            <li v-for="j in jalons" :key="j.titre" class="carte jalon">
              <span class="jalon__semaine">{{ t('demandes.plan.semaine', { n: j.semaine }) }}</span>
              <h3>{{ j.titre }}</h3>
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
            <h2 id="roles">{{ t('demandes.plan.roles') }}</h2>
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
            <h2 id="services">{{ t('demandes.plan.services') }}</h2>
            <p class="texte-discret">{{ t('demandes.plan.servicesAide') }}</p>
            <ul class="services">
              <li v-for="s in plan.servicesSuggeres" :key="s.service">
                <strong>{{ t(`systalink.services.${s.service === 'domaine' ? 'domaines' : s.service}`) }}</strong>
                <span class="texte-discret">{{ s.raison }}</span>
              </li>
            </ul>
          </section>
        </div>

        <section v-if="plan.cahierDesCharges.horsPerimetre.length" class="bloc-nu" aria-labelledby="hors">
          <h2 id="hors">{{ t('demandes.plan.horsPerimetre') }}</h2>
          <ul class="hors">
            <li v-for="h in plan.cahierDesCharges.horsPerimetre" :key="h">{{ h }}</li>
          </ul>
        </section>
      </div>

      <section v-if="pret" class="carte offres" aria-labelledby="offres-titre" data-test="offres-resultat">
        <label class="offres__case">
          <input
            type="checkbox"
            :checked="demande.offresSystalink === true"
            :disabled="offres === 'envoi'"
            @change="changerOffres(($event.target as HTMLInputElement).checked)"
          />
          <span>
            <strong id="offres-titre">{{ t('demandes.offres.libelle') }}</strong>
            <span class="texte-discret">{{ t('demandes.offres.aide') }}</span>
            <span class="texte-discret">{{ t('demandes.offres.retrait') }}</span>
          </span>
        </label>
        <p v-if="offres === 'erreur'" role="alert" class="erreur">{{ t('demandes.offres.erreur') }}</p>
      </section>

      <PublierDemande v-if="pret" :demande="demande" :resume-propose="orientation?.resume" @publiee="demande = $event" />

      <template v-if="plan">
        <!-- La décision humaine, toujours visible en bas de l'écran. -->
        <div class="verre decision">
          <p v-if="lancement.etat === 'fait'" role="status" class="decision__fait" data-test="projet-cree">✓ {{ t('demandes.plan.lance') }}</p>
          <template v-else>
            <p class="decision__texte">{{ t('demandes.plan.decision') }}</p>
            <p v-if="lancement.etat === 'erreur'" role="alert" class="erreur">{{ messageErreur }}</p>
            <JBouton :occupe="lancement.etat === 'envoi'" data-test="lancer" @click="lancer">{{ t('demandes.plan.lancer') }}</JBouton>
          </template>
        </div>
      </template>
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
.assistant__avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: var(--action); color: var(--on-action); font-family: var(--font-display); font-weight: 700; }
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
.attente--echec { justify-content: space-between; }
.points { display: inline-flex; gap: 5px; }
.points i { width: 8px; height: 8px; border-radius: 50%; background: var(--action); animation: respire 1.2s var(--ease-out) infinite; }
.points i:nth-child(2) { animation-delay: 0.15s; }
.points i:nth-child(3) { animation-delay: 0.3s; }
@keyframes respire { 50% { opacity: 0.25; transform: translateY(-3px); } }
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
</style>
