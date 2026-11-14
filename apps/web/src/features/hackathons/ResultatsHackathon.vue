<script setup lang="ts">
import { ref, watch } from 'vue'
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
</script>

<template>
  <section class="carte resultats" aria-labelledby="resultats-titre" data-test="resultats">
    <h2 id="resultats-titre">{{ t('hackathons.resultats.titre') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible' || !resultats" role="alert" class="texte-discret">{{ t('hackathons.resultats.indisponible') }}</p>
    <template v-else>
      <p class="texte-discret">{{ t('hackathons.resultats.publieLe', { date: formaterDate(resultats.publieLe, locale) }) }}</p>
      <ul v-if="resultats.gagnants.length" class="gagnants">
        <li v-for="g in resultats.gagnants" :key="g.prix.id ?? g.prix.nom" data-test="gagnant-prix">
          <strong>{{ g.prix.nom }}</strong>
          <span v-if="g.prix.montant" class="texte-discret"> · {{ formaterMontant(g.prix.montant, locale) }}</span>
          <span v-if="g.prix.sponsor" class="texte-discret"> · {{ t('hackathons.resultats.offertPar', { sponsor: g.prix.sponsor }) }}</span>
          <span class="equipe">{{ g.equipe.nom }}</span>
        </li>
      </ul>

      <h3>{{ t('hackathons.resultats.classement') }}</h3>
      <ol class="classement">
        <li v-for="l in resultats.classement" :key="l.equipe.id" data-test="ligne-resultat">
          <p class="ligne">
            <span class="rang">{{ t('hackathons.resultats.rang', { rang: l.rang }, l.rang) }}</span>
            <strong>{{ l.rendu.titre }}</strong>
            <span class="texte-discret">{{ l.equipe.nom }}</span>
          </p>
          <a :href="l.rendu.lienProduit" target="_blank" rel="noopener noreferrer nofollow">{{ t('hackathons.resultats.voir', { titre: l.rendu.titre }) }}</a>
          <template v-if="l.retourDuJury.length">
            <p class="texte-discret">{{ t('hackathons.resultats.retours') }}</p>
            <!-- Texte libre des jurés : affiché échappé, avec ses retours à la ligne. -->
            <ul class="retours">
              <li v-for="(r, i) in l.retourDuJury" :key="i" class="texte">{{ r }}</li>
            </ul>
          </template>
        </li>
      </ol>
    </template>
  </section>
</template>

<style scoped>
.resultats { display: grid; gap: var(--space-4); padding: var(--space-8); }
.resultats h2 { margin: 0; font-size: 1.25rem; }
.resultats h3 { margin: 0; font-size: 1rem; }
.texte-discret, .ligne { margin: 0; }
.gagnants, .classement { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.gagnants li { padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.equipe { display: block; margin-top: var(--space-1); font-weight: 600; }
.classement > li { display: grid; gap: var(--space-2); padding-bottom: var(--space-3); border-bottom: 1px solid var(--line-strong); }
.ligne { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: baseline; }
.rang { font-weight: 700; }
.retours { margin: 0; padding-left: var(--space-4); display: grid; gap: var(--space-1); }
.texte { white-space: pre-line; }
</style>
