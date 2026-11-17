<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import { ajouterTache, changerTache, type Tache } from './cercles'
import IconeCercle from './IconeCercle.vue'
import { composerDescription, decouperDescription, etatEcheance, PRIORITES, type Priorite } from './taches'

/*
 * Une tâche, en grand, dans une vraie boîte de dialogue (`<dialog>` : le focus y reste, Échap ferme) :
 * - créer : le titre, ce qu'il faut faire, quand c'est fini, la priorité, l'échéance et qui s'en charge ;
 * - voir : la fiche complète, avec les actions (prendre, livrer) ;
 * - modifier : le même formulaire, prérempli.
 * Une tâche se comprend sans demander : c'est tout l'objet de la description en deux parties.
 */
type Personne = { id: string; nom: string; avatarUrl?: string | null }
const props = defineProps<{ projetId: string; membres: Personne[]; moiId: string | null; tache: Tache | null; ouvert: boolean }>()
const emit = defineEmits<{ ferme: []; enregistre: [tache: Tache] }>()
const { t, locale } = useI18n()

const boite = ref<HTMLDialogElement | null>(null)
const champTitre = ref<HTMLInputElement | null>(null)
const mode = ref<'voir' | 'modifier' | 'creer'>('creer')
const envoi = ref(false)
const erreur = ref<string | null>(null)

const vide = () => ({ titre: '', faire: '', fini: '', priorite: 'normale' as Priorite, echeance: '', assigneeId: '' })
const form = ref(vide())

function remplir(tache: Tache | null) {
  if (!tache) return void (form.value = vide())
  const d = decouperDescription(tache.description)
  form.value = { titre: tache.titre, faire: d.faire, fini: d.fini, priorite: tache.priorite ?? 'normale', echeance: tache.echeance ?? '', assigneeId: tache.assignee?.id ?? '' }
}

watch(
  () => props.ouvert,
  (ouvert) => {
    const d = boite.value
    if (!d) return
    if (ouvert) {
      erreur.value = null
      mode.value = props.tache ? 'voir' : 'creer'
      remplir(props.tache)
      if (typeof d.showModal === 'function' && !d.open) d.showModal()
      else d.setAttribute('open', '')
      if (mode.value === 'creer') void nextTick(() => champTitre.value?.focus())
    } else if (typeof d.close === 'function' && d.open) d.close()
    else d.removeAttribute('open')
  },
)

function fermer() {
  emit('ferme')
}
function modifier() {
  remplir(props.tache)
  mode.value = 'modifier'
  void nextTick(() => champTitre.value?.focus())
}

const valide = computed(() => form.value.titre.trim().length >= 3)

async function enregistrer() {
  if (!valide.value || envoi.value) return
  envoi.value = true
  erreur.value = null
  const description = composerDescription(form.value.faire, form.value.fini, t('cercles.tache.finiQuandStocke'))
  const details = { description, priorite: form.value.priorite, echeance: form.value.echeance || null, assigneeId: form.value.assigneeId || null }
  try {
    const r =
      mode.value === 'creer' || !props.tache
        ? await ajouterTache(props.projetId, form.value.titre.trim(), details)
        : await changerTache(props.projetId, props.tache, { titre: form.value.titre.trim(), ...details })
    if (!r.ok) {
      erreur.value = t(r.code === 'modification_concurrente' ? 'cercles.tachesTour.concurrente' : 'cercles.tachesTour.erreur')
      return
    }
    emit('enregistre', r.tache)
    if (mode.value === 'creer') fermer()
    else mode.value = 'voir'
  } finally {
    envoi.value = false
  }
}

/** Une action de la fiche : prendre, lâcher, livrer, rouvrir. */
async function agir(changement: { statut?: Tache['statut']; assigneeId?: string | null }) {
  if (!props.tache || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await changerTache(props.projetId, props.tache, changement)
    if (!r.ok) return void (erreur.value = t(r.code === 'modification_concurrente' ? 'cercles.tachesTour.concurrente' : 'cercles.tachesTour.erreur'))
    emit('enregistre', r.tache)
  } finally {
    envoi.value = false
  }
}

const description = computed(() => decouperDescription(props.tache?.description))
const aMoi = computed(() => !!props.moiId && props.tache?.assignee?.id === props.moiId)
const dateLongue = (iso: string) => new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(`${iso.slice(0, 10)}T12:00:00Z`))
const echeance = computed(() => etatEcheance(props.tache?.echeance))
</script>

<template>
  <dialog ref="boite" class="dialogue" aria-labelledby="dialogue-tache-titre" data-test="dialogue-tache" @cancel.prevent="fermer" @click.self="fermer">
    <!-- La fiche -->
    <article v-if="mode === 'voir' && tache" class="fiche" data-test="fiche-tache">
      <header class="fiche__tete">
        <div class="fiche__etiquettes">
          <span class="priorite" :class="`priorite--${tache.priorite}`"><IconeCercle nom="priorite" :taille="14" />{{ t(`cercles.tache.priorites.${tache.priorite}`) }}</span>
          <span class="statut" :class="`statut--${tache.statut}`">{{ t(`cercles.tache.statuts.${tache.statut}`) }}</span>
        </div>
        <button type="button" class="fermer" :aria-label="t('cercles.tache.fermer')" @click="fermer"><IconeCercle nom="fermer" /></button>
      </header>
      <h2 id="dialogue-tache-titre" class="fiche__titre" :class="{ 'fiche__titre--livree': tache.statut === 'livree' }">{{ tache.titre }}</h2>

      <div class="fiche__description">
        <section v-if="description.faire">
          <h3>{{ t('cercles.tache.aFaire') }}</h3>
          <p>{{ description.faire }}</p>
        </section>
        <section v-if="description.fini" class="fini">
          <h3>{{ t('cercles.tache.finiQuand') }}</h3>
          <p>{{ description.fini }}</p>
        </section>
        <p v-if="!description.faire && !description.fini" class="vide">
          {{ t('cercles.tache.sansDescription') }}
          <button type="button" class="lien-texte" @click="modifier">{{ t('cercles.tache.ajouterDescription') }}</button>
        </p>
      </div>

      <dl class="fiche__infos">
        <div>
          <dt>{{ t('cercles.tache.responsable') }}</dt>
          <dd v-if="tache.assignee" class="personne">
            <LienPersonne :id="tache.assignee.id" doublon><JAvatar :nom="tache.assignee.nom" :url="tache.assignee.avatarUrl" :taille="24" /></LienPersonne>
            <LienPersonne :id="tache.assignee.id">{{ tache.assignee.nom }}</LienPersonne>
          </dd>
          <dd v-else class="discret">{{ t('cercles.tache.personne') }}</dd>
        </div>
        <div>
          <dt>{{ t('cercles.tache.echeance') }}</dt>
          <dd v-if="tache.echeance" :class="{ alerte: echeance === 'depassee' && tache.statut !== 'livree' }">
            {{ dateLongue(tache.echeance) }}<span v-if="echeance && tache.statut !== 'livree'" class="discret"> · {{ t(`cercles.tache.echeances.${echeance}`) }}</span>
          </dd>
          <dd v-else class="discret">{{ t('cercles.tache.sansEcheance') }}</dd>
        </div>
        <div>
          <dt>{{ t('cercles.tache.creee') }}</dt>
          <dd>{{ dateLongue(tache.creeLe) }}<span v-if="tache.proposeeParIa" class="discret"> · {{ t('cercles.tache.parAyo') }}</span></dd>
        </div>
      </dl>

      <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      <footer class="actions">
        <button type="button" class="bouton bouton--discret" data-test="modifier-tache" @click="modifier">{{ t('cercles.tache.modifier') }}</button>
        <span class="actions__droite">
          <template v-if="tache.statut !== 'livree'">
            <button v-if="!tache.assignee && moiId" type="button" class="bouton bouton--secondaire" :disabled="envoi" data-test="fiche-prendre" @click="agir({ assigneeId: moiId, statut: 'en_cours' })">{{ t('cercles.tachesTour.prendre') }}</button>
            <button v-else-if="aMoi" type="button" class="bouton bouton--secondaire" :disabled="envoi" @click="agir({ assigneeId: null, statut: 'a_faire' })">{{ t('cercles.tachesTour.lacher') }}</button>
            <button type="button" class="bouton bouton--principal" :disabled="envoi" data-test="fiche-livrer" @click="agir({ statut: 'livree' })"><IconeCercle nom="coche" :taille="16" />{{ t('cercles.tache.livrer') }}</button>
          </template>
          <button v-else type="button" class="bouton bouton--secondaire" :disabled="envoi" @click="agir({ statut: tache.assignee ? 'en_cours' : 'a_faire' })">{{ t('cercles.tache.rouvrir') }}</button>
        </span>
      </footer>
    </article>

    <!-- Créer ou modifier -->
    <form v-else class="formulaire" data-test="formulaire-tache" @submit.prevent="enregistrer">
      <header class="fiche__tete">
        <h2 id="dialogue-tache-titre" class="formulaire__titre">{{ mode === 'creer' ? t('cercles.tache.nouvelle') : t('cercles.tache.modifierTitre') }}</h2>
        <button type="button" class="fermer" :aria-label="t('cercles.tache.fermer')" @click="mode === 'modifier' ? (mode = 'voir') : fermer()"><IconeCercle nom="fermer" /></button>
      </header>

      <label class="champ">
        <span class="champ__libelle">{{ t('cercles.tache.titre') }}</span>
        <input ref="champTitre" v-model="form.titre" class="champ__titre" maxlength="200" required :placeholder="t('cercles.tache.titreExemple')" data-test="champ-titre" />
      </label>

      <div class="deux">
        <label class="champ">
          <span class="champ__libelle">{{ t('cercles.tache.aFaire') }}</span>
          <textarea v-model="form.faire" rows="4" maxlength="1500" :placeholder="t('cercles.tache.aFaireExemple')" data-test="champ-faire" />
        </label>
        <label class="champ">
          <span class="champ__libelle">{{ t('cercles.tache.finiQuand') }}</span>
          <textarea v-model="form.fini" rows="4" maxlength="450" :placeholder="t('cercles.tache.finiQuandExemple')" data-test="champ-fini" />
        </label>
      </div>

      <fieldset class="priorites">
        <legend class="champ__libelle">{{ t('cercles.tache.priorite') }}</legend>
        <label v-for="p in PRIORITES" :key="p" class="choix" :class="[`choix--${p}`, { actif: form.priorite === p }]">
          <input v-model="form.priorite" type="radio" name="priorite" :value="p" :data-test="`priorite-${p}`" />
          <span class="choix__nom"><IconeCercle nom="priorite" :taille="14" />{{ t(`cercles.tache.priorites.${p}`) }}</span>
          <span class="choix__aide">{{ t(`cercles.tache.prioritesAide.${p}`) }}</span>
        </label>
      </fieldset>

      <div class="deux">
        <label class="champ">
          <span class="champ__libelle">{{ t('cercles.tache.echeance') }}</span>
          <input v-model="form.echeance" type="date" data-test="champ-echeance" />
        </label>
        <label class="champ">
          <span class="champ__libelle">{{ t('cercles.tache.responsable') }}</span>
          <select v-model="form.assigneeId" data-test="champ-responsable">
            <option value="">{{ t('cercles.tache.personne') }}</option>
            <option v-for="m in membres" :key="m.id" :value="m.id">{{ m.id === moiId ? t('cercles.tache.moi', { nom: m.nom }) : m.nom }}</option>
          </select>
        </label>
      </div>

      <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      <footer class="actions">
        <span />
        <span class="actions__droite">
          <button type="button" class="bouton bouton--discret" @click="mode === 'modifier' ? (mode = 'voir') : fermer()">{{ t('communautes.annuler') }}</button>
          <button type="submit" class="bouton bouton--principal" :disabled="!valide || envoi" :aria-busy="envoi || undefined" data-test="enregistrer-tache">
            {{ mode === 'creer' ? t('cercles.tache.creer') : t('cercles.tache.enregistrer') }}
          </button>
        </span>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.dialogue {
  /* Safari étirait les <dialog> sur tout l'écran : hauteur du contenu, plafonnée. */
  width: min(42rem, calc(100vw - 2rem)); height: fit-content; max-height: calc(100dvh - 2rem); margin: auto; overflow: auto;
  padding: 0; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); color: var(--ink);
  box-shadow: 0 24px 64px rgb(2 7 10 / 0.35);
}
.dialogue[open] { animation: entre 200ms var(--ease-out); }
.dialogue::backdrop { background: rgb(2 7 10 / 0.55); backdrop-filter: blur(3px); }
@keyframes entre { from { opacity: 0; transform: translateY(8px); } }
@media (prefers-reduced-motion: reduce) { .dialogue[open] { animation: none; } }

.fiche, .formulaire { display: grid; gap: var(--space-6); padding: var(--space-6); }
.fiche__tete { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.fiche__etiquettes { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.fiche__titre { margin: calc(-1 * var(--space-3)) 0 0; font-size: 1.625rem; line-height: 1.2; overflow-wrap: anywhere; }
.fiche__titre--livree { color: var(--ink-muted); text-decoration: line-through; text-decoration-thickness: 1px; }
.formulaire__titre { margin: 0; font-size: 1.375rem; }
.fermer { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid var(--line); border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; }
.fermer:hover { color: var(--ink); }
.fermer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

/* La priorité : un seul repère, la couleur dit l'urgence ; « normale » reste neutre. */
.priorite { display: inline-flex; align-items: center; gap: 4px; padding: 2px var(--space-2); border-radius: var(--radius-pill); font-size: 0.8125rem; font-weight: 700; border: 1px solid var(--line); color: var(--ink-muted); }
.priorite--haute { border-color: transparent; background: var(--proof-soft); color: var(--proof-ink); }
.priorite--basse :deep(svg) { transform: rotate(180deg); }
.priorite--normale :deep(svg) { display: none; }
.statut { padding: 2px var(--space-2); border-radius: var(--radius-pill); font-size: 0.8125rem; font-weight: 600; background: var(--surface-raised); color: var(--ink-muted); }
.statut--en_cours { background: var(--action-soft); color: var(--action-ink); }

.fiche__description { display: grid; gap: var(--space-4); }
.fiche__description h3 { margin: 0 0 var(--space-1); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 700; }
.fiche__description p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
.fini { padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--surface-raised); }
.vide { color: var(--ink-muted); }
.lien-texte { padding: 0; border: 0; background: none; color: var(--action-ink); font: inherit; font-weight: 600; cursor: pointer; text-decoration: underline; text-underline-offset: 2px; }

.fiche__infos { margin: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.fiche__infos dt { color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; margin-bottom: var(--space-1); }
.fiche__infos dd { margin: 0; font-size: var(--type-body-sm-size); }
.fiche__infos .personne { display: flex; align-items: center; gap: var(--space-2); }
.discret { color: var(--ink-muted); }
.alerte { color: var(--danger); font-weight: 600; }

.champ { display: grid; gap: var(--space-1); }
.champ__libelle { font-size: 0.8125rem; font-weight: 700; color: var(--ink-muted); padding: 0; }
.champ input, .champ textarea, .champ select {
  width: 100%; box-sizing: border-box; min-height: 44px; padding: var(--space-2) var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md);
  background: var(--surface-raised); color: var(--ink); font: inherit;
}
.champ textarea { resize: vertical; line-height: 1.5; }
.champ__titre { font-size: 1.125rem !important; font-weight: 600; }
.champ :focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.deux { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }

.priorites { margin: 0; padding: 0; border: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-2); }
.priorites legend { margin-bottom: var(--space-1); }
.choix { position: relative; display: grid; gap: 2px; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); cursor: pointer; transition: border-color var(--motion-fast) var(--ease-out); }
.choix input { position: absolute; opacity: 0; pointer-events: none; }
.choix:hover { border-color: var(--ink-muted); }
.choix.actif { border-color: var(--ink); box-shadow: inset 0 0 0 1px var(--ink); }
.choix--haute.actif { border-color: var(--proof); box-shadow: inset 0 0 0 1px var(--proof); background: var(--proof-soft); }
.choix:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.choix__nom { display: inline-flex; align-items: center; gap: 4px; font-weight: 700; }
.choix--haute .choix__nom { color: var(--proof-ink); }
.choix--normale .choix__nom :deep(svg) { display: none; }
.choix--basse .choix__nom :deep(svg) { transform: rotate(180deg); }
.choix__aide { color: var(--ink-muted); font-size: 0.8125rem; }

.actions { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; }
.actions__droite { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-inline-start: auto; }
.bouton { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-6); border-radius: var(--radius-pill); font: inherit; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.bouton--principal { background: var(--action); color: var(--on-action); }
.bouton--secondaire { background: var(--surface); color: var(--ink); border-color: var(--line); }
.bouton--discret { background: transparent; color: var(--ink-muted); padding: 0 var(--space-3); }
.bouton--discret:hover { color: var(--ink); }
.bouton:disabled { opacity: 0.5; cursor: not-allowed; }
.bouton:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

@media (max-width: 640px) {
  .fiche, .formulaire { padding: var(--space-4); }
  .deux, .priorites, .fiche__infos { grid-template-columns: minmax(0, 1fr); }
}
</style>
