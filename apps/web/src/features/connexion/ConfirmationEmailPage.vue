<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import MotifsCoins from '../../components/MotifsCoins.vue'
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
  <section class="confirmation avec-motifs" aria-labelledby="titre-confirmation">
    <MotifsCoins planche="communautes" />
    <div class="confirmation__cadre">
      <!-- Le même bandeau que la page de connexion : on reconnaît la suite du parcours. -->
      <div class="bandeau" aria-hidden="true">
        <span class="bandeau__motif" />
        <p class="bandeau__logo">Jamii<span class="bandeau__point" /></p>
      </div>
      <div class="confirmation__corps">
        <span class="confirmation__icone" :class="{ 'confirmation__icone--echec': resultat === 'lien_invalide' }" aria-hidden="true">
          <svg v-if="resultat === 'lien_invalide'" viewBox="0 0 24 24"><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /><path d="m4 4 16 16" /></svg>
          <svg v-else viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m4 7 8 6 8-6" /></svg>
        </span>
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
      </div>
    </div>
  </section>
</template>

<style scoped>
.confirmation { display: grid; place-items: center; min-height: calc(100dvh - 9rem); padding: var(--space-8) 0; }
.confirmation__cadre {
  width: 100%; max-width: 30rem; overflow: hidden;
  border: 1px solid var(--line); border-radius: 28px; background: var(--surface);
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.06), 0 24px 60px rgb(2 7 10 / 0.12);
}
.bandeau {
  position: relative; isolation: isolate; padding: var(--space-6) clamp(1.5rem, 5vw, 2.5rem) var(--space-8); color: #EEF4F7;
  background:
    radial-gradient(18rem 10rem at 110% -20%, color-mix(in srgb, var(--proof) 45%, transparent), transparent 70%),
    radial-gradient(20rem 12rem at -10% 140%, color-mix(in srgb, var(--action) 55%, transparent), transparent 70%),
    #02070A;
}
.bandeau__motif {
  position: absolute; inset: 0; z-index: -1; background: #EEF4F7; opacity: 0.07;
  -webkit-mask: url('../../../../../docs/design/motifs/reseau.svg') 0 0 / 96px 96px repeat;
  mask: url('../../../../../docs/design/motifs/reseau.svg') 0 0 / 96px 96px repeat;
}
.bandeau__logo { margin: 0; font-family: var(--font-display); font-size: 1.5rem; font-weight: 700; letter-spacing: -0.02em; }
.bandeau__point { display: inline-block; width: 0.32em; height: 0.32em; margin-inline-start: 2px; border-radius: 50%; background: var(--proof); }
.confirmation__corps { display: grid; gap: var(--space-4); justify-items: start; padding: 0 clamp(1.5rem, 5vw, 2.5rem) clamp(1.5rem, 5vw, 2.5rem); }
.confirmation__icone {
  display: grid; place-items: center; width: 56px; height: 56px; margin-top: -28px; border: 4px solid var(--surface); border-radius: 50%;
  background: var(--action); color: var(--on-action);
}
.confirmation__icone--echec { background: var(--danger); color: #fff; }
.confirmation__icone svg { width: 24px; height: 24px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.confirmation h1 { margin: 0; font-family: var(--font-display); font-size: 1.5rem; letter-spacing: -0.02em; }
.confirmation__corps p { margin: 0; color: var(--ink-muted); line-height: 1.55; }
.confirmation__corps .alerte { color: var(--danger); font-weight: 600; }
</style>
