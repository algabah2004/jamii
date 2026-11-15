<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JBouton } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import BanniereCommunaute from './BanniereCommunaute.vue'
import TextureCommunaute from './TextureCommunaute.vue'
import { monogramme } from './communautes'
import { PAYS_ANTENNES, THEMES, etiquettesDe, teinteDe, type Theme } from './identite'

type Communaute = components['schemas']['Communaute']

/*
 * Créer une communauté, une question à la fois (contracts/communautes.yaml v0.4) : le nom, le thème, à quoi elle sert,
 * puis l'aperçu. Les étapes glissent l'une après l'autre (rien si le mouvement est réduit), Entrée fait avancer, et
 * l'aperçu suit chaque frappe. Une fois créée, on la salue, puis on entre dans son # général.
 */
const { t, locale } = useI18n()
const router = useRouter()

const ETAPES = ['nom', 'theme', 'description', 'apercu'] as const
type Etape = (typeof ETAPES)[number]
const etape = ref<Etape>('nom')
const sens = ref<'avant' | 'arriere'>('avant')
const rang = computed(() => ETAPES.indexOf(etape.value))

const nom = ref('')
const theme = ref<Theme | null>(null)
const pays = ref<string>('SN')
const description = ref('')

const nomPropre = computed(() => nom.value.trim())
const adresse = computed(() =>
  nomPropre.value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50))
const valides: Record<Etape, () => boolean> = {
  nom: () => nomPropre.value.length >= 3 && adresse.value.length > 0,
  theme: () => theme.value !== null,
  description: () => description.value.trim().length >= 10,
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
/**
 * À chaque étape, le curseur va dans son champ (ou sur sa question) : on tape sans toucher la souris. On attend la fin
 * de l'animation d'entrée (`after-enter`) : avant, la nouvelle étape n'existe pas encore.
 */
function focaliser() {
  champ.value?.focus({ preventScroll: true })
}
onMounted(() => void nextTick(focaliser))

let avance: ReturnType<typeof setTimeout> | undefined
/** Choisir un thème fait avancer tout seul, sauf une antenne, qui demande encore son pays. */
function choisirTheme(th: Theme) {
  theme.value = th
  clearTimeout(avance)
  if (th !== 'antenne') avance = setTimeout(() => etape.value === 'theme' && suivant(), 320)
}
onBeforeUnmount(() => {
  clearTimeout(avance)
  clearTimeout(entree)
})

/** Des débuts de phrase pour ne pas rester devant une page blanche. */
const DEBUTS = ['debut1', 'debut2', 'debut3'] as const
function commencer(cle: (typeof DEBUTS)[number]) {
  description.value = t(`communautes.nouvelle.debuts.${cle}`)
  void nextTick(() => {
    const zone = champ.value as HTMLTextAreaElement | null
    zone?.focus()
    zone?.setSelectionRange(zone.value.length, zone.value.length)
  })
}
const INSPIRATIONS = ['Devs du Sahel', 'Mobile Money Builders', 'Code & Café Bamako', 'Open Data Afrique'] as const

const apercu = computed<Communaute>(() => ({
  id: 'apercu', slug: adresse.value || 'nouvelle',
  nom: nomPropre.value || t('communautes.nouvelle.nomExemple'),
  description: description.value.trim() || t('communautes.nouvelle.descriptionExemple'),
  theme: theme.value ?? 'autre', pays: theme.value === 'antenne' ? pays.value : null,
  nombreMembres: 1, questionsOuvertes: 0, membre: true, moderateur: true,
}))
const teinte = computed(() => teinteDe(apercu.value.slug))

const envoi = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const creee = ref<Communaute | null>(null)
let entree: ReturnType<typeof setTimeout> | undefined
async function creer() {
  if (envoi.value === 'envoi' || !theme.value) return
  envoi.value = 'envoi'
  try {
    const { data, response } = await api.POST('/api/communautes', {
      body: {
        nom: nomPropre.value, description: description.value.trim(), theme: theme.value,
        ...(theme.value === 'antenne' ? { pays: pays.value } : {}),
      },
    })
    if (!data) return void (envoi.value = response.status === 429 ? 'trop' : 'erreur')
    envoi.value = 'repos'
    creee.value = data
    entree = setTimeout(entrer, 2200)
  } catch {
    envoi.value = 'erreur'
  }
}
function entrer() {
  clearTimeout(entree)
  if (creee.value) void router.push({ path: `/communautes/${creee.value.slug}`, query: { salon: 'general' } })
}

/** Entrée avance ; dans la description, Maj + Entrée passe à la ligne. */
function touche(e: KeyboardEvent) {
  if (e.key !== 'Enter' || e.isComposing) return
  if (etape.value === 'description' && e.shiftKey) return
  e.preventDefault()
  suivant()
}
</script>

<template>
  <section class="assistant" aria-labelledby="titre-etape" @keydown="touche">
    <header class="assistant__haut">
      <RouterLink to="/communautes" class="retour">{{ t('communautes.retour') }}</RouterLink>
      <p v-if="!creee" class="assistant__rang" aria-live="polite">{{ t('communautes.nouvelle.etape', { n: rang + 1, total: ETAPES.length }) }}</p>
    </header>
    <div v-if="!creee" class="progression" role="progressbar" :aria-valuenow="rang + 1" aria-valuemin="1" :aria-valuemax="ETAPES.length" :aria-label="t('communautes.nouvelle.titre')">
      <span class="progression__barre" :style="{ width: `${((rang + 1) / ETAPES.length) * 100}%` }" />
    </div>

    <div class="assistant__scene" :class="{ 'assistant__scene--fin': creee }">
      <!-- La carte de l'étape en cours : une seule question, elle glisse à chaque changement. -->
      <div class="carte">
        <Transition :name="creee ? 'apparait' : `glisse-${sens}`" mode="out-in" @after-enter="focaliser">
          <!-- Créée : on la salue. -->
          <div v-if="creee" key="succes" class="etape succes" data-test="succes">
            <div class="succes__eclats" aria-hidden="true">
              <span v-for="i in 12" :key="i" :style="{ '--i': i, '--couleur': i % 3 === 0 ? 'var(--cantaloupe)' : i % 3 === 1 ? 'var(--electric-blue)' : teinte.fond }" />
            </div>
            <span class="succes__monogramme" :style="{ background: teinte.fond, color: teinte.encre }" aria-hidden="true">{{ monogramme(creee.nom) }}</span>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('communautes.nouvelle.succes', { nom: creee.nom }) }}</h1>
            <p class="etape__aide">{{ t('communautes.nouvelle.succesAide') }}</p>
            <JBouton data-test="entrer" @click="entrer">{{ t('communautes.nouvelle.entrer') }} →</JBouton>
          </div>

          <div v-else-if="etape === 'nom'" key="nom" class="etape">
            <p class="etape__numero" aria-hidden="true">01</p>
            <h1 id="titre-etape">{{ t('communautes.nouvelle.q.nom') }}</h1>
            <p class="etape__aide">{{ t('communautes.nouvelle.q.nomAide') }}</p>
            <label class="visuellement-cache" for="champ-nom">{{ t('communautes.nouvelle.nom') }}</label>
            <input
              id="champ-nom"
              ref="champ"
              v-model="nom"
              class="grand-champ"
              :maxlength="80"
              autocomplete="off"
              :placeholder="t('communautes.nouvelle.nomExemple')"
              data-test="champ-nom"
            />
            <p class="adresse" :class="{ 'adresse--vide': !adresse }">
              {{ t('communautes.nouvelle.adresse') }} <code>jamii/communautes/{{ adresse || '…' }}</code>
            </p>
            <div class="pastilles">
              <span class="texte-discret">{{ t('communautes.nouvelle.inspirations') }}</span>
              <button v-for="i in INSPIRATIONS" :key="i" type="button" class="pastille" @click="nom = i">{{ i }}</button>
            </div>
          </div>

          <div v-else-if="etape === 'theme'" key="theme" class="etape">
            <p class="etape__numero" aria-hidden="true">02</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('communautes.nouvelle.q.theme') }}</h1>
            <p class="etape__aide">{{ t('communautes.nouvelle.q.themeAide') }}</p>
            <fieldset class="tuiles">
              <legend class="visuellement-cache">{{ t('communautes.nouvelle.theme') }}</legend>
              <label
                v-for="(th, i) in THEMES"
                :key="th"
                class="tuile"
                :class="{ 'tuile--choisie': theme === th }"
                :style="{ '--i': i, '--fond': teinteDe(th).fond, '--encre': teinteDe(th).encre }"
              >
                <input type="radio" name="theme" :value="th" :checked="theme === th" :data-test="`theme-${th}`" @change="choisirTheme(th)" />
                <span class="tuile__pastille" aria-hidden="true">{{ t(`communautes.themes.${th}`).slice(0, 2) }}</span>
                <span class="tuile__nom">{{ t(`communautes.themes.${th}`) }}</span>
                <span class="tuile__exemples">{{ etiquettesDe(th).slice(0, 2).join(' · ') }}</span>
              </label>
            </fieldset>
            <Transition name="apparait">
              <label v-if="theme === 'antenne'" class="pays">
                <span>{{ t('communautes.nouvelle.pays') }}</span>
                <select v-model="pays" data-test="pays">
                  <option v-for="p in PAYS_ANTENNES" :key="p" :value="p">{{ nomPays(p, locale) }}</option>
                </select>
              </label>
            </Transition>
          </div>

          <div v-else-if="etape === 'description'" key="description" class="etape">
            <p class="etape__numero" aria-hidden="true">03</p>
            <h1 id="titre-etape">{{ t('communautes.nouvelle.q.description', { nom: nomPropre }) }}</h1>
            <p class="etape__aide">{{ t('communautes.nouvelle.q.descriptionAide') }}</p>
            <label class="visuellement-cache" for="champ-description">{{ t('communautes.nouvelle.description') }}</label>
            <textarea
              id="champ-description"
              ref="champ"
              v-model="description"
              class="grand-champ grand-champ--texte"
              rows="3"
              :maxlength="500"
              data-test="champ-description"
            />
            <p class="compteur" :class="{ 'compteur--ok': description.trim().length >= 10 }">{{ description.length }} / 500</p>
            <div class="pastilles">
              <span class="texte-discret">{{ t('communautes.nouvelle.commencer') }}</span>
              <button v-for="d in DEBUTS" :key="d" type="button" class="pastille" @click="commencer(d)">{{ t(`communautes.nouvelle.debuts.${d}`).split(' ').slice(0, 4).join(' ') }}…</button>
            </div>
          </div>

          <div v-else key="apercu" class="etape">
            <p class="etape__numero" aria-hidden="true">04</p>
            <h1 id="titre-etape" ref="champ" tabindex="-1">{{ t('communautes.nouvelle.q.apercu') }}</h1>
            <BanniereCommunaute :communaute="apercu" class="etape__banniere" />
            <ul class="promesses">
              <li><span aria-hidden="true">✓</span> {{ t('communautes.nouvelle.moderateur') }}</li>
              <li><span aria-hidden="true">✓</span> {{ t('communautes.nouvelle.salons') }}</li>
            </ul>
            <p v-if="envoi === 'trop'" role="alert" class="erreur">{{ t('communautes.nouvelle.trop') }}</p>
            <p v-else-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
          </div>
        </Transition>

        <footer v-if="!creee" class="commandes">
          <JBouton v-if="rang > 0" variante="discret" data-test="precedent" @click="precedent">← {{ t('communautes.nouvelle.retour') }}</JBouton>
          <span class="commandes__aide" aria-hidden="true">{{ t('communautes.nouvelle.entree') }}</span>
          <JBouton :desactive="!etapeValide" :occupe="envoi === 'envoi'" data-test="suivant" @click="suivant">
            {{ etape === 'apercu' ? t('communautes.nouvelle.creer') : t('communautes.nouvelle.suivant') }} →
          </JBouton>
        </footer>
      </div>

      <!-- L'aperçu vivant : il suit chaque frappe, jusqu'à la bannière complète de la dernière étape. -->
      <aside v-if="!creee && etape !== 'apercu'" class="vivant" :aria-label="t('communautes.nouvelle.apercu')">
        <div class="vivant__carte" :style="{ '--fond': teinte.fond, '--accent': teinte.accent, '--encre': teinte.encre }">
          <div class="vivant__bandeau">
            <TextureCommunaute :key="apercu.slug" :slug="apercu.slug" />
            <span v-if="theme" class="vivant__etiquette">{{ etiquettesDe(theme)[0] }}</span>
          </div>
          <div class="vivant__corps">
            <span class="vivant__monogramme">{{ monogramme(apercu.nom) }}</span>
            <strong>{{ apercu.nom }}</strong>
            <span class="texte-discret">{{ theme ? t(`communautes.themes.${theme}`) : t('communautes.nouvelle.themeAVenir') }}</span>
            <p>{{ apercu.description }}</p>
          </div>
        </div>
        <ol class="vivant__etapes">
          <li v-for="(e, i) in ETAPES" :key="e" :class="{ fait: i < rang, actuelle: i === rang }">
            <button type="button" :disabled="i > rang" @click="aller(e)">{{ t(`communautes.nouvelle.etapes.${e}`) }}</button>
          </li>
        </ol>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.assistant { display: grid; gap: var(--space-4); max-width: 72rem; margin: var(--space-4) auto 0; }
.assistant__haut { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.retour { font-size: var(--type-body-sm-size); }
.assistant__rang { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; font-variant-numeric: tabular-nums; }

.progression { height: 6px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 8%, transparent); overflow: hidden; }
.progression__barre {
  display: block; height: 100%; border-radius: inherit;
  background: linear-gradient(90deg, var(--electric-blue), var(--cantaloupe));
  transition: width 480ms var(--ease-out);
}

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
.etape h1 { margin: 0; font-size: clamp(1.75rem, 3.4vw, 2.75rem); line-height: 1.08; letter-spacing: -0.03em; max-width: 22ch; outline: none; }
.etape__aide { margin: 0; color: var(--ink-muted); max-width: 52ch; }

.grand-champ {
  width: 100%; padding: var(--space-3) 0; border: 0; border-bottom: 3px solid var(--line-strong); border-radius: 0;
  background: transparent; color: var(--ink); font-family: var(--font-display); font-size: clamp(1.5rem, 3vw, 2.25rem); font-weight: 700; outline: none;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.grand-champ:focus { border-bottom-color: var(--electric-blue); }
.grand-champ::placeholder { color: color-mix(in srgb, var(--ink) 25%, transparent); }
.grand-champ--texte { font-family: var(--font-text); font-size: 1.25rem; font-weight: 500; line-height: 1.5; resize: none; }
.adresse { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.adresse code { padding: 2px 8px; border-radius: 6px; background: var(--surface-raised); color: var(--ink); }
.adresse--vide code { color: var(--ink-muted); }
.compteur { margin: 0; justify-self: end; color: var(--ink-muted); font-size: 0.8125rem; font-variant-numeric: tabular-nums; }
.compteur--ok { color: #1a7f45; }

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
  position: relative; display: grid; gap: 4px; padding: var(--space-4); cursor: pointer;
  border: 2px solid var(--line); border-radius: var(--radius-lg); background: var(--surface);
  animation: arrive 360ms var(--ease-out) both; animation-delay: calc(var(--i) * 28ms);
  transition: transform var(--motion-fast) var(--ease-pop), border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.tuile:hover { transform: translateY(-3px) rotate(-1deg); border-color: var(--line-strong); }
.tuile input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.tuile:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.tuile--choisie { border-color: var(--fond); box-shadow: 0 0 0 4px color-mix(in srgb, var(--fond) 25%, transparent); transform: translateY(-3px) scale(1.02); }
.tuile__pastille {
  display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; margin-bottom: var(--space-1);
  background: var(--fond); color: var(--encre); font-family: var(--font-display); font-weight: 700; transform: rotate(-6deg);
}
.tuile__nom { font-weight: 700; }
.tuile__exemples { color: var(--ink-muted); font-size: 0.8125rem; }
.pays { display: grid; gap: var(--space-2); max-width: 20rem; font-weight: 600; }
.pays select { min-height: 48px; padding: 0 var(--space-3); border-radius: var(--radius-md); border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit; }

.etape__banniere { margin-top: var(--space-2); }
.promesses { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.promesses li { display: flex; gap: var(--space-2); align-items: baseline; }
.promesses span { color: #1a7f45; font-weight: 800; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

.commandes { display: flex; align-items: center; gap: var(--space-3); padding-top: var(--space-6); }
.commandes__aide { margin-inline-start: auto; color: var(--ink-muted); font-size: 0.8125rem; }
@media (max-width: 640px) { .commandes__aide { display: none; } .commandes > :last-child { margin-inline-start: auto; } }

.vivant { position: sticky; top: calc(64px + var(--space-6)); display: grid; gap: var(--space-4); }
.vivant__carte { overflow: hidden; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); }
.vivant__bandeau { position: relative; overflow: hidden; height: 84px; background: radial-gradient(circle at 80% 130%, color-mix(in srgb, var(--accent) 80%, transparent), transparent 60%), var(--fond); transition: background 400ms var(--ease-out); }
.vivant__etiquette { position: absolute; top: 14px; right: 14px; padding: 3px 10px; border-radius: 10px; transform: rotate(-4deg); background: #fff; color: #02070A; font-family: var(--font-display); font-weight: 700; font-size: 0.75rem; }
.vivant__corps { display: grid; gap: 4px; padding: 0 var(--space-4) var(--space-4); }
.vivant__monogramme {
  margin-top: -26px; display: grid; place-items: center; width: 52px; height: 52px; border-radius: 14px; border: 3px solid var(--surface);
  background: var(--fond); color: var(--encre); font-family: var(--font-display); font-weight: 700; transform: rotate(-4deg); transition: background 400ms var(--ease-out);
}
.vivant__corps strong { font-size: 1.125rem; overflow-wrap: anywhere; }
.vivant__corps p { margin: var(--space-1) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
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
.succes__monogramme {
  display: grid; place-items: center; width: 96px; height: 96px; border-radius: 28px;
  font-family: var(--font-display); font-weight: 700; font-size: 2rem;
  animation: saut 700ms var(--ease-pop) both;
}
.succes__eclats { position: absolute; inset: 0; pointer-events: none; }
.succes__eclats span {
  position: absolute; left: 50%; top: 28%; width: 10px; height: 10px; border-radius: 3px; background: var(--couleur);
  animation: eclat 1100ms var(--ease-out) both; animation-delay: 120ms;
  --angle: calc(var(--i) * 30deg);
}

/* Les étapes glissent : vers la gauche en avançant, vers la droite en revenant. */
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
@keyframes saut { 0% { transform: scale(0.4) rotate(-20deg); opacity: 0; } 60% { transform: scale(1.12) rotate(4deg); opacity: 1; } 100% { transform: scale(1) rotate(-4deg); } }
@keyframes eclat {
  from { transform: translate(-50%, -50%) rotate(var(--angle)) translateY(0) scale(1); opacity: 1; }
  to { transform: translate(-50%, -50%) rotate(var(--angle)) translateY(-140px) scale(0.4); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .progression__barre, .tuile, .vivant__bandeau, .vivant__monogramme { transition: none; animation: none; }
  .glisse-avant-enter-active, .glisse-avant-leave-active, .glisse-arriere-enter-active, .glisse-arriere-leave-active,
  .apparait-enter-active, .apparait-leave-active { transition: none; }
  .glisse-avant-enter-from, .glisse-avant-leave-to, .glisse-arriere-enter-from, .glisse-arriere-leave-to, .apparait-enter-from { transform: none; }
  .succes__monogramme, .succes__eclats span { animation: none; }
  .succes__eclats { display: none; }
}
</style>
