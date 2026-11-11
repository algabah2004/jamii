<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import { adresseConnexionGithub } from './api/client'
import { LANGUES, type Langue } from './i18n'
import { chargerSession, seDeconnecter, useSession } from './session'
import { choisirTheme, themeActuel, type Theme } from './design-system'

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
      <RouterLink v-if="etat === 'connecte'" to="/communautes">{{ t('menu.communautes') }}</RouterLink>
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
      <label>
        <span class="visuellement-cache">{{ t('theme') }}</span>
        <select :value="themeActuel" data-test="theme" @change="choisirTheme(($event.target as HTMLSelectElement).value as Theme)">
          <option value="systeme">◐ {{ t('themeCourt.systeme') }}</option>
          <option value="clair">☀ {{ t('themeCourt.clair') }}</option>
          <option value="sombre">☾ {{ t('themeCourt.sombre') }}</option>
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
/* L'en-tête et la mise en page, sur les variables du design system (src/design-system, #19). */
.entete {
  display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--surface); border-bottom: 1px solid var(--line);
}
.entete nav { flex: 1; display: flex; gap: var(--space-4); white-space: nowrap; overflow-x: auto; }
.entete nav a { color: var(--ink); text-decoration: none; font-weight: 600; padding-block: var(--space-1); border-bottom: 2px solid transparent; }
.entete nav a.router-link-active { border-bottom-color: var(--action); }
.marque { font-family: var(--font-display); font-weight: 700; font-size: var(--type-h3-size); color: var(--ink); text-decoration: none; }
.actions { display: flex; align-items: center; gap: var(--space-2); }
.actions button, .actions .bouton { white-space: nowrap; }
/* Le menu défile sur mobile : l'anneau de focus passe à l'intérieur pour ne pas être rogné. */
.entete nav a:focus-visible { outline-offset: -2px; }
.actions select, .actions button {
  min-height: 36px; padding: 0 var(--space-3);
  background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); cursor: pointer;
}
.bouton {
  display: inline-flex; align-items: center; min-height: 36px; padding: 0 var(--space-4);
  background: var(--action); color: var(--on-action); border-radius: var(--radius-md);
  text-decoration: none; font-weight: var(--type-label-weight);
}
main { max-width: 60rem; margin: 0 auto; padding: var(--space-6) var(--space-4); }
</style>
