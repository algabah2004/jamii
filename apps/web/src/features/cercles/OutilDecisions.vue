<script setup lang="ts">
import EnTeteOutil from './EnTeteOutil.vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import { ilYa } from '../vitrine/vitrine'
import { RouterLink } from 'vue-router'
import IconeCercle from './IconeCercle.vue'
import { chefDeProjet, titreDuTour, type Cercle, type Tour } from './cercles'
import { prenom } from './salon'
import { envoyer, LIMITES_SALON, lireSalon, type MessageCercle } from './salon'

/*
 * Les décisions du cercle (le « message board » de Basecamp, réduit à l'essentiel) : ce que l'équipe a tranché, écrit
 * une fois, retrouvé sans fouiller le salon. « On part sur Flutter », « la démo est jeudi ».
 */
const props = defineProps<{ cercle: Cercle; moiId: string | null }>()
// Les décisions du tour sont consignées par son chef de projet (le membre dont c'est le tour) ; les autres les proposent dans le salon.
const chef = computed(() => chefDeProjet(props.cercle))
const jeSuisChef = computed(() => !!props.moiId && chef.value.id === props.moiId)
const { t, locale } = useI18n()

const decisions = ref<MessageCercle[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
onMounted(async () => {
  const r = await lireSalon(props.cercle.id).catch(() => null)
  if (!r?.ok) return void (etat.value = 'erreur')
  decisions.value = r.salon.decisions
  etat.value = 'pret'
})

const texte = ref('')
const envoi = ref(false)
const erreur = ref(false)
const pret = computed(() => !envoi.value && texte.value.trim().length >= 3)
// Le registre, rangé par tour : une décision appartient au premier tour livré après elle, sinon au tour en cours.
// Chaque décision garde son numéro d'ordre dans le cercle (n° 1 = la plus ancienne).
const tours = computed(() => [...props.cercle.tours].sort((a, b) => a.numero - b.numero))
function tourDe(iso: string): Tour | null {
  return tours.value.find((x) => x.livreLe && x.livreLe >= iso) ?? tours.value.find((x) => x.numero === props.cercle.tourEnCours) ?? null
}
const groupes = computed(() => {
  const total = decisions.value.length
  const parTour = new Map<number, { tour: Tour | null; liste: { d: MessageCercle; numero: number }[] }>()
  decisions.value.forEach((d, i) => {
    const tour = tourDe(d.envoyeLe)
    const cle = tour?.numero ?? 0
    if (!parTour.has(cle)) parTour.set(cle, { tour, liste: [] })
    parTour.get(cle)!.liste.push({ d, numero: total - i })
  })
  return [...parTour.values()]
})
function titreGroupe(tour: Tour | null) {
  if (!tour) return t('cercles.decisions.horsTour')
  const titre = titreDuTour(props.cercle, tour)
  return titre ? t('cercles.decisions.tourAvecTitre', { n: tour.numero, titre }) : t('cercles.decisions.tour', { n: tour.numero })
}
const date = (iso: string) => new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short' }).format(new Date(iso))

async function consigner() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = false
  const r = await envoyer(props.cercle.id, { type: 'decision', texte: texte.value.trim() }).catch(() => null)
  envoi.value = false
  if (!r?.ok) return void (erreur.value = true)
  decisions.value = [r.message, ...decisions.value]
  texte.value = ''
}
</script>

<template>
  <section class="outil-decisions" aria-labelledby="decisions-titre" data-test="outil-decisions-page">
    <EnTeteOutil id="decisions-titre" icone="decisions" :titre="t('cercles.qg.outils.decisions')" :aide="t('cercles.decisions.aide')" />

    <!-- Qui tient le registre, et comment y ajouter une décision. -->
    <div class="registre carte">
      <div class="registre__tenu">
        <JAvatar :nom="chef.nom" :url="chef.avatarUrl" :taille="44" />
        <div>
          <p class="registre__surtitre">{{ t('cercles.decisions.registreTenuPar') }}</p>
          <p class="registre__nom"><LienPersonne :id="chef.id">{{ chef.nom }}</LienPersonne> <span class="registre__role">{{ t('cercles.chef.titre') }}</span></p>
        </div>
        <p v-if="etat === 'pret'" class="registre__compte"><strong>{{ decisions.length }}</strong> {{ t('cercles.decisions.compte', { n: decisions.length }, decisions.length) }}</p>
      </div>

      <form v-if="jeSuisChef" class="nouvelle" data-test="nouvelle-decision" @submit.prevent="consigner">
        <label for="decision-texte" class="visuellement-cache">{{ t('cercles.qg.nouvelleDecision') }}</label>
        <textarea id="decision-texte" v-model="texte" rows="2" :maxlength="LIMITES_SALON.texte" :placeholder="t('cercles.qg.decisionExemple')" />
        <button type="submit" class="envoyer" :disabled="!pret">{{ t('cercles.qg.consigner') }}</button>
        <p v-if="erreur" role="alert" class="erreur">{{ t('cercles.salon.erreurs.envoi') }}</p>
      </form>
      <p v-else class="reserve" data-test="decisions-reservees">
        <span>{{ t('cercles.chef.proposerDecision', { nom: prenom(chef.nom) }) }}</span>
        <RouterLink :to="{ query: { outil: 'salon' } }" class="reserve__lien"><IconeCercle nom="salon" :taille="16" />{{ t('cercles.chef.allerAuSalon') }}</RouterLink>
      </p>
    </div>

    <p v-if="etat === 'chargement'" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'erreur'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>
    <div v-else-if="!decisions.length" class="vide carte">
      <span class="vide__icone"><IconeCercle nom="decisions" :taille="22" /></span>
      <p>{{ t('cercles.qg.aucuneDecision') }}</p>
    </div>

    <!-- Le registre : un fil par tour, chaque décision numérotée, la plus récente en haut. -->
    <section v-for="g in groupes" :key="g.tour?.numero ?? 0" class="groupe" :aria-label="titreGroupe(g.tour)">
      <h3 class="groupe__titre">{{ titreGroupe(g.tour) }}</h3>
      <ol class="fil">
        <li v-for="{ d, numero } in g.liste" :key="d.id" class="decision" data-test="decision">
          <span class="decision__numero" aria-hidden="true">{{ numero }}</span>
          <div class="decision__corps carte">
            <p class="decision__texte">{{ d.texte }}</p>
            <p class="decision__qui">
              <LienPersonne :id="d.auteur?.id" doublon><JAvatar :nom="d.auteur?.nom ?? '?'" :url="d.auteur?.avatarUrl" :taille="22" /></LienPersonne>
              <LienPersonne :id="d.auteur?.id">{{ d.auteur?.nom }}</LienPersonne>
              <span aria-hidden="true">·</span>
              <time :datetime="d.envoyeLe" :title="ilYa(d.envoyeLe, locale)">{{ date(d.envoyeLe) }}</time>
            </p>
          </div>
        </li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.outil-decisions { display: grid; gap: var(--space-6); }

.registre { display: grid; gap: var(--space-4); padding: var(--space-5) var(--space-6); }
.registre__tenu { display: flex; align-items: center; gap: var(--space-3); }
.registre__surtitre { margin: 0; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; }
.registre__nom { margin: 0; font-weight: 700; font-size: 1.0625rem; }
.registre__role { margin-inline-start: var(--space-1); color: var(--proof-ink); font-size: 0.75rem; }
.registre__compte { margin: 0 0 0 auto; color: var(--ink-muted); font-size: var(--type-body-sm-size); text-align: right; }
.registre__compte strong { display: block; color: var(--ink); font-size: 1.75rem; line-height: 1; }

.nouvelle { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: var(--space-3); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.nouvelle textarea { width: 100%; box-sizing: border-box; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); color: var(--ink); font: inherit; resize: vertical; }
.nouvelle textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.envoyer { min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; }
.envoyer:disabled { opacity: 0.5; cursor: not-allowed; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.reserve { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); margin: 0; padding-top: var(--space-4); border-top: 1px solid var(--line); color: var(--ink-muted); }
.reserve__lien { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 38px; padding: 0 var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-pill); color: var(--ink); font-weight: 600; text-decoration: none; white-space: nowrap; }
.reserve__lien:hover { background: var(--surface-raised); }
.reserve__lien:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.vide { display: grid; justify-items: center; gap: var(--space-2); padding: var(--space-8); color: var(--ink-muted); text-align: center; }
.vide p { margin: 0; max-width: 32rem; }
.vide__icone { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: var(--proof-soft); color: var(--proof-ink); }

.groupe { display: grid; gap: var(--space-3); }
.groupe__titre { margin: 0; color: var(--ink-muted); font-size: 0.8125rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; }
.fil { position: relative; list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
/* Le fil du registre : une ligne verticale qui relie les numéros. */
.fil::before { content: ''; position: absolute; left: 17px; top: 18px; bottom: 18px; width: 2px; background: var(--line); }
.decision { position: relative; display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: var(--space-4); align-items: start; }
.decision__numero { position: relative; display: grid; place-items: center; width: 36px; height: 36px; margin-top: var(--space-3); border-radius: 50%; background: var(--surface); border: 2px solid var(--proof); color: var(--proof-ink); font-size: 0.8125rem; font-weight: 800; }
.decision:first-child .decision__numero { background: var(--proof); color: var(--on-proof); }
.decision__corps { display: grid; gap: var(--space-2); padding: var(--space-4) var(--space-5); }
.decision__texte { margin: 0; font-size: 1.125rem; line-height: 1.4; font-weight: 600; white-space: pre-wrap; overflow-wrap: anywhere; }
.decision__qui { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.erreur { grid-column: 1 / -1; margin: 0; color: var(--danger); font-weight: 600; }

@media (max-width: 640px) {
  .registre { padding: var(--space-4); }
  .registre__tenu { flex-wrap: wrap; }
  .nouvelle { grid-template-columns: minmax(0, 1fr); }
  .fil::before { left: 13px; }
  .decision { grid-template-columns: 28px minmax(0, 1fr); gap: var(--space-3); }
  .decision__numero { width: 28px; height: 28px; }
}
</style>
