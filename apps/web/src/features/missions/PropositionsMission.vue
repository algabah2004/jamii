<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { formaterDate } from './action'
import { formaterMontant, listerPropositions, retenirProposition, retirerProposition, type Mission, type Proposition } from './missions'

/*
 * Les propositions d'une mission (contracts/missions.yaml, tag « propositions ») : toutes pour le client, qui en
 * retient une ; la sienne seulement pour un développeur, qui peut la retirer tant que la mission n'est pas attribuée.
 */
const props = defineProps<{ mission: Mission }>()
const emit = defineEmits<{ maj: [mission: Mission]; recharger: [] }>()
const { t, locale } = useI18n()

const propositions = ref<Proposition[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const envoi = ref<string | null>(null)
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

async function agir(p: Proposition, retenir: boolean) {
  if (envoi.value) return
  envoi.value = p.id
  code.value = null
  try {
    if (retenir) {
      const r = await retenirProposition(props.mission.id, p.id, props.mission.version)
      if (r.ok) emit('maj', r.valeur)
      else code.value = r.code ?? 'autre'
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
    <ul v-else class="liste">
      <li v-for="p in propositions" :key="p.id" :data-statut="p.statut" data-test="proposition">
        <header class="tete">
          <span class="personne"><JAvatar :nom="p.developpeur.nom" :url="p.developpeur.avatarUrl" :taille="28" /> {{ p.developpeur.nom }}</span>
          <span class="etiquette" data-test="statut-proposition">{{ t(`missions.proposition.statuts.${p.statut}`) }}</span>
        </header>
        <p class="faits">
          <strong>{{ formaterMontant(p.prix, locale) }}</strong> ·
          {{ t('missions.proposition.delai', { n: p.delaiJours }, p.delaiJours) }} ·
          <span class="texte-discret">{{ t('missions.proposition.envoyeeLe', { date: formaterDate(p.envoyeeLe, locale) }) }}</span>
        </p>
        <!-- Texte libre du développeur : affiché échappé, avec ses retours à la ligne. -->
        <p class="message">{{ p.message }}</p>
        <div class="actions">
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
.actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
