<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { DECISIONS, listerInscriptions, trierInscription, type Hackathon, type Inscription } from './hackathons'

/*
 * Trier les candidatures, côté organisateur, quand le hackathon est sur candidature (contracts/hackathons.yaml,
 * `listerInscriptions` et `trierInscription`) : accepter, mettre en liste d'attente ou refuser. La personne est
 * prévenue par l'API. Les candidatures en attente d'une réponse viennent d'abord.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t } = useI18n()

const inscriptions = ref<Inscription[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const enCours = ref<string | null>(null)
const erreur = ref<{ id: string; code: string } | null>(null)

const triees = computed(() => [...inscriptions.value].sort((a, b) => Number(b.statut === 'en_attente') - Number(a.statut === 'en_attente')))
const aTrier = computed(() => inscriptions.value.filter((i) => i.statut === 'en_attente').length)
const message = (code: string) => t(`hackathons.tri.erreurs.${code === 'etat_invalide' ? code : 'autre'}`)

async function charger() {
  try {
    inscriptions.value = await listerInscriptions(props.hackathon.id)
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.hackathon.id, () => void charger(), { immediate: true })

async function decider(inscription: Inscription, statut: (typeof DECISIONS)[number]) {
  if (enCours.value) return
  enCours.value = inscription.id
  erreur.value = null
  try {
    const r = await trierInscription(props.hackathon.id, inscription.id, statut)
    if (r.ok) inscriptions.value = inscriptions.value.map((i) => (i.id === r.valeur.id ? r.valeur : i))
    else erreur.value = { id: inscription.id, code: r.code ?? 'autre' }
  } catch {
    erreur.value = { id: inscription.id, code: 'autre' }
  } finally {
    enCours.value = null
  }
}
</script>

<template>
  <section class="carte tri" aria-labelledby="tri-titre" data-test="tri">
    <h2 id="tri-titre">{{ t('hackathons.tri.titre') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.tri.indisponible') }}</p>
    <p v-else-if="!inscriptions.length" class="texte-discret" data-test="aucune-candidature">{{ t('hackathons.tri.aucune') }}</p>
    <template v-else>
      <p class="texte-discret" data-test="a-trier">{{ t('hackathons.tri.aTrier', { n: aTrier }, aTrier) }}</p>
      <ul class="liste">
        <li v-for="i in triees" :key="i.id" class="candidature" data-test="candidature">
          <div class="personne">
            <JAvatar :nom="i.personne.nom" :url="i.personne.avatarUrl" :taille="40" />
            <div>
              <strong>{{ i.personne.nom }}</strong>
              <p v-if="i.competences.length" class="texte-discret">{{ i.competences.join(', ') }}</p>
              <p class="statut" data-test="statut-candidature">{{ t(`hackathons.tri.statuts.${i.statut}`) }}</p>
            </div>
          </div>
          <div class="decisions" role="group" :aria-label="t('hackathons.tri.decider', { nom: i.personne.nom })">
            <template v-for="d in DECISIONS" :key="d">
              <JBouton
                v-if="d !== i.statut"
                :variante="d === 'acceptee' ? 'principal' : 'secondaire'"
                :occupe="enCours === i.id"
                :data-test="`decider-${d}`"
                @click="decider(i, d)"
              >
                {{ t(`hackathons.tri.actions.${d}`) }}
              </JBouton>
            </template>
          </div>
          <p v-if="erreur?.id === i.id" role="alert" class="erreur" data-test="erreur-tri">{{ message(erreur.code) }}</p>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.tri { display: grid; gap: var(--space-4); padding: var(--space-8); }
.tri h2 { margin: 0; font-size: 1.25rem; }
.texte-discret, .statut { margin: 0; }
.statut { font-weight: 600; }
.liste { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.candidature { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.personne { display: flex; gap: var(--space-3); align-items: flex-start; }
.decisions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
