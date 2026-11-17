<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JSquelette } from '../../design-system'
import { LIMITES_JURY, lireMesNotes, listerRendus, noter, type Hackathon, type Rendu } from './hackathons'

/*
 * Noter, côté juré (contracts/hackathons.yaml, `listerRendus` et `noter`) : chaque rendu à juger, une valeur de 1 à 5
 * par critère de la grille et un commentaire. Le rendu de sa propre équipe n'apparaît pas (conflit d'intérêts). On
 * peut renoter tant que les résultats ne sont pas publiés : les nouvelles notes remplacent les précédentes.
 * Les notes déjà données sont relues (`lireMesNotes`) : le juré retrouve où il en est en revenant sur la page.
 */
const props = defineProps<{ hackathon: Hackathon }>()
/** Où en est le juré : la page l'annonce dans son résumé et sa barre mobile. */
const emit = defineEmits<{ progression: [etat: { notes: number; total: number }] }>()
const { t, locale } = useI18n()

type Saisie = { valeurs: Record<string, number | null>; commentaire: string; score: number | null; envoi: boolean; code: string | null }

const rendus = ref<Rendu[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const saisies = reactive<Record<string, Saisie>>({})

// L'identifiant d'un critère est facultatif dans le contrat (absent à la création) : seuls les critères enregistrés se notent.
const criteres = computed(() => props.hackathon.criteres.filter((c): c is typeof c & { id: string } => !!c.id))
const dateLimite = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'long', timeStyle: 'short' }).format(new Date(props.hackathon.calendrier.resultatsLe)))
const score = (n: number) => new Intl.NumberFormat(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
/** Combien de rendus ont déjà des notes enregistrées : le juré sait ce qu'il lui reste. */
const notes = computed(() => rendus.value.filter((r) => saisies[r.id]?.score != null).length)
const complet = (s: Saisie) => criteres.value.every((c) => s.valeurs[c.id] != null)
const message = (code: string) =>
  t(`hackathons.notation.erreurs.${['etat_invalide', 'delai_depasse', 'conflit_interets', 'validation'].includes(code) ? code : 'autre'}`)

async function charger() {
  try {
    const [lus, deja] = await Promise.all([listerRendus(props.hackathon.id), lireMesNotes(props.hackathon.id).catch(() => [])])
    rendus.value = lus
    for (const r of lus) {
      const d = deja.find((n) => n.renduId === r.id)
      const valeurs: Record<string, number | null> = Object.fromEntries(criteres.value.map((c) => [c.id, null]))
      for (const n of d?.notes ?? []) if (n.critereId in valeurs) valeurs[n.critereId] = n.valeur
      saisies[r.id] ??= { valeurs, commentaire: d?.commentaire ?? '', score: d?.scorePondere ?? null, envoi: false, code: null }
    }
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.hackathon.id, () => void charger(), { immediate: true })
watch([etat, notes, () => rendus.value.length], () => {
  if (etat.value === 'pret') emit('progression', { notes: notes.value, total: rendus.value.length })
})

async function envoyer(rendu: Rendu) {
  const s = saisies[rendu.id]
  if (!s || !complet(s) || s.envoi) return
  s.envoi = true
  s.code = null
  try {
    const notes = criteres.value.map((c) => ({ critereId: c.id, valeur: s.valeurs[c.id]! }))
    const r = await noter(props.hackathon.id, rendu.id, notes, s.commentaire.trim() || null)
    if (r.ok) s.score = r.valeur.scorePondere
    else s.code = r.code ?? 'autre'
  } catch {
    s.code = 'autre'
  } finally {
    s.envoi = false
  }
}
</script>

<template>
  <section class="carte notation" aria-labelledby="notation-titre" data-test="notation">
    <h2 id="notation-titre">{{ t('hackathons.notation.titre') }}</h2>
    <p class="texte-discret">{{ t('hackathons.notation.intro') }}</p>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.notation.indisponible') }}</p>
    <p v-else-if="!rendus.length" class="texte-discret" data-test="aucun-rendu">{{ t('hackathons.notation.aucun') }}</p>
    <template v-else>
    <div class="progression" data-test="progression">
      <p><strong>{{ t('hackathons.notation.progression', { n: notes, total: rendus.length }, notes) }}</strong></p>
      <progress :value="notes" :max="rendus.length" :aria-label="t('hackathons.notation.progression', { n: notes, total: rendus.length }, notes)" />
      <p v-if="hackathon.calendrier.resultatsLe" class="texte-discret">{{ t('hackathons.notation.avant', { date: dateLimite }) }}</p>
    </div>
    <article v-for="r in rendus" :key="r.id" class="rendu" :data-note="saisies[r.id]?.score != null" :aria-labelledby="`rendu-${r.id}`" data-test="rendu-a-noter">
      <div class="rendu__tete">
        <h3 :id="`rendu-${r.id}`">{{ r.titre }}</h3>
        <span v-if="saisies[r.id]?.score != null" class="pastille" data-test="deja-note">{{ t('hackathons.notation.note') }}</span>
      </div>
      <!-- Texte libre de l'équipe : affiché échappé, avec ses retours à la ligne. -->
      <p class="texte">{{ r.description }}</p>
      <ul class="liens">
        <li><a :href="r.lienProduit" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.notation.produit') }}</a></li>
        <li v-if="r.depot"><a :href="r.depot" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.notation.depot') }}</a></li>
        <li v-if="r.video"><a :href="r.video" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.notation.video') }}</a></li>
      </ul>
      <p v-if="r.lienRepond === false" class="texte-discret">{{ t('hackathons.notation.lienNeRepondPas') }}</p>

      <form v-if="saisies[r.id]" class="formulaire" data-test="form-notes" @submit.prevent="envoyer(r)">
        <fieldset v-for="c in criteres" :key="c.id" data-test="critere">
          <legend>{{ c.nom }} <span class="texte-discret">· {{ t('hackathons.notation.poids', { n: c.poids }) }}</span></legend>
          <div class="valeurs">
            <label v-for="v in 5" :key="v"><input v-model="saisies[r.id]!.valeurs[c.id]" type="radio" :name="`${r.id}-${c.id}`" :value="v" /> {{ v }}</label>
          </div>
        </fieldset>
        <JChamp
          v-model="saisies[r.id]!.commentaire"
          :libelle="t('hackathons.notation.commentaire')"
          :aide="t('hackathons.notation.aideCommentaire')"
          multiligne
          :lignes="3"
          :maxlength="LIMITES_JURY.commentaireMax"
        />
        <div>
          <JBouton type="submit" :occupe="saisies[r.id]!.envoi" :desactive="!complet(saisies[r.id]!)" data-test="noter">
            {{ saisies[r.id]!.score === null ? t('hackathons.notation.envoyer') : t('hackathons.notation.renoter') }}
          </JBouton>
        </div>
        <p v-if="saisies[r.id]!.score !== null" role="status" class="succes" data-test="score">
          {{ t('hackathons.notation.enregistre', { score: score(saisies[r.id]!.score!) }) }}
        </p>
        <p v-if="saisies[r.id]!.code" role="alert" class="erreur" data-test="erreur-notes">{{ message(saisies[r.id]!.code!) }}</p>
      </form>
    </article>
    </template>
  </section>
</template>

<style scoped>
.notation, .formulaire { display: grid; gap: var(--space-4); }
.notation { padding: clamp(var(--space-4), 6vw, var(--space-8)); }
.notation h2 { margin: 0; font-size: 1.25rem; }
.notation h3 { margin: 0; font-size: 1rem; }
.rendu { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.texte, .texte-discret { margin: 0; }
.texte { white-space: pre-line; }
.liens { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: var(--space-4); }
fieldset { margin: 0; padding: 0; border: 0; display: grid; gap: var(--space-2); }
legend { font-weight: 600; margin-bottom: var(--space-1); }
.valeurs { display: flex; flex-wrap: wrap; gap: var(--space-4); }
.valeurs label { display: inline-flex; gap: var(--space-1); align-items: center; min-height: 44px; }
.succes { margin: 0; font-weight: 600; }
.progression { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action) 10%, transparent); }
.progression p { margin: 0; }
.progression progress { inline-size: 100%; block-size: 0.5rem; accent-color: var(--action); }
.rendu[data-note='true'] { border-inline-start: 4px solid var(--action); }
.rendu__tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.pastille { padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--action); font-size: var(--type-body-sm-size); font-weight: 700; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
