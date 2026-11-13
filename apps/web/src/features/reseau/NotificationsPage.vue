<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { rafraichirActivite } from './activite'
import OngletsReseau from './OngletsReseau.vue'
import { lireNotifications, quand, toutMarquerLu, type Notification } from './reseau'

/* Ce qui s'est passé : on montre la liste telle qu'elle était (les nouvelles en avant), puis tout passe à « lu ». */
const { t, locale } = useI18n()
const notifications = ref<Notification[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    notifications.value = await lireNotifications()
    etat.value = 'pret'
    if (notifications.value.some((n) => !n.lue)) {
      await toutMarquerLu()
      void rafraichirActivite()
    }
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

const phrase = (n: Notification) => t(`reseau.notifications.${n.type}`, { nom: n.personne?.nom ?? '', cercle: n.cercle?.nom ?? '' })
</script>

<template>
  <section class="reseau-page">
    <header class="tete">
      <h1>{{ t('reseau.onglets.notifications') }}</h1>
      <OngletsReseau />
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <div v-else-if="!notifications.length" class="etat-vide" data-test="notifications-vide">
      <p>{{ t('reseau.notifications.vide') }}</p>
      <RouterLink to="/cercles" class="j-bouton j-bouton--principal">{{ t('menu.cercles') }}</RouterLink>
    </div>
    <ul v-else class="liste carte" data-test="notifications">
      <li v-for="n in notifications" :key="n.id">
        <RouterLink :to="n.lien" class="ligne" :class="{ 'ligne--nouvelle': !n.lue }">
          <JAvatar v-if="n.personne" :nom="n.personne.nom" :url="n.personne.avatarUrl" :taille="40" />
          <span v-else class="ligne__icone" aria-hidden="true">◎</span>
          <span class="ligne__texte">{{ phrase(n) }}</span>
          <time :datetime="n.creeLe">{{ quand(n.creeLe, locale) }}</time>
          <span v-if="!n.lue" class="visuellement-cache">{{ t('reseau.notifications.nouvelle') }}</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.reseau-page { display: grid; gap: var(--space-6); max-width: 44rem; }
.tete { display: grid; gap: var(--space-4); }
.tete h1 { margin: 0; }
.liste { list-style: none; margin: 0; padding: var(--space-2); display: grid; }
.ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3); border-radius: var(--radius-md); color: var(--ink); text-decoration: none; }
.ligne:hover { background: color-mix(in srgb, var(--ink) 5%, transparent); }
.ligne__texte { flex: 1; min-width: 0; }
.ligne time { color: var(--ink-muted); font-size: var(--type-body-sm-size); white-space: nowrap; }
/* Une nouvelle notification : un point orange et un fond à peine teinté. */
.ligne--nouvelle { background: color-mix(in srgb, var(--action) 8%, transparent); font-weight: 600; }
.ligne--nouvelle::after { content: ''; width: 8px; height: 8px; border-radius: 50%; background: var(--proof); flex: none; }
.ligne__icone { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: var(--action-soft); color: var(--action-ink); }
</style>
