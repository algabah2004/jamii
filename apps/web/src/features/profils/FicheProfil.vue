<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import { JAvatar, JBadgePreuve, JIllustration } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import {
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
 * Ce que l'on voit d'un profil (#159), sur « Mon profil » comme sur la page publique, construit comme un profil
 * LinkedIn : en-tête (couverture, photo, titre, lieu, abonnés, boutons ; GitHub et liens à droite), puis une colonne
 * de sections (Infos, Expérience, Activité GitHub, Compétences, Recommandations, Langues) et, à droite, la fiabilité
 * et les langages. `moi` : la personne regarde son propre profil (les états vides lui disent quoi faire ; un visiteur
 * ne voit que ce qui est rempli). Emplacements : `boutons` (sous le lieu), `actions` (sous l'en-tête), `debut`.
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
/**
 * Les mises en avant d'abord, puis les plus prouvées : gardées de l'IA (avec dépôts), déclarées, déduites de GitHub.
 * Une compétence venue de deux sources ne s'affiche qu'une fois, avec la plus forte.
 */
const ORDRE_SOURCES = { ia: 0, declaree: 1, github: 2 } as const
const competencesTriees = computed(() =>
  [...props.profil.competences].sort(
    (a, b) =>
      Number(!phares.value.includes(a.competence)) - Number(!phares.value.includes(b.competence)) ||
      ORDRE_SOURCES[a.source] - ORDRE_SOURCES[b.source],
  ).filter((c, i, liste) => liste.findIndex((x) => x.competence === c.competence) === i),
)
/** Comme sur LinkedIn : les cinq premières, puis « Afficher les N compétences ». */
const COMPETENCES_VISIBLES = 5
const toutesCompetences = ref(false)
const competencesAffichees = computed(() =>
  toutesCompetences.value ? competencesTriees.value : competencesTriees.value.slice(0, COMPETENCES_VISIBLES),
)
const langues = computed(() => props.profil.langues.map((l) => nomLangue(l, locale.value)))
/** L'activité GitHub : les dépôts du plus récemment mis à jour au plus ancien. */
const depotsRecents = computed(() =>
  [...props.profil.depotsGithub].sort((a, b) => (b.misAJourLe ?? '').localeCompare(a.misAJourLe ?? '')),
)
const etoiles = computed(() => props.profil.depotsGithub.reduce((total, d) => total + d.etoiles, 0))
const nombre = (n: number) => new Intl.NumberFormat(locale.value).format(n)
const initiale = (texte: string) => texte.trim().charAt(0).toLocaleUpperCase(locale.value) || '·'
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const

/* Les recommandations : le texte des avis de coéquipiers se lit avec un compte (contrat : listerPreuves). */
const avis = ref<Preuve[]>([])
const nombreAvis = computed(() => props.profil.fiabilite.find((f) => f.type === 'avis_coequipier')?.nombre ?? 0)
watch(
  () => [props.profil.utilisateurId, nombreAvis.value, !!session.moi.value] as const,
  async ([id, n, connecte]) => {
    avis.value = []
    if (!n || !connecte) return
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
      <div class="identite__principal">
        <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="152" class="identite__photo" />
        <h1>{{ profil.nom }}</h1>
        <p v-if="profil.titre" class="identite__titre" data-test="titre">{{ profil.titre }}</p>
        <p class="identite__infos">
          <span v-if="lieu" class="lieu">
            <img v-if="drapeau" :src="drapeau" alt="" class="drapeau" width="20" height="15" data-test="drapeau" />
            {{ lieu }}
          </span>
          <span v-if="lieu" aria-hidden="true">·</span>
          <span class="disponibilite" :class="`disponibilite--${profil.disponibilite}`">
            {{ t(`profil.disponibilite.${profil.disponibilite}`) }}<template v-if="profil.heuresParSemaine">
              · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</template>
          </span>
        </p>
        <div class="identite__boutons"><slot name="boutons" /></div>
      </div>

      <!-- À droite, comme l'entreprise et l'école sur LinkedIn : GitHub et les liens de la personne. -->
      <ul v-if="profil.loginGithub || profil.linkedin || profil.siteWeb" class="liens" data-test="liens">
        <li v-if="profil.loginGithub">
          <a :href="`https://github.com/${profil.loginGithub}`" rel="noopener noreferrer" target="_blank">
            <span class="liens__logo liens__logo--github"><svg viewBox="0 0 16 16" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M8 0a8 8 0 0 0-2.53 15.59c.4.07.55-.17.55-.38v-1.33c-2.23.48-2.7-1.07-2.7-1.07-.36-.92-.89-1.17-.89-1.17-.73-.5.06-.49.06-.49.8.06 1.23.83 1.23.83.72 1.22 1.88.87 2.34.66.07-.52.28-.87.5-1.07-1.78-.2-3.65-.89-3.65-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8 8 0 0 0 8 0Z" /></svg></span>
            <span class="liens__texte"><strong>GitHub</strong> @{{ profil.loginGithub }}<small v-if="etoiles">★ {{ t('profil.depots.etoiles', { n: nombre(etoiles) }, etoiles) }}</small></span>
          </a>
        </li>
        <li v-if="profil.linkedin">
          <a :href="profil.linkedin" rel="noopener noreferrer" target="_blank">
            <span class="liens__logo liens__logo--linkedin"><svg viewBox="0 0 16 16" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M3.6 5.9V13h2.2V5.9H3.6Zm1.1-3.4a1.27 1.27 0 1 0 0 2.54 1.27 1.27 0 0 0 0-2.54ZM7.2 5.9V13h2.2V9.5c0-.92.17-1.82 1.32-1.82 1.13 0 1.15 1.06 1.15 1.88V13h2.2V9.1c0-1.9-.41-3.37-2.64-3.37-1.07 0-1.79.59-2.08 1.14h-.03v-.97H7.2Z" /></svg></span>
            <span class="liens__texte"><strong>LinkedIn</strong></span>
          </a>
        </li>
        <li v-if="profil.siteWeb">
          <a :href="profil.siteWeb" rel="noopener noreferrer" target="_blank">
            <span class="liens__logo"><svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M8.5 11.5a3.5 3.5 0 0 0 5 0l2.5-2.5a3.5 3.5 0 0 0-5-5l-1 1m1.5 3.5a3.5 3.5 0 0 0-5 0L4 11a3.5 3.5 0 0 0 5 5l1-1" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" /></svg></span>
            <span class="liens__texte"><strong>{{ t('profil.liens.site') }}</strong> {{ hoteLisible(profil.siteWeb) }}</span>
          </a>
        </li>
      </ul>
    </div>
  </header>

  <slot name="actions" />

  <div class="colonnes">
    <div class="colonne">
      <slot name="debut" />

      <!-- Infos : la bio, le résumé de l'IA (étiqueté), puis les compétences principales, comme sur LinkedIn. -->
      <section v-if="profil.bio || profil.resumeIa || phares.length" class="carte section" data-test="a-propos">
        <h2>{{ t('profil.aPropos.titre') }}</h2>
        <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>
        <div v-if="profil.resumeIa" class="resume-ia">
          <p class="resume-ia__mention">✨ {{ t('profil.aPropos.ia') }}</p>
          <p>{{ profil.resumeIa }}</p>
        </div>
        <div v-if="phares.length" class="principales">
          <span class="principales__icone" aria-hidden="true">◆</span>
          <div>
            <p class="principales__titre">{{ t('profil.phares.titre') }}</p>
            <p data-test="phares">{{ phares.join(' • ') }}</p>
          </div>
        </div>
      </section>

      <!-- Expérience : les projets Jamii d'abord (vérifiés), puis ce qui est déclaré hors de Jamii. -->
      <section v-if="profil.projets.length || profil.realisations.length || moi" class="carte section">
        <h2>{{ t('profil.experience.titre') }}</h2>
        <ul v-if="profil.projets.length" class="lignes" data-test="projets">
          <li v-for="p in profil.projets" :key="p.projetId" class="ligne">
            <span class="logo logo--jamii" aria-hidden="true">{{ initiale(p.titre) }}</span>
            <div class="ligne__texte">
              <h3>
                <a v-if="p.url" :href="p.url" rel="noopener noreferrer" target="_blank">{{ p.titre }}</a>
                <template v-else>{{ p.titre }}</template>
              </h3>
              <p>{{ p.role }} · {{ t('profil.experience.surJamii') }}</p>
              <p v-if="p.paysEquipe?.length" class="texte-discret">{{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</p>
              <JBadgePreuve v-if="p.enLigne" class="ligne__badge">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
            </div>
          </li>
        </ul>
        <div v-else-if="moi" class="vide" data-test="projets-vide">
          <JIllustration nom="en-ligne" class="vide__illustration" />
          <p>{{ t('profil.projets.vide') }}</p>
          <RouterLink to="/vitrine">{{ t('profil.projets.inspiration') }}</RouterLink>
        </div>
        <ul v-if="profil.realisations.length" class="lignes" data-test="realisations">
          <li v-for="(r, i) in profil.realisations" :key="i" class="ligne">
            <span class="logo" aria-hidden="true">{{ initiale(r.titre) }}</span>
            <div class="ligne__texte">
              <h3>
                <a v-if="r.url" :href="r.url" rel="noopener noreferrer" target="_blank">{{ r.titre }}</a>
                <template v-else>{{ r.titre }}</template>
                <span class="declare" :title="t('profil.realisations.declareAide')">{{ t('profil.realisations.declare') }}</span>
              </h3>
              <p v-if="r.role">{{ r.role }}</p>
              <p v-if="r.annee" class="texte-discret">{{ r.annee }}</p>
              <p v-if="r.description" class="ligne__description">{{ r.description }}</p>
              <p v-if="r.technologies?.length" class="ligne__competences">
                <strong>{{ t('profil.experience.competences') }}</strong> {{ r.technologies.join(' · ') }}</p>
            </div>
          </li>
        </ul>
      </section>

      <!-- Activité : ce que la personne a poussé sur GitHub, le plus récent d'abord. -->
      <section v-if="profil.depotsGithub.length" class="carte section">
        <div class="titre-section">
          <div>
            <h2>{{ t('profil.activite.titre') }}</h2>
            <p v-if="profil.loginGithub" class="texte-discret">{{ t('profil.activite.sousTitre', { login: profil.loginGithub }) }}</p>
          </div>
        </div>
        <ul class="activite" data-test="depots">
          <li v-for="d in depotsRecents" :key="d.url" class="publication">
            <p class="publication__entete texte-discret">
              {{ ilYa(d.misAJourLe, locale) ? t('profil.activite.misAJour', { nom: prenom, quand: ilYa(d.misAJourLe, locale) }) : t('profil.activite.depot', { nom: prenom }) }}
            </p>
            <h3><a :href="d.url" rel="noopener noreferrer" target="_blank">{{ d.nom }}</a></h3>
            <p v-if="d.description" class="publication__texte">{{ d.description }}</p>
            <p class="publication__meta">
              <span v-if="d.langage"><span class="pastille-langage" :style="{ background: couleurLangage(d.langage) }" />{{ d.langage }}</span>
              <span>★ {{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span>
            </p>
          </li>
        </ul>
        <a v-if="profil.loginGithub" :href="`https://github.com/${profil.loginGithub}?tab=repositories`" rel="noopener noreferrer" target="_blank" class="pied-section">
          {{ t('profil.depots.toutVoir') }} →</a>
        <p class="texte-discret">{{ t('profil.depots.pasUnePreuve') }}</p>
      </section>

      <!-- Compétences : une ligne par compétence, avec d'où elle vient (au lieu des recommandations de LinkedIn). -->
      <section v-if="profil.competences.length" class="carte section">
        <h2>{{ t('profil.competences.titre') }}</h2>
        <ul class="lignes lignes--serrees" data-test="competences">
          <li v-for="c in competencesAffichees" :key="c.competence + c.source" class="competence">
            <h3>{{ c.competence }}<span v-if="phares.includes(c.competence)" class="competence__phare">{{ t('profil.competences.phare') }}</span></h3>
            <p class="texte-discret competence__source">
              <template v-if="c.source === 'ia'">✨ {{ t('profil.competences.sources.ia') }}<template v-if="c.niveau"> · {{ t(`profil.carte.niveaux.${c.niveau}`) }}</template></template>
              <template v-else>{{ t(`profil.competences.sources.${c.source}`) }}</template>
            </p>
            <p v-if="c.preuves?.length" class="competence__preuves">
              <span v-for="depot in c.preuves" :key="depot" class="depot">{{ depot }}</span>
            </p>
          </li>
        </ul>
        <button v-if="competencesTriees.length > COMPETENCES_VISIBLES" type="button" class="pied-section" :aria-expanded="toutesCompetences" data-test="toutes-competences" @click="toutesCompetences = !toutesCompetences">
          {{ toutesCompetences ? t('profil.competences.moins') : t('profil.competences.toutes', { n: competencesTriees.length }) }}
          {{ toutesCompetences ? '↑' : '→' }}</button>
      </section>

      <section v-if="avis.length" class="carte section" data-test="avis">
        <h2>{{ t('profil.avis.titre') }}</h2>
        <ul class="lignes">
          <li v-for="a in avis" :key="a.id" class="ligne">
            <JAvatar v-if="a.auteur" :nom="a.auteur.nom" :url="a.auteur.avatarUrl" :taille="48" />
            <div class="ligne__texte">
              <h3 v-if="a.auteur"><RouterLink :to="`/p/${a.auteur.id}`">{{ a.auteur.nom }}</RouterLink></h3>
              <p v-if="a.source.titre" class="texte-discret">{{ t('profil.avis.projet', { projet: a.source.titre }) }}</p>
              <blockquote>{{ a.detail }}</blockquote>
            </div>
          </li>
        </ul>
      </section>

      <section v-if="langues.length" class="carte section">
        <h2>{{ t('profil.langues.titre') }}</h2>
        <ul class="lignes lignes--serrees" data-test="langues">
          <li v-for="l in langues" :key="l"><h3>{{ l }}</h3></li>
        </ul>
      </section>
    </div>

    <aside class="colonne colonne--laterale">
      <!-- L'indice de fiabilité : un point par type de preuve, jamais de note globale. -->
      <section class="carte section fiabilite">
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

      <section v-if="profil.langages.length" class="carte section" :aria-label="t('profil.langages.titre')">
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
    </aside>
  </div>
</template>

<style scoped>
/* ---------- En-tête : couverture bord à bord (padding de .carte remis à 0), photo qui déborde à gauche ---------- */
.identite { padding: 0; overflow: hidden; display: grid; }
.identite__couverture {
  position: relative; height: clamp(96px, 18vw, 200px);
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
.identite__corps { display: flex; flex-wrap: wrap; gap: var(--space-4) var(--space-8); justify-content: space-between; align-items: flex-start; padding: 0 var(--space-6) var(--space-6); }
@media (max-width: 640px) { .identite__corps { padding: 0 var(--space-4) var(--space-4); } }
.identite__principal { display: grid; gap: var(--space-1); min-width: 0; flex: 1 1 22rem; justify-items: start; }
.identite__principal > p, .identite__principal > h1 { margin: 0; }
.identite__photo { margin-top: -100px; margin-block-end: var(--space-3); box-shadow: 0 0 0 4px var(--surface); border-radius: 50%; position: relative; }
@media (max-width: 640px) { .identite__photo { margin-top: -64px; width: 104px !important; height: 104px !important; } }
.identite h1 { font-size: clamp(1.625rem, 3vw, 2rem); line-height: 1.15; letter-spacing: -0.4px; }
.identite__titre { font-size: 1.0625rem; line-height: 1.4; }
.identite__infos { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-2); align-items: center; font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.identite__boutons { display: grid; gap: var(--space-3); justify-items: start; margin-top: var(--space-2); }
.identite__boutons:empty { display: none; }
.lieu { display: inline-flex; align-items: center; gap: var(--space-2); }
.drapeau { width: 20px; height: 15px; border-radius: 2px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }
.disponibilite { color: var(--ink); font-weight: 600; }
.disponibilite::before { content: ''; display: inline-block; width: 0.55em; height: 0.55em; margin-inline-end: var(--space-1); border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible::before { background: #2DA44E; }
.liens { list-style: none; margin: var(--space-4) 0 0; padding: 0; display: grid; gap: var(--space-3); flex: 0 1 16rem; min-width: 0; }
.liens a { display: flex; gap: var(--space-3); align-items: center; text-decoration: none; color: var(--ink); }
.liens a:hover strong { text-decoration: underline; }
.liens__logo { flex: none; display: grid; place-items: center; width: 36px; height: 36px; border-radius: var(--radius-sm); background: var(--surface-raised); color: var(--ink); }
.liens__logo--github { background: var(--ink); color: var(--surface); }
.liens__logo--linkedin { background: #0A66C2; color: #fff; }
.liens__texte { display: grid; font-size: var(--type-body-sm-size); line-height: 1.35; min-width: 0; overflow-wrap: anywhere; }
.liens__texte small { color: var(--ink-muted); }

/* ---------- Colonnes et sections ---------- */
.colonnes { display: grid; gap: var(--space-4) var(--space-6); grid-template-columns: minmax(0, 1fr) minmax(16rem, 20rem); align-items: start; }
@media (max-width: 900px) { .colonnes { grid-template-columns: 1fr; } }
.colonne { display: grid; gap: var(--space-4); min-width: 0; }
.section { padding: var(--space-6); }
@media (max-width: 640px) { .section { padding: var(--space-4); } }
.section h2 { font-size: 1.25rem; line-height: 1.3; margin: 0 0 var(--space-4); }
.section h3 { margin: 0; font-size: 1rem; line-height: 1.4; }
.titre-section { margin-block-end: var(--space-4); }
.titre-section h2 { margin: 0; }
.titre-section p { margin: 0; }
.pied-section { display: block; width: calc(100% + 2 * var(--space-6)); margin: var(--space-4) calc(-1 * var(--space-6)) 0; padding: var(--space-3); border: 0; border-top: 1px solid var(--line); background: none; color: var(--ink-muted); font: inherit; font-weight: 600; text-align: center; text-decoration: none; cursor: pointer; }
.pied-section:hover { background: var(--surface-raised); color: var(--ink); }
.pied-section + .texte-discret { margin: var(--space-3) 0 0; }
@media (max-width: 640px) { .pied-section { width: calc(100% + 2 * var(--space-4)); margin-inline: calc(-1 * var(--space-4)); } }

.bio { white-space: pre-line; margin: 0; }
.resume-ia { margin-top: var(--space-4); padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action-soft) 60%, transparent); }
.section > .resume-ia:nth-child(2) { margin-top: 0; }
.resume-ia p { margin: 0; }
.resume-ia__mention { font-size: var(--type-label-size); font-weight: 600; color: var(--ink-muted); margin-block-end: var(--space-2) !important; }
.principales { display: flex; gap: var(--space-3); margin-top: var(--space-4); padding: var(--space-3) var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); }
.section > .principales:nth-child(2) { margin-top: 0; }
.principales p { margin: 0; }
.principales__icone { color: var(--action); }
.principales__titre { font-weight: 700; }

/* Lignes à la LinkedIn : un « logo » carré, puis le texte ; séparées par un trait. */
.lignes { list-style: none; margin: 0; padding: 0; display: grid; }
.lignes > li { padding-block: var(--space-4); border-top: 1px solid var(--line); }
.lignes > li:first-child { border-top: 0; padding-top: 0; }
.lignes > li:last-child { padding-bottom: 0; }
.lignes + .lignes { margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.lignes--serrees > li { padding-block: var(--space-3); }
.ligne { display: flex; gap: var(--space-3); align-items: flex-start; }
.ligne__texte { display: grid; gap: 2px; min-width: 0; flex: 1; justify-items: start; }
.ligne__texte p { margin: 0; font-size: var(--type-body-sm-size); }
.ligne__texte h3 { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.ligne__description { margin-top: var(--space-2) !important; color: var(--ink); }
.ligne__competences { margin-top: var(--space-1) !important; }
.ligne__badge { margin-top: var(--space-2); }
.ligne blockquote { margin: var(--space-2) 0 0; font-size: var(--type-body-sm-size); }
.logo { flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: var(--radius-sm); background: var(--surface-raised); color: var(--ink-muted); font-family: var(--font-display); font-weight: 700; font-size: 1.25rem; }
.logo--jamii { background: var(--action-soft); color: var(--action-ink); }
.declare { font-size: 0.75rem; font-weight: 500; color: var(--ink-muted); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); padding: 0 var(--space-2); }
.vide { display: grid; gap: var(--space-3); justify-items: center; text-align: center; padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); }
.vide p { margin: 0; color: var(--ink-muted); max-width: 30rem; }
.vide__illustration { width: 9rem; }

/* Activité GitHub : des « publications » en carrousel, comme l'activité LinkedIn. */
.activite { list-style: none; margin: 0; padding: 0 0 var(--space-2); display: grid; grid-auto-flow: column; grid-auto-columns: minmax(15rem, 1fr); gap: var(--space-3); overflow-x: auto; scroll-snap-type: x mandatory; }
.publication { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); scroll-snap-align: start; min-width: 0; }
.publication p { margin: 0; }
.publication h3 { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9375rem; overflow-wrap: anywhere; }
.publication__entete { font-size: 0.8125rem; }
.publication__texte { font-size: var(--type-body-sm-size); flex: 1; }
.publication__meta { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-4); font-size: 0.8125rem; color: var(--ink-muted); padding-top: var(--space-2); border-top: 1px solid var(--line); }
.publication__meta > span { display: inline-flex; align-items: center; gap: var(--space-1); }
.pastille-langage { display: inline-block; flex: none; width: 0.75em; height: 0.75em; border-radius: 50%; box-shadow: 0 0 0 1px color-mix(in srgb, var(--ink) 15%, transparent); }

.competence { display: grid; gap: 2px; }
.competence p { margin: 0; }
.competence h3 { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.competence__phare { font-size: 0.75rem; font-weight: 700; color: var(--action-ink); background: var(--action-soft); border-radius: var(--radius-pill); padding: 0 var(--space-2); }
.competence__source { font-size: 0.8125rem; }
.competence__preuves { display: flex; flex-wrap: wrap; gap: var(--space-1); margin-top: var(--space-1) !important; }
.depot { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.8125rem; padding: 0 var(--space-2); border-radius: var(--radius-sm); background: var(--surface-raised); }

/* ---------- Colonne latérale ---------- */
.barre-langages { display: flex; height: 10px; border-radius: var(--radius-pill); overflow: hidden; gap: 2px; margin-block-end: var(--space-4); background: var(--surface-raised); }
.barre-langages > span { min-width: 4px; }
.langages { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.langages li { display: grid; grid-template-columns: auto 1fr auto; gap: var(--space-2); align-items: center; }
.langages__nom { font-weight: 600; }
.points { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
.points li { display: grid; grid-template-columns: auto 1fr; gap: 2px var(--space-3); align-items: baseline; padding: var(--space-3); background: var(--proof-soft); border-radius: var(--radius-sm); }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: 1.25rem; color: var(--proof-ink); }
.points small { grid-column: 2; }
.comment { margin: 0 0 var(--space-3); padding-inline-start: 1.2em; display: grid; gap: var(--space-1); }
.fiabilite > p:last-child, .section > .texte-discret:last-child { margin: 0; }
</style>
