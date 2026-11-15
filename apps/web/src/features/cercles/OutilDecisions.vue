<script setup lang="ts">
import EnTeteOutil from './EnTeteOutil.vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import { ilYa } from '../vitrine/vitrine'
import type { Cercle } from './cercles'
import { envoyer, LIMITES_SALON, lireSalon, type MessageCercle } from './salon'

/*
 * Les décisions du cercle (le « message board » de Basecamp, réduit à l'essentiel) : ce que l'équipe a tranché, écrit
 * une fois, retrouvé sans fouiller le salon. « On part sur Flutter », « la démo est jeudi ».
 */
const props = defineProps<{ cercle: Cercle }>()
const { t, locale } = useI18n()

const decisions = ref<MessageCercle[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
onMounted(async () => {
  const r = await lireSalon(props.cercle.id).catch(() => null)
  if (!r?.ok) return void (etat.value = 'erreur')
  decisions.value = r.salon.decisions
  etat.value = 'pret'
})

const texte = ref('')
const envoi = ref(false)
const erreur = ref(false)
const pret = computed(() => !envoi.value && texte.value.trim().length >= 3)
async function consigner() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = false
  const r = await envoyer(props.cercle.id, { type: 'decision', texte: texte.value.trim() }).catch(() => null)
  envoi.value = false
  if (!r?.ok) return void (erreur.value = true)
  decisions.value = [r.message, ...decisions.value]
  texte.value = ''
}
</script>

<template>
  <section class="outil-decisions" aria-labelledby="decisions-titre" data-test="outil-decisions-page">
    <EnTeteOutil id="decisions-titre" icone="decisions" :titre="t('cercles.qg.outils.decisions')" :aide="t('cercles.qg.decisionsAide')" />

    <form class="carte nouvelle" data-test="nouvelle-decision" @submit.prevent="consigner">
      <label for="decision-texte">{{ t('cercles.qg.nouvelleDecision') }}</label>
      <textarea id="decision-texte" v-model="texte" rows="2" :maxlength="LIMITES_SALON.texte" :placeholder="t('cercles.qg.decisionExemple')" />
      <p v-if="erreur" role="alert" class="erreur">{{ t('cercles.salon.erreurs.envoi') }}</p>
      <button type="submit" class="envoyer" :disabled="!pret">{{ t('cercles.qg.consigner') }}</button>
    </form>

    <p v-if="etat === 'chargement'" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'erreur'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>
    <p v-else-if="!decisions.length" class="texte-discret">{{ t('cercles.qg.aucuneDecision') }}</p>
    <ol v-else class="liste">
      <li v-for="d in decisions" :key="d.id" class="carte decision" data-test="decision">
        <p class="decision__texte">{{ d.texte }}</p>
        <p class="decision__qui">
          <LienPersonne :id="d.auteur?.id" doublon><JAvatar :nom="d.auteur?.nom ?? '?'" :url="d.auteur?.avatarUrl" :taille="22" /></LienPersonne>
          <LienPersonne :id="d.auteur?.id">{{ d.auteur?.nom }}</LienPersonne> · {{ ilYa(d.envoyeLe, locale) }}
        </p>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.outil-decisions { display: grid; gap: var(--space-4); max-width: 52rem; }
.nouvelle { display: grid; gap: var(--space-2); }
.nouvelle label { font-weight: 600; }
.nouvelle textarea { width: 100%; box-sizing: border-box; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); color: var(--ink); font: inherit; resize: vertical; }
.nouvelle textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.envoyer { justify-self: end; min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; }
.envoyer:disabled { opacity: 0.5; cursor: not-allowed; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.decision { display: grid; gap: var(--space-2); border-left: 4px solid var(--proof); }
.decision__texte { margin: 0; font-size: 1.0625rem; font-weight: 600; white-space: pre-wrap; overflow-wrap: anywhere; }
.decision__qui { margin: 0; display: flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
