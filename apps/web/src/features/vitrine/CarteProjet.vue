<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { JAvatar, JBadgePreuve } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { ilYa, lienSur, type ProjetEnVitrine } from './vitrine'

/**
 * Un projet en ligne, tel que la vitrine et l'accueil le montrent. Le badge orange dit ce qui est prouvé
 * (l'adresse a répondu), le lien s'ouvre dans un nouvel onglet sans lui donner la main sur Jamii.
 * Pas de bouton bleu par carte : la charte réserve le bleu à l'action principale de l'écran.
 */
withDefaults(defineProps<{ projet: ProjetEnVitrine; niveau?: 2 | 3 }>(), { niveau: 2 })
const { t, locale } = useI18n()
</script>

<template>
  <article class="projet">
    <div class="projet__haut">
      <JBadgePreuve v-if="projet.hebergeur === 'datacloud'" data-test="badge-systalink">✓ {{ t('vitrine.hebergeSurSystalink') }}</JBadgePreuve>
      <JBadgePreuve v-else data-test="badge-en-ligne">✓ {{ t('profil.projets.enLigne') }}</JBadgePreuve>
      <span v-if="projet.origine === 'demande'" class="etiquette">{{ t('vitrine.pourUnePme') }}</span>
    </div>

    <component :is="`h${niveau}`" class="projet__titre">{{ projet.titre }}</component>
    <p class="projet__accroche">{{ projet.accroche }}</p>

    <div class="projet__equipe">
      <span class="projet__visages" aria-hidden="true">
        <JAvatar v-for="m in projet.equipe.slice(0, 4)" :key="m.id" :nom="m.nom" :url="m.avatarUrl" :taille="28" />
      </span>
      <p>
        {{ t('vitrine.equipe', { noms: projet.equipe.map((m) => m.nom).join(', ') }) }}
        <br />
        <span class="projet__pays">{{ projet.paysEquipe.map((code) => nomPays(code, locale)).join(' · ') }}</span>
      </p>
    </div>

    <div class="projet__pied">
      <a
        v-if="lienSur(projet.url)"
        :href="lienSur(projet.url)!"
        target="_blank"
        rel="noopener noreferrer nofollow ugc"
        class="projet__lien"
      >
        {{ t('vitrine.voir') }}<span aria-hidden="true"> ↗</span><span class="visuellement-cache"> ({{ t('vitrine.nouvelOnglet') }})</span>
      </a>
      <span class="projet__verifie">{{ t('vitrine.verifie', { quand: ilYa(projet.verifieLe, locale) }) }}</span>
    </div>
  </article>
</template>

<style scoped>
.projet {
  height: 100%;
  display: flex; flex-direction: column; gap: var(--space-3);
  padding: var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
  transition: border-color var(--motion-fast) var(--ease-out);
}
.projet:hover { border-color: var(--line-strong); }
.projet__haut { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.projet__titre { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.projet__accroche {
  margin: 0; color: var(--ink-muted);
  display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.projet__equipe { display: flex; gap: var(--space-3); align-items: center; margin-top: auto; padding-top: var(--space-2); }
.projet__equipe p { margin: 0; font-size: var(--type-body-sm-size); line-height: var(--type-body-sm-line); }
.projet__pays { color: var(--ink-muted); }
.projet__visages { display: flex; flex-shrink: 0; }
/* Les visages se chevauchent, cerclés de la couleur de la carte pour rester distincts. */
.projet__visages > :deep(*) { box-shadow: 0 0 0 2px var(--surface); border-radius: 50%; }
.projet__visages > :deep(* + *) { margin-inline-start: -6px; }
.projet__pied { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); align-items: baseline; justify-content: space-between; padding-top: var(--space-3); border-top: 1px solid var(--line); }
.projet__lien { font-weight: 600; text-decoration: none; }
.projet__lien:hover { text-decoration: underline; }
.projet__verifie { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
