<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JChamp, JSquelette } from '../../design-system'
import {
  annulerHackathon,
  formaterDate,
  formaterMontant,
  LIMITES,
  lireHackathon,
  publierHackathon,
  type Hackathon,
  type Resultat,
} from './hackathons'
import ConfirmerResultats from './ConfirmerResultats.vue'
import EquipesHackathon from './EquipesHackathon.vue'
import InscriptionHackathon from './InscriptionHackathon.vue'
import JuryHackathon from './JuryHackathon.vue'
import NotationHackathon from './NotationHackathon.vue'
import RenduEquipe from './RenduEquipe.vue'
import ResultatsHackathon from './ResultatsHackathon.vue'

/*
 * Un hackathon : son défi, son calendrier, ses critères et ses prix, et ce que la personne qui le lit peut en faire.
 * Les boutons viennent de `moi.actions`, calculé par l'API (qui revérifie chaque action). S'inscrire et former son
 * équipe : `InscriptionHackathon` et `EquipesHackathon` ; rendre son projet : `RenduEquipe` ;
 * le jury : `JuryHackathon` (organisateur) et `NotationHackathon` (juré) ; les gagnants : `ConfirmerResultats`
 * (organisateur) puis `ResultatsHackathon` (tout le monde).
 * DESIGN PROVISOIRE : l'écran montre le parcours avec les composants actuels ; son design sera repris avec le design system.
 */
const { t, locale } = useI18n()
const route = useRoute()
const id = computed(() => String(route.params.id))

const hackathon = ref<Hackathon | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const action = ref<'repos' | 'envoi'>('repos')
const codeErreur = ref<string | null>(null)
const annulation = ref(false)
const raison = ref('')

async function charger() {
  etat.value = 'chargement'
  try {
    hackathon.value = await lireHackathon(id.value)
    etat.value = hackathon.value ? 'pret' : 'introuvable'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(id, () => void charger(), { immediate: true })

/** Relire le hackathon sans repasser par l'écran de chargement (après une inscription). */
async function rafraichir() {
  try {
    hackathon.value = (await lireHackathon(id.value)) ?? hackathon.value
  } catch {
    // Le hackathon affiché reste juste ; la prochaine action le relira.
  }
}
/** Mon équipe rend son projet pendant le hackathon, et retrouve son rendu ensuite. */
const monEquipe = computed(() => {
  const h = hackathon.value
  return h?.moi.equipeId && ['en_cours', 'evaluation', 'resultats'].includes(h.statut) ? h.moi.equipeId : null
})
/** L'organisateur gère son jury dès la publication, jusqu'aux résultats. */
const voitJury = computed(() => hackathon.value?.moi.role === 'organisateur' && ['publie', 'en_cours', 'evaluation', 'resultats'].includes(hackathon.value.statut))
const voitEquipes = computed(() => !['brouillon', 'annule'].includes(hackathon.value?.statut ?? 'brouillon'))

const peut = (nom: string) => (hackathon.value?.moi.actions as string[] | undefined)?.includes(nom) ?? false
const dates = computed(() => {
  const c = hackathon.value?.calendrier
  if (!c) return []
  return [
    ['ouvertes', c.inscriptionsOuvertesLe],
    ['fermees', c.inscriptionsFermeesLe],
    ['debut', c.debutLe],
    ['fin', c.finRendusLe],
    ['resultats', c.resultatsLe],
  ] as const
})
const raisonValide = computed(() => raison.value.trim().length >= LIMITES.raisonMin)
const message = computed(() => {
  if (!codeErreur.value) return ''
  const connus = ['modification_concurrente', 'etat_invalide', 'limite_atteinte', 'validation']
  return t(`hackathons.detail.erreurs.${connus.includes(codeErreur.value) ? codeErreur.value : 'autre'}`)
})

async function agir(appel: () => Promise<Resultat<Hackathon>>) {
  if (!hackathon.value || action.value === 'envoi') return
  action.value = 'envoi'
  codeErreur.value = null
  try {
    const r = await appel()
    if (r.ok) {
      hackathon.value = r.valeur
      annulation.value = false
    } else codeErreur.value = r.code ?? 'autre'
  } catch {
    codeErreur.value = 'autre'
  } finally {
    action.value = 'repos'
  }
}
const publier = () => agir(() => publierHackathon(hackathon.value!.id, hackathon.value!.version))
const annuler = () => (raisonValide.value ? agir(() => annulerHackathon(hackathon.value!.id, hackathon.value!.version, raison.value.trim())) : undefined)
</script>

<template>
  <section class="hackathon">
    <p class="retour"><RouterLink to="/hackathons">{{ t('hackathons.detail.retour') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'introuvable'" class="carte message" data-test="introuvable">{{ t('hackathons.detail.introuvable') }}</p>
    <p v-else-if="etat === 'indisponible'" role="alert" class="carte message">{{ t('hackathons.indisponible') }}</p>

    <template v-else-if="hackathon">
      <header class="page-tete motif motif--reseau">
        <div class="page-tete__texte">
          <p class="etiquette" :data-statut="hackathon.statut" data-test="statut">{{ t(`hackathons.statuts.${hackathon.statut}`) }}</p>
          <h1 data-test="titre">{{ hackathon.titre }}</h1>
          <p>{{ hackathon.theme }}</p>
        </div>
      </header>

      <article class="carte contenu">
        <p v-if="hackathon.statut === 'brouillon'" class="note" data-test="brouillon">{{ t('hackathons.detail.brouillon') }}</p>

        <!-- Texte brut de l'organisateur : affiché échappé, avec ses retours à la ligne. -->
        <p class="texte" data-test="description">{{ hackathon.description }}</p>

        <dl class="faits">
          <div>
            <dt>{{ t('hackathons.nouveau.lieu') }}</dt>
            <dd>{{ [t(`hackathons.lieux.${hackathon.lieu}`), hackathon.ville].filter(Boolean).join(' · ') }}</dd>
          </div>
          <div>
            <dt>{{ t('hackathons.nouveau.equipes') }}</dt>
            <dd>{{ t('hackathons.detail.equipes', { min: hackathon.tailleEquipe.min, max: hackathon.tailleEquipe.max }) }}</dd>
          </div>
          <div>
            <dt>{{ t('hackathons.nouveau.mode') }}</dt>
            <dd>{{ t(`hackathons.detail.mode.${hackathon.modeInscription}`) }}</dd>
          </div>
        </dl>

        <section aria-labelledby="calendrier">
          <h2 id="calendrier">{{ t('hackathons.detail.calendrier') }}</h2>
          <ol class="calendrier" data-test="calendrier">
            <li v-for="[cle, instant] in dates" :key="cle"><strong>{{ t(`hackathons.calendrier.${cle}`) }}</strong> {{ formaterDate(instant, locale) }}</li>
          </ol>
        </section>

        <p v-if="hackathon.deploiementObligatoire" class="note" data-test="deploiement">{{ t('hackathons.detail.deploiement') }}</p>

        <section aria-labelledby="criteres">
          <h2 id="criteres">{{ t('hackathons.detail.criteres') }}</h2>
          <ul class="criteres" data-test="criteres">
            <li v-for="c in hackathon.criteres" :key="c.id ?? c.nom">{{ c.nom }} <span class="texte-discret">({{ t('hackathons.detail.poids', { n: c.poids }) }})</span></li>
          </ul>
        </section>

        <section v-if="hackathon.prix.length" aria-labelledby="prix">
          <h2 id="prix">{{ t('hackathons.detail.prix') }}</h2>
          <ul class="criteres" data-test="prix">
            <li v-for="p in hackathon.prix" :key="p.id ?? p.nom">
              <strong>{{ p.nom }}</strong>
              <span v-if="p.montant"> · {{ formaterMontant(p.montant, locale) }}</span>
              <span v-if="p.sponsor" class="texte-discret"> · {{ t('hackathons.detail.offertPar', { sponsor: p.sponsor }) }}</span>
            </li>
          </ul>
        </section>

        <section v-if="hackathon.questions.length" aria-labelledby="questions">
          <h2 id="questions">{{ t('hackathons.detail.questions') }}</h2>
          <ul class="criteres"><li v-for="q in hackathon.questions" :key="q">{{ q }}</li></ul>
        </section>

        <section v-if="hackathon.codeDeConduite" aria-labelledby="code">
          <h2 id="code">{{ t('hackathons.detail.code') }}</h2>
          <p class="texte">{{ hackathon.codeDeConduite }}</p>
        </section>

        <p class="client"><JAvatar :nom="hackathon.organisateur.nom" :url="hackathon.organisateur.avatarUrl" :taille="28" /> {{ t('hackathons.par', { nom: hackathon.organisateur.nom }) }}</p>

        <div v-if="peut('publier') || peut('annuler')" class="actions">
          <div v-if="peut('publier')">
            <JBouton :occupe="action === 'envoi'" data-test="publier" @click="publier">{{ t('hackathons.detail.publier') }}</JBouton>
            <p class="texte-discret">{{ t('hackathons.detail.publierAide') }}</p>
          </div>
          <div v-if="peut('annuler')">
            <JBouton v-if="!annulation" data-test="annuler" @click="annulation = true">{{ t('hackathons.detail.annuler') }}</JBouton>
            <form v-else class="annulation" data-test="annulation" @submit.prevent="annuler">
              <JChamp v-model="raison" :libelle="t('hackathons.detail.raison')" multiligne :lignes="3" :maxlength="LIMITES.raisonMax" requis />
              <JBouton type="submit" :desactive="!raisonValide" :occupe="action === 'envoi'" data-test="confirmer-annulation">{{ t('hackathons.detail.confirmerAnnulation') }}</JBouton>
            </form>
            <p class="texte-discret">{{ t('hackathons.detail.annulerAide') }}</p>
          </div>
        </div>
        <p v-if="message" role="alert" class="erreur" data-test="erreur">{{ message }}</p>
        <p class="texte-discret provisoire" data-test="provisoire">{{ t('hackathons.detail.provisoire') }}</p>
      </article>

      <ResultatsHackathon v-if="hackathon.statut === 'resultats'" :hackathon="hackathon" />
      <ConfirmerResultats v-if="peut('confirmer_resultats')" :hackathon="hackathon" @publie="rafraichir" />
      <InscriptionHackathon v-if="peut('sinscrire') || peut('creer_equipe')" :hackathon="hackathon" @inscrit="rafraichir" />
      <NotationHackathon v-if="peut('noter')" :hackathon="hackathon" />
      <JuryHackathon v-if="voitJury" :hackathon="hackathon" />
      <RenduEquipe v-if="monEquipe" :hackathon="hackathon" :equipe-id="monEquipe" />
      <EquipesHackathon v-if="voitEquipes" :hackathon="hackathon" />
    </template>
  </section>
</template>

<style scoped>
.hackathon { display: grid; gap: var(--space-6); max-width: 56rem; }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.message { margin: 0; padding: var(--space-6); }
.contenu { display: grid; gap: var(--space-6); padding: var(--space-8); }
.contenu h2 { margin: 0 0 var(--space-2); font-size: 1.0625rem; }
.texte { margin: 0; white-space: pre-line; }
.faits { margin: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr)); }
.faits dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.faits dd { margin: 0; font-weight: 600; }
.calendrier { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); }
.criteres { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); }
.client { margin: 0; display: flex; gap: var(--space-2); align-items: center; }
.etiquette { display: inline-block; margin: 0 0 var(--space-2); padding: 2px var(--space-3); border-radius: var(--radius-pill); border: 1px solid var(--line-strong); font-size: var(--type-body-sm-size); font-weight: 700; }
.etiquette[data-statut='publie'], .etiquette[data-statut='en_cours'] { border-color: var(--action); }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-6); }
.actions p { margin: var(--space-2) 0 0; }
.annulation { display: grid; gap: var(--space-3); max-width: 28rem; }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.provisoire { margin: 0; font-size: var(--type-body-sm-size); }
</style>
