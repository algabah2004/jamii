<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar } from '../../design-system'
import EnTeteOutil from './EnTeteOutil.vue'
import IconeCercle from './IconeCercle.vue'
import { ajouterJalon, changerJalon, chefDeProjet, DUREE_TOUR_JOURS, lireJalons, lireTaches, type Avancement, type Cercle, type Jalon, type Tache } from './cercles'
import { joursDuTour } from './qg'
import { prenom } from './salon'

/*
 * Le calendrier du cercle (le « schedule » de Basecamp). Il est tenu par le chef de projet du tour (le membre dont c'est
 * le tour) : lui seul pose les étapes et les marque atteintes ; tout le monde les voit. La livraison au 14e jour est
 * fixe : c'est la règle de la tontine. Viennent ensuite les échéances des tâches et le planning de tous les tours (les
 * dates des tours à venir sont estimées : un tour dure deux semaines et commence quand le précédent est livré).
 */
const props = defineProps<{ cercle: Cercle; suivi: Avancement | null; moiId: string | null }>()
const { t, locale } = useI18n()

const tour = computed(() => props.cercle.tours.find((x) => x.numero === props.cercle.tourEnCours) ?? null)
const chef = computed(() => chefDeProjet(props.cercle))
const jeSuisChef = computed(() => !!props.moiId && chef.value.id === props.moiId)
const debut = computed(() => (props.suivi ? new Date(Date.now() - (props.suivi.jour - 1) * 86_400_000) : null))
const jours = computed(() => (debut.value ? joursDuTour(debut.value.toISOString()) : []))

const taches = ref<Tache[]>([])
const jalons = ref<Jalon[]>([])
onMounted(async () => {
  const projet = tour.value?.projetId
  if (!projet) return
  const [t1, j1] = await Promise.all([lireTaches(projet), lireJalons(projet)])
  taches.value = t1 ?? []
  jalons.value = j1 ?? []
})
const echeancesDu = (iso: string) => taches.value.filter((x) => x.echeance === iso && x.statut !== 'livree')
const etapesDu = (iso: string) => jalons.value.filter((x) => x.echeance === iso)
const etapes = computed(() => [...jalons.value].sort((a, b) => (a.echeance ?? '9999').localeCompare(b.echeance ?? '9999') || a.ordre - b.ordre))

// ---------------------------------------------------------------- le chef de projet pose les étapes

const nouvelle = ref({ titre: '', echeance: '' })
const envoi = ref(false)
const erreur = ref<string | null>(null)
const valide = computed(() => nouvelle.value.titre.trim().length >= 3 && !!nouvelle.value.echeance)
async function ajouter() {
  if (!valide.value || !tour.value?.projetId) return
  envoi.value = true
  erreur.value = null
  const r = await ajouterJalon(tour.value.projetId, nouvelle.value.titre.trim(), nouvelle.value.echeance)
  envoi.value = false
  if (!r.ok) return void (erreur.value = t(r.code === 'pas_chef_de_projet' ? 'cercles.chef.reserveCalendrier' : 'cercles.tachesTour.erreur'))
  jalons.value = [...jalons.value, r.jalon]
  nouvelle.value = { titre: '', echeance: '' }
}
async function basculer(j: Jalon) {
  if (!tour.value?.projetId || !jeSuisChef.value) return
  const r = await changerJalon(tour.value.projetId, j.id, { statut: j.statut === 'atteint' ? 'prevu' : 'atteint' })
  if (r.ok) jalons.value = jalons.value.map((x) => (x.id === r.jalon.id ? r.jalon : x))
  else erreur.value = t('cercles.tachesTour.erreur')
}

// ---------------------------------------------------------------- affichage

const court = (d: Date) => new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric' }).format(d)
const date = (d: Date) => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'long' }).format(d)
const dateIso = (iso: string) => new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(`${iso}T12:00:00Z`))
const bornes = computed(() => (jours.value.length ? { min: jours.value[0]!.iso, max: jours.value.at(-1)!.iso } : { min: undefined, max: undefined }))

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
    <EnTeteOutil id="calendrier-titre" icone="calendrier" :titre="t('cercles.qg.outils.calendrier')" :aide="t('cercles.chef.calendrierTenuPar', { nom: chef.nom })" />

    <section v-if="jours.length" class="carte" aria-labelledby="quinzaine">
      <h3 id="quinzaine">{{ t('cercles.qg.quinzaine', { n: tour?.numero, nom: prenom(tour?.beneficiaire.nom) }) }}</h3>
      <ol class="jours" data-test="jours-du-tour">
        <li v-for="j in jours" :key="j.iso" class="jour" :class="{ passe: j.passe, aujourdhui: j.aujourdhui }" :aria-current="j.aujourdhui ? 'date' : undefined">
          <span class="jour__tete"><span class="jour__date">{{ court(j.date) }}</span><span class="jour__numero">{{ t('cercles.qg.jourN', { n: j.numero }) }}</span></span>
          <span v-if="j.aujourdhui" class="marque marque--aujourdhui">{{ t('cercles.salon.aujourdhui') }}</span>
          <span v-for="e in etapesDu(j.iso)" :key="e.id" class="marque marque--etape" :class="{ atteinte: e.statut === 'atteint' }" data-test="etape-jour">
            <IconeCercle :nom="e.statut === 'atteint' ? 'coche' : 'depart'" :taille="12" />{{ e.titre }}
          </span>
          <span v-if="j.jalon === 'livraison'" class="marque marque--livraison"><IconeCercle nom="fin" :taille="12" />{{ t('cercles.qg.jalons.livraison') }}</span>
          <span v-for="x in echeancesDu(j.iso)" :key="x.id" class="marque marque--tache">{{ x.titre }}</span>
        </li>
      </ol>
    </section>
    <p v-else class="texte-discret">{{ t('cercles.qg.calendrierAvant') }}</p>

    <div class="deux">
      <section class="carte" aria-labelledby="etapes" data-test="etapes">
        <h3 id="etapes">{{ t('cercles.chef.etapes') }}</h3>
        <p v-if="!etapes.length" class="texte-discret">{{ jeSuisChef ? t('cercles.chef.aucuneEtapeChef') : t('cercles.chef.aucuneEtape', { nom: prenom(chef.nom) }) }}</p>
        <ul v-else class="etapes">
          <li v-for="e in etapes" :key="e.id" class="etape" :class="{ 'etape--atteinte': e.statut === 'atteint' }">
            <input v-if="jeSuisChef" type="checkbox" :checked="e.statut === 'atteint'" :aria-label="t('cercles.chef.marquerAtteinte', { titre: e.titre })" data-test="atteindre-etape" @change="basculer(e)" />
            <IconeCercle v-else :nom="e.statut === 'atteint' ? 'coche' : 'depart'" :taille="16" />
            <span class="etape__titre">{{ e.titre }}</span>
            <span class="etape__date">{{ e.echeance ? dateIso(e.echeance) : '' }}</span>
          </li>
        </ul>

        <form v-if="jeSuisChef && tour?.projetId" class="ajout" data-test="ajouter-etape" @submit.prevent="ajouter">
          <label class="visuellement-cache" for="etape-titre">{{ t('cercles.chef.titreEtape') }}</label>
          <input id="etape-titre" v-model="nouvelle.titre" maxlength="120" :placeholder="t('cercles.chef.titreEtapeExemple')" />
          <label class="visuellement-cache" for="etape-date">{{ t('cercles.tache.echeance') }}</label>
          <input id="etape-date" v-model="nouvelle.echeance" type="date" :min="bornes.min" :max="bornes.max" />
          <button type="submit" class="ajouter" :disabled="!valide || envoi"><IconeCercle nom="plus" :taille="16" />{{ t('cercles.chef.ajouterEtape') }}</button>
        </form>
        <p v-else-if="tour" class="texte-discret note">
          {{ t('cercles.chef.proposerEtape', { nom: prenom(chef.nom) }) }}
          <RouterLink :to="{ query: { outil: 'salon' } }">{{ t('cercles.chef.allerAuSalon') }}</RouterLink>
        </p>
        <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      </section>

      <section class="carte" aria-labelledby="planning">
        <h3 id="planning">{{ t('cercles.qg.planning') }}</h3>
        <ol class="planning" data-test="planning">
          <li v-for="p in planning" :key="p.tour.numero" class="planning__ligne" :class="`planning__ligne--${p.tour.statut}`">
            <span class="planning__numero">{{ p.tour.numero }}</span>
            <JAvatar :nom="p.tour.beneficiaire.nom" :url="p.tour.beneficiaire.avatarUrl" :taille="28" />
            <span class="planning__qui"><strong>{{ p.tour.beneficiaire.nom }}</strong><span class="texte-discret">{{ p.periode }}</span></span>
            <span class="planning__statut">{{ t(`cercles.statutTour.${p.tour.statut}`) }}</span>
          </li>
        </ol>
      </section>
    </div>
  </section>
</template>

<style scoped>
.outil-calendrier { display: grid; gap: var(--space-6); }
h3 { margin: 0 0 var(--space-3); font-size: var(--type-body-size); }
.jours { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); grid-template-columns: repeat(7, minmax(0, 1fr)); }
.jour { display: grid; align-content: start; gap: 4px; min-height: 6rem; padding: var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); font-size: var(--type-body-sm-size); }
.jour.passe { background: var(--surface-raised); }
.jour.passe .jour__date { color: var(--ink-muted); }
.jour.aujourdhui { border-color: var(--action); box-shadow: inset 0 0 0 1px var(--action); }
.jour__tete { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-1); }
.jour__date { font-weight: 700; text-transform: capitalize; }
.jour__numero { color: var(--ink-muted); font-size: 0.6875rem; }
.marque { display: inline-flex; align-items: center; gap: 4px; justify-self: start; max-width: 100%; padding: 1px 6px; border-radius: 6px; font-size: 0.6875rem; font-weight: 600; overflow-wrap: anywhere; }
.marque--aujourdhui { background: var(--action); color: var(--on-action); }
.marque--etape { background: var(--ink); color: var(--surface); }
.marque--etape.atteinte { background: var(--surface-raised); color: var(--ink-muted); text-decoration: line-through; }
.marque--livraison { background: var(--proof-soft); color: var(--proof-ink); }
.marque--tache { background: var(--action-soft); color: var(--action-ink); font-weight: 500; }

.deux { display: grid; gap: var(--space-4); grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; }
.etapes { list-style: none; margin: 0 0 var(--space-3); padding: 0; }
.etape { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; min-height: 44px; border-top: 1px solid var(--line); }
.etape:first-child { border-top: 0; }
.etape input { width: 18px; height: 18px; margin: 0; accent-color: var(--proof); cursor: pointer; }
.etape__titre { font-weight: 600; overflow-wrap: anywhere; }
.etape__date { color: var(--ink-muted); font-size: 0.8125rem; text-transform: capitalize; white-space: nowrap; }
.etape--atteinte .etape__titre { color: var(--ink-muted); text-decoration: line-through; }
.ajout { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: var(--space-2); }
.ajout input { min-height: 40px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); color: var(--ink); font: inherit; min-width: 0; }
.ajout input:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.ajouter { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 var(--space-4); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-size: var(--type-body-sm-size); font-weight: 600; cursor: pointer; white-space: nowrap; }
.ajouter:disabled { opacity: 0.5; cursor: not-allowed; }
.ajouter:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.note { margin: 0; font-size: var(--type-body-sm-size); }
.erreur { margin: var(--space-2) 0 0; color: var(--danger); font-weight: 600; font-size: var(--type-body-sm-size); }

.planning { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-1); }
.planning__ligne { display: grid; grid-template-columns: 1.75rem auto minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; min-height: 48px; }
.planning__numero { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; border: 1px solid var(--line); font-size: 0.8125rem; font-weight: 700; }
.planning__qui { display: grid; min-width: 0; }
.planning__statut { font-size: 0.8125rem; font-weight: 600; color: var(--ink-muted); }
.planning__ligne--en_cours .planning__numero { background: var(--ink); border-color: var(--ink); color: var(--surface); }
.planning__ligne--en_cours .planning__statut { color: var(--ink); }
.planning__ligne--livre { opacity: 0.7; }
@media (max-width: 1000px) { .deux { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 720px) {
  .jours { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ajout { grid-template-columns: minmax(0, 1fr); }
}
</style>
