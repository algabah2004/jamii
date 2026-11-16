<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton, JChamp } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import IconeProfil from './IconeProfil.vue'
import SaisieEtiquettes from './SaisieEtiquettes.vue'
import { SECTIONS_EDITION, type SectionEdition } from './completude'
import { DISPONIBILITES, LANGUES_PROPOSEES, nomLangue, PAYS_AFRIQUE, urlDrapeau, type Disponibilite, type MonProfil } from './profils'

/*
 * « Modifier mon profil » (#159) : un panneau sur le côté plutôt qu'un formulaire au milieu de la page. Chaque
 * changement se voit tout de suite sur le profil derrière (`apercu`), rien n'est envoyé avant « Enregistrer ».
 * Quatre sections, dans l'ordre où l'on remplit un profil : l'essentiel, les compétences, les liens, les réalisations.
 */
const props = defineProps<{ profil: MonProfil; section?: SectionEdition }>()
const emit = defineEmits<{
  fermer: []
  enregistre: [profil: MonProfil]
  conflit: []
  apercu: [champs: Partial<MonProfil>]
}>()
const { t, locale } = useI18n()

interface BrouillonRealisation { titre: string; role: string; description: string; technologies: string[]; url: string; annee: string; ouverte: boolean }
interface Brouillon {
  titre: string; bio: string; pays: string; ville: string; disponibilite: Disponibilite; heures: string
  competences: string[]; linkedin: string; siteWeb: string; langues: string[]; phares: string[]; realisations: BrouillonRealisation[]
}

const REALISATIONS_MAX = 6
const PHARES_MAX = 3
const LANGUES_MAX = 8
const BIO_MAX = 500
const HEURES_PROPOSEES = [5, 10, 20, 40]

function depuis(p: MonProfil): Brouillon {
  return {
    titre: p.titre ?? '',
    bio: p.bio ?? '',
    pays: p.pays ?? '',
    ville: p.ville ?? '',
    disponibilite: p.disponibilite,
    heures: p.heuresParSemaine ? String(p.heuresParSemaine) : '',
    competences: p.competences.filter((c) => c.source === 'declaree').map((c) => c.competence),
    linkedin: p.linkedin ?? '',
    siteWeb: p.siteWeb ?? '',
    langues: [...p.langues],
    phares: [...p.competencesPhares],
    realisations: p.realisations.map((r) => ({
      titre: r.titre, role: r.role ?? '', description: r.description ?? '', technologies: [...(r.technologies ?? [])],
      url: r.url ?? '', annee: r.annee ? String(r.annee) : '', ouverte: false,
    })),
  }
}

const brouillon = ref<Brouillon>(depuis(props.profil))
const initial = JSON.stringify(sansEtat(brouillon.value))
function sansEtat(b: Brouillon) {
  return { ...b, realisations: b.realisations.map(({ ouverte: _o, ...r }) => r) }
}
const modifie = computed(() => JSON.stringify(sansEtat(brouillon.value)) !== initial)

const erreurs = ref<Record<string, string>>({})
const occupe = ref(false)
const erreurGenerale = ref<string | null>(null)
const confirmerSortie = ref(false)

// ---------- Ce qui est envoyé, et ce qui se voit en direct derrière le panneau ----------
const competencesAutres = computed(() => props.profil.competences.filter((c) => c.source !== 'declaree'))
const realisationsEnvoyees = computed(() =>
  brouillon.value.realisations
    .filter((r) => r.titre.trim() || r.description.trim() || r.url.trim())
    .map((r) => ({
      titre: r.titre.trim(),
      role: r.role.trim() || null,
      description: r.description.trim() || null,
      technologies: r.technologies,
      url: r.url.trim() || null,
      annee: r.annee ? Number(r.annee) : null,
    })),
)
const champs = computed(() => {
  const b = brouillon.value
  return {
    titre: b.titre.trim() || null,
    bio: b.bio.trim() || null,
    pays: b.pays || null,
    ville: b.ville.trim() || null,
    disponibilite: b.disponibilite,
    heuresParSemaine: b.heures ? Number(b.heures) : null,
    linkedin: b.linkedin.trim() || null,
    siteWeb: b.siteWeb.trim() || null,
    langues: b.langues,
    competencesPhares: b.phares,
    realisations: realisationsEnvoyees.value,
  }
})
watch(
  champs,
  (c) => emit('apercu', {
    ...c,
    competences: [...competencesAutres.value, ...brouillon.value.competences.map((competence) => ({ competence, source: 'declaree' as const }))],
  }),
  { deep: true, immediate: true },
)

// ---------- Choix proposés ----------
const paysTries = computed(() =>
  [...PAYS_AFRIQUE].sort((a, b) => nomPays(a, locale.value).localeCompare(nomPays(b, locale.value), locale.value)),
)
const languesTriees = computed(() =>
  [...LANGUES_PROPOSEES].sort((a, b) => nomLangue(a, locale.value).localeCompare(nomLangue(b, locale.value), locale.value)),
)
/** Toutes les compétences du profil, pour choisir les 3 à mettre en avant. */
const choixPhares = computed(() => [
  ...new Set([...competencesAutres.value.map((c) => c.competence), ...brouillon.value.competences, ...brouillon.value.phares]),
])
/** Les suggestions viennent de GitHub et de la carte IA : ce que la personne fait déjà, à déclarer en un clic. */
const suggestionsCompetences = computed(() =>
  [...new Set([...(props.profil.competencesProposees ?? []).map((c) => c.competence), ...props.profil.langages.map((l) => l.nom.toLowerCase())])]
    .filter((c) => !competencesAutres.value.some((x) => x.competence === c)),
)
const drapeau = computed(() => urlDrapeau(brouillon.value.pays))

function basculer(liste: string[], valeur: string, max: number) {
  const i = liste.indexOf(valeur)
  if (i >= 0) liste.splice(i, 1)
  else if (liste.length < max) liste.push(valeur)
}
function ajouterRealisation() {
  if (brouillon.value.realisations.length >= REALISATIONS_MAX) return
  brouillon.value.realisations.forEach((r) => (r.ouverte = false))
  brouillon.value.realisations.push({ titre: '', role: '', description: '', technologies: [], url: '', annee: '', ouverte: true })
}

// ---------- Navigation entre les sections ----------
const sectionActive = ref<SectionEdition>(props.section ?? 'essentiel')
const corps = ref<HTMLElement | null>(null)
function allerA(s: SectionEdition) {
  sectionActive.value = s
  corps.value?.querySelector(`#edition-${s}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}
function suivreDefilement() {
  const zone = corps.value
  if (!zone) return
  const haut = zone.getBoundingClientRect().top
  for (const s of [...SECTIONS_EDITION].reverse()) {
    const el = zone.querySelector<HTMLElement>(`#edition-${s}`)
    if (el && el.getBoundingClientRect().top - haut < 120) {
      sectionActive.value = s
      return
    }
  }
}

// ---------- Ouverture, fermeture ----------
const titre = ref<HTMLElement | null>(null)
let debordement = ''
function clavier(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    demanderFermeture()
  }
}
onMounted(async () => {
  debordement = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', clavier)
  await nextTick()
  if (props.section && props.section !== 'essentiel') allerA(props.section)
  titre.value?.focus()
})
onBeforeUnmount(() => {
  document.body.style.overflow = debordement
  document.removeEventListener('keydown', clavier)
})

function demanderFermeture() {
  if (modifie.value && !confirmerSortie.value) confirmerSortie.value = true
  else emit('fermer')
}

// ---------- Enregistrer ----------
const saisieCompetences = ref<InstanceType<typeof SaisieEtiquettes> | null>(null)
const saisiesTechnos = ref<InstanceType<typeof SaisieEtiquettes>[]>([])

async function enregistrer() {
  saisieCompetences.value?.valider()
  saisiesTechnos.value.forEach((s) => s?.valider())
  await nextTick()
  occupe.value = true
  erreurs.value = {}
  erreurGenerale.value = null
  confirmerSortie.value = false
  try {
    const { data, error, response } = await api.PATCH('/api/me/profil', {
      headers: { 'Content-Type': 'application/merge-patch+json' },
      body: { ...champs.value, competencesDeclarees: brouillon.value.competences, version: props.profil.version },
    })
    if (data) emit('enregistre', data)
    else if (response.status === 409) emit('conflit')
    else if (response.status === 400) {
      const liste = (error as { erreurs?: { champ: string; code: string }[] } | undefined)?.erreurs ?? []
      // « competencesDeclarees[1] » s'affiche sous le champ entier ; « realisations[0].url » sous le champ de la ligne.
      erreurs.value = Object.fromEntries(
        liste.flatMap((e) => [[e.champ, t('profil.modifier.invalide')], [e.champ.replace(/\[\d+\]$/, ''), t('profil.modifier.invalide')]]),
      )
      liste.forEach((e) => {
        const i = /^realisations\[(\d+)\]/.exec(e.champ)?.[1]
        if (i !== undefined && brouillon.value.realisations[Number(i)]) brouillon.value.realisations[Number(i)]!.ouverte = true
      })
      erreurGenerale.value = t('profil.editeur.aCorriger')
      await nextTick()
      corps.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    } else erreurGenerale.value = t('etat.indisponible')
  } catch {
    erreurGenerale.value = t('etat.indisponible')
  } finally {
    occupe.value = false
  }
}
</script>

<template>
  <div class="editeur" data-test="editeur">
    <div class="editeur__voile" aria-hidden="true" @click="demanderFermeture" />
    <form
      class="editeur__panneau"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editeur-titre"
      novalidate
      data-test="formulaire"
      @submit.prevent="enregistrer"
    >
      <header class="editeur__tete">
        <div>
          <p class="editeur__surtitre"><i aria-hidden="true" />{{ t('profil.editeur.surtitre') }}</p>
          <h2 id="editeur-titre" ref="titre" tabindex="-1">{{ t('profil.editeur.titre') }}</h2>
        </div>
        <button type="button" class="editeur__fermer" :aria-label="t('profil.editeur.fermer')" data-test="fermer-editeur" @click="demanderFermeture">
          <IconeProfil nom="fermer" />
        </button>
        <nav class="editeur__sections" :aria-label="t('profil.editeur.sections')">
          <button
            v-for="s in SECTIONS_EDITION"
            :key="s"
            type="button"
            :aria-current="sectionActive === s ? 'true' : undefined"
            @click="allerA(s)"
          >{{ t(`profil.editeur.section.${s}`) }}</button>
        </nav>
      </header>

      <div ref="corps" class="editeur__corps" @scroll.passive="suivreDefilement">
        <!-- L'essentiel : qui je suis, où, et quand je suis disponible. -->
        <section id="edition-essentiel" class="partie" aria-labelledby="partie-essentiel">
          <h3 id="partie-essentiel"><IconeProfil nom="personne" />{{ t('profil.editeur.section.essentiel') }}</h3>
          <JChamp v-model="brouillon.titre" :libelle="t('profil.modifier.titrePro')" :aide="t('profil.modifier.titreProAide')" :maxlength="80" :erreur="erreurs.titre" data-test="champ-titre" />
          <div class="avec-compteur">
            <JChamp v-model="brouillon.bio" :libelle="t('profil.modifier.bio')" :aide="t('profil.editeur.bioAide')" multiligne :lignes="4" :maxlength="BIO_MAX" :erreur="erreurs.bio" data-test="champ-bio" />
            <span class="compteur" :class="{ 'compteur--proche': brouillon.bio.length > BIO_MAX - 50 }" aria-hidden="true">{{ brouillon.bio.length }} / {{ BIO_MAX }}</span>
          </div>
          <div class="deux">
            <label class="champ-natif">
              <span>{{ t('profil.modifier.pays') }}</span>
              <span class="champ-natif__zone">
                <img v-if="drapeau" :src="drapeau" alt="" width="22" height="16" class="drapeau" />
                <select v-model="brouillon.pays" data-test="champ-pays">
                  <option value="">{{ t('profil.modifier.paysAucun') }}</option>
                  <option v-for="code in paysTries" :key="code" :value="code">{{ nomPays(code, locale) }}</option>
                </select>
              </span>
            </label>
            <JChamp v-model="brouillon.ville" :libelle="t('profil.modifier.ville')" :maxlength="80" :erreur="erreurs.ville" data-test="champ-ville" />
          </div>

          <fieldset class="groupe">
            <legend>{{ t('profil.modifier.disponibilite') }}</legend>
            <div class="disponibilites">
              <label v-for="d in DISPONIBILITES" :key="d" class="disponibilite" :class="`disponibilite--${d}`">
                <input v-model="brouillon.disponibilite" type="radio" name="disponibilite" :value="d" />
                <i aria-hidden="true" />
                <span>{{ t(`profil.disponibilite.${d}`) }}</span>
              </label>
            </div>
          </fieldset>
          <fieldset v-if="brouillon.disponibilite !== 'indisponible'" class="groupe">
            <legend>{{ t('profil.editeur.heures') }}</legend>
            <div class="heures">
              <button
                v-for="h in HEURES_PROPOSEES"
                :key="h"
                type="button"
                class="choix"
                :aria-pressed="brouillon.heures === String(h)"
                @click="brouillon.heures = brouillon.heures === String(h) ? '' : String(h)"
              >{{ t('profil.editeur.heuresCourt', { n: h }) }}</button>
              <label class="heures__autre">
                <span class="visuellement-cache">{{ t('profil.modifier.heures') }}</span>
                <input v-model="brouillon.heures" type="number" min="1" max="60" inputmode="numeric" :placeholder="t('profil.editeur.autre')" :aria-invalid="erreurs.heuresParSemaine ? 'true' : undefined" data-test="champ-heures" />
                <span aria-hidden="true">h</span>
              </label>
            </div>
            <p v-if="erreurs.heuresParSemaine" class="erreur">{{ erreurs.heuresParSemaine }}</p>
          </fieldset>
        </section>

        <!-- Compétences et langues. -->
        <section id="edition-competences" class="partie" aria-labelledby="partie-competences">
          <h3 id="partie-competences"><IconeProfil nom="etincelle" />{{ t('profil.editeur.section.competences') }}</h3>
          <p class="partie__aide">{{ t('profil.editeur.competencesIntro') }}</p>
          <SaisieEtiquettes
            ref="saisieCompetences"
            v-model="brouillon.competences"
            :libelle="t('profil.modifier.competences')"
            :aide="t('profil.editeur.competencesAide')"
            :erreur="erreurs.competencesDeclarees"
            :suggestions="suggestionsCompetences"
            data-test="champ-competences"
          />
          <fieldset v-if="choixPhares.length" class="groupe">
            <legend>{{ t('profil.modifier.phares', { n: PHARES_MAX }) }}</legend>
            <div class="pastilles">
              <button
                v-for="c in choixPhares"
                :key="c"
                type="button"
                class="choix choix--etoile"
                :aria-pressed="brouillon.phares.includes(c)"
                :disabled="!brouillon.phares.includes(c) && brouillon.phares.length >= PHARES_MAX"
                @click="basculer(brouillon.phares, c, PHARES_MAX)"
              ><IconeProfil nom="etoile" :taille="14" />{{ c }}</button>
            </div>
            <p v-if="erreurs.competencesPhares" class="erreur">{{ erreurs.competencesPhares }}</p>
          </fieldset>
          <fieldset class="groupe">
            <legend>{{ t('profil.modifier.langues') }} <span class="texte-discret">· {{ brouillon.langues.length }} / {{ LANGUES_MAX }}</span></legend>
            <div class="pastilles">
              <button
                v-for="l in languesTriees"
                :key="l"
                type="button"
                class="choix"
                :aria-pressed="brouillon.langues.includes(l)"
                @click="basculer(brouillon.langues, l, LANGUES_MAX)"
              ><IconeProfil v-if="brouillon.langues.includes(l)" nom="coche" :taille="14" />{{ nomLangue(l, locale) }}</button>
            </div>
            <p v-if="erreurs.langues" class="erreur">{{ erreurs.langues }}</p>
          </fieldset>
        </section>

        <!-- Liens. -->
        <section id="edition-liens" class="partie" aria-labelledby="partie-liens">
          <h3 id="partie-liens"><IconeProfil nom="lien" />{{ t('profil.editeur.section.liens') }}</h3>
          <div class="lien-saisie">
            <span class="lien-saisie__icone"><IconeProfil nom="github" /></span>
            <div class="lien-saisie__github">
              <strong>GitHub</strong>
              <span v-if="profil.loginGithub" class="texte-discret">@{{ profil.loginGithub }} · {{ t('profil.editeur.githubRelie') }}</span>
              <span v-else class="texte-discret">{{ t('profil.editeur.githubAbsent') }}</span>
            </div>
          </div>
          <div class="lien-saisie">
            <span class="lien-saisie__icone"><IconeProfil nom="linkedin" /></span>
            <JChamp v-model="brouillon.linkedin" type="url" :libelle="t('profil.modifier.linkedin')" :aide="t('profil.modifier.linkedinAide')" :maxlength="300" :erreur="erreurs.linkedin" data-test="champ-linkedin" />
          </div>
          <div class="lien-saisie">
            <span class="lien-saisie__icone"><IconeProfil nom="site" /></span>
            <JChamp v-model="brouillon.siteWeb" type="url" :libelle="t('profil.modifier.site')" :aide="t('profil.modifier.siteAide')" :maxlength="300" :erreur="erreurs.siteWeb" data-test="champ-site" />
          </div>
        </section>

        <!-- Réalisations hors de Jamii : repliées pour garder le panneau lisible, une seule ouverte à la fois. -->
        <section id="edition-realisations" class="partie" aria-labelledby="partie-realisations" data-test="champ-realisations">
          <h3 id="partie-realisations"><IconeProfil nom="valise" />{{ t('profil.editeur.section.realisations') }}</h3>
          <p class="partie__aide">{{ t('profil.modifier.realisationsAide') }}</p>
          <ol class="realisations-saisie">
            <li v-for="(r, i) in brouillon.realisations" :key="i" class="realisation-saisie" :class="{ 'realisation-saisie--ouverte': r.ouverte }">
              <div class="realisation-saisie__tete">
                <button type="button" class="realisation-saisie__bascule" :aria-expanded="r.ouverte" @click="r.ouverte = !r.ouverte">
                  <span class="realisation-saisie__numero">{{ i + 1 }}</span>
                  <span class="realisation-saisie__resume">
                    <strong>{{ r.titre.trim() || t('profil.editeur.nouvelleRealisation') }}</strong>
                    <small v-if="r.role || r.annee">{{ [r.role, r.annee].filter(Boolean).join(' · ') }}</small>
                  </span>
                  <IconeProfil :nom="r.ouverte ? 'fermer' : 'crayon'" :taille="16" />
                </button>
                <button type="button" class="realisation-saisie__retirer" :aria-label="t('profil.modifier.realisation.retirer')" @click="brouillon.realisations.splice(i, 1)">
                  {{ t('profil.editeur.retirer') }}
                </button>
              </div>
              <div v-show="r.ouverte" class="realisation-saisie__corps">
                <div class="deux">
                  <JChamp v-model="r.titre" :libelle="t('profil.modifier.realisation.titre')" requis :maxlength="80" :erreur="erreurs[`realisations[${i}].titre`]" />
                  <JChamp v-model="r.role" :libelle="t('profil.modifier.realisation.role')" :maxlength="60" :erreur="erreurs[`realisations[${i}].role`]" />
                </div>
                <JChamp v-model="r.description" :libelle="t('profil.modifier.realisation.description')" multiligne :lignes="2" :maxlength="300" :erreur="erreurs[`realisations[${i}].description`]" />
                <SaisieEtiquettes
                  ref="saisiesTechnos"
                  v-model="r.technologies"
                  :libelle="t('profil.editeur.technologies')"
                  :max="10"
                  :erreur="erreurs[`realisations[${i}].technologies`]"
                />
                <div class="deux deux--lien">
                  <JChamp v-model="r.url" type="url" :libelle="t('profil.modifier.realisation.lien')" :maxlength="300" :erreur="erreurs[`realisations[${i}].url`]" />
                  <JChamp v-model="r.annee" type="number" :libelle="t('profil.modifier.realisation.annee')" :erreur="erreurs[`realisations[${i}].annee`]" />
                </div>
              </div>
            </li>
          </ol>
          <button v-if="brouillon.realisations.length < REALISATIONS_MAX" type="button" class="ajouter" data-test="ajouter-realisation" @click="ajouterRealisation">
            <IconeProfil nom="plus" />{{ t('profil.modifier.realisation.ajouter') }}
            <span class="texte-discret">{{ brouillon.realisations.length }} / {{ REALISATIONS_MAX }}</span>
          </button>
        </section>
      </div>

      <footer class="editeur__pied">
        <div v-if="confirmerSortie" class="sortie" role="alert" data-test="confirmer-sortie">
          <p>{{ t('profil.editeur.nonEnregistre') }}</p>
          <div>
            <JBouton variante="discret" @click="confirmerSortie = false">{{ t('profil.editeur.continuer') }}</JBouton>
            <JBouton variante="secondaire" data-test="abandonner" @click="emit('fermer')">{{ t('profil.editeur.abandonner') }}</JBouton>
          </div>
        </div>
        <template v-else>
          <p v-if="erreurGenerale" class="erreur" role="alert">{{ erreurGenerale }}</p>
          <p v-else class="editeur__etat" aria-live="polite">
            <template v-if="modifie"><i aria-hidden="true" />{{ t('profil.editeur.apercuDirect') }}</template>
            <template v-else>{{ t('profil.editeur.rienAChanger') }}</template>
          </p>
          <div class="editeur__boutons">
            <JBouton variante="discret" @click="demanderFermeture">{{ t('profil.modifier.annuler') }}</JBouton>
            <JBouton type="submit" :occupe="occupe" data-test="enregistrer">{{ t('profil.modifier.enregistrer') }}</JBouton>
          </div>
        </template>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.editeur { position: fixed; inset: 0; z-index: 60; display: flex; justify-content: flex-end; }
.editeur__voile { position: absolute; inset: 0; background: color-mix(in srgb, var(--midnight) 38%, transparent); backdrop-filter: blur(2px); animation: apparaitre var(--motion-base) var(--ease-out); }
.editeur__panneau { position: relative; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; width: min(620px, 100%); height: 100%; background: var(--bg); border-inline-start: 1px solid var(--line); box-shadow: -24px 0 60px color-mix(in srgb, var(--midnight) 22%, transparent); animation: glisser var(--motion-base) var(--ease-out); }
@keyframes glisser { from { transform: translateX(40px); opacity: 0; } }
@keyframes apparaitre { from { opacity: 0; } }

.editeur__tete { position: relative; display: grid; gap: 14px; padding: 22px 24px 0; background: var(--surface); border-bottom: 1px solid var(--line); }
.editeur__tete h2 { margin: 0; font-size: 1.625rem; letter-spacing: -.03em; outline: none; }
.editeur__surtitre { display: flex; align-items: center; gap: 8px; margin: 0 0 6px; color: var(--ink-muted); font-size: .75rem; letter-spacing: .1em; text-transform: uppercase; font-weight: 700; }
.editeur__surtitre i { width: 6px; height: 6px; border-radius: 50%; background: var(--proof); }
.editeur__fermer { position: absolute; top: 18px; right: 18px; display: grid; place-items: center; width: 40px; height: 40px; border: 1px solid var(--line); border-radius: 50%; background: var(--surface); color: var(--ink); cursor: pointer; }
.editeur__fermer:hover { border-color: var(--line-strong); }
.editeur__sections { display: flex; gap: 4px; overflow-x: auto; margin-inline: -4px; scrollbar-width: none; }
.editeur__sections button { flex: none; padding: 10px 14px 12px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--ink-muted); font-weight: 600; font-size: .9375rem; cursor: pointer; }
.editeur__sections button[aria-current='true'] { color: var(--ink); border-bottom-color: var(--proof); }

.editeur__corps { overflow-y: auto; padding: 8px 24px 32px; scroll-behavior: smooth; overscroll-behavior: contain; }
.partie { display: grid; gap: 18px; padding: 24px 0; border-bottom: 1px solid var(--line); scroll-margin-top: 8px; }
.partie:last-child { border-bottom: 0; }
.partie h3 { display: flex; align-items: center; gap: 10px; margin: 0; font-size: 1.125rem; letter-spacing: -.01em; }
.partie h3 .icone-profil { color: var(--action-ink); }
.partie__aide { margin: -8px 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); line-height: 1.5; }
.deux { display: grid; gap: 14px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.deux--lien { grid-template-columns: minmax(0, 1fr) 7rem; }
.avec-compteur { position: relative; }
.compteur { position: absolute; top: 0; right: 0; font-size: .75rem; color: var(--ink-muted); font-variant-numeric: tabular-nums; }
.compteur--proche { color: var(--proof-ink); font-weight: 700; }

.champ-natif { display: grid; gap: 8px; }
.champ-natif > span:first-child { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.champ-natif__zone { position: relative; display: flex; align-items: center; }
.champ-natif__zone .drapeau { position: absolute; left: 12px; pointer-events: none; }
.champ-natif__zone select { width: 100%; min-height: 48px; border-radius: 12px; }
.champ-natif__zone .drapeau + select { padding-inline-start: 44px; }
.drapeau { width: 22px; height: 16px; border-radius: 3px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }

.groupe { border: 0; margin: 0; padding: 0; display: grid; gap: 10px; min-width: 0; }
.groupe legend { padding: 0; margin-bottom: 10px; font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.disponibilites { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.disponibilite { position: relative; display: flex; align-items: center; gap: 10px; min-height: 52px; padding: 10px 14px; border: 1px solid var(--line-strong); border-radius: 14px; background: var(--surface); cursor: pointer; font-weight: 600; font-size: .9375rem; line-height: 1.25; }
.disponibilite input { position: absolute; opacity: 0; pointer-events: none; }
.disponibilite i { width: 10px; height: 10px; flex: none; border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible i { background: #1FA35B; }
.disponibilite--occupe i { background: #F2A541; }
.disponibilite:has(input:checked) { border-color: var(--action); background: var(--action-soft); box-shadow: inset 0 0 0 1px var(--action); }
.disponibilite:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.heures { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.heures__autre { display: inline-flex; align-items: center; gap: 6px; }
.heures__autre input { width: 6.5rem; min-height: 40px !important; border-radius: 50px !important; text-align: center; }

.pastilles { display: flex; flex-wrap: wrap; gap: 8px; }
.choix { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 14px; border: 1px solid var(--line-strong); border-radius: 50px; background: var(--surface); color: var(--ink); font-weight: 600; font-size: .875rem; cursor: pointer; transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out); }
.choix:hover:not(:disabled) { border-color: var(--action); }
.choix[aria-pressed='true'] { background: var(--action-soft); border-color: var(--action); color: var(--action-ink); }
.choix--etoile .icone-profil { color: var(--line-strong); }
.choix--etoile[aria-pressed='true'] .icone-profil { color: var(--proof); fill: var(--proof); }
.choix:disabled { opacity: .45; cursor: not-allowed; }

.lien-saisie { display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: 12px; align-items: start; }
.lien-saisie__icone { display: grid; place-items: center; width: 40px; height: 40px; margin-top: 26px; border-radius: 12px; background: var(--surface-raised); color: var(--ink); }
.lien-saisie:first-child .lien-saisie__icone, .lien-saisie__github + * { margin-top: 0; }
.lien-saisie__github { display: grid; gap: 2px; padding-top: 0; min-height: 40px; align-content: center; }
.lien-saisie:has(.lien-saisie__github) .lien-saisie__icone { margin-top: 0; }

.realisations-saisie { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.realisation-saisie { border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }
.realisation-saisie--ouverte { border-color: var(--line-strong); box-shadow: var(--ombre-carte); }
.realisation-saisie__tete { display: flex; align-items: center; gap: 8px; padding: 6px 10px 6px 6px; }
.realisation-saisie__bascule { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; min-height: 52px; padding: 6px 8px; border: 0; border-radius: 12px; background: transparent; color: var(--ink); text-align: start; cursor: pointer; }
.realisation-saisie__bascule:hover { background: var(--surface-raised); }
.realisation-saisie__numero { display: grid; place-items: center; width: 30px; height: 30px; flex: none; border-radius: 50%; background: var(--proof-soft); color: var(--proof-ink); font-weight: 700; font-size: .875rem; }
.realisation-saisie__resume { flex: 1; display: grid; min-width: 0; }
.realisation-saisie__resume strong, .realisation-saisie__resume small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.realisation-saisie__resume small { color: var(--ink-muted); }
.realisation-saisie__retirer { flex: none; min-height: 36px; padding: 0 10px; border: 0; border-radius: 50px; background: transparent; color: var(--danger); font-weight: 600; font-size: .8125rem; cursor: pointer; }
.realisation-saisie__retirer:hover { background: color-mix(in srgb, var(--danger) 10%, transparent); }
.realisation-saisie__corps { display: grid; gap: 14px; padding: 6px 16px 18px; }
.ajouter { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 56px; padding: 12px 16px; border: 1px dashed var(--line-strong); border-radius: 16px; background: transparent; color: var(--action-ink); font-weight: 700; cursor: pointer; }
.ajouter .texte-discret { margin-inline-start: auto; font-weight: 500; }
.ajouter:hover { background: var(--action-soft); border-color: var(--action); }

.editeur__pied { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 24px; background: var(--surface); border-top: 1px solid var(--line); }
.editeur__pied p { margin: 0; }
.editeur__etat { display: flex; align-items: center; gap: 8px; color: var(--ink-muted); font-size: .875rem; }
.editeur__etat i { width: 8px; height: 8px; border-radius: 50%; background: var(--proof); animation: pulser 1.6s ease-in-out infinite; }
@keyframes pulser { 50% { opacity: .35; } }
.editeur__boutons { display: flex; gap: 8px; margin-inline-start: auto; }
.sortie { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; width: 100%; }
.sortie p { font-weight: 600; }
.sortie div { display: flex; gap: 8px; }
.erreur { color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); margin: 0; }

@media (max-width: 640px) {
  .editeur__panneau { width: 100%; border-inline-start: 0; }
  .editeur__tete { padding: 16px 16px 0; }
  .editeur__corps { padding: 4px 16px 24px; }
  .editeur__pied { padding: 12px 16px; }
  .deux, .deux--lien { grid-template-columns: minmax(0, 1fr); }
  .disponibilites { grid-template-columns: minmax(0, 1fr); }
}
@media (prefers-reduced-motion: reduce) {
  .editeur__panneau, .editeur__voile { animation: none; }
  .editeur__etat i { animation: none; }
  .editeur__corps { scroll-behavior: auto; }
}
</style>
