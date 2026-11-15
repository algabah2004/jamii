<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JSquelette } from '../../design-system'
import { formaterDate, formaterMontant, lireResultats, type Hackathon, type Resultats } from './hackathons'

/*
 * Les résultats publiés (contracts/hackathons.yaml, `lireResultats`), visibles par tout le monde : les gagnants de
 * chaque prix, puis le classement avec les retours du jury, sans le nom des jurés.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t, locale } = useI18n()

const resultats = ref<Resultats | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  try {
    resultats.value = await lireResultats(props.hackathon.id)
    etat.value = resultats.value ? 'pret' : 'indisponible'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.hackathon.id, () => void charger(), { immediate: true })

/** Deux rendus au même rang sont ex aequo : on le dit, plutôt que d'afficher deux « 1er » sans explication. */
const exAequo = computed(() => {
  const compte = new Map<number, number>()
  for (const l of resultats.value?.classement ?? []) compte.set(l.rang, (compte.get(l.rang) ?? 0) + 1)
  return (rang: number) => (compte.get(rang) ?? 0) > 1
})
const score = (n: number) => new Intl.NumberFormat(locale.value, { minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(n)
</script>

<template>
  <section class="carte resultats" aria-labelledby="resultats-titre" data-test="resultats">
    <h2 id="resultats-titre">{{ t('hackathons.resultats.titre') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible' || !resultats" role="alert" class="texte-discret">{{ t('hackathons.resultats.indisponible') }}</p>
    <template v-else>
      <p class="texte-discret">{{ t('hackathons.resultats.publieLe', { date: formaterDate(resultats.publieLe, locale) }) }}</p>
      <ul v-if="resultats.gagnants.length" class="gagnants">
        <li v-for="(g, i) in resultats.gagnants" :key="g.prix.id ?? g.prix.nom" :data-place="i" data-test="gagnant-prix">
          <span class="gagnants__prix">{{ g.prix.nom }}</span>
          <strong class="gagnants__equipe">{{ g.equipe.nom }}</strong>
          <span v-if="g.prix.montant" class="gagnants__montant">{{ formaterMontant(g.prix.montant, locale) }}</span>
          <span v-if="g.prix.sponsor" class="texte-discret">{{ t('hackathons.resultats.offertPar', { sponsor: g.prix.sponsor }) }}</span>
        </li>
      </ul>

      <h3>{{ t('hackathons.resultats.classement') }}</h3>
      <ol class="classement">
        <li v-for="l in resultats.classement" :key="l.equipe.id" data-test="ligne-resultat">
          <p class="ligne">
            <span class="rang" :data-podium="l.rang <= 3 ? l.rang : undefined">{{ t('hackathons.resultats.rang', { rang: l.rang }, l.rang) }}</span>
            <span class="ligne__texte">
              <strong>{{ l.rendu.titre }}</strong>
              <span class="texte-discret">{{ l.equipe.nom }}<template v-if="exAequo(l.rang)"> · {{ t('hackathons.resultats.exAequo') }}</template></span>
            </span>
            <span class="score" data-test="score">{{ t('hackathons.resultats.score', { score: score(l.scorePondere) }) }}</span>
          </p>
          <a :href="l.rendu.lienProduit" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.resultats.voir', { titre: l.rendu.titre }) }}</a>
          <details v-if="l.retourDuJury.length" class="retours">
            <summary>{{ t('hackathons.resultats.retours', { n: l.retourDuJury.length }) }}</summary>
            <!-- Texte libre des jurés : affiché échappé, avec ses retours à la ligne. -->
            <ul>
              <li v-for="(r, i) in l.retourDuJury" :key="i" class="texte">{{ r }}</li>
            </ul>
          </details>
        </li>
      </ol>
    </template>
  </section>
</template>

<style scoped>
.resultats { display: grid; gap: var(--space-4); padding: var(--space-8); }
@media (max-width: 600px) { .resultats { padding: var(--space-4); } }
.resultats h2 { margin: 0; font-size: 1.25rem; }
.resultats h3 { margin: var(--space-2) 0 0; font-size: 1rem; }
.texte-discret, .ligne { margin: 0; }
.gagnants, .classement { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.gagnants { grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr)); }
.gagnants li { display: grid; gap: var(--space-1); align-content: start; padding: var(--space-4); border: 1px solid var(--line-strong); border-block-start: 4px solid var(--cantaloupe); border-radius: var(--radius-md); }
.gagnants li[data-place='0'] { border-block-start-color: var(--action); }
.gagnants__prix { font-size: var(--type-body-sm-size); font-weight: 700; color: var(--ink-muted); }
.gagnants__equipe { font-size: 1.25rem; }
.gagnants__montant { font-weight: 700; }
.classement > li { display: grid; gap: var(--space-2); padding-block-end: var(--space-3); border-block-end: 1px solid var(--line-strong); }
.ligne { display: grid; grid-template-columns: 3rem 1fr auto; gap: var(--space-3); align-items: center; }
.ligne__texte { display: grid; }
.rang { display: grid; place-items: center; block-size: 2.25rem; border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-weight: 800; font-size: var(--type-body-sm-size); }
.rang[data-podium='1'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
.score { font-weight: 800; font-variant-numeric: tabular-nums; }
.retours summary { cursor: pointer; font-size: var(--type-body-sm-size); color: var(--action-ink); font-weight: 600; }
.retours ul { margin: var(--space-2) 0 0; padding-inline-start: var(--space-4); display: grid; gap: var(--space-1); }
.texte { white-space: pre-line; }
</style>
