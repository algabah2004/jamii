<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar, JBouton } from '../../design-system'
import { marquerLu } from './lus'
import SupprimerSalon from './SupprimerSalon.vue'

/*
 * Un salon d'une communauté, sur toute la hauteur (contracts/communautes.yaml v0.2, #155) :
 * - la conversation : les messages d'un même auteur à la suite sont groupés, un séparateur marque chaque jour ;
 * - « en direct » = on relit les nouveaux messages toutes les 3 secondes (`apres`), et on s'arrête quand l'onglet
 *   est caché ou qu'on quitte la page ;
 * - l'appel vocal ou vidéo du salon, pour les membres.
 * Les textes sont affichés échappés (jamais v-html).
 */
type Salon = components['schemas']['Salon']
type Message = components['schemas']['MessageSalon']

const props = defineProps<{ slug: string; salon?: string; membre: boolean; moderateur?: boolean; adhesionEnCours?: boolean }>()
const emit = defineEmits<{ rejoindre: []; salons: [salons: Salon[]]; supprime: [salon: string] }>()
const { t, locale } = useI18n()

const INTERVALLE = 3000
const LONGUEUR = 2000
/** Deux messages du même auteur à moins de 7 minutes d'écart : le second se range sous le premier. */
const ECART_GROUPE_MS = 7 * 60_000

const salons = ref<Salon[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const actuel = computed(() => salons.value.find((s) => s.slug === props.salon) ?? salons.value[0] ?? null)

async function chargerSalons() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } })
    if (!data) throw new Error('salons indisponibles')
    salons.value = data.elements
    emit('salons', data.elements)
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}

const messages = ref<Message[]>([])
const premierChargement = ref(true)
const coupe = ref(false)
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
    retenirLu()
  })
}
function retenirLu() {
  const dernier = messages.value.at(-1)
  if (actuel.value && dernier) marquerLu(props.slug, actuel.value.slug, dernier.numero)
}
function defilement() {
  if (enBas()) {
    nonVus.value = 0
    retenirLu()
  }
}

/** Sans `complet` : seulement les messages arrivés depuis le dernier reçu. */
async function lire(complet = false) {
  const salon = actuel.value
  if (!salon) return
  const numero = ++lecture
  const apres = complet ? undefined : messages.value.at(-1)?.numero
  try {
    const { data } = await api.GET('/api/communautes/{slug}/salons/{salon}/messages', {
      params: { path: { slug: props.slug, salon: salon.slug }, query: apres === undefined ? {} : { apres } },
    })
    // Une réponse arrivée après un changement de salon ne concerne plus l'écran.
    if (numero !== lecture || salon !== actuel.value) return
    if (!data) throw new Error('messages indisponibles')
    coupe.value = false
    ajouter(complet ? data.elements : [...messages.value, ...data.elements], complet)
  } catch {
    if (numero === lecture) coupe.value = true
  } finally {
    if (complet && numero === lecture) premierChargement.value = false
  }
}

/** Sans doublon (un message envoyé revient aussi par la relecture). On suit le bas si on y était. */
function ajouter(tous: Message[], forcerLeBas: boolean) {
  const avant = messages.value.length
  const etaitEnBas = enBas()
  const vus = new Set<string>()
  messages.value = tous.filter((m) => !vus.has(m.id) && vus.add(m.id)).sort((a, b) => a.numero - b.numero)
  if (forcerLeBas || etaitEnBas) allerEnBas()
  else nonVus.value += Math.max(0, messages.value.length - avant)
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

watch(actuel, (salon, avant) => {
  if (salon?.id === avant?.id) return
  messages.value = []
  nonVus.value = 0
  premierChargement.value = true
  texte.value = ''
  void lire(true)
  demarrer()
})
watch(() => props.slug, () => void chargerSalons())
onMounted(async () => {
  document.addEventListener('visibilitychange', visibilite)
  await chargerSalons()
})
onBeforeUnmount(() => {
  arreter()
  document.removeEventListener('visibilitychange', visibilite)
})

/** Les lignes à afficher : un séparateur à chaque nouveau jour, et les messages groupés par auteur. */
type Ligne = { type: 'jour'; cle: string; libelle: string } | { type: 'message'; cle: string; message: Message; suite: boolean }
const jour = (iso: string) => new Date(iso).toDateString()
function libelleJour(iso: string) {
  const d = new Date(iso)
  const aujourdHui = new Date()
  const hier = new Date(aujourdHui.getTime() - 86_400_000)
  if (d.toDateString() === aujourdHui.toDateString()) return t('communautes.salons.aujourdhui')
  if (d.toDateString() === hier.toDateString()) return t('communautes.salons.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(d)
}
const lignes = computed<Ligne[]>(() => {
  const resultat: Ligne[] = []
  let precedent: Message | null = null
  for (const m of messages.value) {
    const nouveauJour = !precedent || jour(precedent.envoyeLe) !== jour(m.envoyeLe)
    if (nouveauJour) resultat.push({ type: 'jour', cle: `jour-${m.id}`, libelle: libelleJour(m.envoyeLe) })
    const suite = !nouveauJour && !!precedent && precedent.auteur.id === m.auteur.id
      && new Date(m.envoyeLe).getTime() - new Date(precedent.envoyeLe).getTime() < ECART_GROUPE_MS
    resultat.push({ type: 'message', cle: m.id, message: m, suite })
    precedent = m
  }
  return resultat
})

const texte = ref('')
const zone = ref<HTMLTextAreaElement | null>(null)
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const valide = computed(() => texte.value.trim().length > 0 && texte.value.length <= LONGUEUR)
async function envoyer() {
  const salon = actuel.value
  if (!salon || !valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const { data, response } = await api.POST('/api/communautes/{slug}/salons/{salon}/messages', {
      params: { path: { slug: props.slug, salon: salon.slug } },
      body: { texte: texte.value.trim() },
    })
    if (!data) return void (envoi.value = response.status === 429 ? 'trop' : 'erreur')
    texte.value = ''
    envoi.value = 'repos'
    ajouter([...messages.value, data], true)
    void nextTick(ajusterZone)
  } catch {
    envoi.value = 'erreur'
  }
}
/** Entrée envoie, Maj + Entrée passe à la ligne. */
function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void envoyer()
  }
}
/** La zone d'écriture grandit avec le texte, jusqu'à une dizaine de lignes. */
function ajusterZone() {
  const el = zone.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 240)}px`
}
watch(texte, () => void nextTick(ajusterZone))

const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
/** Une couleur de nom stable par personne, prise dans la charte : on reconnaît qui parle d'un coup d'œil. */
const TEINTES = ['var(--action-ink)', 'var(--proof-ink)', 'var(--cantaloupe)', 'var(--ink)']
function teinte(id: string) {
  let h = 0
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return TEINTES[h % TEINTES.length]
}
</script>

<template>
  <section class="salon" :aria-label="actuel ? `# ${actuel.nom}` : t('communautes.salons.titre')" data-test="salons">
    <p v-if="etat === 'indisponible'" role="alert" class="salon__etat">
      {{ t('etat.indisponible') }}
      <JBouton variante="discret" @click="chargerSalons">{{ t('etat.reessayer') }}</JBouton>
    </p>

    <template v-else-if="actuel">
      <header class="salon__tete">
        <h1 class="salon__nom"><span aria-hidden="true">#</span> {{ actuel.nom }}</h1>
        <p class="salon__sujet">{{ actuel.sujet }}</p>
        <div class="salon__actions">
          <slot name="actions" />
          <!-- # général ne se supprime pas : c'est là qu'on arrive dans la communauté. -->
          <SupprimerSalon
            v-if="moderateur && actuel.slug !== 'general'"
            :slug="slug"
            :salon="actuel.slug"
            :nom="actuel.nom"
            :messages="messages.length"
            @supprime="emit('supprime', $event)"
          />
          <a
            v-if="actuel.appelUrl"
            :href="actuel.appelUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="appel"
            data-test="appel"
          >
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
            <span>{{ t('communautes.salons.appel') }}</span><span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
          </a>
        </div>
      </header>

      <div class="salon__fil">
        <ol ref="liste" class="salon__messages" aria-live="polite" aria-relevant="additions" data-test="messages" @scroll.passive="defilement">
          <li class="accueil">
            <span class="accueil__signe" aria-hidden="true">#</span>
            <h2>{{ t('communautes.salons.bienvenue', { salon: actuel.nom }) }}</h2>
            <p>{{ actuel.sujet }} {{ t('communautes.salons.debut', { salon: actuel.nom }) }}</p>
          </li>
          <li v-if="premierChargement" class="salon__chargement texte-discret" role="status">{{ t('etat.chargement') }}</li>
          <template v-for="l in lignes" :key="l.cle">
            <li v-if="l.type === 'jour'" class="jour" role="separator"><span>{{ l.libelle }}</span></li>
            <li v-else class="message" :class="{ 'message--suite': l.suite }" data-test="message">
              <span v-if="!l.suite" class="message__avatar"><JAvatar :nom="l.message.auteur.nom" :url="l.message.auteur.avatarUrl" :taille="40" /></span>
              <time v-else class="message__heure-survol" :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
              <div class="message__corps">
                <p v-if="!l.suite" class="message__meta">
                  <strong :style="{ color: teinte(l.message.auteur.id) }">{{ l.message.auteur.nom }}</strong>
                  <time :datetime="l.message.envoyeLe">{{ heure(l.message.envoyeLe) }}</time>
                </p>
                <p class="message__texte">{{ l.message.texte }}</p>
              </div>
            </li>
          </template>
        </ol>
        <button v-if="nonVus > 0" type="button" class="nouveaux" data-test="nouveaux" @click="allerEnBas">
          {{ t('communautes.salons.nouveauxMessages', { n: nonVus }, nonVus) }} ↓
        </button>
      </div>

      <p v-if="coupe" class="salon__coupe" role="status">{{ t('communautes.salons.coupe') }}</p>

      <form v-if="membre" class="ecrire" data-test="ecrire" @submit.prevent="envoyer">
        <div class="ecrire__boite">
          <label class="visuellement-cache" :for="`ecrire-${actuel.slug}`">{{ t('communautes.salons.ecrireDans', { salon: actuel.nom }) }}</label>
          <textarea
            :id="`ecrire-${actuel.slug}`"
            ref="zone"
            v-model="texte"
            rows="1"
            :maxlength="LONGUEUR"
            :placeholder="t('communautes.salons.ecrireDans', { salon: actuel.nom })"
            @keydown="touche"
          />
          <button type="submit" class="ecrire__envoyer" :disabled="!valide || envoi === 'envoi'" :aria-label="t('communautes.salons.envoyer')">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3 10 17 3l-4 14-3-6-7-1Z" fill="currentColor" /></svg>
          </button>
        </div>
        <p v-if="envoi === 'erreur' || envoi === 'trop'" role="alert" class="ecrire__erreur">
          {{ envoi === 'trop' ? t('communautes.salons.trop') : t('etat.indisponible') }}
        </p>
        <p v-else class="ecrire__aide" aria-hidden="true">{{ t('communautes.salons.aideClavier') }}</p>
      </form>
      <div v-else class="rejoindre">
        <p>{{ t('communautes.salons.rejoindre') }}</p>
        <JBouton :occupe="adhesionEnCours" data-test="rejoindre-salon" @click="emit('rejoindre')">{{ t('communautes.rejoindre') }}</JBouton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.salon { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.salon__etat { margin: var(--space-6); }

.salon__tete {
  display: flex; align-items: center; gap: var(--space-3); min-height: 56px; padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--line); box-shadow: 0 1px 0 color-mix(in srgb, var(--ink) 4%, transparent);
}
.salon__nom { margin: 0; flex-shrink: 0; font-size: 1.0625rem; font-family: var(--font-text); font-weight: 700; }
.salon__nom span { color: var(--ink-muted); font-weight: 500; }
.salon__sujet {
  margin: 0; flex: 1; min-width: 0; padding-inline-start: var(--space-3); border-inline-start: 1px solid var(--line);
  color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.salon__actions { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }
.appel {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-3);
  border-radius: var(--radius-pill); background: color-mix(in srgb, #23a55a 14%, transparent); color: #1a7f45;
  font-weight: 600; font-size: var(--type-body-sm-size); text-decoration: none;
}
:root[data-theme='sombre'] .appel { color: #57d68d; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .appel { color: #57d68d; } }
.appel:hover { background: color-mix(in srgb, #23a55a 24%, transparent); }
.appel:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.salon__fil { position: relative; flex: 1; min-height: 0; display: flex; }
.salon__messages { flex: 1; list-style: none; margin: 0; padding: var(--space-4) 0 var(--space-3); overflow-y: auto; overscroll-behavior: contain; display: flex; flex-direction: column; }
/* Les messages se posent en bas, comme dans une messagerie, même quand il y en a peu. */
.salon__messages > :first-child { margin-top: auto; }

.accueil { padding: var(--space-6) var(--space-4) var(--space-4); }
.accueil__signe {
  display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%;
  background: var(--surface-raised); color: var(--ink); font-size: 2.25rem; font-weight: 400;
}
.accueil h2 { margin: var(--space-3) 0 var(--space-1); font-size: 1.5rem; }
.accueil p { margin: 0; color: var(--ink-muted); }
.salon__chargement { padding: 0 var(--space-4); }

.jour { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-4) var(--space-4) var(--space-2); color: var(--ink-muted); font-size: 0.75rem; font-weight: 600; }
.jour::before, .jour::after { content: ''; flex: 1; border-top: 1px solid var(--line); }

.message {
  position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3);
  padding: 2px var(--space-4); margin-top: var(--space-3);
}
.message--suite { margin-top: 0; }
.message:hover { background: color-mix(in srgb, var(--ink) 3%, transparent); }
.message p { margin: 0; }
.message__meta { display: flex; align-items: baseline; gap: var(--space-2); }
.message__meta strong { font-size: 0.9375rem; }
.message__meta time { color: var(--ink-muted); font-size: 0.75rem; }
.message__heure-survol { visibility: hidden; align-self: center; color: var(--ink-muted); font-size: 0.6875rem; text-align: center; }
.message:hover .message__heure-survol { visibility: visible; }
.message__texte { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.45; }

.nouveaux {
  position: absolute; bottom: var(--space-3); left: 50%; transform: translateX(-50%);
  padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-pill);
  background: var(--action); color: var(--on-action); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600;
  box-shadow: var(--ombre-carte); cursor: pointer;
}
.nouveaux:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.salon__coupe { margin: 0; padding: 0 var(--space-4) var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.ecrire { padding: 0 var(--space-4) var(--space-3); }
.ecrire__boite {
  display: flex; align-items: flex-end; gap: var(--space-2); padding: var(--space-1) var(--space-1) var(--space-1) var(--space-4);
  border-radius: var(--radius-md); background: var(--surface-raised); border: 1px solid transparent;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.ecrire__boite:focus-within { border-color: var(--line-strong); }
.ecrire textarea {
  flex: 1; min-width: 0; min-height: 40px; max-height: 240px; resize: none; padding: 10px 0; border: 0; outline: 0;
  background: transparent; color: var(--ink); font: inherit; line-height: 1.4;
}
.ecrire__envoyer {
  display: grid; place-items: center; width: 40px; height: 40px; flex-shrink: 0; border: 0; border-radius: var(--radius-sm);
  background: var(--action); color: var(--on-action); cursor: pointer;
  transition: opacity var(--motion-fast) var(--ease-out);
}
.ecrire__envoyer:disabled { background: transparent; color: var(--ink-muted); cursor: default; }
.ecrire__envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ecrire__aide { margin: var(--space-1) 0 0; color: var(--ink-muted); font-size: 0.75rem; }
.ecrire__erreur { margin: var(--space-1) 0 0; color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }

.rejoindre {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3);
  margin: 0 var(--space-4) var(--space-3); padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--action-soft);
}
.rejoindre p { margin: 0; font-weight: 600; }

@media (max-width: 760px) {
  .salon__sujet { display: none; }
  .appel span:first-of-type { display: none; }
  .ecrire__aide { display: none; }
}
</style>
