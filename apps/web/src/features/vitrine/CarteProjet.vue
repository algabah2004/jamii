<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { JAvatar, JBadgePreuve } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { ilYa, initiales, lienSur, teinte, type ProjetEnVitrine } from './vitrine'

/**
 * Un projet en ligne, tel que la vitrine et l'accueil le montrent. Ordre de lecture : ce que c'est (titre,
 * accroche), ce qui est prouvé (badge orange : l'adresse a répondu), qui l'a fait, puis le lien.
 * Le lien couvre toute la carte et s'ouvre dans un nouvel onglet sans lui donner la main sur Jamii.
 * Pas de bouton bleu par carte : la charte réserve le bleu à l'action principale de l'écran.
 */
withDefaults(defineProps<{ projet: ProjetEnVitrine; niveau?: 2 | 3; illustration?: boolean }>(), { niveau: 2, illustration: false })
const { t, locale } = useI18n()
</script>

<template>
  <article class="projet" :class="{ 'projet--cliquable': lienSur(projet.url) }">
    <div class="projet__entete">
      <span v-if="illustration" class="projet__monogramme" :class="`projet__monogramme--${teinte(projet.projetId)}`" aria-hidden="true">{{ initiales(projet.titre) }}</span>
      <component :is="`h${niveau}`" class="projet__titre">{{ projet.titre }}</component>
    </div>
    <p class="projet__accroche">{{ projet.accroche }}</p>

    <div class="projet__preuves">
      <JBadgePreuve v-if="projet.hebergeur === 'datacloud'" data-test="badge-systalink">✓ {{ t('vitrine.hebergeSurSystalink') }}</JBadgePreuve>
      <JBadgePreuve v-else data-test="badge-en-ligne">✓ {{ t('profil.projets.enLigne') }}</JBadgePreuve>
      <span v-if="projet.origine === 'demande'" class="etiquette">{{ t('vitrine.pourUnePme') }}</span>
      <span v-else-if="projet.origine === 'cercle'" class="etiquette" data-test="etiquette-tontine">{{ t('vitrine.neDansUneTontine') }}</span>
    </div>

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
      <span class="projet__verifie"><i aria-hidden="true" />{{ t('vitrine.verifie', { quand: ilYa(projet.verifieLe, locale) }) }}</span>
      <a
        v-if="lienSur(projet.url)"
        :href="lienSur(projet.url)!"
        target="_blank"
        rel="noopener noreferrer nofollow ugc"
        class="projet__lien"
      >
        {{ t('vitrine.voir') }}<span aria-hidden="true" class="projet__fleche"> ↗</span><span class="visuellement-cache"> ({{ t('vitrine.nouvelOnglet') }})</span>
      </a>
    </div>
  </article>
</template>

<style scoped>
.projet {
  position: relative;
  height: 100%;
  display: flex; flex-direction: column; gap: var(--space-4);
  padding: var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
  transition: border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.projet:hover { border-color: var(--line-strong); }
.projet--cliquable:hover, .projet--cliquable:focus-within { border-color: var(--action); }
.projet--cliquable:hover { transform: translateY(-2px); }
@media (prefers-reduced-motion: reduce) { .projet--cliquable:hover { transform: none; } }

.projet__entete { display: flex; gap: var(--space-4); align-items: center; }
.projet__monogramme {
  display: grid; place-items: center; flex-shrink: 0;
  width: 52px; height: 52px; border-radius: 16px;
  font: 700 1.25rem var(--font-display); letter-spacing: -.02em;
}
.projet__monogramme--action { background: var(--action-soft); color: var(--action-ink); }
.projet__monogramme--preuve { background: var(--proof-soft); color: var(--proof-ink); }
.projet__titre { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); overflow-wrap: anywhere; }
.projet__accroche {
  margin: 0; color: var(--ink-muted);
  display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.projet__preuves { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }

.projet__equipe { display: flex; gap: var(--space-3); align-items: center; margin-top: auto; }
.projet__equipe p { margin: 0; font-size: var(--type-body-sm-size); line-height: var(--type-body-sm-line); }
.projet__pays { color: var(--ink-muted); }
.projet__visages { display: flex; flex-shrink: 0; }
/* Les visages se chevauchent, cerclés de la couleur de la carte pour rester distincts. */
.projet__visages > :deep(*) { box-shadow: 0 0 0 2px var(--surface); border-radius: 50%; }
.projet__visages > :deep(* + *) { margin-inline-start: -6px; }

.projet__pied { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); align-items: center; justify-content: space-between; padding-top: var(--space-4); border-top: 1px solid var(--line); }
.projet__verifie { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
/* Le point vert de présence : l'adresse répond. */
.projet__verifie i { width: 8px; height: 8px; border-radius: 50%; background: var(--en-ligne); box-shadow: 0 0 0 3px color-mix(in srgb, var(--en-ligne) 25%, transparent); }
.projet__lien { font-weight: 600; text-decoration: none; }
/* Toute la carte ouvre le projet : la zone du lien s'étend à la carte, sans second lien pour les lecteurs d'écran. */
.projet__lien::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.projet__lien:focus-visible { outline: none; }
.projet__lien:focus-visible::after { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: var(--radius-lg); }
.projet__fleche { display: inline-block; transition: transform var(--motion-fast) var(--ease-out); }
.projet--cliquable:hover .projet__fleche { transform: translate(2px, -2px); }
</style>
