<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { formaterDate } from './action'
import { lireConversations } from '../reseau/reseau'
import { formaterMontant, listerPropositions, retenirProposition, retirerProposition, type Mission, type Proposition } from './missions'
import ReponsesQuestionnaire from '../questionnaires/ReponsesQuestionnaire.vue'

/*
 * Les propositions d'une mission (contracts/missions.yaml, tag « propositions ») : toutes pour le client, qui en
 * retient une ; la sienne seulement pour un développeur, qui peut la retirer tant que la mission n'est pas attribuée.
 */
const props = defineProps<{ mission: Mission }>()
const emit = defineEmits<{ maj: [mission: Mission]; recharger: []; ecrire: [personne: Proposition['developpeur']] }>()
const { t, locale } = useI18n()

const propositions = ref<Proposition[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const envoi = ref<string | null>(null)
const tri = ref<'recentes' | 'prix' | 'delai'>('recentes')
const TRIS = ['recentes', 'prix', 'delai'] as const
/** Les messages non lus de chaque développeur, pour le compteur du bouton « Écrire » (client seulement). */
const nonLus = ref<Record<string, number>>({})
async function chargerNonLus() {
  if (props.mission.moi.role !== 'client') return
  const conversations = await lireConversations().catch(() => [])
  nonLus.value = Object.fromEntries(conversations.map((c) => [c.personne.id, c.nonLus]))
}
void chargerNonLus()

/** Le client compare : par prix ou par délai ; les propositions retirées ou écartées passent après. */
const triees = computed(() => {
  const actives = (p: Proposition) => (p.statut === 'retiree' || p.statut === 'non_retenue' ? 1 : 0)
  const cle = (p: Proposition) => (tri.value === 'prix' ? p.prix.centimes : tri.value === 'delai' ? p.delaiJours : -Date.parse(p.envoyeeLe))
  return [...propositions.value].sort((a, b) => actives(a) - actives(b) || cle(a) - cle(b))
})

/** Au-dessus du budget annoncé (même devise seulement) : un repère pour le client, pas un refus. */
function auDessus(p: Proposition) {
  const b = props.mission.budget
  return estClient.value && b !== null && b.devise === p.prix.devise && p.prix.centimes > b.centimes
}
const code = ref<string | null>(null)

const estClient = computed(() => props.mission.moi.role === 'client')
const peutRetenir = computed(() => (props.mission.moi.actions as string[]).includes('retenir'))
const ouverte = computed(() => props.mission.statut === 'publiee')
const message = computed(() => {
  if (!code.value) return ''
  const connus = ['modification_concurrente', 'etat_invalide']
  return t(`missions.proposition.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

async function charger() {
  try {
    propositions.value = await listerPropositions(props.mission.id)
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
// La mission change après chaque action (version, statut) : la liste suit.
watch(() => props.mission.version, () => void charger(), { immediate: true })

function ecrire(p: Proposition) {
  nonLus.value = { ...nonLus.value, [p.developpeur.id]: 0 }
  emit('ecrire', p.developpeur)
}

async function agir(p: Proposition, retenir: boolean) {
  if (envoi.value) return
  envoi.value = p.id
  code.value = null
  try {
    if (retenir) {
      const r = await retenirProposition(props.mission.id, p.id, props.mission.version)
      // L'API ne change pas la version de la mission en retenant une proposition : on relit la liste nous-mêmes.
      if (r.ok) {
        emit('maj', r.valeur)
        await charger()
      } else code.value = r.code ?? 'autre'
    } else {
      const r = await retirerProposition(props.mission.id, p.id)
      if (r.ok) emit('recharger')
      else code.value = r.code ?? 'autre'
    }
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = null
  }
}
</script>

<template>
  <section class="carte propositions" aria-labelledby="propositions-titre" data-test="propositions">
    <h2 id="propositions-titre">{{ estClient ? t('missions.proposition.titre') : t('missions.proposition.titreMienne') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('missions.proposition.indisponible') }}</p>
    <p v-else-if="!propositions.length" class="texte-discret" data-test="aucune-proposition">{{ t('missions.proposition.aucune') }}</p>
    <div v-if="estClient && propositions.length > 1" class="tris" role="group" :aria-label="t('missions.proposition.trier')">
      <button v-for="x in TRIS" :key="x" type="button" class="tri" :aria-pressed="tri === x" :data-test="`tri-${x}`" @click="tri = x">
        {{ t(`missions.proposition.tris.${x}`) }}
      </button>
    </div>
    <ul v-if="etat === 'pret' && propositions.length" class="liste">
      <li v-for="p in triees" :key="p.id" :data-statut="p.statut" data-test="proposition">
        <header class="tete">
          <span class="personne">
            <JAvatar :nom="p.developpeur.nom" :url="p.developpeur.avatarUrl" :taille="28" />
            <RouterLink v-if="estClient" :to="`/p/${p.developpeur.id}`" data-test="profil-developpeur">{{ p.developpeur.nom }}</RouterLink>
            <template v-else>{{ p.developpeur.nom }}</template>
          </span>
          <span class="etiquette" data-test="statut-proposition">{{ t(`missions.proposition.statuts.${p.statut}`) }}</span>
        </header>
        <p class="faits">
          <strong>{{ formaterMontant(p.prix, locale) }}</strong>
          <span v-if="auDessus(p)" class="au-dessus" data-test="au-dessus">{{ t('missions.proposition.auDessus') }}</span> ·
          {{ t('missions.proposition.delai', { n: p.delaiJours }, p.delaiJours) }} ·
          <span class="texte-discret">{{ t('missions.proposition.envoyeeLe', { date: formaterDate(p.envoyeeLe, locale) }) }}</span>
        </p>
        <!-- Texte libre du développeur : affiché échappé, avec ses retours à la ligne. -->
        <p class="message">{{ p.message }}</p>
        <ReponsesQuestionnaire :questions="mission.questionnaire" :reponses="p.reponses ?? []" />
        <div class="actions">
          <JBouton v-if="estClient" variante="secondaire" data-test="ecrire-developpeur" @click="ecrire(p)">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M4 4.5h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H9l-3.5 3v-3H4a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
            {{ t('missions.proposition.ecrire') }}<span class="visuellement-cache">&nbsp;{{ t('missions.proposition.ecrireA', { nom: p.developpeur.nom }) }}</span>
            <span v-if="nonLus[p.developpeur.id]" class="non-lus" data-test="non-lus">
              {{ nonLus[p.developpeur.id] }}<span class="visuellement-cache"> {{ t('missions.proposition.nonLus', nonLus[p.developpeur.id] ?? 0) }}</span>
            </span>
          </JBouton>
          <JBouton v-if="peutRetenir && p.statut === 'envoyee'" variante="secondaire" :occupe="envoi === p.id" data-test="retenir" @click="agir(p, true)">
            {{ t('missions.proposition.retenir') }}
          </JBouton>
          <JBouton
            v-if="!estClient && ouverte && (p.statut === 'envoyee' || p.statut === 'retenue')"
            variante="discret"
            :occupe="envoi === p.id"
            data-test="retirer"
            @click="agir(p, false)"
          >
            {{ t('missions.proposition.retirer') }}
          </JBouton>
        </div>
        <p v-if="p.statut === 'retenue'" class="texte-discret" data-test="aide-retenue">
          {{ estClient ? t('missions.proposition.aideRetenueClient') : t('missions.proposition.aideRetenueDeveloppeur') }}
        </p>
      </li>
    </ul>
    <p v-if="peutRetenir" class="texte-discret">{{ t('missions.proposition.aideRetenir') }}</p>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-propositions">{{ message }}</p>
  </section>
</template>

<style scoped>
.propositions { display: grid; gap: var(--space-4); padding: var(--space-8); }
.propositions h2 { margin: 0; font-size: 1.25rem; }
.liste { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-4); }
.liste > li { display: grid; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.liste > li[data-statut='retenue'] { border-color: var(--action); }
.liste > li[data-statut='retiree'], .liste > li[data-statut='non_retenue'] { opacity: 0.75; }
.tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.personne { display: flex; gap: var(--space-2); align-items: center; font-weight: 600; }
.etiquette { margin: 0; }
.liste p { margin: 0; }
.message { white-space: pre-line; }
.non-lus {
  min-inline-size: 1.25rem; padding: 0 6px; border-radius: var(--radius-pill); background: var(--cantaloupe); color: var(--midnight);
  font-size: 0.75rem; font-weight: 800; text-align: center;
}
.tris { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.tri { min-height: 36px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); background: var(--surface); font-weight: 600; cursor: pointer; }
.tri[aria-pressed='true'] { background: var(--action); border-color: var(--action); color: var(--on-action); }
.tri:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.au-dessus { margin-inline-start: var(--space-2); padding: 0 var(--space-2); border-radius: var(--radius-pill); border: 1px solid var(--cantaloupe); font-size: var(--type-body-sm-size); font-weight: 700; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
