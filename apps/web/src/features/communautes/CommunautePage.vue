<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JFiltres, JSquelette } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'
import { ilYa } from '../vitrine/vitrine'
import { monogramme } from './communautes'

type Communaute = components['schemas']['Communaute']
type PublicationResume = components['schemas']['PublicationResume']

const { t, locale } = useI18n()
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
  <section>
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
      <p class="retour"><RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink></p>
      <header class="page-tete">
        <div class="page-tete__texte titre-communaute">
          <span class="monogramme" :class="{ 'monogramme--antenne': communaute.pays }" aria-hidden="true">{{ monogramme(communaute.nom) }}</span>
          <div>
            <h1>{{ communaute.nom }}</h1>
            <p>{{ communaute.description }}</p>
          </div>
        </div>
        <div class="page-tete__actions">
          <JBouton variante="secondaire" data-test="adhesion" :occupe="adhesionEnCours" @click="basculerAdhesion">
            {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
          </JBouton>
          <JBouton v-if="communaute.membre && !formulaireOuvert" data-test="ouvrir-formulaire" @click="formulaireOuvert = true">
            {{ t('communautes.poser') }}
          </JBouton>
        </div>
      </header>

      <div class="mise-en-page">
        <div class="fil">
          <form v-if="communaute.membre && formulaireOuvert" class="carte formulaire" data-test="publier" @submit.prevent="publier">
            <h2>{{ t('communautes.poser') }}</h2>
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
            <div class="commandes">
              <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'">{{ t('communautes.publier') }}</JBouton>
              <JBouton variante="discret" @click="formulaireOuvert = false">{{ t('communautes.annuler') }}</JBouton>
            </div>
          </form>
          <div v-else-if="!communaute.membre" class="invitation">
            <p>{{ t('communautes.rejoindrePourPublier') }}</p>
            <JBouton :occupe="adhesionEnCours" @click="basculerAdhesion">{{ t('communautes.rejoindre') }}</JBouton>
          </div>

          <JFiltres
            v-model="filtre"
            :libelle="t('communautes.filtre')"
            :options="[
              { valeur: 'toutes', texte: t('communautes.toutes') },
              { valeur: 'ouvertes', texte: t('communautes.ouvertes') },
            ]"
          />

          <JSquelette v-if="etat === 'chargement'" :nombre="3" />
          <div v-else-if="publications.length === 0" class="etat-vide">
            <p data-test="vide">{{ t('communautes.vide') }}</p>
          </div>
          <ul v-else class="publications" data-test="publications">
            <li v-for="p in publications" :key="p.id" :class="{ acceptee: p.accepteeLe }">
              <div class="ligne">
                <JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="32" />
                <span class="discret">
                  <strong class="auteur">{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</strong> · {{ ilYa(p.creeLe, locale) }}
                </span>
                <span class="type">{{ t(`communautes.types.${p.type}`) }}</span>
              </div>
              <h3><RouterLink :to="`/publications/${p.id}`" class="lien-publication">{{ p.titre }}</RouterLink></h3>
              <div class="ligne">
                <JBadgePreuve v-if="p.accepteeLe">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
                <span class="discret">💬 {{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}</span>
              </div>
            </li>
          </ul>
        </div>

        <aside class="apropos">
          <dl class="carte chiffres">
            <div>
              <dt>{{ t('communautes.chiffres.membres') }}</dt>
              <dd>{{ communaute.nombreMembres.toLocaleString(locale) }}</dd>
            </div>
            <div>
              <dt>{{ t('communautes.chiffres.questionsOuvertes') }}</dt>
              <dd>{{ communaute.questionsOuvertes }}</dd>
            </div>
            <p v-if="communaute.pays" class="antenne">📍 {{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}</p>
          </dl>
          <section class="carte fonctionnement" aria-labelledby="fonctionnement">
            <h2 id="fonctionnement">{{ t('communautes.fonctionnement.titre') }}</h2>
            <ol>
              <li>{{ t('communautes.fonctionnement.poser') }}</li>
              <li>{{ t('communautes.fonctionnement.ia') }}</li>
              <li>
                {{ t('communautes.fonctionnement.accepter') }}
                <JBadgePreuve class="exemple">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
              </li>
            </ol>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.retour { margin-bottom: var(--space-4); }
.titre-communaute { display: flex; gap: var(--space-4); align-items: flex-start; }
.monogramme {
  flex-shrink: 0; display: grid; place-items: center;
  width: 64px; height: 64px; border-radius: var(--radius-lg);
  background: var(--action-soft); color: var(--action-ink);
  font-family: var(--font-display); font-weight: 700; font-size: 1.5rem; letter-spacing: -0.5px;
}
.monogramme--antenne { background: var(--surface-raised); color: var(--ink); }

.mise-en-page { display: grid; gap: var(--space-8); grid-template-columns: minmax(0, 1fr) 18rem; align-items: start; }
@media (max-width: 900px) { .mise-en-page { grid-template-columns: 1fr; } .apropos { order: -1; } .fonctionnement { display: none; } }
.fil { display: grid; gap: var(--space-4); }

.formulaire { display: grid; gap: var(--space-4); }
.formulaire h2 { margin: 0; font-size: var(--type-h3-size); }
.commandes { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }
.invitation { display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); align-items: center; justify-content: space-between; padding: var(--space-4) var(--space-6); background: var(--action-soft); border-radius: var(--radius-md); }
.invitation p { margin: 0; font-weight: 600; }

.publications { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-3); }
.publications li {
  position: relative; display: grid; gap: var(--space-2);
  padding: var(--space-4) var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md);
  transition: border-color var(--motion-fast) var(--ease-out);
}
.publications li:hover { border-color: var(--line-strong); }
.publications li:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
/* Orange = la preuve : une question qui a sa réponse acceptée porte un liseré orange. */
.publications li.acceptee { border-inline-start: 4px solid var(--proof); }
.publications h3 { margin: 0; font-size: 1.125rem; line-height: 1.35; }
/* Toute la ligne mène à la question ; le titre reste le seul nom du lien. */
.lien-publication { color: var(--ink); text-decoration: none; }
.lien-publication::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.lien-publication:focus-visible { outline: none; }
.publications li:hover .lien-publication { color: var(--action-ink); }
.ligne { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.auteur { color: var(--ink); }
.type { margin-inline-start: auto; padding: 0 var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-pill); font-size: var(--type-label-size); color: var(--ink-muted); }
.discret { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.apropos { display: grid; gap: var(--space-4); position: sticky; top: calc(64px + var(--space-6)); }
@media (max-width: 900px) { .apropos { position: static; } }
.chiffres { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-8); margin: 0; padding: var(--space-6); }
.chiffres div { display: flex; flex-direction: column-reverse; }
.chiffres dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.75rem; font-weight: 700; line-height: 1.15; font-variant-numeric: tabular-nums; }
.antenne { flex-basis: 100%; margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.fonctionnement { padding: var(--space-6); }
.fonctionnement h2 { font-size: 1.0625rem; margin-bottom: var(--space-3); }
.fonctionnement ol { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-3); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.fonctionnement ol ::marker { color: var(--ink); font-weight: 700; }
.exemple { display: flex; width: fit-content; margin-top: var(--space-2); }
</style>
