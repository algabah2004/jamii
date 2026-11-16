<script setup lang="ts">
import MotifsCoins from '../../components/MotifsCoins.vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JBouton, JSquelette } from '../../design-system'
import BadgeIa from '../../components/BadgeIa.vue'
import AyoAvatar from './AyoAvatar.vue'
import ConversationDemande from './ConversationDemande.vue'
import ConfirmerSuppression from './ConfirmerSuppression.vue'
import { dateCourte, listerDemandes, supprimerDemande, type DemandeResumee } from './demandes'

/*
 * « Tes demandes » (#199) : tous les besoins déjà décrits, comme sur un profil. On y revient pour relire une réponse,
 * la préciser ou la publier, et on en décrit un nouveau d'un clic. Le menu « Demandes » ouvre cette page.
 */
const { t } = useI18n()

const route = useRoute()
const exempleInitial = computed(() => (route.query.exemple !== undefined && Number.isInteger(Number(route.query.exemple)) ? Number(route.query.exemple) : null))
// Un lien vers /besoin?titre=… pendant qu'on est déjà là : la conversation repart avec ce début.
const cleConversation = computed(() => `${route.query.titre ?? ''}|${route.query.exemple ?? ''}`)
const demandes = ref<DemandeResumee[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    demandes.value = await listerDemandes()
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

/** Où en est chaque demande : décrite, puis analysée (son résumé est là), puis publiée. */
type Etat = 'preparation' | 'prete' | 'publiee'
const etatDe = (d: DemandeResumee): Etat => (d.publiee ? 'publiee' : d.resume ? 'prete' : 'preparation')
const ETATS: Etat[] = ['preparation', 'prete', 'publiee']
const filtre = ref<Etat | 'toutes'>('toutes')
const compte = (e: Etat) => demandes.value.filter((d) => etatDe(d) === e).length
/** Supprimer une discussion depuis la liste, après confirmation. */
const suppressionRatee = ref(false)
const confirmation = ref<InstanceType<typeof ConfirmerSuppression> | null>(null)
async function supprimer(d: DemandeResumee) {
  if (!(await confirmation.value?.demander())) return
  suppressionRatee.value = false
  try {
    if (!(await supprimerDemande(d.id))) return void (suppressionRatee.value = true)
    demandes.value = demandes.value.filter((x) => x.id !== d.id)
  } catch {
    suppressionRatee.value = true
  }
}
const affichees = computed(() => (filtre.value === 'toutes' ? demandes.value : demandes.value.filter((d) => etatDe(d) === filtre.value)))
</script>

<template>
  <section class="avec-motifs mes-demandes">
    <!-- Des taches de dessins sur les bords de la page, à son image. -->
    <MotifsCoins planche="demandes" />
    <!-- Le titre reste pour les lecteurs d'écran : à l'écran, la discussion avec Ayo est le point de départ. -->
    <h1 class="visuellement-cache">{{ t('demandes.mesDemandes.titre') }}</h1>

    <!-- On décrit son besoin ici même, en parlant à Ayo : pas de formulaire sur une autre page. -->
    <ConversationDemande :key="cleConversation" :titre-initial="String(route.query.titre ?? '')" :exemple-initial="exempleInitial" />

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="carte vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <section v-else-if="demandes.length" class="historique" aria-labelledby="mes-demandes-titre">
      <div class="historique__tete">
        <h2 id="mes-demandes-titre">{{ t('demandes.mesDemandes.dejaDemande') }}</h2>
        <div class="filtres" role="group" :aria-label="t('demandes.mesDemandes.filtrer')">
          <button type="button" class="filtre" :aria-pressed="filtre === 'toutes'" data-test="filtre-toutes" @click="filtre = 'toutes'">
            {{ t('demandes.mesDemandes.etats.toutes') }} <span>{{ demandes.length }}</span>
          </button>
          <button v-for="e in ETATS" :key="e" type="button" class="filtre" :aria-pressed="filtre === e" :disabled="!compte(e)" :data-test="`filtre-${e}`" @click="filtre = e">
            {{ t(`demandes.mesDemandes.etats.${e}`) }} <span>{{ compte(e) }}</span>
          </button>
        </div>
      </div>
      <p v-if="suppressionRatee" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
      <ul class="liste" data-test="liste-demandes">
        <li v-for="d in affichees" :key="d.id" class="liste__element">
          <RouterLink :to="`/demandes/${d.id}`" class="carte demande" :class="`demande--${etatDe(d)}`" data-test="demande-resumee">
            <span class="demande__tete">
              <span class="etat" :class="`etat--${etatDe(d)}`">{{ t(`demandes.mesDemandes.etats.${etatDe(d)}`) }}</span>
              <span class="texte-discret">{{ dateCourte(d.creeLe) }}</span>
            </span>
            <strong class="demande__titre">{{ d.titre }}</strong>
            <!-- La réponse d'Ayo, en une ligne : texte brut, affiché échappé. -->
            <span v-if="d.resume" class="demande__ayo"><AyoAvatar :taille="20" /><span class="demande__resume">{{ d.resume }}</span></span>
            <span v-else class="demande__ayo texte-discret"><AyoAvatar :taille="20" reflechit />{{ t('demandes.mesDemandes.enCours') }}</span>
            <span class="demande__etiquettes">
              <span v-if="d.precisions" class="etiquette">{{ t('demandes.discussion.messages', { n: d.precisions + 1 }, d.precisions + 1) }}</span>
              <span class="demande__ouvrir">{{ t('demandes.mesDemandes.ouvrir') }} →</span>
            </span>
          </RouterLink>
          <button type="button" class="supprimer" :aria-label="`${t('demandes.discussion.supprimer')} : ${d.titre}`" :title="t('demandes.discussion.supprimer')" data-test="supprimer-demande" @click="supprimer(d)">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M4 6h12M8 6V4h4v2M6 6l1 10h6l1-10" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </button>
        </li>
      </ul>
    </section>
    <ConfirmerSuppression ref="confirmation" />
  </section>
</template>

<style scoped>
.mes-demandes { display: grid; gap: var(--space-8); min-width: 0; }


.vide { display: grid; gap: var(--space-3); justify-items: start; padding: var(--space-8); }
.vide p { margin: 0; }

.historique { display: grid; gap: var(--space-4); }
.historique__tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.historique__tete h2 { margin: 0; font-size: 1.25rem; }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
.liste__element { position: relative; }
.supprimer { position: absolute; top: var(--space-3); inset-inline-end: var(--space-3); display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink-muted); cursor: pointer; opacity: 0.6; }
.supprimer:hover, .supprimer:focus-visible { opacity: 1; color: var(--danger); background: var(--surface-raised); }
.supprimer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.demande__tete { padding-inline-end: var(--space-8); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.demande { display: grid; gap: var(--space-3); height: 100%; padding: var(--space-6); color: var(--ink); text-decoration: none; transition: border-color var(--motion-fast) var(--ease-out); }
.demande:hover { border-color: var(--action); }
.demande:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.demande__tete { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
.etat { padding: 2px 10px; border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; background: var(--surface-raised); color: var(--ink-muted); }
.etat--prete { background: var(--action-soft); color: var(--action-ink); }
.etat--publiee { background: var(--proof-soft); color: var(--proof-ink); }
.demande__titre { font-size: 1.0625rem; }
.demande__ayo { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-2); align-items: start; font-size: var(--type-body-sm-size); }
.demande__resume { display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.5; }
.demande__etiquettes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); align-self: end; }
.demande__ouvrir { margin-inline-start: auto; color: var(--action-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
.filtres { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.filtre {
  display: inline-flex; align-items: center; gap: var(--space-2); min-height: 36px; padding: 0 var(--space-3); cursor: pointer;
  border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600;
}
.filtre span { padding: 0 7px; border-radius: var(--radius-pill); background: var(--surface-raised); font-variant-numeric: tabular-nums; }
.filtre[aria-pressed='true'] { background: var(--ink); border-color: var(--ink); color: var(--surface); }
.filtre[aria-pressed='true'] span { background: color-mix(in srgb, var(--surface) 20%, transparent); }
.filtre:disabled { opacity: 0.5; cursor: default; }
.filtre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
