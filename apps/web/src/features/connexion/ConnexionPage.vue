<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { adresseConnexionGithub, adresseConnexionGoogle } from '../../api/client'
import MotifsCoins from '../../components/MotifsCoins.vue'
import { JBouton, JChamp } from '../../design-system'
import { useSession } from '../../session'
import { cheminSur, demanderLien, erreurGithub, lireFournisseurs, type ResultatDemande } from './connexion'

/*
 * Se connecter : avec GitHub (les développeurs), ou avec Google ou un lien reçu par e-mail (une PME, une association,
 * toute personne sans compte GitHub). Google n'apparaît que si le serveur l'a configuré (#260). L'API renvoie aussi
 * ici quand une connexion échoue (`?erreur=…`).
 */
const { t, tm, rt } = useI18n()
/** Ce qu'on fait sur Jamii, en trois lignes, à côté des boutons de connexion. */
const points = computed(() => (tm('connexion.points') as unknown[]).map((p) => rt(p as Parameters<typeof rt>[0])))
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
  <section class="connexion avec-motifs" aria-labelledby="titre-connexion">
    <MotifsCoins planche="communautes" />
    <div class="connexion__cadre">
      <!-- Ce qu'est Jamii : on sait où l'on entre avant de choisir comment. -->
      <div class="presentation">
        <p class="presentation__marque" aria-hidden="true">Jamii<span class="presentation__point" /></p>
        <h1 id="titre-connexion" class="presentation__titre">{{ t('connexion.bienvenue') }}</h1>
        <p class="presentation__accroche">{{ t('connexion.accroche') }}</p>
        <ul class="presentation__points">
          <li v-for="(p, i) in points" :key="p"><span class="puce" :class="`puce--${i}`" aria-hidden="true" />{{ p }}</li>
        </ul>
      </div>

      <div class="acces">
        <p v-if="erreur" role="alert" class="alerte" data-test="erreur-github">{{ t(`connexion.erreurs.${erreur}`) }}</p>

        <!-- Les développeurs : GitHub, d'un clic. -->
        <div class="voie">
          <h2 class="voie__titre">{{ t('connexion.githubTitre') }}</h2>
          <a :href="adresseConnexionGithub(retour)" class="bouton-large bouton-github" data-test="connexion-github">
            <svg viewBox="0 0 16 16" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" /></svg>
            {{ t('connexion.github') }}
          </a>
          <p class="voie__aide">{{ t('connexion.githubTexte') }}</p>
        </div>

        <p class="separateur" aria-hidden="true"><span>{{ t('connexion.ou') }}</span></p>

        <!-- Tout le monde : Google si le serveur le permet, sinon un lien par e-mail. Jamais de mot de passe. -->
        <div class="voie">
          <h2 class="voie__titre">{{ t('connexion.emailTitre') }}</h2>
          <p class="voie__aide">{{ t('connexion.emailTexte') }}</p>

          <a v-if="google" :href="adresseConnexionGoogle(retour)" class="bouton-large bouton-google" data-test="connexion-google">
            <svg viewBox="0 0 48 48" width="20" height="20" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.1C12.5 13.6 17.8 9.5 24 9.5z" />
              <path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.3-4.6 7l7.2 5.6c4.2-3.9 7.1-9.6 7.1-17.1z" />
              <path fill="#FBBC05" d="M10.6 28.6c-.5-1.4-.8-2.9-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C1 16.6 0 20.2 0 24s1 7.4 2.7 10.7l7.9-6.1z" />
              <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.2-5.6c-2 1.4-4.7 2.3-8.7 2.3-6.2 0-11.5-4.1-13.4-9.9l-7.9 6.1C6.6 42.6 14.6 48 24 48z" />
            </svg>
            {{ t('connexion.google') }}
          </a>
          <p v-if="google" class="sous-separateur">{{ t('connexion.googleOu') }}</p>

          <div v-if="resultat?.etat === 'envoye'" role="status" class="envoye" data-test="lien-envoye">
            <span class="envoye__icone" aria-hidden="true">✓</span>
            <div>
              <p class="envoye__texte">{{ t('connexion.envoye', { email: email.trim() }) }}</p>
              <p class="voie__aide">{{ t('connexion.envoyeAide') }}</p>
            </div>
            <JBouton variante="secondaire" @click="autreAdresse">{{ t('connexion.autreAdresse') }}</JBouton>
          </div>

          <form v-else class="formulaire" data-test="formulaire-email" @submit.prevent="envoyer">
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
            <JBouton type="submit" variante="principal" :occupe="envoi" class="formulaire__envoyer" data-test="envoyer-lien">{{ t('connexion.recevoirLien') }}</JBouton>
          </form>
        </div>

        <p class="rassurance">{{ t('connexion.rassurance') }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.connexion { display: grid; place-items: center; min-height: calc(100dvh - 9rem); padding: var(--space-8) 0; }
.connexion__cadre {
  display: grid; width: 100%; max-width: 64rem; overflow: hidden;
  border: 1px solid var(--line); border-radius: 28px; background: var(--surface);
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.06), 0 24px 60px rgb(2 7 10 / 0.12);
}
@media (min-width: 900px) { .connexion__cadre { grid-template-columns: 1fr 1.1fr; } }

/* À gauche : Jamii, sur un fond profond, avec un halo des deux couleurs de la marque. */
.presentation {
  position: relative; display: grid; align-content: center; gap: var(--space-4); padding: clamp(2rem, 5vw, 3.5rem);
  color: #EEF4F7; isolation: isolate;
  background:
    radial-gradient(28rem 20rem at 110% -10%, color-mix(in srgb, var(--proof) 45%, transparent), transparent 70%),
    radial-gradient(30rem 22rem at -20% 120%, color-mix(in srgb, var(--action) 55%, transparent), transparent 70%),
    #02070A;
}
.presentation__marque { margin: 0; font-family: var(--font-display); font-size: 1.5rem; font-weight: 700; letter-spacing: -0.02em; }
.presentation__point { display: inline-block; width: 0.32em; height: 0.32em; margin-inline-start: 2px; border-radius: 50%; background: var(--proof); }
.presentation__titre { margin: var(--space-4) 0 0; font-family: var(--font-display); font-size: clamp(2rem, 4vw, 2.75rem); line-height: 1.08; letter-spacing: -0.03em; }
.presentation__accroche { margin: 0; max-width: 26rem; color: #B9C9D3; font-size: 1.0625rem; line-height: 1.55; }
.presentation__points { list-style: none; margin: var(--space-4) 0 0; padding: 0; display: grid; gap: var(--space-3); }
.presentation__points li { display: flex; align-items: center; gap: var(--space-3); line-height: 1.4; }
.puce { flex: none; width: 10px; height: 10px; border-radius: 50%; background: var(--action); box-shadow: 0 0 0 4px color-mix(in srgb, var(--action) 25%, transparent); }
.puce--1 { background: var(--proof); box-shadow: 0 0 0 4px color-mix(in srgb, var(--proof) 25%, transparent); }
.puce--2 { background: #7BC86C; box-shadow: 0 0 0 4px rgb(123 200 108 / 0.25); }

/* À droite : les deux façons d'entrer, des boutons pleine largeur. */
.acces { display: grid; align-content: center; gap: var(--space-6); padding: clamp(1.5rem, 5vw, 3.5rem); }
.voie { display: grid; gap: var(--space-3); }
.voie__titre { margin: 0; font-size: 1.0625rem; }
.voie__aide { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); line-height: 1.5; }
.bouton-large {
  display: flex; align-items: center; justify-content: center; gap: var(--space-3); width: 100%; min-height: 48px; padding: 0 var(--space-4);
  border-radius: var(--radius-pill); font: inherit; font-weight: 700; text-decoration: none;
  transition: transform var(--motion-fast, 120ms) var(--ease-out, ease-out), background var(--motion-fast, 120ms) var(--ease-out, ease-out);
}
.bouton-large:active { transform: translateY(1px); }
.bouton-large:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; }
.bouton-github { background: var(--ink); color: var(--surface); }
.bouton-github:hover { background: color-mix(in srgb, var(--ink) 85%, var(--action)); }
/* Le bouton Google suit les consignes de Google : fond clair, logo en couleurs, texte « Continuer avec Google ». */
.bouton-google { border: 1px solid #747775; background: #fff; color: #1f1f1f; }
.bouton-google:hover { background: #f2f2f2; }
.separateur, .sous-separateur { display: flex; align-items: center; gap: var(--space-3); margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.separateur::before, .separateur::after, .sous-separateur::before, .sous-separateur::after { content: ''; flex: 1; border-top: 1px solid var(--line); }
.separateur span { padding: 2px 10px; border: 1px solid var(--line); border-radius: var(--radius-pill); text-transform: uppercase; letter-spacing: 0.06em; font-size: 0.75rem; font-weight: 700; }
.formulaire { display: grid; gap: var(--space-3); }
.formulaire :deep(.j-champ) { width: 100%; }
.formulaire__envoyer { width: 100%; justify-content: center; min-height: 48px; }
.envoye { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-3); padding: var(--space-4); border-radius: var(--radius-md); background: var(--action-soft); }
.envoye > :last-child { grid-column: 1 / -1; justify-self: start; }
.envoye__icone { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--action); color: var(--on-action); font-weight: 800; }
.envoye__texte { margin: 0 0 var(--space-1); font-weight: 600; }
.alerte { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: color-mix(in srgb, var(--danger) 10%, transparent); color: var(--danger); font-weight: 600; }
.rassurance { margin: 0; color: var(--ink-muted); font-size: 0.8125rem; text-align: center; }
@media (prefers-reduced-motion: reduce) { .bouton-large { transition: none; } }
</style>
