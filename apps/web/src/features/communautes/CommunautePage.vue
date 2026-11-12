<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JFiltres, JSquelette } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'
import { ilYa } from '../vitrine/vitrine'
import { useSession } from '../../session'
import { monogramme } from './communautes'
import EspaceCommunautes from './EspaceCommunautes.vue'

type Communaute = components['schemas']['Communaute']
type PublicationResume = components['schemas']['PublicationResume']

const { t, locale } = useI18n()
const { moi } = useSession()
const route = useRoute()
const router = useRouter()

const slug = computed(() => String(route.params.slug))
const seulementOuvertes = computed(() => route.query.filtre === 'ouvertes')
const communaute = ref<Communaute | null>(null)
const publications = ref<PublicationResume[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const [c, p] = await Promise.all([
      api.GET('/api/communautes/{slug}', { params: { path: { slug: slug.value } } }),
      api.GET('/api/communautes/{slug}/publications', {
        params: { path: { slug: slug.value }, query: { sansReponseAcceptee: seulementOuvertes.value || undefined } },
      }),
    ])
    if (c.response.status === 404) return void (etat.value = 'introuvable')
    if (!c.data || !p.data) throw new Error('communauté indisponible')
    communaute.value = c.data
    publications.value = p.data.elements ?? []
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch([slug, seulementOuvertes], charger, { immediate: true })

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
  formulaireOuvert.value = !communaute.value.membre
  await charger()
}

// Formulaire « Poser une question » : mêmes limites que le contrat, vérifiées aussi par le serveur.
const formulaire = ref({ type: 'question' as 'question' | 'discussion', titre: '', contenu: '' })
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const valide = computed(
  () => formulaire.value.titre.trim().length >= 10 && formulaire.value.contenu.trim().length >= 20,
)
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

const filtre = computed({
  get: () => (seulementOuvertes.value ? 'ouvertes' : 'toutes') as 'toutes' | 'ouvertes',
  set: (v) => void router.replace({ query: v === 'ouvertes' ? { filtre: 'ouvertes' } : {} }),
})

/** Comme dans la maquette : les échanges d'abord, le formulaire s'ouvre à la demande (et dès qu'on rejoint). */
const formulaireOuvert = ref(false)
</script>

<template>
  <EspaceCommunautes :actuelle="slug">
    <JSquelette v-if="etat === 'chargement' && !communaute" :nombre="4" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="communaute">
      <header class="banniere" :class="{ 'banniere--antenne': communaute.pays }">
        <!-- La couverture : le nom en très grand, en filigrane. Décoratif. -->
        <div class="banniere__couverture" aria-hidden="true"><span>{{ communaute.nom }}</span></div>
        <div class="banniere__corps">
          <span class="banniere__monogramme" aria-hidden="true">{{ monogramme(communaute.nom) }}</span>
          <div class="banniere__texte">
            <h1>{{ communaute.nom }}</h1>
            <p class="banniere__meta">
              {{ t('communautes.membres', { n: communaute.nombreMembres }, communaute.nombreMembres) }}
              <template v-if="communaute.pays"> · 📍 {{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}</template>
            </p>
          </div>
          <JBouton
            :variante="communaute.membre ? 'secondaire' : 'principal'"
            data-test="adhesion"
            :occupe="adhesionEnCours"
            @click="basculerAdhesion"
          >
            {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
          </JBouton>
        </div>
        <p class="banniere__description">{{ communaute.description }}</p>
      </header>

      <!-- Le compositeur, comme sur un réseau social : une ligne qui invite, le formulaire complet au clic. -->
      <form v-if="communaute.membre && formulaireOuvert" class="compositeur compositeur--ouvert" data-test="publier" @submit.prevent="publier">
        <div class="compositeur__tete">
          <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
          <JFiltres
            v-model="formulaire.type"
            :libelle="t('communautes.type')"
            :options="[
              { valeur: 'question', texte: t('communautes.types.question') },
              { valeur: 'discussion', texte: t('communautes.types.discussion') },
            ]"
          />
        </div>
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
        <div class="compositeur__commandes">
          <JBouton variante="discret" @click="formulaireOuvert = false">{{ t('communautes.annuler') }}</JBouton>
          <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'">{{ t('communautes.publier') }}</JBouton>
        </div>
      </form>
      <div v-else-if="communaute.membre" class="compositeur">
        <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
        <button type="button" class="compositeur__invite" data-test="ouvrir-formulaire" @click="formulaireOuvert = true">
          {{ t('communautes.espace.invite') }}
        </button>
      </div>
      <div v-else class="compositeur compositeur--rejoindre">
        <p>{{ t('communautes.rejoindrePourPublier') }}</p>
        <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
      </div>

      <div class="onglets">
        <JFiltres
          v-model="filtre"
          :libelle="t('communautes.filtre')"
          :options="[
            { valeur: 'toutes', texte: t('communautes.toutes') },
            { valeur: 'ouvertes', texte: t('communautes.ouvertes') },
          ]"
        />
      </div>

      <JSquelette v-if="etat === 'chargement'" :nombre="3" />
      <div v-else-if="publications.length === 0" class="etat-vide">
        <p data-test="vide">{{ t('communautes.vide') }}</p>
      </div>
      <ul v-else class="fils" data-test="publications">
        <li v-for="p in publications" :key="p.id" class="fil" :class="{ 'fil--resolu': p.accepteeLe }">
          <div class="fil__auteur">
            <JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="40" />
            <div>
              <strong>{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</strong>
              <span>{{ ilYa(p.creeLe, locale) }}</span>
            </div>
            <span class="fil__type" :class="`fil__type--${p.type}`">{{ t(`communautes.types.${p.type}`) }}</span>
          </div>
          <h2 class="fil__titre"><RouterLink :to="`/publications/${p.id}`" class="fil__lien">{{ p.titre }}</RouterLink></h2>
          <div class="fil__pied">
            <span class="fil__reponses">
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
              {{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}
            </span>
            <JBadgePreuve v-if="p.accepteeLe">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
            <span v-else-if="p.type === 'question' && p.nombreReponses === 0" class="fil__appel">{{ t('communautes.espace.premier') }}</span>
          </div>
        </li>
      </ul>
    </template>

    <template v-if="communaute" #contexte>
      <section class="carte contexte" aria-labelledby="apropos">
        <h2 id="apropos">{{ t('communautes.espace.apropos') }}</h2>
        <dl class="chiffres">
          <div>
            <dt>{{ t('communautes.chiffres.membres') }}</dt>
            <dd>{{ communaute.nombreMembres.toLocaleString(locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('communautes.chiffres.questionsOuvertes') }}</dt>
            <dd>{{ communaute.questionsOuvertes }}</dd>
          </div>
        </dl>
      </section>
      <section class="carte contexte" aria-labelledby="fonctionnement">
        <h2 id="fonctionnement">{{ t('communautes.fonctionnement.titre') }}</h2>
        <ol class="etapes">
          <li>{{ t('communautes.fonctionnement.poser') }}</li>
          <li>{{ t('communautes.fonctionnement.ia') }}</li>
          <li>
            {{ t('communautes.fonctionnement.accepter') }}
            <JBadgePreuve class="exemple">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
          </li>
        </ol>
      </section>
    </template>
  </EspaceCommunautes>
</template>

<style scoped>
/* Bannière : une couverture typographique (le nom en filigrane), le monogramme qui la chevauche. */
.banniere { overflow: hidden; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.banniere__couverture {
  height: 112px; overflow: hidden; position: relative;
  background: var(--action); color: var(--on-action);
}
.banniere--antenne .banniere__couverture { background: var(--ink); color: var(--bg); }
.banniere__couverture span {
  position: absolute; left: -0.06em; bottom: -0.32em; white-space: nowrap;
  font-family: var(--font-display); font-weight: 700; font-size: 7.5rem; line-height: 1; letter-spacing: -0.05em;
  opacity: 0.16;
}
.banniere__corps { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--space-3) var(--space-4); padding: var(--space-3) var(--space-6) 0; }
.banniere__monogramme {
  display: grid; place-items: center; width: 72px; height: 72px; flex-shrink: 0;
  position: relative; margin-top: -48px; border-radius: var(--radius-lg); border: 4px solid var(--surface);
  background: var(--action-soft); color: var(--action-ink);
  font-family: var(--font-display); font-weight: 700; font-size: 1.625rem; letter-spacing: -0.5px;
}
.banniere--antenne .banniere__monogramme { background: var(--surface-raised); color: var(--ink); }
.banniere__texte { flex: 1 1 14rem; min-width: 0; padding-bottom: var(--space-1); }
.banniere h1 { margin: 0; font-size: clamp(1.5rem, 3vw, 1.875rem); line-height: 1.15; letter-spacing: -0.5px; }
.banniere__meta { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.banniere__description { margin: var(--space-3) 0 0; padding: 0 var(--space-6) var(--space-6); color: var(--ink-muted); }

.compositeur { display: flex; gap: var(--space-3); align-items: center; padding: var(--space-4); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.compositeur__invite {
  flex: 1; min-height: 44px; text-align: start; padding: 0 var(--space-4);
  background: var(--surface-raised); border: 1px solid transparent; border-radius: var(--radius-pill);
  color: var(--ink-muted); cursor: text;
}
.compositeur__invite:hover { border-color: var(--line-strong); }
.compositeur--ouvert { display: grid; gap: var(--space-4); align-items: stretch; padding: var(--space-6); border-color: var(--action); }
.compositeur__tete { display: flex; gap: var(--space-3); align-items: center; flex-wrap: wrap; }
.compositeur__commandes { display: flex; gap: var(--space-2); justify-content: flex-end; }
.compositeur--rejoindre { justify-content: space-between; flex-wrap: wrap; background: var(--action-soft); border-color: transparent; }
.compositeur--rejoindre p { margin: 0; font-weight: 600; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }

.onglets { padding-top: var(--space-2); }

.fils { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.fil {
  position: relative; display: grid; gap: var(--space-3);
  padding: var(--space-4) var(--space-6) var(--space-4);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg);
  transition: border-color var(--motion-fast) var(--ease-out);
}
.fil:hover { border-color: var(--line-strong); }
.fil:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.fil__auteur { display: flex; align-items: center; gap: var(--space-3); }
.fil__auteur > div { display: grid; line-height: 1.3; min-width: 0; }
.fil__auteur strong { font-size: var(--type-body-sm-size); }
.fil__auteur span { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.fil__type { margin-inline-start: auto; padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-label-size); font-weight: 600; background: var(--surface-raised); color: var(--ink-muted); }
.fil__type--question { background: var(--action-soft); color: var(--ink); }
.fil__titre { margin: 0; font-size: 1.1875rem; line-height: 1.35; letter-spacing: -0.2px; }
/* Toute la carte mène au fil ; le titre reste le seul nom du lien. */
.fil__lien { color: var(--ink); text-decoration: none; }
.fil__lien::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.fil__lien:focus-visible { outline: none; }
.fil:hover .fil__lien { color: var(--action-ink); }
.fil__pied { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--line); }
.fil__reponses { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.fil__appel { color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; }
/* Orange = la preuve : un fil résolu porte un liseré orange. */
.fil--resolu { box-shadow: inset 4px 0 0 var(--proof); }

.contexte { padding: var(--space-6); display: grid; gap: var(--space-3); }
.contexte h2 { margin: 0; font-size: 1.0625rem; }
.chiffres { display: flex; gap: var(--space-6); margin: 0; }
.chiffres div { display: flex; flex-direction: column-reverse; }
.chiffres dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.75rem; font-weight: 700; line-height: 1.15; font-variant-numeric: tabular-nums; }
.etapes { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-3); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.etapes ::marker { color: var(--ink); font-weight: 700; }
.exemple { display: flex; width: fit-content; margin-top: var(--space-2); }
</style>
