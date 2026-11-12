<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JChamp, JDeplier, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import { nomPays } from '../systalink/calculs'
import { ilYa } from '../vitrine/vitrine'
import { avantTirage, deposerIdee, livrerTour, estMembre, joursRestants, LIMITES, lireCercle, ordreDePassage, positionsAnneau, pourcentage, rejoindreCercle, tirerAuSort, tourEnCours, type Cercle, type Resultat } from './cercles'

/*
 * La page d'un cercle : qui en fait partie, l'ordre de passage, le tour en cours et le message du Griot.
 * L'anneau montre la règle de la tontine d'un coup d'œil : chacun son tour, et Systalink au centre, garant.
 */
const { t, locale } = useI18n()
const { moi } = useSession()
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
// Avant le tirage, l'anneau montre aussi les places libres : on voit combien il manque de monde.
const places = computed(() => positionsAnneau(cercle.value ? Math.max(cercle.value.taille, ordre.value.length) : 0))
const placesLibres = computed(() => places.value.slice(ordre.value.length))
const tourDe = (utilisateurId: string) => cercle.value?.tours.find((x) => x.beneficiaireId === utilisateurId) ?? null

const membre = computed(() => (cercle.value ? estMembre(cercle.value, moi.value?.id) : false))
const createur = computed(() => !!cercle.value && cercle.value.createurId === moi.value?.id)
const preparation = computed(() => (cercle.value ? avantTirage(cercle.value) : null))
const monIdee = computed(() => cercle.value?.membres.find((m) => m.utilisateurId === moi.value?.id)?.idee ?? null)

// Une seule action à la fois ; l'erreur dit ce qui bloque, avec le code renvoyé par l'API.
const action = ref<'repos' | 'rejoindre' | 'idee' | 'tirage' | 'livrer'>('repos')
const erreur = ref<string | null>(null)
async function agir(quoi: 'rejoindre' | 'idee' | 'tirage' | 'livrer', appel: () => Promise<Resultat>) {
  action.value = quoi
  erreur.value = null
  try {
    const r = await appel()
    if (r.ok) {
      cercle.value = r.cercle
      if (quoi === 'idee') ideeOuverte.value = false
      if (quoi === 'livrer') confirmerLivraison.value = false
    } else erreur.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    action.value = 'repos'
  }
}

// Livrer : réservé au bénéficiaire du tour et au créateur (contrat), avec une confirmation qui dit ce qui va se passer.
const peutLivrer = computed(() => !!tour.value && (tour.value.beneficiaireId === moi.value?.id || createur.value))
const confirmerLivraison = ref(false)
const suivant = computed(() => {
  const n = tour.value?.numero
  const t2 = n ? cercle.value?.tours.find((x) => x.numero === n + 1) : null
  return t2 ? (ordre.value.find((m) => m.utilisateurId === t2.beneficiaireId) ?? null) : null
})
const dernierLivre = ref<string | null>(null)
async function livrer() {
  const nom = beneficiaire.value?.nom.split(' ')[0] ?? ''
  await agir('livrer', () => livrerTour(cercle.value!.id, tour.value!.numero))
  if (!erreur.value) dernierLivre.value = nom
}
const livres = computed(() => cercle.value?.tours.filter((x) => x.statut === 'livre') ?? [])

const idee = ref({ titre: '', description: '' })
const ideeOuverte = ref(false)
function ouvrirIdee() {
  idee.value = { titre: monIdee.value?.titre ?? '', description: monIdee.value?.description ?? '' }
  ideeOuverte.value = true
}
const ideeValide = computed(() => idee.value.titre.trim().length >= LIMITES.ideeTitreMin && idee.value.description.trim().length >= LIMITES.ideeDescriptionMin)
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
            <span v-for="(p, i) in placesLibres" :key="`libre-${i}`" class="anneau__libre" :style="{ left: `${p.x}%`, top: `${p.y}%` }" aria-hidden="true" />
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
          <p v-if="dernierLivre" class="livre carte" role="status" data-test="livre">
            <strong>{{ t('cercles.livreTitre', { nom: dernierLivre }) }}</strong>
            {{ t('cercles.livreTexte') }}
            <RouterLink to="/vitrine">{{ t('cercles.voirVitrine') }}</RouterLink>
          </p>
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
            <template v-if="peutLivrer">
              <JDeplier>
                <JBouton v-if="!confirmerLivraison" variante="secondaire" data-test="livrer" @click="confirmerLivraison = true">{{ t('cercles.livrer') }}</JBouton>
              </JDeplier>
              <JDeplier>
                <div v-if="confirmerLivraison" class="confirmation" data-test="confirmer-livraison">
                  <p>{{ suivant ? t('cercles.livrerConfirmation', { nom: beneficiaire.nom.split(' ')[0], suivant: suivant.nom.split(' ')[0] }) : t('cercles.livrerDernier', { nom: beneficiaire.nom.split(' ')[0] }) }}</p>
                  <div class="commandes">
                    <JBouton :occupe="action === 'livrer'" data-test="confirmer" @click="livrer">{{ t('cercles.livrerOui') }}</JBouton>
                    <JBouton variante="discret" @click="confirmerLivraison = false">{{ t('communautes.annuler') }}</JBouton>
                  </div>
                </div>
              </JDeplier>
              <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
            </template>
          </section>
          <section v-else-if="cercle.statut === 'termine'" class="carte tour" data-test="termine">
            <h2>{{ t('cercles.termine') }}</h2>
            <p class="texte-discret">{{ t('cercles.termineAide', { n: livres.length }) }}</p>
            <RouterLink to="/vitrine">{{ t('cercles.voirVitrine') }}</RouterLink>
          </section>
          <section v-else-if="!cercle.tirageFait && preparation" class="carte tour" aria-labelledby="avant-titre" data-test="attente-tirage">
            <h2 id="avant-titre">{{ t('cercles.avantTirage') }}</h2>
            <ul class="etapes-tirage">
              <li :class="{ fait: !preparation.manquants }">{{ t('cercles.etapeMembres', { n: cercle.membres.length, taille: cercle.taille }) }}</li>
              <li :class="{ fait: !preparation.sansIdee }">{{ t('cercles.etapeIdees', { n: cercle.membres.length - preparation.sansIdee, total: cercle.membres.length }) }}</li>
            </ul>
            <p class="texte-discret">{{ t('cercles.avantTirageAide', { n: preparation.manquants }, preparation.manquants) }}</p>

            <JBouton v-if="!membre && preparation.manquants" :occupe="action === 'rejoindre'" data-test="rejoindre" @click="agir('rejoindre', () => rejoindreCercle(cercle!.id))">{{ t('cercles.rejoindre') }}</JBouton>

            <template v-if="membre">
              <JDeplier>
                <div v-if="!ideeOuverte" class="mon-idee" data-test="mon-idee">
                  <p v-if="monIdee"><strong>{{ t('cercles.monIdee') }}</strong> {{ monIdee.titre }}</p>
                  <JBouton :variante="monIdee ? 'secondaire' : 'principal'" data-test="ouvrir-idee" @click="ouvrirIdee">{{ monIdee ? t('cercles.modifierIdee') : t('cercles.deposerIdee') }}</JBouton>
                </div>
              </JDeplier>
              <JDeplier>
                <form v-if="ideeOuverte" class="formulaire-idee" data-test="form-idee" @submit.prevent="agir('idee', () => deposerIdee(cercle!.id, idee))">
                  <JChamp v-model="idee.titre" :libelle="t('cercles.ideeTitre')" :maxlength="LIMITES.ideeTitreMax" requis />
                  <JChamp v-model="idee.description" :libelle="t('cercles.ideeDescription')" :aide="t('cercles.ideeAide')" multiligne :lignes="4" :maxlength="LIMITES.ideeDescriptionMax" requis />
                  <div class="commandes">
                    <JBouton type="submit" :desactive="!ideeValide" :occupe="action === 'idee'">{{ t('cercles.enregistrerIdee') }}</JBouton>
                    <JBouton variante="secondaire" @click="ideeOuverte = false">{{ t('communautes.annuler') }}</JBouton>
                  </div>
                </form>
              </JDeplier>
            </template>

            <!-- Le tirage est fait par le serveur, une seule fois : ici on ne fait que le demander. -->
            <JBouton v-if="createur" :desactive="!preparation.pret" :occupe="action === 'tirage'" data-test="tirer" @click="agir('tirage', () => tirerAuSort(cercle!.id))">{{ t('cercles.tirer') }}</JBouton>
            <p v-if="createur" class="texte-discret">{{ t('cercles.tirageAide') }}</p>
            <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
          </section>

          <!-- Le Griot propose, le cercle décide : son message est présenté comme un résumé, pas comme une décision. -->
          <section v-if="cercle.messageGriot" class="griot zone-sombre" aria-labelledby="griot-titre" data-test="griot">
            <h2 id="griot-titre" class="griot__qui">{{ t('cercles.griot') }}</h2>
            <p class="griot__texte">{{ cercle.messageGriot.texte }}</p>
            <p class="griot__quand">{{ ilYa(cercle.messageGriot.le, locale) }}</p>
          </section>
        </div>
      </div>

      <section class="ordre" aria-labelledby="ordre-titre">
        <h2 id="ordre-titre">{{ cercle.tirageFait ? t('cercles.ordre') : t('cercles.membresTitre') }}</h2>
        <ol class="ordre__liste" data-test="ordre">
          <li v-for="m in ordre" :key="m.utilisateurId" class="carte ordre__ligne" :class="`ordre__ligne--${tourDe(m.utilisateurId)?.statut ?? 'a_venir'}`">
            <span v-if="cercle.tirageFait" class="ordre__numero">{{ m.ordre }}</span>
            <JAvatar :nom="m.nom" :url="m.avatarUrl" :taille="40" />
            <span class="ordre__qui">
              <strong>{{ m.nom }}</strong>
              <span>{{ m.idee?.titre ?? t('cercles.pasDIdee') }}</span>
            </span>
            <span class="ordre__confiance" :title="t('cercles.confianceAide')">{{ t('cercles.confiance', { n: m.confiance }) }}</span>
            <span v-if="cercle.tirageFait" class="ordre__statut">{{ t(`cercles.statutTour.${tourDe(m.utilisateurId)?.statut ?? 'a_venir'}`) }}</span>
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
/* Au tirage, chacun glisse jusqu'à sa place : on voit l'ordre se former. */
.anneau__membre { transition: left calc(var(--motion-celebrate) * 1.4) var(--ease-pop), top calc(var(--motion-celebrate) * 1.4) var(--ease-pop); }
.anneau__libre { position: absolute; translate: -50% -50%; width: 52px; height: 52px; border-radius: 50%; border: 2px dashed var(--line-strong); background: var(--surface); }
.etapes-tirage { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.etapes-tirage li { display: flex; align-items: center; gap: var(--space-2); }
.etapes-tirage li::before { content: ''; width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--line-strong); flex: none; }
.etapes-tirage li.fait::before { content: '✓'; display: grid; place-items: center; font-size: 0.75rem; line-height: 1; font-weight: 700; border-color: var(--action); background: var(--action); color: var(--on-action); }
.mon-idee, .formulaire-idee { display: grid; gap: var(--space-3); }
.mon-idee p { margin: 0; }
.commandes { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.confirmation { display: grid; gap: var(--space-3); padding: var(--space-4); border-radius: var(--radius-md); background: var(--proof-soft); }
.confirmation p { margin: 0; }
.livre { display: grid; gap: var(--space-1); margin: 0; padding: var(--space-4) var(--space-6); border-left: 4px solid var(--proof); animation: apparait var(--motion-celebrate) var(--ease-pop) both; }

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
