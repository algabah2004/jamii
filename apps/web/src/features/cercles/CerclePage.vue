<script setup lang="ts">
import PreparationIdee from './PreparationIdee.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import type { PieceJointe } from '../../components/texte/fichiers'
import type { DepotIdee } from './cercles'
import LienRetour from '../../components/LienRetour.vue'
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JChamp, JDeplier, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import ProfilsRecommandes from '../reseau/ProfilsRecommandes.vue'
import PartagerCercle from './PartagerCercle.vue'
import CodeDuTour from './CodeDuTour.vue'
import TachesDuTour from './TachesDuTour.vue'
import SalonDuCercle from './SalonDuCercle.vue'
import QgDuCercle from './QgDuCercle.vue'
import OutilTaches from './OutilTaches.vue'
import OutilPoints from './OutilPoints.vue'
import OutilDecisions from './OutilDecisions.vue'
import OutilCalendrier from './OutilCalendrier.vue'
import { outilDepuis, OUTILS } from './qg'
import IconeCercle from './IconeCercle.vue'
import { avancement, avantTirage, chefDeProjet, deposerIdee, LIMITES, lireAvancement, lireCercle, livrerTour, ordreDePassage, positionsAnneau, pourcentage, rejoindreCercle, tirerAuSort, titreDuTour, tourEnCours, type Avancement, type Cercle, type Resultat, type Tache } from './cercles'

/*
 * La page d'un cercle : qui en fait partie, l'ordre de passage, le tour en cours et où il en est.
 * L'anneau montre les membres et leur ordre de passage, avec le bénéficiaire du tour mis en avant.
 */
const { t } = useI18n()
const { moi } = useSession()
const route = useRoute()
const id = computed(() => String(route.params.id))

const cercle = ref<Cercle | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    cercle.value = await lireCercle(id.value)
    etat.value = cercle.value ? 'pret' : 'introuvable'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(id, charger, { immediate: true })

const ordre = computed(() => (cercle.value ? ordreDePassage(cercle.value) : []))
const tour = computed(() => (cercle.value ? tourEnCours(cercle.value) : null))
const beneficiaire = computed(() => tour.value?.beneficiaire ?? null)
const prenom = (nom: string) => nom.split(' ')[0] ?? nom
const titreTour = computed(() => (cercle.value && tour.value ? titreDuTour(cercle.value, tour.value) : null))

// L'avancement vient du projet du tour (date de création et tâches livrées). Absent si on n'y a pas accès.
const suivi = ref<Avancement | null>(null)
watch(
  () => tour.value?.projetId,
  async (projetId) => {
    suivi.value = null
    if (projetId) suivi.value = await lireAvancement(projetId)
  },
  { immediate: true },
)
// Quand on coche ou ajoute une tâche, la barre suit sans recharger : même jour du tour, nouveau compte.
function majTaches(taches: Tache[]) {
  if (!suivi.value) return
  const { faites, total } = avancement(new Date().toISOString(), taches)
  suivi.value = { ...suivi.value, faites, total }
}
// Avant le tirage, l'anneau montre aussi les places libres : on voit combien il manque de monde.
const places = computed(() => positionsAnneau(cercle.value ? Math.max(cercle.value.taille, ordre.value.length) : 0))
const placesLibres = computed(() => places.value.slice(ordre.value.length))
const tourDe = (personneId: string) => cercle.value?.tours.find((x) => x.beneficiaire.id === personneId) ?? null

const membre = computed(() => !!cercle.value?.moi.estMembre)
// Le QG du cercle, à la façon d'un projet Basecamp : des outils (salon, tâches, point du soir, décisions, calendrier,
// le cercle), chacun à son adresse (`?outil=`) pour qu'un lien mène droit à l'un d'eux. Les autres voient le cercle.
const outil = computed(() => outilDepuis(route.query, membre.value, !!cercle.value?.tirage))
const createur = computed(() => !!cercle.value?.moi.estCreateur)
const preparation = computed(() => (cercle.value ? avantTirage(cercle.value) : null))
const monIdee = computed(() => cercle.value?.membres.find((m) => m.personne.id === moi.value?.id)?.idee ?? null)

// Une seule action à la fois ; l'erreur dit ce qui bloque, avec le code renvoyé par l'API.
const action = ref<'repos' | 'rejoindre' | 'idee' | 'tirage' | 'livrer'>('repos')
const erreur = ref<string | null>(null)
async function agir(quoi: 'rejoindre' | 'idee' | 'tirage' | 'livrer', appel: () => Promise<Resultat>) {
  action.value = quoi
  erreur.value = null
  try {
    const r = await appel()
    if (r.ok) {
      cercle.value = r.cercle
      if (quoi === 'idee') ideeOuverte.value = false
      if (quoi === 'livrer') confirmerLivraison.value = false
    } else erreur.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    action.value = 'repos'
  }
}

// Livrer : réservé au bénéficiaire du tour et au créateur (contrat), avec une confirmation qui dit ce qui va se passer.
const peutLivrer = computed(() => !!tour.value && (tour.value.beneficiaire.id === moi.value?.id || createur.value))
const confirmerLivraison = ref(false)
const suivant = computed(() => {
  const n = tour.value?.numero
  const t2 = n ? cercle.value?.tours.find((x) => x.numero === n + 1) : null
  return t2?.beneficiaire ?? null
})
const dernierLivre = ref<string | null>(null)
async function livrer() {
  const nom = prenom(beneficiaire.value?.nom ?? '')
  await agir('livrer', () => livrerTour(cercle.value!.id, tour.value!.numero))
  if (!erreur.value) dernierLivre.value = nom
}
const livres = computed(() => cercle.value?.tours.filter((x) => x.statut === 'livre') ?? [])

const idee = ref<DepotIdee>({ titre: '', description: '', backlog: [] })
const documentIdee = ref<PieceJointe | null>(null)
const preparationOccupee = ref(false)
const csvInvalide = ref(false)
const ideeOuverte = ref(false)
function ouvrirIdee() {
  idee.value = { titre: monIdee.value?.titre ?? '', description: monIdee.value?.description ?? '', backlog: monIdee.value?.backlog ?? [] }
  documentIdee.value = monIdee.value?.cahierCharges ?? null
  preparationOccupee.value = false
  csvInvalide.value = false
  ideeOuverte.value = true
}
const ideeValide = computed(() => idee.value.titre.trim().length >= LIMITES.ideeTitreMin && idee.value.description.trim().length >= LIMITES.ideeDescriptionMin)
</script>

<template>
  <section class="cercle-page" :class="{ 'cercle-page--salon': etat === 'pret' && outil === 'salon' }">

    <LienRetour v-if="etat !== 'pret'" to="/cercles">{{ t('cercles.tous') }}</LienRetour>
    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/cercles">{{ t('cercles.tous') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="cercle">
      <!-- Hors du cercle, un en-tête compact. Dedans, le nom passe en haut de la barre latérale : la place va aux outils. -->
      <header v-if="!membre" class="cercle-tete">
        <LienRetour to="/cercles">{{ t('cercles.tous') }}</LienRetour>
        <div class="cercle-tete__ligne">
          <h1>{{ cercle.nom }}</h1>
          <p class="cercle-tete__infos">{{ cercle.ville ? t('cercles.sousTitre', { n: cercle.membres.length, ville: cercle.ville }) : t('cercles.sousTitreSansVille', { n: cercle.membres.length }) }}</p>
        </div>
        <!-- Tant que le cercle se remplit, les profils qu'il cherche : pour que les bonnes personnes le rejoignent. -->
        <p v-if="cercle.statut === 'ouvert' && cercle.profilsRecherches?.length" class="cercle-tete__profils" data-test="profils-recherches">
          <span>{{ t('cercles.cherche') }}</span>
          <span v-for="p in cercle.profilsRecherches" :key="p" class="cercle-tete__profil">{{ t(`cercles.profils.${p}`) }}</span>
        </p>
      </header>

      <div class="espace" :class="{ 'espace--outils': membre }">
      <!-- La barre latérale du cercle : ses outils, puis l'équipe. Sur mobile, une rangée qui défile. -->
      <aside v-if="membre" class="barre-laterale">
      <header class="identite" data-test="identite-cercle">
        <RouterLink to="/cercles" class="identite__retour" data-test="tous-les-cercles"><IconeCercle nom="retour" :taille="14" />{{ t('cercles.tous') }}</RouterLink>
        <h1 class="identite__nom">{{ cercle.nom }}</h1>
        <p class="identite__infos">{{ cercle.ville ? t('cercles.sousTitre', { n: cercle.membres.length, ville: cercle.ville }) : t('cercles.sousTitreSansVille', { n: cercle.membres.length }) }}</p>
        <p v-if="cercle.statut === 'ouvert' && cercle.profilsRecherches?.length" class="cercle-tete__profils" data-test="profils-recherches">
          <span>{{ t('cercles.cherche') }}</span>
          <span v-for="p in cercle.profilsRecherches" :key="p" class="cercle-tete__profil">{{ t(`cercles.profils.${p}`) }}</span>
        </p>
      </header>
      <nav class="outils-nav" :aria-label="t('cercles.onglets.aria')">
        <RouterLink
          v-for="o in OUTILS"
          :key="o"
          :to="{ query: { outil: o } }"
          class="outil-lien"
          :class="{ actif: outil === o }"
          :aria-current="outil === o ? 'page' : undefined"
          :data-test="o === 'cercle' ? 'onglet-cercle' : o === 'salon' ? 'onglet-salon' : `onglet-${o}`"
        ><IconeCercle :nom="o" :taille="18" /><span>{{ t(`cercles.qg.outils.${o}`) }}</span></RouterLink>
      </nav>
      <section class="equipe" :aria-label="t('cercles.qg.equipe')">
        <h2 class="equipe__titre">{{ t('cercles.qg.equipe') }}</h2>
        <ul>
          <li v-for="m in cercle.membres" :key="m.personne.id">
            <LienPersonne :id="m.personne.id" class="equipe__membre">
              <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="24" />
              <span>{{ m.personne.nom }}</span>
              <span v-if="m.personne.id === chefDeProjet(cercle).id" class="equipe__tour">{{ t('cercles.chef.titre') }}</span>
            </LienPersonne>
          </li>
        </ul>
      </section>
      </aside>

      <div class="espace__contenu">

      <QgDuCercle v-if="outil === 'qg'" :key="`qg-${cercle.id}`" :cercle="cercle" :suivi="suivi" :titre-tour="titreTour" />
      <SalonDuCercle v-else-if="outil === 'salon'" :key="cercle.id" :cercle="cercle" :suivi="suivi" :titre-tour="titreTour" />
      <OutilTaches v-else-if="outil === 'taches'" :cercle="cercle" :moi-id="moi?.id ?? null" @maj="majTaches" />
      <OutilPoints v-else-if="outil === 'points'" :cercle="cercle" />
      <OutilDecisions v-else-if="outil === 'decisions'" :cercle="cercle" :moi-id="moi?.id ?? null" />
      <OutilCalendrier v-else-if="outil === 'calendrier'" :cercle="cercle" :suivi="suivi" :moi-id="moi?.id ?? null" />

      <template v-else>
      <div class="mise-en-page" :class="{ 'mise-en-page--edition': ideeOuverte }">
        <!-- L'anneau : les membres dans l'ordre de passage, le bénéficiaire du tour mis en avant. -->
        <figure v-show="!ideeOuverte" class="anneau carte" data-test="anneau">
          <div class="anneau__scene">
            <svg class="anneau__trace" viewBox="0 0 100 100" aria-hidden="true">
              <circle cx="50" cy="50" r="40" />
            </svg>
            <span v-for="(p, i) in placesLibres" :key="`libre-${i}`" class="anneau__libre" :style="{ left: `${p.x}%`, top: `${p.y}%` }" aria-hidden="true" />
            <div
              v-for="(m, i) in ordre"
              :key="m.personne.id"
              class="anneau__membre"
              :class="{ 'anneau__membre--tour': m.personne.id === tour?.beneficiaire.id, 'anneau__membre--livre': tourDe(m.personne.id)?.statut === 'livre' }"
              :style="{ left: `${places[i]!.x}%`, top: `${places[i]!.y}%`, '--i': i }"
            >
              <LienPersonne :id="m.personne.id" class="anneau__lien">
                <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="52" />
                <span class="anneau__nom">{{ prenom(m.personne.nom) }}</span>
              </LienPersonne>
            </div>
          </div>
          <figcaption class="visuellement-cache">{{ t('cercles.anneauDescription', { ordre: ordre.map((m) => m.personne.nom).join(', ') }) }}</figcaption>
        </figure>

        <div class="cote">
          <p v-if="dernierLivre" class="livre carte" role="status" data-test="livre">
            <strong>{{ t('cercles.livreTitre', { nom: dernierLivre }) }}</strong>
            {{ t('cercles.livreTexte') }}
            <RouterLink to="/vitrine">{{ t('cercles.voirVitrine') }}</RouterLink>
          </p>
          <section v-if="tour && beneficiaire" class="carte tour" aria-labelledby="tour-titre" data-test="tour">
            <p class="tour__surtitre">{{ t('cercles.tourDe', { n: tour.numero, nom: prenom(beneficiaire.nom) }) }}</p>
            <h2 id="tour-titre">{{ titreTour ?? t('cercles.projetDe', { nom: prenom(beneficiaire.nom) }) }}</h2>
            <div v-if="suivi" class="avancement" data-test="avancement">
              <div class="avancement__barre" role="progressbar" :aria-valuenow="pourcentage(suivi)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')">
                <i :style="{ width: `${pourcentage(suivi)}%` }" />
              </div>
              <p>{{ t('cercles.taches', { faites: suivi.faites, total: suivi.total }) }} · {{ t('cercles.joursRestants', { n: suivi.joursRestants }, suivi.joursRestants) }}</p>
            </div>
            <TachesDuTour v-if="membre && tour.projetId" :key="tour.projetId" :projet-id="tour.projetId" :moi-id="moi?.id ?? null" :membres="cercle.membres.map((m) => m.personne)" :beneficiaire-id="tour.beneficiaire.id" @maj="majTaches" />
            <CodeDuTour :key="`${cercle.id}-${tour.numero}`" :cercle-id="cercle.id" :numero="tour.numero" />
            <template v-if="peutLivrer">
              <JDeplier>
                <JBouton v-if="!confirmerLivraison" variante="secondaire" data-test="livrer" @click="confirmerLivraison = true">{{ t('cercles.livrer') }}</JBouton>
              </JDeplier>
              <JDeplier>
                <div v-if="confirmerLivraison" class="confirmation" data-test="confirmer-livraison">
                  <p>{{ suivant ? t('cercles.livrerConfirmation', { nom: prenom(beneficiaire.nom), suivant: prenom(suivant.nom) }) : t('cercles.livrerDernier', { nom: prenom(beneficiaire.nom) }) }}</p>
                  <div class="commandes">
                    <JBouton :occupe="action === 'livrer'" data-test="confirmer" @click="livrer">{{ t('cercles.livrerOui') }}</JBouton>
                    <JBouton variante="discret" @click="confirmerLivraison = false">{{ t('communautes.annuler') }}</JBouton>
                  </div>
                </div>
              </JDeplier>
              <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
            </template>
          </section>
          <section v-else-if="cercle.statut === 'termine'" class="carte tour" data-test="termine">
            <h2>{{ t('cercles.termine') }}</h2>
            <p class="texte-discret">{{ t('cercles.termineAide', { n: livres.length }) }}</p>
            <RouterLink to="/vitrine">{{ t('cercles.voirVitrine') }}</RouterLink>
          </section>
          <section v-else-if="!cercle.tirage && preparation" class="carte tour" aria-labelledby="avant-titre" data-test="attente-tirage">
            <h2 id="avant-titre">{{ ideeOuverte ? t('cercles.dossier.titre') : t('cercles.avantTirage') }}</h2>
            <ul v-if="!ideeOuverte" class="etapes-tirage">
              <li :class="{ fait: !preparation.manquants }">{{ t('cercles.etapeMembres', { n: cercle.membres.length, taille: cercle.taille }) }}</li>
              <li :class="{ fait: !preparation.sansIdee }">{{ t('cercles.etapeIdees', { n: cercle.membres.length - preparation.sansIdee, total: cercle.membres.length }) }}</li>
            </ul>
            <p v-if="!ideeOuverte" class="texte-discret">{{ t('cercles.avantTirageAide', { n: preparation.manquants }, preparation.manquants) }}</p>

            <JBouton v-if="!membre && preparation.manquants" :occupe="action === 'rejoindre'" data-test="rejoindre" @click="agir('rejoindre', () => rejoindreCercle(cercle!.id))">{{ t('cercles.rejoindre') }}</JBouton>

            <template v-if="membre">
              <JDeplier>
                <div v-if="!ideeOuverte" class="mon-idee" data-test="mon-idee">
                  <p v-if="monIdee"><strong>{{ t('cercles.monIdee') }}</strong> {{ monIdee.titre }}</p>
                  <JBouton :variante="monIdee ? 'secondaire' : 'principal'" data-test="ouvrir-idee" @click="ouvrirIdee">{{ monIdee ? t('cercles.modifierIdee') : t('cercles.deposerIdee') }}</JBouton>
                </div>
              </JDeplier>
              <JDeplier>
                <form v-if="ideeOuverte" class="formulaire-idee" data-test="form-idee" @submit.prevent="!preparationOccupee && !csvInvalide && agir('idee', () => deposerIdee(cercle!.id, { ...idee, cahierChargesId: documentIdee?.id ?? null }))">
                  <div class="idee-champs">
                  <JChamp v-model="idee.titre" :libelle="t('cercles.ideeTitre')" :maxlength="LIMITES.ideeTitreMax" requis />
                  <JChamp v-model="idee.description" :libelle="t('cercles.ideeDescription')" :aide="t('cercles.ideeAide')" multiligne :lignes="4" :maxlength="LIMITES.ideeDescriptionMax" requis />
                  </div>
                  <PreparationIdee :key="cercle.id" v-model:document="documentIdee" :backlog="idee.backlog ?? []" :cercle-id="cercle.id" @update:backlog="idee.backlog = $event" @occupe="preparationOccupee = $event" @invalide="csvInvalide = $event" />
                  <div class="commandes">
                    <JBouton type="submit" :desactive="!ideeValide || preparationOccupee || csvInvalide" :occupe="action === 'idee'">{{ t('cercles.enregistrerIdee') }}</JBouton>
                    <JBouton variante="secondaire" @click="ideeOuverte = false">{{ t('communautes.annuler') }}</JBouton>
                  </div>
                </form>
              </JDeplier>
            </template>

            <!-- Le tirage est fait par le serveur, une seule fois : ici on ne fait que le demander. -->
            <JBouton v-if="membre && !ideeOuverte" :desactive="!preparation.pret" :occupe="action === 'tirage'" data-test="tirer" @click="agir('tirage', () => tirerAuSort(cercle!.id))">{{ t('cercles.tirer') }}</JBouton>
            <p v-if="membre && !ideeOuverte" class="texte-discret">{{ t('cercles.tirageAide') }}</p>
            <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
          </section>

        </div>
      </div>

      <!-- Un cercle ouvert se remplit : les membres voient les profils qui lui manquent et peuvent les inviter. -->
      <ProfilsRecommandes v-if="membre && !cercle.tirage && preparation?.manquants" :key="cercle.id" :cercle-id="cercle.id" :cercle-nom="cercle.nom" />
      <PartagerCercle v-if="membre && !cercle.tirage && preparation?.manquants" :cercle-id="cercle.id" :cercle-nom="cercle.nom" :ville="cercle.ville" :manquants="preparation.manquants" />

      <section class="ordre" aria-labelledby="ordre-titre">
        <h2 id="ordre-titre">{{ cercle.tirage ? t('cercles.ordre') : t('cercles.membresTitre') }}</h2>
        <ol class="ordre__liste" data-test="ordre">
          <li v-for="m in ordre" :key="m.personne.id" class="carte ordre__ligne" :class="`ordre__ligne--${tourDe(m.personne.id)?.statut ?? 'a_venir'}`">
            <span v-if="cercle.tirage" class="ordre__numero">{{ m.rang }}</span>
            <LienPersonne :id="m.personne.id" doublon><JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="40" /></LienPersonne>
            <div class="ordre__qui">
              <strong><LienPersonne :id="m.personne.id">{{ m.personne.nom }}</LienPersonne></strong>
              <!-- Les idées ne sont visibles que des membres : les autres voient seulement si elle est déposée. -->
              <span>{{ m.idee?.titre ?? (m.aDeposeIdee ? t('cercles.ideeDeposee') : t('cercles.pasDIdee')) }}</span>
              <details v-if="m.idee" class="dossier-membre" data-test="dossier-idee">
                <summary>{{ t('cercles.dossier.voir') }}</summary>
                <p>{{ m.idee.description }}</p>
                <PiecesJointes v-if="m.idee.cahierCharges" :pieces="[m.idee.cahierCharges]" />
                <template v-if="m.idee.backlog?.length"><strong>{{ t('cercles.dossier.tachesPretes', { n: m.idee.backlog.length }) }}</strong><ol><li v-for="(tache, i) in m.idee.backlog" :key="i">{{ tache.titre }}</li></ol></template>
              </details>
            </div>
            <span v-if="cercle.tirage" class="ordre__statut">{{ t(`cercles.statutTour.${tourDe(m.personne.id)?.statut ?? 'a_venir'}`) }}</span>
          </li>
        </ol>
      </section>
      </template>
      </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.cercle-page { display: grid; gap: var(--space-6); width: 100%; max-width: 84rem; margin-inline: auto; }
/* Dans le salon, la page monte sous la barre du site : la discussion prend toute la hauteur et toute la largeur. */
.cercle-page--salon { max-width: 96rem; margin-top: calc(-1 * var(--space-4)); }
.cercle-page--salon .espace--outils { align-items: stretch; }
/* Un en-tête compact : le retour, puis le nom du cercle et ses infos sur une ligne. La place va aux outils. */
.cercle-tete { display: grid; gap: var(--space-2); padding-top: var(--space-2); }
.cercle-tete__ligne { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-1) var(--space-4); }
.cercle-tete h1 { margin: 0; font-size: clamp(1.625rem, 3vw, 2.125rem); line-height: 1.15; letter-spacing: -0.5px; }
.cercle-tete__infos { margin: 0; color: var(--ink-muted); }
/* L'espace d'un membre : la barre latérale à gauche, l'outil ouvert à droite. */
.espace { display: grid; gap: var(--space-6); }
.espace--outils { grid-template-columns: 14.5rem minmax(0, 1fr); align-items: start; }
.espace__contenu { display: grid; gap: var(--space-6); min-width: 0; }
/* Le nom du cercle, en haut de la barre latérale : un retour discret vers la liste, le nom, ses infos. */
.identite { display: grid; gap: 2px; padding: 0 var(--space-3) var(--space-4); border-bottom: 1px solid var(--line); }
.identite__retour { display: inline-flex; align-items: center; gap: 4px; justify-self: start; margin-bottom: var(--space-2); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; text-decoration: none; }
.identite__retour:hover { color: var(--ink); }
.identite__retour:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: 4px; }
.identite__nom { margin: 0; font-size: 1.25rem; line-height: 1.2; letter-spacing: -0.3px; overflow-wrap: anywhere; }
.identite__infos { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.barre-laterale { position: sticky; top: calc(var(--space-12) + var(--space-8)); display: grid; gap: var(--space-6); }
.outils-nav { display: grid; gap: 2px; }
.outil-lien {
  display: flex; align-items: center; gap: var(--space-3); min-height: 42px; padding: 0 var(--space-3); border-radius: var(--radius-md);
  color: var(--ink-muted); font-weight: 600; text-decoration: none; white-space: nowrap;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.outil-lien:hover { color: var(--ink); background: color-mix(in srgb, var(--surface) 70%, transparent); }
.outil-lien.actif { color: var(--ink); background: var(--surface); box-shadow: 0 0 0 1px var(--line); }
.outil-lien.actif :deep(svg) { color: var(--action); }
.outil-lien:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.equipe { display: grid; gap: var(--space-2); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.equipe__titre { margin: 0 0 var(--space-1); padding: 0 var(--space-3); color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; }
.equipe ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.equipe__membre { display: flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-3); border-radius: var(--radius-md); font-size: var(--type-body-sm-size); }
.equipe__membre:hover { background: color-mix(in srgb, var(--surface) 70%, transparent); text-decoration: none !important; }
.equipe__membre span:first-of-type { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.equipe__tour { margin-inline-start: auto; color: var(--proof-ink); font-size: 0.6875rem; font-weight: 700; }
@media (max-width: 900px) {
  .espace--outils { grid-template-columns: minmax(0, 1fr); margin-top: 0; }
  .barre-laterale { position: static; }
  .outils-nav { display: flex; gap: var(--space-1); overflow-x: auto; scrollbar-width: none; margin-inline: calc(-1 * var(--space-4)); padding-inline: var(--space-4); }
  .outil-lien { flex: none; min-height: 40px; }
  .equipe { display: none; }
}
.mise-en-page { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 860px) { .mise-en-page { grid-template-columns: 1fr; } }

.mise-en-page--edition { grid-template-columns: minmax(0, 1fr); }
.idee-champs { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.2fr); gap: var(--space-4); align-items: start; }
@media (max-width: 760px) { .idee-champs { grid-template-columns: 1fr; } }

.anneau { margin: 0; padding: var(--space-6); }
.anneau__scene { position: relative; aspect-ratio: 1; max-width: 30rem; margin: 0 auto; }
.anneau__trace { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.anneau__trace circle {
  fill: none; stroke: var(--line-strong); stroke-width: 0.5; stroke-dasharray: 1.2 1.8;
}

.anneau__membre {
  position: absolute; translate: -50% -50%;
  display: grid; justify-items: center; gap: var(--space-1);
  animation: apparait var(--motion-celebrate) var(--ease-pop) both; animation-delay: calc(var(--i) * 70ms);
}
.anneau__membre :deep(.j-avatar) { box-shadow: 0 0 0 3px var(--surface); transition: transform var(--motion-fast) var(--ease-out); }
.anneau__lien { display: grid; justify-items: center; gap: var(--space-1); }
.anneau__lien:hover :deep(.j-avatar) { transform: scale(1.06); }
@media (prefers-reduced-motion: reduce) { .anneau__lien:hover :deep(.j-avatar) { transform: none; } }
.anneau__nom { padding: 0 var(--space-2); border-radius: var(--radius-pill); background: var(--surface); font-size: var(--type-body-sm-size); font-weight: 600; }
/* Le bénéficiaire du tour : un halo orange, la couleur de la preuve. */
.anneau__membre--tour :deep(.j-avatar) { box-shadow: 0 0 0 3px var(--surface), 0 0 0 7px var(--proof), 0 0 26px color-mix(in srgb, var(--proof) 55%, transparent); }
.anneau__membre--livre { opacity: 0.6; }
/* Au tirage, chacun glisse jusqu'à sa place : on voit l'ordre se former. */
.anneau__membre { transition: left calc(var(--motion-celebrate) * 1.4) var(--ease-pop), top calc(var(--motion-celebrate) * 1.4) var(--ease-pop); }
.anneau__libre { position: absolute; translate: -50% -50%; width: 52px; height: 52px; border-radius: 50%; border: 2px dashed var(--line-strong); background: var(--surface); }
.etapes-tirage { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.etapes-tirage li { display: flex; align-items: center; gap: var(--space-2); }
.etapes-tirage li::before { content: ''; width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--line-strong); flex: none; }
.etapes-tirage li.fait::before { content: '✓'; display: grid; place-items: center; font-size: 0.75rem; line-height: 1; font-weight: 700; border-color: var(--action); background: var(--action); color: var(--on-action); }
.mon-idee, .formulaire-idee { display: grid; gap: var(--space-3); }
.mon-idee p { margin: 0; }
.dossier-membre { margin-top: var(--space-2); min-width: 0; font-size: .9375rem; }
.dossier-membre summary { cursor: pointer; color: var(--action-ink); font-weight: 600; }
.dossier-membre > p { white-space: pre-wrap; }
.dossier-membre ol { max-height: 240px; overflow: auto; padding-left: 22px; }
.commandes { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.confirmation { display: grid; gap: var(--space-3); padding: var(--space-4); border-radius: var(--radius-md); background: var(--proof-soft); }
.confirmation p { margin: 0; }
.livre { display: grid; gap: var(--space-1); margin: 0; padding: var(--space-4) var(--space-6); border-left: 4px solid var(--proof); animation: apparait var(--motion-celebrate) var(--ease-pop) both; }

.cote { display: grid; gap: var(--space-4); }
.tour { display: grid; gap: var(--space-3); padding: var(--space-6); }
.tour h2 { margin: 0; font-size: 1.375rem; }
.tour__surtitre { margin: 0; color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
.avancement { display: grid; gap: var(--space-2); }
.avancement p { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.avancement__barre { height: 10px; border-radius: var(--radius-pill); background: var(--surface-sunken, color-mix(in srgb, var(--ink) 8%, transparent)); overflow: hidden; }
.avancement__barre i { display: block; height: 100%; border-radius: inherit; background: var(--action); animation: remplit var(--motion-celebrate) var(--ease-out) both; transform-origin: left; }


.ordre h2 { margin-bottom: var(--space-4); }
.ordre__liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.ordre__ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4); }
.ordre__numero { width: 1.5rem; text-align: center; font-family: var(--font-display); font-weight: 700; color: var(--ink-muted); font-variant-numeric: tabular-nums; }
.ordre__qui { flex: 1; display: grid; min-width: 0; }
.ordre__qui span { color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ordre__statut { padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600; background: color-mix(in srgb, var(--ink) 7%, transparent); white-space: nowrap; }
.ordre__ligne--en_cours { border-color: var(--proof); }
.ordre__ligne--en_cours .ordre__statut { background: var(--proof); color: var(--on-proof); }
.ordre__ligne--livre .ordre__statut { background: var(--action-soft); color: var(--action-ink); }
@keyframes apparait { from { opacity: 0; scale: 0.6; } }
@keyframes remplit { from { transform: scaleX(0); } }
@media (prefers-reduced-motion: reduce) { .anneau__trace circle { animation: none; } }
.cercle-tete__profils { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: var(--space-2) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.cercle-tete__profil { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; }
</style>
