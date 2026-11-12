<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { ilYa } from '../vitrine/vitrine'
import { joursRestants, lireCercle, ordreDePassage, positionsAnneau, pourcentage, tourEnCours, type Cercle } from './cercles'

/*
 * La page d'un cercle : qui en fait partie, l'ordre de passage, le tour en cours et le message du Griot.
 * L'anneau montre la règle de la tontine d'un coup d'œil : chacun son tour, et Systalink au centre, garant.
 */
const { t, locale } = useI18n()
const route = useRoute()
const id = computed(() => String(route.params.id))

const cercle = ref<Cercle | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    cercle.value = await lireCercle(id.value)
    etat.value = cercle.value ? 'pret' : 'introuvable'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(id, charger, { immediate: true })

const ordre = computed(() => (cercle.value ? ordreDePassage(cercle.value) : []))
const tour = computed(() => (cercle.value ? tourEnCours(cercle.value) : null))
const beneficiaire = computed(() => ordre.value.find((m) => m.utilisateurId === tour.value?.beneficiaireId) ?? null)
const places = computed(() => positionsAnneau(ordre.value.length))
const tourDe = (utilisateurId: string) => cercle.value?.tours.find((x) => x.beneficiaireId === utilisateurId) ?? null
</script>

<template>
  <section class="cercle-page">
    <p class="retour"><RouterLink to="/cercles">{{ t('cercles.tous') }}</RouterLink></p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/cercles">{{ t('cercles.tous') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <template v-else-if="cercle">
      <header class="page-tete motif motif--reseau">
        <div class="page-tete__texte">
          <h1>{{ cercle.nom }}</h1>
          <p>{{ t('cercles.sousTitre', { n: cercle.membres.length, ville: cercle.ville, pays: nomPays(cercle.pays, locale) }) }}</p>
        </div>
      </header>

      <div class="mise-en-page">
        <!-- L'anneau : les membres dans l'ordre de passage, le bénéficiaire du tour mis en avant. -->
        <figure class="anneau carte" data-test="anneau">
          <div class="anneau__scene">
            <svg class="anneau__trace" viewBox="0 0 100 100" aria-hidden="true">
              <circle cx="50" cy="50" r="40" />
            </svg>
            <div class="anneau__garant">
              <strong>Systalink</strong>
              <span>{{ t('cercles.garant') }}</span>
            </div>
            <div
              v-for="(m, i) in ordre"
              :key="m.utilisateurId"
              class="anneau__membre"
              :class="{ 'anneau__membre--tour': m.utilisateurId === tour?.beneficiaireId, 'anneau__membre--livre': tourDe(m.utilisateurId)?.statut === 'livre' }"
              :style="{ left: `${places[i]!.x}%`, top: `${places[i]!.y}%`, '--i': i }"
            >
              <JAvatar :nom="m.nom" :url="m.avatarUrl" :taille="52" />
              <span class="anneau__nom">{{ m.nom.split(' ')[0] }}</span>
            </div>
          </div>
          <figcaption class="visuellement-cache">{{ t('cercles.anneauDescription', { ordre: ordre.map((m) => m.nom).join(', ') }) }}</figcaption>
        </figure>

        <div class="cote">
          <section v-if="tour && beneficiaire" class="carte tour" aria-labelledby="tour-titre" data-test="tour">
            <p class="tour__surtitre">{{ t('cercles.tourDe', { n: tour.numero, nom: beneficiaire.nom.split(' ')[0] }) }}</p>
            <h2 id="tour-titre">{{ tour.titre }}</h2>
            <div class="avancement">
              <div class="avancement__barre" role="progressbar" :aria-valuenow="pourcentage(tour.avancement)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')">
                <i :style="{ width: `${pourcentage(tour.avancement)}%` }" />
              </div>
              <p>{{ t('cercles.taches', { faites: tour.avancement.faites, total: tour.avancement.total }) }} · {{ t('cercles.joursRestants', { n: joursRestants(tour) }, joursRestants(tour)) }}</p>
            </div>
            <a v-if="tour.depot" :href="tour.depot.url" class="depot" target="_blank" rel="noopener noreferrer" data-test="depot">
              <span class="depot__icone" aria-hidden="true">⎇</span>
              <span>
                <strong>{{ t('cercles.depot') }}</strong>
                <span class="depot__url">{{ tour.depot.url.replace(/^https?:\/\//, '') }}</span>
              </span>
              <span class="visuellement-cache">({{ t('vitrine.nouvelOnglet') }})</span>
            </a>
          </section>
          <section v-else-if="!cercle.tirageFait" class="carte tour" data-test="attente-tirage">
            <h2>{{ t('cercles.avantTirage') }}</h2>
            <p class="texte-discret">{{ t('cercles.avantTirageAide', { n: cercle.taille - cercle.membres.length }, cercle.taille - cercle.membres.length) }}</p>
          </section>

          <!-- Le Griot propose, le cercle décide : son message est présenté comme un résumé, pas comme une décision. -->
          <section v-if="cercle.messageGriot" class="griot zone-sombre" aria-labelledby="griot-titre" data-test="griot">
            <h2 id="griot-titre" class="griot__qui">{{ t('cercles.griot') }} · {{ cercle.nom }}</h2>
            <p class="griot__texte">{{ cercle.messageGriot.texte }}</p>
            <p class="griot__quand">{{ ilYa(cercle.messageGriot.le, locale) }}</p>
          </section>
        </div>
      </div>

      <section class="ordre" aria-labelledby="ordre-titre">
        <h2 id="ordre-titre">{{ t('cercles.ordre') }}</h2>
        <ol class="ordre__liste" data-test="ordre">
          <li v-for="m in ordre" :key="m.utilisateurId" class="carte ordre__ligne" :class="`ordre__ligne--${tourDe(m.utilisateurId)?.statut ?? 'a_venir'}`">
            <span class="ordre__numero">{{ m.ordre ?? '–' }}</span>
            <JAvatar :nom="m.nom" :url="m.avatarUrl" :taille="40" />
            <span class="ordre__qui">
              <strong>{{ m.nom }}</strong>
              <span>{{ m.idee?.titre ?? t('cercles.pasDIdee') }}</span>
            </span>
            <span class="ordre__confiance" :title="t('cercles.confianceAide')">{{ t('cercles.confiance', { n: m.confiance }) }}</span>
            <span class="ordre__statut">{{ t(`cercles.statutTour.${tourDe(m.utilisateurId)?.statut ?? 'a_venir'}`) }}</span>
          </li>
        </ol>
      </section>
    </template>
  </section>
</template>

<style scoped>
.cercle-page { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.mise-en-page { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 860px) { .mise-en-page { grid-template-columns: 1fr; } }

.anneau { margin: 0; padding: var(--space-6); }
.anneau__scene { position: relative; aspect-ratio: 1; max-width: 30rem; margin: 0 auto; }
.anneau__trace { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.anneau__trace circle {
  fill: none; stroke: var(--line-strong); stroke-width: 0.5; stroke-dasharray: 1.2 1.8;
  animation: tourne 60s linear infinite; transform-origin: 50% 50%;
}
.anneau__garant {
  position: absolute; left: 50%; top: 50%; translate: -50% -50%;
  display: grid; place-content: center; justify-items: center; gap: 2px; text-align: center; width: 34%; aspect-ratio: 1; border-radius: 50%;
  border: 2px solid var(--action); background: var(--surface);
  box-shadow: 0 0 0 10px color-mix(in srgb, var(--action) 8%, transparent);
}
.anneau__garant strong { font-family: var(--font-display); font-size: clamp(0.9rem, 2.4vw, 1.25rem); }
.anneau__garant span { color: var(--action-ink); font-size: var(--type-body-sm-size); }
.anneau__membre {
  position: absolute; translate: -50% -50%;
  display: grid; justify-items: center; gap: var(--space-1);
  animation: apparait var(--motion-celebrate) var(--ease-pop) both; animation-delay: calc(var(--i) * 70ms);
}
.anneau__membre :deep(.j-avatar) { box-shadow: 0 0 0 3px var(--surface); }
.anneau__nom { padding: 0 var(--space-2); border-radius: var(--radius-pill); background: var(--surface); font-size: var(--type-body-sm-size); font-weight: 600; }
/* Le bénéficiaire du tour : un halo orange, la couleur de la preuve. */
.anneau__membre--tour :deep(.j-avatar) { box-shadow: 0 0 0 3px var(--surface), 0 0 0 7px var(--proof), 0 0 26px color-mix(in srgb, var(--proof) 55%, transparent); }
.anneau__membre--livre { opacity: 0.6; }

.cote { display: grid; gap: var(--space-4); }
.tour { display: grid; gap: var(--space-3); padding: var(--space-6); }
.tour h2 { margin: 0; font-size: 1.375rem; }
.tour__surtitre { margin: 0; color: var(--proof-ink); font-weight: 700; font-size: var(--type-body-sm-size); }
.avancement { display: grid; gap: var(--space-2); }
.avancement p { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.avancement__barre { height: 10px; border-radius: var(--radius-pill); background: var(--surface-sunken, color-mix(in srgb, var(--ink) 8%, transparent)); overflow: hidden; }
.avancement__barre i { display: block; height: 100%; border-radius: inherit; background: var(--action); animation: remplit var(--motion-celebrate) var(--ease-out) both; transform-origin: left; }
.depot {
  display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4);
  border: 1px solid var(--line); border-radius: var(--radius-md); color: var(--ink); text-decoration: none;
  transition: border-color var(--motion-fast) var(--ease-out);
}
.depot:hover { border-color: var(--action); }
.depot__icone { display: grid; place-items: center; width: 36px; height: 36px; border-radius: var(--radius-md); background: var(--action-soft); color: var(--action-ink); font-size: 1.1rem; }
.depot > span:nth-child(2) { display: grid; min-width: 0; }
.depot__url { color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.griot { display: grid; gap: var(--space-2); padding: var(--space-5, 1.25rem) var(--space-6); border-radius: var(--radius-lg); background: var(--bg); color: var(--ink); }
.griot__qui { margin: 0; font-size: var(--type-body-sm-size); color: var(--action-ink); font-weight: 700; }
.griot__texte { margin: 0; line-height: 1.55; }
.griot__quand { margin: 0; justify-self: end; color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.ordre h2 { margin-bottom: var(--space-4); }
.ordre__liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.ordre__ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4); }
.ordre__numero { width: 1.5rem; text-align: center; font-family: var(--font-display); font-weight: 700; color: var(--ink-muted); font-variant-numeric: tabular-nums; }
.ordre__qui { flex: 1; display: grid; min-width: 0; }
.ordre__qui span { color: var(--ink-muted); font-size: var(--type-body-sm-size); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ordre__confiance { font-size: var(--type-body-sm-size); color: var(--ink-muted); white-space: nowrap; }
.ordre__statut { padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600; background: color-mix(in srgb, var(--ink) 7%, transparent); white-space: nowrap; }
.ordre__ligne--en_cours { border-color: var(--proof); }
.ordre__ligne--en_cours .ordre__statut { background: var(--proof); color: var(--on-proof); }
.ordre__ligne--livre .ordre__statut { background: var(--action-soft); color: var(--action-ink); }
@media (max-width: 600px) { .ordre__confiance { display: none; } }

@keyframes tourne { to { rotate: 360deg; } }
@keyframes apparait { from { opacity: 0; scale: 0.6; } }
@keyframes remplit { from { transform: scaleX(0); } }
@media (prefers-reduced-motion: reduce) { .anneau__trace circle { animation: none; } }
</style>
