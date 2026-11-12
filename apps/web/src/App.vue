<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import { adresseConnexionGithub } from './api/client'
import ReglagesAffichage from './components/ReglagesAffichage.vue'
import { chargerSession, seDeconnecter, useSession } from './session'
import { JAvatar, JBouton, JSquelette } from './design-system'

const { t } = useI18n()
const { etat, moi } = useSession()
</script>

<template>
  <a href="#contenu" class="aller-au-contenu">{{ t('menu.allerAuContenu') }}</a>
  <header class="entete">
    <div class="entete__interieur">
      <RouterLink to="/" class="marque">Jamii<span class="marque__point" aria-hidden="true" /></RouterLink>
      <nav :aria-label="t('menu.principal')">
        <RouterLink v-if="etat === 'connecte'" to="/communautes">{{ t('menu.communautes') }}</RouterLink>
        <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
        <RouterLink to="/systalink">{{ t('menu.systalink') }}</RouterLink>
      </nav>
      <div class="actions">
        <ReglagesAffichage class="reglages--entete" />
        <a v-if="etat === 'non_connecte'" :href="adresseConnexionGithub($route.fullPath)" class="j-bouton j-bouton--principal bouton-entete">
          {{ t('connexion.github') }}
        </a>
        <template v-else-if="etat === 'connecte' && moi">
          <!-- Le profil se reconnaît à son visage : l'avatar mène au profil, le nom reste lisible par tous. -->
          <RouterLink to="/profil" class="moi" data-test="mon-profil">
            <JAvatar :nom="moi.nom" :url="moi.avatarUrl" :taille="32" />
            <span class="moi__nom">{{ t('menu.profil') }}</span>
          </RouterLink>
          <button type="button" class="deconnexion" @click="seDeconnecter">{{ t('connexion.deconnexion') }}</button>
        </template>
      </div>
    </div>
  </header>

  <main id="contenu" tabindex="-1">
    <JSquelette v-if="etat === 'chargement'" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="carte hors-ligne">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="chargerSession">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <!-- Passage d'une page à l'autre : un fondu court, sans rien remonter quand seul le filtre change. -->
    <RouterView v-else v-slot="{ Component, route: courante }">
      <Transition name="page" mode="out-in">
        <component :is="Component" :key="courante.path" />
      </Transition>
    </RouterView>
  </main>

  <footer class="pied">
    <div class="pied__interieur">
      <div class="pied__marque">
        <RouterLink to="/" class="marque">Jamii<span class="marque__point" aria-hidden="true" /></RouterLink>
        <p>{{ t('pied.mission') }}</p>
      </div>
      <nav :aria-label="t('pied.liens')">
        <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
        <RouterLink to="/systalink">{{ t('menu.systalink') }}</RouterLink>
        <RouterLink to="/design-system">{{ t('pied.charte') }}</RouterLink>
      </nav>
      <ReglagesAffichage class="reglages--pied" />
      <p class="pied__cadev">{{ t('pied.cadev') }}</p>
    </div>
  </footer>
</template>

<style>
/* Le cadre commun : en-tête, contenu et pied alignés sur la même largeur (variables du design system, #19). */
#app { min-height: 100vh; display: flex; flex-direction: column; }
.entete__interieur, main, .pied__interieur { width: 100%; max-width: 72rem; margin-inline: auto; padding-inline: var(--space-6); }
@media (max-width: 720px) { .entete__interieur, main, .pied__interieur { padding-inline: var(--space-4); } }

.aller-au-contenu { position: absolute; left: var(--space-4); top: -4rem; z-index: 20; padding: var(--space-2) var(--space-4); background: var(--action); color: var(--on-action); border-radius: var(--radius-md); font-weight: 600; }
.aller-au-contenu:focus { top: var(--space-2); }

.entete {
  position: sticky; top: 0; z-index: 10;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: saturate(1.4) blur(10px);
  border-bottom: 1px solid var(--line);
}
.entete__interieur { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-8); min-height: 64px; }
.entete nav { flex: 1; display: flex; gap: var(--space-6); white-space: nowrap; overflow-x: auto; align-self: stretch; }
.entete nav a {
  display: flex; align-items: center;
  color: var(--ink-muted); text-decoration: none; font-weight: 600;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  transition: color var(--motion-fast) var(--ease-out);
}
.entete nav a:hover { color: var(--ink); }
.entete nav a.router-link-active { color: var(--ink); border-bottom-color: var(--action); }
/* Le menu défile sur mobile : l'anneau de focus passe à l'intérieur pour ne pas être rogné. */
.entete nav a:focus-visible { outline-offset: -2px; }

.marque { display: inline-flex; align-items: baseline; gap: 2px; font-family: var(--font-display); font-weight: 700; font-size: 1.5rem; letter-spacing: -0.5px; color: var(--ink); text-decoration: none; }
/* Le point orange : la couleur de la preuve, la signature de la marque. */
.marque__point { width: 0.3em; height: 0.3em; border-radius: 50%; background: var(--proof); }

.actions { display: flex; align-items: center; gap: var(--space-2); }
/* Sur ordinateur, la langue et le thème sont dans l'en-tête ; sur téléphone, dans le pied de page. */
.pied .reglages--pied { display: none; }
.bouton-entete { min-height: 36px; white-space: nowrap; }
.moi { display: inline-flex; align-items: center; gap: var(--space-2); padding: 2px var(--space-3) 2px 2px; border: 1px solid var(--line); border-radius: var(--radius-pill); color: var(--ink); text-decoration: none; font-weight: 600; font-size: var(--type-body-sm-size); }
.moi:hover, .moi.router-link-active { border-color: var(--action); }
.deconnexion { background: none; border: 0; padding: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); cursor: pointer; white-space: nowrap; }
.deconnexion:hover { color: var(--ink); text-decoration: underline; }

/* Écran étroit : la marque et les réglages sur la première ligne, le menu entier sur la seconde. */
@media (max-width: 720px) {
  .entete__interieur { padding-block: var(--space-2); }
  .actions { margin-inline-start: auto; }
  .entete nav { order: 3; flex: 1 1 100%; min-height: 40px; gap: var(--space-4); scrollbar-width: none; }
  .entete .reglages--entete { display: none; }
  .pied .reglages--pied { display: flex; }
  .moi__nom { display: none; }
  .moi { padding: 2px; }
}

main { flex: 1; padding-block: var(--space-8) var(--space-12); }
main:focus { outline: none; }
.page-enter-active, .page-leave-active { transition: opacity var(--motion-fast) var(--ease-out), transform var(--motion-base) var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; }
.hors-ligne { display: grid; gap: var(--space-3); justify-items: start; }

.pied { border-top: 1px solid var(--line); background: var(--surface); }
.pied__interieur { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); align-items: flex-start; padding-block: var(--space-8); }
.pied__marque { flex: 1 1 18rem; display: grid; gap: var(--space-2); }
.pied__marque p { margin: 0; color: var(--ink-muted); max-width: 28rem; }
.pied nav { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-6); }
.pied nav a { color: var(--ink); text-decoration: none; font-weight: 600; }
.pied nav a:hover { color: var(--action-ink); text-decoration: underline; }
.pied__cadev { flex-basis: 100%; margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
