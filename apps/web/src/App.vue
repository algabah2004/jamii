<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import { adresseConnexionGithub } from './api/client'
import { LANGUES, type Langue } from './i18n'
import { chargerSession, seDeconnecter, useSession } from './session'

const { t, locale } = useI18n()
const { etat, moi } = useSession()

function changerLangue(langue: Langue) {
  locale.value = langue
  document.documentElement.lang = langue
}
</script>

<template>
  <header class="entete">
    <RouterLink to="/" class="marque">Jamii</RouterLink>
    <nav :aria-label="t('menu.principal')">
      <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
      <RouterLink to="/systalink">{{ t('menu.systalink') }}</RouterLink>
      <RouterLink v-if="etat === 'connecte'" to="/profil">{{ t('menu.profil') }}</RouterLink>
    </nav>
    <div class="actions">
      <label>
        <span class="visuellement-cache">{{ t('langue') }}</span>
        <select :value="locale" @change="changerLangue(($event.target as HTMLSelectElement).value as Langue)">
          <option v-for="l in LANGUES" :key="l" :value="l">{{ l.toUpperCase() }}</option>
        </select>
      </label>
      <a v-if="etat === 'non_connecte'" :href="adresseConnexionGithub($route.fullPath)" class="bouton">
        {{ t('connexion.github') }}
      </a>
      <button v-else-if="etat === 'connecte' && moi" type="button" @click="seDeconnecter">
        {{ t('connexion.deconnexion') }}
      </button>
    </div>
  </header>

  <main>
    <p v-if="etat === 'chargement'" role="status">{{ t('etat.chargement') }}</p>
    <div v-else-if="etat === 'indisponible'" role="alert">
      <p>{{ t('etat.indisponible') }}</p>
      <button type="button" @click="chargerSession">{{ t('etat.reessayer') }}</button>
    </div>
    <RouterView v-else />
  </main>
</template>

<style>
/* Mise en page minimale : le design system (@rev22rse22) la remplacera dans src/design-system. */
body { margin: 0; font-family: system-ui, sans-serif; background: #f4f8fa; color: #02070a; }
.entete { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; padding: 0.75rem 1rem; background: #fff; border-bottom: 1px solid #d3dee5; }
.entete nav { flex: 1; display: flex; gap: 1rem; white-space: nowrap; }
.marque { font-weight: 700; font-size: 1.25rem; color: inherit; text-decoration: none; }
.actions { display: flex; align-items: center; gap: 0.5rem; }
.bouton { background: #0297de; color: #02070a; padding: 0.5rem 0.75rem; border-radius: 0.375rem; text-decoration: none; font-weight: 600; }
main { max-width: 60rem; margin: 0 auto; padding: 1.5rem 1rem; }
.visuellement-cache { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
