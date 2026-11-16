<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ajusterZone } from '../../design-system/zonesDeTexte'
import { ACCEPTES, envoyerFichier, PIECES_MAX, tailleLisible, type PieceJointe } from './fichiers'
import { appliquerFormat, type Format } from './miseEnForme'
import SelecteurEmoji from './SelecteurEmoji.vue'
import { convertirALaFin, convertirEnTapant, type Conversion } from './emojis'

/*
 * La barre d'écriture de Jamii, la même partout où l'on écrit à plusieurs (salons des cercles et des communautés, fil
 * des communautés) :
 * - la mise en forme (gras, italique, barré, code, bloc de code, lien, liste, citation), par boutons ou raccourcis
 *   (Ctrl/⌘ + B, I, K, E) ; le texte reste un petit Markdown sûr (miseEnForme.ts), jamais du HTML ;
 * - les pièces jointes : bouton, glisser-déposer, ou coller une image ; chacune part tout de suite au serveur, avec un
 *   aperçu et une croix pour la retirer ; 6 au plus ;
 * - les emojis : un bouton 😊 ouvre le choix, et « :) » devient « 🙂 » dès qu'on tape un espace (Retour arrière juste
 *   après annule la conversion) ;
 * - Entrée envoie, Maj + Entrée va à la ligne.
 * La zone grandit avec le texte (zonesDeTexte.ts) : pas de barre de défilement avant sa hauteur maximale.
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    destinataireId?: string
    pieces?: PieceJointe[]
    placeholder: string
    libelleEnvoyer: string
    envoi?: boolean
    erreur?: string | null
    accent?: 'normal' | 'alerte'
    avecFichiers?: boolean
    idZone: string
    max?: number
    lignes?: number
    entreeEnvoie?: boolean
    testZone?: string
    testEnvoyer?: string
    /** Pendant un enregistrement vocal, l'enregistreur (slot `action`) prend la ligne : il a son propre bouton d'envoi, et la mise en forme ne sert à rien. */
    masquerEnvoi?: boolean
  }>(),
  { pieces: () => [], envoi: false, erreur: null, accent: 'normal', avecFichiers: true, max: 2000, lignes: 1, entreeEnvoie: true, testZone: undefined, testEnvoyer: 'envoyer', masquerEnvoi: false },
)
const emit = defineEmits<{ 'update:modelValue': [texte: string]; 'update:pieces': [pieces: PieceJointe[]]; envoyer: [] }>()
const { t, locale } = useI18n()

const zone = ref<HTMLTextAreaElement | null>(null)
const choix = ref<HTMLInputElement | null>(null)
const enCours = ref<{ cle: string; nom: string }[]>([])
const probleme = ref<string | null>(null)
const survol = ref(false)
const emojis = ref(false)

let actif = true
onBeforeUnmount(() => { actif = false })

const pret = computed(() => !props.envoi && !enCours.value.length && (!!props.modelValue.trim() || props.pieces.length > 0))


// ---------------------------------------------------------------- écrire et mettre en forme

const BOUTONS: { format: Format; icone: string; raccourci?: string }[] = [
  { format: 'gras', icone: 'M6 4h5a3 3 0 0 1 0 6H6zM6 10h6a3 3 0 0 1 0 6H6z', raccourci: 'B' },
  { format: 'italique', icone: 'M12 4h-4M12 16H8M11 4 9 16', raccourci: 'I' },
  { format: 'barre', icone: 'M4 10h12M13 6.5C12.4 5 11.2 4 10 4 8.2 4 7 5 7 6.5M7 13.5C7.6 15 8.8 16 10 16c1.8 0 3-1 3-2.5' },
  { format: 'code', icone: 'M7.5 6 3.5 10l4 4M12.5 6l4 4-4 4', raccourci: 'E' },
  { format: 'bloc', icone: 'M7 4.5C5.5 4.5 5 5.3 5 6.5v2c0 .8-.5 1.5-1.5 1.5.9 0 1.5.7 1.5 1.5v2c0 1.2.5 2 2 2M13 4.5c1.5 0 2 .8 2 2v2c0 .8.5 1.5 1.5 1.5-1 0-1.5.7-1.5 1.5v2c0 1.2-.5 2-2 2' },
  { format: 'lien', icone: 'M8.5 11.5a3 3 0 0 0 4.2 0l2.6-2.6a3 3 0 0 0-4.2-4.2l-.9.9M11.5 8.5a3 3 0 0 0-4.2 0l-2.6 2.6a3 3 0 0 0 4.2 4.2l.9-.9', raccourci: 'K' },
  { format: 'liste', icone: 'M8 6h8M8 10h8M8 14h8M4.5 6h0M4.5 10h0M4.5 14h0' },
  { format: 'citation', icone: 'M5 6v8M8.5 7.5h7M8.5 12.5h5' },
]

function formater(format: Format) {
  const z = zone.value
  if (!z) return
  const r = appliquerFormat(props.modelValue, z.selectionStart, z.selectionEnd, format, t(`texteRiche.exemples.${format}`))
  emit('update:modelValue', r.texte)
  void nextTick(() => {
    z.focus()
    z.setSelectionRange(r.debut, r.fin)
    ajusterZone(z)
  })
}

// ---------------------------------------------------------------- les emojis

/** La dernière conversion d'un smiley : Retour arrière juste après la défait (« :) » revient). */
let derniere: Conversion | null = null

function saisir(e: Event) {
  const z = e.target as HTMLTextAreaElement
  derniere = null
  const conversion = (e as InputEvent).inputType?.startsWith('insert') ? convertirEnTapant(z.value, z.selectionStart) : null
  if (!conversion) return emit('update:modelValue', z.value)
  derniere = conversion
  emit('update:modelValue', conversion.texte)
  void nextTick(() => { z.value = conversion.texte; z.setSelectionRange(conversion.curseur, conversion.curseur) })
}

function defaireConversion(e: KeyboardEvent): boolean {
  const z = zone.value
  const d = derniere
  derniere = null
  if (e.key !== 'Backspace' || !z || !d || z.selectionStart !== z.selectionEnd || z.selectionStart !== d.curseur) return false
  if (props.modelValue.slice(d.debut, d.debut + d.apres.length) !== d.apres) return false
  e.preventDefault()
  // On remet le smiley tapé, sans l'espace qui l'avait déclenché.
  const texte = props.modelValue.slice(0, d.debut) + d.avant + props.modelValue.slice(d.curseur)
  const curseur = d.debut + d.avant.length
  emit('update:modelValue', texte)
  void nextTick(() => { z.setSelectionRange(curseur, curseur); ajusterZone(z) })
  return true
}

function insererEmoji(emoji: string) {
  const z = zone.value
  const debut = z?.selectionStart ?? props.modelValue.length
  const fin = z?.selectionEnd ?? props.modelValue.length
  const texte = props.modelValue.slice(0, debut) + emoji + props.modelValue.slice(fin)
  if (texte.length > props.max) return
  emit('update:modelValue', texte)
  void nextTick(() => {
    if (!z) return
    z.focus()
    z.setSelectionRange(debut + emoji.length, debut + emoji.length)
    ajusterZone(z)
  })
}

function fermerEmojis() {
  emojis.value = false
  zone.value?.focus()
}

function touche(e: KeyboardEvent) {
  if (defaireConversion(e)) return
  if ((e.metaKey || e.ctrlKey) && !e.altKey) {
    const b = BOUTONS.find((x) => x.raccourci?.toLowerCase() === e.key.toLowerCase())
    if (b) {
      e.preventDefault()
      return formater(b.format)
    }
  }
  // Entrée envoie (salons) ; dans un fil, on écrit plus long : Ctrl/⌘ + Entrée publie.
  const envoie = props.entreeEnvoie ? !e.shiftKey && !e.metaKey && !e.ctrlKey : e.metaKey || e.ctrlKey
  if (e.key === 'Enter' && envoie && !e.isComposing) {
    e.preventDefault()
    soumettre()
  }
}

function soumettre() {
  if (!pret.value) return
  // « Merci :) » envoyé tel quel : le smiley final est converti aussi.
  const converti = convertirALaFin(props.modelValue)
  if (converti !== props.modelValue) emit('update:modelValue', converti)
  emojis.value = false
  emit('envoyer')
}

/** Après un envoi, le parent vide le texte : la zone reprend sa hauteur d'une ligne. */
function rafraichir() {
  void nextTick(() => zone.value && ajusterZone(zone.value))
}
defineExpose({ focus: () => zone.value?.focus(), rafraichir })

// ---------------------------------------------------------------- les pièces jointes

async function joindre(fichiers: FileList | File[] | null) {
  if (!fichiers) return
  probleme.value = null
  const liste = Array.from(fichiers)
  const place = PIECES_MAX - props.pieces.length - enCours.value.length
  if (liste.length > place) probleme.value = t('fichiers.erreurs.tropDePieces', { n: PIECES_MAX })
  let pieces = [...props.pieces]
  await Promise.all(
    liste.slice(0, Math.max(0, place)).map(async (f) => {
      const cle = `${f.name}-${f.size}-${Math.random()}`
      enCours.value = [...enCours.value, { cle, nom: f.name }]
      const r = await envoyerFichier(f, props.destinataireId)
      if (!actif) return
      enCours.value = enCours.value.filter((x) => x.cle !== cle)
      if (r.ok) {
        pieces = [...pieces, r.piece]
        emit('update:pieces', pieces)
      } else probleme.value = t(`fichiers.erreurs.${r.code}`, { nom: f.name })
    }),
  )
  if (choix.value) choix.value.value = ''
}

function retirer(id: string) {
  emit('update:pieces', props.pieces.filter((p) => p.id !== id))
}

function deposer(e: DragEvent) {
  survol.value = false
  if (!props.avecFichiers || !e.dataTransfer?.files.length) return
  e.preventDefault()
  void joindre(e.dataTransfer.files)
}

/** Coller une capture d'écran l'ajoute en pièce jointe. */
function coller(e: ClipboardEvent) {
  if (!props.avecFichiers) return
  const fichiers = Array.from(e.clipboardData?.files ?? [])
  if (fichiers.length) {
    e.preventDefault()
    void joindre(fichiers)
  }
}
</script>

<template>
  <div
    class="composeur"
    :class="{ 'composeur--alerte': accent === 'alerte', 'composeur--survol': survol }"
    @dragover.prevent="avecFichiers && (survol = true)"
    @dragleave="survol = false"
    @drop="deposer"
  >
    <ul v-if="pieces.length || enCours.length" class="apercus" :aria-label="t('fichiers.joints')">
      <li v-for="p in pieces" :key="p.id" class="apercu" data-test="piece-a-envoyer">
        <img v-if="p.sorte === 'image'" :src="p.url" alt="" class="apercu__image" />
        <span v-else class="apercu__icone" aria-hidden="true">{{ p.sorte === 'video' ? '▶' : p.sorte === 'audio' ? '♪' : (p.nom.split('.').pop() ?? '').slice(0, 4).toUpperCase() }}</span>
        <span class="apercu__texte"><span class="apercu__nom">{{ p.nom }}</span><span class="apercu__taille">{{ tailleLisible(p.taille, locale) }}</span></span>
        <button type="button" class="apercu__retirer" :aria-label="t('fichiers.retirer', { nom: p.nom })" @click="retirer(p.id)">
          <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        </button>
      </li>
      <li v-for="e in enCours" :key="e.cle" class="apercu apercu--envoi" role="status">
        <span class="apercu__icone apercu__icone--envoi" aria-hidden="true" />
        <span class="apercu__texte"><span class="apercu__nom">{{ e.nom }}</span><span class="apercu__taille">{{ t('fichiers.envoi') }}</span></span>
      </li>
    </ul>

    <label :for="idZone" class="visuellement-cache">{{ placeholder }}</label>
    <textarea
      :id="idZone"
      ref="zone"
      :value="modelValue"
      :rows="lignes"
      :maxlength="max"
      :placeholder="placeholder"
      :data-test="testZone"
      @input="saisir"
      @keydown="touche"
      @paste="coller"
    />

    <div class="outils">
      <div v-show="!masquerEnvoi" class="outils__format" role="toolbar" :aria-label="t('texteRiche.barre')">
        <button
          v-for="b in BOUTONS"
          :key="b.format"
          type="button"
          class="outil"
          :aria-label="t(`texteRiche.formats.${b.format}`)"
          :title="t(`texteRiche.formats.${b.format}`) + (b.raccourci ? ` (⌘/Ctrl + ${b.raccourci})` : '')"
          :data-test="`format-${b.format}`"
          @mousedown.prevent
          @click="formater(b.format)"
        >
          <svg viewBox="0 0 20 20" width="17" height="17" aria-hidden="true"><path :d="b.icone" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <span class="separateur" aria-hidden="true" />
        <span class="emojis">
          <button
            type="button"
            class="outil"
            :aria-label="t('emojis.ouvrir')"
            :title="t('emojis.ouvrir')"
            :aria-expanded="emojis"
            data-test="ouvrir-emojis"
            @mousedown.prevent
            @click="emojis = !emojis"
          >
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="1.6" /><path d="M7 11.5a3.5 3.5 0 0 0 6 0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" /><circle cx="7.6" cy="8" r="1" fill="currentColor" /><circle cx="12.4" cy="8" r="1" fill="currentColor" /></svg>
          </button>
          <SelecteurEmoji v-if="emojis" class="emojis__choix" @choisir="insererEmoji" @fermer="fermerEmojis" />
        </span>
        <template v-if="avecFichiers">
          <button type="button" class="outil" :aria-label="t('fichiers.joindre')" :title="t('fichiers.joindre')" :disabled="pieces.length + enCours.length >= 6" data-test="joindre" @click="choix?.click()">
            <svg viewBox="0 0 20 20" width="17" height="17" aria-hidden="true"><path d="M15.5 9.5 10 15a3.5 3.5 0 0 1-5-5l6-6a2.3 2.3 0 0 1 3.3 3.3l-6 6a1.2 1.2 0 0 1-1.7-1.7L12 6.2" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
          <input ref="choix" type="file" multiple :aria-label="t('fichiers.joindre')" :accept="ACCEPTES" class="visuellement-cache" tabindex="-1" data-test="choisir-fichiers" @change="joindre(($event.target as HTMLInputElement).files)" />
        </template>
        <slot name="outils" />
      </div>
      <span v-if="erreur || probleme" role="alert" class="outils__erreur">{{ erreur || probleme }}</span>
      <span v-else class="outils__aide">{{ entreeEnvoie ? t('texteRiche.aide') : t('texteRiche.aideFil') }}</span>
      <div class="envoi">
        <slot name="action" />
        <button v-show="!masquerEnvoi" type="button" class="envoyer" :class="{ 'envoyer--pret': pret }" :disabled="!pret" :aria-busy="envoi || undefined" :data-test="testEnvoyer" @click="soumettre">
          <span v-if="envoi" class="envoyer__attente" aria-hidden="true" />
          <svg v-else viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3.4 9.2 15.6 3.6c.8-.4 1.6.4 1.2 1.2l-5.6 12.2c-.4.8-1.5.7-1.7-.1L8.4 12a.8.8 0 0 0-.5-.5L3.5 10.9c-.8-.2-.9-1.3-.1-1.7ZM8.3 11.7l3.4-3.4" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <span class="envoyer__texte">{{ libelleEnvoyer }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.composeur { container-type: inline-size; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); transition: border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out); }
.composeur:focus-within { border-color: var(--ink-muted); }
.composeur--alerte, .composeur--alerte:focus-within { border-color: var(--proof); }
.composeur--survol { border-color: var(--action); box-shadow: 0 0 0 3px var(--action-soft); }

textarea {
  display: block; width: 100%; box-sizing: border-box; min-height: 48px; padding: var(--space-3) var(--space-4) var(--space-1);
  border: 0; border-radius: var(--radius-lg); background: transparent; color: var(--ink); font: inherit; line-height: 1.5; resize: none;
}
textarea:focus-visible { outline: none; }

.apercus { list-style: none; margin: 0; padding: var(--space-3) var(--space-3) 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.apercu { position: relative; display: flex; align-items: center; gap: var(--space-2); max-width: 15rem; padding: 6px var(--space-2) 6px 6px; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); }
.apercu__image { width: 40px; height: 40px; border-radius: 6px; object-fit: cover; flex: none; }
.apercu__icone { display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: 6px; background: var(--surface); color: var(--ink-muted); font-size: 0.6875rem; font-weight: 800; }
.apercu__icone--envoi { background: conic-gradient(var(--action) 0 25%, var(--surface) 0); animation: tourne 1s linear infinite; border-radius: 50%; width: 22px; height: 22px; margin: 9px; }
@keyframes tourne { to { transform: rotate(360deg); } }
.apercu__texte { display: grid; min-width: 0; }
.apercu__nom { font-size: 0.8125rem; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.apercu__taille { color: var(--ink-muted); font-size: 0.6875rem; }
.apercu__retirer { position: absolute; top: -7px; right: -7px; display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: var(--surface); color: var(--ink); cursor: pointer; }
.apercu__retirer:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }

.outils { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-1) var(--space-2) var(--space-2); }
.outils__format { display: flex; align-items: center; gap: 2px; flex-wrap: wrap; }
.outil { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--ink-muted); cursor: pointer; }
.outil:hover:not(:disabled) { background: var(--surface-raised); color: var(--ink); }
.outil:disabled { opacity: 0.4; cursor: not-allowed; }
.outil:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.emojis { position: relative; display: inline-flex; }
.emojis__choix { position: absolute; bottom: calc(100% + 8px); left: 0; z-index: 30; }
@media (max-width: 720px) { .emojis { position: static; } .emojis__choix { left: 8px; right: 8px; width: auto; } .composeur { position: relative; } }
.separateur { width: 1px; height: 18px; margin: 0 var(--space-1); background: var(--line); }
.outils__aide { flex: 1; min-width: 0; color: var(--ink-muted); font-size: 0.75rem; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.outils__erreur { flex: 1; min-width: 0; color: var(--danger); font-size: 0.8125rem; font-weight: 600; text-align: right; }
.envoi { flex: none; display: flex; align-items: center; gap: var(--space-2); margin-inline-start: auto; }
.envoyer {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 40px; padding: 0 var(--space-4) 0 14px;
  border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted);
  font: inherit; font-size: var(--type-body-sm-size); font-weight: 700; cursor: not-allowed;
  transition: background var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.envoyer svg { transition: transform var(--motion-fast) var(--ease-out); }
.envoyer--pret { border-color: var(--action); background: var(--action); color: var(--on-action); cursor: pointer; box-shadow: 0 6px 18px -8px var(--action); }
.envoyer--pret:hover { filter: brightness(1.08); }
.envoyer--pret:hover svg { transform: translate(1px, -1px); }
.envoyer--pret:active { transform: scale(0.96); }
.composeur--alerte .envoyer--pret { border-color: var(--proof); background: var(--proof); color: #02070A; box-shadow: 0 6px 18px -8px var(--proof); }
.envoyer[aria-busy='true'] { cursor: progress; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.envoyer__attente { width: 16px; height: 16px; border: 2px solid currentColor; border-right-color: transparent; border-radius: 50%; animation: tourne 0.8s linear infinite; }
/* Une zone étroite (colonne de discussion) : l'aide au clavier coupée en « En… » ne sert à rien, on la retire. */
@container (max-width: 640px) { .outils__aide { visibility: hidden; } }
/* Sur un petit écran : les outils sur une ligne, puis l'envoi à droite en dessous. */
@media (max-width: 720px) {
  .outils { flex-wrap: wrap; }
  .outils__aide { display: none; }
  .outils__format { flex: 1 1 100%; }
  .outils__erreur { flex: 1 1 100%; text-align: left; }
  .envoyer { width: 40px; padding: 0; }
  .envoyer__texte { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
}
@media (prefers-reduced-motion: reduce) { .apercu__icone--envoi, .envoyer__attente { animation: none; } .envoyer, .envoyer svg { transition: none; } }
</style>
