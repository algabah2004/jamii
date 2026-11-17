<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import { JAvatar, JBadgePreuve } from '../../design-system'
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
  type BlocProfil,
  type Preuve,
  type Profil,
} from './profils'

/**
 * Ce que l'on voit d'un profil (#302), sur « Mon profil » comme sur la page publique : le nom en grand, un anneau de
 * preuves autour de la photo, les quatre preuves en chiffres, puis les projets, les compétences, l'activité GitHub et
 * les avis. Une règle visuelle partout : trait plein = vérifié par Jamii, pointillé = déclaré par la personne.
 * `moi` : la personne regarde son propre profil (un crayon ouvre chaque bloc, les états vides disent quoi faire ; un
 * visiteur ne voit que ce qui est rempli). Emplacements : `boutons` (sous le titre), `actions` (sous l'en-tête),
 * `debut` (avant les projets).
 */
const props = withDefaults(defineProps<{ profil: Profil; moi?: boolean }>(), { moi: false })
const emit = defineEmits<{ modifier: [bloc: BlocProfil] }>()
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
/** Les cinq premières, puis « Afficher les N compétences ». */
const COMPETENCES_VISIBLES = 5
const toutesCompetences = ref(false)
const competencesAffichees = computed(() =>
  toutesCompetences.value ? competencesTriees.value : competencesTriees.value.slice(0, COMPETENCES_VISIBLES),
)
const cartesPhares = computed(() =>
  phares.value.map((nom) => ({ nom, competence: props.profil.competences.find((c) => c.competence === nom) })),
)
const langues = computed(() => props.profil.langues.map((l) => nomLangue(l, locale.value)))
/** L'activité GitHub : les dépôts du plus récemment mis à jour au plus ancien. */
const depotsRecents = computed(() =>
  [...props.profil.depotsGithub].sort((a, b) => (b.misAJourLe ?? '').localeCompare(a.misAJourLe ?? '')),
)
const etoiles = computed(() => props.profil.depotsGithub.reduce((total, d) => total + d.etoiles, 0))
const nombre = (n: number) => new Intl.NumberFormat(locale.value).format(n)
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const
/** Où l'on gagne chaque preuve : un clic sur la preuve à venir y mène. */
const CHEMINS_PREUVE = {
  projet_en_ligne: '/cercles',
  tache_livree_a_temps: '/cercles',
  reponse_acceptee: '/communautes',
  avis_coequipier: '/cercles',
} as const

/*
 * L'anneau : un arc par type de preuve, plein quand la personne en a au moins une. Le fond en pointillé dit « pas
 * encore ». Jamais de note globale (charte) : l'anneau montre ce qui existe, il ne compte pas.
 */
const ARCS = computed(() =>
  TYPES_PREUVE.map((type, i) => ({
    type,
    obtenu: (props.profil.fiabilite.find((f) => f.type === type)?.nombre ?? 0) > 0,
    rotation: -84 + i * 90,
  })),
)
const resumeAnneau = computed(() => {
  const obtenues = ARCS.value.filter((a) => a.obtenu).map((a) => t(`profil.fiabilite.types.${a.type}`, 2))
  return obtenues.length ? t('profil.anneau.avec', { liste: obtenues.join(', ') }) : t('profil.anneau.sans')
})

/** Les liens de navigation : seulement vers les sections qui existent. */
const sections = computed(() =>
  [
    { id: 'projets', titre: t('profil.experience.titre'), present: props.profil.projets.length > 0 || props.profil.realisations.length > 0 },
    { id: 'competences', titre: t('profil.competences.titre'), present: props.profil.competences.length > 0 },
    { id: 'github', titre: t('profil.activite.titre'), present: props.profil.depotsGithub.length > 0 },
    { id: 'avis', titre: t('profil.avis.titre'), present: avis.value.length > 0 },
  ].filter((s) => s.present),
)

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
  <!-- L'en-tête : le nom en grand, le titre, puis l'anneau de preuves autour de la photo. -->
  <header class="identite" data-test="identite">
    <div class="identite__texte">
      <p class="identite__ligne">
        <span class="disponibilite" :class="`disponibilite--${profil.disponibilite}`">
          {{ t(`profil.disponibilite.${profil.disponibilite}`) }}<template v-if="profil.heuresParSemaine"> · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</template>
        </span>
        <span v-if="lieu" class="lieu">
          <img v-if="drapeau" :src="drapeau" alt="" class="drapeau" width="20" height="15" data-test="drapeau" />
          {{ lieu }}
        </span>
        <button v-if="moi" type="button" class="crayon" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.lieu') })" data-test="crayon-lieu" @click="emit('modifier', 'lieu')">
          <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </p>
      <h1>{{ profil.nom }}<span class="identite__point" aria-hidden="true">.</span></h1>
      <p v-if="profil.titre" class="identite__titre" data-test="titre">{{ profil.titre }}</p>
      <button v-if="moi" type="button" class="crayon crayon--texte" data-test="crayon-identite" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.identite') })" @click="emit('modifier', 'identite')">
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        {{ t('profil.modifier.blocs.identite') }}
      </button>
      <div class="identite__boutons"><slot name="boutons" /></div>
    </div>

    <div class="anneau">
      <div class="anneau__cercle">
        <svg class="anneau__svg" viewBox="0 0 180 180" role="img" :aria-label="resumeAnneau">
          <circle cx="90" cy="90" r="84" fill="none" stroke="var(--line-strong)" stroke-width="2" stroke-dasharray="2 8" stroke-linecap="round" />
          <circle
            v-for="arc in ARCS" v-show="arc.obtenu" :key="arc.type" class="anneau__arc" :class="`anneau__arc--${arc.type}`"
            cx="90" cy="90" r="84" fill="none" stroke-width="7" stroke-linecap="round" stroke-dasharray="116 412"
            :transform="`rotate(${arc.rotation} 90 90)`"
          />
        </svg>
        <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="136" class="anneau__photo" />
      </div>
    </div>
  </header>

  <slot name="actions" />

  <!-- Les preuves, en chiffres : seul ce que Jamii a vérifié. Jamais de note globale. Sans preuve, un visiteur ne voit rien ;
       la personne elle-même voit les quatre preuves à gagner, chacune menant là où on les obtient. -->
  <section v-if="profil.fiabilite.length || moi" class="preuves" :aria-label="t('profil.fiabilite.titre', { prenom })">
    <h2 class="preuves__titre">{{ profil.fiabilite.length ? t('profil.fiabilite.titre', { prenom }) : t('profil.fiabilite.titreAVenir') }}</h2>
    <ul v-if="profil.fiabilite.length" class="points" data-test="fiabilite">
      <li v-for="f in profil.fiabilite" :key="f.type" :class="`point--${f.type}`">
        <span class="points__nombre">{{ f.sur != null ? t('profil.fiabilite.sur', { n: f.nombre, sur: f.sur }) : f.nombre }}</span>
        <span>{{ t(`profil.fiabilite.types.${f.type}`, f.nombre) }}</span>
        <small v-if="f.contestees" class="texte-discret">{{ t('profil.fiabilite.contestees', { n: f.contestees }, f.contestees) }}</small>
      </li>
    </ul>
    <div v-else class="a-venir" data-test="fiabilite-vide">
      <p>{{ t('profil.fiabilite.vide') }}</p>
      <ul>
        <li v-for="type in TYPES_PREUVE" :key="type" :class="`a-venir--${type}`">
          <RouterLink :to="CHEMINS_PREUVE[type]">
            <span class="a-venir__marque" aria-hidden="true" />
            {{ t(`profil.fiabilite.types.${type}`, 1) }}
          </RouterLink>
        </li>
      </ul>
    </div>
    <p v-if="profil.fiabilite.length" class="preuves__pied texte-discret">
      <span class="legende"><span class="legende__plein" aria-hidden="true" />{{ t('profil.preuves.plein') }}</span>
      <span class="legende"><span class="legende__pointille" aria-hidden="true" />{{ t('profil.preuves.pointille') }}</span>
      <span>{{ t('profil.fiabilite.sansNote') }}</span>
    </p>
  </section>

  <nav v-if="sections.length > 1" class="navigation" :aria-label="t('profil.navigation')">
    <a v-for="s in sections" :key="s.id" :href="`#${s.id}`">{{ s.titre }}</a>
  </nav>

  <div class="colonnes">
    <div class="colonne">
      <slot name="debut" />

      <!-- Projets : ceux de Jamii d'abord (vérifiés, trait plein), puis ce qui est déclaré hors de Jamii (pointillé). -->
      <section v-if="profil.projets.length || profil.realisations.length" id="projets" class="section" aria-labelledby="titre-projets">
        <div class="section__tete">
          <h2 id="titre-projets">{{ t('profil.experience.titre') }}</h2>
          <button v-if="moi" type="button" class="crayon" data-test="crayon-realisations" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.realisations') })" @click="emit('modifier', 'realisations')">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
        <ul v-if="profil.projets.length" class="projets" data-test="projets">
          <li v-for="(p, i) in profil.projets" :key="p.projetId" class="projet" :class="{ 'projet--phare': i === 0 }">
            <JBadgePreuve v-if="p.enLigne">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
            <h3>
              <a v-if="p.url" :href="p.url" rel="noopener noreferrer" target="_blank">{{ p.titre }}</a>
              <template v-else>{{ p.titre }}</template>
            </h3>
            <p class="projet__role">{{ p.role }} · {{ t('profil.experience.surJamii') }}</p>
            <p v-if="p.paysEquipe?.length" class="texte-discret">{{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</p>
          </li>
        </ul>
        <ul v-if="profil.realisations.length" class="projets projets--declares" data-test="realisations">
          <li v-for="(r, i) in profil.realisations" :key="i" class="projet projet--declare">
            <span class="declare" :title="t('profil.realisations.declareAide')">{{ t('profil.realisations.declare') }}<template v-if="r.annee"> · {{ r.annee }}</template></span>
            <h3>
              <a v-if="r.url" :href="r.url" rel="noopener noreferrer" target="_blank">{{ r.titre }}</a>
              <template v-else>{{ r.titre }}</template>
            </h3>
            <p v-if="r.description" class="projet__description">{{ r.description }}</p>
            <p v-if="r.role || r.technologies?.length" class="projet__role">
              <strong v-if="r.role">{{ r.role }}</strong><template v-if="r.role && r.technologies?.length"> · </template>{{ r.technologies?.join(', ') }}
            </p>
          </li>
        </ul>
      </section>

      <!-- Compétences : les trois mises en avant en grand, puis une ligne par compétence avec d'où elle vient. -->
      <section v-if="profil.competences.length" id="competences" class="section" aria-labelledby="titre-competences">
        <div class="section__tete">
          <h2 id="titre-competences">{{ t('profil.competences.titre') }}</h2>
          <button v-if="moi" type="button" class="crayon" data-test="crayon-competences" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.competences') })" @click="emit('modifier', 'competences')">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
        <ul v-if="cartesPhares.length" class="phares" data-test="phares">
          <li v-for="p in cartesPhares" :key="p.nom">
            <span class="phares__mention">{{ t('profil.competences.phare') }}</span>
            <strong class="phares__nom">{{ p.nom }}</strong>
            <span v-if="p.competence?.niveau" class="texte-discret">{{ t(`profil.carte.niveaux.${p.competence.niveau}`) }}<template v-if="p.competence.preuves?.length"> · {{ t('profil.competences.preuvesNombre', { n: p.competence.preuves.length }, p.competence.preuves.length) }}</template></span>
          </li>
        </ul>
        <ul class="lignes" data-test="competences">
          <li v-for="c in competencesAffichees" :key="c.competence + c.source" class="competence">
            <h3>{{ c.competence }}</h3>
            <p class="texte-discret competence__source">
              <template v-if="c.source === 'ia'">✨ {{ t('profil.competences.sources.ia') }}<template v-if="c.niveau"> · {{ t(`profil.carte.niveaux.${c.niveau}`) }}</template></template>
              <template v-else>{{ t(`profil.competences.sources.${c.source}`) }}</template>
            </p>
            <p v-if="c.preuves?.length" class="competence__preuves">
              <span v-for="depot in c.preuves" :key="depot" class="depot">{{ depot }}</span>
            </p>
          </li>
        </ul>
        <button v-if="competencesTriees.length > COMPETENCES_VISIBLES" type="button" class="bouton-lien" :aria-expanded="toutesCompetences" data-test="toutes-competences" @click="toutesCompetences = !toutesCompetences">
          {{ toutesCompetences ? t('profil.competences.moins') : t('profil.competences.toutes', { n: competencesTriees.length }) }}
          {{ toutesCompetences ? '↑' : '→' }}</button>

        <div v-if="profil.langages.length" class="langages-bloc" :aria-label="t('profil.langages.titre')">
          <h3>{{ t('profil.langages.titre') }}</h3>
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
        </div>
      </section>
      <!-- Sans compétence, les langages restent visibles : ils viennent des dépôts. -->
      <section v-else-if="profil.langages.length" class="section" :aria-label="t('profil.langages.titre')">
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

      <!-- Activité : ce que la personne a poussé sur GitHub, le plus récent d'abord. -->
      <section v-if="profil.depotsGithub.length" id="github" class="section" aria-labelledby="titre-github">
        <div class="section__tete">
          <div>
            <h2 id="titre-github">{{ t('profil.activite.titre') }}</h2>
            <p v-if="profil.loginGithub" class="texte-discret">{{ t('profil.activite.sousTitre', { login: profil.loginGithub }) }}</p>
          </div>
          <a v-if="profil.loginGithub" :href="`https://github.com/${profil.loginGithub}?tab=repositories`" rel="noopener noreferrer" target="_blank" class="bouton-lien">{{ t('profil.depots.toutVoir') }} →</a>
        </div>
        <ul class="activite" data-test="depots">
          <li v-for="d in depotsRecents" :key="d.url" class="depot-ligne">
            <h3><a :href="d.url" rel="noopener noreferrer" target="_blank">{{ d.nom }}</a></h3>
            <p v-if="d.description" class="depot-ligne__texte">{{ d.description }}</p>
            <p class="depot-ligne__meta texte-discret">
              <span v-if="d.langage"><span class="pastille-langage" :style="{ background: couleurLangage(d.langage) }" />{{ d.langage }}</span>
              <span>★ {{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span>
              <span>{{ ilYa(d.misAJourLe, locale) ? t('profil.activite.misAJour', { nom: prenom, quand: ilYa(d.misAJourLe, locale) }) : t('profil.activite.depot', { nom: prenom }) }}</span>
            </p>
          </li>
        </ul>
        <p class="texte-discret">{{ t('profil.depots.pasUnePreuve') }}</p>
      </section>

      <section v-if="avis.length" id="avis" class="section" data-test="avis" aria-labelledby="titre-avis">
        <h2 id="titre-avis">{{ t('profil.avis.titre') }}</h2>
        <ul class="avis">
          <li v-for="a in avis" :key="a.id">
            <figure>
              <blockquote>{{ a.detail }}</blockquote>
              <figcaption>
                <JAvatar v-if="a.auteur" :nom="a.auteur.nom" :url="a.auteur.avatarUrl" :taille="40" />
                <span>
                  <RouterLink v-if="a.auteur" :to="`/p/${a.auteur.id}`">{{ a.auteur.nom }}</RouterLink>
                  <span v-if="a.source.titre" class="texte-discret"><br />{{ t('profil.avis.projet', { projet: a.source.titre }) }}</span>
                </span>
              </figcaption>
            </figure>
          </li>
        </ul>
      </section>
    </div>

    <!-- À droite : qui est la personne, en bref. -->
    <aside class="colonne colonne--laterale">
      <section v-if="profil.bio || profil.resumeIa" class="lateral" data-test="a-propos">
        <div class="section__tete">
          <h2>{{ t('profil.aPropos.titre') }}</h2>
          <button v-if="moi" type="button" class="crayon" data-test="crayon-bio" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.identite') })" @click="emit('modifier', 'identite')">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
        <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>
        <div v-if="profil.resumeIa" class="resume-ia">
          <p class="resume-ia__mention">✨ {{ t('profil.aPropos.ia') }}</p>
          <p>{{ profil.resumeIa }}</p>
        </div>
      </section>

      <section v-if="langues.length" class="lateral" aria-labelledby="titre-langues">
        <div class="section__tete">
          <h2 id="titre-langues">{{ t('profil.langues.titre') }}</h2>
          <button v-if="moi" type="button" class="crayon" data-test="crayon-langues" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.langues') })" @click="emit('modifier', 'langues')">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
        <ul class="pastilles" data-test="langues">
          <li v-for="l in langues" :key="l">{{ l }}</li>
        </ul>
      </section>

      <!-- À droite, comme sur GitHub : GitHub et les liens de la personne. -->
      <section v-if="profil.loginGithub || profil.linkedin || profil.siteWeb" class="lateral" aria-labelledby="titre-liens">
        <div class="section__tete">
          <h2 id="titre-liens">{{ t('profil.liens.titre') }}</h2>
          <button v-if="moi" type="button" class="crayon" data-test="crayon-liens" :aria-label="t('profil.modifier.crayon', { bloc: t('profil.modifier.blocs.liens') })" @click="emit('modifier', 'liens')">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M11 2.5l2.5 2.5L5.5 13l-3 .5.5-3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </div>
        <ul class="liens" data-test="liens">
          <li v-if="profil.loginGithub">
            <a :href="`https://github.com/${profil.loginGithub}`" rel="noopener noreferrer" target="_blank">
              <strong>GitHub</strong>
              <span class="liens__valeur">@{{ profil.loginGithub }}<template v-if="etoiles"> · ★ {{ t('profil.depots.etoiles', { n: nombre(etoiles) }, etoiles) }}</template></span>
            </a>
          </li>
          <li v-if="profil.linkedin">
            <a :href="profil.linkedin" rel="noopener noreferrer" target="_blank">
              <strong>LinkedIn</strong>
              <span class="liens__valeur">{{ hoteLisible(profil.linkedin) }}</span>
            </a>
          </li>
          <li v-if="profil.siteWeb">
            <a :href="profil.siteWeb" rel="noopener noreferrer" target="_blank">
              <strong>{{ t('profil.liens.site') }}</strong>
              <span class="liens__valeur">{{ hoteLisible(profil.siteWeb) }}</span>
            </a>
          </li>
        </ul>
      </section>
    </aside>
  </div>
</template>

<style scoped>
/* ---------- En-tête : le nom en grand, l'anneau de preuves autour de la photo ---------- */
.identite { display: flex; flex-wrap: wrap; gap: var(--space-8) var(--space-12); align-items: center; justify-content: space-between; padding-block: var(--space-6) var(--space-8); }
.identite__texte { flex: 999 1 22rem; min-width: 0; display: grid; gap: var(--space-3); justify-items: start; }
.identite__texte > * { margin: 0; }
.identite__ligne { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2) var(--space-4); font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.identite h1 { font-size: clamp(2.25rem, 5.5vw, var(--type-display-size)); line-height: 1.05; letter-spacing: -0.03em; font-weight: var(--type-display-weight); overflow-wrap: anywhere; }
.identite__point { color: var(--proof); }
.identite__titre { font-family: var(--font-display); font-size: var(--type-h3-size); line-height: var(--type-h3-line); max-width: 34rem; }
.identite__titre--vide { color: var(--ink-muted); font-family: var(--font-text); font-size: var(--type-body-size); }
.identite__boutons { display: grid; gap: var(--space-3); justify-items: start; margin-top: var(--space-2); }
.identite__boutons:empty { display: none; }
.lieu { display: inline-flex; align-items: center; gap: var(--space-2); }
.drapeau { width: 20px; height: 15px; border-radius: 2px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }
.disponibilite { display: inline-flex; align-items: center; gap: var(--space-2); padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink); font-weight: 600; }
.disponibilite::before { content: ''; width: 0.55em; height: 0.55em; border-radius: 50%; background: var(--line-strong); }
.disponibilite--disponible { background: color-mix(in srgb, var(--en-ligne) 14%, var(--surface)); }
.disponibilite--disponible::before { background: var(--en-ligne); }

/* L'anneau : un arc par type de preuve, plein = obtenu. Les couleurs sont celles des chiffres en dessous. */
.anneau { flex: 0 0 auto; }
.anneau__cercle { position: relative; width: 11.25rem; height: 11.25rem; }
.anneau__svg { position: absolute; inset: 0; width: 100%; height: 100%; }
.anneau__photo { position: absolute; inset: 1.375rem; width: 8.5rem !important; height: 8.5rem !important; }
.anneau__arc--projet_en_ligne { stroke: var(--proof); }
.anneau__arc--tache_livree_a_temps { stroke: var(--action); }
.anneau__arc--reponse_acceptee { stroke: color-mix(in srgb, var(--proof) 55%, var(--surface)); }
.anneau__arc--avis_coequipier { stroke: color-mix(in srgb, var(--action) 55%, var(--surface)); }
@media (max-width: 640px) { .anneau__cercle { width: 9rem; height: 9rem; } .anneau__photo { inset: 1.125rem; width: 6.75rem !important; height: 6.75rem !important; } }

.crayon { flex: none; display: inline-flex; align-items: center; justify-content: center; gap: var(--space-2); width: 44px; height: 44px; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: var(--surface-raised); color: var(--ink); font: inherit; font-weight: 600; cursor: pointer; }
.crayon:hover { border-color: var(--action); color: var(--action-ink); }
.crayon--texte { width: auto; padding: 0 var(--space-4); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); }

/* ---------- Les preuves en chiffres ---------- */
.preuves { border-block: 1px solid var(--line); background: var(--surface-raised); margin-inline: calc(-1 * var(--space-4)); padding: var(--space-6) var(--space-4); }
.preuves__titre { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-muted); margin: 0 0 var(--space-4); font-family: var(--font-text); }
.points { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr)); }
.points li { display: grid; gap: var(--space-1); align-content: start; padding: var(--space-2) var(--space-6); border-inline-start: 1px solid var(--line); }
.points li:first-child { padding-inline-start: 0; border-inline-start: 0; }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: var(--type-display-size); line-height: 1; letter-spacing: -0.03em; }
.point--projet_en_ligne .points__nombre { color: var(--proof-ink); }
.point--tache_livree_a_temps .points__nombre { color: var(--action-ink); }
.point--reponse_acceptee .points__nombre { color: color-mix(in srgb, var(--proof-ink) 70%, var(--ink)); }
.point--avis_coequipier .points__nombre { color: color-mix(in srgb, var(--action-ink) 70%, var(--ink)); }
.points li > span:nth-child(2) { font-weight: 600; }
/* Les preuves à gagner (la personne seule les voit) : quatre cases en pointillé, chacune de la couleur de son arc. */
.a-venir { display: grid; gap: var(--space-4); }
.a-venir > p { margin: 0; color: var(--ink-muted); }
.a-venir ul { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 8rem), 1fr)); gap: var(--space-3); }
.a-venir a { display: grid; gap: var(--space-3); height: 100%; box-sizing: border-box; padding: var(--space-4); border: 2px dashed var(--line-strong); border-radius: var(--radius-lg); color: var(--ink); font-weight: 600; text-decoration: none; }
.a-venir a:hover { border-style: solid; border-color: var(--ink); }
.a-venir__marque { width: 28px; height: 6px; border-radius: var(--radius-pill); background: var(--proof); }
.a-venir--tache_livree_a_temps .a-venir__marque { background: var(--action); }
.a-venir--reponse_acceptee .a-venir__marque { background: color-mix(in srgb, var(--proof) 55%, var(--surface)); }
.a-venir--avis_coequipier .a-venir__marque { background: color-mix(in srgb, var(--action) 55%, var(--surface)); }
.preuves__pied { margin: var(--space-4) 0 0; display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-6); font-size: var(--type-body-sm-size); }
.legende { display: inline-flex; align-items: center; gap: var(--space-2); }
.legende__plein, .legende__pointille { width: 22px; height: 12px; border-radius: 4px; box-sizing: border-box; border: 2px solid var(--ink); }
.legende__pointille { border: 2px dashed var(--line-strong); }

.navigation { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-6); padding-block: var(--space-2); border-bottom: 1px solid var(--line); margin-top: var(--space-2); }
.navigation a { padding: var(--space-3) 0; font-weight: 600; color: var(--ink-muted); text-decoration: none; border-bottom: 3px solid transparent; }
.navigation a:hover { color: var(--ink); border-bottom-color: var(--proof); }

/* ---------- Colonnes et sections ---------- */
.colonnes { display: grid; gap: var(--space-12) var(--space-12); grid-template-columns: minmax(0, 1fr) minmax(16rem, 20rem); align-items: start; padding-block: var(--space-8) var(--space-6); }
@media (max-width: 900px) { .colonnes { grid-template-columns: minmax(0, 1fr); } }
/* minmax(0, 1fr) : sans lui, la piste prend la largeur minimale de son contenu le plus large, et la colonne sortait
   de l'écran de 22 px à 320 px (docs/responsive.md). */
.colonne { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-12); min-width: 0; }
.colonne--laterale { gap: var(--space-8); }
.section { display: grid; gap: var(--space-4); }
.section h2, .lateral h2 { margin: 0; }
.section h3 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.section__tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2) var(--space-4); }
.section__tete > div > p { margin: 0; }
.lateral { display: grid; gap: var(--space-3); }
.lateral h2 { font-family: var(--font-text); font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-muted); }
.lateral p { margin: 0; }
.bio { white-space: pre-line; }
.resume-ia { padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action-soft) 60%, transparent); }
.resume-ia p { margin: 0; font-size: var(--type-body-sm-size); }
.resume-ia__mention { font-weight: 600; color: var(--ink-muted); margin-block-end: var(--space-2) !important; }
.bouton-lien { align-self: start; justify-self: start; padding: var(--space-2) 0; border: 0; background: none; color: var(--action-ink); font: inherit; font-weight: 600; text-decoration: none; cursor: pointer; }
.bouton-lien:hover { text-decoration: underline; }

/* ---------- Projets : trait plein = vérifié par Jamii, pointillé = déclaré ---------- */
.projets { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); }
.projet { display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); border: 2px solid var(--ink); border-radius: var(--radius-lg); background: var(--surface); }
.projet--phare { grid-column: 1 / -1; }
.projet--phare h3 { font-size: var(--type-h2-size); line-height: var(--type-h2-line); }
.projet--declare { border: 2px dashed var(--line-strong); background: none; }
.projet p { margin: 0; }
.projet__role { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.projet__role strong { color: var(--ink); }
.projet__description { color: var(--ink-muted); }
.projet :deep(.badge-preuve), .projet > :first-child:not(h3) { justify-self: start; }
.declare { justify-self: start; padding: 0 var(--space-3); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); font-size: var(--type-label-size); font-weight: 600; color: var(--ink-muted); }

/* ---------- Compétences ---------- */
.phares { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr)); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); overflow: hidden; }
.phares li { display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); border-inline-start: 1px solid var(--line); }
.phares li:first-child { border-inline-start: 0; }
.phares__mention { font-size: var(--type-label-size); font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--proof-ink); }
.phares__nom { font-family: var(--font-display); font-size: var(--type-h1-size); line-height: 1.05; letter-spacing: -0.02em; overflow-wrap: anywhere; }
.lignes { list-style: none; margin: 0; padding: 0; display: grid; }
.competence { display: grid; grid-template-columns: minmax(8rem, 12rem) minmax(0, 14rem) minmax(0, 1fr); gap: var(--space-1) var(--space-6); align-items: center; padding-block: var(--space-4); border-top: 1px solid var(--line); }
.competence:last-child { border-bottom: 1px solid var(--line); }
.competence p { margin: 0; }
.competence h3 { font-size: var(--type-body-size); line-height: var(--type-body-line); font-weight: 700; }
.competence__source { font-size: var(--type-body-sm-size); }
.competence__preuves { display: flex; flex-wrap: wrap; gap: var(--space-2); }
@media (max-width: 760px) { .competence { grid-template-columns: minmax(0, 1fr); } }
.depot { padding: 0 var(--space-2); border: 1px solid var(--ink); border-radius: var(--radius-sm); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.8125rem; }
.langages-bloc { display: grid; gap: var(--space-3); margin-top: var(--space-2); }
.langages-bloc h3 { margin: 0; }
.langages-bloc p { margin: 0; }
.barre-langages { display: flex; height: 14px; border-radius: var(--radius-pill); overflow: hidden; gap: 3px; background: var(--surface-raised); }
.barre-langages > span { min-width: 4px; }
.langages { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-6); }
.langages li { display: inline-flex; align-items: center; gap: var(--space-2); }
.langages__nom { font-weight: 600; }
.pastille-langage { display: inline-block; flex: none; width: 0.75em; height: 0.75em; margin-inline-end: var(--space-1); border-radius: 50%; box-shadow: 0 0 0 1px color-mix(in srgb, var(--ink) 15%, transparent); }

/* ---------- Activité GitHub : des lignes, pas des cartes ---------- */
.activite { list-style: none; margin: 0; padding: 0; display: grid; }
.depot-ligne { display: grid; gap: var(--space-1); padding-block: var(--space-4); border-top: 1px solid var(--line); }
.depot-ligne:last-child { border-bottom: 1px solid var(--line); }
.depot-ligne h3 { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: var(--type-body-size); line-height: var(--type-body-line); overflow-wrap: anywhere; }
.depot-ligne p { margin: 0; }
.depot-ligne__texte { color: var(--ink-muted); }
.depot-ligne__meta { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-6); font-size: var(--type-body-sm-size); }
.depot-ligne__meta > span { display: inline-flex; align-items: center; }
.section > .texte-discret { margin: 0; }

/* ---------- Avis ---------- */
.avis { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: var(--space-4); }
.avis figure { margin: 0; height: 100%; box-sizing: border-box; display: grid; gap: var(--space-4); align-content: space-between; padding: var(--space-6); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); }
.avis blockquote { margin: 0; font-family: var(--font-display); font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.avis figcaption { display: flex; align-items: center; gap: var(--space-3); font-size: var(--type-body-sm-size); }

/* ---------- Colonne latérale ---------- */
.pastilles { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.pastilles li { padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--surface-raised); font-weight: 600; }
.liens { list-style: none; margin: 0; padding: 0; display: grid; }
.liens li { border-top: 1px solid var(--line); }
.liens li:last-child { border-bottom: 1px solid var(--line); }
.liens a { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-1) var(--space-3); min-height: 48px; color: var(--ink); text-decoration: none; }
.liens a:hover strong { text-decoration: underline; }
.liens__valeur { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: var(--type-body-sm-size); color: var(--ink-muted); overflow-wrap: anywhere; }

@media (prefers-reduced-motion: no-preference) {
  .projet, .crayon { transition: border-color var(--motion-fast) var(--ease-out), color var(--motion-fast) var(--ease-out); }
}
</style>
