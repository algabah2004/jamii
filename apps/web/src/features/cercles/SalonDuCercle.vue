<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import TableauDuTour from './TableauDuTour.vue'
import { useSession } from '../../session'
import { ilYa } from '../vitrine/vitrine'
import type { Avancement, Cercle } from './cercles'
import {
  envoyer,
  fusionner,
  lancerAppel,
  LIMITES_SALON,
  lignesDuSalon,
  lireSalon,
  prenom,
  resoudre,
  terminerAppel,
  type AppelCercle,
  type MessageCercle,
  type ModeSaisie,
} from './salon'

/*
 * Le salon du cercle (contracts/cercles.yaml v0.2) : pas un salon de discussion comme les autres, celui d'une équipe
 * qui construit ensemble pendant deux semaines.
 * - En tête, où en est le tour, qui a fait son point du jour, et l'appel du cercle.
 * - Les coups de main ouverts restent épinglés tant que personne ne les a réglés : on aide d'abord qui bloque.
 * - Le fil mêle les messages, les points du jour (fait, prévu, bloqué) et les étapes du cercle racontées par Jamii.
 * - On relit les nouveautés toutes les 4 secondes (`apres`), et on s'arrête quand l'onglet est caché.
 * Les textes sont affichés échappés (jamais v-html).
 */
const props = defineProps<{ cercle: Cercle; suivi: Avancement | null; titreTour: string | null }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const INTERVALLE = 4000

const messages = ref<MessageCercle[]>([])
const coupsDeMain = ref<MessageCercle[]>([])
const pointsDuJour = ref<string[]>([])
const appel = ref<AppelCercle | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const coupe = ref(false)

const lignes = computed(() => lignesDuSalon(messages.value))
const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)
const membres = computed(() => props.cercle.membres.map((m) => m.personne))
const pointFait = computed(() => !!moi.value && pointsDuJour.value.includes(moi.value.id))

// ---------------------------------------------------------------- lecture en direct

const liste = ref<HTMLElement | null>(null)
const nonVus = ref(0)
let minuterie: ReturnType<typeof setInterval> | null = null
let lecture = 0

const enBas = () => {
  const el = liste.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
}
function allerEnBas() {
  void nextTick(() => {
    if (liste.value) liste.value.scrollTop = liste.value.scrollHeight
    nonVus.value = 0
  })
}

async function lire(complet = false) {
  const numero = ++lecture
  const apres = complet ? undefined : messages.value.at(-1)?.numero
  try {
    const r = await lireSalon(props.cercle.id, apres)
    if (numero !== lecture) return
    if (!r.ok) {
      if (complet) etat.value = 'indisponible'
      else coupe.value = true
      return
    }
    coupe.value = false
    const etaitEnBas = enBas()
    const avant = messages.value.length
    messages.value = complet ? r.salon.elements : fusionner(messages.value, r.salon.elements)
    coupsDeMain.value = r.salon.coupsDeMain
    pointsDuJour.value = r.salon.pointsDuJour
    appel.value = r.salon.appel
    etat.value = 'pret'
    if (complet || etaitEnBas) allerEnBas()
    else nonVus.value += Math.max(0, messages.value.length - avant)
  } catch {
    if (numero !== lecture) return
    if (complet) etat.value = 'indisponible'
    else coupe.value = true
  }
}

function demarrer() {
  arreter()
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
  minuterie = setInterval(() => void lire(), INTERVALLE)
}
function arreter() {
  if (minuterie) clearInterval(minuterie)
  minuterie = null
}
function visibilite() {
  if (document.visibilityState === 'hidden') return arreter()
  void lire()
  demarrer()
}
onMounted(() => {
  void lire(true)
  demarrer()
  document.addEventListener('visibilitychange', visibilite)
})
onBeforeUnmount(() => {
  arreter()
  document.removeEventListener('visibilitychange', visibilite)
})

// ---------------------------------------------------------------- écrire

const mode = ref<ModeSaisie>('texte')
const texte = ref('')
const point = ref({ fait: '', prevu: '', bloque: '' })
const envoi = ref(false)
const erreur = ref<string | null>(null)
const zone = ref<HTMLTextAreaElement | null>(null)

const pret = computed(() => {
  if (envoi.value) return false
  if (mode.value === 'point') return !pointFait.value && !!point.value.fait.trim() && !!point.value.prevu.trim()
  return !!texte.value.trim()
})

function choisir(m: ModeSaisie) {
  mode.value = m
  erreur.value = null
  void nextTick(() => (m === 'point' ? document.getElementById('point-fait')?.focus() : zone.value?.focus()))
}

async function soumettre() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r =
      mode.value === 'point'
        ? await envoyer(props.cercle.id, {
            type: 'point',
            point: { fait: point.value.fait.trim(), prevu: point.value.prevu.trim(), bloque: point.value.bloque.trim() || null },
          })
        : await envoyer(props.cercle.id, { type: mode.value, texte: texte.value.trim() })
    if (!r.ok) {
      erreur.value = t(
        r.code === 'point_deja_fait' ? 'cercles.salon.erreurs.pointDejaFait' : r.statut === 429 ? 'cercles.salon.erreurs.trop' : 'cercles.salon.erreurs.envoi',
      )
      return
    }
    messages.value = fusionner(messages.value, [r.message])
    if (r.message.type === 'coup_de_main') coupsDeMain.value = [...coupsDeMain.value, r.message]
    if (r.message.type === 'point' && moi.value) pointsDuJour.value = [...pointsDuJour.value, moi.value.id]
    texte.value = ''
    point.value = { fait: '', prevu: '', bloque: '' }
    if (mode.value !== 'texte') mode.value = 'texte'
    allerEnBas()
  } catch {
    erreur.value = t('cercles.salon.erreurs.envoi')
  } finally {
    envoi.value = false
  }
}

/** Entrée envoie, Maj+Entrée va à la ligne (comme partout ailleurs dans Jamii). */
function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void soumettre()
  }
}

// ---------------------------------------------------------------- coups de main

const enCours = ref<string | null>(null)

/** « Je peux aider » : on répond dans le fil, en s'adressant à la personne. */
function aider(m: MessageCercle) {
  mode.value = 'texte'
  texte.value = `@${prenom(m.auteur?.nom)} `
  void nextTick(() => {
    zone.value?.focus()
    zone.value?.setSelectionRange(texte.value.length, texte.value.length)
  })
}

async function regler(m: MessageCercle) {
  enCours.value = m.id
  try {
    const resolu = await resoudre(props.cercle.id, m.id)
    if (resolu) {
      coupsDeMain.value = coupsDeMain.value.filter((x) => x.id !== m.id)
      messages.value = messages.value.map((x) => (x.id === resolu.id ? resolu : x))
    }
  } finally {
    enCours.value = null
  }
}

// ---------------------------------------------------------------- l'appel du cercle

const etatAppel = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const peutTerminer = computed(() => !!appel.value && !!moi.value && (appel.value.lancePar?.id === moi.value.id || props.cercle.moi.estCreateur))

/** L'onglet de l'appel s'ouvre pendant le clic (sinon le navigateur le bloque), puis reçoit l'adresse de la salle. */
async function appeler() {
  if (etatAppel.value === 'envoi') return
  etatAppel.value = 'envoi'
  const fenetre = typeof window.open === 'function' ? window.open('', '_blank') : null
  try {
    const r = await lancerAppel(props.cercle.id)
    if (!r.appel?.url) {
      fenetre?.close()
      etatAppel.value = r.statut === 429 ? 'trop' : 'erreur'
      return
    }
    if (fenetre) {
      fenetre.opener = null
      fenetre.location.href = r.appel.url
    }
    appel.value = r.appel
    etatAppel.value = 'repos'
    void lire()
  } catch {
    fenetre?.close()
    etatAppel.value = 'erreur'
  }
}
async function raccrocher() {
  await terminerAppel(props.cercle.id)
  appel.value = null
  void lire()
}

// ---------------------------------------------------------------- affichage

const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
function jour(date: Date) {
  const aujourdhui = new Date()
  const hier = new Date(aujourdhui.getTime() - 86_400_000)
  const meme = (a: Date, b: Date) => a.toDateString() === b.toDateString()
  if (meme(date, aujourdhui)) return t('cercles.salon.aujourdhui')
  if (meme(date, hier)) return t('cercles.salon.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(date)
}
const ICONES: Record<string, string> = {
  cercle_cree: '✨',
  membre_rejoint: '👋',
  tirage: '🎲',
  tour_ouvert: '🚀',
  tour_livre: '✅',
  cercle_termine: '🏁',
}
function evenement(m: MessageCercle) {
  const e = m.evenement!
  return t(`cercles.salon.evenements.${e.code}`, { nom: prenom(e.personne?.nom) || t('cercles.salon.quelquun'), n: e.tour ?? '' })
}
const contexte = computed(() => {
  const c = props.cercle
  if (c.statut === 'termine') return t('cercles.salon.contexteTermine')
  if (!c.tirage) return t('cercles.salon.contexteAvant', { n: c.membres.length, taille: c.taille })
  if (!tour.value) return ''
  const base = t('cercles.salon.contexteTour', { n: tour.value.numero, titre: props.titreTour ?? t('cercles.projetDe', { nom: prenom(tour.value.beneficiaire.nom) }) })
  return props.suivi ? `${base} · ${t('cercles.salon.contexteJour', { jour: props.suivi.jour, faites: props.suivi.faites, total: props.suivi.total })}` : base
})
</script>

<template>
  <section class="salon" aria-labelledby="salon-titre" data-test="salon-cercle">
    <header class="salon__tete carte">
      <div class="salon__titres">
        <h2 id="salon-titre">{{ t('cercles.salon.titre') }}</h2>
        <p v-if="contexte" class="salon__contexte" data-test="contexte">{{ contexte }}</p>
      </div>

      <div class="presence" data-test="points-du-jour">
        <ul class="presence__avatars" :aria-label="t('cercles.salon.pointsAria')">
          <li v-for="p in membres" :key="p.id" :class="{ fait: pointsDuJour.includes(p.id) }" :title="pointsDuJour.includes(p.id) ? t('cercles.salon.aFaitSonPoint', { nom: prenom(p.nom) }) : prenom(p.nom)">
            <LienPersonne :id="p.id" :nom="p.nom"><JAvatar :nom="p.nom" :url="p.avatarUrl" :taille="34" /></LienPersonne>
            <span v-if="pointsDuJour.includes(p.id)" class="presence__coche" aria-hidden="true">✓</span>
          </li>
        </ul>
        <p class="presence__texte">{{ t('cercles.salon.pointsCompte', { n: pointsDuJour.length, total: membres.length }) }}</p>
      </div>

      <div class="appel" :class="{ 'appel--actif': appel }" data-test="appel-cercle">
        <template v-if="appel">
          <span class="appel__pulse" aria-hidden="true" />
          <span class="appel__texte">{{ t('cercles.salon.appelEnCours', { nom: prenom(appel.lancePar?.nom) || t('cercles.salon.quelquun'), quand: ilYa(appel.lanceLe, locale) }) }}</span>
          <a :href="appel.url" target="_blank" rel="noopener noreferrer" class="appel__bouton" data-test="rejoindre-appel">{{ t('cercles.salon.rejoindre') }}</a>
          <button v-if="peutTerminer" type="button" class="appel__fin" data-test="terminer-appel" @click="raccrocher">{{ t('cercles.salon.terminer') }}</button>
        </template>
        <button v-else type="button" class="appel__bouton" :disabled="etatAppel === 'envoi'" :aria-busy="etatAppel === 'envoi' || undefined" data-test="lancer-appel" @click="appeler">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h7A1.5 1.5 0 0 1 13 6.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 3 13.5zM13 9l4-2.5v7L13 11" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
          {{ t('cercles.salon.lancerAppel') }}
        </button>
        <p v-if="etatAppel === 'erreur' || etatAppel === 'trop'" role="alert" class="erreur">{{ t(etatAppel === 'trop' ? 'cercles.salon.erreurs.tropAppels' : 'etat.indisponible') }}</p>
      </div>
    </header>

    <p v-if="etat === 'chargement'" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'indisponible'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>

    <div v-else class="salon__grille">
    <div class="salon__corps carte">
      <!-- Épinglés : on aide d'abord qui bloque. -->
      <section v-if="coupsDeMain.length" class="coups" aria-labelledby="coups-titre" data-test="coups-de-main">
        <h3 id="coups-titre">{{ t('cercles.salon.coupsTitre', { n: coupsDeMain.length }, coupsDeMain.length) }}</h3>
        <ul>
          <li v-for="c in coupsDeMain" :key="c.id" class="coup" data-test="coup-de-main">
            <LienPersonne :id="c.auteur?.id" :nom="c.auteur?.nom"><JAvatar :nom="c.auteur?.nom ?? '?'" :url="c.auteur?.avatarUrl" :taille="32" /></LienPersonne>
            <div class="coup__corps">
              <p class="coup__qui"><strong>{{ prenom(c.auteur?.nom) }}</strong> <span class="texte-discret">· {{ ilYa(c.envoyeLe, locale) }}</span></p>
              <p class="coup__texte">{{ c.texte }}</p>
            </div>
            <div class="coup__actions">
              <button v-if="c.auteur?.id !== moi?.id" type="button" class="pilule" data-test="aider" @click="aider(c)">{{ t('cercles.salon.aider') }}</button>
              <button type="button" class="pilule pilule--ok" :disabled="enCours === c.id" data-test="regler" @click="regler(c)">{{ t('cercles.salon.regle') }}</button>
            </div>
          </li>
        </ul>
      </section>

      <div class="fil-cadre">
        <ol ref="liste" class="fil" aria-live="polite" :aria-label="t('cercles.salon.fil')" @scroll="enBas() && (nonVus = 0)">
          <li v-if="!messages.length" class="vide">{{ t('cercles.salon.vide') }}</li>
          <template v-for="l in lignes" :key="l.cle">
            <li v-if="l.type === 'jour'" class="jour" role="separator"><span>{{ jour(l.date) }}</span></li>

            <li v-else-if="l.message.type === 'evenement'" class="evenement" :class="`evenement--${l.message.evenement?.code}`" data-test="evenement">
              <span class="evenement__icone" aria-hidden="true">{{ ICONES[l.message.evenement?.code ?? ''] }}</span>
              <span>{{ evenement(l.message) }}</span>
              <time class="evenement__heure" :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
            </li>

            <li v-else class="message" :class="[`message--${l.message.type}`, { 'message--suite': l.suite }]" data-test="message">
              <span class="message__avatar">
                <LienPersonne v-if="!l.suite" :id="l.message.auteur?.id" doublon><JAvatar :nom="l.message.auteur?.nom ?? '?'" :url="l.message.auteur?.avatarUrl" :taille="38" /></LienPersonne>
              </span>
              <div class="message__corps">
                <p v-if="!l.suite" class="message__meta">
                  <strong><LienPersonne :id="l.message.auteur?.id">{{ l.message.auteur?.nom ?? t('communautes.compteSupprime') }}</LienPersonne></strong>
                  <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
                </p>

                <p v-if="l.message.type === 'texte'" class="message__texte">{{ l.message.texte }}</p>

                <div v-else-if="l.message.type === 'point' && l.message.point" class="point-jour" data-test="point-du-jour">
                  <p class="point-jour__titre">{{ t('cercles.salon.sonPoint') }}</p>
                  <dl>
                    <div><dt>{{ t('cercles.salon.fait') }}</dt><dd>{{ l.message.point.fait }}</dd></div>
                    <div><dt>{{ t('cercles.salon.prevu') }}</dt><dd>{{ l.message.point.prevu }}</dd></div>
                    <div v-if="l.message.point.bloque" class="point-jour__bloque"><dt>{{ t('cercles.salon.bloque') }}</dt><dd>{{ l.message.point.bloque }}</dd></div>
                  </dl>
                </div>

                <div v-else-if="l.message.type === 'coup_de_main'" class="demande" :class="{ 'demande--reglee': l.message.coupDeMain?.resoluLe }">
                  <p class="demande__titre">{{ t('cercles.salon.demandeTitre') }}</p>
                  <p class="message__texte">{{ l.message.texte }}</p>
                  <p v-if="l.message.coupDeMain?.resoluLe" class="demande__statut">✓ {{ l.message.coupDeMain.resoluPar ? t('cercles.salon.regleePar', { nom: prenom(l.message.coupDeMain.resoluPar.nom) }) : t('cercles.salon.reglee') }}</p>
                </div>

                <div v-else-if="l.message.type === 'decision'" class="decision-fil" data-test="decision-fil">
                  <p class="decision-fil__titre">📌 {{ t('cercles.qg.decisionPrise') }}</p>
                  <p class="message__texte">{{ l.message.texte }}</p>
                </div>

                <div v-else-if="l.message.type === 'appel'" class="annonce-appel">
                  <span>{{ t('cercles.salon.aLanceAppel') }}</span>
                  <a v-if="l.message.appel?.enCours && appel && appel.id === l.message.appel.id" :href="appel.url" target="_blank" rel="noopener noreferrer" class="pilule">{{ t('cercles.salon.rejoindre') }}</a>
                  <span v-else class="texte-discret">{{ t('cercles.salon.appelTermine') }}</span>
                </div>
              </div>
            </li>
          </template>
        </ol>
        <button v-if="nonVus" type="button" class="nouveaux" @click="allerEnBas">{{ t('cercles.salon.nouveaux', { n: nonVus }, nonVus) }}</button>
      </div>
      <p v-if="coupe" class="texte-discret coupe" role="status">{{ t('cercles.salon.coupe') }}</p>

      <form class="saisie" data-test="saisie" @submit.prevent="soumettre">
        <div class="modes" role="radiogroup" :aria-label="t('cercles.salon.modes')">
          <button type="button" role="radio" :aria-checked="mode === 'texte'" class="mode" :class="{ actif: mode === 'texte' }" data-test="mode-texte" @click="choisir('texte')">{{ t('cercles.salon.modeTexte') }}</button>
          <button type="button" role="radio" :aria-checked="mode === 'point'" class="mode" :class="{ actif: mode === 'point' }" data-test="mode-point" @click="choisir('point')">
            {{ t('cercles.salon.modePoint') }}<span v-if="pointFait" class="mode__fait" aria-hidden="true"> ✓</span>
          </button>
          <button type="button" role="radio" :aria-checked="mode === 'coup_de_main'" class="mode mode--bloque" :class="{ actif: mode === 'coup_de_main' }" data-test="mode-bloque" @click="choisir('coup_de_main')">{{ t('cercles.salon.modeBloque') }}</button>
        </div>

        <template v-if="mode === 'point'">
          <p v-if="pointFait" class="texte-discret" data-test="point-deja-fait">{{ t('cercles.salon.pointFaitAujourdhui') }}</p>
          <div v-else class="point-champs">
            <label for="point-fait">{{ t('cercles.salon.fait') }}</label>
            <textarea id="point-fait" v-model="point.fait" rows="2" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.faitAide')" />
            <label for="point-prevu">{{ t('cercles.salon.prevu') }}</label>
            <textarea id="point-prevu" v-model="point.prevu" rows="2" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.prevuAide')" />
            <label for="point-bloque">{{ t('cercles.salon.bloqueFacultatif') }}</label>
            <textarea id="point-bloque" v-model="point.bloque" rows="1" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.bloqueAide')" />
          </div>
        </template>
        <div v-else class="zone" :class="{ 'zone--bloque': mode === 'coup_de_main' }">
          <label for="salon-texte" class="visuellement-cache">{{ t(mode === 'coup_de_main' ? 'cercles.salon.bloquePlaceholder' : 'cercles.salon.placeholder') }}</label>
          <textarea
            id="salon-texte"
            ref="zone"
            v-model="texte"
            rows="2"
            :maxlength="LIMITES_SALON.texte"
            :placeholder="t(mode === 'coup_de_main' ? 'cercles.salon.bloquePlaceholder' : 'cercles.salon.placeholder')"
            @keydown="touche"
          />
        </div>

        <div class="saisie__bas">
          <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
          <span v-else class="texte-discret saisie__aide">{{ mode === 'point' ? '' : t('cercles.salon.aideEntree') }}</span>
          <button type="submit" class="envoyer" :class="{ 'envoyer--bloque': mode === 'coup_de_main' }" :disabled="!pret" :aria-busy="envoi || undefined" data-test="envoyer">
            {{ t(mode === 'point' ? 'cercles.salon.envoyerPoint' : mode === 'coup_de_main' ? 'cercles.salon.envoyerBloque' : 'cercles.salon.envoyer') }}
          </button>
        </div>
      </form>
    </div>
    <TableauDuTour :key="tour?.projetId ?? 'aucun'" :projet-id="tour?.projetId ?? null" :membres="membres" :moi-id="moi?.id ?? null" :points-du-jour="pointsDuJour" />
    </div>
  </section>
</template>

<style scoped>
.salon { display: grid; gap: var(--space-4); }
.salon__tete { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-4) var(--space-6); align-items: center; }
.salon__titres { display: grid; gap: var(--space-1); min-width: 0; }
.salon__titres h2 { margin: 0; font-size: 1.375rem; }
.salon__contexte { margin: 0; color: var(--ink-muted); }

/* Qui a fait son point aujourd'hui : un anneau orange (la preuve) et une coche. */
.presence { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); grid-column: 1 / -1; }
.presence__avatars { display: flex; list-style: none; margin: 0; padding: 0; }
.presence__avatars li { position: relative; margin-inline-end: -6px; }
.presence__avatars li :deep(.j-avatar) { box-shadow: 0 0 0 2px var(--surface); opacity: 0.55; filter: grayscale(0.4); transition: opacity var(--motion-fast) var(--ease-out); }
.presence__avatars li.fait :deep(.j-avatar) { opacity: 1; filter: none; box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--proof); }
.presence__coche {
  position: absolute; right: -2px; bottom: -2px; display: grid; place-items: center; width: 16px; height: 16px; border-radius: 50%;
  background: var(--proof); color: #02070A; font-size: 10px; font-weight: 800; box-shadow: 0 0 0 2px var(--surface);
}
.presence__texte { margin: 0 0 0 var(--space-3); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.appel { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); grid-column: 2; grid-row: 1; justify-self: end; }
.appel--actif { padding: var(--space-2) var(--space-2) var(--space-2) var(--space-4); border-radius: var(--radius-pill); background: var(--action-soft); }
.appel__texte { color: var(--action-ink); font-weight: 600; font-size: var(--type-body-sm-size); }
.appel__pulse { width: 10px; height: 10px; border-radius: 50%; background: #2EAD5F; box-shadow: 0 0 0 0 rgb(46 173 95 / 0.6); animation: pulse 1.6s infinite; }
.appel__bouton {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; text-decoration: none; cursor: pointer;
}
.appel__bouton:disabled { opacity: 0.6; cursor: progress; }
.appel__fin { min-height: 44px; padding: 0 var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; cursor: pointer; }
.appel__bouton:focus-visible, .appel__fin:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes pulse { 70% { box-shadow: 0 0 0 8px rgb(46 173 95 / 0); } 100% { box-shadow: 0 0 0 0 rgb(46 173 95 / 0); } }

.salon__grille { display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 1fr) minmax(22rem, 26rem); align-items: start; }
@media (max-width: 1000px) { .salon__grille { grid-template-columns: minmax(0, 1fr); } }
.salon__corps { display: grid; gap: var(--space-4); padding: 0; overflow: hidden; }

/* Les coups de main épinglés : la seule zone chaude du salon. */
.coups { margin: 0; padding: var(--space-4) var(--space-6); background: var(--proof-soft); border-bottom: 1px solid color-mix(in srgb, var(--proof) 35%, transparent); }
.coups h3 { margin: 0 0 var(--space-3); color: var(--proof-ink); font-size: var(--type-body-size); }
.coups ul { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.coup { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: start; }
.coup__corps p { margin: 0; }
.coup__texte { overflow-wrap: anywhere; }
.coup__actions { display: flex; flex-wrap: wrap; gap: var(--space-2); justify-content: flex-end; }

.pilule {
  display: inline-flex; align-items: center; min-height: 36px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill);
  background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; text-decoration: none; cursor: pointer;
}
.pilule:hover { border-color: var(--action); }
.pilule--ok { border-color: color-mix(in srgb, var(--proof) 60%, transparent); }
.pilule:disabled { opacity: 0.6; cursor: progress; }
.pilule:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.fil-cadre { position: relative; }
.fil { list-style: none; margin: 0; padding: var(--space-2) var(--space-6); display: grid; gap: 2px; max-height: min(62vh, 640px); min-height: 260px; overflow-y: auto; overscroll-behavior: contain; align-content: start; }
.vide { padding: var(--space-8) 0; text-align: center; color: var(--ink-muted); }
.jour { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-4) 0 var(--space-2); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.jour::before, .jour::after { content: ''; flex: 1; height: 1px; background: var(--line); }
.jour span::first-letter { text-transform: uppercase; }

/* Les étapes du cercle : une ligne de chronique, centrée, discrète. */
.evenement { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: var(--space-2); margin: var(--space-2) 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); text-align: center; }
.evenement__icone { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--surface-raised); border: 1px solid var(--line); font-size: 13px; }
.evenement__heure { font-size: 0.75rem; opacity: 0.8; }
.evenement--tirage, .evenement--tour_ouvert, .evenement--tour_livre, .evenement--cercle_termine { color: var(--ink); font-weight: 600; }

.message { display: grid; grid-template-columns: 38px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-2) var(--space-2); margin: 0 calc(-1 * var(--space-2)); border-radius: var(--radius-md); }
.message:hover { background: var(--surface-raised); }
.message--suite { padding-top: 0; }
.message__corps { min-width: 0; display: grid; gap: var(--space-1); }
.message__meta { margin: 0; display: flex; align-items: baseline; gap: var(--space-2); }
.message__meta time { color: var(--ink-muted); font-size: 0.75rem; }
.message__texte { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }

.point-jour { padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-left: 3px solid var(--action); border-radius: var(--radius-md); background: var(--surface); max-width: 38rem; }
.point-jour__titre { margin: 0 0 var(--space-2); color: var(--action-ink); font-size: 0.8125rem; font-weight: 700; }
.point-jour dl { margin: 0; display: grid; gap: var(--space-2); }
.point-jour dl > div { display: grid; grid-template-columns: 5.5rem minmax(0, 1fr); gap: var(--space-2); }
.point-jour dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.point-jour dd { margin: 0; overflow-wrap: anywhere; }
.point-jour__bloque dt, .point-jour__bloque dd { color: var(--proof-ink); }

.demande { padding: var(--space-3) var(--space-4); border: 1px solid color-mix(in srgb, var(--proof) 55%, transparent); border-radius: var(--radius-md); background: var(--proof-soft); max-width: 38rem; display: grid; gap: var(--space-1); }
.demande__titre { margin: 0; color: var(--proof-ink); font-size: 0.8125rem; font-weight: 700; }
.demande--reglee { background: var(--surface); border-color: var(--line); }
.demande--reglee .demande__titre { color: var(--ink-muted); }
.demande__statut { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }

.decision-fil { padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-left: 4px solid var(--proof); border-radius: var(--radius-md); background: var(--surface); max-width: 38rem; display: grid; gap: var(--space-1); }
.decision-fil__titre { margin: 0; color: var(--proof-ink); font-size: 0.8125rem; font-weight: 700; }
.annonce-appel { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--action-soft); color: var(--action-ink); font-weight: 600; max-width: 38rem; }

.nouveaux { position: absolute; left: 50%; bottom: var(--space-3); translate: -50% 0; padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; box-shadow: 0 6px 20px rgb(2 7 10 / 0.25); }
.coupe { margin: 0 var(--space-6); }

/* La saisie : trois façons de parler au cercle. */
.saisie { display: grid; gap: var(--space-3); padding: var(--space-4) var(--space-6) var(--space-6); border-top: 1px solid var(--line); }
.modes { display: flex; flex-wrap: wrap; gap: var(--space-1); padding: 4px; border-radius: var(--radius-pill); background: var(--surface-raised); justify-self: start; }
.mode { min-height: 36px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill); background: transparent; color: var(--ink-muted); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }
.mode.actif { background: var(--surface); color: var(--ink); box-shadow: 0 1px 3px rgb(2 7 10 / 0.15); }
.mode--bloque.actif { background: var(--proof); color: #02070A; }
.mode:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.mode__fait { color: var(--proof-ink); }
.zone textarea, .point-champs textarea {
  width: 100%; box-sizing: border-box; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md);
  background: var(--surface-raised); color: var(--ink); font: inherit; resize: vertical;
}
.zone--bloque textarea { border-color: color-mix(in srgb, var(--proof) 60%, transparent); }
.zone textarea:focus-visible, .point-champs textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.point-champs { display: grid; grid-template-columns: 8rem minmax(0, 1fr); gap: var(--space-2) var(--space-3); align-items: start; }
.point-champs label { padding-top: var(--space-3); font-size: var(--type-body-sm-size); font-weight: 600; }
.saisie__bas { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.saisie__aide { font-size: 0.8125rem; }
.envoyer { min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; margin-inline-start: auto; }
.envoyer--bloque { background: var(--proof); color: #02070A; }
.envoyer:disabled { opacity: 0.5; cursor: not-allowed; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

@media (max-width: 720px) {
  .salon__tete { grid-template-columns: 1fr; }
  .appel { grid-column: 1; grid-row: auto; justify-self: start; }
  .coup { grid-template-columns: auto minmax(0, 1fr); }
  .coup__actions { grid-column: 2; justify-content: flex-start; }
  .point-champs { grid-template-columns: 1fr; }
  .point-champs label { padding-top: 0; }
  .fil { padding-inline: var(--space-3); }
}
@media (prefers-reduced-motion: reduce) { .appel__pulse { animation: none; } }
</style>
