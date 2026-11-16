<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import { JAvatar, JBadgePreuve, JIllustration } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import {
  chiffresProfil,
  competencesPhares,
  couleurLangage,
  hoteLisible,
  ilYa,
  nomLangue,
  pourcentage,
  urlDrapeau,
  type Preuve,
  type Profil,
} from './profils'

/**
 * Ce que l'on voit d'un profil (#159), sur « Mon profil » comme sur la page publique : un portfolio de développeur.
 * En-tête (couverture, photo, identité, boutons), une bande de chiffres, puis le travail (projets, réalisations,
 * dépôts, avis) à gauche et la stack, les compétences et la fiabilité à droite.
 * `moi` : la personne regarde son propre profil (les états vides lui disent quoi faire ; un visiteur ne voit que ce
 * qui est rempli). Emplacements : `boutons` dans l'en-tête, `actions` sous l'en-tête, `debut` en tête de colonne.
 */
const props = withDefaults(defineProps<{ profil: Profil; moi?: boolean }>(), { moi: false })
const { t, locale } = useI18n()
const session = useSession()

const prenom = computed(() => props.profil.nom.split(/\s+/)[0] ?? '')
const drapeau = computed(() => urlDrapeau(props.profil.pays))
const lieu = computed(() =>
  [props.profil.ville, props.profil.pays && nomPays(props.profil.pays, locale.value)].filter(Boolean).join(', '),
)
const phares = computed(() => competencesPhares(props.profil, 3))
const competencesTriees = computed(() => [
  ...props.profil.competences.filter((c) => phares.value.includes(c.competence)),
  ...props.profil.competences.filter((c) => !phares.value.includes(c.competence)),
])
const langues = computed(() => props.profil.langues.map((l) => nomLangue(l, locale.value)))
const chiffres = computed(() => chiffresProfil(props.profil))
const nombre = (n: number) => new Intl.NumberFormat(locale.value).format(n)
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const

/* Les avis des coéquipiers : leur texte se lit avec un compte (contrat : listerPreuves), seulement s'il y en a. */
const avis = ref<Preuve[]>([])
watch(
  () => [props.profil.utilisateurId, chiffres.value.avis, !!session.moi.value] as const,
  async ([id, nombreAvis, connecte]) => {
    avis.value = []
    if (!nombreAvis || !connecte) return
    try {
      const { data } = await api.GET('/api/profils/{utilisateurId}/preuves', {
        params: { path: { utilisateurId: id }, query: { type: 'avis_coequipier', limite: 3 } },
      })
      avis.value = (data?.elements ?? []).filter((p) => p.detail)
    } catch {
      avis.value = []
    }
  },
  { immediate: true },
)
</script>

<template>
  <header class="carte identite" data-test="identite">
    <div class="identite__couverture" aria-hidden="true" />
    <div class="identite__corps">
      <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="128" class="identite__photo" />
      <div class="identite__ligne-haute">
        <div class="identite__texte">
          <h1>{{ profil.nom }}</h1>
          <p v-if="profil.titre" class="identite__titre" data-test="titre">{{ profil.titre }}</p>
          <p class="identite__infos">
            <span v-if="lieu" class="lieu">
              <img v-if="drapeau" :src="drapeau" alt="" class="drapeau" width="24" height="18" data-test="drapeau" />
              {{ lieu }}
            </span>
            <span class="disponibilite" :class="`disponibilite--${profil.disponibilite}`">
              {{ t(`profil.disponibilite.${profil.disponibilite}`) }}<template v-if="profil.heuresParSemaine">
                · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</template>
            </span>
            <span v-if="langues.length" class="langues" data-test="langues">
              <span class="visuellement-cache">{{ t('profil.langues.parle') }}</span>
              <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M10 2.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15Zm-7.2 7.5h14.4M10 2.5c2 2 3 4.6 3 7.5s-1 5.5-3 7.5c-2-2-3-4.6-3-7.5s1-5.5 3-7.5Z" fill="none" stroke="currentColor" stroke-width="1.4" /></svg>
              {{ langues.join(' · ') }}
            </span>
          </p>
        </div>
        <div class="identite__boutons"><slot name="boutons" /></div>
      </div>
      <ul v-if="profil.loginGithub || profil.linkedin || profil.siteWeb" class="liens" data-test="liens">
        <li v-if="profil.loginGithub">
          <a :href="`https://github.com/${profil.loginGithub}`" rel="noopener noreferrer" target="_blank">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M8 0a8 8 0 0 0-2.53 15.59c.4.07.55-.17.55-.38v-1.33c-2.23.48-2.7-1.07-2.7-1.07-.36-.92-.89-1.17-.89-1.17-.73-.5.06-.49.06-.49.8.06 1.23.83 1.23.83.72 1.22 1.88.87 2.34.66.07-.52.28-.87.5-1.07-1.78-.2-3.65-.89-3.65-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8 8 0 0 0 8 0Z" /></svg>
            <span class="liens__type">GitHub</span> @{{ profil.loginGithub }}</a>
        </li>
        <li v-if="profil.linkedin">
          <a :href="profil.linkedin" rel="noopener noreferrer" target="_blank">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M1.5 1h13c.28 0 .5.22.5.5v13a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-13c0-.28.22-.5.5-.5Zm2.1 4.9V13h2.2V5.9H3.6Zm1.1-3.4a1.27 1.27 0 1 0 0 2.54 1.27 1.27 0 0 0 0-2.54ZM7.2 5.9V13h2.2V9.5c0-.92.17-1.82 1.32-1.82 1.13 0 1.15 1.06 1.15 1.88V13h2.2V9.1c0-1.9-.41-3.37-2.64-3.37-1.07 0-1.79.59-2.08 1.14h-.03v-.97H7.2Z" /></svg>
            <span class="liens__type">LinkedIn</span></a>
        </li>
        <li v-if="profil.siteWeb">
          <a :href="profil.siteWeb" rel="noopener noreferrer" target="_blank">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M8.5 11.5a3.5 3.5 0 0 0 5 0l2.5-2.5a3.5 3.5 0 0 0-5-5l-1 1m1.5 3.5a3.5 3.5 0 0 0-5 0L4 11a3.5 3.5 0 0 0 5 5l1-1" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" /></svg>
            <span class="liens__type">{{ t('profil.liens.site') }}</span> {{ hoteLisible(profil.siteWeb) }}</a>
        </li>
      </ul>
      <ul v-if="phares.length" class="phares" :aria-label="t('profil.phares.titre')" data-test="phares">
        <li v-for="c in phares" :key="c">{{ c }}</li>
      </ul>
    </div>

    <!-- La bande de chiffres : seulement ce qui existe, jamais un « 0 » qui dessert. -->
    <dl v-if="chiffres.etoiles || chiffres.langage || chiffres.enLigne || chiffres.aTemps || chiffres.avis" class="chiffres" data-test="chiffres">
      <div v-if="chiffres.enLigne">
        <dt>{{ t('profil.chiffres.enLigne', chiffres.enLigne) }}</dt>
        <dd>{{ nombre(chiffres.enLigne) }}</dd>
      </div>
      <div v-if="chiffres.aTemps">
        <dt>{{ t('profil.chiffres.aTemps') }}</dt>
        <dd>{{ chiffres.aTemps.sur != null ? t('profil.fiabilite.sur', { n: chiffres.aTemps.nombre, sur: chiffres.aTemps.sur }) : nombre(chiffres.aTemps.nombre) }}</dd>
      </div>
      <div v-if="chiffres.avis">
        <dt>{{ t('profil.chiffres.avis', chiffres.avis) }}</dt>
        <dd>{{ nombre(chiffres.avis) }}</dd>
      </div>
      <div v-if="chiffres.etoiles">
        <dt>{{ t('profil.chiffres.etoiles', chiffres.etoiles) }}</dt>
        <dd>★ {{ nombre(chiffres.etoiles) }}</dd>
      </div>
      <div v-if="chiffres.langage">
        <dt>{{ t('profil.chiffres.langage') }}</dt>
        <dd><span class="pastille-langage" :style="{ background: couleurLangage(chiffres.langage) }" />{{ chiffres.langage }}</dd>
      </div>
    </dl>
  </header>

  <slot name="actions" />

  <div class="colonnes">
    <div class="colonne">
      <slot name="debut" />

      <section v-if="profil.bio || profil.resumeIa" class="carte" data-test="a-propos">
        <h2>{{ t('profil.aPropos.titre') }}</h2>
        <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>
        <div v-if="profil.resumeIa" class="resume-ia">
          <p class="resume-ia__mention">✨ {{ t('profil.aPropos.ia') }}</p>
          <p>{{ profil.resumeIa }}</p>
        </div>
      </section>

      <!-- Les projets d'abord : c'est la preuve la plus forte (contrat : Profil.projets). -->
      <section v-if="profil.projets.length || moi" class="carte">
        <div class="titre-section">
          <h2>{{ t('profil.projets.titre') }}</h2>
          <span v-if="profil.projets.length" class="compteur">{{ profil.projets.length }}</span>
        </div>
        <ul v-if="profil.projets.length" class="grille" data-test="projets">
          <li v-for="p in profil.projets" :key="p.projetId" class="tuile" :class="{ 'tuile--en-ligne': p.enLigne }">
            <h3>
              <a v-if="p.url" :href="p.url" rel="noopener noreferrer" target="_blank">{{ p.titre }}</a>
              <template v-else>{{ p.titre }}</template>
            </h3>
            <p class="texte-discret">{{ p.role }}<template v-if="p.paysEquipe?.length"> · {{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</template></p>
            <p v-if="p.url" class="tuile__url">{{ hoteLisible(p.url) }}</p>
            <JBadgePreuve v-if="p.enLigne" class="tuile__badge">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
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
        <div class="titre-section">
          <h2>{{ t('profil.realisations.titre') }}</h2>
          <span class="declare" :title="t('profil.realisations.declareAide')">{{ t('profil.realisations.declare') }}</span>
        </div>
        <ul class="grille">
          <li v-for="(r, i) in profil.realisations" :key="i" class="tuile tuile--declaree">
            <div class="tuile__tete">
              <h3>
                <a v-if="r.url" :href="r.url" rel="noopener noreferrer" target="_blank">{{ r.titre }}</a>
                <template v-else>{{ r.titre }}</template>
              </h3>
              <span v-if="r.annee" class="tuile__annee">{{ r.annee }}</span>
            </div>
            <p v-if="r.role" class="texte-discret">{{ r.role }}</p>
            <p v-if="r.description">{{ r.description }}</p>
            <ul v-if="r.technologies?.length" class="etiquettes">
              <li v-for="tech in r.technologies" :key="tech" class="etiquette">{{ tech }}</li>
            </ul>
          </li>
        </ul>
      </section>

      <section v-if="profil.depotsGithub.length" class="carte">
        <div class="titre-section">
          <h2>{{ t('profil.depots.titre') }}</h2>
          <a v-if="profil.loginGithub" :href="`https://github.com/${profil.loginGithub}?tab=repositories`" rel="noopener noreferrer" target="_blank" class="tout-voir">
            {{ t('profil.depots.toutVoir') }}</a>
        </div>
        <ul class="grille" data-test="depots">
          <li v-for="d in profil.depotsGithub" :key="d.url" class="tuile depot">
            <h3>
              <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.71 1.71.75.75 0 1 1-1.06 1.06A2.5 2.5 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.71A2.5 2.5 0 0 1 4.5 9h8Z" /></svg>
              <a :href="d.url" rel="noopener noreferrer" target="_blank">{{ d.nom }}</a>
            </h3>
            <p v-if="d.description" class="depot__description">{{ d.description }}</p>
            <p class="depot__meta">
              <span v-if="d.langage"><span class="pastille-langage" :style="{ background: couleurLangage(d.langage) }" />{{ d.langage }}</span>
              <span>★ {{ nombre(d.etoiles) }}<span class="visuellement-cache">{{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span></span>
              <span v-if="ilYa(d.misAJourLe, locale)">{{ t('profil.depots.misAJour', { quand: ilYa(d.misAJourLe, locale) }) }}</span>
            </p>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.depots.pasUnePreuve') }}</p>
      </section>

      <section v-if="avis.length" class="carte" data-test="avis">
        <h2>{{ t('profil.avis.titre') }}</h2>
        <ul class="avis">
          <li v-for="a in avis" :key="a.id">
            <blockquote>{{ a.detail }}</blockquote>
            <p v-if="a.auteur" class="avis__auteur">
              <JAvatar :nom="a.auteur.nom" :url="a.auteur.avatarUrl" :taille="28" />
              <RouterLink :to="`/p/${a.auteur.id}`">{{ a.auteur.nom }}</RouterLink>
              <span v-if="a.source.titre" class="texte-discret">· {{ a.source.titre }}</span>
            </p>
          </li>
        </ul>
      </section>
    </div>

    <aside class="colonne">
      <section v-if="profil.langages.length" class="carte" :aria-label="t('profil.langages.titre')">
        <h2>{{ t('profil.langages.titre') }}</h2>
        <div class="barre-langages" aria-hidden="true">
          <span v-for="l in profil.langages" :key="l.nom" :style="{ flexGrow: Math.max(l.part, 0.01), background: couleurLangage(l.nom) }" />
        </div>
        <ul class="langages" data-test="langages">
          <li v-for="l in profil.langages" :key="l.nom">
            <span class="pastille-langage" :style="{ background: couleurLangage(l.nom) }" />
            <span class="langages__nom">{{ l.nom }}</span>
            <span class="texte-discret">{{ pourcentage(l.part, locale) }}</span>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.langages.estimation') }}</p>
      </section>

      <section v-if="profil.competences.length" class="carte">
        <h2>{{ t('profil.competences.titre') }}</h2>
        <ul class="etiquettes" data-test="competences">
          <li v-for="c in competencesTriees" :key="c.competence + c.source" class="etiquette" :class="{ 'etiquette--declaree': c.source === 'declaree', 'etiquette--phare': phares.includes(c.competence) }">
            {{ c.competence }}
            <span v-if="c.source === 'declaree'" class="visuellement-cache">{{ t('profil.competences.declaree') }}</span>
            <span v-if="c.source === 'ia' && c.niveau" class="etiquette__niveau">· {{ t(`profil.carte.niveaux.${c.niveau}`) }}</span>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.competences.source') }}</p>
      </section>

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
    </aside>
  </div>
</template>

<style scoped>
/* ---------- En-tête : couverture bord à bord (padding de .carte remis à 0), photo qui déborde ---------- */
.identite { padding: 0; overflow: hidden; display: grid; }
.identite__couverture {
  position: relative; height: 168px;
  background:
    radial-gradient(28rem 14rem at 88% 120%, color-mix(in srgb, var(--proof) 75%, transparent), transparent 70%),
    linear-gradient(120deg, color-mix(in srgb, var(--action-ink) 80%, var(--midnight)), var(--action) 70%);
}
/* Le motif tech au trait, en masque CSS, qui s'efface vers la gauche pour laisser respirer la photo. */
.identite__couverture::after {
  content: ''; position: absolute; inset: 0; background: #fff; opacity: 0.24;
  -webkit-mask-image: url('../../../../../docs/design/motifs/tech.svg'), linear-gradient(90deg, transparent 5%, #000 55%);
  mask-image: url('../../../../../docs/design/motifs/tech.svg'), linear-gradient(90deg, transparent 5%, #000 55%);
  -webkit-mask-size: 220px, 100% 100%; mask-size: 220px, 100% 100%;
  -webkit-mask-repeat: repeat, no-repeat; mask-repeat: repeat, no-repeat;
  -webkit-mask-composite: source-in; mask-composite: intersect;
}
@media (prefers-contrast: more) { .identite__couverture::after { display: none; } }
@media (max-width: 640px) { .identite__couverture { height: 112px; } }
.identite__corps { display: grid; gap: var(--space-3); padding: 0 var(--space-6) var(--space-6); }
@media (max-width: 640px) { .identite__corps { padding: 0 var(--space-4) var(--space-4); } }
.identite__photo { margin-top: -64px; box-shadow: 0 0 0 4px var(--surface); border-radius: 50%; position: relative; justify-self: start; }
.identite__ligne-haute { display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: flex-start; justify-content: space-between; }
.identite__texte { display: grid; gap: var(--space-2); min-width: 0; flex: 1 1 20rem; }
.identite__texte h1, .identite__texte p { margin: 0; }
.identite h1 { font-size: clamp(1.75rem, 3.5vw, 2.25rem); line-height: 1.1; letter-spacing: -0.5px; }
.identite__titre { font-size: 1.125rem; color: var(--ink-muted); font-weight: 500; }
.identite__infos { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); align-items: center; font-size: 0.9375rem; }
.identite__boutons { display: grid; gap: var(--space-2); justify-items: end; }
.identite__boutons:empty { display: none; }
@media (max-width: 640px) { .identite__boutons { justify-items: start; } }
.lieu, .langues { display: inline-flex; align-items: center; gap: var(--space-2); }
.lieu { font-weight: 600; }
.langues { color: var(--ink-muted); }
.drapeau { width: 24px; height: 18px; border-radius: 3px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }
.liens { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.liens a { display: inline-flex; gap: var(--space-2); align-items: center; min-height: 34px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); text-decoration: none; color: var(--ink); font-size: 0.9375rem; }
.liens a:hover { border-color: var(--action); color: var(--action-ink); }
.liens__type { font-weight: 700; }
.phares { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.phares li { padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-weight: 700; }
.disponibilite { font-weight: 600; }
.disponibilite::before { content: ''; display: inline-block; width: 0.6em; height: 0.6em; margin-inline-end: var(--space-1); border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible::before { background: #2DA44E; box-shadow: 0 0 0 3px color-mix(in srgb, #2DA44E 25%, transparent); }

/* ---------- Bande de chiffres ---------- */
.chiffres { margin: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr)); border-top: 1px solid var(--line); }
.chiffres > div { display: flex; flex-direction: column-reverse; justify-content: flex-end; gap: 2px; padding: var(--space-4) var(--space-6); border-inline-start: 1px solid var(--line); }
.chiffres > div:first-child { border-inline-start: 0; }
.chiffres dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.chiffres dd { margin: 0; font-family: var(--font-display); font-weight: 700; font-size: 1.375rem; display: inline-flex; align-items: center; gap: var(--space-2); }
@media (max-width: 640px) { .chiffres > div { padding: var(--space-3) var(--space-4); } }

/* ---------- Colonnes ---------- */
.colonnes { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 900px) { .colonnes { grid-template-columns: 1fr; } }
.colonne { display: grid; gap: var(--space-6); min-width: 0; }
.carte h2 { font-size: var(--type-h3-size); line-height: var(--type-h3-line); margin: 0 0 var(--space-4); }
.titre-section { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-3); margin-block-end: var(--space-4); }
.titre-section h2 { margin: 0; }
.compteur { font-weight: 700; color: var(--ink-muted); }
.tout-voir { font-weight: 600; font-size: var(--type-body-sm-size); }
.bio { white-space: pre-line; margin: 0; font-size: 1.0625rem; }
.resume-ia { margin-top: var(--space-4); padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action-soft) 60%, transparent); }
.carte > .resume-ia:first-of-type { margin-top: 0; }
.resume-ia p { margin: 0; }
.resume-ia__mention { font-size: var(--type-label-size); font-weight: 600; color: var(--ink-muted); margin-block-end: var(--space-2) !important; }

/* Tuiles de portfolio : projets, réalisations, dépôts. */
.grille { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); }
.tuile { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); min-width: 0; transition: border-color var(--motion-fast) var(--ease-out); }
.tuile:hover { border-color: var(--action); }
.tuile p { margin: 0; }
.tuile h3 { margin: 0; font-size: 1.0625rem; line-height: 1.3; display: flex; align-items: center; gap: var(--space-2); min-width: 0; }
.tuile h3 a { overflow-wrap: anywhere; }
.tuile__tete { display: flex; justify-content: space-between; gap: var(--space-2); align-items: baseline; }
.tuile__annee { font-size: var(--type-body-sm-size); color: var(--ink-muted); flex: none; }
.tuile__url { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.8125rem; color: var(--ink-muted); overflow-wrap: anywhere; }
.tuile__badge { align-self: flex-start; margin-top: auto; }
.tuile--en-ligne { border-color: color-mix(in srgb, var(--proof) 45%, var(--line)); background: linear-gradient(180deg, var(--proof-soft), var(--surface) 70%); }
.tuile--declaree { border-style: dashed; }
.tuile .etiquettes { margin: auto 0 0; }
.declare { flex: none; font-size: 0.8125rem; color: var(--ink-muted); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); padding: 0 var(--space-2); }
.depot h3 { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9375rem; color: var(--ink-muted); }
.depot h3 a { font-weight: 700; }
.depot__description { font-size: var(--type-body-sm-size); color: var(--ink-muted); flex: 1; }
.depot__meta { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-4); font-size: 0.8125rem; color: var(--ink-muted); }
.depot__meta > span { display: inline-flex; align-items: center; gap: var(--space-1); }
.pastille-langage { display: inline-block; flex: none; width: 0.75em; height: 0.75em; border-radius: 50%; box-shadow: 0 0 0 1px color-mix(in srgb, var(--ink) 15%, transparent); }

.vide { display: grid; gap: var(--space-3); justify-items: center; text-align: center; padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); }
.vide p { margin: 0; color: var(--ink-muted); max-width: 30rem; }
.vide__illustration { width: 9rem; }

.avis { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); }
.avis blockquote { margin: 0; padding-inline-start: var(--space-4); border-inline-start: 3px solid var(--proof); font-size: 1.0625rem; }
.avis__auteur { margin: var(--space-2) 0 0; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); font-weight: 600; }

/* ---------- Colonne latérale ---------- */
.barre-langages { display: flex; height: 10px; border-radius: var(--radius-pill); overflow: hidden; gap: 2px; margin-block-end: var(--space-4); background: var(--surface-raised); }
.barre-langages > span { min-width: 4px; }
.langages { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.langages li { display: grid; grid-template-columns: auto 1fr auto; gap: var(--space-2); align-items: center; }
.langages__nom { font-weight: 600; }
.etiquettes { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.etiquette--declaree { background: transparent; border: 1px solid var(--line-strong); }
.etiquette--phare { background: var(--action-soft); color: var(--action-ink); font-weight: 700; }
.etiquette__niveau { color: var(--ink-muted); font-weight: 500; }
.points { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.points li { display: grid; grid-template-columns: auto 1fr; gap: 2px var(--space-3); align-items: baseline; padding: var(--space-3); background: var(--proof-soft); border-radius: var(--radius-sm); }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: 1.25rem; color: var(--proof-ink); }
.points small { grid-column: 2; }
.comment { margin: 0 0 var(--space-3); padding-inline-start: 1.2em; display: grid; gap: var(--space-1); }
.fiabilite > p:last-child { margin: 0; }
</style>
