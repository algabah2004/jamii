<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { adresseConnexionGithub, api } from '../api/client'
import { JIllustration } from '../design-system'
import type { TableauDeBord } from '../features/systalink/calculs'
import CarteProjet from '../features/vitrine/CarteProjet.vue'
import type { ProjetEnVitrine } from '../features/vitrine/vitrine'
import { useSession } from '../session'

const { t, locale } = useI18n()
const { etat, moi } = useSession()
const route = useRoute()
// Après une redirection du garde (« connexion requise »), on revient là où la personne voulait aller.
const retour = typeof route.query.retour === 'string' ? route.query.retour : '/profil'

/** Le parcours d'un développeur sur Jamii : c'est une suite, d'où la numérotation. */
const ETAPES = [
  { cle: 'profil', illustration: 'profil-prouve', lien: '/profil' },
  { cle: 'equipe', illustration: 'communaute', lien: '/communautes' },
  { cle: 'enLigne', illustration: 'en-ligne', lien: '/vitrine' },
] as const

// La scène ne montre que des chiffres et des projets réels, lus dans les API publiques.
// Si l'une ne répond pas, sa partie disparaît : l'accueil reste utilisable.
const tableau = ref<TableauDeBord | null>(null)
const projet = ref<ProjetEnVitrine | null>(null)
void (async () => {
  try {
    const { data } = await api.GET('/api/systalink/tableau-de-bord', { params: { query: { periode: 'tout' } } })
    tableau.value = data ?? null
  } catch {
    tableau.value = null
  }
})()
void (async () => {
  try {
    const { data } = await api.GET('/api/vitrine', { params: { query: { limite: 1, hebergeur: 'datacloud' } } })
    projet.value = data?.elements?.[0] ?? null
  } catch {
    projet.value = null
  }
})()

const nombre = (n: number) => n.toLocaleString(locale.value)
const chiffres = computed(() => {
  const tb = tableau.value
  if (!tb) return null
  const pays = tb.inscrits.parPays.length
  return {
    gauche: [
      { valeur: nombre(tb.inscrits.total), libelle: t('accueil.scene.membres') },
      { valeur: tb.inscrits.autres > 0 ? `${pays}+` : String(pays), libelle: t('accueil.scene.pays') },
    ],
    droite: [
      { valeur: nombre(tb.projets.enLigne), libelle: t('accueil.scene.enLigne') },
      { valeur: nombre(tb.projets.surDatacloud), libelle: t('accueil.scene.surSystalink') },
    ],
  }
})
</script>

<template>
  <div class="accueil">
    <!-- La scène : le bleu de l'action descend vers l'orange de la preuve, là où sont posés les projets en ligne. -->
    <section class="scene zone-sombre" aria-labelledby="titre-accueil">
      <div class="scene__lumiere" aria-hidden="true" />
      <div class="scene__contenu">
        <p v-if="moi" class="scene__bonjour">{{ t('accueil.bienvenue', { nom: moi.nom }) }}</p>
        <h1 id="titre-accueil" class="scene__titre">
          {{ t('accueil.scene.titre1') }}<br />
          {{ t('accueil.scene.titre2') }} <em>{{ t('accueil.scene.titreAccent') }}</em>.
        </h1>
        <p class="scene__intro">{{ t('accueil.texte') }}</p>

        <dl v-if="chiffres" class="scene__chiffres scene__chiffres--gauche" data-test="chiffres">
          <div v-for="c in chiffres.gauche" :key="c.libelle"><dt>{{ c.libelle }}</dt><dd>{{ c.valeur }}</dd></div>
        </dl>
        <dl v-if="chiffres" class="scene__chiffres scene__chiffres--droite">
          <div v-for="c in chiffres.droite" :key="c.libelle"><dt>{{ c.libelle }}</dt><dd>{{ c.valeur }}</dd></div>
        </dl>

        <div class="scene__actions">
          <RouterLink v-if="etat === 'connecte'" to="/profil" class="bouton-rond">
            <span class="bouton-rond__point" aria-hidden="true" />{{ t('accueil.voirProfil') }}
          </RouterLink>
          <a v-else :href="adresseConnexionGithub(retour)" class="bouton-rond" data-test="connexion">
            <span class="bouton-rond__point" aria-hidden="true" />{{ t('accueil.scene.commencer') }}
          </a>
          <RouterLink :to="etat === 'connecte' ? '/communautes' : '/vitrine'" class="scene__lien">
            {{ etat === 'connecte' ? t('accueil.voirCommunautes') : t('accueil.voirVitrine') }}
          </RouterLink>
        </div>
        <p class="scene__rassurance">{{ t('accueil.rassurance') }}</p>
      </div>

      <!-- Trois aperçus de Jamii posés en bas de la scène, légèrement inclinés. -->
      <div class="apercus">
        <RouterLink to="/communautes" class="apercu apercu--gauche">
          <span class="apercu__etiquette">{{ t('accueil.scene.apercuCommunautes') }}</span>
          <span class="apercu__fenetre maquette" aria-hidden="true">
            <span v-for="(a, i) in ['KY', 'AD', 'CO']" :key="a" class="maquette__ligne">
              <span class="maquette__avatar">{{ a }}</span>
              <span class="maquette__textes"><i class="trait trait--fort" :style="{ width: `${78 - i * 14}%` }" /><i class="trait" :style="{ width: `${48 + i * 9}%` }" /></span>
              <span v-if="i === 2" class="maquette__preuve">✓</span>
            </span>
          </span>
        </RouterLink>
        <div class="apercu apercu--centre">
          <span class="apercu__etiquette">{{ t('accueil.scene.apercuEnLigne') }}</span>
          <CarteProjet v-if="projet" :projet="projet" :niveau="2" class="apercu__projet" />
          <span v-else class="apercu__fenetre maquette" aria-hidden="true"><span class="maquette__pastilles"><b>✓</b></span><i class="trait trait--fort" style="width: 70%" /><i class="trait" style="width: 90%" /><i class="trait" style="width: 60%" /></span>
        </div>
        <RouterLink to="/profil" class="apercu apercu--droite">
          <span class="apercu__etiquette">{{ t('accueil.scene.apercuProfil') }}</span>
          <span class="apercu__fenetre maquette" aria-hidden="true">
            <span class="maquette__ligne"><span class="maquette__avatar maquette__avatar--grand">AD</span><span class="maquette__textes"><i class="trait trait--fort" style="width: 62%" /><i class="trait" style="width: 40%" /></span></span>
            <span v-for="(l, i) in [72, 48, 30]" :key="i" class="maquette__jauge"><i :style="{ width: `${l}%` }" /></span>
            <span class="maquette__pastilles"><b>✓ 3</b><b>✓ 12</b><em /></span>
          </span>
        </RouterLink>
      </div>
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

/* La scène occupe toute la largeur et passe sous l'en-tête (rendu transparent sur l'accueil, App.vue). */
.scene {
  position: relative; isolation: isolate; overflow: hidden;
  margin-inline: calc(50% - 50vw);
  margin-top: calc(-1 * (64px + var(--space-8)));
  padding: calc(64px + var(--space-8)) var(--space-6) 0;
  background: var(--bg);
}
/* Des traits de lumière verticaux, révélés par deux halos : bleu au centre, orange en bas. */
.scene__lumiere {
  position: absolute; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(20% 16% at 50% 66%, color-mix(in srgb, var(--proof) 80%, var(--ink)), transparent 75%),
    radial-gradient(60% 34% at 50% 72%, var(--proof), transparent 72%),
    radial-gradient(110% 40% at 50% 100%, color-mix(in srgb, var(--proof) 75%, transparent), transparent 75%),
    radial-gradient(12% 40% at 50% 34%, color-mix(in srgb, var(--action) 65%, var(--ink)), transparent 78%),
    radial-gradient(40% 46% at 50% 36%, var(--action), transparent 74%), radial-gradient(70% 50% at 50% 52%, color-mix(in srgb, var(--action) 45%, var(--proof)), transparent 75%),
    linear-gradient(var(--bg), var(--bg));
}
/* Les traits : de fines lignes claires, plus visibles là où il y a de la lumière. */
.scene__lumiere::after {
  content: ''; position: absolute; inset: 0;
  background: repeating-linear-gradient(90deg, color-mix(in srgb, var(--ink) 22%, transparent) 0 1px, transparent 1px 9px);
  mix-blend-mode: overlay;
  mask-image: radial-gradient(60% 85% at 50% 55%, #000 25%, transparent 78%);
  -webkit-mask-image: radial-gradient(60% 85% at 50% 55%, #000 25%, transparent 78%);
}

.scene__contenu {
  position: relative; max-width: 72rem; margin: 0 auto;
  display: grid; justify-items: center; text-align: center; gap: var(--space-3);
}
.scene__bonjour { margin: 0; color: var(--ink-muted); font-weight: 600; }
.scene__titre {
  margin: 0; max-width: 20ch;
  font-family: var(--font-editorial); font-weight: 400;
  font-size: clamp(2.75rem, 6.6vw, 5.25rem); line-height: 1; letter-spacing: -0.02em;
  color: var(--ink); text-wrap: balance;
  text-shadow: 0 2px 40px color-mix(in srgb, var(--bg) 70%, transparent);
  animation: monte var(--motion-celebrate) var(--ease-out) both;
}
.scene__titre em { font-style: italic; }
.scene__intro { margin: 0; max-width: 34rem; text-shadow: 0 1px 16px var(--bg); color: color-mix(in srgb, var(--ink) 82%, transparent); font-size: 1.0625rem; line-height: 1.6; }

.scene__chiffres { position: absolute; top: 34%; display: grid; gap: var(--space-6); margin: 0; }
.scene__chiffres--gauche { left: var(--space-4); text-align: start; }
.scene__chiffres--droite { right: var(--space-4); text-align: end; }
.scene__chiffres div { display: flex; flex-direction: column-reverse; }
.scene__chiffres dd { margin: 0; font-family: var(--font-editorial); font-size: 2.25rem; line-height: 1; color: var(--ink); font-variant-numeric: tabular-nums; }
.scene__chiffres dt { margin-top: var(--space-1); color: color-mix(in srgb, var(--ink) 70%, transparent); font-size: var(--type-body-sm-size); }

.scene__actions { display: grid; justify-items: center; gap: var(--space-2); margin-top: var(--space-4); }
/* Le bouton rond, lumineux : l'unique action de la scène. */
.bouton-rond {
  display: grid; place-items: center; align-content: center; gap: var(--space-1);
  width: 116px; height: 116px; border-radius: 50%;
  background: radial-gradient(circle at 50% 35%, color-mix(in srgb, var(--ink) 34%, transparent), color-mix(in srgb, var(--ink) 10%, transparent) 70%);
  border: 1px solid color-mix(in srgb, var(--ink) 45%, transparent);
  box-shadow: 0 0 0 10px color-mix(in srgb, var(--ink) 6%, transparent), 0 0 60px color-mix(in srgb, var(--proof) 55%, transparent);
  backdrop-filter: blur(8px);
  color: var(--ink); font-weight: 600; text-decoration: none; text-align: center; padding: var(--space-3); line-height: 1.25;
  transition: transform var(--motion-base) var(--ease-out), box-shadow var(--motion-base) var(--ease-out);
}
.bouton-rond:hover { transform: scale(1.04); box-shadow: 0 0 0 14px color-mix(in srgb, var(--ink) 8%, transparent), 0 0 80px color-mix(in srgb, var(--proof) 70%, transparent); }
.bouton-rond:focus-visible { outline: 2px solid var(--ink); outline-offset: 6px; }
.bouton-rond__point { width: 8px; height: 8px; border-radius: 50%; background: var(--proof); box-shadow: 0 0 12px var(--proof); }
.scene__lien { color: var(--ink); font-weight: 600; text-underline-offset: 0.25em; }
.scene__rassurance { margin: 0; color: color-mix(in srgb, var(--ink) 70%, transparent); font-size: var(--type-body-sm-size); }

.apercus {
  position: relative; max-width: 72rem; margin: var(--space-8) auto 0;
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-6); align-items: end;
  perspective: 1200px;
}
.apercu { display: grid; gap: var(--space-2); color: var(--ink); text-decoration: none; transition: transform var(--motion-base) var(--ease-out); }
.apercu__etiquette { display: flex; align-items: center; gap: var(--space-2); font-size: var(--type-body-sm-size); color: color-mix(in srgb, var(--ink) 82%, transparent); }
.apercu__etiquette::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.apercu__fenetre, .apercu__projet {
  display: block; border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  background: color-mix(in srgb, var(--bg) 45%, transparent);
  border: 1px solid color-mix(in srgb, var(--ink) 22%, transparent); border-bottom: 0;
  backdrop-filter: blur(14px);
  box-shadow: 0 -20px 60px color-mix(in srgb, var(--bg) 60%, transparent);
}
.apercu__fenetre { padding: var(--space-4) var(--space-4) 0; }
/* Des bouts d'interface abstraits : lignes, visages, jauges, preuves. Décoratifs, sans faux contenu. */
.maquette { display: grid; gap: var(--space-3); padding: var(--space-6) var(--space-6) var(--space-8); }
.maquette__ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2); border-radius: var(--radius-md); background: color-mix(in srgb, var(--ink) 6%, transparent); }
.maquette__avatar { flex-shrink: 0; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; }
.maquette__avatar--grand { width: 44px; height: 44px; background: var(--action); color: var(--on-action); }
.maquette__textes { flex: 1; display: grid; gap: 6px; }
.trait { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 22%, transparent); }
.trait--fort { height: 9px; background: color-mix(in srgb, var(--ink) 60%, transparent); }
.maquette__preuve { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--proof); color: var(--on-proof); font-size: 0.75rem; font-weight: 700; }
.maquette__jauge { display: block; height: 8px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 12%, transparent); overflow: hidden; }
.maquette__jauge i { display: block; height: 100%; border-radius: inherit; background: var(--action); }
.maquette__pastilles { display: flex; gap: var(--space-2); }
.maquette__pastilles b { padding: 2px var(--space-3); border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.75rem; }
.maquette__pastilles em { width: 48px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 12%, transparent); }
.apercu__projet { text-align: start; }
.apercu--gauche { transform: rotateY(14deg) rotateZ(-3deg) translateY(18px); transform-origin: right bottom; }
.apercu--droite { transform: rotateY(-14deg) rotateZ(3deg) translateY(18px); transform-origin: left bottom; }
.apercu--gauche:hover { transform: rotateY(8deg) rotateZ(-2deg) translateY(6px); }
.apercu--droite:hover { transform: rotateY(-8deg) rotateZ(2deg) translateY(6px); }

@media (max-width: 960px) {
  .scene__chiffres { position: static; display: flex; gap: var(--space-8); justify-content: center; text-align: center; }
  .scene__chiffres--gauche, .scene__chiffres--droite { text-align: center; }
  .apercus { grid-template-columns: 1fr; max-width: 26rem; }
  .apercu--gauche, .apercu--droite { display: none; }
}

.parcours h2 { margin-bottom: var(--space-6); }
.etapes { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-8); grid-template-columns: repeat(3, minmax(0, 1fr)); counter-reset: etape; }
.etape { display: grid; gap: var(--space-2); align-content: start; justify-items: start; counter-increment: etape; }
.etape__illustration { width: 100%; max-width: 13rem; margin-bottom: var(--space-2); }
.etape h3 { margin: 0; display: flex; gap: var(--space-2); align-items: baseline; }
/* Le numéro de l'étape, en orange comme la preuve qu'on gagne à chaque étape. */
.etape h3::before { content: counter(etape); color: var(--proof-ink); font-variant-numeric: tabular-nums; }
.etape p { margin: 0; color: var(--ink-muted); }
.etape__lien { font-weight: 600; margin-top: var(--space-1); }
@media (max-width: 760px) { .etapes { grid-template-columns: 1fr; } .etape__illustration { max-width: 10rem; } }

@keyframes monte { from { opacity: 0; transform: translateY(14px); filter: blur(4px); } }
</style>
