<script setup lang="ts">
import Composeur from '../../components/texte/Composeur.vue'
import type { PieceJointe } from '../../components/texte/fichiers'
import { JAvatar, JBouton, JIllustration, JSquelette } from '../../design-system'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { ilYa } from '../vitrine/vitrine'
import { useSession } from '../../session'
import BanniereCommunaute from './BanniereCommunaute.vue'
import CartePublication from './CartePublication.vue'
import EnDirect from './EnDirect.vue'
import EspaceCommunautes from './EspaceCommunautes.vue'
import MembresCommunaute from './MembresCommunaute.vue'
import SalonsCommunaute from './SalonsCommunaute.vue'
import { usePleinEcranTelephone } from '../../components/pleinEcran'

type Communaute = components['schemas']['Communaute']
type PublicationResume = components['schemas']['PublicationResume']
type Onglet = 'recents' | 'ouvertes' | 'resolues' | 'projets'

/*
 * L'espace d'une communauté. L'adresse dit ce qu'on regarde, et se partage telle quelle :
 * - le fil (par défaut) : la bannière, « Quoi de neuf ? » et les publications, avec `?onglet=ouvertes|resolues|projets` ;
 * - un salon en direct : `?salon=general` ;
 * - les membres : `?voir=membres` ; « À propos » : `?voir=apropos`.
 * (Jamais `?vue=` : en développement, Vite prend une adresse avec `?vue` pour un fichier Vue.)
 */
const { t, locale } = useI18n()
const { moi } = useSession()
const route = useRoute()
const router = useRouter()

const slug = computed(() => String(route.params.slug))
const salon = computed(() => (typeof route.query.salon === 'string' && route.query.salon ? route.query.salon : null))
const vue = computed(() => {
  if (salon.value) return 'salon'
  return route.query.voir === 'membres' || route.query.voir === 'apropos' ? route.query.voir : 'fil'
})
// Sur téléphone, un salon ouvert prend tout l'écran, comme une conversation.
usePleinEcranTelephone(() => vue.value === 'salon')
const actif = computed(() => (vue.value === 'salon' ? salon.value! : vue.value))
const onglet = computed<Onglet>(() => {
  const o = route.query.onglet
  return o === 'ouvertes' || o === 'resolues' || o === 'projets' ? o : 'recents'
})

const communaute = ref<Communaute | null>(null)
const publications = ref<PublicationResume[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const etatFil = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const actifs = ref(0)

/** `silencieux` : après « Rejoindre », on relit sans effacer l'écran. */
async function chargerCommunaute(silencieux = false) {
  if (!silencieux) etat.value = 'chargement'
  try {
    const { data, response } = await api.GET('/api/communautes/{slug}', { params: { path: { slug: slug.value } } })
    if (response.status === 404) return void (etat.value = 'introuvable')
    if (!data) throw new Error('communauté indisponible')
    communaute.value = data
    etat.value = 'pret'
  } catch {
    if (!silencieux) etat.value = 'indisponible'
  }
}
async function chargerFil() {
  etatFil.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes/{slug}/publications', {
      params: {
        path: { slug: slug.value },
        query: {
          limite: 50,
          ...(onglet.value === 'ouvertes' ? { sansReponseAcceptee: true } : {}),
          ...(onglet.value === 'projets' ? { type: 'montre_ton_projet' as const } : {}),
        },
      },
    })
    if (!data) throw new Error('fil indisponible')
    publications.value = data.elements ?? []
    etatFil.value = 'pret'
  } catch {
    etatFil.value = 'indisponible'
  }
}
watch(slug, () => void chargerCommunaute(), { immediate: true })
watch([slug, onglet, vue], () => {
  if (vue.value === 'fil') void chargerFil()
}, { immediate: true })

const adhesionEnCours = ref(false)
async function basculerAdhesion() {
  if (!communaute.value) return
  adhesionEnCours.value = true
  const options = { params: { path: { slug: slug.value } } }
  const { response } = communaute.value.membre
    ? await api.DELETE('/api/communautes/{slug}/adhesion', options)
    : await api.POST('/api/communautes/{slug}/adhesion', options)
  adhesionEnCours.value = false
  if (!response.ok) return
  const devientMembre = !communaute.value.membre
  await chargerCommunaute(true)
  cleRecharge.value++
  if (devientMembre && vue.value === 'fil') void nextTick(() => composeur.value?.focus())
}
/** Après « Rejoindre », les salons et le côté se rechargent (adresse de l'appel, zone d'écriture). */
const cleRecharge = ref(0)

/** Un salon supprimé : on revient dans # général, et la barre et le côté oublient le salon. */
async function salonSupprime() {
  await router.replace({ query: { salon: 'general' } })
  cleRecharge.value++
}

const ONGLETS: Onglet[] = ['recents', 'ouvertes', 'resolues', 'projets']
function choisirOnglet(o: Onglet) {
  void router.replace({ query: o === 'recents' ? {} : { onglet: o } })
}

// La recherche filtre ce qui est affiché (titre et auteur), sans rien demander au serveur.
const recherche = ref('')
const visibles = computed(() => {
  let liste = publications.value
  if (onglet.value === 'resolues') liste = liste.filter((p) => p.accepteeLe)
  const q = recherche.value.trim().toLocaleLowerCase(locale.value)
  if (!q) return liste
  return liste.filter((p) => `${p.titre} ${p.auteur?.nom ?? ''}`.toLocaleLowerCase(locale.value).includes(q))
})

// « Quoi de neuf ? » : on écrit directement dans le fil. Le contrat demande un titre : c'est le début du texte
// (sa première ligne, ou ses 80 premiers caractères). La question demande l'aide de l'IA, la discussion non.
const texte = ref('')
const genre = ref<'question' | 'discussion'>('discussion')
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
// Avec une image ou une vidéo, le texte peut être court : la pièce jointe parle d'elle-même.
const valide = computed(() => texte.value.trim().length >= (pieces.value.length ? 0 : 20))
function titreDe(contenu: string) {
  const ligne = contenu.split('\n')[0]!.replace(/[*_~`>#-]/g, '').trim()
  let base = ligne.length >= 10 ? ligne : contenu.replace(/\s+/g, ' ')
  if (base.length < 10) base = t(`communautes.fil.partage.${pieces.value.find((p) => p.sorte !== 'document')?.sorte ?? 'document'}`, { prenom: prenom.value })
  return base.length > 150 ? `${base.slice(0, 149).trimEnd()}…` : base
}
async function publier() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  const contenu = texte.value.trim()
  try {
    const { data } = await api.POST('/api/communautes/{slug}/publications', {
      params: { path: { slug: slug.value } },
      body: { type: genre.value, titre: titreDe(contenu), contenu, ...(pieces.value.length ? { pieces: pieces.value.map((p) => p.id) } : {}) },
    })
    if (!data) return void (envoi.value = 'erreur')
    const { id, communaute: c, type, titre, auteur, nombreReponses, accepteeLe, creeLe, piecesJointes } = data
    publications.value = [{ id, communaute: c, type, titre, auteur, nombreReponses, accepteeLe, creeLe, contenu: data.contenu, piecesJointes }, ...publications.value]
    texte.value = ''
    pieces.value = []
    envoi.value = 'repos'
    composeur.value?.rafraichir()
  } catch {
    envoi.value = 'erreur'
  }
}
function remplacer(maj: PublicationResume) {
  publications.value = publications.value.map((x) => (x.id === maj.id ? maj : x))
}
const prenom = computed(() => moi.value?.nom.split(' ')[0] ?? '')
</script>

<template>
  <EspaceCommunautes :actuelle="etat === 'introuvable' ? null : slug" :actif="actif" :communaute="communaute" :recharge="cleRecharge">
    <JSquelette v-if="etat === 'chargement' && !communaute" :nombre="4" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="chargerCommunaute()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="communaute">
      <!-- Un salon en direct, à la hauteur de l'écran. -->
      <div v-if="vue === 'salon'" class="cadre-salon">
        <SalonsCommunaute
          :key="`${slug}-${cleRecharge}`"
          :slug="slug"
          :salon="salon ?? undefined"
          :membre="communaute.membre"
          :moderateur="communaute.moderateur"
          :adhesion-en-cours="adhesionEnCours"
          @rejoindre="basculerAdhesion"
          @supprime="salonSupprime"
        />
      </div>

      <MembresCommunaute v-else-if="vue === 'membres'" :key="`m-${slug}-${cleRecharge}`" :slug="slug" />

      <section v-else-if="vue === 'apropos'" class="carte-espace apropos" aria-labelledby="titre-apropos" data-test="apropos">
        <h1 id="titre-apropos">{{ t('communautes.espace.apropos') }}</h1>
        <p class="apropos__description">{{ communaute.description }}</p>
        <dl class="chiffres">
          <div><dt>{{ t('communautes.chiffres.membres') }}</dt><dd>{{ communaute.nombreMembres.toLocaleString(locale) }}</dd></div>
          <div><dt>{{ t('communautes.chiffres.questionsOuvertes') }}</dt><dd>{{ communaute.questionsOuvertes }}</dd></div>
        </dl>
        <h2>{{ t('communautes.fonctionnement.titre') }}</h2>
        <ol class="etapes">
          <li>{{ t('communautes.fonctionnement.poser') }}</li>
          <li>{{ t('communautes.fonctionnement.accepter') }}</li>
        </ol>
        <div>
          <JBouton :variante="communaute.membre ? 'secondaire' : 'principal'" :occupe="adhesionEnCours" data-test="adhesion" @click="basculerAdhesion">
            {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
          </JBouton>
        </div>
      </section>

      <!-- Le fil : la bannière, « Quoi de neuf ? », puis les publications. -->
      <template v-else>
        <BanniereCommunaute :communaute="communaute" :actifs="actifs" :adhesion-en-cours="adhesionEnCours" @adhesion="basculerAdhesion" @maj="(c) => (communaute = c)" />

        <div class="barre-fil">
          <nav class="onglets" :aria-label="t('communautes.filtre')">
            <button
              v-for="o in ONGLETS"
              :key="o"
              type="button"
              class="onglet"
              :aria-pressed="onglet === o"
              :data-test="`onglet-${o}`"
              @click="choisirOnglet(o)"
            >
              {{ t(`communautes.onglets.${o}`) }}
            </button>
          </nav>
          <label class="recherche">
            <span class="visuellement-cache">{{ t('communautes.chercher') }}</span>
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="m13.5 13.5 3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
            <input v-model="recherche" type="search" :placeholder="t('communautes.chercher')" data-test="recherche" />
          </label>
        </div>

        <!-- Écrire directement dans le fil : ce qu'on publie apparaît en haut, sans changer de page. -->
        <section class="carte-espace fil" :aria-label="t('communautes.espace.fil')">
          <form v-if="communaute.membre" class="ecrire" data-test="publier" @submit.prevent="publier">
            <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="44" />
            <div class="ecrire__corps">
              <Composeur
                ref="composeur"
                v-model="texte"
                v-model:pieces="pieces"
                id-zone="quoi-de-neuf"
                :placeholder="t('communautes.quoiDeNeuf', { prenom })"
                :libelle-envoyer="t('communautes.publier')"
                :envoi="envoi === 'envoi'"
                :max="10000"
                :lignes="2"
                :entree-envoie="false"
                test-zone="zone-publier"
                test-envoyer="envoyer-publication"
                @envoyer="publier"
              >
                <template #outils>
                  <span class="separateur" aria-hidden="true" />
                  <div class="genres" role="radiogroup" :aria-label="t('communautes.type')">
                    <button
                      v-for="g in (['discussion', 'question'] as const)"
                      :key="g"
                      type="button"
                      role="radio"
                      class="genre"
                      :aria-checked="genre === g"
                      :data-test="`genre-${g}`"
                      @click="genre = g"
                    >
                      {{ g === 'question' ? t('communautes.types.question') : t('communautes.types.discussion') }}
                    </button>
                  </div>
                </template>
              </Composeur>
              <p v-if="texte.trim().length > 0 && !valide" class="compteur compteur--court">{{ t('communautes.fil.encore', { n: 20 - texte.trim().length }, 20 - texte.trim().length) }}</p>
              <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('communautes.erreurPublier') }}</p>
            </div>
          </form>
          <div v-else class="rejoindre">
            <p>{{ t('communautes.rejoindrePourPublier') }}</p>
            <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
          </div>

          <JSquelette v-if="etatFil === 'chargement'" :nombre="3" class="fil__attente" />
          <div v-else-if="etatFil === 'indisponible'" role="alert" class="vide">
            <p>{{ t('etat.indisponible') }}</p>
            <JBouton variante="secondaire" @click="chargerFil">{{ t('etat.reessayer') }}</JBouton>
          </div>
          <div v-else-if="visibles.length === 0" class="vide">
            <JIllustration nom="communaute" />
            <p data-test="vide">{{ recherche ? t('communautes.rienTrouve') : t('communautes.vide') }}</p>
          </div>
          <TransitionGroup v-else tag="div" name="nouvelle" class="publications" data-test="publications">
            <CartePublication
              v-for="pub in visibles"
              :key="pub.id"
              :publication="pub"
              :membre="communaute.membre"
              @maj="remplacer"
            />
          </TransitionGroup>
        </section>
      </template>
    </template>

    <template v-if="communaute && vue !== 'apropos'" #contexte>
      <EnDirect :key="`d-${slug}-${cleRecharge}`" :slug="slug" @actifs="actifs = $event" />
    </template>
  </EspaceCommunautes>
</template>

<style scoped>
.carte-espace { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); }
.etat-vide { margin-top: var(--space-6); }

.cadre-salon {
  display: flex; height: calc(100dvh - 64px - var(--space-6) - var(--space-8));
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); overflow: hidden;
}

.barre-fil { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.onglets { display: flex; flex-wrap: wrap; gap: var(--space-1); padding: 4px; border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line); }
.onglet {
  min-height: 36px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill); background: transparent;
  color: var(--ink-muted); font: inherit; font-weight: 600; font-size: var(--type-body-sm-size); cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out);
}
.onglet:hover { color: var(--ink); }
.onglet[aria-pressed='true'] { background: var(--ink); color: var(--bg); }
.onglet:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.recherche { display: flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-4); border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line); color: var(--ink-muted); }
.recherche input { border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; width: 16rem; max-width: 60vw; }
.recherche:focus-within { outline: 2px solid var(--focus); outline-offset: 1px; }

/* Le fil : un seul panneau, la zone pour écrire en haut, puis les publications séparées par une ligne. */
.fil { overflow: hidden; }
.ecrire { display: grid; grid-template-columns: 44px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--line); }
.ecrire__corps { display: grid; gap: var(--space-2); min-width: 0; }
.genres { display: flex; gap: var(--space-1); }
.separateur { width: 1px; height: 18px; margin: 0 var(--space-1); background: var(--line); }
.genre {
  min-height: 32px; padding: 0 var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line); background: transparent;
  color: var(--ink-muted); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer;
}
.genre[aria-checked='true'] { border-color: var(--electric-blue); background: color-mix(in srgb, var(--electric-blue) 12%, transparent); color: var(--action-ink); }
.genre:focus-visible, .publier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.compteur { margin: 0; color: var(--ink-muted); font-size: 0.8125rem; font-variant-numeric: tabular-nums; }
.compteur--court { color: var(--proof-ink); }
.publier {
  min-height: 38px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--electric-blue); color: #fff;
  font: inherit; font-weight: 700; cursor: pointer;
}
.publier:disabled { opacity: 0.45; cursor: default; }
.rejoindre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--line); background: var(--action-soft); }
.rejoindre p { margin: 0; font-weight: 600; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }
.fil__attente { padding: var(--space-4); }
.vide { display: grid; justify-items: center; gap: var(--space-3); padding: var(--space-8) var(--space-6); text-align: center; color: var(--ink-muted); }
.vide p { margin: 0; }
.vide .j-illustration { width: 12rem; }
/* Une publication qu'on vient d'écrire arrive en douceur en haut du fil (rien si le mouvement est réduit). */
.nouvelle-enter-active { transition: opacity 260ms var(--ease-out), transform 260ms var(--ease-out); }
.nouvelle-enter-from { opacity: 0; transform: translateY(-8px); }
@media (prefers-reduced-motion: reduce) { .nouvelle-enter-active { transition: none; } }

.apropos { padding: var(--space-6); display: grid; gap: var(--space-4); }
.apropos h1 { margin: 0; font-size: 1.5rem; }
.apropos h2 { margin: var(--space-2) 0 0; font-size: 1.0625rem; }
.apropos__description { margin: 0; color: var(--ink-muted); max-width: 46rem; }
.chiffres { margin: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr)); gap: var(--space-3); max-width: 32rem; }
.chiffres div { padding: var(--space-4); border-radius: var(--radius-md); background: var(--surface-raised); }
.chiffres dt { color: var(--ink-muted); font-size: 0.8125rem; }
.chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.75rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.etapes { margin: 0; padding-inline-start: var(--space-6); display: grid; gap: var(--space-2); color: var(--ink-muted); max-width: 46rem; }

@media (max-width: 860px) {
  .cadre-salon { height: calc(100dvh - 12rem); }
  .ecrire { padding: var(--space-4); }
}
/* Téléphone : l'avatar à côté du champ lui prenait 56 px sur 256. Le champ prend toute la largeur. */
@media (max-width: 480px) {
  .ecrire { grid-template-columns: minmax(0, 1fr); padding: var(--space-3); }
  .ecrire > :first-child:not(.ecrire__corps) { display: none; }
}
/* Salon en plein écran sur téléphone (components/pleinEcran.ts) : tout l'écran, sans cadre. */
@media (max-width: 720px) {
  :root.discussion-plein-ecran .cadre-salon { height: 100dvh; border: 0; border-radius: 0; box-shadow: none; }
  /* Le fil va bord à bord : plus de marge, de cadre ni d'arrondi autour des publications. */
  .fil, .banniere { margin-inline: calc(-1 * var(--space-4)); border-radius: 0; border-width: 1px 0; box-shadow: none; }
}
</style>
