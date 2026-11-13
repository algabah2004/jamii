<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { RouterLink } from 'vue-router'
import { JAvatar, JBadgePreuve, JBouton, JChamp, JIllustration, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import { DISPONIBILITES, lireCompetences, PAYS_AFRIQUE, pourcentage, type Disponibilite, type MonProfil } from './profils'

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
const prenom = computed(() => profil.value?.nom.split(/\s+/)[0] ?? '')
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const

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
const brouillon = ref({ bio: '', pays: '', ville: '', disponibilite: 'disponible' as Disponibilite, heures: '', competences: '' })
const erreurs = ref<Record<string, string>>({})

const competencesDeclarees = computed(() =>
  (profil.value?.competences ?? []).filter((c) => c.source === 'declaree').map((c) => c.competence),
)
const paysTries = computed(() =>
  [...PAYS_AFRIQUE].sort((a, b) => nomPays(a, locale.value).localeCompare(nomPays(b, locale.value), locale.value)),
)

function ouvrirEdition() {
  const p = profil.value!
  brouillon.value = {
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
      erreurs.value = Object.fromEntries(champs.map((e) => [e.champ.replace(/\[\d+\]$/, ''), t('profil.modifier.invalide')]))
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
        <p class="accueil-profil__intro">{{ t('profil.generation.texte') }}</p>
        <ul class="accueil-profil__lu">
          <li v-for="cle in ['langages', 'sujets', 'etoiles']" :key="cle">✓ {{ t(`profil.generation.lu.${cle}`) }}</li>
          <li class="jamais">✕ {{ t('profil.generation.lu.jamais') }}</li>
        </ul>
        <p v-if="sansGithub" class="texte-discret">{{ t('profil.generation.sansGithub') }}</p>
        <template v-else>
          <p v-if="enCours" role="status" class="en-cours" data-test="en-cours">{{ t('profil.generation.enCours') }}</p>
          <JBouton v-else :occupe="occupe" data-test="generer" @click="generer">{{ t('profil.generation.bouton') }}</JBouton>
          <p class="texte-discret">{{ t('profil.generation.prive') }}</p>
        </template>
        <p v-if="echec" role="alert" class="erreur">{{ t(`profil.generation.erreurs.${echec}`) }}</p>
        <p v-if="message" role="alert" class="erreur">{{ message }}</p>
      </div>
      <div class="accueil-profil__visuel">
        <JIllustration nom="profil-prouve" />
      </div>
    </div>

    <template v-else-if="profil">
      <header class="carte identite">
        <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="72" />
        <div class="identite__texte">
          <h1>{{ profil.nom }}</h1>
          <p class="texte-discret">
            <span v-if="profil.loginGithub">@{{ profil.loginGithub }}</span>
            <span v-if="profil.ville || profil.pays">
              · {{ [profil.ville, profil.pays && nomPays(profil.pays, locale)].filter(Boolean).join(', ') }}</span>
          </p>
          <p class="identite__dispo">
            <span class="disponibilite" :class="`disponibilite--${profil.disponibilite}`">
              {{ t(`profil.disponibilite.${profil.disponibilite}`) }}</span>
            <span v-if="profil.heuresParSemaine" class="texte-discret">
              · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</span>
          </p>
          <ul v-if="profil.competences.length" class="etiquettes etiquettes--entete">
            <li v-for="c in profil.competences.slice(0, 6)" :key="c.competence" class="etiquette">{{ c.competence }}</li>
          </ul>
        </div>
      </header>

      <div class="barre-actions">
        <p class="visibilite" :class="{ 'visibilite--publie': profil.publie }" data-test="visibilite">
          {{ profil.publie ? t('profil.visibilite.publie') : t('profil.visibilite.prive') }}
        </p>
        <JBouton :variante="profil.publie ? 'secondaire' : 'principal'" :occupe="occupe" data-test="publier" @click="changerVisibilite">
          {{ profil.publie ? t('profil.visibilite.retirer') : t('profil.visibilite.publier') }}
        </JBouton>
        <JBouton v-if="!edition" variante="secondaire" data-test="modifier" @click="ouvrirEdition">{{ t('profil.modifier.bouton') }}</JBouton>
        <JBouton variante="discret" :occupe="occupe || enCours" data-test="regenerer" @click="generer">
          {{ enCours ? t('profil.generation.enCours') : t('profil.generation.regenerer') }}
        </JBouton>
      </div>
      <p v-if="message" role="status" data-test="message">{{ message }}</p>
      <p v-if="echec" role="alert">{{ t(`profil.generation.erreurs.${echec}`) }}</p>

      <form v-if="edition" class="carte formulaire-profil" data-test="formulaire" @submit.prevent="enregistrer">
        <h2>{{ t('profil.modifier.titre') }}</h2>
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
        <JChamp
          v-model="brouillon.competences"
          :libelle="t('profil.modifier.competences')"
          :aide="t('profil.modifier.competencesAide')"
          :erreur="erreurs.competencesDeclarees"
        />
        <div class="barre-actions">
          <JBouton type="submit" :occupe="occupe" data-test="enregistrer">{{ t('profil.modifier.enregistrer') }}</JBouton>
          <JBouton variante="discret" @click="edition = false">{{ t('profil.modifier.annuler') }}</JBouton>
        </div>
      </form>

      <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>

      <div class="colonnes">
        <div class="colonne">
          <!-- Les projets d'abord : c'est la preuve la plus forte (contrat : Profil.projets). -->
          <section class="carte">
            <h2>{{ t('profil.projets.titre') }}</h2>
            <ul v-if="profil.projets.length" class="projets" data-test="projets">
              <li v-for="p in profil.projets" :key="p.projetId">
                <div>
                  <strong>{{ p.titre }}</strong>
                  <p class="texte-discret">{{ p.role }}<template v-if="p.paysEquipe?.length"> · {{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</template></p>
                </div>
                <JBadgePreuve v-if="p.enLigne">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
              </li>
            </ul>
            <div v-else class="vide" data-test="projets-vide">
              <JIllustration nom="en-ligne" class="vide__illustration" />
              <p>{{ t('profil.projets.vide') }}</p>
              <RouterLink to="/vitrine">{{ t('profil.projets.inspiration') }}</RouterLink>
            </div>
          </section>

          <section v-if="profil.langages.length" class="carte" :aria-label="t('profil.langages.titre')">
            <h2>{{ t('profil.langages.titre') }}</h2>
            <ul class="langages" data-test="langages">
              <li v-for="l in profil.langages" :key="l.nom">
                <span>{{ l.nom }}</span>
                <span class="jauge" aria-hidden="true"><span :style="{ width: `${Math.max(l.part * 100, 2)}%` }" /></span>
                <span class="texte-discret">{{ pourcentage(l.part, locale) }}</span>
              </li>
            </ul>
            <p class="texte-discret">{{ t('profil.langages.estimation') }}</p>
          </section>

          <section v-if="profil.depotsGithub.length" class="carte">
            <h2>{{ t('profil.depots.titre') }}</h2>
            <ul class="depots" data-test="depots">
              <li v-for="d in profil.depotsGithub" :key="d.url">
                <a :href="d.url" rel="noopener noreferrer" target="_blank">{{ d.nom }}</a>
                <span class="texte-discret">
                  <template v-if="d.langage">{{ d.langage }} · </template>{{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span>
                <p v-if="d.description">{{ d.description }}</p>
              </li>
            </ul>
            <p class="texte-discret">{{ t('profil.depots.pasUnePreuve') }}</p>
          </section>
        </div>

        <aside class="colonne">
          <!-- L'indice de fiabilité : un point par type de preuve, jamais de note globale. -->
          <section class="carte fiabilite">
            <h2>{{ t('profil.fiabilite.titre', { prenom: prenom }) }}</h2>
            <ul v-if="profil.fiabilite.length" class="points" data-test="fiabilite">
              <li v-for="f in profil.fiabilite" :key="f.type">
                <span class="points__nombre">{{ f.sur != null ? t('profil.fiabilite.sur', { n: f.nombre, sur: f.sur }) : f.nombre }}</span>
                <span>{{ t(`profil.fiabilite.types.${f.type}`, f.nombre) }}</span>
                <small v-if="f.contestees" class="texte-discret">{{ t('profil.fiabilite.contestees', { n: f.contestees }, f.contestees) }}</small>
              </li>
            </ul>
            <template v-else>
              <p class="texte-discret" data-test="fiabilite-vide">{{ t('profil.fiabilite.vide') }}</p>
              <ul class="comment">
                <li v-for="type in TYPES_PREUVE" :key="type">{{ t(`profil.fiabilite.comment.${type}`) }}</li>
              </ul>
            </template>
            <p class="texte-discret">{{ t('profil.fiabilite.sansNote') }}</p>
          </section>

          <section v-if="profil.competences.length" class="carte">
            <h2>{{ t('profil.competences.titre') }}</h2>
            <ul class="etiquettes" data-test="competences">
              <li v-for="c in profil.competences" :key="c.competence + c.source" class="etiquette" :class="{ 'etiquette--declaree': c.source === 'declaree' }">
                {{ c.competence }}
                <span v-if="c.source === 'declaree'" class="visuellement-cache">{{ t('profil.competences.declaree') }}</span>
              </li>
            </ul>
            <p class="texte-discret">{{ t('profil.competences.source') }}</p>
          </section>
        </aside>
      </div>
    </template>
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
.erreur { color: var(--danger); font-weight: 600; }
.identite { flex-direction: row; display: flex; gap: var(--space-6); align-items: center; flex-wrap: wrap; }
.identite__texte { display: grid; gap: var(--space-1); min-width: 0; flex: 1; }
.identite__texte h1, .identite__texte p { margin: 0; }
.etiquettes--entete { margin: var(--space-2) 0 0; }
.visibilite { padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--surface-raised); }
.visibilite--publie { background: var(--action-soft); }
.colonnes { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 860px) { .colonnes { grid-template-columns: 1fr; } }
.colonne { display: grid; gap: var(--space-6); min-width: 0; }
.carte h2 { font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.projets { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.projets li { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; padding: var(--space-3) var(--space-4); background: var(--surface-raised); border-radius: var(--radius-sm); }
.projets p { margin: 0; }
.vide { display: grid; gap: var(--space-3); justify-items: center; text-align: center; padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); }
.vide p { margin: 0; color: var(--ink-muted); max-width: 30rem; }
.vide__illustration { width: 9rem; }
.points { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.points li { display: grid; grid-template-columns: auto 1fr; gap: 2px var(--space-3); align-items: baseline; padding: var(--space-3); background: var(--proof-soft); border-radius: var(--radius-sm); }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: 1.25rem; color: var(--proof-ink); }
.points small { grid-column: 2; }
.comment { margin: 0 0 var(--space-3); padding-inline-start: 1.2em; display: grid; gap: var(--space-1); }
.etiquette--declaree { background: transparent; border: 1px solid var(--line-strong); }
.disponibilite { font-weight: 600; }
.disponibilite::before { content: ''; display: inline-block; width: 0.6em; height: 0.6em; margin-inline-end: var(--space-1); border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible::before { background: var(--action); }
.barre-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.barre-actions .visibilite { margin: 0; }
.formulaire-profil { display: grid; gap: var(--space-4); }
.deux-colonnes { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); }
.j-champ > span { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.selection { width: 100%; min-height: 44px; padding: var(--space-2) var(--space-3); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }
.bio { max-width: 40rem; white-space: pre-line; }
.langages { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.langages li { display: grid; grid-template-columns: 7rem 1fr 3.5rem; gap: var(--space-3); align-items: center; }
.jauge { height: 8px; background: var(--surface-raised); border-radius: var(--radius-pill); overflow: hidden; }
.jauge > span { display: block; height: 100%; background: var(--action); border-radius: var(--radius-pill); }
.etiquettes { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.depots { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-4); }
.depots li { display: grid; gap: var(--space-1); }
.depots p { margin: 0; }
.depots a { font-weight: 600; }
</style>
