<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBadgePreuve, JIllustration } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import IconeProfil from './IconeProfil.vue'
import type { SectionEdition } from './completude'
import { competencesPhares, hoteLisible, nomLangue, pourcentage, urlDrapeau, type Profil } from './profils'

/**
 * Ce que l'on voit d'un profil (#159), sur « Mon profil » comme sur la page publique. `moi` : la personne regarde
 * son propre profil (les sections vides lui disent quoi faire et ouvrent le bon endroit du panneau « Modifier » ;
 * un visiteur ne voit que ce qui est rempli).
 */
const props = withDefaults(defineProps<{ profil: Profil; moi?: boolean }>(), { moi: false })
const emit = defineEmits<{ modifier: [section: SectionEdition] }>()
const { t, locale } = useI18n()

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
const TYPES_PREUVE = ['projet_en_ligne', 'tache_livree_a_temps', 'reponse_acceptee', 'avis_coequipier'] as const

/** Les chiffres de l'en-tête : uniquement des faits comptés, jamais une note. */
const chiffres = computed(() => [
  { cle: 'projets', valeur: props.profil.projets.filter((p) => p.enLigne).length },
  { cle: 'realisations', valeur: props.profil.realisations.length },
  { cle: 'competences', valeur: props.profil.competences.length },
  { cle: 'preuves', valeur: props.profil.fiabilite.reduce((n, f) => n + f.nombre, 0) },
])
const misAJour = computed(() =>
  new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(props.profil.misAJourLe)),
)
</script>

<template>
  <header class="hero" data-test="identite">
    <div class="hero__fond" aria-hidden="true"><i /><i /></div>
    <div class="hero__grille">
      <div class="hero__identite">
        <div class="hero__photo">
          <JAvatar :nom="profil.nom" :url="profil.avatarUrl" :taille="128" />
          <span class="hero__point" :class="`hero__point--${profil.disponibilite}`" aria-hidden="true" />
        </div>
        <p class="hero__surtitre" :class="`hero__surtitre--${profil.disponibilite}`">
          <i aria-hidden="true" />{{ t(`profil.disponibilite.${profil.disponibilite}`) }}<template v-if="profil.heuresParSemaine">
            · {{ t('profil.heures', { n: profil.heuresParSemaine }) }}</template>
        </p>
        <h1>{{ profil.nom }}<span v-if="profil.titre" class="hero__titre" data-test="titre">{{ profil.titre }}</span></h1>

        <ul v-if="lieu || langues.length" class="hero__meta">
          <li v-if="lieu">
            <img v-if="drapeau" :src="drapeau" alt="" class="drapeau" width="22" height="16" data-test="drapeau" />
            <IconeProfil v-else nom="lieu" />
            {{ lieu }}
          </li>
          <li v-if="langues.length" data-test="langues">
            <IconeProfil nom="langue" /><span class="visuellement-cache">{{ t('profil.langues.parle') }}</span>
            {{ langues.join(' · ') }}
          </li>
        </ul>

        <ul v-if="profil.loginGithub || profil.linkedin || profil.siteWeb" class="hero__liens" data-test="liens">
          <li v-if="profil.loginGithub">
            <a :href="`https://github.com/${profil.loginGithub}`" rel="noopener noreferrer" target="_blank">
              <IconeProfil nom="github" /><span class="visuellement-cache">GitHub</span> @{{ profil.loginGithub }}</a>
          </li>
          <li v-if="profil.linkedin">
            <a :href="profil.linkedin" rel="noopener noreferrer" target="_blank"><IconeProfil nom="linkedin" /> LinkedIn</a>
          </li>
          <li v-if="profil.siteWeb">
            <a :href="profil.siteWeb" rel="noopener noreferrer" target="_blank">
              <IconeProfil nom="site" /><span class="visuellement-cache">{{ t('profil.liens.site') }}</span> {{ hoteLisible(profil.siteWeb) }}</a>
          </li>
        </ul>

        <ul v-if="phares.length" class="hero__phares" :aria-label="t('profil.phares.titre')" data-test="phares">
          <li v-for="c in phares" :key="c"><IconeProfil nom="etoile" :taille="14" />{{ c }}</li>
        </ul>
      </div>

      <aside class="hero__chiffres" :aria-label="t('profil.chiffres.titre')">
        <p class="hero__chiffres-titre"><i aria-hidden="true" />{{ t('profil.chiffres.titre') }}</p>
        <dl>
          <div v-for="c in chiffres" :key="c.cle" :class="{ 'hero__chiffre--preuve': c.cle === 'projets' && c.valeur }">
            <dt>{{ t(`profil.chiffres.${c.cle}`, c.valeur) }}</dt>
            <dd>{{ c.valeur }}</dd>
          </div>
        </dl>
        <p class="hero__maj"><IconeProfil nom="rafraichir" :taille="14" />{{ t('profil.chiffres.misAJour', { date: misAJour }) }}</p>
      </aside>

      <div v-if="$slots.actions" class="hero__actions"><slot name="actions" /></div>
    </div>
  </header>

  <slot name="apres-entete" />

  <div class="colonnes">
    <div class="colonne">
      <section v-if="profil.bio || moi" class="bloc" aria-labelledby="bloc-presentation">
        <div class="bloc__tete">
          <span class="bloc__icone"><IconeProfil nom="personne" /></span>
          <h2 id="bloc-presentation">{{ t('profil.presentation.titre', { prenom }) }}</h2>
        </div>
        <p v-if="profil.bio" class="bio">{{ profil.bio }}</p>
        <button v-else type="button" class="vide-action" data-test="ajouter-bio" @click="emit('modifier', 'essentiel')">
          <IconeProfil nom="plus" />{{ t('profil.presentation.vide') }}
        </button>
      </section>

      <slot name="debut" />

      <!-- Les projets d'abord : c'est la preuve la plus forte (contrat : Profil.projets). -->
      <section v-if="profil.projets.length || moi" class="bloc" aria-labelledby="bloc-projets">
        <div class="bloc__tete">
          <span class="bloc__icone bloc__icone--preuve"><IconeProfil nom="fusee" /></span>
          <div>
            <h2 id="bloc-projets">{{ t('profil.projets.titre') }}</h2>
            <p class="bloc__aide">{{ t('profil.projets.aide') }}</p>
          </div>
        </div>
        <ul v-if="profil.projets.length" class="projets" data-test="projets">
          <li v-for="p in profil.projets" :key="p.projetId">
            <div class="projets__texte">
              <strong>{{ p.titre }}</strong>
              <span class="texte-discret">{{ p.role }}<template v-if="p.paysEquipe?.length"> · {{ t('profil.projets.equipe', { pays: p.paysEquipe.map((c) => nomPays(c, locale)).join(' · ') }) }}</template></span>
            </div>
            <JBadgePreuve v-if="p.enLigne">✓ {{ p.hebergeur === 'datacloud' ? t('profil.projets.surSystalink') : t('profil.projets.enLigne') }}</JBadgePreuve>
          </li>
        </ul>
        <div v-else class="vide" data-test="projets-vide">
          <JIllustration nom="en-ligne" class="vide__illustration" />
          <p>{{ t('profil.projets.vide') }}</p>
          <RouterLink to="/vitrine" class="vide__lien">{{ t('profil.projets.inspiration') }} <IconeProfil nom="fleche" :taille="14" /></RouterLink>
        </div>
      </section>

      <!-- Hors de Jamii : déclaré par la personne, jamais présenté comme une preuve. -->
      <section v-if="profil.realisations.length || moi" class="bloc" aria-labelledby="bloc-realisations" data-test="realisations">
        <div class="bloc__tete">
          <span class="bloc__icone"><IconeProfil nom="valise" /></span>
          <div>
            <h2 id="bloc-realisations">{{ t('profil.realisations.titre') }}</h2>
            <p class="bloc__aide">{{ t('profil.realisations.declareAide') }}</p>
          </div>
        </div>
        <ul v-if="profil.realisations.length" class="realisations">
          <li v-for="(r, i) in profil.realisations" :key="i" class="realisation">
            <div class="realisation__tete">
              <h3>
                <a v-if="r.url" :href="r.url" rel="noopener noreferrer" target="_blank">{{ r.titre }} <IconeProfil nom="fleche" :taille="14" /></a>
                <template v-else>{{ r.titre }}</template>
              </h3>
              <span class="declare" :title="t('profil.realisations.declareAide')">{{ t('profil.realisations.declare') }}</span>
            </div>
            <p v-if="r.role || r.annee" class="realisation__role">{{ [r.role, r.annee].filter(Boolean).join(' · ') }}</p>
            <p v-if="r.description" class="realisation__description">{{ r.description }}</p>
            <ul v-if="r.technologies?.length" class="puces">
              <li v-for="tech in r.technologies" :key="tech">{{ tech }}</li>
            </ul>
          </li>
        </ul>
        <button v-else type="button" class="vide-action" data-test="ajouter-realisation-vide" @click="emit('modifier', 'realisations')">
          <IconeProfil nom="plus" />{{ t('profil.realisations.vide') }}
        </button>
      </section>

      <section v-if="profil.depotsGithub.length" class="bloc" aria-labelledby="bloc-depots">
        <div class="bloc__tete">
          <span class="bloc__icone"><IconeProfil nom="github" /></span>
          <div>
            <h2 id="bloc-depots">{{ t('profil.depots.titre') }}</h2>
            <p class="bloc__aide">{{ t('profil.depots.pasUnePreuve') }}</p>
          </div>
        </div>
        <ul class="depots" data-test="depots">
          <li v-for="d in profil.depotsGithub" :key="d.url" class="depot">
            <a :href="d.url" rel="noopener noreferrer" target="_blank" class="depot__nom"><IconeProfil nom="code" :taille="16" />{{ d.nom }}</a>
            <p v-if="d.description">{{ d.description }}</p>
            <span class="depot__pied">
              <template v-if="d.langage"><i aria-hidden="true" />{{ d.langage }}</template>
              <span><IconeProfil nom="etoile" :taille="14" /> {{ t('profil.depots.etoiles', { n: d.etoiles }, d.etoiles) }}</span>
            </span>
          </li>
        </ul>
      </section>
    </div>

    <aside class="colonne colonne--cote">
      <!-- L'indice de fiabilité : un point par type de preuve, jamais de note globale. -->
      <section class="bloc fiabilite" aria-labelledby="bloc-fiabilite">
        <div class="bloc__tete">
          <span class="bloc__icone bloc__icone--preuve"><IconeProfil nom="bouclier" /></span>
          <h2 id="bloc-fiabilite">{{ t('profil.fiabilite.titre', { prenom }) }}</h2>
        </div>
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
            <li v-for="type in TYPES_PREUVE" :key="type"><IconeProfil nom="coche" :taille="14" />{{ t(`profil.fiabilite.comment.${type}`) }}</li>
          </ul>
        </template>
        <p class="bloc__note">{{ t('profil.fiabilite.sansNote') }}</p>
      </section>

      <section v-if="profil.competences.length || moi" class="bloc" aria-labelledby="bloc-competences">
        <div class="bloc__tete">
          <span class="bloc__icone"><IconeProfil nom="etincelle" /></span>
          <h2 id="bloc-competences">{{ t('profil.competences.titre') }}</h2>
        </div>
        <ul v-if="profil.competences.length" class="competences" data-test="competences">
          <li
            v-for="c in competencesTriees"
            :key="c.competence + c.source"
            :class="{ 'competence--declaree': c.source === 'declaree', 'competence--phare': phares.includes(c.competence) }"
          >
            <IconeProfil v-if="phares.includes(c.competence)" nom="etoile" :taille="13" />
            {{ c.competence }}
            <span v-if="c.source === 'declaree'" class="visuellement-cache">{{ t('profil.competences.declaree') }}</span>
            <span v-if="c.source === 'ia' && c.niveau" class="competence__niveau">{{ t(`profil.carte.niveaux.${c.niveau}`) }}</span>
          </li>
        </ul>
        <button v-else type="button" class="vide-action" @click="emit('modifier', 'competences')">
          <IconeProfil nom="plus" />{{ t('profil.competences.vide') }}
        </button>
        <p class="bloc__note">{{ t('profil.competences.source') }}</p>
      </section>

      <section v-if="profil.langages.length" class="bloc" aria-labelledby="bloc-langages">
        <div class="bloc__tete">
          <span class="bloc__icone"><IconeProfil nom="code" /></span>
          <h2 id="bloc-langages">{{ t('profil.langages.titre') }}</h2>
        </div>
        <ul class="langages" data-test="langages">
          <li v-for="l in profil.langages" :key="l.nom">
            <span class="langages__nom">{{ l.nom }}</span>
            <span class="langages__part">{{ pourcentage(l.part, locale) }}</span>
            <span class="jauge" aria-hidden="true"><span :style="{ width: `${Math.max(l.part * 100, 2)}%` }" /></span>
          </li>
        </ul>
        <p class="bloc__note">{{ t('profil.langages.estimation') }}</p>
      </section>
    </aside>
  </div>
</template>

<style scoped>
/* ---------- En-tête : le langage des pages d'exploration (surtitre, titre en deux temps, carte de chiffres). ---------- */
.hero { position: relative; isolation: isolate; border: 1px solid var(--line); border-radius: 28px; background: var(--surface); box-shadow: var(--ombre-carte); overflow: hidden; }
.hero__fond { position: absolute; inset: 0 0 auto; height: 148px; z-index: -1; overflow: hidden;
  background: radial-gradient(ellipse 60% 120% at 15% 0%, color-mix(in srgb, var(--action) 38%, transparent), transparent 70%),
    radial-gradient(ellipse 45% 110% at 90% 10%, color-mix(in srgb, var(--proof) 30%, transparent), transparent 70%),
    linear-gradient(180deg, var(--action-soft), var(--surface)); }
.hero__fond i { position: absolute; border-radius: 50%; border: 1px solid color-mix(in srgb, var(--ink) 10%, transparent); }
.hero__fond i:nth-child(1) { width: 420px; height: 420px; right: -120px; top: -300px; }
.hero__fond i:nth-child(2) { width: 260px; height: 260px; right: 40px; top: -200px; }
.hero__grille { display: grid; grid-template-columns: minmax(0, 1fr) minmax(250px, 300px); gap: 24px clamp(24px, 4vw, 56px); padding: 72px clamp(20px, 4vw, 44px) clamp(24px, 3vw, 32px); align-items: center; }
.hero__identite { display: grid; justify-items: start; gap: 14px; min-width: 0; }
.hero__identite > * { margin: 0; }
.hero__photo { position: relative; border-radius: 50%; box-shadow: 0 0 0 5px var(--surface), 0 12px 30px color-mix(in srgb, var(--midnight) 18%, transparent); margin-bottom: 6px; }
.hero__point { position: absolute; right: 8px; bottom: 8px; width: 22px; height: 22px; border-radius: 50%; border: 4px solid var(--surface); background: var(--line-strong); }
.hero__point--disponible { background: #1FA35B; }
.hero__point--occupe { background: #F2A541; }
.hero__surtitre { display: flex; align-items: center; gap: 10px; color: var(--ink-muted); font-size: .8125rem; letter-spacing: .1em; text-transform: uppercase; font-weight: 700; }
.hero__surtitre i { width: 7px; height: 7px; border-radius: 50%; background: var(--line-strong); flex: none; }
.hero__surtitre--disponible i { background: #1FA35B; box-shadow: 0 0 0 4px color-mix(in srgb, #1FA35B 20%, transparent); }
.hero__surtitre--occupe i { background: #F2A541; }
.hero h1 { font-size: clamp(2.25rem, 4.6vw, 3.75rem); line-height: 1.04; letter-spacing: -.045em; text-wrap: balance; overflow-wrap: anywhere; }
.hero__titre { display: block; margin-top: 6px; font-size: clamp(1.25rem, 2.3vw, 1.75rem); line-height: 1.2; letter-spacing: -.025em; color: var(--action-ink); }
.hero__meta { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px 22px; color: var(--ink); font-weight: 600; }
.hero__meta li { display: inline-flex; align-items: center; gap: 8px; }
.hero__meta .icone-profil { color: var(--ink-muted); }
.drapeau { width: 22px; height: 16px; border-radius: 3px; object-fit: cover; box-shadow: 0 0 0 1px var(--line); }
.hero__liens { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.hero__liens a { display: inline-flex; align-items: center; gap: 8px; min-height: 38px; padding: 0 14px; border: 1px solid var(--line-strong); border-radius: 50px; background: var(--surface); color: var(--ink); font-size: .9375rem; font-weight: 600; text-decoration: none; transition: border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out); }
.hero__liens a:hover { border-color: var(--action); transform: translateY(-1px); }
.hero__phares { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.hero__phares li { display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: 50px; background: var(--action-soft); color: var(--action-ink); font-weight: 700; font-size: .9375rem; }
.hero__actions { grid-column: 1 / -1; min-width: 0; }

.hero__chiffres { position: relative; align-self: end; margin-bottom: 4px; padding: 22px; border: 1px solid var(--line-strong); border-radius: 22px; background: radial-gradient(ellipse at 100% 0, var(--action-soft), transparent 70%), var(--surface); }
.hero__chiffres::before { content: ''; position: absolute; inset: 10px; border: 1px solid var(--line); border-radius: 14px; pointer-events: none; opacity: .6; }
.hero__chiffres > * { position: relative; }
.hero__chiffres-titre { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; color: var(--ink-muted); font-size: .75rem; letter-spacing: .1em; text-transform: uppercase; font-weight: 700; }
.hero__chiffres-titre i { width: 6px; height: 6px; border-radius: 50%; background: var(--proof); }
.hero__chiffres dl { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 12px; margin: 0; }
.hero__chiffres dl > div { display: flex; flex-direction: column-reverse; gap: 2px; }
.hero__chiffres dt { color: var(--ink-muted); font-size: .8125rem; line-height: 1.3; }
.hero__chiffres dd { margin: 0; font-family: var(--font-display); font-size: 2rem; font-weight: 700; letter-spacing: -.04em; line-height: 1; }
.hero__chiffre--preuve dd { color: var(--proof-ink); }
.hero__maj { display: flex; align-items: center; gap: 6px; margin: 18px 0 0; padding-top: 14px; border-top: 1px solid var(--line); color: var(--ink-muted); font-size: .8125rem; }

/* ---------- Corps ---------- */
.colonnes { display: grid; gap: 24px; grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); align-items: start; }
.colonne { display: grid; gap: 24px; min-width: 0; }
.colonne--cote { position: sticky; top: 96px; }
.bloc { display: grid; gap: 16px; padding: clamp(20px, 2.5vw, 28px); background: var(--surface); border: 1px solid var(--line); border-radius: 24px; box-shadow: var(--ombre-carte); min-width: 0; }
.bloc > p, .bloc h2 { margin: 0; }
.bloc__tete { display: flex; align-items: flex-start; gap: 12px; }
.bloc__tete h2 { font-size: 1.25rem; line-height: 1.3; letter-spacing: -.02em; margin: 0; }
.bloc__aide { margin: 2px 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); line-height: 1.45; }
.bloc__icone { display: grid; place-items: center; width: 36px; height: 36px; flex: none; border-radius: 12px; background: var(--action-soft); color: var(--action-ink); }
.bloc__icone--preuve { background: var(--proof-soft); color: var(--proof-ink); }
.bloc__note { color: var(--ink-muted); font-size: .8125rem; line-height: 1.45; }
.bio { white-space: pre-line; font-size: 1.0625rem; line-height: 1.75; max-width: 62ch; }

.vide-action { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 56px; padding: 14px 18px; border: 1px dashed var(--line-strong); border-radius: 16px; background: transparent; color: var(--action-ink); font-weight: 600; text-align: start; cursor: pointer; transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out); }
.vide-action:hover { background: var(--action-soft); border-color: var(--action); }
.vide { display: grid; gap: 12px; justify-items: center; text-align: center; padding: 24px; border: 1px dashed var(--line-strong); border-radius: 18px; }
.vide p { margin: 0; color: var(--ink-muted); max-width: 30rem; }
.vide__illustration { width: 8rem; }
.vide__lien { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; }

.projets { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.projets li { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; padding: 14px 16px; background: var(--surface-raised); border-radius: 14px; }
.projets__texte { display: grid; gap: 2px; min-width: 0; }

.realisations { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 15rem), 1fr)); }
.realisation { display: grid; align-content: start; gap: 6px; padding: 16px; border: 1px solid var(--line); border-radius: 16px; background: linear-gradient(180deg, var(--surface-raised), var(--surface) 60%); }
.realisation p { margin: 0; }
.realisation__tete { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
.realisation h3 { margin: 0; font-size: 1.0625rem; line-height: 1.35; letter-spacing: -.01em; }
.realisation h3 a { color: var(--ink); text-decoration: none; }
.realisation h3 a:hover { color: var(--action-ink); text-decoration: underline; }
.realisation__role { color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.realisation__description { font-size: .9375rem; line-height: 1.6; }
.declare { flex: none; font-size: .75rem; font-weight: 600; color: var(--ink-muted); border: 1px dashed var(--line-strong); border-radius: 50px; padding: 1px 8px; }
.puces { list-style: none; margin: 4px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px; }
.puces li { padding: 2px 10px; border-radius: 50px; background: var(--surface); border: 1px solid var(--line); font-size: .8125rem; }

.depots { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 14rem), 1fr)); }
.depot { display: grid; align-content: start; gap: 6px; padding: 14px 16px; border: 1px solid var(--line); border-radius: 16px; }
.depot p { margin: 0; font-size: .875rem; line-height: 1.5; color: var(--ink-muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.depot__nom { display: inline-flex; align-items: center; gap: 6px; font-weight: 700; overflow-wrap: anywhere; }
.depot__pied { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 14px; margin-top: 2px; font-size: .8125rem; color: var(--ink-muted); }
.depot__pied i { display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: var(--proof); margin-inline-end: -8px; }
.depot__pied span { display: inline-flex; align-items: center; gap: 4px; }

.points { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.points li { display: grid; grid-template-columns: auto 1fr; gap: 2px 12px; align-items: baseline; padding: 12px 14px; background: var(--proof-soft); border-radius: 14px; }
.points__nombre { font-family: var(--font-display); font-weight: 700; font-size: 1.375rem; color: var(--proof-ink); }
.points small { grid-column: 2; }
.comment { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; font-size: .9375rem; }
.comment li { display: flex; gap: 8px; align-items: flex-start; }
.comment .icone-profil { margin-top: 4px; color: var(--proof-ink); }

.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.competences li { display: inline-flex; align-items: center; gap: 6px; padding: 5px 12px; border-radius: 50px; background: var(--surface-raised); font-size: .875rem; font-weight: 600; }
.competence--declaree { background: transparent !important; border: 1px dashed var(--line-strong); font-weight: 500 !important; }
.competence--phare { background: var(--action-soft) !important; color: var(--action-ink); }
.competence__niveau { padding-inline-start: 6px; border-inline-start: 1px solid var(--line-strong); color: var(--ink-muted); font-weight: 500; }

.langages { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
.langages li { display: grid; grid-template-columns: 1fr auto; gap: 6px 12px; align-items: center; }
.langages__nom { font-weight: 600; }
.langages__part { color: var(--ink-muted); font-size: .875rem; font-variant-numeric: tabular-nums; }
.jauge { grid-column: 1 / -1; height: 8px; background: var(--surface-raised); border-radius: 50px; overflow: hidden; }
.jauge > span { display: block; height: 100%; background: linear-gradient(90deg, var(--action), color-mix(in srgb, var(--action) 60%, var(--proof))); border-radius: 50px; }

@media (max-width: 960px) {
  .colonnes { grid-template-columns: minmax(0, 1fr); }
  .colonne--cote { position: static; }
}
@media (max-width: 760px) {
  .hero__grille { grid-template-columns: minmax(0, 1fr); padding-top: 56px; }
  .hero__fond { height: 120px; }
}
@media (prefers-reduced-motion: reduce) { .hero__liens a { transition: none; } .hero__liens a:hover { transform: none; } }
</style>
