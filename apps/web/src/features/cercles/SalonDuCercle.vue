<script setup lang="ts">
import { appelCercleActif, quitter as quitterAppelCercle, reduire as reduireAppelCercle, rejoindre, useAppelCercle } from './appelCercle'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import IconeCercle, { type NomIcone } from './IconeCercle.vue'
import Composeur from '../../components/texte/Composeur.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import type { PieceJointe } from '../../components/texte/fichiers'
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
// Un message, ou une demande d'aide (« Je bloque ») qui reste en vue tant que personne ne l'a réglée. Le point du jour a
// son propre outil (Point du soir) : le salon reste une conversation.

const bloque = ref(false)
const mode = computed<ModeSaisie>(() => (bloque.value ? 'coup_de_main' : 'texte'))
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
const pret = computed(() => !envoi.value && (!!texte.value.trim() || (!bloque.value && pieces.value.length > 0)))

function basculerBloque() {
  bloque.value = !bloque.value
  erreur.value = null
  void nextTick(() => composeur.value?.focus())
}

async function soumettre() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await envoyer(props.cercle.id, { type: mode.value as 'texte' | 'coup_de_main', texte: texte.value.trim(), pieces: pieces.value.map((p) => p.id) })
    if (!r.ok) {
      erreur.value = t(r.statut === 429 ? 'cercles.salon.erreurs.trop' : 'cercles.salon.erreurs.envoi')
      return
    }
    messages.value = fusionner(messages.value, [r.message])
    if (r.message.type === 'coup_de_main') coupsDeMain.value = [...coupsDeMain.value, r.message]
    texte.value = ''
    pieces.value = []
    bloque.value = false
    composeur.value?.rafraichir()
    allerEnBas()
  } catch {
    erreur.value = t('cercles.salon.erreurs.envoi')
  } finally {
    envoi.value = false
  }
}

// ---------------------------------------------------------------- coups de main

const enCours = ref<string | null>(null)
const voirCoups = ref(false)

/** « Je peux aider » : on répond dans le fil, en s'adressant à la personne. */
function aider(m: MessageCercle) {
  bloque.value = false
  voirCoups.value = false
  texte.value = `@${prenom(m.auteur?.nom)} `
  void nextTick(() => composeur.value?.focus())
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

// ---------------------------------------------------------------- l'appel du cercle (#157)

const etatAppel = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const peutTerminer = computed(() => !!appel.value && !!moi.value && (appel.value.lancePar?.id === moi.value.id || props.cercle.moi.estCreateur))
const { etat: appelEnDirect } = useAppelCercle()
const dansCetAppel = computed(() => appelCercleActif() && appelEnDirect.cercleId === props.cercle.id)

/** L'appel se passe dans Jamii, en WebRTC (appelCercle.ts) : lancer ou rejoindre ouvre la fenêtre d'appel. */
async function appeler(video = false) {
  if (etatAppel.value === 'envoi') return
  if (dansCetAppel.value) return reduireAppelCercle(false)
  etatAppel.value = 'envoi'
  try {
    await rejoindre({ id: props.cercle.id, nom: props.cercle.nom }, video)
    const fin = appelEnDirect.phase === 'fini' ? appelEnDirect.fin : null
    etatAppel.value = fin === 'trop_de_requetes' ? 'trop' : fin === 'impossible' ? 'erreur' : 'repos'
    void lire()
  } catch {
    etatAppel.value = 'erreur'
  }
}
async function raccrocher() {
  if (dansCetAppel.value) await quitterAppelCercle()
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
const ICONES: Record<string, NomIcone> = {
  cercle_cree: 'creation',
  membre_rejoint: 'arrivee',
  tirage: 'tirage',
  tour_ouvert: 'depart',
  tour_livre: 'livre',
  cercle_termine: 'fin',
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
  <section class="salon carte" aria-labelledby="salon-titre" data-test="salon-cercle">
    <!-- Une barre fine : de quoi on parle, et l'appel du cercle. -->
    <header class="salon__barre">
      <div class="salon__titres">
        <h2 id="salon-titre"><IconeCercle nom="salon" :taille="18" />{{ t('cercles.qg.outils.salon') }}</h2>
        <p v-if="contexte" class="salon__contexte" data-test="contexte">{{ contexte }}</p>
      </div>
      <div class="appel" data-test="appel-cercle">
        <template v-if="appel">
          <span class="appel__direct"><span class="appel__pulse" aria-hidden="true" />{{ t('cercles.salon.appelEnCours', { nom: prenom(appel.lancePar?.nom) || t('cercles.salon.quelquun'), quand: ilYa(appel.lanceLe, locale) }) }}<template v-if="appel.presents"> · {{ t('cercles.salon.presents', { n: appel.presents }, appel.presents) }}</template></span>
          <button v-if="dansCetAppel" type="button" class="bouton bouton--direct" data-test="revenir-appel" @click="reduireAppelCercle(false)">{{ t('cercles.salon.revenirAppel') }}</button>
          <template v-else>
            <button type="button" class="bouton bouton--direct" :disabled="etatAppel === 'envoi'" data-test="rejoindre-appel" @click="appeler(false)">{{ t('cercles.salon.rejoindre') }}</button>
            <button type="button" class="bouton" :disabled="etatAppel === 'envoi'" data-test="rejoindre-appel-video" @click="appeler(true)">{{ t('cercles.salon.rejoindreVideo') }}</button>
          </template>
          <button v-if="peutTerminer" type="button" class="bouton bouton--discret" data-test="terminer-appel" @click="raccrocher">{{ t('cercles.salon.terminer') }}</button>
        </template>
        <button v-else type="button" class="bouton" :disabled="etatAppel === 'envoi'" :aria-busy="etatAppel === 'envoi' || undefined" data-test="lancer-appel" @click="appeler(false)">
          <IconeCercle nom="appel" :taille="16" />{{ t('cercles.salon.lancerAppel') }}
        </button>
      </div>
      <p v-if="etatAppel === 'erreur' || etatAppel === 'trop'" role="alert" class="erreur salon__erreur">{{ t(etatAppel === 'trop' ? 'cercles.salon.erreurs.tropAppels' : 'etat.indisponible') }}</p>
    </header>

    <!-- Les demandes d'aide ouvertes : une ligne discrète, dépliable. On aide d'abord qui bloque. -->
    <section v-if="coupsDeMain.length" class="aides" aria-labelledby="coups-titre" data-test="coups-de-main">
      <button type="button" class="aides__bascule" :aria-expanded="voirCoups" @click="voirCoups = !voirCoups">
        <span class="aides__point" aria-hidden="true" />
        <strong id="coups-titre">{{ t('cercles.salon.coupsTitre', { n: coupsDeMain.length }, coupsDeMain.length) }}</strong>
        <span class="aides__apercu">{{ prenom(coupsDeMain[0]!.auteur?.nom) }} · {{ coupsDeMain[0]!.texte }}</span>
        <span class="aides__voir">{{ voirCoups ? t('cercles.salon.masquer') : t('cercles.salon.voir') }}</span>
      </button>
      <ul v-show="voirCoups" class="aides__liste">
        <li v-for="c in coupsDeMain" :key="c.id" class="aide" data-test="coup-de-main">
          <LienPersonne :id="c.auteur?.id" :nom="c.auteur?.nom"><JAvatar :nom="c.auteur?.nom ?? '?'" :url="c.auteur?.avatarUrl" :taille="28" /></LienPersonne>
          <p class="aide__texte"><strong>{{ prenom(c.auteur?.nom) }}</strong> {{ c.texte }} <span class="discret">· {{ ilYa(c.envoyeLe, locale) }}</span></p>
          <span class="aide__actions">
            <button v-if="c.auteur?.id !== moi?.id" type="button" class="bouton bouton--petit" data-test="aider" @click="aider(c)">{{ t('cercles.salon.aider') }}</button>
            <button type="button" class="bouton bouton--petit" :disabled="enCours === c.id" data-test="regler" @click="regler(c)"><IconeCercle nom="coche" :taille="14" />{{ t('cercles.salon.regle') }}</button>
          </span>
        </li>
      </ul>
    </section>

    <p v-if="etat === 'chargement'" class="salon__etat" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'indisponible'" class="salon__etat" role="alert">{{ t('etat.indisponible') }}</p>

    <div v-else class="fil-cadre motif fond-discussion" data-test="motif-discussion">
      <ol ref="liste" class="fil" aria-live="polite" :aria-label="t('cercles.salon.fil')" @scroll="enBas() && (nonVus = 0)">
        <li v-if="!messages.length" class="vide">{{ t('cercles.salon.vide') }}</li>
        <template v-for="l in lignes" :key="l.cle">
          <li v-if="l.type === 'jour'" class="jour" role="separator"><span>{{ jour(l.date) }}</span></li>

          <li v-else-if="l.message.type === 'evenement'" class="evenement" data-test="evenement">
            <IconeCercle :nom="ICONES[l.message.evenement?.code ?? ''] ?? 'cercle'" :taille="14" />
            <span>{{ evenement(l.message) }}</span>
            <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
          </li>

          <li v-else class="message" :class="{ 'message--suite': l.suite }" data-test="message">
            <span class="message__avatar">
              <LienPersonne v-if="!l.suite" :id="l.message.auteur?.id" doublon><JAvatar :nom="l.message.auteur?.nom ?? '?'" :url="l.message.auteur?.avatarUrl" :taille="36" /></LienPersonne>
              <time v-else class="message__heure-survol" :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
            </span>
            <div class="message__corps">
              <p v-if="!l.suite" class="message__meta">
                <LienPersonne :id="l.message.auteur?.id" class="message__nom">{{ l.message.auteur?.nom ?? t('communautes.compteSupprime') }}</LienPersonne>
                <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
              </p>

              <template v-if="l.message.type === 'texte'">
                <TexteRiche v-if="l.message.texte" :texte="l.message.texte" />
                <PiecesJointes :pieces="l.message.piecesJointes ?? []" />
              </template>

              <div v-else-if="l.message.type === 'point' && l.message.point" class="bloc" data-test="point-du-jour">
                <p class="bloc__titre"><IconeCercle nom="points" :taille="14" />{{ t('cercles.salon.sonPoint') }}</p>
                <dl class="bloc__lignes">
                  <div><dt>{{ t('cercles.salon.fait') }}</dt><dd>{{ l.message.point.fait }}</dd></div>
                  <div><dt>{{ t('cercles.salon.prevu') }}</dt><dd>{{ l.message.point.prevu }}</dd></div>
                  <div v-if="l.message.point.bloque" class="bloc__alerte"><dt>{{ t('cercles.salon.bloque') }}</dt><dd>{{ l.message.point.bloque }}</dd></div>
                </dl>
              </div>

              <div v-else-if="l.message.type === 'coup_de_main'" class="bloc" :class="l.message.coupDeMain?.resoluLe ? 'bloc--regle' : 'bloc--aide'">
                <p class="bloc__titre"><IconeCercle :nom="l.message.coupDeMain?.resoluLe ? 'coche' : 'priorite'" :taille="14" />{{ l.message.coupDeMain?.resoluLe ? (l.message.coupDeMain.resoluPar ? t('cercles.salon.regleePar', { nom: prenom(l.message.coupDeMain.resoluPar.nom) }) : t('cercles.salon.reglee')) : t('cercles.salon.demandeTitre') }}</p>
                <TexteRiche :texte="l.message.texte ?? ''" />
                <PiecesJointes :pieces="l.message.piecesJointes ?? []" compact />
              </div>

              <div v-else-if="l.message.type === 'decision'" class="bloc" data-test="decision-fil">
                <p class="bloc__titre"><IconeCercle nom="decisions" :taille="14" />{{ t('cercles.qg.decisionPrise') }}</p>
                <TexteRiche :texte="l.message.texte ?? ''" />
              </div>

              <p v-else-if="l.message.type === 'appel'" class="message__appel">
                <IconeCercle nom="appel" :taille="16" />{{ t('cercles.salon.aLanceAppel') }}
                <button v-if="l.message.appel?.enCours && appel && appel.id === l.message.appel.id && !dansCetAppel" type="button" class="lien-fort lien-bouton" @click="appeler(false)">{{ t('cercles.salon.rejoindre') }}</button>
                <span v-else class="discret">· {{ t('cercles.salon.appelTermine') }}</span>
              </p>
            </div>
          </li>
        </template>
      </ol>
      <button v-if="nonVus" type="button" class="nouveaux" @click="allerEnBas">{{ t('cercles.salon.nouveaux', { n: nonVus }, nonVus) }}</button>
    </div>
    <p v-if="coupe" class="salon__etat" role="status">{{ t('cercles.salon.coupe') }}</p>

    <!-- La saisie : un message, ou « Je bloque » pour demander de l'aide. -->
    <form class="saisie" data-test="saisie" @submit.prevent="soumettre">
      <Composeur
        ref="composeur"
        v-model="texte"
        v-model:pieces="pieces"
        id-zone="salon-texte"
        :placeholder="t(bloque ? 'cercles.salon.bloquePlaceholder' : 'cercles.salon.placeholder')"
        :libelle-envoyer="t(bloque ? 'cercles.salon.envoyerBloque' : 'cercles.salon.envoyer')"
        :envoi="envoi"
        :erreur="erreur"
        :accent="bloque ? 'alerte' : 'normal'"
        :max="LIMITES_SALON.texte"
        @envoyer="soumettre"
      >
        <template #outils>
          <span class="separateur" aria-hidden="true" />
          <button type="button" class="bascule" :aria-pressed="bloque" data-test="mode-bloque" @click="basculerBloque">
            <IconeCercle nom="priorite" :taille="14" />{{ t('cercles.salon.modeBloque') }}
          </button>
        </template>
      </Composeur>
    </form>
  </section>
</template>

<style scoped>
/* Une seule carte, à la hauteur de l'écran : la barre, le fil qui défile, la saisie collée en bas. */
.salon { display: flex; flex-direction: column; height: clamp(28rem, calc(100dvh - 13.5rem), 56rem); padding: 0; overflow: hidden; }

.salon__barre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--line); }
.salon__titres { display: grid; gap: 2px; min-width: 0; }
.salon__titres h2 { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: 1.125rem; }
.salon__contexte { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.salon__erreur { width: 100%; }
.salon__etat { margin: 0; padding: var(--space-3) var(--space-6); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.appel { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.appel__direct { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.appel__pulse { width: 8px; height: 8px; border-radius: 50%; background: #2EAD5F; box-shadow: 0 0 0 0 rgb(46 173 95 / 0.6); animation: pulse 1.6s infinite; }
@keyframes pulse { 70% { box-shadow: 0 0 0 7px rgb(46 173 95 / 0); } 100% { box-shadow: 0 0 0 0 rgb(46 173 95 / 0); } }

.bouton {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 38px; padding: 0 var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-pill);
  background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; text-decoration: none; cursor: pointer; white-space: nowrap;
}
.bouton:hover { border-color: var(--ink-muted); }
.bouton:disabled { opacity: 0.6; cursor: progress; }
.bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.bouton--direct { background: #1F7A5C; border-color: #1F7A5C; color: #fff; }
.bouton--direct:hover { background: #18654B; border-color: #18654B; }
.bouton--discret { border-color: transparent; color: var(--ink-muted); }
.bouton--petit { min-height: 32px; padding: 0 var(--space-3); font-size: 0.8125rem; }

/* Les demandes d'aide : une ligne, un point orange, rien de plus tant qu'on ne déplie pas. */
.aides { border-bottom: 1px solid var(--line); background: var(--surface-raised); }
.aides__bascule { display: flex; align-items: center; gap: var(--space-2); width: 100%; min-height: 44px; padding: 0 var(--space-6); border: 0; background: none; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); text-align: left; cursor: pointer; }
.aides__bascule:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
.aides__point { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--proof); }
.aides__apercu { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); }
.aides__voir { flex: none; color: var(--action-ink); font-weight: 600; }
.aides__liste { list-style: none; margin: 0; padding: 0 var(--space-6) var(--space-3); display: grid; gap: var(--space-2); }
.aide { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; }
.aide__texte { margin: 0; font-size: var(--type-body-sm-size); overflow-wrap: anywhere; }
.aide__actions { display: flex; gap: var(--space-2); }

.fil-cadre { position: relative; flex: 1; min-height: 0; }
.fil { list-style: none; margin: 0; height: 100%; box-sizing: border-box; padding: var(--space-3) var(--space-6); display: grid; gap: 2px; overflow-y: auto; overscroll-behavior: contain; align-content: start; }
.vide { padding: var(--space-12) 0; text-align: center; color: var(--ink-muted); }
.jour { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-4) 0 var(--space-2); color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; }
.jour::before, .jour::after { content: ''; flex: 1; height: 1px; background: var(--line); }
.jour span::first-letter { text-transform: uppercase; }

.evenement { display: flex; justify-content: center; align-items: center; gap: var(--space-2); margin: var(--space-1) 0; color: var(--ink-muted); font-size: 0.8125rem; text-align: center; }
.evenement time { font-size: 0.75rem; opacity: 0.75; }

.message { display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: var(--space-3); padding: var(--space-2) var(--space-2); margin: 0 calc(-1 * var(--space-2)); border-radius: var(--radius-md); }
.message:hover { background: var(--surface-raised); }
.message--suite { padding-top: 2px; }
.message__heure-survol { display: block; padding-top: 3px; color: var(--ink-muted); font-size: 0.6875rem; text-align: right; opacity: 0; }
.message:hover .message__heure-survol { opacity: 1; }
.message__corps { min-width: 0; display: grid; gap: var(--space-1); }
.message__meta { margin: 0; display: flex; align-items: baseline; gap: var(--space-2); }
.message__nom { font-weight: 700; }
.message__meta time { color: var(--ink-muted); font-size: 0.75rem; }
.message__texte { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; }
.message__appel { margin: 0; display: inline-flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); color: var(--ink-muted); }
.lien-fort { color: #1F7A5C; font-weight: 700; }

/* Les messages structurés : un même bloc sobre, un seul accent de couleur à gauche quand il faut agir. */
.bloc { display: grid; gap: var(--space-1); max-width: 40rem; padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); }
.bloc__titre { margin: 0; display: inline-flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; }
.bloc--aide { border-left: 3px solid var(--proof); }
.bloc--aide .bloc__titre { color: var(--proof-ink); }
.bloc--regle .bloc__titre { color: var(--ink-muted); }
.bloc__lignes { margin: 0; display: grid; gap: var(--space-1); }
.bloc__lignes > div { display: grid; grid-template-columns: 4.5rem minmax(0, 1fr); gap: var(--space-2); }
.bloc__lignes dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.bloc__lignes dd { margin: 0; overflow-wrap: anywhere; }
.bloc__alerte dt, .bloc__alerte dd { color: var(--proof-ink); }
.discret { color: var(--ink-muted); }

.nouveaux { position: absolute; left: 50%; bottom: var(--space-3); translate: -50% 0; padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-pill); background: var(--ink); color: var(--surface); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; }

.saisie { margin: var(--space-3) var(--space-6) var(--space-4); }
.separateur { width: 1px; height: 18px; margin: 0 var(--space-1); background: var(--line); }
.bascule { display: inline-flex; align-items: center; gap: 6px; min-height: 30px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); background: transparent; color: var(--ink-muted); font: inherit; font-size: 0.8125rem; font-weight: 600; cursor: pointer; }
.bascule[aria-pressed='true'] { border-color: var(--proof); background: var(--proof-soft); color: var(--proof-ink); }
.bascule:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-size: var(--type-body-sm-size); font-weight: 600; }

@media (max-width: 720px) {
  .salon { height: calc(100dvh - 9rem); border-radius: var(--radius-md); }
  .salon__barre, .aides__bascule, .fil { padding-inline: var(--space-4); }
  .saisie { margin-inline: var(--space-3); }
  .aide { grid-template-columns: auto minmax(0, 1fr); }
  .aide__actions { grid-column: 2; }
}
@media (prefers-reduced-motion: reduce) { .appel__pulse { animation: none; } }
.lien-bouton { border: 0; background: none; padding: 0; font: inherit; cursor: pointer; }
</style>
