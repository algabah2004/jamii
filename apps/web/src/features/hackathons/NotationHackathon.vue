<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JSquelette } from '../../design-system'
import { LIMITES_JURY, listerRendus, noter, type Hackathon, type Rendu } from './hackathons'

/*
 * Noter, côté juré (contracts/hackathons.yaml, `listerRendus` et `noter`) : chaque rendu à juger, une valeur de 1 à 5
 * par critère de la grille et un commentaire. Le rendu de sa propre équipe n'apparaît pas (conflit d'intérêts). On
 * peut renoter tant que les résultats ne sont pas publiés : les nouvelles notes remplacent les précédentes.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t, locale } = useI18n()

type Saisie = { valeurs: Record<string, number | null>; commentaire: string; score: number | null; envoi: boolean; code: string | null }

const rendus = ref<Rendu[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const saisies = reactive<Record<string, Saisie>>({})

// L'identifiant d'un critère est facultatif dans le contrat (absent à la création) : seuls les critères enregistrés se notent.
const criteres = computed(() => props.hackathon.criteres.filter((c): c is typeof c & { id: string } => !!c.id))
const score = (n: number) => new Intl.NumberFormat(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
const complet = (s: Saisie) => criteres.value.every((c) => s.valeurs[c.id] != null)
const message = (code: string) =>
  t(`hackathons.notation.erreurs.${['etat_invalide', 'delai_depasse', 'conflit_interets', 'validation'].includes(code) ? code : 'autre'}`)

async function charger() {
  try {
    rendus.value = await listerRendus(props.hackathon.id)
    for (const r of rendus.value) {
      saisies[r.id] ??= { valeurs: Object.fromEntries(criteres.value.map((c) => [c.id, null])), commentaire: '', score: null, envoi: false, code: null }
    }
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.hackathon.id, () => void charger(), { immediate: true })

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
    <article v-for="r in rendus" :key="r.id" class="rendu" :aria-labelledby="`rendu-${r.id}`" data-test="rendu-a-noter">
      <h3 :id="`rendu-${r.id}`">{{ r.titre }}</h3>
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
.notation { padding: var(--space-8); }
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
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
