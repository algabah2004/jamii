<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import { JBouton, JChamp, JIllustration, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import CarteCompetences from './CarteCompetences.vue'
import FicheProfil from './FicheProfil.vue'
import PartagerCarte from './PartagerCarte.vue'
import ReglagePresence from '../reseau/ReglagePresence.vue'
import AbonnesProfil from '../reseau/AbonnesProfil.vue'
import {
  DISPONIBILITES,
  LANGUES_PROPOSEES,
  lireCompetences,
  nomLangue,
  PAYS_AFRIQUE,
  type Disponibilite,
  type MonProfil,
} from './profils'

const { t, locale } = useI18n()
const { moi } = useSession()

const profil = ref<MonProfil | null>(null)
const etat = ref<'chargement' | 'aucun' | 'pret' | 'indisponible'>('chargement')
const occupe = ref(false)
const message = ref<string | null>(null)
let minuterie: ReturnType<typeof setTimeout> | undefined

/** Pendant la génération, on relit le profil toutes les 2 secondes, 2 minutes au plus (l'API abandonne après). */
const ATTENTE_MS = 2000
const ESSAIS_MAX = 60

async function charger(essai = 0) {
  clearTimeout(minuterie)
  try {
    const { data, response } = await api.GET('/api/me/profil')
    if (response.status === 404) {
      profil.value = null
      etat.value = 'aucun'
      return
    }
    if (!data) throw new Error('profil indisponible')
    profil.value = data
    etat.value = data.langages.length || data.generation.statut === 'terminee' ? 'pret' : 'aucun'
    if (data.generation.statut === 'en_cours' && essai < ESSAIS_MAX) {
      minuterie = setTimeout(() => void charger(essai + 1), ATTENTE_MS)
    }
  } catch {
    etat.value = 'indisponible'
  }
}
onBeforeUnmount(() => clearTimeout(minuterie))
void charger()

const enCours = computed(() => profil.value?.generation.statut === 'en_cours')
const echec = computed(() => (profil.value?.generation.statut === 'echec' ? profil.value.generation.erreur : null))
const sansGithub = computed(() => !moi.value?.loginGithub)

/*
 * Relier son GitHub (proposition 0005) : une personne venue par Google ou par e-mail rattache son compte GitHub à son
 * compte Jamii, puis génère sa carte de compétences. L'API revient ici avec `?github=relie` ou `?erreurGithub=…`.
 */
const route = useRoute()
const lienGithub = '/api/auth/github?lier=1'
const githubRelie = computed(() => route.query.github === 'relie')
const ERREURS_LIEN = ['github_deja_utilise', 'compte_deja_relie', 'refus_github', 'requete_invalide', 'github_indisponible']
const erreurLien = computed(() => {
  const code = route.query.erreurGithub
  if (typeof code !== 'string' || !code) return null
  return ERREURS_LIEN.includes(code) ? code : 'github_indisponible'
})

async function generer() {
  occupe.value = true
  message.value = null
  try {
    const { response } = await api.POST('/api/me/profil/generation')
    if (response.status === 429) message.value = t('profil.generation.tropSouvent')
    else if (!response.ok && response.status !== 409) message.value = t('etat.indisponible')
    await charger()
  } finally {
    occupe.value = false
  }
}

async function changerVisibilite() {
  if (!profil.value) return
  occupe.value = true
  try {
    const { data } = profil.value.publie
      ? await api.POST('/api/me/profil/retirer')
      : await api.POST('/api/me/profil/publier')
    if (data) profil.value = data
  } finally {
    occupe.value = false
  }
}

// ---------- Modifier ce que je déclare ----------
const edition = ref(false)
interface BrouillonRealisation { titre: string; role: string; description: string; technologies: string; url: string; annee: string }
const brouillon = ref({
  titre: '', bio: '', pays: '', ville: '', disponibilite: 'disponible' as Disponibilite, heures: '', competences: '',
  linkedin: '', siteWeb: '', langues: [] as string[], phares: [] as string[], realisations: [] as BrouillonRealisation[],
})
const REALISATIONS_MAX = 6
const PHARES_MAX = 3
const erreurs = ref<Record<string, string>>({})

const competencesDeclarees = computed(() =>
  (profil.value?.competences ?? []).filter((c) => c.source === 'declaree').map((c) => c.competence),
)
const languesTriees = computed(() =>
  [...LANGUES_PROPOSEES].sort((a, b) => nomLangue(a, locale.value).localeCompare(nomLangue(b, locale.value), locale.value)),
)
/** Les compétences que l'on peut mettre en avant : celles du profil, plus celles en cours de saisie. */
const choixPhares = computed(() => [
  ...new Set([
    ...(profil.value?.competences ?? []).filter((c) => c.source !== 'declaree').map((c) => c.competence),
    ...lireCompetences(brouillon.value.competences).map((c) => c.toLowerCase()),
    ...brouillon.value.phares,
  ]),
])
function basculer(liste: string[], valeur: string, max = Infinity) {
  const i = liste.indexOf(valeur)
  if (i >= 0) liste.splice(i, 1)
  else if (liste.length < max) liste.push(valeur)
}
function ajouterRealisation() {
  if (brouillon.value.realisations.length < REALISATIONS_MAX) {
    brouillon.value.realisations.push({ titre: '', role: '', description: '', technologies: '', url: '', annee: '' })
  }
}
const paysTries = computed(() =>
  [...PAYS_AFRIQUE].sort((a, b) => nomPays(a, locale.value).localeCompare(nomPays(b, locale.value), locale.value)),
)

function ouvrirEdition() {
  const p = profil.value!
  brouillon.value = {
    titre: p.titre ?? '',
    linkedin: p.linkedin ?? '',
    siteWeb: p.siteWeb ?? '',
    langues: [...p.langues],
    phares: [...p.competencesPhares],
    realisations: p.realisations.map((r) => ({
      titre: r.titre,
      role: r.role ?? '',
      description: r.description ?? '',
      technologies: (r.technologies ?? []).join(', '),
      url: r.url ?? '',
      annee: r.annee ? String(r.annee) : '',
    })),
    bio: p.bio ?? '',
    pays: p.pays ?? '',
    ville: p.ville ?? '',
    disponibilite: p.disponibilite,
    heures: p.heuresParSemaine ? String(p.heuresParSemaine) : '',
    competences: competencesDeclarees.value.join(', '),
  }
  erreurs.value = {}
  edition.value = true
}

async function enregistrer() {
  const b = brouillon.value
  occupe.value = true
  erreurs.value = {}
  message.value = null
  try {
    const { data, error, response } = await api.PATCH('/api/me/profil', {
      headers: { 'Content-Type': 'application/merge-patch+json' },
      body: {
        bio: b.bio.trim() || null,
        pays: b.pays || null,
        ville: b.ville.trim() || null,
        disponibilite: b.disponibilite,
        heuresParSemaine: b.heures ? Number(b.heures) : null,
        competencesDeclarees: lireCompetences(b.competences),
        titre: b.titre.trim() || null,
        linkedin: b.linkedin.trim() || null,
        siteWeb: b.siteWeb.trim() || null,
        langues: b.langues,
        competencesPhares: b.phares,
        realisations: b.realisations
          .filter((r) => r.titre.trim() || r.description.trim() || r.url.trim())
          .map((r) => ({
            titre: r.titre.trim(),
            role: r.role.trim() || null,
            description: r.description.trim() || null,
            technologies: lireCompetences(r.technologies),
            url: r.url.trim() || null,
            annee: r.annee ? Number(r.annee) : null,
          })),
        version: profil.value!.version,
      },
    })
    if (data) {
      profil.value = data
      edition.value = false
      message.value = t('profil.modifier.enregistre')
    } else if (response.status === 409) {
      message.value = t('profil.modifier.conflit')
      await charger()
      edition.value = false
    } else if (response.status === 400) {
      const champs = (error as { erreurs?: { champ: string; code: string }[] } | undefined)?.erreurs ?? []
      // « competencesDeclarees[1] » s'affiche sous le champ entier ; « realisations[0].url » sous le champ de la ligne.
      erreurs.value = Object.fromEntries(
        champs.flatMap((e) => [[e.champ, t('profil.modifier.invalide')], [e.champ.replace(/\[\d+\]$/, ''), t('profil.modifier.invalide')]]),
      )
    } else {
      message.value = t('etat.indisponible')
    }
  } finally {
    occupe.value = false
  }
}
</script>

<template>
  <section class="mon-profil">
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />

    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <!-- Pas encore de profil : on propose de le générer, en disant ce qu'on lit. -->
    <div v-else-if="etat === 'aucun'" class="accueil-profil" data-test="sans-profil">
      <div class="accueil-profil__texte">
        <h1>{{ t('profil.generation.titre') }}</h1>
        <AbonnesProfil v-if="moi" :personne-id="moi.id" />
        <p class="accueil-profil__intro">{{ t('profil.generation.texte') }}</p>
        <ul class="accueil-profil__lu">
          <li v-for="cle in ['langages', 'sujets', 'etoiles']" :key="cle">✓ {{ t(`profil.generation.lu.${cle}`) }}</li>
          <li class="jamais">✕ {{ t('profil.generation.lu.jamais') }}</li>
        </ul>
        <template v-if="sansGithub">
          <p class="texte-discret">{{ t('profil.generation.sansGithub') }}</p>
          <a :href="lienGithub" class="j-bouton j-bouton--principal" data-test="relier-github">{{ t('profil.generation.relier') }}</a>
        </template>
        <template v-else>
          <p v-if="enCours" role="status" class="en-cours" data-test="en-cours">{{ t('profil.generation.enCours') }}</p>
          <JBouton v-else :occupe="occupe" data-test="generer" @click="generer">{{ t('profil.generation.bouton') }}</JBouton>
          <p class="texte-discret">{{ t('profil.generation.prive') }}</p>
        </template>
        <p v-if="githubRelie && !sansGithub" role="status" class="relie" data-test="github-relie">✓ {{ t('profil.generation.relie') }}</p>
        <p v-if="erreurLien" role="alert" class="erreur" data-test="erreur-lien">{{ t(`profil.generation.erreursLien.${erreurLien}`) }}</p>
        <p v-if="echec" role="alert" class="erreur">{{ t(`profil.generation.erreurs.${echec}`) }}</p>
        <p v-if="message" role="alert" class="erreur">{{ message }}</p>
      </div>
      <div class="accueil-profil__visuel">
        <JIllustration nom="profil-prouve" />
      </div>
    </div>

    <template v-else-if="profil">
      <FicheProfil :profil="profil" moi>
        <template #actions>
          <AbonnesProfil v-if="moi" :personne-id="moi.id" />
          <div class="barre-actions">
            <p class="visibilite" :class="{ 'visibilite--publie': profil.publie }" data-test="visibilite">
              {{ profil.publie ? t('profil.visibilite.publie') : t('profil.visibilite.prive') }}
            </p>
            <JBouton :variante="profil.publie ? 'secondaire' : 'principal'" :occupe="occupe" data-test="publier" @click="changerVisibilite">
              {{ profil.publie ? t('profil.visibilite.retirer') : t('profil.visibilite.publier') }}
            </JBouton>
            <JBouton v-if="!edition" variante="secondaire" data-test="modifier" @click="ouvrirEdition">{{ t('profil.modifier.bouton') }}</JBouton>
            <PartagerCarte :profil="profil" :publie="profil.publie" />
            <RouterLink v-if="profil.publie" :to="`/p/${profil.utilisateurId}`" class="lien-public" data-test="voir-public">
              {{ t('profil.visibilite.voirPublic') }}</RouterLink>
            <JBouton variante="discret" :occupe="occupe || enCours" data-test="regenerer" @click="generer">
              {{ enCours ? t('profil.generation.enCours') : t('profil.generation.regenerer') }}
            </JBouton>
          </div>
          <p v-if="message" role="status" data-test="message">{{ message }}</p>
          <p v-if="echec" role="alert">{{ t(`profil.generation.erreurs.${echec}`) }}</p>

          <form v-if="edition" class="carte formulaire-profil" data-test="formulaire" @submit.prevent="enregistrer">
            <h2>{{ t('profil.modifier.titre') }}</h2>
            <JChamp v-model="brouillon.titre" :libelle="t('profil.modifier.titrePro')" :aide="t('profil.modifier.titreProAide')" :maxlength="80" :erreur="erreurs.titre" data-test="champ-titre" />
            <JChamp v-model="brouillon.bio" :libelle="t('profil.modifier.bio')" multiligne :maxlength="500" :erreur="erreurs.bio" />
            <div class="deux-colonnes">
              <label class="j-champ">
                <span>{{ t('profil.modifier.pays') }}</span>
                <select v-model="brouillon.pays" class="selection">
                  <option value="">{{ t('profil.modifier.paysAucun') }}</option>
                  <option v-for="code in paysTries" :key="code" :value="code">{{ nomPays(code, locale) }}</option>
                </select>
              </label>
              <JChamp v-model="brouillon.ville" :libelle="t('profil.modifier.ville')" :maxlength="80" :erreur="erreurs.ville" />
            </div>
            <div class="deux-colonnes">
              <label class="j-champ">
                <span>{{ t('profil.modifier.disponibilite') }}</span>
                <select v-model="brouillon.disponibilite" class="selection">
                  <option v-for="d in DISPONIBILITES" :key="d" :value="d">{{ t(`profil.disponibilite.${d}`) }}</option>
                </select>
              </label>
              <JChamp v-model="brouillon.heures" type="number" :libelle="t('profil.modifier.heures')" :erreur="erreurs.heuresParSemaine" />
            </div>
            <div class="deux-colonnes">
              <JChamp v-model="brouillon.linkedin" type="url" :libelle="t('profil.modifier.linkedin')" :aide="t('profil.modifier.linkedinAide')" :maxlength="300" :erreur="erreurs.linkedin" />
              <JChamp v-model="brouillon.siteWeb" type="url" :libelle="t('profil.modifier.site')" :aide="t('profil.modifier.siteAide')" :maxlength="300" :erreur="erreurs.siteWeb" />
            </div>

            <fieldset class="choix">
              <legend>{{ t('profil.modifier.langues') }}</legend>
              <div class="pastilles">
                <button v-for="l in languesTriees" :key="l" type="button" class="pastille" :aria-pressed="brouillon.langues.includes(l)" @click="basculer(brouillon.langues, l, 8)">
                  {{ nomLangue(l, locale) }}</button>
              </div>
              <p v-if="erreurs.langues" class="erreur">{{ erreurs.langues }}</p>
            </fieldset>

            <JChamp
              v-model="brouillon.competences"
              :libelle="t('profil.modifier.competences')"
              :aide="t('profil.modifier.competencesAide')"
              :erreur="erreurs.competencesDeclarees"
            />
            <fieldset v-if="choixPhares.length" class="choix">
              <legend>{{ t('profil.modifier.phares', { n: PHARES_MAX }) }}</legend>
              <div class="pastilles">
                <button v-for="c in choixPhares" :key="c" type="button" class="pastille" :aria-pressed="brouillon.phares.includes(c)" :disabled="!brouillon.phares.includes(c) && brouillon.phares.length >= PHARES_MAX" @click="basculer(brouillon.phares, c, PHARES_MAX)">
                  {{ c }}</button>
              </div>
              <p v-if="erreurs.competencesPhares" class="erreur">{{ erreurs.competencesPhares }}</p>
            </fieldset>

            <fieldset class="choix" data-test="champ-realisations">
              <legend>{{ t('profil.modifier.realisations') }}</legend>
              <p class="texte-discret">{{ t('profil.modifier.realisationsAide') }}</p>
              <div v-for="(r, i) in brouillon.realisations" :key="i" class="realisation-saisie">
                <div class="deux-colonnes">
                  <JChamp v-model="r.titre" :libelle="t('profil.modifier.realisation.titre')" requis :maxlength="80" :erreur="erreurs[`realisations[${i}].titre`]" />
                  <JChamp v-model="r.role" :libelle="t('profil.modifier.realisation.role')" :maxlength="60" :erreur="erreurs[`realisations[${i}].role`]" />
                </div>
                <JChamp v-model="r.description" :libelle="t('profil.modifier.realisation.description')" multiligne :lignes="2" :maxlength="300" :erreur="erreurs[`realisations[${i}].description`]" />
                <div class="trois-colonnes">
                  <JChamp v-model="r.technologies" :libelle="t('profil.modifier.realisation.technologies')" :erreur="erreurs[`realisations[${i}].technologies`]" />
                  <JChamp v-model="r.url" type="url" :libelle="t('profil.modifier.realisation.lien')" :maxlength="300" :erreur="erreurs[`realisations[${i}].url`]" />
                  <JChamp v-model="r.annee" type="number" :libelle="t('profil.modifier.realisation.annee')" :erreur="erreurs[`realisations[${i}].annee`]" />
                </div>
                <JBouton variante="discret" @click="brouillon.realisations.splice(i, 1)">{{ t('profil.modifier.realisation.retirer') }}</JBouton>
              </div>
              <JBouton v-if="brouillon.realisations.length < REALISATIONS_MAX" variante="secondaire" data-test="ajouter-realisation" @click="ajouterRealisation">
                {{ t('profil.modifier.realisation.ajouter') }}</JBouton>
            </fieldset>

            <div class="barre-actions">
              <JBouton type="submit" :occupe="occupe" data-test="enregistrer">{{ t('profil.modifier.enregistrer') }}</JBouton>
              <JBouton variante="discret" @click="edition = false">{{ t('profil.modifier.annuler') }}</JBouton>
            </div>
          </form>
        </template>
        <template #debut>
          <CarteCompetences :profil="profil" @mis-a-jour="(p) => (profil = p)" />
        </template>
      </FicheProfil>
    </template>
    <ReglagePresence v-if="etat !== 'chargement'" />
  </section>
</template>

<style scoped>
.mon-profil { display: grid; gap: var(--space-6); }
.accueil-profil { display: grid; gap: var(--space-8); grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); align-items: center; padding-top: var(--space-4); }
@media (max-width: 760px) { .accueil-profil { grid-template-columns: 1fr; } .accueil-profil__visuel { order: -1; max-width: 18rem; } }
.accueil-profil__texte { display: grid; gap: var(--space-4); justify-items: start; }
.accueil-profil__texte > * { margin: 0; }
.accueil-profil h1 { font-size: clamp(2rem, 4.5vw, 2.75rem); line-height: 1.1; letter-spacing: -0.8px; }
.accueil-profil__intro { font-size: 1.0625rem; color: var(--ink-muted); max-width: 34rem; }
.accueil-profil__lu { list-style: none; padding: 0; display: grid; gap: var(--space-2); font-weight: 600; }
.accueil-profil__lu .jamais { color: var(--ink-muted); font-weight: 400; }
.accueil-profil__visuel { padding: var(--space-8); background: var(--action-soft); border-radius: var(--radius-lg); }
.en-cours { font-weight: 600; color: var(--action-ink); }
.erreur { color: var(--danger); font-weight: 600; margin: 0; }
.visibilite { padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--surface-raised); }
.visibilite--publie { background: var(--action-soft); }
.barre-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.barre-actions .visibilite { margin: 0; }
.lien-public { font-weight: 600; padding: 0 var(--space-2); }
.formulaire-profil { display: grid; gap: var(--space-4); }
.formulaire-profil h2 { font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.deux-colonnes { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); }
.trois-colonnes { display: grid; gap: var(--space-4); grid-template-columns: 2fr 2fr 1fr; }
@media (max-width: 640px) { .trois-colonnes { grid-template-columns: 1fr; } }
.j-champ > span { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.selection { width: 100%; min-height: 44px; padding: var(--space-2) var(--space-3); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }
.choix { border: 0; padding: 0; margin: 0; display: grid; gap: var(--space-2); }
.choix legend { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); margin-block-end: var(--space-2); padding: 0; }
.choix > p { margin: 0; }
.pastilles { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.pastille { min-height: 36px; padding: 0 var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; }
.pastille[aria-pressed='true'] { background: var(--action-soft); border-color: var(--action); color: var(--action-ink); font-weight: 700; }
.pastille:disabled { opacity: 0.45; cursor: not-allowed; }
.realisation-saisie { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); justify-items: stretch; }
.realisation-saisie > :last-child { justify-self: start; }
.relie { margin: 0; font-weight: 700; color: var(--success, var(--ink)); }
</style>
