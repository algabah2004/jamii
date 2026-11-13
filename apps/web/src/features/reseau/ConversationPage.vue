<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { JAvatar, JBouton, JChamp, JSquelette } from '../../design-system'
import { rejoindreCercle } from '../cercles/cercles'
import { rafraichirActivite } from './activite'
import { envoyerMessage, LONGUEUR_MESSAGE, lireConversation, prenom, quand, type Message, type Personne } from './reseau'

/* La conversation avec une personne : les messages, l'invitation à un cercle et la réponse. */
const { t, locale } = useI18n()
const route = useRoute()
const routeur = useRouter()

const personne = ref<Personne | null>(null)
const messages = ref<Message[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const fil = ref<HTMLElement | null>(null)

async function charger() {
  etat.value = 'chargement'
  try {
    const r = await lireConversation(String(route.params.personneId))
    if (!r) {
      etat.value = 'introuvable'
      return
    }
    personne.value = r.personne
    messages.value = r.messages
    etat.value = 'pret'
    // La lecture a marqué la conversation comme lue : la pastille du menu se met à jour tout de suite.
    void rafraichirActivite()
    await descendre()
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => route.params.personneId, charger, { immediate: true })

async function descendre() {
  await nextTick()
  fil.value?.lastElementChild?.scrollIntoView?.({ block: 'end' })
}

const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const valide = computed(() => texte.value.trim().length > 0 && texte.value.length <= LONGUEUR_MESSAGE)

async function envoyer() {
  if (!valide.value || !personne.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await envoyerMessage(personne.value.id, texte.value)
    if (r.ok) {
      messages.value.push(r.message)
      texte.value = ''
      await descendre()
    } else erreur.value = t(`reseau.erreurs.${r.code ?? 'autre'}`, t('reseau.erreurs.autreMessage'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    envoi.value = false
  }
}

// Rejoindre depuis l'invitation : la même route que le bouton de la page du cercle (cercles.yaml).
const rejoint = ref<string | null>(null)
const erreurInvitation = ref<string | null>(null)
async function rejoindre(cercleId: string) {
  rejoint.value = cercleId
  erreurInvitation.value = null
  try {
    const r = await rejoindreCercle(cercleId)
    if (r.ok) await routeur.push(`/cercles/${cercleId}`)
    // Déjà membre : on y va quand même, c'est ce que la personne voulait.
    else if (r.code === 'deja_membre') await routeur.push(`/cercles/${cercleId}`)
    else erreurInvitation.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreurInvitation.value = t('etat.indisponible')
  } finally {
    rejoint.value = null
  }
}
const placeLibre = (m: Message) => !!m.invitation && m.invitation.cercle.statut === 'ouvert' && m.invitation.cercle.nombreMembres < m.invitation.cercle.taille
</script>

<template>
  <section class="conversation">
    <p class="retour"><RouterLink to="/messages">{{ t('reseau.messages.tous') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="personne">
      <header class="tete">
        <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="48" />
        <div>
          <h1>{{ personne.nom }}</h1>
          <p v-if="personne.ville" class="texte-discret">{{ personne.ville }}</p>
        </div>
      </header>

      <div class="carte fil-carte">
        <p v-if="!messages.length" class="texte-discret" data-test="conversation-vide">{{ t('reseau.messages.premier', { nom: prenom(personne.nom) }) }}</p>
        <ol ref="fil" class="fil" data-test="fil" aria-live="polite">
          <li v-for="m in messages" :key="m.id" class="bulle" :class="m.deMoi ? 'bulle--moi' : 'bulle--autre'">
            <p class="bulle__texte">{{ m.texte }}</p>
            <!-- L'invitation : le cercle tel qu'il est maintenant, et le bouton pour le rejoindre s'il reste de la place. -->
            <div v-if="m.invitation" class="invitation" data-test="invitation">
              <span class="invitation__titre">{{ t('reseau.messages.invitation') }}</span>
              <RouterLink :to="`/cercles/${m.invitation.cercle.id}`" class="invitation__cercle">{{ m.invitation.cercle.nom }}</RouterLink>
              <span class="texte-discret">{{ m.invitation.cercle.ville }} · {{ t('cercles.membres', { n: m.invitation.cercle.nombreMembres, taille: m.invitation.cercle.taille }) }}</span>
              <template v-if="!m.deMoi">
                <JBouton v-if="placeLibre(m)" :occupe="rejoint === m.invitation.cercle.id" data-test="rejoindre-invitation" @click="rejoindre(m.invitation.cercle.id)">{{ t('cercles.rejoindre') }}</JBouton>
                <span v-else class="texte-discret">{{ t('reseau.messages.cercleFerme') }}</span>
              </template>
            </div>
            <time class="bulle__heure" :datetime="m.envoyeLe">{{ quand(m.envoyeLe, locale) }}</time>
          </li>
        </ol>
        <p v-if="erreurInvitation" role="alert" class="erreur">{{ erreurInvitation }}</p>
      </div>

      <form class="reponse" data-test="form-message" @submit.prevent="envoyer">
        <JChamp v-model="texte" :libelle="t('reseau.messages.ecrire', { nom: prenom(personne.nom) })" multiligne :lignes="2" :maxlength="LONGUEUR_MESSAGE" requis />
        <JBouton type="submit" :desactive="!valide" :occupe="envoi">{{ t('reseau.messages.envoyer') }}</JBouton>
        <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      </form>
    </template>
  </section>
</template>

<style scoped>
.conversation { display: grid; gap: var(--space-4); max-width: 44rem; }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.tete { display: flex; align-items: center; gap: var(--space-3); }
.tete h1 { margin: 0; font-size: 1.75rem; }
.tete p { margin: 0; }
.fil-carte { padding: var(--space-4); display: grid; gap: var(--space-3); }
.fil { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); max-height: 60vh; overflow-y: auto; }
.bulle { display: grid; gap: var(--space-2); max-width: 80%; padding: var(--space-3) var(--space-4); border-radius: var(--radius-lg); }
.bulle--autre { justify-self: start; background: color-mix(in srgb, var(--ink) 6%, transparent); border-bottom-left-radius: var(--radius-sm, 4px); }
.bulle--moi { justify-self: end; background: var(--action-soft); color: var(--ink); border-bottom-right-radius: var(--radius-sm, 4px); }
.bulle__texte { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.bulle__heure { color: var(--ink-muted); font-size: 0.75rem; justify-self: end; }
.invitation { display: grid; gap: var(--space-1); justify-items: start; padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line); }
.invitation__titre { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.invitation__cercle { font-weight: 700; color: var(--ink); }
.reponse { display: grid; gap: var(--space-2); justify-items: start; }
.reponse :deep(.j-champ), .reponse > :first-child { width: 100%; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
