<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton } from '../../design-system'
import { useSession } from '../../session'
import {
  annoncer,
  formaterDate,
  lireDiscussion,
  ouvrirEspace,
  questionsOuvertes,
  retirerAnnonce,
  type Annonce,
  type Hackathon,
  type QuestionOuverte,
} from './hackathons'

/*
 * La discussion d'un hackathon (contracts/hackathons.yaml, tag « discussion ») :
 * - les annonces de l'organisateur, lues par tous, même sans compte ;
 * - l'espace d'échange : une communauté liée, où les participants posent leurs questions (avec une réponse acceptée)
 *   et discutent dans des salons. On réutilise les communautés au lieu de refaire un forum : modération, salons, appels.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const TEXTE_MAX = 2000
const annonces = ref<Annonce[]>([])
const communaute = ref<string | null>(null)
const questions = ref<QuestionOuverte[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const texte = ref('')
const envoi = ref(false)
const ouverture = ref(false)
const erreur = ref<string | null>(null)
const toutes = ref(false)

const organisateur = computed(() => props.hackathon.moi.role === 'organisateur')
const connecte = computed(() => Boolean(moi.value))
const visibles = computed(() => (toutes.value ? annonces.value : annonces.value.slice(0, 3)))
const peutAnnoncer = computed(() => organisateur.value && props.hackathon.statut !== 'annule')

async function charger() {
  try {
    const d = await lireDiscussion(props.hackathon.id)
    annonces.value = d.annonces
    communaute.value = d.communaute
    etat.value = 'pret'
    questions.value = d.communaute && connecte.value ? await questionsOuvertes(d.communaute).catch(() => []) : []
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => [props.hackathon.id, connecte.value], () => void charger(), { immediate: true })

function message(code: string | null) {
  // 429 : `limite_atteinte` côté hackathons, `trop_de_requetes` côté communautés ; une seule phrase.
  const cle = code === 'limite_atteinte' ? 'trop_de_requetes' : code
  const connus = ['etat_invalide', 'trop_de_requetes']
  return t(`hackathons.discussion.erreurs.${cle && connus.includes(cle) ? cle : 'autre'}`)
}

async function publier() {
  const propre = texte.value.trim()
  if (!propre || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await annoncer(props.hackathon.id, propre)
    if (r.ok) {
      annonces.value = [r.valeur, ...annonces.value]
      texte.value = ''
    } else erreur.value = message(r.code)
  } catch {
    erreur.value = message(null)
  } finally {
    envoi.value = false
  }
}

async function retirer(a: Annonce) {
  if (await retirerAnnonce(props.hackathon.id, a.id).catch(() => false)) {
    annonces.value = annonces.value.filter((x) => x.id !== a.id)
  }
}

async function ouvrir() {
  if (ouverture.value) return
  ouverture.value = true
  erreur.value = null
  try {
    const r = await ouvrirEspace(props.hackathon)
    if (r.ok) communaute.value = r.valeur
    else erreur.value = message(r.code)
  } catch {
    erreur.value = message(null)
  } finally {
    ouverture.value = false
  }
}
</script>

<template>
  <section class="carte discussion" aria-labelledby="discussion-titre" data-test="discussion">
    <h2 id="discussion-titre">{{ t('hackathons.discussion.titre') }}</h2>
    <p v-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.discussion.indisponible') }}</p>
    <template v-else-if="etat === 'pret'">
      <div class="bloc">
        <h3>{{ t('hackathons.discussion.annonces') }}</h3>
        <form v-if="peutAnnoncer" class="formulaire" data-test="form-annonce" @submit.prevent="publier">
          <label for="annonce-texte" class="libelle">{{ t('hackathons.discussion.nouvelle') }}</label>
          <textarea id="annonce-texte" v-model="texte" rows="3" :maxlength="TEXTE_MAX" :placeholder="t('hackathons.discussion.exemple')" />
          <div class="ligne">
            <span class="texte-discret">{{ t('hackathons.discussion.aideAnnonce') }}</span>
            <JBouton type="submit" variante="secondaire" :occupe="envoi" :desactive="!texte.trim()" data-test="publier-annonce">
              {{ t('hackathons.discussion.publier') }}
            </JBouton>
          </div>
        </form>
        <p v-if="!annonces.length" class="texte-discret" data-test="aucune-annonce">{{ t('hackathons.discussion.aucuneAnnonce') }}</p>
        <ol v-else class="annonces">
          <li v-for="a in visibles" :key="a.id" data-test="annonce">
            <p class="annonce__tete">
              <span class="personne"><JAvatar :nom="a.auteur.nom" :url="a.auteur.avatarUrl" :taille="24" /> {{ a.auteur.nom }}</span>
              <span class="texte-discret">{{ formaterDate(a.publieeLe, locale) }}</span>
            </p>
            <!-- Texte libre : affiché échappé, avec ses retours à la ligne. -->
            <p class="annonce__texte">{{ a.texte }}</p>
            <JBouton v-if="organisateur" variante="discret" data-test="retirer-annonce" @click="retirer(a)">{{ t('hackathons.discussion.retirer') }}</JBouton>
          </li>
        </ol>
        <div v-if="annonces.length > 3">
          <JBouton variante="discret" :aria-expanded="toutes" data-test="toutes-annonces" @click="toutes = !toutes">
            {{ toutes ? t('hackathons.discussion.moins') : t('hackathons.discussion.toutes', { n: annonces.length }) }}
          </JBouton>
        </div>
      </div>

      <div class="bloc espace" data-test="espace">
        <h3>{{ t('hackathons.discussion.espace') }}</h3>
        <template v-if="communaute">
          <p>{{ t('hackathons.discussion.aideEspace') }}</p>
          <ul v-if="questions.length" class="questions" :aria-label="t('hackathons.discussion.questionsOuvertes')" data-test="questions">
            <li v-for="q in questions" :key="q.id">
              <RouterLink :to="`/publications/${q.id}`">{{ q.titre }}</RouterLink>
              <span class="texte-discret"> · {{ t('hackathons.discussion.reponses', { n: q.nombreReponses }, q.nombreReponses) }}</span>
            </li>
          </ul>
          <div class="liens">
            <RouterLink :to="`/communautes/${communaute}?onglet=ouvertes`" class="j-bouton j-bouton--principal" data-test="poser-question">
              {{ t('hackathons.discussion.poser') }}
            </RouterLink>
            <RouterLink :to="`/communautes/${communaute}?salon=general`" class="j-bouton j-bouton--secondaire" data-test="salons">
              {{ t('hackathons.discussion.salons') }}
            </RouterLink>
          </div>
          <p v-if="!connecte" class="texte-discret">{{ t('hackathons.discussion.connexion') }}</p>
        </template>
        <template v-else-if="organisateur">
          <p>{{ t('hackathons.discussion.aideOuvrir') }}</p>
          <div><JBouton :occupe="ouverture" data-test="ouvrir-espace" @click="ouvrir">{{ t('hackathons.discussion.ouvrir') }}</JBouton></div>
        </template>
        <p v-else class="texte-discret" data-test="pas-d-espace">{{ t('hackathons.discussion.pasEncore') }}</p>
      </div>
      <p v-if="erreur" role="alert" class="erreur" data-test="erreur-discussion">{{ erreur }}</p>
    </template>
  </section>
</template>

<style scoped>
.discussion { display: grid; gap: var(--space-6); padding: var(--space-8); }
.discussion h2, .discussion h3, .discussion p { margin: 0; }
.discussion h2 { font-size: 1.25rem; }
.discussion h3 { font-size: 1.0625rem; }
.bloc { display: grid; gap: var(--space-3); }
.espace { padding-block-start: var(--space-6); border-block-start: 1px solid var(--line-strong); }
.formulaire { display: grid; gap: var(--space-2); }
.libelle { font-weight: 600; }
textarea {
  inline-size: 100%; padding: var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--surface); color: var(--ink); font: inherit; resize: vertical;
}
textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ligne { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.annonces { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.annonces li {
  display: grid; gap: var(--space-2); justify-items: start; padding: var(--space-4); border-radius: var(--radius-md);
  border-inline-start: 3px solid var(--action); background: color-mix(in srgb, var(--action) 6%, transparent);
}
.annonce__tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; inline-size: 100%; }
.annonce__texte { white-space: pre-line; }
.personne { display: inline-flex; gap: var(--space-2); align-items: center; font-weight: 600; }
.questions { margin: 0; padding-inline-start: var(--space-6); display: grid; gap: var(--space-2); }
.liens { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.erreur { color: var(--danger); font-weight: 600; }
</style>
