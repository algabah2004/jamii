<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { adresseConnexionGithub } from '../../api/client'
import { JBouton, JChamp } from '../../design-system'
import { useSession } from '../../session'
import { cheminSur, demanderLien, erreurGithub, type ResultatDemande } from './connexion'

/*
 * Se connecter : avec GitHub (les développeurs), ou avec un lien reçu par e-mail (une PME, une association,
 * toute personne sans compte GitHub). L'API renvoie aussi ici quand la connexion GitHub échoue (`?erreur=…`).
 */
const { t } = useI18n()
const route = useRoute()
const routeur = useRouter()
const { etat } = useSession()

const retour = computed(() => cheminSur(route.query.retour))
const erreur = computed(() => erreurGithub(route.query.erreur))

// Déjà connecté (par exemple en revenant en arrière) : rien à faire ici.
watchEffect(() => {
  if (etat.value === 'connecte') void routeur.replace(retour.value)
})

const email = ref('')
const envoi = ref(false)
const resultat = ref<ResultatDemande | null>(null)

async function envoyer() {
  if (envoi.value) return
  envoi.value = true
  resultat.value = await demanderLien(email.value, retour.value)
  envoi.value = false
}

function autreAdresse() {
  resultat.value = null
}
</script>

<template>
  <section class="connexion" aria-labelledby="titre-connexion">
    <div class="carte bloc">
      <h1 id="titre-connexion">{{ t('connexion.titre') }}</h1>

      <p v-if="erreur" role="alert" class="alerte" data-test="erreur-github">{{ t(`connexion.erreurs.${erreur}`) }}</p>

      <div class="voie">
        <h2>{{ t('connexion.githubTitre') }}</h2>
        <p class="texte-discret">{{ t('connexion.githubTexte') }}</p>
        <a :href="adresseConnexionGithub(retour)" class="j-bouton j-bouton--principal" data-test="connexion-github">{{ t('connexion.github') }}</a>
      </div>

      <p class="separateur" aria-hidden="true"><span>{{ t('connexion.ou') }}</span></p>

      <div class="voie">
        <h2>{{ t('connexion.emailTitre') }}</h2>

        <div v-if="resultat?.etat === 'envoye'" role="status" class="envoye" data-test="lien-envoye">
          <p>{{ t('connexion.envoye', { email: email.trim() }) }}</p>
          <p class="texte-discret">{{ t('connexion.envoyeAide') }}</p>
          <JBouton variante="secondaire" @click="autreAdresse">{{ t('connexion.autreAdresse') }}</JBouton>
        </div>

        <form v-else class="formulaire" data-test="formulaire-email" @submit.prevent="envoyer">
          <p class="texte-discret">{{ t('connexion.emailTexte') }}</p>
          <JChamp
            v-model="email"
            type="email"
            :libelle="t('connexion.email')"
            :maxlength="254"
            :erreur="resultat?.etat === 'invalide' ? t('connexion.emailInvalide') : undefined"
            requis
          />
          <p v-if="resultat?.etat === 'trop'" role="alert" class="alerte">{{ t('connexion.trop', { n: resultat.minutes }, resultat.minutes) }}</p>
          <p v-else-if="resultat?.etat === 'indisponible'" role="alert" class="alerte">{{ t('etat.indisponible') }}</p>
          <JBouton type="submit" variante="secondaire" :occupe="envoi" data-test="envoyer-lien">{{ t('connexion.recevoirLien') }}</JBouton>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.connexion { max-width: 34rem; margin: var(--space-8) auto; }
.connexion .bloc { display: grid; gap: var(--space-6); }
.connexion h1 { margin: 0; }
.voie { display: grid; gap: var(--space-3); justify-items: start; }
.voie h2 { margin: 0; font-size: 1.125rem; }
.voie p { margin: 0; }
.formulaire, .envoye { display: grid; gap: var(--space-3); justify-items: start; width: 100%; }
.formulaire :deep(.j-champ) { width: 100%; }
.separateur { display: flex; align-items: center; gap: var(--space-3); margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.separateur::before, .separateur::after { content: ''; flex: 1; border-top: 1px solid var(--line); }
.alerte { margin: 0; color: var(--danger); font-weight: 600; }
</style>
