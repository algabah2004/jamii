<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar, JBouton } from '../../design-system'

/*
 * Les salons d'une communauté (contracts/communautes.yaml v0.2, #155) : une discussion écrite en direct, et l'appel
 * vocal ou vidéo du salon. « En direct » = on relit les nouveaux messages toutes les 3 secondes (`apres`), et on
 * s'arrête quand l'onglet est caché. Les textes sont affichés échappés (jamais v-html).
 */
type Salon = components['schemas']['Salon']
type Message = components['schemas']['MessageSalon']

const props = defineProps<{ slug: string; membre: boolean; salon?: string }>()
const emit = defineEmits<{ choisir: [salon: string]; rejoindre: [] }>()
const { t, locale } = useI18n()

const INTERVALLE = 3000
const LONGUEUR = 2000

const salons = ref<Salon[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const actuel = computed(() => salons.value.find((s) => s.slug === props.salon) ?? salons.value[0] ?? null)

async function chargerSalons() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes/{slug}/salons', { params: { path: { slug: props.slug } } })
    if (!data) throw new Error('salons indisponibles')
    salons.value = data.elements
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}

const messages = ref<Message[]>([])
const coupe = ref(false)
const liste = ref<HTMLElement | null>(null)
let minuterie: ReturnType<typeof setInterval> | null = null
let lecture = 0

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
  }
}

/** Sans doublon (un message envoyé arrive aussi par la relecture), et on suit le bas si on y était. */
function ajouter(tous: Message[], forcerLeBas: boolean) {
  const el = liste.value
  const enBas = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
  const vus = new Set<string>()
  messages.value = tous.filter((m) => !vus.has(m.id) && vus.add(m.id)).sort((a, b) => a.numero - b.numero)
  if (forcerLeBas || enBas) {
    void nextTick(() => {
      if (liste.value) liste.value.scrollTop = liste.value.scrollHeight
    })
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

watch(actuel, (salon, avant) => {
  if (salon?.id === avant?.id) return
  messages.value = []
  void lire(true)
  demarrer()
})
onMounted(async () => {
  document.addEventListener('visibilitychange', visibilite)
  await chargerSalons()
})
onBeforeUnmount(() => {
  arreter()
  document.removeEventListener('visibilitychange', visibilite)
})

const texte = ref('')
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

const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
</script>

<template>
  <section class="salons" :aria-label="t('communautes.salons.titre')" data-test="salons">
    <p v-if="etat === 'indisponible'" role="alert" class="texte-discret">
      {{ t('etat.indisponible') }}
      <JBouton variante="discret" @click="chargerSalons">{{ t('etat.reessayer') }}</JBouton>
    </p>

    <template v-else-if="actuel">
      <nav class="salons__liste" :aria-label="t('communautes.salons.liste')">
        <button
          v-for="s in salons"
          :key="s.id"
          type="button"
          class="salons__onglet"
          :aria-current="s.id === actuel.id ? 'true' : undefined"
          :data-test="`salon-${s.slug}`"
          @click="emit('choisir', s.slug)"
        >
          # {{ s.nom }}
        </button>
      </nav>

      <div class="salon carte">
        <header class="salon__tete">
          <div>
            <h2># {{ actuel.nom }}</h2>
            <p class="texte-discret">{{ actuel.sujet }}</p>
          </div>
          <a
            v-if="actuel.appelUrl"
            :href="actuel.appelUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="j-bouton j-bouton--secondaire"
            data-test="appel"
          >
            {{ t('communautes.salons.appel') }}<span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
          </a>
        </header>

        <ol ref="liste" class="salon__messages" aria-live="polite" aria-relevant="additions" data-test="messages">
          <li v-if="!messages.length" class="salon__vide texte-discret">{{ t('communautes.salons.vide', { salon: actuel.nom }) }}</li>
          <li v-for="m in messages" :key="m.id" class="message" data-test="message">
            <JAvatar :nom="m.auteur.nom" :url="m.auteur.avatarUrl" :taille="32" />
            <div>
              <p class="message__meta"><strong>{{ m.auteur.nom }}</strong> <time :datetime="m.envoyeLe">{{ heure(m.envoyeLe) }}</time></p>
              <p class="message__texte">{{ m.texte }}</p>
            </div>
          </li>
        </ol>
        <p v-if="coupe" class="texte-discret salon__coupe" role="status">{{ t('communautes.salons.coupe') }}</p>

        <form v-if="membre" class="salon__ecrire" data-test="ecrire" @submit.prevent="envoyer">
          <label class="visuellement-cache" :for="`ecrire-${actuel.slug}`">{{ t('communautes.salons.ecrireDans', { salon: actuel.nom }) }}</label>
          <textarea
            :id="`ecrire-${actuel.slug}`"
            v-model="texte"
            rows="1"
            :maxlength="LONGUEUR"
            :placeholder="t('communautes.salons.ecrireDans', { salon: actuel.nom })"
            @keydown="touche"
          />
          <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'">{{ t('communautes.salons.envoyer') }}</JBouton>
          <p v-if="envoi === 'erreur' || envoi === 'trop'" role="alert" class="erreur">
            {{ envoi === 'trop' ? t('communautes.salons.trop') : t('etat.indisponible') }}
          </p>
        </form>
        <div v-else class="salon__rejoindre">
          <p>{{ t('communautes.salons.rejoindre') }}</p>
          <JBouton @click="emit('rejoindre')">{{ t('communautes.rejoindre') }}</JBouton>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.salons { display: grid; gap: var(--space-3); }
.salons__liste { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.salons__onglet {
  min-height: 36px; padding: 0 var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong);
  background: var(--surface); color: var(--ink-muted); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer;
}
.salons__onglet[aria-current='true'] { background: var(--action-soft); border-color: transparent; color: var(--action-ink); }
.salons__onglet:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.salon { display: grid; grid-template-rows: auto minmax(0, 1fr) auto auto; padding: 0; overflow: hidden; }
.salon__tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-5); border-bottom: 1px solid var(--line); }
.salon__tete h2 { margin: 0; font-size: 1.125rem; }
.salon__tete p { margin: var(--space-1) 0 0; }
.salon__messages { list-style: none; margin: 0; padding: var(--space-4) var(--space-5); display: grid; gap: var(--space-3); align-content: start; min-height: 16rem; max-height: min(60vh, 32rem); overflow-y: auto; overscroll-behavior: contain; }
.salon__vide { align-self: center; text-align: center; }
.message { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-3); align-items: start; }
.message p { margin: 0; }
.message__meta { font-size: var(--type-body-sm-size); }
.message__meta time { color: var(--ink-muted); margin-inline-start: var(--space-1); }
.message__texte { white-space: pre-wrap; overflow-wrap: anywhere; }
.salon__coupe { margin: 0; padding: 0 var(--space-5) var(--space-2); }
.salon__ecrire { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-2); align-items: end; padding: var(--space-3) var(--space-5) var(--space-4); border-top: 1px solid var(--line); }
.salon__ecrire textarea {
  min-height: 44px; max-height: 10rem; resize: vertical; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md);
  border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit;
}
.salon__ecrire textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.salon__ecrire .erreur { grid-column: 1 / -1; margin: 0; color: var(--danger); font-weight: 600; }
.salon__rejoindre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); padding: var(--space-4) var(--space-5); border-top: 1px solid var(--line); }
.salon__rejoindre p { margin: 0; }
</style>
