<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JBouton } from '../../design-system'
import {
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
 * « Décris ton besoin » (étape 1 de la démo, /besoin), une question à la fois, comme on crée un cercle ou une
 * communauté. Écrit pour un commerçant ou un cabinet, pas pour un développeur : des mots simples, des exemples à
 * reprendre, et aucune question technique. Les questions guidées, le secteur, la ville et la durée sont facultatifs.
 * La case Systalink n'est jamais cochée d'avance. Entrée fait avancer ; le récapitulatif sur le côté suit chaque frappe.
 */
const { t, tm, rt } = useI18n()
const router = useRouter()

const ETAPES = ['besoin', 'details', 'questions', 'precisions', 'envoi'] as const
type Etape = (typeof ETAPES)[number]
const etape = ref<Etape>('besoin')
const sens = ref<'avant' | 'arriere'>('avant')
const rang = computed(() => ETAPES.indexOf(etape.value))

const demande = ref<NouvelleDemande>({ titre: '', description: '', secteur: '', ville: '', offresSystalink: false })
const duree = ref<number | 'inconnue'>('inconnue')
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'quota'>('repos')
const reponses = ref<Reponses>({})
const NOMS: Record<Question, { question: string; libelle: string }> = {
  qui: { question: 'questionQui', libelle: 'libelleQui' },
  probleme: { question: 'questionProbleme', libelle: 'libelleProbleme' },
  reussite: { question: 'questionReussite', libelle: 'libelleReussite' },
}
/** Le besoin tel qu'il partira : le texte, puis les réponses guidées avec leur question. */
const composee = computed(() =>
  composerDescription(
    demande.value.description,
    reponses.value,
    Object.fromEntries(QUESTIONS.map((q) => [q, t(`demandes.nouvelle.${NOMS[q].libelle}`)])) as Record<Question, string>,
  ),
)
const titre = computed(() => demande.value.titre.trim())
const longueur = computed(() => demande.value.description.trim().length)
const valide = computed(() => longueur.value >= LIMITES.descriptionMin && demandeValide({ ...demande.value, description: composee.value }))

const valides: Record<Etape, () => boolean> = {
  besoin: () => titre.value.length >= LIMITES.titreMin && titre.value.length <= LIMITES.titreMax,
  details: () => longueur.value >= LIMITES.descriptionMin,
  questions: () => true,
  precisions: () => true,
  envoi: () => valide.value,
}
const etapeValide = computed(() => valides[etape.value]())

const champ = ref<HTMLElement | null>(null)
function aller(vers: Etape) {
  sens.value = ETAPES.indexOf(vers) > rang.value ? 'avant' : 'arriere'
  etape.value = vers
}
function suivant() {
  if (!etapeValide.value) return
  if (etape.value === 'envoi') return void envoyer()
  aller(ETAPES[rang.value + 1]!)
}
function precedent() {
  if (rang.value > 0) aller(ETAPES[rang.value - 1]!)
}
function focaliser() {
  champ.value?.focus({ preventScroll: true })
}
onMounted(() => void nextTick(focaliser))

/** Des exemples à reprendre d'un clic : ils montrent le niveau de détail attendu. */
const exemples = computed(() => (tm('demandes.exemples') as { titre: string; description: string }[]).map((e) => ({ titre: rt(e.titre), description: rt(e.description) })))
function reprendre(e: { titre: string; description: string }) {
  demande.value = { ...demande.value, titre: e.titre, description: e.description }
}

/** Les questions guidées auxquelles on a répondu. */
const repondues = computed(() => QUESTIONS.filter((q) => (reponses.value[q] ?? '').trim().length > 0).length)

async function envoyer() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const creee = await creerDemande({
      ...demande.value,
      description: composee.value,
      dureeSemaines: duree.value === 'inconnue' ? undefined : duree.value,
    })
    if (creee === 'quota') return void (envoi.value = 'quota')
    if (!creee) return void (envoi.value = 'erreur')
    await router.push(`/demandes/${creee.id}`)
  } catch {
    envoi.value = 'erreur'
  }
}

/** Entrée avance ; dans un texte long, Maj + Entrée passe à la ligne. */
function touche(e: KeyboardEvent) {
  if (e.key !== 'Enter' || e.isComposing) return
  if (e.shiftKey && (e.target as HTMLElement | null)?.tagName === 'TEXTAREA') return
  if ((e.target as HTMLElement | null)?.tagName === 'BUTTON') return
  e.preventDefault()
  suivant()
}
</script>

<template>
  <form class="assistant" aria-labelledby="titre-etape" data-test="demande" novalidate @submit.prevent="envoyer" @keydown="touche">
    <header class="assistant__haut">
      <RouterLink to="/demandes" class="retour">← {{ t('demandes.mesDemandes.titre') }}</RouterLink>
      <p class="assistant__rang" aria-live="polite">{{ t('demandes.nouvelle.etape', { n: rang + 1, total: ETAPES.length }) }}</p>
    </header>
    <div class="progression" role="progressbar" :aria-valuenow="rang + 1" aria-valuemin="1" :aria-valuemax="ETAPES.length" :aria-label="t('demandes.nouvelle.titre')">
      <span class="progression__barre" :style="{ width: `${((rang + 1) / ETAPES.length) * 100}%` }" />
    </div>

    <div class="assistant__scene">
      <div class="carte">
        <Transition :name="`glisse-${sens}`" mode="out-in" @after-enter="focaliser">
          <div v-if="etape === 'besoin'" key="besoin" class="etape">
            <p class="etape__numero" aria-hidden="true">01</p>
            <h1 id="titre-etape">{{ t('demandes.nouvelle.q.besoin') }}</h1>
            <p class="etape__aide">{{ t('demandes.nouvelle.intro') }}</p>
            <label class="visuellement-cache" for="champ-titre">{{ t('demandes.nouvelle.champTitre') }}</label>
            <input
              id="champ-titre"
              ref="champ"
              v-model="demande.titre"
              class="grand-champ"
              :maxlength="LIMITES.titreMax"
              autocomplete="off"
              :placeholder="t('demandes.nouvelle.aideTitre')"
              data-test="champ-titre"
            />
            <div class="exemples">
              <p class="texte-discret">{{ t('demandes.nouvelle.exemplesTitre') }}</p>
              <button v-for="e in exemples" :key="e.titre" type="button" class="exemple" data-test="exemple" @click="reprendre(e)">
                <strong>{{ e.titre }}</strong>
                <span>{{ e.description }}</span>
              </button>
            </div>
          </div>

          <div v-else-if="etape === 'details'" key="details" class="etape">
            <p class="etape__numero" aria-hidden="true">02</p>
            <h1 id="titre-etape">{{ t('demandes.nouvelle.q.details') }}</h1>
            <p class="etape__aide">{{ t('demandes.nouvelle.champDescription') }}</p>
            <p class="prudence" data-test="prudence">{{ t('demandes.nouvelle.prudence') }}</p>
            <label class="visuellement-cache" for="champ-description">{{ t('demandes.nouvelle.champDescription') }}</label>
            <textarea
              id="champ-description"
              ref="champ"
              v-model="demande.description"
              class="grand-champ grand-champ--texte"
              rows="5"
              :maxlength="LIMITES.descriptionSaisieMax"
              data-test="champ-description"
            />
            <p class="compteur" :class="{ 'compteur--ok': longueur >= LIMITES.descriptionMin }">
              {{ longueur < LIMITES.descriptionMin ? t('demandes.nouvelle.encore', { n: LIMITES.descriptionMin - longueur }) : t('demandes.nouvelle.assez') }}
            </p>
          </div>

          <div v-else-if="etape === 'questions'" key="questions" class="etape">
            <p class="etape__numero" aria-hidden="true">03</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('demandes.nouvelle.q.questions') }}</h1>
            <p class="etape__aide">{{ t('demandes.nouvelle.questionsAide') }}</p>
            <fieldset class="questions" data-test="questions">
              <legend class="visuellement-cache">{{ t('demandes.nouvelle.questionsTitre') }}</legend>
              <label v-for="(q, i) in QUESTIONS" :key="q" class="question" :style="{ '--i': i }">
                <span>{{ t(`demandes.nouvelle.${NOMS[q].question}`) }}</span>
                <input v-model="reponses[q]" :maxlength="LIMITES.reponseMax" autocomplete="off" :data-test="`question-${q}`" />
              </label>
            </fieldset>
          </div>

          <div v-else-if="etape === 'precisions'" key="precisions" class="etape">
            <p class="etape__numero" aria-hidden="true">04</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('demandes.nouvelle.q.precisions') }}</h1>
            <p class="etape__aide">{{ t('demandes.nouvelle.precisionsAide') }}</p>
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
          </div>

          <div v-else key="envoi" class="etape">
            <p class="etape__numero" aria-hidden="true">05</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('demandes.nouvelle.q.envoi') }}</h1>
            <ol class="ensuite">
              <li><span aria-hidden="true">1</span> {{ t('demandes.nouvelle.ensuite1') }}</li>
              <li><span aria-hidden="true">2</span> {{ t('demandes.nouvelle.ensuite2') }}</li>
              <li><span aria-hidden="true">3</span> {{ t('demandes.nouvelle.ensuite3') }}</li>
            </ol>
            <label class="offres" data-test="offres">
              <input v-model="demande.offresSystalink" type="checkbox" />
              <span>
                <strong>{{ t('demandes.offres.libelle') }}</strong>
                <span class="texte-discret">{{ t('demandes.offres.aide') }}</span>
              </span>
            </label>
            <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('demandes.nouvelle.erreur') }}</p>
            <p v-else-if="envoi === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</p>
          </div>
        </Transition>

        <footer class="commandes">
          <JBouton v-if="rang > 0" variante="discret" data-test="precedent" @click="precedent">← {{ t('demandes.nouvelle.retour') }}</JBouton>
          <span class="commandes__aide" aria-hidden="true">{{ etape === 'envoi' ? t('demandes.nouvelle.rassurance') : t('demandes.nouvelle.entree') }}</span>
          <JBouton v-if="etape === 'envoi'" type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="envoyer">{{ t('demandes.nouvelle.envoyer') }} →</JBouton>
          <JBouton v-else :desactive="!etapeValide" data-test="suivant" @click="suivant">
            {{ (etape === 'questions' && repondues === 0) || (etape === 'precisions' && !demande.secteur && !demande.ville && duree === 'inconnue') ? t('demandes.nouvelle.passer') : t('demandes.nouvelle.suivant') }} →
          </JBouton>
        </footer>
      </div>

      <!-- Le récapitulatif vivant : ce que Jamii lira, au fil des étapes. -->
      <aside class="vivant" :aria-label="t('demandes.nouvelle.recap')">
        <div class="vivant__carte">
          <p class="vivant__surtitre">{{ t('demandes.nouvelle.recap') }}</p>
          <strong class="vivant__titre" :class="{ vide: !titre }">{{ titre || t('demandes.nouvelle.titreAVenir') }}</strong>
          <p class="vivant__texte" :class="{ vide: !longueur }">{{ demande.description.trim() || t('demandes.nouvelle.descriptionAVenir') }}</p>
          <ul v-if="demande.secteur || demande.ville?.trim() || duree !== 'inconnue' || repondues" class="vivant__etiquettes">
            <li v-if="demande.secteur">{{ t(`demandes.secteurs.${demande.secteur}`) }}</li>
            <li v-if="demande.ville?.trim()">{{ demande.ville.trim() }}</li>
            <li v-if="duree !== 'inconnue'">{{ t('demandes.nouvelle.semaines', { n: duree }, duree) }}</li>
            <li v-if="repondues">{{ t('demandes.nouvelle.precisionsDonnees', { n: repondues }, repondues) }}</li>
          </ul>
        </div>
        <ol class="vivant__etapes">
          <li v-for="(e, i) in ETAPES" :key="e" :class="{ fait: i < rang, actuelle: i === rang }">
            <button type="button" :disabled="i > rang" @click="aller(e)">{{ t(`demandes.nouvelle.etapes.${e}`) }}</button>
          </li>
        </ol>
      </aside>
    </div>
  </form>
</template>

<style scoped>
/* Même gabarit que la création d'un cercle ou d'une communauté : une seule façon de créer dans Jamii. */
.assistant { display: grid; gap: var(--space-4); max-width: 72rem; margin: var(--space-4) auto 0; }
.assistant__haut { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.retour { font-size: var(--type-body-sm-size); }
.assistant__rang { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; font-variant-numeric: tabular-nums; }
.progression { height: 6px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 8%, transparent); overflow: hidden; }
.progression__barre { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--electric-blue), var(--cantaloupe)); transition: width 480ms var(--ease-out); }

.assistant__scene { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr) 20rem; align-items: start; }
@media (max-width: 1000px) { .assistant__scene { grid-template-columns: 1fr; } .vivant { display: none; } }

.carte {
  position: relative; display: grid; grid-template-rows: minmax(0, 1fr) auto; min-height: min(36rem, calc(100dvh - 15rem));
  padding: clamp(var(--space-6), 4vw, var(--space-12)); overflow: hidden;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
}
.etape { display: grid; gap: var(--space-4); align-content: start; }
.etape__numero { margin: 0; font-family: var(--font-display); font-weight: 700; font-size: 0.875rem; color: var(--cantaloupe); letter-spacing: 0.08em; }
.etape h1 { margin: 0; font-size: clamp(1.75rem, 3.4vw, 2.75rem); line-height: 1.08; letter-spacing: -0.03em; max-width: 22ch; outline: none; }
.etape__aide { margin: 0; color: var(--ink-muted); max-width: 56ch; }

.grand-champ {
  width: 100%; padding: var(--space-3) 0; border: 0; border-bottom: 3px solid var(--line-strong); border-radius: 0;
  background: transparent; color: var(--ink); font-family: var(--font-display); font-size: clamp(1.375rem, 2.6vw, 2rem); font-weight: 700; outline: none;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.grand-champ:focus { border-bottom-color: var(--electric-blue); }
.grand-champ::placeholder { color: color-mix(in srgb, var(--ink) 25%, transparent); }
.grand-champ--texte { font-family: var(--font-text); font-size: 1.125rem; font-weight: 500; line-height: 1.55; resize: vertical; min-height: 8rem; }
.compteur { margin: 0; justify-self: end; color: var(--ink-muted); font-size: 0.8125rem; }
.compteur--ok { color: #1a7f45; font-weight: 600; }
.prudence {
  margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe);
  background: var(--surface-raised); font-size: var(--type-body-sm-size); font-weight: 600;
}

.exemples { display: grid; gap: var(--space-2); }
.exemples p { margin: var(--space-2) 0 0; font-size: var(--type-body-sm-size); }
.exemple {
  display: grid; gap: 2px; text-align: start; cursor: pointer; padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md); border: 1px dashed var(--line-strong); background: transparent; color: var(--ink); font: inherit;
  transition: border-color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.exemple:hover { border-style: solid; border-color: var(--action); background: var(--surface-raised); transform: translateY(-1px); }
.exemple:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.exemple strong { font-size: var(--type-body-sm-size); }
.exemple span { color: var(--ink-muted); font-size: var(--type-body-sm-size); display: -webkit-box; -webkit-line-clamp: 1; line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }

.questions { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.question { display: grid; gap: var(--space-2); animation: arrive 360ms var(--ease-out) both; animation-delay: calc(var(--i) * 60ms); }
.question span { font-weight: 700; font-size: 1.0625rem; }
.question input, .ville input {
  min-height: 48px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--surface); color: var(--ink); font: inherit;
}
.question input:focus, .ville input:focus { outline: 2px solid var(--focus); outline-offset: 1px; border-color: transparent; }
.ville { display: grid; gap: var(--space-2); max-width: 24rem; }
.ville span { font-size: var(--type-label-size); font-weight: var(--type-label-weight); }

.choix { border: 0; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.choix legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.pilule { position: relative; }
.pilule input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
.pilule span {
  display: inline-flex; align-items: center; min-height: 40px; padding: 0 var(--space-4);
  border: 1px solid var(--line-strong); border-radius: var(--radius-pill); background: var(--surface);
  font-size: var(--type-body-sm-size); font-weight: 600;
  transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-pop);
}
.pilule:hover span { transform: translateY(-1px); border-color: var(--ink-muted); }
.pilule input:checked + span { background: var(--action); border-color: var(--action); color: var(--on-action); }
.pilule input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }

.ensuite { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); max-width: 58ch; }
.ensuite li { display: flex; gap: var(--space-3); align-items: baseline; }
.ensuite span {
  flex-shrink: 0; display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%;
  background: var(--action-soft); color: var(--action-ink); font-weight: 800; font-size: 0.875rem;
}
.offres { display: flex; gap: var(--space-3); align-items: flex-start; cursor: pointer; padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); }
.offres input { width: 20px; height: 20px; margin: 2px 0 0; flex: none; accent-color: var(--action); }
.offres > span { display: grid; gap: 2px; }
.offres .texte-discret { font-size: var(--type-body-sm-size); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

.commandes { display: flex; align-items: center; gap: var(--space-3); padding-top: var(--space-6); }
.commandes__aide { margin-inline-start: auto; color: var(--ink-muted); font-size: 0.8125rem; text-align: end; }
@media (max-width: 640px) { .commandes__aide { display: none; } .commandes > :last-child { margin-inline-start: auto; } }

.vivant { position: sticky; top: calc(64px + var(--space-6)); display: grid; gap: var(--space-4); }
.vivant__carte { display: grid; gap: var(--space-2); padding: var(--space-5, 1.25rem); background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); border-top: 4px solid var(--cantaloupe); }
.vivant__surtitre { margin: 0; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; }
.vivant__titre { font-size: 1.125rem; line-height: 1.25; overflow-wrap: anywhere; }
.vivant__texte { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); white-space: pre-line; display: -webkit-box; -webkit-line-clamp: 6; line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
.vide { color: color-mix(in srgb, var(--ink) 35%, transparent); font-style: italic; }
.vivant__etiquettes { list-style: none; margin: var(--space-1) 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.vivant__etiquettes li { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 600; }
.vivant__etapes { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; counter-reset: etape; }
.vivant__etapes li { counter-increment: etape; }
.vivant__etapes button {
  display: flex; align-items: center; gap: var(--space-3); width: 100%; padding: var(--space-2) var(--space-3); border: 0; border-radius: var(--radius-md);
  background: transparent; color: var(--ink-muted); font: inherit; font-size: var(--type-body-sm-size); text-align: start; cursor: pointer;
}
.vivant__etapes button::before {
  content: counter(etape); display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%;
  border: 1.5px solid var(--line-strong); font-size: 0.75rem; font-weight: 700;
}
.vivant__etapes .fait button::before { content: '✓'; background: var(--ink); color: var(--bg); border-color: var(--ink); }
.vivant__etapes .actuelle button { background: var(--surface); color: var(--ink); font-weight: 700; box-shadow: var(--ombre-carte); }
.vivant__etapes .actuelle button::before { border-color: var(--cantaloupe); color: var(--cantaloupe); }
.vivant__etapes button:disabled { cursor: default; opacity: 0.6; }

.glisse-avant-enter-active, .glisse-avant-leave-active, .glisse-arriere-enter-active, .glisse-arriere-leave-active {
  transition: opacity 240ms var(--ease-out), transform 300ms var(--ease-out);
}
.glisse-avant-enter-from { opacity: 0; transform: translateX(48px); }
.glisse-avant-leave-to { opacity: 0; transform: translateX(-48px); }
.glisse-arriere-enter-from { opacity: 0; transform: translateX(-48px); }
.glisse-arriere-leave-to { opacity: 0; transform: translateX(48px); }
@keyframes arrive { from { opacity: 0; transform: translateY(10px); } }

@media (prefers-reduced-motion: reduce) {
  .progression__barre, .question, .pilule span, .exemple { transition: none; animation: none; }
  .glisse-avant-enter-active, .glisse-avant-leave-active, .glisse-arriere-enter-active, .glisse-arriere-leave-active { transition: none; }
  .glisse-avant-enter-from, .glisse-avant-leave-to, .glisse-arriere-enter-from, .glisse-arriere-leave-to { transform: none; }
}
</style>
