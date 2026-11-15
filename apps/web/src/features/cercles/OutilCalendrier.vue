<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar } from '../../design-system'
import { DUREE_TOUR_JOURS, lireTaches, type Avancement, type Cercle, type Tache } from './cercles'
import { joursDuTour } from './qg'
import { prenom } from './salon'

/*
 * Le calendrier du cercle (le « schedule » de Basecamp) : les 14 jours du tour en cours, avec aujourd'hui, la démo du
 * milieu et la livraison, et les tâches qui ont une échéance ; puis le planning de tous les tours. Les dates des tours
 * à venir sont estimées (un tour dure deux semaines et commence quand le précédent est livré).
 */
const props = defineProps<{ cercle: Cercle; suivi: Avancement | null }>()
const { t, locale } = useI18n()

const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)
const debut = computed(() => (props.suivi ? new Date(Date.now() - (props.suivi.jour - 1) * 86_400_000) : null))
const jours = computed(() => (debut.value ? joursDuTour(debut.value.toISOString()) : []))

const taches = ref<Tache[]>([])
onMounted(async () => {
  if (tour.value?.projetId) taches.value = (await lireTaches(tour.value.projetId)) ?? []
})
const echeancesDu = (iso: string) => taches.value.filter((x) => x.echeance === iso)

const court = (d: Date) => new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric' }).format(d)
const date = (d: Date) => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'long' }).format(d)

/** Le planning des tours : livrés (date réelle), en cours (depuis son ouverture), à venir (estimés). */
const planning = computed(() => {
  const enCours = props.cercle.tourEnCours
  return props.cercle.tours.map((x) => {
    let periode: string | null = null
    if (x.statut === 'livre' && x.livreLe) periode = t('cercles.qg.livreLe', { date: date(new Date(x.livreLe)) })
    else if (x.statut === 'en_cours' && debut.value) {
      const fin = new Date(debut.value.getTime() + (DUREE_TOUR_JOURS - 1) * 86_400_000)
      periode = t('cercles.qg.du', { debut: date(debut.value), fin: date(fin) })
    } else if (enCours && debut.value) {
      const depart = new Date(debut.value.getTime() + (x.numero - enCours) * DUREE_TOUR_JOURS * 86_400_000)
      periode = t('cercles.qg.vers', { date: date(depart) })
    }
    return { tour: x, periode }
  })
})
</script>

<template>
  <section class="outil-calendrier" aria-labelledby="calendrier-titre" data-test="outil-calendrier-page">
    <header class="tete carte">
      <span class="icone" aria-hidden="true">📅</span>
      <div>
        <h2 id="calendrier-titre">{{ t('cercles.qg.outils.calendrier') }}</h2>
        <p class="texte-discret">{{ t('cercles.qg.calendrierAide') }}</p>
      </div>
    </header>

    <section v-if="jours.length" class="carte" aria-labelledby="quinzaine">
      <h3 id="quinzaine">{{ t('cercles.qg.quinzaine', { n: tour?.numero, nom: prenom(tour?.beneficiaire.nom) }) }}</h3>
      <ol class="jours" data-test="jours-du-tour">
        <li v-for="j in jours" :key="j.iso" class="jour" :class="{ passe: j.passe, aujourdhui: j.aujourdhui, [`jour--${j.jalon}`]: j.jalon }" :aria-current="j.aujourdhui ? 'date' : undefined">
          <span class="jour__date">{{ court(j.date) }}</span>
          <span class="jour__numero">{{ t('cercles.qg.jourN', { n: j.numero }) }}</span>
          <span v-if="j.aujourdhui" class="etiquette etiquette--aujourdhui">{{ t('cercles.salon.aujourdhui') }}</span>
          <span v-if="j.jalon" class="etiquette etiquette--jalon">{{ t(`cercles.qg.jalons.${j.jalon}`) }}</span>
          <span v-for="x in echeancesDu(j.iso)" :key="x.id" class="echeance">{{ x.titre }}</span>
        </li>
      </ol>
    </section>
    <p v-else class="texte-discret">{{ t('cercles.qg.calendrierAvant') }}</p>

    <section class="carte" aria-labelledby="planning">
      <h3 id="planning">{{ t('cercles.qg.planning') }}</h3>
      <ol class="planning" data-test="planning">
        <li v-for="p in planning" :key="p.tour.numero" class="planning__ligne" :class="`planning__ligne--${p.tour.statut}`">
          <span class="planning__numero">{{ p.tour.numero }}</span>
          <JAvatar :nom="p.tour.beneficiaire.nom" :url="p.tour.beneficiaire.avatarUrl" :taille="32" />
          <span class="planning__qui"><strong>{{ p.tour.beneficiaire.nom }}</strong><span class="texte-discret">{{ p.periode }}</span></span>
          <span class="planning__statut">{{ t(`cercles.statutTour.${p.tour.statut}`) }}</span>
        </li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.outil-calendrier { display: grid; gap: var(--space-4); }
.tete { display: flex; gap: var(--space-4); align-items: flex-start; }
.tete h2, .tete p { margin: 0; }
.icone { display: grid; place-items: center; flex: none; width: 48px; height: 48px; border-radius: 14px; background: var(--surface-raised); font-size: 24px; }
h3 { margin: 0 0 var(--space-3); font-size: var(--type-body-size); }
.jours { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); grid-template-columns: repeat(7, minmax(0, 1fr)); }
.jour { display: grid; align-content: start; gap: 4px; min-height: 5.5rem; padding: var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); font-size: var(--type-body-sm-size); }
.jour.passe { background: var(--surface-raised); color: var(--ink-muted); }
.jour.aujourdhui { border: 2px solid var(--action); }
.jour--demo, .jour--livraison { border-color: color-mix(in srgb, var(--proof) 60%, transparent); }
.jour__date { font-weight: 700; text-transform: capitalize; }
.jour__numero { color: var(--ink-muted); font-size: 0.75rem; }
.etiquette { justify-self: start; padding: 1px 6px; border-radius: var(--radius-pill); font-size: 0.6875rem; font-weight: 700; }
.etiquette--aujourdhui { background: var(--action); color: var(--on-action); }
.etiquette--jalon { background: var(--proof-soft); color: var(--proof-ink); }
.echeance { padding: 2px 6px; border-radius: 6px; background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; overflow-wrap: anywhere; }
.planning { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.planning__ligne { display: grid; grid-template-columns: 1.75rem auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; min-height: 48px; }
.planning__numero { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: var(--surface-raised); font-weight: 700; }
.planning__qui { display: grid; min-width: 0; }
.planning__statut { font-size: var(--type-body-sm-size); font-weight: 600; color: var(--ink-muted); }
.planning__ligne--en_cours .planning__numero { background: var(--proof); color: #02070A; }
.planning__ligne--en_cours .planning__statut { color: var(--proof-ink); }
.planning__ligne--livre { opacity: 0.75; }
@media (max-width: 720px) { .jours { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
