<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBadgePreuve, JIllustration } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { competencesPhares, hoteLisible, nomLangue, pourcentage, urlDrapeau, type Profil } from './profils'

/**
 * Ce que l'on voit d'un profil (#159), sur « Mon profil » comme sur la page publique. `moi` : la personne regarde
 * son propre profil (les états vides lui disent quoi faire ; un visiteur ne voit que ce qui est rempli).
 */
const props = withDefaults(defineProps<{ profil: Profil; moi?: boolean }>(), { moi: false })
const { t, locale } = useI18n()

const prenom = computed(() => props.profil.nom.split(/\s+/)[0] ?? '')
const drapeau = computed(() => urlDrapeau(props.profil.pays))
const lieu = computed(() =>
  [props.profil.ville, props.profil.pays && nomPays(props.profil.pays, locale.value)].filter(Boolean).join(', '),
)
const phares = computed(() => competencesPhares(props.profil, 3))
const autres = computed(() => props.profil.competences.filter((c) => !phares.value.includes(c.competence)))
const langues = computed(() => props.profil.langues.map((l) => nomLangue(l, locale.value)))
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const
</script>

<template>
  <header class="carte identite" data-test="identite">
    <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="104" class="identite__photo" />
    <div class="identite__texte">
      <h1>{{ profil.nom }}</h1>
      <p v-if="profil.titre" class="identite__titre" data-test="titre">{{ profil.titre }}</p>
      <p class="identite__ligne">
        <span v-if="lieu" class="lieu">
          <img v-if="drapeau" :src="drapeau" alt="" class="drapeau" width="24" height="18" data-test="drapeau" />
          {{ lieu }}
        </span>
        <span class="disponibilite" :class="`disponibilite--${profil.disponibilite}`">
          {{ t(`profil.disponibilite.${profil.disponibilite}`) }}<template v-if="profil.heuresParSemaine">
            · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</template>
        </span>
      </p>
      <ul v-if="profil.loginGithub || profil.linkedin || profil.siteWeb" class="liens" data-test="liens">
        <li v-if="profil.loginGithub">
          <a :href="`https://github.com/${profil.loginGithub}`" rel="noopener noreferrer" target="_blank">
            <span class="liens__type">GitHub</span> @{{ profil.loginGithub }}</a>
        </li>
        <li v-if="profil.linkedin">
          <a :href="profil.linkedin" rel="noopener noreferrer" target="_blank"><span class="liens__type">LinkedIn</span></a>
        </li>
        <li v-if="profil.siteWeb">
          <a :href="profil.siteWeb" rel="noopener noreferrer" target="_blank">
            <span class="liens__type">{{ t('profil.liens.site') }}</span> {{ hoteLisible(profil.siteWeb) }}</a>
        </li>
      </ul>
      <p v-if="langues.length" class="langues" data-test="langues">
        <span class="texte-discret">{{ t('profil.langues.parle') }}</span> {{ langues.join(' · ') }}
      </p>
      <ul v-if="phares.length" class="phares" :aria-label="t('profil.phares.titre')" data-test="phares">
        <li v-for="c in phares" :key="c">{{ c }}</li>
      </ul>
    </div>
  </header>

  <slot name="actions" />

  <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>

  <div class="colonnes">
    <div class="colonne">
      <slot name="debut" />

      <!-- Les projets d'abord : c'est la preuve la plus forte (contrat : Profil.projets). -->
      <section v-if="profil.projets.length || moi" class="carte">
        <h2>{{ t('profil.projets.titre') }}</h2>
        <ul v-if="profil.projets.length" class="projets" data-test="projets">
          <li v-for="p in profil.projets" :key="p.projetId">
            <div>
              <strong>{{ p.titre }}</strong>
              <p class="texte-discret">{{ p.role }}<template v-if="p.paysEquipe?.length"> · {{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</template></p>
            </div>
            <JBadgePreuve v-if="p.enLigne">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
          </li>
        </ul>
        <div v-else class="vide" data-test="projets-vide">
          <JIllustration nom="en-ligne" class="vide__illustration" />
          <p>{{ t('profil.projets.vide') }}</p>
          <RouterLink to="/vitrine">{{ t('profil.projets.inspiration') }}</RouterLink>
        </div>
      </section>

      <!-- Hors de Jamii : déclaré par la personne, jamais présenté comme une preuve. -->
      <section v-if="profil.realisations.length" class="carte" data-test="realisations">
        <h2>{{ t('profil.realisations.titre') }}</h2>
        <ul class="realisations">
          <li v-for="(r, i) in profil.realisations" :key="i">
            <div class="realisations__entete">
              <h3>
                <a v-if="r.url" :href="r.url" rel="noopener noreferrer" target="_blank">{{ r.titre }}</a>
                <template v-else>{{ r.titre }}</template>
              </h3>
              <span class="declare" :title="t('profil.realisations.declareAide')">{{ t('profil.realisations.declare') }}</span>
            </div>
            <p v-if="r.role || r.annee" class="texte-discret">{{ [r.role, r.annee].filter(Boolean).join(' · ') }}</p>
            <p v-if="r.description">{{ r.description }}</p>
            <ul v-if="r.technologies?.length" class="etiquettes">
              <li v-for="tech in r.technologies" :key="tech" class="etiquette">{{ tech }}</li>
            </ul>
          </li>
        </ul>
      </section>

      <section v-if="profil.langages.length" class="carte" :aria-label="t('profil.langages.titre')">
        <h2>{{ t('profil.langages.titre') }}</h2>
        <ul class="langages" data-test="langages">
          <li v-for="l in profil.langages" :key="l.nom">
            <span>{{ l.nom }}</span>
            <span class="jauge" aria-hidden="true"><span :style="{ width: `${Math.max(l.part * 100, 2)}%` }" /></span>
            <span class="texte-discret">{{ pourcentage(l.part, locale) }}</span>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.langages.estimation') }}</p>
      </section>

      <section v-if="profil.depotsGithub.length" class="carte">
        <h2>{{ t('profil.depots.titre') }}</h2>
        <ul class="depots" data-test="depots">
          <li v-for="d in profil.depotsGithub" :key="d.url">
            <a :href="d.url" rel="noopener noreferrer" target="_blank">{{ d.nom }}</a>
            <span class="texte-discret">
              <template v-if="d.langage">{{ d.langage }} · </template>{{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span>
            <p v-if="d.description">{{ d.description }}</p>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.depots.pasUnePreuve') }}</p>
      </section>
    </div>

    <aside class="colonne">
      <!-- L'indice de fiabilité : un point par type de preuve, jamais de note globale. -->
      <section class="carte fiabilite">
        <h2>{{ t('profil.fiabilite.titre', { prenom: prenom }) }}</h2>
        <ul v-if="profil.fiabilite.length" class="points" data-test="fiabilite">
          <li v-for="f in profil.fiabilite" :key="f.type">
            <span class="points__nombre">{{ f.sur != null ? t('profil.fiabilite.sur', { n: f.nombre, sur: f.sur }) : f.nombre }}</span>
            <span>{{ t(`profil.fiabilite.types.${f.type}`, f.nombre) }}</span>
            <small v-if="f.contestees" class="texte-discret">{{ t('profil.fiabilite.contestees', { n: f.contestees }, f.contestees) }}</small>
          </li>
        </ul>
        <template v-else>
          <p class="texte-discret" data-test="fiabilite-vide">{{ t('profil.fiabilite.vide') }}</p>
          <ul v-if="moi" class="comment">
            <li v-for="type in TYPES_PREUVE" :key="type">{{ t(`profil.fiabilite.comment.${type}`) }}</li>
          </ul>
        </template>
        <p class="texte-discret">{{ t('profil.fiabilite.sansNote') }}</p>
      </section>

      <section v-if="profil.competences.length" class="carte">
        <h2>{{ t('profil.competences.titre') }}</h2>
        <ul class="etiquettes" data-test="competences">
          <li v-for="c in [...profil.competences.filter((x) => phares.includes(x.competence)), ...autres]" :key="c.competence + c.source" class="etiquette" :class="{ 'etiquette--declaree': c.source === 'declaree', 'etiquette--phare': phares.includes(c.competence) }">
            {{ c.competence }}
            <span v-if="c.source === 'declaree'" class="visuellement-cache">{{ t('profil.competences.declaree') }}</span>
            <span v-if="c.source === 'ia' && c.niveau" class="etiquette__niveau">· {{ t(`profil.carte.niveaux.${c.niveau}`) }}</span>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.competences.source') }}</p>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.identite { display: flex; flex-direction: row; gap: var(--space-6); align-items: flex-start; flex-wrap: wrap; position: relative; overflow: hidden; }
.identite::before { content: ''; position: absolute; inset: 0 0 auto 0; height: 4px; background: linear-gradient(90deg, var(--action), var(--proof)); }
.identite__photo { box-shadow: 0 0 0 3px var(--surface), 0 0 0 5px var(--action); border-radius: 50%; flex: none; }
.identite__texte { display: grid; gap: var(--space-2); min-width: 0; flex: 1; }
.identite__texte h1, .identite__texte p { margin: 0; }
.identite h1 { font-size: clamp(1.75rem, 3.5vw, 2.25rem); line-height: 1.1; letter-spacing: -0.5px; }
.identite__titre { font-size: 1.125rem; color: var(--ink-muted); font-weight: 500; }
.identite__ligne { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); align-items: center; }
.lieu { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: 600; }
.drapeau { width: 24px; height: 18px; border-radius: 3px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }
.liens { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.liens a { display: inline-flex; gap: var(--space-1); align-items: center; min-height: 32px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); text-decoration: none; color: var(--ink); font-size: 0.9375rem; }
.liens a:hover { border-color: var(--action); }
.liens__type { font-weight: 700; }
.langues { font-size: 0.9375rem; }
.phares { list-style: none; margin: var(--space-1) 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.phares li { padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-weight: 700; }
.colonnes { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 860px) { .colonnes { grid-template-columns: 1fr; } }
.colonne { display: grid; gap: var(--space-6); min-width: 0; }
.carte h2 { font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.projets { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.projets li { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; padding: var(--space-3) var(--space-4); background: var(--surface-raised); border-radius: var(--radius-sm); }
.projets p { margin: 0; }
.realisations { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.realisations > li { display: grid; gap: var(--space-1); padding-block-end: var(--space-4); border-block-end: 1px solid var(--line); }
.realisations > li:last-child { border: 0; padding: 0; }
.realisations p { margin: 0; }
.realisations__entete { display: flex; justify-content: space-between; gap: var(--space-3); align-items: baseline; }
.realisations h3 { margin: 0; font-size: 1.0625rem; }
.realisations .etiquettes { margin: var(--space-1) 0 0; }
.declare { flex: none; font-size: 0.8125rem; color: var(--ink-muted); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); padding: 0 var(--space-2); }
.vide { display: grid; gap: var(--space-3); justify-items: center; text-align: center; padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); }
.vide p { margin: 0; color: var(--ink-muted); max-width: 30rem; }
.vide__illustration { width: 9rem; }
.points { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.points li { display: grid; grid-template-columns: auto 1fr; gap: 2px var(--space-3); align-items: baseline; padding: var(--space-3); background: var(--proof-soft); border-radius: var(--radius-sm); }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: 1.25rem; color: var(--proof-ink); }
.points small { grid-column: 2; }
.comment { margin: 0 0 var(--space-3); padding-inline-start: 1.2em; display: grid; gap: var(--space-1); }
.etiquette--declaree { background: transparent; border: 1px solid var(--line-strong); }
.etiquette--phare { background: var(--action-soft); color: var(--action-ink); font-weight: 700; }
.disponibilite { font-weight: 600; }
.disponibilite::before { content: ''; display: inline-block; width: 0.6em; height: 0.6em; margin-inline-end: var(--space-1); border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible::before { background: var(--action); }
.bio { max-width: 44rem; white-space: pre-line; margin: 0; font-size: 1.0625rem; }
.langages { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.langages li { display: grid; grid-template-columns: 7rem 1fr 3.5rem; gap: var(--space-3); align-items: center; }
.jauge { height: 8px; background: var(--surface-raised); border-radius: var(--radius-pill); overflow: hidden; }
.jauge > span { display: block; height: 100%; background: var(--action); border-radius: var(--radius-pill); }
.etiquettes { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.depots { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-4); }
.depots li { display: grid; gap: var(--space-1); }
.depots p { margin: 0; }
.depots a { font-weight: 600; }
.etiquette__niveau { color: var(--ink-muted); font-weight: 500; }
</style>
