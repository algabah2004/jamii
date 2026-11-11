<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { adresseConnexionGithub } from '../api/client'
import { JIllustration } from '../design-system'
import { useSession } from '../session'

const { t } = useI18n()
const { etat, moi } = useSession()
const route = useRoute()
// Après une redirection du garde (« connexion requise »), on revient là où la personne voulait aller.
const retour = typeof route.query.retour === 'string' ? route.query.retour : '/profil'

const PILIERS = [
  { cle: 'profil', illustration: 'profil-prouve', lien: '/profil' },
  { cle: 'equipe', illustration: 'equipe', lien: '/communautes' },
  { cle: 'enLigne', illustration: 'en-ligne', lien: '/vitrine' },
] as const
</script>

<template>
  <div class="accueil">
    <section class="hero">
      <div class="hero__texte">
        <p v-if="moi" class="hero__bonjour">{{ t('accueil.bienvenue', { nom: moi.nom }) }}</p>
        <h1 class="hero__titre">{{ t('accueil.titre') }}</h1>
        <p class="hero__intro">{{ t('accueil.texte') }}</p>
        <div class="hero__actions">
          <template v-if="etat === 'connecte'">
            <RouterLink to="/profil" class="j-bouton j-bouton--principal">{{ t('accueil.voirProfil') }}</RouterLink>
            <RouterLink to="/communautes" class="j-bouton j-bouton--secondaire">{{ t('accueil.voirCommunautes') }}</RouterLink>
          </template>
          <template v-else>
            <a :href="adresseConnexionGithub(retour)" class="j-bouton j-bouton--principal" data-test="connexion">
              {{ t('connexion.github') }}
            </a>
            <RouterLink to="/vitrine" class="j-bouton j-bouton--secondaire">{{ t('accueil.voirVitrine') }}</RouterLink>
          </template>
        </div>
        <p class="texte-discret">{{ t('accueil.rassurance') }}</p>
      </div>
      <JIllustration nom="equipe" class="hero__illustration" />
    </section>

    <section class="piliers" :aria-label="t('accueil.piliers')">
      <RouterLink v-for="p in PILIERS" :key="p.cle" :to="p.lien" class="carte pilier">
        <JIllustration :nom="p.illustration" class="pilier__illustration" />
        <h2>{{ t(`accueil.pilier.${p.cle}.titre`) }}</h2>
        <p>{{ t(`accueil.pilier.${p.cle}.texte`) }}</p>
        <span class="pilier__lien">{{ t(`accueil.pilier.${p.cle}.lien`) }} →</span>
      </RouterLink>
    </section>

    <section class="principe carte">
      <span class="j-preuve">✓ {{ t('accueil.principe.badge') }}</span>
      <p>{{ t('accueil.principe.texte') }}</p>
    </section>
  </div>
</template>

<style scoped>
.accueil { display: grid; gap: var(--space-12); }
.hero { display: grid; gap: var(--space-8); align-items: center; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); padding-top: var(--space-6); }
@media (max-width: 760px) { .hero { grid-template-columns: 1fr; } .hero__illustration { max-width: 22rem; margin-inline: auto; } }
.hero__texte { display: grid; gap: var(--space-4); justify-items: start; }
.hero__bonjour { margin: 0; color: var(--action-ink); font-weight: 600; }
.hero__titre {
  margin: 0;
  font-size: clamp(2.25rem, 6vw, var(--type-display-size));
  line-height: 1.07;
  letter-spacing: -1px;
  animation: entree var(--motion-base) var(--ease-out) both;
}
.hero__intro { margin: 0; font-size: 1.125rem; color: var(--ink-muted); max-width: 34rem; }
.hero__actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.piliers { display: grid; gap: var(--space-4); grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 760px) { .piliers { grid-template-columns: 1fr; } }
.pilier {
  display: grid; gap: var(--space-3); align-content: start;
  color: var(--ink); text-decoration: none;
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.pilier:hover { transform: translateY(-2px); border-color: var(--line-strong); }
.pilier h2 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.pilier p { margin: 0; color: var(--ink-muted); }
.pilier__illustration { max-width: 12rem; }
.pilier__lien { color: var(--action-ink); font-weight: 600; }
.principe { display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: center; background: var(--proof-soft); border-color: transparent; }
.principe p { margin: 0; flex: 1 1 20rem; }
@keyframes entree { from { opacity: 0; transform: translateY(8px); } }
</style>
