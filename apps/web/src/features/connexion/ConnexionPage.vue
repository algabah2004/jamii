<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { adresseConnexionGithub, adresseConnexionGoogle } from '../../api/client'
import { JBouton, JChamp } from '../../design-system'
import { useSession } from '../../session'
import { cheminSur, demanderLien, erreurGithub, lireFournisseurs, type ResultatDemande } from './connexion'

/*
 * Se connecter : avec GitHub (les développeurs), ou avec Google ou un lien reçu par e-mail (une PME, une association,
 * toute personne sans compte GitHub). Google n'apparaît que si le serveur l'a configuré (#260). L'API renvoie aussi
 * ici quand une connexion échoue (`?erreur=…`).
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

const google = ref(false)
onMounted(async () => {
  google.value = (await lireFournisseurs()).google
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

        <a v-if="google" :href="adresseConnexionGoogle(retour)" class="google" data-test="connexion-google">
          <svg viewBox="0 0 48 48" width="20" height="20" aria-hidden="true">
            <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.1C12.5 13.6 17.8 9.5 24 9.5z" />
            <path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.3-4.6 7l7.2 5.6c4.2-3.9 7.1-9.6 7.1-17.1z" />
            <path fill="#FBBC05" d="M10.6 28.6c-.5-1.4-.8-2.9-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C1 16.6 0 20.2 0 24s1 7.4 2.7 10.7l7.9-6.1z" />
            <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.2-5.6c-2 1.4-4.7 2.3-8.7 2.3-6.2 0-11.5-4.1-13.4-9.9l-7.9 6.1C6.6 42.6 14.6 48 24 48z" />
          </svg>
          {{ t('connexion.google') }}
        </a>
        <p v-if="google" class="texte-discret">{{ t('connexion.googleOu') }}</p>

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
/* Le bouton Google suit les consignes de Google : fond clair, logo en couleurs, texte « Continuer avec Google ». */
.google {
  display: inline-flex; align-items: center; gap: var(--space-3); min-height: 44px; padding: 0 var(--space-5, 1.25rem);
  border: 1px solid #747775; border-radius: var(--radius-pill); background: #fff; color: #1f1f1f;
  font-weight: 600; text-decoration: none;
}
.google:hover { background: #f2f2f2; }
.google:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
