<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { JBouton } from '../../design-system'
import { chargerSession } from '../../session'
import { cheminSur, confirmerLien, type ResultatConfirmation } from './connexion'

/*
 * La page du lien reçu par e-mail (`/connexion/email?jeton=…`). La connexion se fait sur un clic, jamais à
 * l'ouverture : les antivirus de messagerie ouvrent les liens à l'avance, ils ne doivent pas consommer le jeton
 * (contracts/socle.yaml). Le jeton est retiré de l'adresse dès l'arrivée : il ne reste ni dans l'historique, ni
 * dans un en-tête Referer.
 */
const { t } = useI18n()
const route = useRoute()
const routeur = useRouter()

const jeton = typeof route.query.jeton === 'string' ? route.query.jeton : ''
const retour = cheminSur(route.query.retour)

const envoi = ref(false)
const resultat = ref<ResultatConfirmation | null>(jeton ? null : 'lien_invalide')

onMounted(() => {
  if (route.query.jeton !== undefined) void routeur.replace({ path: route.path })
})

async function confirmer() {
  if (envoi.value) return
  envoi.value = true
  resultat.value = await confirmerLien(jeton)
  if (resultat.value === 'connecte') {
    await chargerSession()
    await routeur.replace(retour)
  }
  envoi.value = false
}
</script>

<template>
  <section class="confirmation carte bloc" aria-labelledby="titre-confirmation">
    <template v-if="resultat === 'lien_invalide'">
      <h1 id="titre-confirmation">{{ t('connexion.lienInvalideTitre') }}</h1>
      <p role="alert" data-test="lien-invalide">{{ t('connexion.lienInvalide') }}</p>
      <RouterLink :to="{ path: '/connexion', query: retour === '/' ? {} : { retour } }" class="j-bouton j-bouton--principal">
        {{ t('connexion.nouveauLien') }}
      </RouterLink>
    </template>
    <template v-else>
      <h1 id="titre-confirmation">{{ t('connexion.confirmerTitre') }}</h1>
      <p>{{ t('connexion.confirmerTexte') }}</p>
      <p v-if="resultat === 'indisponible'" role="alert" class="alerte">{{ t('etat.indisponible') }}</p>
      <JBouton :occupe="envoi" data-test="confirmer-lien" @click="confirmer">{{ t('connexion.confirmer') }}</JBouton>
    </template>
  </section>
</template>

<style scoped>
.confirmation { max-width: 34rem; margin: var(--space-8) auto; display: grid; gap: var(--space-4); justify-items: start; }
.confirmation h1, .confirmation p { margin: 0; }
.alerte { color: var(--danger); font-weight: 600; }
</style>
