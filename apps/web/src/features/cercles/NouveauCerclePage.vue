<script setup lang="ts">
import LienRetour from '../../components/LienRetour.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JAvatar, JBouton } from '../../design-system'
import { useSession } from '../../session'
import { creerCercle, LIMITES, PROFILS, positionsAnneau, profilsMax, TAILLES, type Cercle, type ProfilRecherche } from './cercles'
import { lireDuBesoin, prenom } from '../reseau/reseau'

/*
 * Créer un cercle, une question à la fois, comme on crée une communauté : le nom, combien on sera, puis l'aperçu. Pas de
 * ville : un cercle travaille en ligne, ses membres peuvent venir de tout le continent. Les étapes glissent (rien si le
 * mouvement est réduit), Entrée fait avancer, et l'anneau sur le côté suit chaque choix. Une fois créé, on le salue,
 * puis on entre dans le cercle.
 */
const { t, locale } = useI18n()
const router = useRouter()
const { moi } = useSession()

// Venu de l'orientation d'un besoin : le titre arrive par l'état de la navigation (jamais par l'adresse).
const depart = (router.options.history.state ?? {}) as { nom?: unknown; duBesoin?: unknown }
const duBesoin = lireDuBesoin(depart.duBesoin)
const listeProfils = computed(() => new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(profils.value.map((p) => t(`cercles.profils.${p}`))))
const nomsDuBesoin = computed(() => new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(duBesoin.map((p) => prenom(p.nom))))

const ETAPES = ['nom', 'taille', 'profils', 'apercu'] as const
type Etape = (typeof ETAPES)[number]
const etape = ref<Etape>('nom')
const sens = ref<'avant' | 'arriere'>('avant')
const rang = computed(() => ETAPES.indexOf(etape.value))

const nom = ref(typeof depart.nom === 'string' ? depart.nom.slice(0, LIMITES.nomMax) : '')
const taille = ref<number | null>(null)
/** Les profils dont le cercle a besoin : facultatif, au plus un par place libre. */
const profils = ref<ProfilRecherche[]>([])
const maxProfils = computed(() => profilsMax(taille.value ?? 6))
function basculerProfil(p: ProfilRecherche) {
  if (profils.value.includes(p)) profils.value = profils.value.filter((x) => x !== p)
  else if (profils.value.length < maxProfils.value) profils.value = [...profils.value, p]
}
watch(maxProfils, (m) => (profils.value = profils.value.slice(0, m)))
const nomPropre = computed(() => nom.value.trim())

const valides: Record<Etape, () => boolean> = {
  nom: () => nomPropre.value.length >= LIMITES.nomMin,
  taille: () => taille.value !== null,
  profils: () => true,
  apercu: () => true,
}
const etapeValide = computed(() => valides[etape.value]())

const champ = ref<HTMLElement | null>(null)
function aller(vers: Etape) {
  sens.value = ETAPES.indexOf(vers) > rang.value ? 'avant' : 'arriere'
  etape.value = vers
}
function suivant() {
  if (!etapeValide.value) return
  if (etape.value === 'apercu') return void creer()
  aller(ETAPES[rang.value + 1]!)
}
function precedent() {
  if (rang.value > 0) aller(ETAPES[rang.value - 1]!)
}
/** À chaque étape, le curseur va dans son champ ou sur sa question, une fois l'animation d'entrée finie. */
function focaliser() {
  champ.value?.focus({ preventScroll: true })
}
onMounted(() => void nextTick(focaliser))

let avance: ReturnType<typeof setTimeout> | undefined
/** Choisir la taille fait avancer tout seul. */
function choisirTaille(n: number) {
  taille.value = n
  clearTimeout(avance)
  avance = setTimeout(() => etape.value === 'taille' && suivant(), 320)
}
onBeforeUnmount(() => {
  clearTimeout(avance)
  clearTimeout(entree)
})

const INSPIRATIONS = ['Cercle Ubuntu', 'Les Bâtisseurs', 'Cercle Mobile Money', 'Teranga Tech'] as const

/** L'anneau de l'aperçu : moi à la première place, puis les places libres. 6 par défaut tant qu'on n'a pas choisi. */
const places = computed(() => positionsAnneau(taille.value ?? 6, 38))

const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const cree = ref<Cercle | null>(null)
let entree: ReturnType<typeof setTimeout> | undefined
async function creer() {
  if (envoi.value === 'envoi' || taille.value === null) return
  envoi.value = 'envoi'
  try {
    const r = await creerCercle({ nom: nomPropre.value, taille: taille.value, profils: profils.value })
    if (!r.ok) return void (envoi.value = 'erreur')
    envoi.value = 'repos'
    cree.value = r.cercle
    entree = setTimeout(entrer, 2200)
  } catch {
    envoi.value = 'erreur'
  }
}
function entrer() {
  clearTimeout(entree)
  if (cree.value) void router.push({ path: `/cercles/${cree.value.id}`, state: duBesoin.length ? { duBesoin } : {} })
}

function touche(e: KeyboardEvent) {
  if (e.key !== 'Enter' || e.isComposing) return
  e.preventDefault()
  suivant()
}
</script>

<template>
  <section class="assistant" aria-labelledby="titre-etape" data-test="nouveau-cercle" @keydown="touche">
    <header class="assistant__haut">
      <LienRetour to="/cercles">{{ t('cercles.tous') }}</LienRetour>
      <p v-if="!cree" class="assistant__rang" aria-live="polite">{{ t('cercles.nouveau.etape', { n: rang + 1, total: ETAPES.length }) }}</p>
    </header>
    <div v-if="!cree" class="progression" role="progressbar" :aria-valuenow="rang + 1" aria-valuemin="1" :aria-valuemax="ETAPES.length" :aria-label="t('cercles.nouveau.titre')">
      <span class="progression__barre" :style="{ width: `${((rang + 1) / ETAPES.length) * 100}%` }" />
    </div>

    <div class="assistant__scene" :class="{ 'assistant__scene--fin': cree }">
      <div class="carte">
        <Transition :name="cree ? 'apparait' : `glisse-${sens}`" mode="out-in" @after-enter="focaliser">
          <div v-if="cree" key="succes" class="etape succes" data-test="succes">
            <div class="succes__eclats" aria-hidden="true">
              <span v-for="i in 12" :key="i" :style="{ '--i': i, '--couleur': i % 2 ? 'var(--cantaloupe)' : 'var(--electric-blue)' }" />
            </div>
            <span class="succes__anneau" aria-hidden="true">
              <i v-for="(p, i) in positionsAnneau(cree.taille, 38)" :key="i" :class="{ pris: i === 0 }" :style="{ left: `${p.x}%`, top: `${p.y}%` }" />
            </span>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('cercles.nouveau.succes', { nom: cree.nom }) }}</h1>
            <p class="etape__aide">{{ t('cercles.nouveau.succesAide', { n: cree.taille - 1 }) }}</p>
            <JBouton data-test="entrer" @click="entrer">{{ t('cercles.nouveau.entrer') }} →</JBouton>
          </div>

          <div v-else-if="etape === 'nom'" key="nom" class="etape">
            <p class="etape__numero" aria-hidden="true">01</p>
            <h1 id="titre-etape">{{ t('cercles.nouveau.q.nom') }}</h1>
            <p class="etape__aide">{{ t('cercles.nouveau.q.nomAide') }}</p>
            <label class="visuellement-cache" for="champ-nom">{{ t('cercles.nouveau.nom') }}</label>
            <input
              id="champ-nom"
              ref="champ"
              v-model="nom"
              class="grand-champ"
              :maxlength="LIMITES.nomMax"
              autocomplete="off"
              :placeholder="t('cercles.nouveau.nomExemple')"
              data-test="champ-nom"
            />
            <div class="pastilles">
              <span class="texte-discret">{{ t('cercles.nouveau.inspirations') }}</span>
              <button v-for="i in INSPIRATIONS" :key="i" type="button" class="pastille" @click="nom = i">{{ i }}</button>
            </div>
            <p v-if="duBesoin.length" class="etape__aide" data-test="du-besoin">{{ t('cercles.nouveau.duBesoin', { noms: nomsDuBesoin }) }}</p>
          </div>

          <div v-else-if="etape === 'taille'" key="taille" class="etape">
            <p class="etape__numero" aria-hidden="true">02</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('cercles.nouveau.q.taille') }}</h1>
            <p class="etape__aide">{{ t('cercles.nouveau.q.tailleAide') }}</p>
            <fieldset class="tuiles">
              <legend class="visuellement-cache">{{ t('cercles.nouveau.taille') }}</legend>
              <label v-for="(n, i) in TAILLES" :key="n" class="tuile" :class="{ 'tuile--choisie': taille === n }" :style="{ '--i': i }">
                <input type="radio" name="taille" :value="n" :checked="taille === n" :data-test="`taille-${n}`" @change="choisirTaille(n)" />
                <span class="tuile__anneau" aria-hidden="true">
                  <i v-for="(p, j) in positionsAnneau(n, 36)" :key="j" :style="{ left: `${p.x}%`, top: `${p.y}%` }" />
                </span>
                <span class="tuile__nom">{{ t('cercles.nouveau.personnes', { n }) }}</span>
                <span class="tuile__exemples">{{ t('cercles.nouveau.duree', { semaines: n * 2 }) }}</span>
              </label>
            </fieldset>
          </div>

          <div v-else-if="etape === 'profils'" key="profils" class="etape">
            <p class="etape__numero" aria-hidden="true">03</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('cercles.nouveau.q.profils') }}</h1>
            <p class="etape__aide">{{ t('cercles.nouveau.q.profilsAide', { n: maxProfils }, maxProfils) }}</p>
            <fieldset class="profils" data-test="profils">
              <legend class="visuellement-cache">{{ t('cercles.nouveau.etapes.profils') }}</legend>
              <label
                v-for="p in PROFILS"
                :key="p"
                class="profil"
                :class="{ 'profil--choisi': profils.includes(p), 'profil--plein': !profils.includes(p) && profils.length >= maxProfils }"
              >
                <input type="checkbox" :checked="profils.includes(p)" :disabled="!profils.includes(p) && profils.length >= maxProfils" :data-test="`profil-${p}`" @change="basculerProfil(p)" />
                <span class="profil__nom">{{ t(`cercles.profils.${p}`) }}</span>
                <span class="profil__aide">{{ t(`cercles.profilsAide.${p}`) }}</span>
              </label>
            </fieldset>
          </div>

          <div v-else key="apercu" class="etape">
            <p class="etape__numero" aria-hidden="true">04</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('cercles.nouveau.q.apercu', { nom: nomPropre }) }}</h1>
            <ol class="promesses">
              <li><span aria-hidden="true">1</span> {{ t('cercles.nouveau.promesses.membre') }}</li>
              <li><span aria-hidden="true">2</span> {{ t('cercles.nouveau.promesses.idees', { n: (taille ?? 6) - 1 }) }}</li>
              <li><span aria-hidden="true">3</span> {{ t('cercles.nouveau.promesses.tirage') }}</li>
              <li><span aria-hidden="true">4</span> {{ t('cercles.nouveau.promesses.tours', { n: taille ?? 6 }) }}</li>
            </ol>
            <p v-if="profils.length" class="etape__aide" data-test="profils-choisis">{{ t('cercles.nouveau.onCherche', { profils: listeProfils }) }}</p>
            <p v-if="duBesoin.length" class="etape__aide">{{ t('cercles.nouveau.duBesoin', { noms: nomsDuBesoin }) }}</p>
            <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('cercles.erreurs.autre') }}</p>
          </div>
        </Transition>

        <footer v-if="!cree" class="commandes">
          <JBouton v-if="rang > 0" variante="discret" data-test="precedent" @click="precedent">← {{ t('cercles.nouveau.retour') }}</JBouton>
          <span class="commandes__aide" aria-hidden="true">{{ t('cercles.nouveau.entree') }}</span>
          <JBouton :desactive="!etapeValide" :occupe="envoi === 'envoi'" data-test="suivant" @click="suivant">
            {{ etape === 'apercu' ? t('cercles.nouveau.creer') : t('cercles.nouveau.suivant') }} →
          </JBouton>
        </footer>
      </div>

      <!-- L'aperçu vivant : l'anneau du cercle, moi à la première place, qui suit le nom et la taille. -->
      <aside v-if="!cree" class="vivant" :aria-label="t('cercles.nouveau.apercu')">
        <div class="vivant__carte">
          <div class="vivant__anneau">
            <span v-for="(p, i) in places" :key="`${places.length}-${i}`" class="vivant__place" :class="{ 'vivant__place--moi': i === 0 }" :style="{ left: `${p.x}%`, top: `${p.y}%`, '--i': i }">
              <JAvatar v-if="i === 0" :nom="moi?.nom ?? '?'" :url="moi?.avatarUrl" :taille="40" />
            </span>
            <strong class="vivant__nom">{{ nomPropre || t('cercles.nouveau.nomExemple') }}</strong>
          </div>
          <p class="texte-discret">{{ taille ? t('cercles.nouveau.places', { n: taille - 1 }) : t('cercles.nouveau.tailleAVenir') }}</p>
          <ul v-if="profils.length" class="vivant__profils">
            <li v-for="p in profils" :key="p">{{ t(`cercles.profils.${p}`) }}</li>
          </ul>
        </div>
        <ol class="vivant__etapes">
          <li v-for="(e, i) in ETAPES" :key="e" :class="{ fait: i < rang, actuelle: i === rang }">
            <button type="button" :disabled="i > rang" @click="aller(e)">{{ t(`cercles.nouveau.etapes.${e}`) }}</button>
          </li>
        </ol>
      </aside>
    </div>
  </section>
</template>

<style scoped>
/* Même gabarit que la création d'une communauté (communautes/NouvelleCommunautePage.vue), pour une seule façon de créer. */
.assistant { display: grid; gap: var(--space-4); max-width: 72rem; margin: var(--space-4) auto 0; }
.assistant__haut { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.retour { font-size: var(--type-body-sm-size); }
.assistant__rang { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; font-variant-numeric: tabular-nums; }
.progression { height: 6px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 8%, transparent); overflow: hidden; }
.progression__barre { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--electric-blue), var(--cantaloupe)); transition: width 480ms var(--ease-out); }

.assistant__scene { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1fr) 20rem; align-items: start; }
.assistant__scene--fin { grid-template-columns: minmax(0, 1fr); }
@media (max-width: 1000px) { .assistant__scene { grid-template-columns: 1fr; } .vivant { display: none; } }

.carte {
  position: relative; display: grid; grid-template-rows: minmax(0, 1fr) auto; min-height: min(36rem, calc(100dvh - 15rem));
  padding: clamp(var(--space-6), 4vw, var(--space-12)); overflow: hidden;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
}
.etape { display: grid; gap: var(--space-4); align-content: start; }
.etape__numero { margin: 0; font-family: var(--font-display); font-weight: 700; font-size: 0.875rem; color: var(--cantaloupe); letter-spacing: 0.08em; }
.etape h1 { margin: 0; font-size: clamp(1.75rem, 3.4vw, 2.75rem); line-height: 1.08; letter-spacing: -0.03em; max-width: 22ch; outline: none; overflow-wrap: anywhere; }
.etape__aide { margin: 0; color: var(--ink-muted); max-width: 52ch; }

.grand-champ {
  width: 100%; padding: var(--space-3) 0; border: 0; border-bottom: 3px solid var(--line-strong); border-radius: 0;
  background: transparent; color: var(--ink); font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 700; outline: none;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.grand-champ:focus { border-bottom-color: var(--electric-blue); }
.grand-champ::placeholder { color: color-mix(in srgb, var(--ink) 25%, transparent); }

.pastilles { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.pastille {
  padding: 6px 14px; border-radius: var(--radius-pill); border: 1px dashed var(--line-strong); background: transparent;
  color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); cursor: pointer;
  transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.pastille:hover { border-style: solid; background: var(--surface-raised); }
.pastille:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.tuiles { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(min(100%, 11rem), 1fr)); }
.tuile {
  position: relative; display: grid; gap: 4px; justify-items: start; padding: var(--space-4); cursor: pointer;
  border: 2px solid var(--line); border-radius: var(--radius-lg); background: var(--surface);
  animation: arrive 360ms var(--ease-out) both; animation-delay: calc(var(--i) * 40ms);
  transition: transform var(--motion-fast) var(--ease-pop), border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.tuile:hover { transform: translateY(-3px) rotate(-1deg); border-color: var(--line-strong); }
.tuile input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.tuile:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.tuile--choisie { border-color: var(--cantaloupe); box-shadow: 0 0 0 4px color-mix(in srgb, var(--cantaloupe) 25%, transparent); transform: translateY(-3px) scale(1.02); }
.tuile__anneau { position: relative; width: 56px; height: 56px; margin-bottom: var(--space-2); }
.tuile__anneau i, .succes__anneau i {
  position: absolute; width: 12px; height: 12px; border-radius: 50%; transform: translate(-50%, -50%);
  background: color-mix(in srgb, var(--ink) 18%, transparent);
}
.tuile--choisie .tuile__anneau i { background: var(--cantaloupe); }
.tuile__nom { font-weight: 700; }
.tuile__exemples { color: var(--ink-muted); font-size: 0.8125rem; }

.promesses { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); max-width: 56ch; }
.promesses li { display: flex; gap: var(--space-3); align-items: baseline; }
.promesses span {
  flex-shrink: 0; display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%;
  background: var(--action-soft); color: var(--action-ink); font-weight: 800; font-size: 0.875rem;
}
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

.commandes { display: flex; align-items: center; gap: var(--space-3); padding-top: var(--space-6); }
.commandes__aide { margin-inline-start: auto; color: var(--ink-muted); font-size: 0.8125rem; }
@media (max-width: 640px) { .commandes__aide { display: none; } .commandes > :last-child { margin-inline-start: auto; } }

.vivant { position: sticky; top: calc(64px + var(--space-6)); display: grid; gap: var(--space-4); }
.vivant__carte { display: grid; gap: var(--space-3); justify-items: center; padding: var(--space-6) var(--space-4); text-align: center; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); }
.vivant__carte p { margin: 0; }
.vivant__anneau { position: relative; width: 100%; aspect-ratio: 1; max-width: 15rem; }
.vivant__anneau::before { content: ''; position: absolute; inset: 12%; border-radius: 50%; border: 2px dashed var(--line-strong); }
.vivant__place {
  position: absolute; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; transform: translate(-50%, -50%);
  border: 2px dashed var(--line-strong); background: var(--surface);
  animation: place 360ms var(--ease-pop) both; animation-delay: calc(var(--i) * 50ms);
}
.vivant__place--moi { border: 3px solid var(--cantaloupe); }
.vivant__nom { position: absolute; inset: 30%; display: grid; place-items: center; font-size: 1rem; line-height: 1.2; overflow-wrap: anywhere; }
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

.succes { justify-items: center; text-align: center; align-content: center; min-height: 100%; position: relative; }
.succes h1 { max-width: 24ch; }
.succes__anneau { position: relative; width: 120px; height: 120px; animation: saut 700ms var(--ease-pop) both; }
.succes__anneau i { width: 18px; height: 18px; }
.succes__anneau i.pris { background: var(--cantaloupe); }
.succes__eclats { position: absolute; inset: 0; pointer-events: none; }
.succes__eclats span {
  position: absolute; left: 50%; top: 28%; width: 10px; height: 10px; border-radius: 3px; background: var(--couleur);
  animation: eclat 1100ms var(--ease-out) both; animation-delay: 120ms; --angle: calc(var(--i) * 30deg);
}

.glisse-avant-enter-active, .glisse-avant-leave-active, .glisse-arriere-enter-active, .glisse-arriere-leave-active {
  transition: opacity 240ms var(--ease-out), transform 300ms var(--ease-out);
}
.glisse-avant-enter-from { opacity: 0; transform: translateX(48px); }
.glisse-avant-leave-to { opacity: 0; transform: translateX(-48px); }
.glisse-arriere-enter-from { opacity: 0; transform: translateX(-48px); }
.glisse-arriere-leave-to { opacity: 0; transform: translateX(48px); }
.apparait-enter-active { transition: opacity 320ms var(--ease-out), transform 420ms var(--ease-pop); }
.apparait-leave-active { transition: opacity 160ms var(--ease-out); }
.apparait-enter-from { opacity: 0; transform: scale(0.96); }
.apparait-leave-to { opacity: 0; }

@keyframes arrive { from { opacity: 0; transform: translateY(10px); } }
@keyframes place { from { opacity: 0; transform: translate(-50%, -50%) scale(0.5); } }
@keyframes saut { 0% { transform: scale(0.4) rotate(-20deg); opacity: 0; } 60% { transform: scale(1.12) rotate(4deg); opacity: 1; } 100% { transform: scale(1) rotate(0); } }
@keyframes eclat {
  from { transform: translate(-50%, -50%) rotate(var(--angle)) translateY(0) scale(1); opacity: 1; }
  to { transform: translate(-50%, -50%) rotate(var(--angle)) translateY(-140px) scale(0.4); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .progression__barre, .tuile, .vivant__place, .succes__anneau { transition: none; animation: none; }
  .glisse-avant-enter-active, .glisse-avant-leave-active, .glisse-arriere-enter-active, .glisse-arriere-leave-active,
  .apparait-enter-active, .apparait-leave-active { transition: none; }
  .glisse-avant-enter-from, .glisse-avant-leave-to, .glisse-arriere-enter-from, .glisse-arriere-leave-to, .apparait-enter-from { transform: none; }
  .succes__eclats { display: none; }
}
.profils { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-2); grid-template-columns: repeat(auto-fill, minmax(min(100%, 13rem), 1fr)); }
.profil {
  position: relative; display: grid; gap: 2px; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md);
  border: 1px solid var(--line); background: var(--surface); cursor: pointer;
  transition: border-color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.profil input { position: absolute; opacity: 0; pointer-events: none; }
.profil:hover { border-color: var(--line-strong); }
.profil:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.profil--choisi { border-color: var(--action); background: var(--action-soft); }
.profil--choisi .profil__nom::before { content: '✓ '; color: var(--action-ink); }
.profil--plein { opacity: 0.5; cursor: not-allowed; }
.profil__nom { font-weight: 700; }
.profil__aide { color: var(--ink-muted); font-size: 0.8125rem; }
.vivant__profils { list-style: none; margin: var(--space-2) 0 0; padding: 0; display: flex; flex-wrap: wrap; justify-content: center; gap: 6px; }
.vivant__profils li { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; }
</style>
