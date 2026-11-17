<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import ReglagesAffichage from './components/ReglagesAffichage.vue'
import BoutonLangue from './components/BoutonLangue.vue'
import BoutonTheme from './components/BoutonTheme.vue'
import { centrerActif } from './components/rangeeDefilante'
import { chargerSession, seDeconnecter, useSession } from './session'
import { JAvatar, JBouton, JSquelette } from './design-system'
import { arreterActivite, suivreActivite, useActivite } from './features/reseau/activite'
import { arreterAppelsEntrants, suivreAppelsEntrants } from './features/reseau/appel'
import FenetreAppel from './features/reseau/FenetreAppel.vue'
import FenetreAppelCercle from './features/cercles/FenetreAppelCercle.vue'

const { t } = useI18n()
const { etat, moi } = useSession()

// Les pastilles : relues toutes les 30 s tant qu'on est connecté et que l'onglet est visible (features/reseau/activite.ts).
const activite = useActivite()
// Messages et notifications ont chacun leur accès et leur compteur.
const messagesNonLus = computed(() => activite.value?.messagesNonLus ?? 0)
const nonLus = computed(() => activite.value?.notificationsNonLues ?? 0)
watch(etat, (e) => (e === 'connecte' ? suivreActivite() : arreterActivite()), { immediate: true })
// Les appels (#157) : on demande toutes les 2 s si quelqu'un appelle, sur n'importe quelle page (features/reseau/appel.ts).
watch(etat, (e) => (e === 'connecte' ? suivreAppelsEntrants() : arreterAppelsEntrants()), { immediate: true })

// Sur téléphone, le menu défile sur une ligne : la page active est ramenée au centre, sans toucher au défilement de la page.
const route = useRoute()
const menu = ref<HTMLElement | null>(null)
centrerActif(menu, 'a.router-link-active', [() => route.path, etat])
</script>

<template>
  <a href="#contenu" class="aller-au-contenu">{{ t('menu.allerAuContenu') }}</a>
  <!-- La capsule de navigation est sombre sur toutes les pages, comme sur l'accueil (zone-sombre), dans les deux thèmes :
       un repère stable en haut de l'écran, qui tranche sur le contenu clair. -->
  <header class="entete entete--scene zone-sombre" :class="{ 'entete--connecte': etat === 'connecte' }">
    <div class="entete__interieur verre">
      <RouterLink to="/" class="marque">Jamii<span class="marque__point" aria-hidden="true" /></RouterLink>
      <nav ref="menu" :aria-label="t('menu.principal')">
        <RouterLink v-if="etat === 'connecte'" to="/communautes">{{ t('menu.communautes') }}</RouterLink>
        <RouterLink v-if="etat === 'connecte'" to="/cercles">{{ t('menu.cercles') }}</RouterLink>
        <RouterLink v-if="etat === 'connecte'" to="/demandes" :class="{ 'router-link-active': $route.path.startsWith('/demandes') }">{{ t('menu.demandes') }}</RouterLink>
        <RouterLink to="/missions">{{ t('menu.missions') }}</RouterLink>
        <RouterLink to="/hackathons">{{ t('menu.hackathons') }}</RouterLink>
        <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
        <RouterLink to="/systalink" :aria-label="t('menu.systalink')">
          <span class="lien-long" aria-hidden="true">{{ t('menu.systalink') }}</span><span class="lien-court" aria-hidden="true">{{ t('menu.systalinkCourt') }}</span>
        </RouterLink>
      </nav>
      <div class="actions">
        <!-- La langue et le thème en un clic, connecté ou non. Le choix « Système » reste dans le pied de page. -->
        <BoutonLangue />
        <BoutonTheme />
        <RouterLink
          v-if="etat === 'non_connecte' && !$route.path.startsWith('/connexion')"
          :to="{ path: '/connexion', query: $route.path === '/' ? {} : { retour: $route.fullPath } }"
          class="j-bouton j-bouton--principal bouton-entete"
          data-test="lien-connexion"
        >
          {{ t('connexion.seConnecter') }}
        </RouterLink>
        <template v-else-if="etat === 'connecte' && moi">
          <!-- Le profil se reconnaît à son visage : l'avatar mène au profil, le nom reste lisible par tous. -->
          <RouterLink to="/messages" class="acces-messages icone-entete" :class="{ 'router-link-active': $route.path.startsWith('/messages') }" data-test="lien-messages" :aria-label="t('reseau.messages.lien', { n: messagesNonLus }, messagesNonLus)" :title="t('reseau.messages.titre')">
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6 4V6a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M7 9h10M7 13h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
            <span class="acces-messages__texte">{{ t('reseau.messages.titre') }}</span>
            <span v-if="messagesNonLus" class="icone-entete__pastille" aria-hidden="true">{{ messagesNonLus > 9 ? '9+' : messagesNonLus }}</span>
          </RouterLink>
          <RouterLink to="/notifications" class="icone-entete" :class="{ 'router-link-active': $route.path.startsWith('/contacts') }" data-test="lien-activite" :aria-label="t('reseau.onglets.lien', { n: nonLus }, nonLus)">
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

  <main id="contenu" tabindex="-1" :class="{ 'main--pleine': $route.meta.pleineLargeur, 'main--messagerie': $route.meta.messagerie }">
    <JSquelette v-if="etat === 'chargement'" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="carte hors-ligne">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="chargerSession">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <!-- Passage d'une page à l'autre : un fondu court, sans rien remonter quand seul le filtre change. -->
    <RouterView v-else v-slot="{ Component, route: courante }">
      <Transition name="page" mode="out-in">
        <component :is="Component" :key="courante.meta.messagerie ? 'messagerie' : courante.path" />
      </Transition>
    </RouterView>
  </main>

  <footer v-if="!$route.meta.messagerie" class="pied motif motif--ondes motif--haut">
    <div class="pied__interieur">
      <div class="pied__marque">
        <RouterLink to="/" class="marque">Jamii<span class="marque__point" aria-hidden="true" /></RouterLink>
        <p>{{ t('pied.mission') }}</p>
      </div>
      <nav :aria-label="t('pied.liens')">
        <RouterLink to="/missions">{{ t('menu.missions') }}</RouterLink>
        <RouterLink to="/hackathons">{{ t('menu.hackathons') }}</RouterLink>
        <RouterLink to="/vitrine">{{ t('menu.vitrine') }}</RouterLink>
        <RouterLink to="/systalink">{{ t('menu.systalink') }}</RouterLink>
      </nav>
      <ReglagesAffichage class="reglages--pied" />
      <!-- Sur téléphone, « Se déconnecter » n'a plus la place dans l'en-tête : il vit ici, à côté des réglages. -->
      <button v-if="etat === 'connecte'" type="button" class="deconnexion pied__deconnexion" data-test="deconnexion-pied" @click="seDeconnecter">{{ t('connexion.deconnexion') }}</button>
      <p class="pied__cadev">{{ t('pied.cadev') }}</p>
    </div>
  </footer>

  <FenetreAppel v-if="etat === 'connecte'" />
  <FenetreAppelCercle v-if="etat === 'connecte'" />
</template>

<style>
/* Le cadre commun : en-tête, contenu et pied alignés sur la même largeur (variables du design system, #19). */
#app { min-height: 100vh; display: flex; flex-direction: column; overflow-x: clip; }
.entete__interieur, main, .pied__interieur { width: 100%; max-width: 72rem; margin-inline: auto; }
.entete__interieur { max-width: 84rem; }
main, .pied__interieur { padding-inline: var(--space-6); }
@media (max-width: 720px) { main, .pied__interieur { padding-inline: var(--space-4); } }
/* Les pages qui remplissent l'écran (l'espace d'une communauté) : toute la largeur, une petite marge. */
#app:has(.main--messagerie) { height: 100dvh; min-height: 0; }
main.main--messagerie { display: flex; flex-direction: column; min-height: 0; padding-block: var(--space-4); }
.acces-messages.icone-entete { width: auto; padding-inline: 12px; display: flex; gap: 8px; border: 1px solid var(--line); border-radius: var(--radius-pill); text-decoration: none; font-size: 0.8125rem; font-weight: 600; }
.acces-messages.icone-entete.router-link-active { background: var(--proof-soft); color: var(--proof-ink); border-color: color-mix(in srgb, var(--proof) 40%, var(--line)); }
@media (max-width: 480px) { .acces-messages__texte { display: none; } .acces-messages.icone-entete { width: 40px; padding: 0; justify-content: center; } }
main.main--pleine { max-width: none; padding-inline: var(--space-4); }

.aller-au-contenu { position: absolute; left: var(--space-4); top: -4rem; z-index: 20; padding: var(--space-2) var(--space-4); background: var(--action); color: var(--on-action); border-radius: var(--radius-md); font-weight: 600; }
.aller-au-contenu:focus { top: var(--space-2); }

.entete { position: sticky; top: 0; z-index: 10; padding: var(--space-3) var(--space-4) 0; pointer-events: none; }
/* La capsule de navigation : du verre, parce que le contenu défile dessous (couche d'interface). */
.entete__interieur {
  pointer-events: auto;
  display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-8);
  /* Huit entrées de menu depuis les missions et les hackathons : la capsule s'élargit pour qu'aucune ne soit coupée. */
  min-height: 56px; max-width: 84rem;
  padding-inline: var(--space-6) var(--space-2);
  border-radius: var(--radius-pill);
}
/* En sombre, la surface à 58 % se fond dans le fond : le verre prend un voile clair, un bord et un reflet plus
   nets, pour que la capsule se détache tout en laissant voir le motif flouté dessous. */
:root[data-theme='sombre'] .entete__interieur.verre,
.zone-sombre .entete__interieur.verre {
  background: color-mix(in srgb, white 9%, color-mix(in srgb, var(--surface-raised) 55%, transparent));
  border-color: color-mix(in srgb, white 16%, transparent);
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, white 22%, transparent),
    inset 0 -1px 0 color-mix(in srgb, black 30%, transparent),
    0 12px 40px color-mix(in srgb, black 45%, transparent);
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='clair']) .entete__interieur.verre {
    background: color-mix(in srgb, white 9%, color-mix(in srgb, var(--surface-raised) 55%, transparent));
    border-color: color-mix(in srgb, white 16%, transparent);
    box-shadow:
      inset 0 1px 0 color-mix(in srgb, white 22%, transparent),
      inset 0 -1px 0 color-mix(in srgb, black 30%, transparent),
      0 12px 40px color-mix(in srgb, black 45%, transparent);
  }
}
/* Transparence réduite ou contraste augmenté : une capsule pleine, comme le reste du verre. */
@media (prefers-reduced-transparency: reduce), (prefers-contrast: more) {
  :root .entete__interieur.verre { background: var(--surface-raised); }
}
.entete nav { flex: 1; display: flex; align-items: center; gap: var(--space-1); white-space: nowrap; overflow-x: auto; scrollbar-width: none; }
/* Pas de barre de défilement dans le menu : il se resserre avant de déborder (voir les paliers plus bas). */
.entete nav::-webkit-scrollbar { display: none; }

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
.bouton-entete { min-height: 36px; white-space: nowrap; }
.moi { display: inline-flex; align-items: center; gap: var(--space-2); padding: 2px var(--space-3) 2px 2px; border: 1px solid var(--line); border-radius: var(--radius-pill); color: var(--ink); text-decoration: none; font-weight: 600; font-size: var(--type-body-sm-size); }
.moi:hover, .moi.router-link-active { border-color: var(--action); }
.icone-entete { position: relative; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; color: var(--ink); }
.icone-entete:hover, .icone-entete.router-link-active { background: color-mix(in srgb, var(--action) 16%, transparent); }
/* La pastille orange : quelque chose de neuf vous attend. */
.icone-entete__pastille { position: absolute; top: 2px; right: 0; min-width: 18px; height: 18px; padding: 0 4px; border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.6875rem; font-weight: 700; line-height: 18px; text-align: center; box-shadow: 0 0 0 2px var(--surface); }
.deconnexion { background: none; border: 0; padding: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); cursor: pointer; white-space: nowrap; }
.deconnexion:hover { color: var(--ink); text-decoration: underline; }

/* La capsule prend les couleurs sombres de la scène (classe zone-sombre), sur toutes les pages. */
.entete.entete--scene { background: transparent; }
/* Sur la scène, les liens passent au-dessus de lumières vives : texte plus franc. */
.entete--scene { --ink-muted: color-mix(in srgb, var(--ink) 88%, transparent); }

/* Portable (jusqu'à 1600 px de large, soit la plupart des portables à 100 % ou 125 %) : le menu se resserre et
   « Espace Systalink » devient « Systalink », pour que tout tienne sans barre de défilement. */
@media (max-width: 1600px) {
  .entete__interieur { column-gap: var(--space-4); }
  .entete nav a { padding: 0 var(--space-3); }
  .lien-long { display: none; }
  .lien-court { display: inline; }
}
/* Entre tablette et grand écran, « Espace Systalink » se raccourcit. */
@media (max-width: 1180px) {
  .entete nav a { padding: 0 var(--space-3); }
  .lien-long { display: none; }
  .lien-court { display: inline; }
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
  .moi__nom { display: none; }
  .moi { padding: 2px; }
}
/* Écran étroit : le menu tient sur une seule ligne qui défile. Sur trois lignes, l'en-tête collant mangeait 30 % d'un
   téléphone. Un fondu sur les bords montre qu'il y a d'autres entrées, et la page active est amenée dans le champ. */
@media (max-width: 960px) {
  .entete nav {
    padding-inline: var(--space-3);
    -webkit-mask-image: linear-gradient(to right, transparent, #000 var(--space-3), #000 calc(100% - var(--space-6)), transparent);
    mask-image: linear-gradient(to right, transparent, #000 var(--space-3), #000 calc(100% - var(--space-6)), transparent);
  }
  .entete nav a:last-child { margin-inline-end: var(--space-4); }
}
/* Téléphone : la marque et les boutons tiennent sur une seule ligne. */
@media (max-width: 480px) {
  .entete__interieur { column-gap: var(--space-2); padding-inline: var(--space-3) var(--space-1); }
  .actions { gap: 2px; }
  /* Six boutons, la marque et les marges ne tiennent pas dans 320 px : « Se déconnecter » passe dans le pied de page. */
  .entete .deconnexion { display: none; }
  .pied .pied__deconnexion { display: inline-flex; }
  .entete nav { margin-inline: calc(-1 * var(--space-3)) calc(-1 * var(--space-1)); }
  .entete nav a { min-height: 40px; }
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
.pied__deconnexion { display: none; min-height: 44px; align-items: center; padding-inline: 0; color: var(--ink); font-weight: 600; text-decoration: underline; }
.pied__cadev { flex-basis: 100%; margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
