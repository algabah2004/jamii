<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listerEquipes, listerInscriptions, listerJures, listerSansEquipe, type Equipe, type Hackathon, type Inscription, type Jure } from './hackathons'

/*
 * Le tableau de bord de l'organisateur, dans le résumé du hackathon : les chiffres qui comptent pour le piloter
 * (équipes, personnes seules, jurés, candidatures, notation) et ce qui demande son attention, avec un lien vers la
 * section où agir. Lecture seule : chaque action reste dans sa section.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t } = useI18n()

const equipes = ref<Equipe[]>([])
const seuls = ref<Inscription[]>([])
const jures = ref<Jure[]>([])
const candidatures = ref<Inscription[]>([])
const pret = ref(false)

async function charger() {
  const h = props.hackathon
  const [e, s, j, c] = await Promise.allSettled([
    listerEquipes(h.id),
    listerSansEquipe(h.id),
    listerJures(h.id),
    h.modeInscription === 'candidature' ? listerInscriptions(h.id) : Promise.resolve([] as Inscription[]),
  ])
  equipes.value = e.status === 'fulfilled' ? e.value : []
  seuls.value = s.status === 'fulfilled' ? s.value : []
  jures.value = j.status === 'fulfilled' ? j.value : []
  candidatures.value = c.status === 'fulfilled' ? c.value : []
  pret.value = true
}
watch(() => [props.hackathon.id, props.hackathon.version], () => void charger(), { immediate: true })

const enAttente = computed(() => candidatures.value.filter((i) => i.statut === 'en_attente').length)
const notes = computed(() => jures.value.reduce((s, j) => s + j.rendusNotes, 0))
const aNoter = computed(() => jures.value.reduce((s, j) => s + j.rendusNotes + j.rendusATraiter, 0))

/** Ce qui demande l'attention de l'organisateur maintenant, avec la section où agir. */
const alertes = computed(() => {
  const statut = props.hackathon.statut
  const liste: { cle: string; ancre: string; n?: number }[] = []
  if (!jures.value.length && ['publie', 'en_cours', 'evaluation'].includes(statut)) liste.push({ cle: 'aucunJure', ancre: '#jury' })
  if (enAttente.value) liste.push({ cle: 'candidatures', ancre: '#candidatures', n: enAttente.value })
  if (seuls.value.length && ['publie', 'en_cours'].includes(statut)) liste.push({ cle: 'seuls', ancre: '#equipes', n: seuls.value.length })
  if (statut === 'evaluation' && aNoter.value > notes.value) liste.push({ cle: 'notation', ancre: '#jury', n: aNoter.value - notes.value })
  return liste
})
</script>

<template>
  <section v-if="pret" class="pilotage" :aria-label="t('hackathons.pilotage.titre')" data-test="pilotage">
    <p class="pilotage__titre">{{ t('hackathons.pilotage.titre') }}</p>
    <dl class="chiffres">
      <div><dt>{{ t('hackathons.pilotage.equipes') }}</dt><dd data-test="pilotage-equipes">{{ equipes.length }}</dd></div>
      <div><dt>{{ t('hackathons.pilotage.seuls') }}</dt><dd>{{ seuls.length }}</dd></div>
      <div><dt>{{ t('hackathons.pilotage.jures') }}</dt><dd data-test="pilotage-jures">{{ jures.length }}</dd></div>
      <div v-if="hackathon.statut === 'evaluation' || hackathon.statut === 'resultats'">
        <dt>{{ t('hackathons.pilotage.notation') }}</dt><dd>{{ notes }} / {{ aNoter }}</dd>
      </div>
    </dl>
    <ul v-if="alertes.length" class="alertes" data-test="alertes">
      <li v-for="a in alertes" :key="a.cle"><a :href="a.ancre">{{ t(`hackathons.pilotage.alertes.${a.cle}`, { n: a.n ?? 0 }, a.n ?? 0) }}</a></li>
    </ul>
    <p v-else class="texte-discret" data-test="tout-va-bien">{{ t('hackathons.pilotage.rienAFaire') }}</p>
  </section>
</template>

<style scoped>
.pilotage { display: grid; gap: var(--space-3); padding-block-start: var(--space-4); border-block-start: 1px solid var(--line-strong); }
.pilotage p { margin: 0; }
.pilotage__titre { font-weight: 700; }
.chiffres { margin: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-3); }
.chiffres dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.chiffres dd { margin: 0; font-size: 1.25rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.alertes { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.alertes li { padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--cantaloupe) 10%, transparent); font-size: var(--type-body-sm-size); font-weight: 600; }
.alertes a { color: inherit; }
</style>
