<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import OngletsReseau from './OngletsReseau.vue'
import { lireConversations, quand, type Conversation } from './reseau'

/* Mes conversations, la plus récente d'abord, avec les messages non lus mis en avant. */
const { t, locale } = useI18n()
const conversations = ref<Conversation[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    conversations.value = await lireConversations()
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

const apercu = (c: Conversation) =>
  (c.dernierMessage.deMoi ? t('reseau.messages.toi') + ' ' : '') + (c.dernierMessage.invitation && !c.dernierMessage.deMoi ? t('reseau.messages.invitationRecue', { cercle: c.dernierMessage.invitation.cercle.nom }) : c.dernierMessage.texte)
</script>

<template>
  <section class="messages">
    <header class="tete">
      <h1>{{ t('reseau.messages.titre') }}</h1>
      <OngletsReseau />
      <p class="texte-discret">{{ t('reseau.messages.intro') }}</p>
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <div v-else-if="!conversations.length" class="etat-vide" data-test="messages-vide">
      <p>{{ t('reseau.messages.vide') }}</p>
      <RouterLink to="/cercles" class="j-bouton j-bouton--principal">{{ t('menu.cercles') }}</RouterLink>
    </div>
    <ul v-else class="liste carte" data-test="conversations">
      <li v-for="c in conversations" :key="c.personne.id">
        <RouterLink :to="`/messages/${c.personne.id}`" class="ligne" :class="{ 'ligne--nouveau': c.nonLus }">
          <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="44" />
          <span class="ligne__texte">
            <span class="ligne__haut">
              <strong>{{ c.personne.nom }}</strong>
              <time :datetime="c.dernierMessage.envoyeLe">{{ quand(c.dernierMessage.envoyeLe, locale) }}</time>
            </span>
            <span class="ligne__apercu">{{ apercu(c) }}</span>
          </span>
          <span v-if="c.nonLus" class="pastille" :aria-label="t('reseau.messages.nonLus', { n: c.nonLus }, c.nonLus)">{{ c.nonLus }}</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.messages { display: grid; gap: var(--space-6); max-width: 44rem; }
.tete { display: grid; gap: var(--space-4); }
.tete h1, .tete p { margin: 0; }
.liste { list-style: none; margin: 0; padding: var(--space-2); display: grid; }
.ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3); border-radius: var(--radius-md); color: var(--ink); text-decoration: none; }
.ligne:hover { background: color-mix(in srgb, var(--ink) 5%, transparent); }
.ligne__texte { display: grid; flex: 1; min-width: 0; }
.ligne__haut { display: flex; justify-content: space-between; gap: var(--space-2); }
.ligne__haut time { color: var(--ink-muted); font-size: var(--type-body-sm-size); white-space: nowrap; }
.ligne__apercu { color: var(--ink-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ligne--nouveau .ligne__apercu { color: var(--ink); font-weight: 600; }
.pastille { min-width: 22px; height: 22px; padding: 0 6px; border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.75rem; font-weight: 700; display: grid; place-items: center; }
</style>
