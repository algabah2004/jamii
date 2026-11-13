<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import { adresseConnexionGithub } from './api/client'
import ReglagesAffichage from './components/ReglagesAffichage.vue'
import { chargerSession, seDeconnecter, useSession } from './session'
import { JAvatar, JBouton, JSquelette } from './design-system'
import { arreterActivite, suivreActivite, useActivite } from './features/reseau/activite'

const { t } = useI18n()
const { etat, moi } = useSession()

// Les pastilles : relues toutes les 30 s tant qu'on est connecté et que l'onglet est visible (features/reseau/activite.ts).
const activite = useActivite()
// Toute nouveauté (message, invitation, demande de contact) crée une notification : une seule pastille suffit.
const nonLus = computed(() => activite.value?.notificationsNonLues ?? 0)
watch(etat, (e) => (e === 'connecte' ? suivreActivite() : arreterActivite()), { immediate: true })
</script>

<template>
  <a href="#contenu" class="aller-au-contenu">{{ t('menu.allerAuContenu') }}</a>
  <header class="entete" :class="{ 'entete--scene zone-sombre': $route.name === 'accueil', 'entete--connecte': etat === 'connecte' }">
    <div class="entete__interieur verre">
      <RouterLink to="/" class="marque">Jamii<span class="marque__point" aria-hidden="true" /></RouterLink>
      <nav :aria-label="t('menu.principal')">
        <RouterLink v-if="etat === 'connecte'" to="/cercles">{{ t('menu.cercles') }}</RouterLink>
        <RouterLink v-if="etat === 'connecte'" to="/communautes">{{ t('menu.communautes') }}</RouterLink>
        <RouterLink v-if="etat === 'connecte'" to="/demandes/nouvelle" :class="{ 'router-link-active': $route.path.startsWith('/demandes') }">{{ t('menu.demandes') }}</RouterLink>
        <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
        <RouterLink to="/systalink" :aria-label="t('menu.systalink')">
          <span class="lien-long" aria-hidden="true">{{ t('menu.systalink') }}</span><span class="lien-court" aria-hidden="true">{{ t('menu.systalinkCourt') }}</span>
        </RouterLink>
      </nav>
      <div class="actions">
        <ReglagesAffichage class="reglages--entete" />
        <a v-if="etat === 'non_connecte'" :href="adresseConnexionGithub($route.fullPath)" class="j-bouton j-bouton--principal bouton-entete">
          {{ t('connexion.github') }}
        </a>
        <template v-else-if="etat === 'connecte' && moi">
          <!-- Le profil se reconnaît à son visage : l'avatar mène au profil, le nom reste lisible par tous. -->
          <RouterLink to="/notifications" class="icone-entete" :class="{ 'router-link-active': ['/messages', '/contacts'].some((p) => $route.path.startsWith(p)) }" data-test="lien-activite" :aria-label="t('reseau.onglets.lien', { n: nonLus }, nonLus)">
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round" /></svg>
            <span v-if="nonLus" class="icone-entete__pastille" aria-hidden="true">{{ nonLus > 9 ? '9+' : nonLus }}</span>
          </RouterLink>
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

  <footer class="pied motif motif--ondes motif--haut" :class="{ 'pied--reglages': etat === 'connecte' }">
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
#app { min-height: 100vh; display: flex; flex-direction: column; overflow-x: clip; }
.entete__interieur, main, .pied__interieur { width: 100%; max-width: 72rem; margin-inline: auto; }
main, .pied__interieur { padding-inline: var(--space-6); }
@media (max-width: 720px) { main, .pied__interieur { padding-inline: var(--space-4); } }

.aller-au-contenu { position: absolute; left: var(--space-4); top: -4rem; z-index: 20; padding: var(--space-2) var(--space-4); background: var(--action); color: var(--on-action); border-radius: var(--radius-md); font-weight: 600; }
.aller-au-contenu:focus { top: var(--space-2); }

.entete { position: sticky; top: 0; z-index: 10; padding: var(--space-3) var(--space-4) 0; pointer-events: none; }
/* La capsule de navigation : du verre, parce que le contenu défile dessous (couche d'interface). */
.entete__interieur {
  pointer-events: auto;
  display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-8);
  min-height: 56px; max-width: 70rem;
  padding-inline: var(--space-6) var(--space-2);
  border-radius: var(--radius-pill);
}
.entete nav { flex: 1; display: flex; align-items: center; gap: var(--space-1); white-space: nowrap; overflow-x: auto; }

/* Les liens sont des capsules ; la page active est une goutte de verre teintée. */
.entete nav a {
  display: flex; align-items: center; min-height: 40px; padding: 0 var(--space-4); border-radius: var(--radius-pill);
  color: var(--ink-muted); text-decoration: none; font-weight: 600;
  transition: color var(--motion-fast) var(--ease-out), background var(--motion-fast) var(--ease-out);
}
.entete nav a:hover { color: var(--ink); background: color-mix(in srgb, var(--ink) 6%, transparent); }
.entete nav a.router-link-active { color: var(--ink); background: color-mix(in srgb, var(--action) 16%, transparent); }
/* Le menu défile sur mobile : l'anneau de focus passe à l'intérieur pour ne pas être rogné. */
.entete nav a:focus-visible { outline-offset: -2px; }

.marque { display: inline-flex; align-items: baseline; gap: 2px; font-family: var(--font-display); font-weight: 700; font-size: 1.5rem; letter-spacing: -0.5px; color: var(--ink); text-decoration: none; }
/* Le point orange : la couleur de la preuve, la signature de la marque. */
.marque__point { width: 0.3em; height: 0.3em; border-radius: 50%; background: var(--proof); }

.actions { display: flex; align-items: center; gap: var(--space-2); }
/* Libellé long sur ordinateur, court sur téléphone (le nom accessible reste le libellé complet). */
.lien-court { display: none; }
/* Sur ordinateur, la langue et le thème sont dans l'en-tête ; sur téléphone, dans le pied de page. */
.pied .reglages--pied { display: none; }
.bouton-entete { min-height: 36px; white-space: nowrap; }
.moi { display: inline-flex; align-items: center; gap: var(--space-2); padding: 2px var(--space-3) 2px 2px; border: 1px solid var(--line); border-radius: var(--radius-pill); color: var(--ink); text-decoration: none; font-weight: 600; font-size: var(--type-body-sm-size); }
.moi:hover, .moi.router-link-active { border-color: var(--action); }
.icone-entete { position: relative; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; color: var(--ink); }
.icone-entete:hover, .icone-entete.router-link-active { background: color-mix(in srgb, var(--action) 16%, transparent); }
/* La pastille orange : quelque chose de neuf vous attend. */
.icone-entete__pastille { position: absolute; top: 2px; right: 0; min-width: 18px; height: 18px; padding: 0 4px; border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.6875rem; font-weight: 700; line-height: 18px; text-align: center; box-shadow: 0 0 0 2px var(--surface); }
.deconnexion { background: none; border: 0; padding: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); cursor: pointer; white-space: nowrap; }
.deconnexion:hover { color: var(--ink); text-decoration: underline; }

/* Sur l'accueil, la capsule prend les couleurs sombres de la scène (classe zone-sombre). */
.entete.entete--scene { background: transparent; }
/* Sur la scène, les liens passent au-dessus de lumières vives : texte plus franc. */
.entete--scene { --ink-muted: color-mix(in srgb, var(--ink) 88%, transparent); }

/* Connecté, le menu a 6 entrées et l'icône des messages : la langue et le thème passent dans le pied de page,
   quelle que soit la largeur (la capsule de l'en-tête ne dépasse jamais 70rem). */
.entete--connecte .reglages--entete { display: none; }
.pied--reglages .reglages--pied { display: flex; }
/* Entre tablette et grand écran, « Espace Systalink » se raccourcit et les réglages passent aussi en bas. */
@media (max-width: 1180px) {
  .entete nav a { padding: 0 var(--space-3); }
  .lien-long { display: none; }
  .lien-court { display: inline; }
  .entete .reglages--entete { display: none; }
  .pied .reglages--pied { display: flex; }
}
/* Écran étroit : la marque et les réglages sur la première ligne, le menu entier sur la seconde. */
@media (max-width: 960px) {
  .entete { padding: var(--space-2) var(--space-2) 0; }
  .entete__interieur { padding-block: var(--space-2); padding-inline: var(--space-4) var(--space-2); border-radius: var(--radius-lg); }
  .actions { margin-inline-start: auto; }
  .entete nav { order: 3; flex: 1 1 100%; min-height: 40px; gap: 0; scrollbar-width: none; }
  .entete nav a { padding: 0 var(--space-2); font-size: var(--type-body-sm-size); }
  .lien-long { display: none; }
  .lien-court { display: inline; }
  .entete .reglages--entete { display: none; }
  .pied .reglages--pied { display: flex; }
  .moi__nom { display: none; }
  .moi { padding: 2px; }
}
/* Téléphone : les 6 entrées du menu passent sur deux lignes plutôt que d'en cacher une hors de l'écran. */
@media (max-width: 480px) {
  .entete nav { flex-wrap: wrap; justify-content: center; row-gap: 2px; padding-block: 2px; }
  .entete nav a { min-height: 36px; }
}

main { flex: 1; padding-block: var(--space-8) var(--space-12); }
main:focus { outline: none; }
.page-enter-active { transition: opacity var(--motion-base) var(--ease-out), transform calc(var(--motion-base) * 1.4) var(--ease-out); }
.page-leave-active { transition: opacity var(--motion-fast) var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(10px); }
.page-leave-to { opacity: 0; }
.hors-ligne { display: grid; gap: var(--space-3); justify-items: start; }

.pied { border-top: 1px solid var(--line); background: color-mix(in srgb, var(--surface) 70%, transparent); }
.pied__interieur { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); align-items: flex-start; padding-block: var(--space-8); }
.pied__marque { flex: 1 1 18rem; display: grid; gap: var(--space-2); }
.pied__marque p { margin: 0; color: var(--ink-muted); max-width: 28rem; }
.pied nav { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-6); }
.pied nav a { color: var(--ink); text-decoration: none; font-weight: 600; }
.pied nav a:hover { color: var(--action-ink); text-decoration: underline; }
.pied__cadev { flex-basis: 100%; margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
