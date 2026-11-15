<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JDeplier, JFiltres, JIllustration, JSquelette } from '../../design-system'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { ilYa } from '../vitrine/vitrine'
import { useSession } from '../../session'
import BanniereCommunaute from './BanniereCommunaute.vue'
import EnDirect from './EnDirect.vue'
import EspaceCommunautes from './EspaceCommunautes.vue'
import MembresCommunaute from './MembresCommunaute.vue'
import SalonsCommunaute from './SalonsCommunaute.vue'

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
  if (devientMembre && vue.value === 'fil') formulaireOuvert.value = true
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

// « Quoi de neuf ? » : mêmes limites que le contrat, vérifiées aussi par le serveur.
const formulaire = ref({ type: 'question' as 'question' | 'discussion', titre: '', contenu: '' })
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const valide = computed(() => formulaire.value.titre.trim().length >= 10 && formulaire.value.contenu.trim().length >= 20)
async function publier() {
  if (!valide.value) return
  envoi.value = 'envoi'
  const { data } = await api.POST('/api/communautes/{slug}/publications', {
    params: { path: { slug: slug.value } },
    body: { ...formulaire.value, titre: formulaire.value.titre.trim(), contenu: formulaire.value.contenu.trim() },
  })
  if (!data) return void (envoi.value = 'erreur')
  envoi.value = 'repos'
  formulaire.value = { type: 'question', titre: '', contenu: '' }
  formulaireOuvert.value = false
  await router.push(`/publications/${data.id}`)
}
const formulaireOuvert = ref(false)
const zoneFormulaire = ref<HTMLElement | null>(null)
function ouvrirFormulaire(type: 'question' | 'discussion') {
  formulaire.value.type = type
  formulaireOuvert.value = true
}
watch(formulaireOuvert, (ouvert) => {
  if (ouvert) void nextTick(() => zoneFormulaire.value?.querySelector('input')?.focus({ preventScroll: true }))
})
const prenom = computed(() => moi.value?.nom.split(' ')[0] ?? '')
</script>

<template>
  <EspaceCommunautes :actuelle="etat === 'introuvable' ? undefined : slug" :actif="actif" :communaute="communaute" :recharge="cleRecharge">
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
          <li>{{ t('communautes.fonctionnement.ia') }}</li>
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
        <BanniereCommunaute :communaute="communaute" :actifs="actifs" :adhesion-en-cours="adhesionEnCours" @adhesion="basculerAdhesion" />

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

        <section class="carte-espace compositeur" :aria-label="t('communautes.espace.invite')">
          <JDeplier>
            <div v-if="communaute.membre && formulaireOuvert" ref="zoneFormulaire">
              <form class="formulaire" data-test="publier" @submit.prevent="publier">
                <JFiltres
                  v-model="formulaire.type"
                  :libelle="t('communautes.type')"
                  :options="[
                    { valeur: 'question', texte: t('communautes.types.question') },
                    { valeur: 'discussion', texte: t('communautes.types.discussion') },
                  ]"
                />
                <JChamp v-model="formulaire.titre" :libelle="t('communautes.champTitre')" :maxlength="150" requis />
                <JChamp
                  v-model="formulaire.contenu"
                  :libelle="t('communautes.champContenu')"
                  :aide="formulaire.type === 'question' ? t('communautes.aideIa') : undefined"
                  multiligne
                  :lignes="5"
                  :maxlength="10000"
                  requis
                />
                <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('communautes.erreurPublier') }}</p>
                <div class="formulaire__commandes">
                  <JBouton variante="discret" @click="formulaireOuvert = false">{{ t('communautes.annuler') }}</JBouton>
                  <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'">{{ t('communautes.publier') }}</JBouton>
                </div>
              </form>
            </div>
          </JDeplier>
          <div v-if="communaute.membre && !formulaireOuvert" class="quoi-de-neuf">
            <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
            <button type="button" class="quoi-de-neuf__invite" data-test="ouvrir-formulaire" @click="ouvrirFormulaire('question')">
              {{ t('communautes.quoiDeNeuf', { prenom }) }}
            </button>
            <button type="button" class="raccourci" :title="t('communautes.types.question')" @click="ouvrirFormulaire('question')">
              <span aria-hidden="true">?</span><span class="visuellement-cache">{{ t('communautes.types.question') }}</span>
            </button>
            <button type="button" class="raccourci" :title="t('communautes.types.discussion')" @click="ouvrirFormulaire('discussion')">
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
              <span class="visuellement-cache">{{ t('communautes.types.discussion') }}</span>
            </button>
          </div>
          <div v-if="!communaute.membre" class="rejoindre">
            <p>{{ t('communautes.rejoindrePourPublier') }}</p>
            <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
          </div>
        </section>

        <JSquelette v-if="etatFil === 'chargement'" :nombre="3" />
        <div v-else-if="etatFil === 'indisponible'" role="alert" class="carte-espace vide">
          <p>{{ t('etat.indisponible') }}</p>
          <JBouton variante="secondaire" @click="chargerFil">{{ t('etat.reessayer') }}</JBouton>
        </div>
        <div v-else-if="visibles.length === 0" class="carte-espace vide">
          <JIllustration nom="communaute" />
          <p data-test="vide">{{ recherche ? t('communautes.rienTrouve') : t('communautes.vide') }}</p>
        </div>
        <TransitionGroup v-else tag="ul" name="fil" class="publications" data-test="publications" appear>
          <li v-for="(p, i) in visibles" :key="p.id" class="publication" :style="{ '--rang': i }">
            <header class="publication__tete">
              <JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="40" />
              <p class="publication__qui">
                <strong>{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</strong>
                <span>{{ ilYa(p.creeLe, locale) }}</span>
              </p>
              <span class="type" :class="`type--${p.type}`">{{ t(`communautes.types.${p.type}`) }}</span>
            </header>
            <h2 class="publication__titre"><RouterLink :to="`/publications/${p.id}`" class="publication__lien">{{ p.titre }}</RouterLink></h2>
            <footer class="publication__pied">
              <span class="reponses">
                <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
                {{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}
              </span>
              <JBadgePreuve v-if="p.accepteeLe">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
              <span v-else-if="p.type === 'question' && p.nombreReponses === 0" class="appel-aide">{{ t('communautes.espace.premier') }}</span>
              <span class="publication__ouvrir" aria-hidden="true">{{ t('communautes.ouvrir') }} →</span>
            </footer>
          </li>
        </TransitionGroup>
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

.compositeur { padding: var(--space-4); }
.quoi-de-neuf { display: flex; align-items: center; gap: var(--space-3); }
.quoi-de-neuf__invite {
  flex: 1; min-height: 48px; text-align: start; padding: 0 var(--space-4); border: 1px solid transparent; border-radius: var(--radius-pill);
  background: var(--surface-raised); color: var(--ink-muted); font: inherit; cursor: text;
}
.quoi-de-neuf__invite:hover { border-color: var(--line-strong); }
.raccourci {
  display: grid; place-items: center; width: 40px; height: 40px; border: 1px solid var(--line); border-radius: 12px;
  background: var(--surface); color: var(--ink-muted); font: inherit; font-weight: 800; font-size: 1.0625rem; cursor: pointer;
  transition: transform var(--motion-fast) var(--ease-out);
}
.raccourci:hover { color: var(--ink); border-color: var(--line-strong); transform: rotate(-6deg); }
.formulaire { display: grid; gap: var(--space-4); }
.formulaire__commandes { display: flex; gap: var(--space-2); justify-content: flex-end; }
.rejoindre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.rejoindre p { margin: 0; font-weight: 600; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }

.vide { display: grid; justify-items: center; gap: var(--space-3); padding: var(--space-8) var(--space-6); text-align: center; color: var(--ink-muted); }
.vide p { margin: 0; }
.vide .j-illustration { width: 12rem; }

.publications { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.publication {
  position: relative; display: grid; gap: var(--space-3); padding: var(--space-4) var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.publication:hover { transform: translateY(-2px); border-color: var(--line-strong); }
.publication:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .publication:hover, .raccourci:hover { transform: none; } }
.publication__tete { display: flex; align-items: center; gap: var(--space-3); }
.publication__qui { margin: 0; flex: 1; min-width: 0; display: grid; line-height: 1.25; }
.publication__qui span { color: var(--ink-muted); font-size: 0.8125rem; }
.type { padding: 3px 10px; border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; background: var(--surface-raised); color: var(--ink); }
.type--question { background: color-mix(in srgb, var(--electric-blue) 16%, transparent); color: var(--action-ink); }
.type--montre_ton_projet { background: color-mix(in srgb, var(--cantaloupe) 20%, transparent); color: var(--proof-ink); }
.publication__titre { margin: 0; font-family: var(--font-display); font-size: 1.25rem; line-height: 1.3; letter-spacing: -0.01em; }
/* Toute la carte mène à la publication ; le titre reste le seul nom du lien. */
.publication__lien { color: var(--ink); text-decoration: none; }
.publication__lien::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.publication__lien:focus-visible { outline: none; }
.publication__pied { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--line); }
.reponses { display: inline-flex; align-items: center; gap: var(--space-1); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.appel-aide { color: var(--proof-ink); font-size: var(--type-body-sm-size); font-weight: 700; }
.publication__ouvrir { margin-inline-start: auto; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.publication:hover .publication__ouvrir { color: var(--ink); }

.fil-enter-active { transition: opacity var(--motion-base) var(--ease-out), transform var(--motion-base) var(--ease-out); transition-delay: calc(var(--rang) * 40ms); }
.fil-enter-from { opacity: 0; transform: translateY(8px); }
@media (prefers-reduced-motion: reduce) { .fil-enter-active { transition: none; } }

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
  .publication { padding: var(--space-4); }
  .raccourci { display: none; }
}
</style>
