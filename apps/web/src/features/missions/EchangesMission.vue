<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton } from '../../design-system'
import { envoyerMessage, LONGUEUR_MESSAGE, lireConversation, quand, type Message } from '../reseau/reseau'
import type { Mission } from './missions'

/*
 * Les échanges entre le client et un développeur, sur la page de la mission. On réutilise la messagerie privée
 * (contracts/reseau.yaml, tag « messages ») plutôt qu'un second fil : un seul endroit pour se parler, une notification
 * à chaque message, et la même conversation dans « Messages ». Côté client, on choisit avec qui parler depuis une
 * proposition ; côté développeur, on écrit au client, avant ou après avoir proposé ses services.
 */
type Personne = { id: string; nom: string; avatarUrl?: string | null }
const props = defineProps<{ mission: Mission; avec: Personne | null }>()
const { t, locale } = useI18n()

const messages = ref<Message[]>([])
const etat = ref<'vide' | 'chargement' | 'pret' | 'indisponible'>('vide')
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const fil = ref<HTMLElement | null>(null)

const estClient = computed(() => props.mission.moi.role === 'client')
/** Avant le choix du développeur, on invite à poser ses questions ; ensuite, une phrase neutre. */
const avantChoix = computed(() => ['publiee', 'fermee'].includes(props.mission.statut))
/** Les 30 derniers : au-delà, la conversation complète est dans « Messages ». */
const recents = computed(() => messages.value.slice(-30))

async function defiler() {
  await nextTick()
  if (fil.value) fil.value.scrollTop = fil.value.scrollHeight
}

async function charger() {
  if (!props.avec) {
    etat.value = 'vide'
    return
  }
  etat.value = 'chargement'
  try {
    const c = await lireConversation(props.avec.id)
    messages.value = c?.messages ?? []
    etat.value = 'pret'
    void defiler()
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.avec?.id, () => void charger(), { immediate: true })

async function envoyer() {
  const propre = texte.value.trim()
  if (!props.avec || !propre || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await envoyerMessage(props.avec.id, propre)
    if (r.ok) {
      messages.value = [...messages.value, r.message]
      texte.value = ''
      void defiler()
    } else erreur.value = t(r.code === 'trop_de_requetes' ? 'missions.echanges.erreurs.trop' : 'missions.echanges.erreurs.autre')
  } catch {
    erreur.value = t('missions.echanges.erreurs.autre')
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section class="carte echanges" aria-labelledby="echanges-titre" data-test="echanges">
    <header class="tete">
      <h2 id="echanges-titre">
        {{ avec ? t('missions.echanges.titreAvec', { nom: avec.nom }) : t('missions.echanges.titre') }}
      </h2>
      <RouterLink v-if="avec" :to="`/messages/${avec.id}`" class="texte-discret" data-test="ouvrir-messages">{{ t('missions.echanges.toutVoir') }}</RouterLink>
    </header>

    <p v-if="!avec" class="texte-discret" data-test="choisir">{{ t('missions.echanges.choisir') }}</p>
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('missions.echanges.indisponible') }}</p>
    <template v-else-if="etat === 'pret'">
      <p v-if="!messages.length" class="texte-discret" data-test="aucun-message">
        {{ !avantChoix ? t('missions.echanges.vide', { nom: avec.nom }) : estClient ? t('missions.echanges.videClient', { nom: avec.nom }) : t('missions.echanges.videDeveloppeur') }}
      </p>
      <!-- Une zone qui défile doit pouvoir prendre le focus (AXE : scrollable-region-focusable). -->
      <ol v-else ref="fil" class="fil" tabindex="0" :aria-label="t('missions.echanges.fil')" data-test="fil">
        <li v-for="m in recents" :key="m.id" :class="{ 'de-moi': m.deMoi }" data-test="message">
          <span v-if="!m.deMoi" class="auteur"><JAvatar :nom="avec.nom" :url="avec.avatarUrl ?? null" :taille="20" /></span>
          <p class="bulle">{{ m.texte }}</p>
          <span class="heure texte-discret">{{ quand(m.envoyeLe, locale) }}</span>
        </li>
      </ol>
      <form class="saisie" data-test="form-echange" @submit.prevent="envoyer">
        <label :for="`echange-${mission.id}`" class="visuellement-cache">{{ t('missions.echanges.ecrire', { nom: avec.nom }) }}</label>
        <textarea
          :id="`echange-${mission.id}`"
          v-model="texte"
          rows="2"
          :maxlength="LONGUEUR_MESSAGE"
          :placeholder="t('missions.echanges.ecrire', { nom: avec.nom })"
          @keydown.enter.exact.prevent="envoyer"
        />
        <JBouton type="submit" variante="secondaire" :occupe="envoi" :desactive="!texte.trim()" data-test="envoyer-echange">{{ t('missions.echanges.envoyer') }}</JBouton>
      </form>
      <p class="texte-discret">{{ t('missions.echanges.prudence') }}</p>
    </template>
    <p v-if="erreur" role="alert" class="erreur" data-test="erreur-echange">{{ erreur }}</p>
  </section>
</template>

<style scoped>
.echanges { display: grid; gap: var(--space-4); padding: clamp(var(--space-4), 6vw, var(--space-8)); }
.echanges h2, .echanges p { margin: 0; }
.echanges h2 { font-size: 1.25rem; }
.tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: baseline; justify-content: space-between; }
.fil {
  margin: 0; padding: var(--space-2); list-style: none; display: grid; gap: var(--space-3); max-block-size: 22rem; overflow-y: auto;
  border: 1px solid var(--line-strong); border-radius: var(--radius-md);
}
.fil:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.fil li { display: grid; grid-template-columns: auto 1fr; gap: var(--space-1) var(--space-2); align-items: end; justify-items: start; }
.fil li.de-moi { grid-template-columns: minmax(0, 1fr); justify-items: end; }
.bulle {
  max-inline-size: 36rem; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); white-space: pre-line;
  background: var(--surface-raised, var(--surface)); border: 1px solid var(--line-strong);
}
.de-moi .bulle { background: var(--action); border-color: var(--action); color: var(--on-action); }
.heure { grid-column: 1 / -1; font-size: 0.75rem; }
.fil li:not(.de-moi) .heure { padding-inline-start: 28px; }
.saisie { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-3); align-items: end; }
textarea {
  inline-size: 100%; padding: var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--surface); color: var(--ink); font: inherit; resize: vertical;
}
textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { color: var(--danger); font-weight: 600; }
</style>
