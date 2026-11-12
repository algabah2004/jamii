<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { adresseConnexionGithub, api } from '../api/client'
import { JBadgePreuve, JIllustration, JSquelette } from '../design-system'
import CarteProjet from '../features/vitrine/CarteProjet.vue'
import type { ProjetEnVitrine } from '../features/vitrine/vitrine'
import { useSession } from '../session'

const { t } = useI18n()
const { etat, moi } = useSession()
const route = useRoute()
// Après une redirection du garde (« connexion requise »), on revient là où la personne voulait aller.
const retour = typeof route.query.retour === 'string' ? route.query.retour : '/profil'

/** Le parcours d'un développeur sur Jamii : c'est une suite, d'où la numérotation. */
const ETAPES = [
  { cle: 'profil', illustration: 'profil-prouve', lien: '/profil' },
  { cle: 'equipe', illustration: 'equipe', lien: '/communautes' },
  { cle: 'enLigne', illustration: 'en-ligne', lien: '/vitrine' },
] as const

// La preuve dès l'accueil : de vrais projets en ligne, lus dans la vitrine publique.
// Si la vitrine ne répond pas ou est vide, le bandeau disparaît : l'accueil reste utilisable.
const projets = ref<ProjetEnVitrine[]>([])
const vitrine = ref<'chargement' | 'pret' | 'absente'>('chargement')
async function chargerVitrine() {
  try {
    const { data } = await api.GET('/api/vitrine', { params: { query: { limite: 3 } } })
    projets.value = data?.elements ?? []
    vitrine.value = projets.value.length ? 'pret' : 'absente'
  } catch {
    vitrine.value = 'absente'
  }
}
void chargerVitrine()
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

    <section v-if="vitrine !== 'absente'" class="direct zone-sombre" aria-labelledby="direct-titre" data-test="direct">
      <div class="direct__texte">
        <div class="direct__titre">
          <JBadgePreuve>✓ {{ t('accueil.principe.badge') }}</JBadgePreuve>
          <h2 id="direct-titre">{{ t('accueil.direct.titre') }}</h2>
        </div>
        <div class="direct__principe">
          <p>{{ t('accueil.principe.texte') }}</p>
          <RouterLink to="/vitrine" class="direct__lien">{{ t('accueil.direct.lien') }}</RouterLink>
        </div>
      </div>
      <JSquelette v-if="vitrine === 'chargement'" forme="cartes" :nombre="3" class="direct__projets" />
      <ul v-else class="direct__projets">
        <li v-for="p in projets" :key="p.projetId"><CarteProjet :projet="p" :niveau="3" /></li>
      </ul>
    </section>

    <section class="parcours" aria-labelledby="parcours-titre">
      <h2 id="parcours-titre">{{ t('accueil.piliers') }}</h2>
      <ol class="etapes">
        <li v-for="e in ETAPES" :key="e.cle" class="etape">
          <JIllustration :nom="e.illustration" class="etape__illustration" />
          <h3>{{ t(`accueil.pilier.${e.cle}.titre`) }}</h3>
          <p>{{ t(`accueil.pilier.${e.cle}.texte`) }}</p>
          <RouterLink :to="e.lien" class="etape__lien">{{ t(`accueil.pilier.${e.cle}.lien`) }}</RouterLink>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.accueil { display: grid; gap: var(--space-12); }

.hero { display: grid; gap: var(--space-8); align-items: center; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); padding-top: var(--space-4); }
.hero__texte { display: grid; gap: var(--space-4); justify-items: start; }
.hero__bonjour { margin: 0; color: var(--action-ink); font-weight: 600; }
.hero__titre {
  margin: 0;
  font-size: clamp(2.5rem, 6.4vw, 4.25rem);
  line-height: 1.02;
  letter-spacing: -0.035em;
  animation: entree var(--motion-celebrate) var(--ease-out) both;
}
.hero__intro { margin: 0; font-size: 1.1875rem; line-height: 1.55; color: var(--ink-muted); max-width: 36rem; }
.hero__actions { display: flex; flex-wrap: wrap; gap: var(--space-3); padding-top: var(--space-2); }
@media (max-width: 760px) {
  .hero { grid-template-columns: 1fr; }
  .hero__illustration { order: -1; max-width: 18rem; }
}

/* Le seul moment fort de la page : un bandeau sombre où l'orange prouve ce qu'il promet. */
.direct {
  display: grid; gap: var(--space-8);
  padding: var(--space-8);
  border: 1px solid var(--line); border-radius: var(--radius-lg);
}
.direct__texte { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-12); align-items: flex-end; justify-content: space-between; }
.direct__titre { display: grid; gap: var(--space-3); justify-items: start; }
.direct__titre h2 { margin: 0; font-size: clamp(1.75rem, 3.4vw, 2.25rem); line-height: 1.1; letter-spacing: -0.6px; }
.direct__principe { flex: 0 1 30rem; display: grid; gap: var(--space-2); justify-items: start; }
.direct__principe p { margin: 0; color: var(--ink-muted); }
.direct__lien { font-weight: 600; color: var(--action-ink); }
.direct__projets { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 960px) { .direct { padding: var(--space-6); } .direct__projets { grid-template-columns: 1fr; } }

.parcours h2 { margin-bottom: var(--space-6); }
.etapes { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-8); grid-template-columns: repeat(3, minmax(0, 1fr)); counter-reset: etape; }
.etape { display: grid; gap: var(--space-2); align-content: start; justify-items: start; counter-increment: etape; position: relative; }
.etape__illustration { width: 100%; max-width: 13rem; margin-bottom: var(--space-2); }
.etape h3 { margin: 0; display: flex; gap: var(--space-2); align-items: baseline; }
/* Le numéro de l'étape, en orange comme la preuve qu'on gagne à chaque étape. */
.etape h3::before { content: counter(etape); color: var(--proof-ink); font-variant-numeric: tabular-nums; }
.etape p { margin: 0; color: var(--ink-muted); }
.etape__lien { font-weight: 600; margin-top: var(--space-1); }
@media (max-width: 760px) { .etapes { grid-template-columns: 1fr; } .etape__illustration { max-width: 10rem; } }

@keyframes entree { from { opacity: 0; transform: translateY(12px); } }
</style>
