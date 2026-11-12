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
        <div class="banniere__couverture" aria-hidden="true" />
        <div class="banniere__corps">
          <span class="banniere__monogramme" aria-hidden="true">{{ monogramme(communaute.nom) }}</span>
          <div class="banniere__texte">
            <h1>{{ communaute.nom }}</h1>
            <p class="banniere__meta">
              {{ t('communautes.membres', { n: communaute.nombreMembres }, communaute.nombreMembres) }}
              <template v-if="communaute.pays"> · {{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}</template>
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

      <!-- Un seul fil continu : publier en haut, les onglets, puis les discussions séparées par une ligne. -->
      <section class="flux" :aria-label="t('communautes.espace.fil')">
        <Transition name="deplie" mode="out-in">
          <form v-if="communaute.membre && formulaireOuvert" key="ouvert" class="compositeur compositeur--ouvert" data-test="publier" @submit.prevent="publier">
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
          <div v-else-if="communaute.membre" key="ferme" class="compositeur">
            <JAvatar v-if="moi" :nom="moi.nom" :url="moi.avatarUrl" :taille="40" />
            <button type="button" class="compositeur__invite" data-test="ouvrir-formulaire" @click="formulaireOuvert = true">
              {{ t('communautes.espace.invite') }}
            </button>
          </div>
          <div v-else key="rejoindre" class="compositeur compositeur--rejoindre">
            <p>{{ t('communautes.rejoindrePourPublier') }}</p>
            <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
          </div>
        </Transition>

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

        <JSquelette v-if="etat === 'chargement'" :nombre="3" class="flux__squelette" />
        <p v-else-if="publications.length === 0" class="flux__vide" data-test="vide">{{ t('communautes.vide') }}</p>
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

    <template v-if="communaute" #contexte>
      <section class="contexte" aria-labelledby="apropos">
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
/* Bannière : une couverture douce (une trame de points sur le bleu clair de la charte), pas d'aplat criard. */
.banniere { overflow: hidden; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.banniere__couverture {
  height: 96px;
  background:
    radial-gradient(color-mix(in srgb, var(--action-ink) 28%, transparent) 1px, transparent 1.6px) 0 0 / 14px 14px,
    linear-gradient(120deg, color-mix(in srgb, var(--action) 30%, var(--action-soft)), var(--action-soft) 70%);
}
.banniere--antenne .banniere__couverture {
  background:
    radial-gradient(color-mix(in srgb, var(--ink) 18%, transparent) 1px, transparent 1.6px) 0 0 / 14px 14px,
    var(--surface-raised);
}
.banniere__corps { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--space-3) var(--space-4); padding: var(--space-3) var(--space-6) 0; }
.banniere__monogramme {
  position: relative; margin-top: -44px;
  display: grid; place-items: center; width: 72px; height: 72px; flex-shrink: 0;
  border-radius: var(--radius-lg); border: 4px solid var(--surface);
  background: var(--action); color: var(--on-action);
  font-family: var(--font-display); font-weight: 700; font-size: 1.625rem; letter-spacing: -0.5px;
}
.banniere--antenne .banniere__monogramme { background: var(--ink); color: var(--bg); }
.banniere__texte { flex: 1 1 14rem; min-width: 0; padding-bottom: var(--space-1); }
.banniere h1 { margin: 0; font-size: clamp(1.5rem, 3vw, 1.75rem); line-height: 1.15; letter-spacing: -0.5px; }
.banniere__meta { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.banniere__description { margin: var(--space-3) 0 0; padding: 0 var(--space-6) var(--space-6); color: var(--ink-muted); }

/* Le fil : un seul panneau, des lignes de séparation, comme un fil d'actualité. */
.flux { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); overflow: hidden; }
.compositeur { display: flex; gap: var(--space-3); align-items: center; padding: var(--space-4) var(--space-6); }
.compositeur__invite {
  flex: 1; min-height: 44px; text-align: start; padding: 0 var(--space-4);
  background: var(--surface-raised); border: 1px solid transparent; border-radius: var(--radius-pill);
  color: var(--ink-muted); cursor: text;
  transition: border-color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.compositeur__invite:hover { border-color: var(--line-strong); }
.compositeur--ouvert { display: grid; gap: var(--space-4); align-items: stretch; padding: var(--space-6); background: color-mix(in srgb, var(--action-soft) 45%, var(--surface)); }
.compositeur__tete { display: flex; gap: var(--space-3); align-items: center; flex-wrap: wrap; }
.compositeur__commandes { display: flex; gap: var(--space-2); justify-content: flex-end; }
.compositeur--rejoindre { justify-content: space-between; flex-wrap: wrap; background: var(--action-soft); }
.compositeur--rejoindre p { margin: 0; font-weight: 600; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }

.flux__onglets { padding: 0 var(--space-6); border-top: 1px solid var(--line); }
.flux__squelette { padding: var(--space-4); }
.flux__squelette :deep(.j-squelette__bloc) { border: 0; padding: var(--space-4); }
.flux__vide { margin: 0; padding: var(--space-12) var(--space-6); text-align: center; color: var(--ink-muted); }

.fils { list-style: none; margin: 0; padding: 0; }
.fil {
  position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  transition: background var(--motion-fast) var(--ease-out);
}
.fil + .fil { border-top: 1px solid var(--line); }
.fil:hover { background: color-mix(in srgb, var(--surface-raised) 60%, transparent); }
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
.deplie-enter-active, .deplie-leave-active { transition: opacity var(--motion-fast) var(--ease-out); }
.deplie-enter-from, .deplie-leave-to { opacity: 0; }

.contexte { display: grid; gap: var(--space-3); padding: var(--space-6); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.contexte h2 { margin: 0; font-size: 1rem; }
.contexte h3 { margin: var(--space-3) 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); font-size: 1rem; }
.chiffres { display: flex; gap: var(--space-6); margin: 0; }
.chiffres div { display: flex; flex-direction: column-reverse; }
.chiffres dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.625rem; font-weight: 700; line-height: 1.15; font-variant-numeric: tabular-nums; }
.etapes { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.etapes ::marker { color: var(--ink-muted); font-weight: 700; }
</style>
