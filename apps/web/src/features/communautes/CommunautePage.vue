<script setup lang="ts">
import { JBouton } from '../../design-system'
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
  if (response.ok) await charger()
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
  await router.push(`/publications/${data.id}`)
}

function filtrer(ouvertes: boolean) {
  void router.replace({ query: ouvertes ? { filtre: 'ouvertes' } : {} })
}
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
          <p v-if="communaute.pays" class="discret">
            {{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}
          </p>
          <p>{{ communaute.description }}</p>
          <p class="discret">{{ t('communautes.membres', { n: communaute.nombreMembres }, communaute.nombreMembres) }}</p>
        </div>
        <JBouton :variante="communaute.membre ? 'secondaire' : 'principal'" data-test="adhesion" :occupe="adhesionEnCours" @click="basculerAdhesion">
          {{ communaute.membre ? t('communautes.quitter') : t('communautes.rejoindre') }}
        </JBouton>
      </header>

      <form v-if="communaute.membre" class="formulaire" data-test="publier" @submit.prevent="publier">
        <h2>{{ t('communautes.poser') }}</h2>
        <label>
          {{ t('communautes.type') }}
          <select v-model="formulaire.type">
            <option value="question">{{ t('communautes.types.question') }}</option>
            <option value="discussion">{{ t('communautes.types.discussion') }}</option>
          </select>
        </label>
        <label>
          {{ t('communautes.champTitre') }}
          <input v-model="formulaire.titre" maxlength="150" required />
        </label>
        <label>
          {{ t('communautes.champContenu') }}
          <textarea v-model="formulaire.contenu" rows="5" maxlength="10000" required />
        </label>
        <p class="discret">{{ t('communautes.aideIa') }}</p>
        <p v-if="envoi === 'erreur'" role="alert">{{ t('communautes.erreurPublier') }}</p>
        <JBouton type="submit" class="principal" :desactive="!valide" :occupe="envoi === 'envoi'">{{ t('communautes.publier') }}</JBouton>
      </form>
      <p v-else class="discret">{{ t('communautes.rejoindrePourPublier') }}</p>

      <div class="filtres" role="group" :aria-label="t('communautes.filtre')">
        <button type="button" :aria-pressed="!seulementOuvertes" @click="filtrer(false)">{{ t('communautes.toutes') }}</button>
        <button type="button" :aria-pressed="seulementOuvertes" @click="filtrer(true)">{{ t('communautes.ouvertes') }}</button>
      </div>

      <p v-if="publications.length === 0" data-test="vide">{{ t('communautes.vide') }}</p>
      <ul v-else class="publications" data-test="publications">
        <li v-for="p in publications" :key="p.id">
          <span class="type">{{ t(`communautes.types.${p.type}`) }}</span>
          <span v-if="p.accepteeLe" class="acceptee">{{ t('communautes.reponseAcceptee') }}</span>
          <RouterLink :to="`/publications/${p.id}`">{{ p.titre }}</RouterLink>
          <span class="discret">
            {{ p.auteur?.nom ?? t('communautes.compteSupprime') }} · {{ ilYa(p.creeLe, locale) }} ·
            {{ t('communautes.reponses', { n: p.nombreReponses }, p.nombreReponses) }}
          </span>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.entete-communaute { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 1rem; }
.entete-communaute h1 { margin: 0 0 0.25rem; }
.formulaire { display: grid; gap: 0.75rem; background: var(--surface); border: 1px solid var(--line); border-radius: 0.5rem; padding: 1rem; margin: 1.5rem 0; }
.formulaire h2 { margin: 0; font-size: 1.125rem; }
.formulaire label { display: grid; gap: 0.25rem; font-weight: 600; }
.filtres { display: flex; gap: 0.5rem; margin: 1rem 0; }
.filtres button { border: 1px solid var(--line); background: var(--surface); color: inherit; border-radius: 999px; padding: 0.375rem 0.75rem; cursor: pointer; }
.filtres button[aria-pressed='true'] { background: var(--action); color: var(--on-action); border-color: var(--action); font-weight: 600; }
.publications { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.5rem; }
.publications li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem; background: var(--surface); border: 1px solid var(--line); border-radius: 0.5rem; padding: 0.75rem 1rem; }
.type { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--ink-muted); }
/* Orange = la preuve (tokens.json) : une réponse acceptée. Texte midnight sur orange. */
.acceptee { background: var(--proof); color: var(--on-action); font-size: 0.75rem; font-weight: 600; padding: 0 0.5rem; border-radius: 999px; }
.discret { color: var(--ink-muted); font-size: 0.875rem; }
.formulaire .principal { justify-self: start; }
</style>
