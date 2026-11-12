<script setup lang="ts">
import { JAvatar, JBadgePreuve, JBouton, JChamp, JFiltres } from '../../design-system'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'
import { ilYa } from '../vitrine/vitrine'

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
    <p v-if="etat === 'chargement' && !communaute" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'introuvable'">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="communaute">
      <p><RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink></p>
      <header class="entete-communaute">
        <div>
          <h1>{{ communaute.nom }}</h1>
          <p class="discret">
            {{ t('communautes.membres', { n: communaute.nombreMembres }, communaute.nombreMembres) }}
            <template v-if="communaute.pays"> · {{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}</template>
          </p>
          <p>{{ communaute.description }}</p>
        </div>
        <div class="commandes">
          <JBouton variante="secondaire" data-test="adhesion" :occupe="adhesionEnCours" @click="basculerAdhesion">
            {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
          </JBouton>
          <JBouton
            v-if="communaute.membre && !formulaireOuvert"
            data-test="ouvrir-formulaire"
            @click="formulaireOuvert = true"
          >
            {{ t('communautes.poser') }}
          </JBouton>
        </div>
      </header>

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
      <p v-else-if="!communaute.membre" class="discret">{{ t('communautes.rejoindrePourPublier') }}</p>

      <JFiltres
        v-model="filtre"
        :libelle="t('communautes.filtre')"
        :options="[
          { valeur: 'toutes', texte: t('communautes.toutes') },
          { valeur: 'ouvertes', texte: t('communautes.ouvertes') },
        ]"
      />

      <p v-if="publications.length === 0" class="carte vide" data-test="vide">{{ t('communautes.vide') }}</p>
      <ul v-else class="publications" data-test="publications">
        <li v-for="p in publications" :key="p.id" class="carte" :class="{ acceptee: p.accepteeLe }">
          <div class="ligne">
            <JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="32" />
            <span class="discret">
              <strong class="auteur">{{ p.auteur?.nom ?? t('communautes.compteSupprime') }}</strong> · {{ ilYa(p.creeLe, locale) }}
            </span>
            <span class="type">{{ t(`communautes.types.${p.type}`) }}</span>
          </div>
          <h3><RouterLink :to="`/publications/${p.id}`">{{ p.titre }}</RouterLink></h3>
          <div class="ligne">
            <JBadgePreuve v-if="p.accepteeLe">✓ {{ t('communautes.reponseAcceptee') }}</JBadgePreuve>
            <span class="discret">{{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}</span>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.entete-communaute { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: var(--space-4); margin-bottom: var(--space-6); }
.entete-communaute h1 { margin: 0 0 var(--space-1); }
.commandes { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.formulaire { display: grid; gap: var(--space-4); margin-bottom: var(--space-6); }
.formulaire h2 { margin: 0; }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }
.j-filtres { margin-bottom: var(--space-4); }
.publications { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-3); }
.publications li { display: grid; gap: var(--space-2); padding: var(--space-4) var(--space-6); }
/* Orange = la preuve : une question qui a sa réponse acceptée porte un liseré orange. */
.publications li.acceptee { border-inline-start: 4px solid var(--proof); }
.publications h3 { margin: 0; }
.publications h3 a { color: var(--ink); text-decoration: none; }
.publications h3 a:hover { color: var(--action-ink); text-decoration: underline; }
.ligne { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.auteur { color: var(--ink); }
.type { margin-inline-start: auto; padding: 0 var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-pill); font-size: var(--type-label-size); color: var(--ink-muted); }
.vide { text-align: center; color: var(--ink-muted); }
.discret { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
