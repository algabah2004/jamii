<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JDeplier, JFiltres, JIllustration, JSquelette } from '../../design-system'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { ilYa } from '../vitrine/vitrine'
import { useSession } from '../../session'
import EspaceCommunautes from './EspaceCommunautes.vue'
import SalonsCommunaute from './SalonsCommunaute.vue'

type Communaute = components['schemas']['Communaute']
type PublicationResume = components['schemas']['PublicationResume']

/*
 * Une communauté, comme une messagerie d'équipe : on arrive dans un salon (`# général` par défaut,
 * `?salon=entraide` pour un autre), et le forum des questions s'ouvre avec `?voir=questions`.
 * L'adresse se partage telle quelle.
 */
const { t, locale } = useI18n()
const { moi } = useSession()
const route = useRoute()
const router = useRouter()

const slug = computed(() => String(route.params.slug))
const vue = computed(() => (route.query.voir === 'questions' ? 'questions' : 'salon'))
const salon = computed(() => (typeof route.query.salon === 'string' && route.query.salon ? route.query.salon : 'general'))
const actif = computed(() => (vue.value === 'questions' ? 'questions' : salon.value))
const seulementOuvertes = computed(() => route.query.filtre === 'ouvertes')

const communaute = ref<Communaute | null>(null)
const publications = ref<PublicationResume[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const etatFil = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

/** `silencieux` : après « Rejoindre », on relit sans effacer l'écran (le salon reste ouvert). */
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
      params: { path: { slug: slug.value }, query: { sansReponseAcceptee: seulementOuvertes.value || undefined } },
    })
    if (!data) throw new Error('fil indisponible')
    publications.value = data.elements ?? []
    etatFil.value = 'pret'
  } catch {
    etatFil.value = 'indisponible'
  }
}
watch(slug, () => void chargerCommunaute(), { immediate: true })
watch([slug, seulementOuvertes, vue], () => {
  if (vue.value === 'questions') void chargerFil()
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
  formulaireOuvert.value = vue.value === 'questions' && !communaute.value.membre
  await chargerCommunaute(true)
  cleSalons.value++
}
/** Après « Rejoindre », le salon se recharge pour recevoir l'adresse de l'appel et la zone d'écriture. */
const cleSalons = ref(0)

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
  set: (v) => void router.replace({ query: v === 'ouvertes' ? { voir: 'questions', filtre: 'ouvertes' } : { voir: 'questions' } }),
})

/** Les échanges d'abord : le formulaire s'ouvre à la demande (et dès qu'on rejoint). */
const formulaireOuvert = ref(false)
const zoneFormulaire = ref<HTMLElement | null>(null)
watch(formulaireOuvert, (ouvert) => {
  if (ouvert) void nextTick(() => zoneFormulaire.value?.querySelector('input')?.focus({ preventScroll: true }))
})
</script>

<template>
  <EspaceCommunautes :actuelle="etat === 'introuvable' ? undefined : slug" :actif="actif" :communaute="communaute">
    <JSquelette v-if="etat === 'chargement' && !communaute" :nombre="4" class="attente" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="chargerCommunaute()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="communaute">
      <!-- Un salon en direct, sur toute la hauteur. -->
      <SalonsCommunaute
        v-if="vue === 'salon'"
        :key="`${slug}-${cleSalons}`"
        :slug="slug"
        :salon="salon"
        :membre="communaute.membre"
        :adhesion-en-cours="adhesionEnCours"
        @rejoindre="basculerAdhesion"
      />

      <!-- Le forum des questions : l'IA propose une réponse, un pair la valide. -->
      <template v-else>
        <header class="tete-vue">
          <h1 class="tete-vue__nom">
            <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
            {{ t('communautes.salons.questions') }}
          </h1>
          <p class="tete-vue__sujet">{{ t('communautes.salons.questionsSujet') }}</p>
          <JBouton
            :variante="communaute.membre ? 'discret' : 'principal'"
            data-test="adhesion"
            :occupe="adhesionEnCours"
            @click="basculerAdhesion"
          >
            {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
          </JBouton>
        </header>

        <section class="flux" :aria-label="t('communautes.espace.fil')">
          <JDeplier>
            <div v-if="communaute.membre && formulaireOuvert" ref="zoneFormulaire">
              <form class="compositeur compositeur--ouvert" data-test="publier" @submit.prevent="publier">
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
            </div>
          </JDeplier>
          <JDeplier>
            <div v-if="communaute.membre && !formulaireOuvert">
              <div class="compositeur">
                <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
                <button type="button" class="compositeur__invite" data-test="ouvrir-formulaire" @click="formulaireOuvert = true">
                  {{ t('communautes.espace.invite') }}
                </button>
              </div>
            </div>
          </JDeplier>
          <div v-if="!communaute.membre" class="compositeur compositeur--rejoindre">
            <p>{{ t('communautes.rejoindrePourPublier') }}</p>
            <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
          </div>

          <JFiltres
            v-model="filtre"
            variante="onglets"
            class="flux__onglets"
            :libelle="t('communautes.filtre')"
            :options="[
              { valeur: 'toutes', texte: t('communautes.toutes') },
              { valeur: 'ouvertes', texte: t('communautes.ouvertes') },
            ]"
          />

          <JSquelette v-if="etatFil === 'chargement'" :nombre="3" class="flux__squelette" />
          <div v-else-if="etatFil === 'indisponible'" role="alert" class="flux__vide">
            <p>{{ t('etat.indisponible') }}</p>
            <JBouton variante="secondaire" @click="chargerFil">{{ t('etat.reessayer') }}</JBouton>
          </div>
          <div v-else-if="publications.length === 0" class="flux__vide">
            <JIllustration nom="communaute" />
            <p data-test="vide">{{ t('communautes.vide') }}</p>
          </div>
          <TransitionGroup v-else tag="ul" name="fil" class="fils" data-test="publications" appear>
            <li v-for="(p, i) in publications" :key="p.id" class="fil" :style="{ '--rang': i }">
              <JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="40" />
              <div class="fil__corps">
                <p class="fil__meta">
                  <strong>{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</strong>
                  <span>{{ ilYa(p.creeLe, locale) }} · {{ t(`communautes.types.${p.type}`) }}</span>
                </p>
                <h2 class="fil__titre"><RouterLink :to="`/publications/${p.id}`" class="fil__lien">{{ p.titre }}</RouterLink></h2>
                <p class="fil__pied">
                  <span class="fil__reponses">
                    <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h11A1.5 1.5 0 0 1 17 4.5v8a1.5 1.5 0 0 1-1.5 1.5H8l-4 3v-3h.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
                    {{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}
                  </span>
                  <JBadgePreuve v-if="p.accepteeLe">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
                  <span v-else-if="p.type === 'question' && p.nombreReponses === 0" class="fil__appel">{{ t('communautes.espace.premier') }}</span>
                </p>
              </div>
            </li>
          </TransitionGroup>
        </section>
      </template>
    </template>

    <template v-if="communaute" #contexte>
      <section class="contexte" aria-labelledby="apropos">
        <h2 id="apropos">{{ t('communautes.espace.apropos') }}</h2>
        <p class="contexte__description">{{ communaute.description }}</p>
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
        <JBouton
          v-if="communaute.membre && vue === 'salon'"
          variante="secondaire"
          data-test="quitter"
          :occupe="adhesionEnCours"
          @click="basculerAdhesion"
        >
          {{ t('communautes.quitter') }}
        </JBouton>
        <h3>{{ t('communautes.fonctionnement.titre') }}</h3>
        <ol class="etapes">
          <li>{{ t('communautes.fonctionnement.poser') }}</li>
          <li>{{ t('communautes.fonctionnement.ia') }}</li>
          <li>{{ t('communautes.fonctionnement.accepter') }}</li>
        </ol>
      </section>
    </template>
  </EspaceCommunautes>
</template>

<style scoped>
.attente { padding: var(--space-6); }
.etat-vide { margin: var(--space-6); }

/* L'en-tête d'une vue : le même que celui d'un salon, pour que le forum en soit un aussi. */
.tete-vue {
  position: sticky; top: 0; z-index: 1;
  display: flex; align-items: center; gap: var(--space-3); min-height: 56px; padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line); background: var(--surface);
}
.tete-vue__nom { display: flex; align-items: center; gap: var(--space-2); margin: 0; flex-shrink: 0; font-size: 1.0625rem; font-family: var(--font-text); font-weight: 700; }
.tete-vue__nom svg { color: var(--ink-muted); }
.tete-vue__sujet {
  margin: 0; flex: 1; min-width: 0; padding-inline-start: var(--space-3); border-inline-start: 1px solid var(--line);
  color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.compositeur { display: flex; gap: var(--space-3); align-items: center; padding: var(--space-4); }
.compositeur__invite {
  flex: 1; min-height: 44px; text-align: start; padding: 0 var(--space-4);
  background: var(--surface-raised); border: 1px solid transparent; border-radius: var(--radius-md);
  color: var(--ink-muted); font: inherit; cursor: text;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.compositeur__invite:hover { border-color: var(--line-strong); }
.compositeur--ouvert { display: grid; gap: var(--space-4); align-items: stretch; padding: var(--space-6) var(--space-4); background: color-mix(in srgb, var(--action-soft) 45%, var(--surface)); }
.compositeur__tete { display: flex; gap: var(--space-3); align-items: center; flex-wrap: wrap; }
.compositeur__commandes { display: flex; gap: var(--space-2); justify-content: flex-end; }
.compositeur--rejoindre { justify-content: space-between; flex-wrap: wrap; margin: var(--space-4); border-radius: var(--radius-md); background: var(--action-soft); }
.compositeur--rejoindre p { margin: 0; font-weight: 600; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }

.flux__onglets { padding: 0 var(--space-4); border-top: 1px solid var(--line); }
.flux__squelette { padding: var(--space-4); }
.flux__squelette :deep(.j-squelette__bloc) { border: 0; padding: var(--space-4); }
.flux__vide { display: grid; justify-items: center; gap: var(--space-3); padding: var(--space-8) var(--space-6) var(--space-12); text-align: center; color: var(--ink-muted); }
.flux__vide p { margin: 0; }
.flux__vide .j-illustration { width: 14rem; }

.fils { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--line); }
.fil {
  position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3);
  padding: var(--space-4);
  transition: background var(--motion-fast) var(--ease-out);
}
.fil + .fil { border-top: 1px solid var(--line); }
.fil:hover { background: color-mix(in srgb, var(--ink) 3%, transparent); }
.fil:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: -2px; }
.fil__corps { display: grid; gap: var(--space-1); min-width: 0; }
.fil__meta { margin: 0; display: flex; flex-wrap: wrap; gap: 0 var(--space-2); align-items: baseline; font-size: var(--type-body-sm-size); }
.fil__meta span { color: var(--ink-muted); }
.fil__titre { margin: 0; font-size: 1.0625rem; line-height: 1.4; font-weight: 600; font-family: var(--font-text); }
/* Toute la ligne mène au fil ; le titre reste le seul nom du lien. */
.fil__lien { color: var(--ink); text-decoration: none; }
.fil__lien::after { content: ''; position: absolute; inset: 0; }
.fil__lien:focus-visible { outline: none; }
.fil:hover .fil__lien { color: var(--action-ink); }
.fil__pied { margin: var(--space-1) 0 0; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); }
.fil__reponses { display: inline-flex; align-items: center; gap: var(--space-1); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.fil__appel { color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; }

/* Les discussions arrivent une à une, très vite (rien si le mouvement est réduit). */
.fil-enter-active { transition: opacity var(--motion-base) var(--ease-out), transform var(--motion-base) var(--ease-out); transition-delay: calc(var(--rang) * 30ms); }
.fil-enter-from { opacity: 0; transform: translateY(6px); }
@media (prefers-reduced-motion: reduce) { .fil-enter-active { transition: none; } }

.contexte { display: grid; gap: var(--space-3); }
.contexte h2 { margin: 0; font-size: 1rem; }
.contexte h3 { margin: var(--space-2) 0 0; font-size: 0.9375rem; }
.contexte__description { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.chiffres { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-2); }
.chiffres div { padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface-raised); }
.chiffres dt { color: var(--ink-muted); font-size: 0.75rem; }
.chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.375rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.etapes { margin: 0; padding-inline-start: var(--space-4); display: grid; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

@media (max-width: 760px) {
  .tete-vue__sujet { display: none; }
}
</style>
