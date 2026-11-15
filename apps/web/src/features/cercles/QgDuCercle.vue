<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { useSession } from '../../session'
import CollineDuTour from './CollineDuTour.vue'
import IconeCercle from './IconeCercle.vue'
import { lireTaches, pourcentage, type Avancement, type Cercle, type Tache } from './cercles'
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
const jours = computed(() => (props.suivi && tour.value ? joursDuTour(new Date(Date.now() - (props.suivi.jour - 1) * 86_400_000).toISOString()) : []))
/** La prochaine étape datée du tour : la démo du milieu, puis la livraison. */
const prochainJalon = computed(() => {
  const j = jours.value.find((x) => x.jalon && !x.passe)
  if (!j?.jalon) return null
  const date = new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(j.date)
  return t(`cercles.qg.prochain.${j.jalon}`, { date })
})
const lien = (outil: string) => ({ query: { outil } })
</script>

<template>
  <div class="qg" data-test="qg">
    <!-- Le tour en cours : ce qui compte cette quinzaine. -->
    <section v-if="tour" class="bandeau carte" aria-labelledby="qg-tour">
      <div class="bandeau__texte">
        <p class="bandeau__surtitre">{{ t('cercles.tourDe', { n: tour.numero, nom: prenom(tour.beneficiaire.nom) }) }}</p>
        <h2 id="qg-tour">{{ titreTour ?? t('cercles.projetDe', { nom: prenom(tour.beneficiaire.nom) }) }}</h2>
        <p v-if="suivi" class="texte-discret">{{ t('cercles.salon.contexteJour', { jour: suivi.jour, faites: suivi.faites, total: suivi.total }) }} · {{ t('cercles.joursRestants', { n: suivi.joursRestants }, suivi.joursRestants) }}</p>
      </div>
      <RouterLink :to="lien('cercle')" class="bandeau__lien">{{ t('cercles.qg.voirLeTour') }}</RouterLink>
      <div v-if="suivi" class="bandeau__barre" role="progressbar" :aria-valuenow="pourcentage(suivi)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')"><i :style="{ width: `${pourcentage(suivi)}%` }" /></div>
    </section>
    <section v-else-if="cercle.statut === 'termine'" class="bandeau carte">
      <div class="bandeau__texte"><h2>{{ t('cercles.termine') }}</h2></div>
    </section>

    <div class="outils">
      <RouterLink :to="lien('salon')" class="outil carte" data-test="outil-salon">
        <span class="outil__tete"><IconeCercle nom="salon" /><strong>{{ t('cercles.qg.outils.salon') }}</strong></span>
        <ul v-if="derniers.length" class="apercu">
          <li v-for="m in derniers" :key="m.id"><strong>{{ prenom(m.auteur?.nom) }}</strong> {{ m.texte }}</li>
        </ul>
        <span v-else class="outil__vide">{{ t('cercles.salon.vide') }}</span>
        <span v-if="salon?.appel || salon?.coupsDeMain.length" class="outil__etat">
          {{ [salon?.appel ? t('cercles.qg.appelEnCours') : null, salon?.coupsDeMain.length ? t('cercles.salon.coupsTitre', { n: salon.coupsDeMain.length }, salon.coupsDeMain.length) : null].filter(Boolean).join(' · ') }}
        </span>
      </RouterLink>

      <RouterLink :to="lien('taches')" class="outil carte" data-test="outil-taches">
        <span class="outil__tete"><IconeCercle nom="taches" /><strong>{{ t('cercles.qg.outils.taches') }}</strong></span>
        <CollineDuTour v-if="taches.length" :taches="taches" compacte />
        <span class="outil__vide">{{ taches.length ? t('cercles.taches', { faites: taches.filter((x) => x.statut === 'livree').length, total: taches.length }) : t('cercles.qg.aucuneTache') }}</span>
        <span v-if="miennes.length" class="outil__etat">{{ t('cercles.qg.mesTaches', { n: miennes.length }, miennes.length) }}</span>
      </RouterLink>

      <RouterLink :to="lien('points')" class="outil carte" data-test="outil-points">
        <span class="outil__tete"><IconeCercle nom="points" /><strong>{{ t('cercles.qg.outils.points') }}</strong></span>
        <span class="outil__vide">{{ t('cercles.salon.pointsCompte', { n: points.length, total: cercle.membres.length }) }}</span>
        <span v-if="!pointFait && tour" class="outil__etat">{{ t('cercles.qg.aToi') }}</span>
      </RouterLink>

      <RouterLink :to="lien('decisions')" class="outil carte" data-test="outil-decisions">
        <span class="outil__tete"><IconeCercle nom="decisions" /><strong>{{ t('cercles.qg.outils.decisions') }}</strong></span>
        <ul v-if="salon?.decisions.length" class="apercu">
          <li v-for="d in salon.decisions.slice(0, 2)" :key="d.id">{{ d.texte }}</li>
        </ul>
        <span v-else class="outil__vide">{{ t('cercles.qg.aucuneDecision') }}</span>
      </RouterLink>

      <RouterLink :to="lien('calendrier')" class="outil carte" data-test="outil-calendrier">
        <span class="outil__tete"><IconeCercle nom="calendrier" /><strong>{{ t('cercles.qg.outils.calendrier') }}</strong></span>
        <span class="outil__vide">{{ suivi ? t('cercles.qg.jourSur', { jour: suivi.jour }) : t('cercles.qg.calendrierAvant') }}</span>
        <span v-if="prochainJalon" class="outil__etat">{{ prochainJalon }}</span>
      </RouterLink>

      <RouterLink :to="lien('cercle')" class="outil carte" data-test="outil-cercle">
        <span class="outil__tete"><IconeCercle nom="cercle" /><strong>{{ t('cercles.qg.outils.cercle') }}</strong></span>
        <span class="outil__vide">{{ t('cercles.qg.ordreResume', { n: cercle.tours.filter((x) => x.statut === 'livre').length, total: cercle.tours.length || cercle.taille }) }}</span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.qg { display: grid; gap: var(--space-6); }
.bandeau { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-3) var(--space-6); align-items: center; }
.bandeau__texte { display: grid; gap: var(--space-1); min-width: 0; }
.bandeau__texte h2, .bandeau__texte p { margin: 0; }
.bandeau__surtitre { color: var(--ink-muted); font-weight: 600; font-size: var(--type-body-sm-size); }
.bandeau__lien { font-weight: 600; }
.bandeau__barre { grid-column: 1 / -1; height: 6px; border-radius: var(--radius-pill); background: var(--surface-raised); overflow: hidden; }
.bandeau__barre i { display: block; height: 100%; background: var(--action); border-radius: inherit; }

/* Six outils, même forme : un titre, ce qu'il faut savoir, et au plus une ligne d'état. */
.outils { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 19rem), 1fr)); }
.outil { display: grid; align-content: start; gap: var(--space-3); min-height: 11rem; color: var(--ink); text-decoration: none; transition: border-color var(--motion-fast) var(--ease-out); }
.outil:hover { border-color: var(--action); }
.outil:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; }
.outil__tete { display: flex; align-items: center; gap: var(--space-2); font-size: 1.0625rem; }
.outil__vide { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.outil__etat { font-weight: 600; font-size: var(--type-body-sm-size); color: var(--action-ink); }
.apercu { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.apercu li { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
@media (max-width: 640px) { .bandeau { grid-template-columns: 1fr; } }
</style>
