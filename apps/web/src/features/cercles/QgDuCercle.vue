<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar } from '../../design-system'
import { useSession } from '../../session'
import { ilYa } from '../vitrine/vitrine'
import CollineDuTour from './CollineDuTour.vue'
import { lireTaches, ordreDePassage, pourcentage, type Avancement, type Cercle, type Tache } from './cercles'
import { joursDuTour } from './qg'
import { lireSalon, prenom, type SalonCercle } from './salon'

/*
 * Le QG du cercle, à la façon d'un projet Basecamp : une grille d'outils, chacun avec un aperçu vivant. On voit d'un
 * coup d'œil où en est l'équipe ; un clic ouvre l'outil en grand. Lu une fois à l'ouverture (chaque outil se met
 * ensuite à jour lui-même).
 */
const props = defineProps<{ cercle: Cercle; suivi: Avancement | null; titreTour: string | null }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const salon = ref<SalonCercle | null>(null)
const taches = ref<Tache[]>([])
const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)

onMounted(async () => {
  const [s, liste] = await Promise.all([
    lireSalon(props.cercle.id).catch(() => null),
    tour.value?.projetId ? lireTaches(tour.value.projetId) : Promise.resolve([] as Tache[]),
  ])
  if (s?.ok) salon.value = s.salon
  taches.value = liste ?? []
})

const derniers = computed(() => (salon.value?.elements ?? []).filter((m) => m.type === 'texte' || m.type === 'coup_de_main').slice(-3).reverse())
const miennes = computed(() => taches.value.filter((x) => x.assignee?.id === moi.value?.id && x.statut !== 'livree'))
const points = computed(() => salon.value?.pointsDuJour ?? [])
const pointFait = computed(() => !!moi.value && points.value.includes(moi.value.id))
const ordre = computed(() => ordreDePassage(props.cercle))
const jours = computed(() => (props.suivi && tour.value ? joursDuTour(new Date(Date.now() - (props.suivi.jour - 1) * 86_400_000).toISOString()) : []))
const lien = (outil: string) => ({ query: { outil } })
</script>

<template>
  <div class="qg" data-test="qg">
    <!-- Le tour en cours, en bandeau : ce qui compte cette quinzaine. -->
    <section v-if="tour" class="bandeau carte" aria-labelledby="qg-tour">
      <div class="bandeau__texte">
        <p class="bandeau__surtitre">{{ t('cercles.tourDe', { n: tour.numero, nom: prenom(tour.beneficiaire.nom) }) }}</p>
        <h2 id="qg-tour">{{ titreTour ?? t('cercles.projetDe', { nom: prenom(tour.beneficiaire.nom) }) }}</h2>
        <p v-if="suivi" class="texte-discret">{{ t('cercles.salon.contexteJour', { jour: suivi.jour, faites: suivi.faites, total: suivi.total }) }} · {{ t('cercles.joursRestants', { n: suivi.joursRestants }, suivi.joursRestants) }}</p>
      </div>
      <div v-if="suivi" class="bandeau__barre" role="progressbar" :aria-valuenow="pourcentage(suivi)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')"><i :style="{ width: `${pourcentage(suivi)}%` }" /></div>
      <RouterLink :to="lien('cercle')" class="bandeau__lien">{{ t('cercles.qg.voirLeTour') }}</RouterLink>
    </section>
    <section v-else-if="cercle.statut === 'termine'" class="bandeau carte">
      <div class="bandeau__texte"><h2>{{ t('cercles.termine') }}</h2></div>
    </section>

    <div class="outils">
      <!-- Le salon (Campfire) -->
      <RouterLink :to="lien('salon')" class="outil carte" data-test="outil-salon">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">💬</span><strong>{{ t('cercles.qg.outils.salon') }}</strong></span>
        <span v-if="salon?.appel" class="pastille pastille--appel">{{ t('cercles.qg.appelEnCours') }}</span>
        <span v-if="salon?.coupsDeMain.length" class="pastille pastille--bloque">{{ t('cercles.salon.coupsTitre', { n: salon.coupsDeMain.length }, salon.coupsDeMain.length) }}</span>
        <ul v-if="derniers.length" class="apercu">
          <li v-for="m in derniers" :key="m.id">
            <JAvatar :nom="m.auteur?.nom ?? '?'" :url="m.auteur?.avatarUrl" :taille="24" />
            <span class="apercu__texte"><strong>{{ prenom(m.auteur?.nom) }}</strong> {{ m.texte }}</span>
          </li>
        </ul>
        <span v-else class="texte-discret">{{ t('cercles.salon.vide') }}</span>
      </RouterLink>

      <!-- Les tâches (To-dos) et la colline -->
      <RouterLink :to="lien('taches')" class="outil carte" data-test="outil-taches">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">✅</span><strong>{{ t('cercles.qg.outils.taches') }}</strong></span>
        <CollineDuTour v-if="taches.length" :taches="taches" compacte />
        <span class="texte-discret">
          {{ taches.length ? t('cercles.taches', { faites: taches.filter((x) => x.statut === 'livree').length, total: taches.length }) : t('cercles.qg.aucuneTache') }}
        </span>
        <span v-if="miennes.length" class="outil__info">{{ t('cercles.qg.mesTaches', { n: miennes.length }, miennes.length) }}</span>
      </RouterLink>

      <!-- Le point du soir (Automatic check-ins) -->
      <RouterLink :to="lien('points')" class="outil carte" :class="{ 'outil--a-faire': !pointFait && tour }" data-test="outil-points">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">🌙</span><strong>{{ t('cercles.qg.outils.points') }}</strong></span>
        <span class="outil__question">« {{ t('cercles.qg.question') }} »</span>
        <span class="avatars">
          <JAvatar v-for="m in cercle.membres" :key="m.personne.id" :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="26" :class="{ eteint: !points.includes(m.personne.id) }" />
        </span>
        <span class="texte-discret">{{ t('cercles.salon.pointsCompte', { n: points.length, total: cercle.membres.length }) }}</span>
        <span v-if="!pointFait && tour" class="outil__info outil__info--chaud">{{ t('cercles.qg.aToi') }}</span>
      </RouterLink>

      <!-- Les décisions (Message Board) -->
      <RouterLink :to="lien('decisions')" class="outil carte" data-test="outil-decisions">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">📌</span><strong>{{ t('cercles.qg.outils.decisions') }}</strong></span>
        <ul v-if="salon?.decisions.length" class="decisions">
          <li v-for="d in salon.decisions.slice(0, 3)" :key="d.id">{{ d.texte }} <span class="texte-discret">· {{ prenom(d.auteur?.nom) }}, {{ ilYa(d.envoyeLe, locale) }}</span></li>
        </ul>
        <span v-else class="texte-discret">{{ t('cercles.qg.aucuneDecision') }}</span>
      </RouterLink>

      <!-- Le calendrier du tour (Schedule) -->
      <RouterLink :to="lien('calendrier')" class="outil carte" data-test="outil-calendrier">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">📅</span><strong>{{ t('cercles.qg.outils.calendrier') }}</strong></span>
        <span v-if="jours.length" class="frise" aria-hidden="true">
          <i v-for="j in jours" :key="j.iso" :class="{ passe: j.passe, aujourdhui: j.aujourdhui, jalon: j.jalon }" />
        </span>
        <span class="texte-discret">{{ suivi ? t('cercles.qg.jourSur', { jour: suivi.jour }) : t('cercles.qg.calendrierAvant') }}</span>
      </RouterLink>

      <!-- Le cercle : l'ordre de passage -->
      <RouterLink :to="lien('cercle')" class="outil carte" data-test="outil-cercle">
        <span class="outil__tete"><span class="outil__icone" aria-hidden="true">⭕</span><strong>{{ t('cercles.qg.outils.cercle') }}</strong></span>
        <ol class="passage">
          <li v-for="m in ordre" :key="m.personne.id" :class="{ actuel: m.personne.id === tour?.beneficiaire.id, livre: cercle.tours.find((x) => x.beneficiaire.id === m.personne.id)?.statut === 'livre' }">
            <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="28" />
          </li>
        </ol>
        <span class="texte-discret">{{ t('cercles.qg.ordreAide') }}</span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.qg { display: grid; gap: var(--space-6); }
.bandeau { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-3) var(--space-6); align-items: center; }
.bandeau__texte { display: grid; gap: var(--space-1); min-width: 0; }
.bandeau__texte h2, .bandeau__texte p { margin: 0; }
.bandeau__surtitre { color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
.bandeau__barre { grid-column: 1 / -1; height: 8px; border-radius: var(--radius-pill); background: var(--surface-raised); overflow: hidden; }
.bandeau__barre i { display: block; height: 100%; background: var(--action); border-radius: inherit; }
.bandeau__lien { grid-column: 2; grid-row: 1; font-weight: 600; }

/* La grille d'outils : trois colonnes, des cartes de même hauteur, cliquables en entier. */
.outils { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 19rem), 1fr)); }
.outil {
  display: grid; align-content: start; gap: var(--space-3); min-height: 13rem; color: var(--ink); text-decoration: none;
  transition: border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.outil:hover { border-color: var(--action); box-shadow: 0 10px 28px rgb(2 7 10 / 0.12); }
.outil:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; }
.outil--a-faire { border-color: color-mix(in srgb, var(--proof) 60%, transparent); }
.outil__tete { display: flex; align-items: center; gap: var(--space-2); font-size: 1.0625rem; }
.outil__icone { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--surface-raised); font-size: 17px; }
.outil__question { font-style: italic; }
.outil__info { font-weight: 600; font-size: var(--type-body-sm-size); color: var(--action-ink); }
.outil__info--chaud { color: var(--proof-ink); }
.pastille { justify-self: start; padding: 2px var(--space-2); border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; }
.pastille--appel { background: var(--action-soft); color: var(--action-ink); }
.pastille--bloque { background: var(--proof-soft); color: var(--proof-ink); }
.apercu, .decisions, .passage { list-style: none; margin: 0; padding: 0; }
.apercu { display: grid; gap: var(--space-2); }
.apercu li { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-2); align-items: start; }
.apercu__texte { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: var(--type-body-sm-size); }
.decisions { display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.decisions li { padding-left: var(--space-3); border-left: 3px solid var(--proof); }
.avatars { display: flex; flex-wrap: wrap; gap: 4px; }
.avatars .eteint { opacity: 0.35; filter: grayscale(0.6); }
.frise { display: grid; grid-template-columns: repeat(14, 1fr); gap: 3px; }
.frise i { height: 18px; border-radius: 4px; background: var(--surface-raised); }
.frise i.passe { background: color-mix(in srgb, var(--action) 45%, var(--surface-raised)); }
.frise i.aujourdhui { background: var(--action); box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--action); }
.frise i.jalon { background: var(--proof); }
.passage { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.passage li { position: relative; }
.passage li:not(:last-child)::after { content: '→'; margin-left: var(--space-2); color: var(--ink-muted); }
.passage li { display: flex; align-items: center; }
.passage li.livre :deep(.j-avatar) { opacity: 0.5; }
.passage li.actuel :deep(.j-avatar) { box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--proof); }
@media (max-width: 640px) { .bandeau { grid-template-columns: 1fr; } .bandeau__lien { grid-column: 1; grid-row: auto; } }
</style>
