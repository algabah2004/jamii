<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
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
  type BlocProfil,
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

// ---------- Modifier ce que je déclare, bloc par bloc ----------
/** Le bloc ouvert : un seul à la fois, à l'endroit où l'on a touché le crayon. `null` : rien n'est en cours de modification. */
const bloc = ref<BlocProfil | null>(null)
interface BrouillonRealisation { titre: string; role: string; description: string; technologies: string; url: string; annee: string }
const brouillon = ref({
  titre: '', bio: '', pays: '', ville: '', disponibilite: 'disponible' as Disponibilite, heures: '', competences: '',
  linkedin: '', siteWeb: '', langues: [] as string[], phares: [] as string[], realisations: [] as BrouillonRealisation[],
})
const REALISATIONS_MAX = 6
const PHARES_MAX = 3
const erreurs = ref<Record<string, string>>({})
const formulaire = ref<HTMLFormElement | null>(null)

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

/** Ce qu'il reste à remplir : chaque case ouvre son bloc. Rien n'est obligatoire. */
const aRemplir = computed(() => {
  const p = profil.value
  if (!p) return []
  const manquants: BlocProfil[] = []
  if (!p.titre || !p.bio) manquants.push('identite')
  if (!p.pays) manquants.push('lieu')
  if (!p.linkedin && !p.siteWeb) manquants.push('liens')
  if (!p.langues.length) manquants.push('langues')
  if (!p.realisations.length) manquants.push('realisations')
  return manquants
})

function ouvrirBloc(nom: BlocProfil) {
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
  message.value = null
  bloc.value = nom
  // Le bloc s'ouvre sous l'en-tête : le focus y va, pour qu'on écrive tout de suite (clavier et lecteur d'écran).
  void nextTick(() => {
    formulaire.value?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
    formulaire.value?.querySelector<HTMLElement>('input, textarea, select')?.focus({ preventScroll: true })
  })
}

/** Les champs du bloc ouvert, et eux seuls : les autres blocs ne bougent pas (JSON Merge Patch). */
function champsDuBloc(nom: BlocProfil) {
  const b = brouillon.value
  switch (nom) {
    case 'identite':
      return { titre: b.titre.trim() || null, bio: b.bio.trim() || null }
    case 'lieu':
      return {
        pays: b.pays || null,
        ville: b.ville.trim() || null,
        disponibilite: b.disponibilite,
        heuresParSemaine: b.heures ? Number(b.heures) : null,
      }
    case 'liens':
      return { linkedin: b.linkedin.trim() || null, siteWeb: b.siteWeb.trim() || null }
    case 'langues':
      return { langues: b.langues }
    case 'competences':
      return { competencesDeclarees: lireCompetences(b.competences), competencesPhares: b.phares }
    case 'realisations':
      return {
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
      }
  }
}

async function enregistrer() {
  if (!bloc.value) return
  occupe.value = true
  erreurs.value = {}
  message.value = null
  try {
    const { data, error, response } = await api.PATCH('/api/me/profil', {
      headers: { 'Content-Type': 'application/merge-patch+json' },
      body: { ...champsDuBloc(bloc.value), version: profil.value!.version },
    })
    if (data) {
      profil.value = data
      bloc.value = null
      message.value = t('profil.modifier.enregistre')
    } else if (response.status === 409) {
      message.value = t('profil.modifier.conflit')
      await charger()
      bloc.value = null
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
      <!-- La barre du propriétaire : ce que seule la personne voit. -->
      <div class="barre-proprio" data-test="barre-proprio">
        <h2 class="barre-proprio__titre">{{ t('profil.barre.titre') }}</h2>
        <button
          type="button" role="switch" class="interrupteur" :class="{ 'interrupteur--publie': profil.publie }" :aria-checked="profil.publie"
          :disabled="occupe" :aria-label="t('profil.barre.visibilite')" data-test="publier" @click="changerVisibilite"
        >
          <span class="interrupteur__etat">{{ profil.publie ? t('profil.barre.publie') : t('profil.barre.prive') }}</span>
          <span class="interrupteur__action">{{ profil.publie ? t('profil.visibilite.retirer') : t('profil.visibilite.publier') }}</span>
        </button>
        <p class="visibilite" :class="{ 'visibilite--publie': profil.publie }" data-test="visibilite">
          {{ profil.publie ? t('profil.visibilite.publie') : t('profil.visibilite.prive') }}
        </p>
        <div class="barre-proprio__actions">
          <JBouton v-if="!bloc" variante="secondaire" data-test="modifier" @click="ouvrirBloc('identite')">{{ t('profil.modifier.bouton') }}</JBouton>
          <PartagerCarte :profil="profil" :publie="profil.publie" />
          <RouterLink v-if="profil.publie" :to="`/p/${profil.utilisateurId}`" class="lien-public" data-test="voir-public">
            {{ t('profil.visibilite.voirPublic') }}</RouterLink>
          <JBouton variante="discret" :occupe="occupe || enCours" data-test="regenerer" @click="generer">
            {{ enCours ? t('profil.generation.enCours') : t('profil.generation.regenerer') }}
          </JBouton>
        </div>
      </div>

      <!-- Ce qu'il reste à remplir : un geste par bloc, pas un formulaire. -->
      <div v-if="aRemplir.length && !bloc" class="a-remplir" data-test="a-remplir">
        <p class="a-remplir__titre">{{ t('profil.modifier.incompletes', { n: aRemplir.length }, aRemplir.length) }}</p>
        <button v-for="nom in aRemplir" :key="nom" type="button" class="a-remplir__ajout" @click="ouvrirBloc(nom)">
          {{ t(`profil.modifier.ajouter.${nom}`) }}</button>
      </div>

      <FicheProfil :profil="profil" moi @modifier="ouvrirBloc">
        <template #boutons>
          <AbonnesProfil v-if="moi" :personne-id="moi.id" />
        </template>
        <template #actions>
          <p v-if="echec" role="alert" class="erreur">{{ t(`profil.generation.erreurs.${echec}`) }}</p>

          <!-- Le bloc ouvert : on écrit là, sous l'en-tête. Les autres blocs ne bougent pas. -->
          <form v-if="bloc" ref="formulaire" class="bloc-ouvert" data-test="formulaire" aria-labelledby="bloc-titre" @submit.prevent="enregistrer" @keydown.esc="bloc = null">
            <div class="bloc-ouvert__tete">
              <h2 id="bloc-titre">{{ t('profil.modifier.blocEnCours', { bloc: t(`profil.modifier.blocs.${bloc}`) }) }}</h2>
              <span class="texte-discret">{{ t('profil.modifier.autresBlocs') }}</span>
            </div>

            <template v-if="bloc === 'identite'">
              <JChamp v-model="brouillon.titre" :libelle="t('profil.modifier.titrePro')" :aide="t('profil.modifier.titreProAide')" :maxlength="80" :erreur="erreurs.titre" data-test="champ-titre" />
              <JChamp v-model="brouillon.bio" :libelle="t('profil.modifier.bio')" :aide="t('profil.modifier.bioAide')" multiligne :maxlength="500" :erreur="erreurs.bio" />
            </template>

            <template v-else-if="bloc === 'lieu'">
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
            </template>

            <template v-else-if="bloc === 'liens'">
              <div class="deux-colonnes">
                <JChamp v-model="brouillon.linkedin" type="url" :libelle="t('profil.modifier.linkedin')" :aide="t('profil.modifier.linkedinAide')" :maxlength="300" :erreur="erreurs.linkedin" />
                <JChamp v-model="brouillon.siteWeb" type="url" :libelle="t('profil.modifier.site')" :aide="t('profil.modifier.siteAide')" :maxlength="300" :erreur="erreurs.siteWeb" />
              </div>
            </template>

            <fieldset v-else-if="bloc === 'langues'" class="choix">
              <legend>{{ t('profil.modifier.langues') }}</legend>
              <div class="pastilles-choix">
                <button v-for="l in languesTriees" :key="l" type="button" class="pastille" :aria-pressed="brouillon.langues.includes(l)" @click="basculer(brouillon.langues, l, 8)">
                  {{ nomLangue(l, locale) }}</button>
              </div>
              <p v-if="erreurs.langues" class="erreur">{{ erreurs.langues }}</p>
            </fieldset>

            <template v-else-if="bloc === 'competences'">
              <fieldset v-if="choixPhares.length" class="choix">
                <legend>{{ t('profil.modifier.phares', { n: PHARES_MAX }) }}</legend>
                <div class="pastilles-choix">
                  <button v-for="c in choixPhares" :key="c" type="button" class="pastille" :aria-pressed="brouillon.phares.includes(c)" :disabled="!brouillon.phares.includes(c) && brouillon.phares.length >= PHARES_MAX" @click="basculer(brouillon.phares, c, PHARES_MAX)">
                    {{ c }}</button>
                </div>
                <p v-if="erreurs.competencesPhares" class="erreur">{{ erreurs.competencesPhares }}</p>
              </fieldset>
              <JChamp
                v-model="brouillon.competences"
                :libelle="t('profil.modifier.competences')"
                :aide="t('profil.modifier.competencesAide')"
                :erreur="erreurs.competencesDeclarees"
              />
              <p class="texte-discret">{{ t('profil.modifier.competencesPointille') }}</p>
            </template>

            <fieldset v-else-if="bloc === 'realisations'" class="choix" data-test="champ-realisations">
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

            <div class="bloc-ouvert__actions">
              <JBouton type="submit" :occupe="occupe" data-test="enregistrer">{{ t('profil.modifier.enregistrerBloc') }}</JBouton>
              <JBouton variante="secondaire" data-test="annuler" @click="bloc = null">{{ t('profil.modifier.annuler') }}</JBouton>
              <span class="texte-discret">{{ t('profil.modifier.rienPublie') }}</span>
            </div>
          </form>
        </template>
        <template #debut>
          <CarteCompetences :profil="profil" @mis-a-jour="(p) => (profil = p)" />
        </template>
      </FicheProfil>

      <p v-if="message" role="status" class="toast" data-test="message">{{ message }}</p>
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

/* ---------- La barre du propriétaire ---------- */
.barre-proprio { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3) var(--space-6); padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); }
.barre-proprio__titre { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.barre-proprio__actions { margin-inline-start: auto; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.interrupteur { display: inline-flex; align-items: center; gap: var(--space-3); min-height: 44px; padding: var(--space-1) var(--space-4) var(--space-1) var(--space-1); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; }
.interrupteur:disabled { opacity: 0.6; cursor: progress; }
.interrupteur__etat { padding: var(--space-1) var(--space-4); border-radius: var(--radius-pill); background: var(--line); font-weight: 700; }
.interrupteur--publie .interrupteur__etat { background: var(--proof); color: var(--midnight); }
.interrupteur__action { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.visibilite { margin: 0; font-size: var(--type-body-sm-size); color: var(--ink-muted); flex: 1 1 18rem; }
.lien-public { font-weight: 600; padding: 0 var(--space-2); }

.a-remplir { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); padding: var(--space-4) var(--space-6); border-radius: var(--radius-lg); background: var(--action-soft); }
.a-remplir__titre { margin: 0; margin-inline-end: var(--space-2); font-family: var(--font-display); font-size: var(--type-h3-size); line-height: var(--type-h3-line); font-weight: 700; }
.a-remplir__ajout { min-height: 44px; padding: 0 var(--space-4); border: 1px dashed var(--action-ink); border-radius: var(--radius-pill); background: transparent; color: var(--ink); font: inherit; font-weight: 600; cursor: pointer; }
.a-remplir__ajout:hover { background: var(--surface); }

/* ---------- Le bloc ouvert ---------- */
.bloc-ouvert { scroll-margin-top: 6rem; display: grid; gap: var(--space-4); padding: var(--space-6); margin-top: var(--space-4); border: 2px solid var(--action); border-radius: var(--radius-lg); background: var(--surface-raised); }
.bloc-ouvert__tete { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: var(--space-2) var(--space-4); }
.bloc-ouvert__tete h2 { margin: 0; font-family: var(--font-text); font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--action-ink); }
.bloc-ouvert__actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.bloc-ouvert p { margin: 0; }
.deux-colonnes { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); }
.trois-colonnes { display: grid; gap: var(--space-4); grid-template-columns: 2fr 2fr 1fr; }
@media (max-width: 640px) { .trois-colonnes { grid-template-columns: 1fr; } }
.j-champ > span { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.selection { width: 100%; min-height: 44px; padding: var(--space-2) var(--space-3); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }
.choix { border: 0; padding: 0; margin: 0; display: grid; gap: var(--space-2); }
.choix legend { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); margin-block-end: var(--space-2); padding: 0; }
.choix > p { margin: 0; }
.pastilles-choix { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.pastille { min-height: 44px; padding: 0 var(--space-4); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; }
.pastille[aria-pressed='true'] { background: var(--proof-soft); border: 2px solid var(--proof); color: var(--ink); font-weight: 700; }
.pastille:disabled { opacity: 0.45; cursor: not-allowed; }
.realisation-saisie { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); background: var(--surface); justify-items: stretch; }
.realisation-saisie > :last-child { justify-self: start; }
.relie { margin: 0; font-weight: 700; color: var(--success, var(--ink)); }

/* Le message d'enregistrement : une pastille en bas de l'écran, qui reste visible pendant qu'on défile. */
.toast { position: sticky; bottom: var(--space-6); justify-self: center; margin: 0; padding: var(--space-3) var(--space-6); border-radius: var(--radius-pill); background: var(--ink); color: var(--surface); font-weight: 600; box-shadow: 0 8px 24px color-mix(in srgb, var(--midnight) 35%, transparent); z-index: 5; }
</style>
