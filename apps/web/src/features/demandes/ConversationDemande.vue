<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import BadgeIa from '../../components/BadgeIa.vue'
import { ajusterZone } from '../../design-system/zonesDeTexte'
import AyoAvatar from './AyoAvatar.vue'
import {
  BUDGETS,
  composerDescription,
  creerDemande,
  demandeValide,
  DUREES,
  LIMITES,
  QUESTIONS,
  SECTEURS,
  type NouvelleDemande,
  type Question,
  type Reponses,
} from './demandes'

/*
 * Décrire son besoin en parlant à Ayo, l'assistant IA, directement sur la page « Tes demandes » : pas de formulaire
 * sur une autre page, une conversation. Ayo pose une question à la fois ; on répond dans la zone en bas, comme dans
 * un chat (Entrée envoie, Maj + Entrée va à la ligne). Chaque réponse se modifie d'un clic. Les questions guidées et
 * les précisions se passent. À la fin, Ayo récapitule ; la case Systalink n'est jamais cochée d'avance.
 */
const props = withDefaults(defineProps<{ titreInitial?: string; exempleInitial?: number | null }>(), { titreInitial: '', exempleInitial: null })
const { t, tm, rt } = useI18n()
const router = useRouter()

type Tour = 'besoin' | 'details' | Question | 'precisions' | 'envoi'
const TOURS: Tour[] = ['besoin', 'details', ...QUESTIONS, 'precisions', 'envoi']
/** Chaque question vient avec un exemple concret : on comprend tout de suite quel genre de réponse est attendu. */
const NOMS: Record<Question, { question: string; libelle: string; exemple: string }> = {
  qui: { question: 'questionQui', libelle: 'libelleQui', exemple: 'exempleQui' },
  probleme: { question: 'questionProbleme', libelle: 'libelleProbleme', exemple: 'exempleProbleme' },
  reussite: { question: 'questionReussite', libelle: 'libelleReussite', exemple: 'exempleReussite' },
}

const demande = ref<NouvelleDemande>({ titre: '', description: '', secteur: '', ville: '', offresSystalink: false })
const reponses = ref<Reponses>({})
const duree = ref<number | 'inconnue'>('inconnue')
const budget = ref<(typeof BUDGETS)[number]>('inconnu')
const tour = ref<Tour>('besoin')
const atteint = ref(0)
const saisie = ref('')
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'quota'>('repos')
const zone = ref<HTMLTextAreaElement | null>(null)
const fil = ref<HTMLElement | null>(null)

const rang = computed(() => TOURS.indexOf(tour.value))
/** Rien n'est encore dit : l'écran d'accueil d'une nouvelle discussion, centré. */
const vide = computed(() => atteint.value === 0 && tour.value === 'besoin' && !demande.value.titre)
const estQuestion = (x: Tour): x is Question => (QUESTIONS as readonly string[]).includes(x)
const exemples = computed(() => (tm('demandes.exemples') as { titre: string; description: string }[]).map((e) => ({ titre: rt(e.titre), description: rt(e.description) })))

// ---------------------------------------------------------------- avancer dans la conversation

function aller(vers: Tour) {
  tour.value = vers
  atteint.value = Math.max(atteint.value, TOURS.indexOf(vers))
  saisie.value = vers === 'besoin' ? demande.value.titre : vers === 'details' ? demande.value.description : estQuestion(vers) ? (reponses.value[vers] ?? '') : ''
  void nextTick(() => {
    if (zone.value) {
      ajusterZone(zone.value)
      if (vers !== 'precisions' && vers !== 'envoi') zone.value.focus({ preventScroll: true })
    }
    // Absent de certains environnements (tests, vieux navigateurs) : on ne fait alors rien.
    fil.value?.lastElementChild?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
  })
}
const suivant = (apres: Tour) => aller(TOURS[TOURS.indexOf(apres) + 1]!)

/** Revenir à une réponse donnée : la conversation reprend à ce moment-là, rien n'est perdu. */
function modifier(x: Tour) {
  aller(x)
}

const longueur = computed(() => saisie.value.trim().length)
const saisieValide = computed(() => {
  if (tour.value === 'besoin') return longueur.value >= LIMITES.titreMin && longueur.value <= LIMITES.titreMax
  if (tour.value === 'details') return longueur.value >= LIMITES.descriptionMin
  return estQuestion(tour.value) ? longueur.value > 0 : false
})
const saisieActive = computed(() => tour.value !== 'precisions' && tour.value !== 'envoi')

/**
 * Quitter l'accueil centré pour la conversation sans à-coup : la zone de saisie glisse à sa nouvelle place, l'avatar
 * et la question d'Ayo rejoignent la première bulle (View Transitions). Sans cette API, ou si la personne a demandé
 * moins de mouvement, le changement est immédiat.
 */
function enDouceur(changement: () => void) {
  const doc = document as Document & { startViewTransition?: (f: () => Promise<void>) => unknown }
  const calme = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!vide.value || calme || typeof doc.startViewTransition !== 'function') return changement()
  doc.startViewTransition(async () => {
    changement()
    await nextTick()
  })
}

function repondre() {
  if (!saisieActive.value || !saisieValide.value) return
  const texte = saisie.value.trim()
  enDouceur(() => {
    if (tour.value === 'besoin') demande.value.titre = texte
    else if (tour.value === 'details') demande.value.description = texte
    else if (estQuestion(tour.value)) reponses.value = { ...reponses.value, [tour.value]: texte }
    suivant(tour.value)
  })
}

/** Une question guidée sans réponse : on la passe, Ayo enchaîne. */
function passer() {
  if (estQuestion(tour.value)) {
    const { [tour.value]: _retiree, ...reste } = reponses.value
    reponses.value = reste
  }
  suivant(tour.value)
}

/**
 * Un exemple se met dans la zone de saisie, comme une suggestion : rien n'est envoyé ni ajouté à la place de la
 * personne. Elle le reprend tel quel ou l'adapte, puis l'envoie elle-même.
 */
function prendreExemple(e: { titre: string }) {
  saisie.value = e.titre
  void nextTick(() => {
    if (!zone.value) return
    ajusterZone(zone.value)
    zone.value.focus()
    zone.value.setSelectionRange(saisie.value.length, saisie.value.length)
  })
}

function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    repondre()
  }
}

// ---------------------------------------------------------------- envoyer

const composee = computed(() => {
  const texte = composerDescription(
    demande.value.description,
    reponses.value,
    Object.fromEntries(QUESTIONS.map((q) => [q, t(`demandes.nouvelle.${NOMS[q].libelle}`)])) as Record<Question, string>,
  )
  return budget.value === 'inconnu' ? texte : `${texte}\n\n${t('demandes.nouvelle.libelleBudget')} ${t(`demandes.budgets.${budget.value}`)}`
})
const valide = computed(() => demande.value.description.trim().length >= LIMITES.descriptionMin && demandeValide({ ...demande.value, description: composee.value }))

async function envoyer() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const creee = await creerDemande({ ...demande.value, description: composee.value, dureeSemaines: duree.value === 'inconnue' ? undefined : duree.value })
    if (creee === 'quota') return void (envoi.value = 'quota')
    if (!creee) return void (envoi.value = 'erreur')
    await router.push(`/demandes/${creee.id}`)
  } catch {
    envoi.value = 'erreur'
  }
}

const precisionsDonnees = computed(() =>
  [
    demande.value.secteur ? t(`demandes.secteurs.${demande.value.secteur}`) : null,
    demande.value.ville?.trim() || null,
    duree.value !== 'inconnue' ? t('demandes.nouvelle.semaines', { n: duree.value }, duree.value) : null,
    budget.value !== 'inconnu' ? t(`demandes.budgets.${budget.value}`) : null,
  ].filter(Boolean) as string[],
)

// Arrivé avec un début de besoin (lien partagé, ancienne adresse) : Ayo le reprend et passe aux détails.
if (props.exempleInitial !== null && exemples.value[props.exempleInitial]) saisie.value = exemples.value[props.exempleInitial]!.titre
else if (props.titreInitial.trim().length >= LIMITES.titreMin) {
  demande.value.titre = props.titreInitial.trim().slice(0, LIMITES.titreMax)
  aller('details')
}
</script>

<template>
  <section class="conversation" :class="{ 'conversation--vide': vide }" aria-labelledby="ayo-titre" data-test="conversation-demande">
    <!-- Une nouvelle discussion : Ayo, sa question et la zone de saisie, au centre de l'écran. -->
    <header v-if="vide" class="accueil">
      <AyoAvatar :taille="64" class="avatar-premier" />
      <p class="accueil__qui"><strong>{{ t('demandes.ayo.nom') }}</strong> <BadgeIa long /></p>
      <h2 id="ayo-titre" class="accueil__titre question-premiere">{{ t('demandes.mesDemandes.ayoBonjour') }}</h2>
    </header>
    <ol v-else ref="fil" class="fil" aria-live="polite">
      <!-- Ayo ouvre la conversation. -->
      <li class="ayo">
        <AyoAvatar :taille="40" class="ayo__avatar avatar-premier" />
        <div class="ayo__bulle">
          <p class="ayo__qui"><strong>{{ t('demandes.ayo.nom') }}</strong> <BadgeIa long /></p>
          <h2 id="ayo-titre" class="ayo__texte question-premiere">{{ t('demandes.mesDemandes.ayoBonjour') }}</h2>
        </div>
      </li>

      <template v-if="atteint >= 1 && demande.titre">
        <li class="toi" data-test="moi-besoin">
          <p class="toi__bulle">{{ demande.titre }}</p>
          <button type="button" class="toi__modifier" data-test="modifier-besoin" @click="modifier('besoin')">{{ t('demandes.nouvelle.modifier') }}</button>
        </li>
        <li class="ayo">
          <AyoAvatar :taille="40" class="ayo__avatar" />
          <div class="ayo__bulle">
            <p class="ayo__texte">{{ t('demandes.nouvelle.q.details') }}</p>
            <p class="ayo__aide">{{ t('demandes.nouvelle.champDescription') }}</p>
            <p class="ayo__prudence" data-test="prudence">{{ t('demandes.nouvelle.prudence') }}</p>
          </div>
        </li>
      </template>

      <template v-if="atteint >= 2 && demande.description">
        <li class="toi" data-test="moi-details">
          <p class="toi__bulle">{{ demande.description }}</p>
          <button type="button" class="toi__modifier" data-test="modifier-details" @click="modifier('details')">{{ t('demandes.nouvelle.modifier') }}</button>
        </li>
      </template>

      <template v-for="(q, i) in QUESTIONS" :key="q">
        <template v-if="atteint >= 2 + i && demande.description">
          <li class="ayo">
            <AyoAvatar :taille="40" class="ayo__avatar" />
            <div class="ayo__bulle">
              <p class="ayo__texte">{{ t(`demandes.nouvelle.${NOMS[q].question}`) }}</p>
              <p class="ayo__aide" data-test="exemple-question">{{ t(`demandes.nouvelle.${NOMS[q].exemple}`) }}</p>
              <div v-if="tour === q" class="puces">
                <button type="button" class="puce puce--discrete" data-test="passer-question" @click="passer">{{ t('demandes.nouvelle.passer') }}</button>
              </div>
            </div>
          </li>
          <li v-if="atteint > 2 + i" class="toi" :data-test="`moi-${q}`">
            <p class="toi__bulle" :class="{ 'toi__bulle--passee': !reponses[q] }">{{ reponses[q] || t('demandes.nouvelle.passee') }}</p>
            <button type="button" class="toi__modifier" @click="modifier(q)">{{ t('demandes.nouvelle.modifier') }}</button>
          </li>
        </template>
      </template>

      <!-- Les précisions : des choix rapides, tous facultatifs. -->
      <li v-if="atteint >= 5" class="ayo">
        <AyoAvatar :taille="40" class="ayo__avatar" />
        <div class="ayo__bulle">
          <p class="ayo__texte">{{ t('demandes.nouvelle.q.precisions') }}</p>
          <div v-if="tour === 'precisions'" class="precisions" data-test="precisions">
            <fieldset class="choix">
              <legend>{{ t('demandes.nouvelle.secteur') }}</legend>
              <label v-for="s in SECTEURS" :key="s" class="pilule">
                <input v-model="demande.secteur" type="radio" name="secteur" :value="s" data-test="secteur" />
                <span>{{ t(`demandes.secteurs.${s}`) }}</span>
              </label>
            </fieldset>
            <label class="ville">
              <span>{{ t('demandes.nouvelle.ville') }}</span>
              <input v-model="demande.ville" :maxlength="80" autocomplete="address-level2" data-test="ville" />
            </label>
            <fieldset class="choix">
              <legend>{{ t('demandes.nouvelle.duree') }}</legend>
              <label v-for="d in [...DUREES, 'inconnue' as const]" :key="d" class="pilule">
                <input v-model="duree" type="radio" name="duree" :value="d" />
                <span>{{ d === 'inconnue' ? t('demandes.nouvelle.jeNeSaisPas') : t('demandes.nouvelle.semaines', { n: d }, d) }}</span>
              </label>
            </fieldset>
            <fieldset class="choix" data-test="budget">
              <legend>{{ t('demandes.nouvelle.budget') }}</legend>
              <label v-for="b in BUDGETS" :key="b" class="pilule">
                <input v-model="budget" type="radio" name="budget" :value="b" :data-test="`budget-${b}`" />
                <span>{{ t(`demandes.budgets.${b}`) }}</span>
              </label>
            </fieldset>
            <button type="button" class="continuer" data-test="continuer-precisions" @click="suivant('precisions')">
              {{ precisionsDonnees.length ? t('demandes.nouvelle.suivant') : t('demandes.nouvelle.passer') }} →
            </button>
          </div>
        </div>
      </li>
      <li v-if="atteint >= 6" class="toi" data-test="moi-precisions">
        <p class="toi__bulle" :class="{ 'toi__bulle--passee': !precisionsDonnees.length }">{{ precisionsDonnees.join(' · ') || t('demandes.nouvelle.passee') }}</p>
        <button type="button" class="toi__modifier" @click="modifier('precisions')">{{ t('demandes.nouvelle.modifier') }}</button>
      </li>

      <!-- Ayo récapitule, et on lance l'analyse. -->
      <li v-if="atteint >= 6" class="ayo" data-test="recap">
        <AyoAvatar :taille="40" class="ayo__avatar" :reflechit="envoi === 'envoi'" />
        <div class="ayo__bulle">
          <p class="ayo__texte">{{ t('demandes.nouvelle.q.envoi') }}</p>
          <ol class="ensuite">
            <li>{{ t('demandes.nouvelle.ensuite1') }}</li>
            <li>{{ t('demandes.nouvelle.ensuite2') }}</li>
            <li>{{ t('demandes.nouvelle.ensuite3') }}</li>
          </ol>
          <label class="offres" data-test="offres">
            <input v-model="demande.offresSystalink" type="checkbox" />
            <span><strong>{{ t('demandes.offres.libelle') }}</strong> <span class="texte-discret">{{ t('demandes.offres.aide') }}</span></span>
          </label>
          <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('demandes.nouvelle.erreur') }}</p>
          <p v-else-if="envoi === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</p>
          <button type="button" class="lancer" :disabled="!valide || envoi === 'envoi'" :aria-busy="envoi === 'envoi' || undefined" data-test="envoyer" @click="envoyer">
            {{ t('demandes.nouvelle.envoyer') }} →
          </button>
        </div>
      </li>
    </ol>

    <!-- La zone de saisie, comme dans une conversation avec une IA. -->
    <form class="saisie-ia" :class="{ 'saisie-ia--inactive': !saisieActive }" @submit.prevent="repondre">
      <label for="saisie-ayo" class="visuellement-cache">{{ t('demandes.nouvelle.repondreAyo') }}</label>
      <textarea
        id="saisie-ayo"
        ref="zone"
        v-model="saisie"
        rows="1"
        :disabled="!saisieActive"
        :maxlength="tour === 'besoin' ? LIMITES.titreMax : tour === 'details' ? LIMITES.descriptionSaisieMax : LIMITES.reponseMax"
        :placeholder="saisieActive ? t(`demandes.nouvelle.placeholders.${estQuestion(tour) ? 'question' : tour}`) : t('demandes.nouvelle.placeholders.choix')"
        data-test="saisie-ayo"
        @keydown="touche"
      />
      <div class="saisie-ia__bas">
        <span class="saisie-ia__aide" :class="{ ok: tour === 'details' && longueur >= LIMITES.descriptionMin }">
          <template v-if="tour === 'details' && longueur < LIMITES.descriptionMin">{{ t('demandes.nouvelle.encore', { n: LIMITES.descriptionMin - longueur }) }}</template>
          <template v-else-if="saisieActive">{{ t('demandes.nouvelle.aideSaisie') }}</template>
        </span>
        <button type="submit" class="saisie-ia__envoyer" :disabled="!saisieActive || !saisieValide" :aria-label="t('demandes.nouvelle.repondreAyo')" data-test="envoyer-reponse">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M10 15.5v-11M5.5 9 10 4.5 14.5 9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>
    </form>
    <div v-if="vide" class="puces puces--accueil">
      <button v-for="e in exemples" :key="e.titre" type="button" class="puce" data-test="exemple" @click="prendreExemple(e)">{{ e.titre }}</button>
    </div>
  </section>
</template>

<style scoped>
.conversation { display: grid; gap: var(--space-4); width: 100%; max-width: 48rem; margin-inline: auto; }
/* Comme une nouvelle discussion avec une IA : tout au centre de l'écran, rien d'autre. */
.conversation--vide { min-height: calc(100dvh - 7rem); align-content: center; gap: var(--space-6); }
.accueil { display: grid; justify-items: center; gap: var(--space-3); text-align: center; }
.accueil__qui { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.accueil__titre { margin: 0; font-size: clamp(1.625rem, 3.4vw, 2.25rem); line-height: 1.2; letter-spacing: -0.02em; }
.conversation--vide .saisie-ia { position: static; }
.puces--accueil { justify-content: center; }
.fil { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }

.ayo { display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: var(--space-3); align-items: start; animation: arrive 260ms var(--ease-out) both; }
.ayo__bulle { justify-self: start; display: grid; gap: var(--space-2); max-width: 40rem; padding: var(--space-3) var(--space-4); border-radius: 4px 18px 18px 18px; border: 1px solid var(--line); background: var(--surface); }
.ayo__qui { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.ayo__texte { margin: 0; font-size: 1.0625rem; font-weight: 600; line-height: 1.4; }
h2.ayo__texte { font-size: clamp(1.25rem, 2.2vw, 1.5rem); }
.ayo__aide { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.ayo__prudence { margin: 0; padding-inline-start: var(--space-3); border-inline-start: 3px solid var(--proof); color: var(--ink-muted); font-size: 0.8125rem; }

.toi { display: grid; justify-items: end; gap: 2px; animation: arrive 200ms var(--ease-out) both; }
.toi__bulle { margin: 0; max-width: min(36rem, 88%); padding: var(--space-3) var(--space-4); border-radius: 18px 18px 4px 18px; background: var(--surface-raised); border: 1px solid var(--line); white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; }
.toi__bulle--passee { color: var(--ink-muted); font-style: italic; }
.toi__modifier { padding: 0 var(--space-2); border: 0; background: none; color: var(--ink-muted); font: inherit; font-size: 0.75rem; cursor: pointer; }
.toi__modifier:hover { color: var(--ink); text-decoration: underline; }
.toi__modifier:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.puces { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.puce { min-height: 34px; padding: 0 var(--space-3); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); background: transparent; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); cursor: pointer; }
.puce:hover { border-style: solid; border-color: var(--ink-muted); }
.puce--discrete { border-style: solid; color: var(--ink-muted); }
.puce:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.precisions { display: grid; gap: var(--space-3); }
.choix { margin: 0; padding: 0; border: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.choix legend { margin-bottom: var(--space-1); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.pilule { position: relative; }
.pilule input { position: absolute; opacity: 0; pointer-events: none; }
.pilule span { display: inline-flex; align-items: center; min-height: 32px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); cursor: pointer; }
.pilule input:checked + span { border-color: var(--ink); background: var(--ink); color: var(--surface); }
.pilule input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }
.ville { display: grid; gap: var(--space-1); max-width: 20rem; }
.ville span { color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.ville input { min-height: 38px; }
.continuer, .lancer { justify-self: start; min-height: 40px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--ink); color: var(--surface); font: inherit; font-weight: 600; cursor: pointer; }
.lancer { background: var(--action); color: var(--on-action); }
.lancer:disabled { opacity: 0.5; cursor: not-allowed; }
.continuer:focus-visible, .lancer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ensuite { margin: 0; padding-inline-start: 1.2em; display: grid; gap: 4px; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.offres { display: flex; gap: var(--space-2); align-items: flex-start; font-size: var(--type-body-sm-size); cursor: pointer; }
.offres input { margin-top: 3px; width: 16px; height: 16px; accent-color: var(--action); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

/* La zone de saisie : la boîte, le texte qui grandit, la flèche pour répondre. Elle reste en bas en défilant. */
.saisie-ia {
  position: sticky; bottom: var(--space-4); z-index: 2;
  display: grid; gap: var(--space-1); padding: var(--space-3) var(--space-3) var(--space-2) var(--space-4);
  border: 1px solid var(--line-strong); border-radius: 22px; background: var(--surface-raised);
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.08), 0 10px 30px rgb(2 7 10 / 0.16);
}
.saisie-ia:focus-within { border-color: color-mix(in srgb, var(--ink) 35%, var(--line-strong)); }
.saisie-ia--inactive { opacity: 0.6; }
.saisie-ia textarea { width: 100%; min-height: 0; padding: var(--space-1) 0; border: 0; border-radius: 0; background: transparent; color: var(--ink); font: inherit; font-size: 1.0625rem; line-height: 1.5; resize: none; outline: none; box-shadow: none; }
.saisie-ia textarea::placeholder { color: color-mix(in srgb, var(--ink) 40%, transparent); }
.saisie-ia__bas { display: flex; align-items: center; gap: var(--space-3); }
.saisie-ia__aide { flex: 1; min-width: 0; color: var(--ink-muted); font-size: 0.8125rem; }
.saisie-ia__aide.ok { color: #1a7f45; font-weight: 600; }
.saisie-ia__envoyer { display: grid; place-items: center; flex: none; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: var(--ink); color: var(--surface); cursor: pointer; }
.saisie-ia__envoyer:disabled { opacity: 0.25; cursor: not-allowed; }
.saisie-ia__envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
@keyframes arrive { from { opacity: 0; transform: translateY(6px); } }
@media (prefers-reduced-motion: reduce) { .ayo, .toi { animation: none; } }
@media (max-width: 640px) { .ayo { grid-template-columns: minmax(0, 1fr); } .ayo__avatar { display: none; } }

/* Les éléments qui voyagent de l'accueil à la conversation (un seul de chaque est affiché à la fois). */
.saisie-ia { view-transition-name: saisie-ayo; }
.avatar-premier { view-transition-name: avatar-ayo; }
.question-premiere { view-transition-name: question-ayo; width: fit-content; }
:global(::view-transition-group(saisie-ayo)),
:global(::view-transition-group(avatar-ayo)),
:global(::view-transition-group(question-ayo)) { animation-duration: 420ms; animation-timing-function: cubic-bezier(0.2, 0.8, 0.2, 1); }
:global(::view-transition-old(root)), :global(::view-transition-new(root)) { animation-duration: 260ms; }
</style>
