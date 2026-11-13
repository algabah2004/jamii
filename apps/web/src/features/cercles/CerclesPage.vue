<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { lireCerclesRecommandes, type CercleRecommande } from '../reseau/reseau'
import { avantTirage, lireAvancement, lireCercle, lireCercles, pourcentage, titreDuTour, tourEnCours, type Avancement, type Cercle, type CercleResume } from './cercles'

/*
 * Les cercles : à droite de l'en-tête, celui qui demande mon attention maintenant (le tour en cours, sinon mon cercle
 * qui cherche encore des membres) ; puis ceux qui ont besoin de moi, les miens avec leurs visages, et les ouverts.
 */
const { t, locale } = useI18n()
const mes = ref<CercleResume[]>([])
const ouverts = ref<CercleResume[]>([])
const pourMoi = ref<CercleRecommande[]>([])
/** Le détail de mes cercles (peu nombreux) : les visages des membres et le tour en cours. */
const details = ref<Record<string, Cercle>>({})
const suivi = ref<Avancement | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

const MAX_DETAILS = 6

async function charger() {
  etat.value = 'chargement'
  try {
    // Les recommandations aident sans rien bloquer : si elles ne répondent pas, la page s'affiche sans elles.
    const [a, b, r] = await Promise.all([lireCercles('mes'), lireCercles('ouverts'), lireCerclesRecommandes().catch(() => [])])
    mes.value = a
    pourMoi.value = r
    // Un cercle n'apparaît qu'une fois : dans « mes cercles », sinon dans les recommandations, sinon dans les ouverts.
    ouverts.value = b.filter((c) => !a.some((x) => x.id === c.id) && !r.some((x) => x.cercle.id === c.id))
    etat.value = 'pret'
    void chargerDetails(a)
  } catch {
    etat.value = 'indisponible'
  }
}

async function chargerDetails(liste: CercleResume[]) {
  const lus = await Promise.all(liste.slice(0, MAX_DETAILS).map((c) => lireCercle(c.id).catch(() => null)))
  details.value = Object.fromEntries(lus.filter((c): c is Cercle => !!c).map((c) => [c.id, c]))
  const tour = vedette.value?.tour
  if (tour?.projetId) suivi.value = await lireAvancement(tour.projetId)
}
void charger()

const liste = (mots: readonly string[]) => new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(mots)
const prenom = (nom: string) => nom.split(' ')[0] ?? nom

/** Le cercle mis en avant : un tour en cours d'abord, sinon un de mes cercles qui cherche encore des membres. */
const vedette = computed(() => {
  const cercles = mes.value.map((c) => details.value[c.id]).filter((c): c is Cercle => !!c)
  const enCours = cercles.find((c) => c.statut === 'en_cours' && tourEnCours(c))
  if (enCours) return { cercle: enCours, tour: tourEnCours(enCours)!, manquants: 0 }
  const ouvert = cercles.find((c) => c.statut === 'ouvert' && avantTirage(c).manquants > 0)
  return ouvert ? { cercle: ouvert, tour: null, manquants: avantTirage(ouvert).manquants } : null
})

/** Ce qu'une carte de mes cercles dit de son état, en une ligne. */
function etape(c: CercleResume): string {
  const d = details.value[c.id]
  if (c.statut === 'en_cours' && d) {
    const tour = tourEnCours(d)
    if (tour) return t('cercles.tourDe', { n: tour.numero, nom: prenom(tour.beneficiaire.nom) })
  }
  if (c.statut === 'ouvert') {
    const manquants = c.taille - c.nombreMembres
    return t('cercles.avantTirageAide', { n: manquants }, manquants)
  }
  return t(`cercles.statut.${c.statut}`)
}
</script>

<template>
  <section class="cercles">
    <header class="tete" :class="{ 'tete--double': vedette }">
      <div class="tete__texte">
        <h1>{{ t('cercles.titre') }}</h1>
        <p>{{ t('cercles.intro') }}</p>
        <RouterLink to="/cercles/nouveau" class="j-bouton j-bouton--principal creer" data-test="nouveau">{{ t('cercles.nouveau.titre') }}</RouterLink>
      </div>

      <!-- Le cercle qui demande mon attention maintenant. -->
      <RouterLink v-if="vedette" :to="`/cercles/${vedette.cercle.id}`" class="vedette" data-test="vedette">
        <span class="vedette__surtitre">
          {{ vedette.tour ? t('cercles.tourDe', { n: vedette.tour.numero, nom: prenom(vedette.tour.beneficiaire.nom) }) : vedette.cercle.nom }}
        </span>
        <strong class="vedette__titre">
          {{ vedette.tour ? (titreDuTour(vedette.cercle, vedette.tour) ?? t('cercles.projetDe', { nom: prenom(vedette.tour.beneficiaire.nom) })) : t('cercles.avantTirageAide', { n: vedette.manquants }, vedette.manquants) }}
        </strong>
        <span class="visages" aria-hidden="true">
          <span v-for="m in vedette.cercle.membres" :key="m.personne.id" class="visages__un" :class="{ 'visages__un--tour': m.personne.id === vedette.tour?.beneficiaire.id }">
            <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="44" />
          </span>
          <i v-for="n in vedette.cercle.taille - vedette.cercle.membres.length" :key="`libre-${n}`" class="visages__libre" />
        </span>
        <span v-if="vedette.tour" class="vedette__cercle">{{ vedette.cercle.nom }} · {{ vedette.cercle.ville }}</span>
        <span v-if="vedette.tour && suivi" class="vedette__suivi">
          <span class="barre" role="progressbar" :aria-valuenow="pourcentage(suivi)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')">
            <i :style="{ width: `${pourcentage(suivi)}%` }" />
          </span>
          <span>{{ t('cercles.taches', { faites: suivi.faites, total: suivi.total }) }} · {{ t('cercles.joursRestants', { n: suivi.joursRestants }, suivi.joursRestants) }}</span>
        </span>
        <span class="vedette__action">{{ vedette.tour ? t('cercles.ouvrir') : t('cercles.trouverMembres') }}</span>
      </RouterLink>
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else>
      <section v-if="pourMoi.length" class="groupe" aria-labelledby="groupe-pour-moi">
        <div class="groupe__tete">
          <h2 id="groupe-pour-moi">{{ t('reseau.pourToi.titre') }}</h2>
          <p class="texte-discret">{{ t('reseau.pourToi.aide') }}</p>
        </div>
        <ul class="grille" data-test="cercles-pour-moi">
          <li v-for="r in pourMoi" :key="r.cercle.id">
            <RouterLink :to="`/cercles/${r.cercle.id}`" class="carte carte-cliquable resume resume--pour-moi">
              <span class="resume__haut">
                <strong>{{ r.cercle.nom }}</strong>
                <span class="pastille-statut">{{ t('cercles.membres', { n: r.cercle.nombreMembres, taille: r.cercle.taille }) }}</span>
              </span>
              <span class="texte-discret">{{ r.cercle.ville }}</span>
              <span class="places" aria-hidden="true">
                <i v-for="n in r.cercle.taille" :key="n" :class="{ pleine: n <= r.cercle.nombreMembres }" />
              </span>
              <!-- Pourquoi ce cercle : dit en clair, à partir des codes de l'API. -->
              <span class="resume__raisons">
                <span v-for="raison in r.raisons" :key="raison" class="resume__raison">{{ t(`reseau.raisonsCercle.${raison}`, { competences: liste(r.competences) }) }}</span>
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section class="groupe" aria-labelledby="groupe-mes">
        <div class="groupe__tete"><h2 id="groupe-mes">{{ t('cercles.groupes.mes') }}</h2></div>
        <p v-if="!mes.length" class="texte-discret" data-test="vide-mes">{{ t('cercles.vide.mes') }}</p>
        <ul v-else class="grille" data-test="cercles-mes">
          <li v-for="c in mes" :key="c.id">
            <RouterLink :to="`/cercles/${c.id}`" class="carte carte-cliquable resume" :class="`resume--${c.statut}`">
              <span class="resume__haut">
                <strong>{{ c.nom }}</strong>
                <span class="pastille-statut" :class="`pastille-statut--${c.statut}`">{{ t(`cercles.statut.${c.statut}`) }}</span>
              </span>
              <span class="texte-discret">{{ c.ville }} · {{ t('cercles.membres', { n: c.nombreMembres, taille: c.taille }) }}</span>
              <!-- Les visages des membres, et les places encore libres en pointillés. -->
              <span v-if="details[c.id]" class="visages visages--petit" aria-hidden="true">
                <span v-for="m in details[c.id]!.membres" :key="m.personne.id" class="visages__un">
                  <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="32" />
                </span>
                <i v-for="n in c.taille - details[c.id]!.membres.length" :key="`libre-${n}`" class="visages__libre" />
              </span>
              <span v-else class="places" aria-hidden="true">
                <i v-for="n in c.taille" :key="n" :class="{ pleine: n <= c.nombreMembres }" />
              </span>
              <span class="resume__etape">{{ etape(c) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section class="groupe" aria-labelledby="groupe-ouverts">
        <div class="groupe__tete"><h2 id="groupe-ouverts">{{ t('cercles.groupes.ouverts') }}</h2></div>
        <p v-if="!ouverts.length" class="texte-discret" data-test="vide-ouverts">{{ t('cercles.vide.ouverts') }}</p>
        <ul v-else class="grille" data-test="cercles-ouverts">
          <li v-for="c in ouverts" :key="c.id">
            <RouterLink :to="`/cercles/${c.id}`" class="carte carte-cliquable resume">
              <span class="resume__haut">
                <strong>{{ c.nom }}</strong>
                <span class="pastille-statut">{{ t('cercles.membres', { n: c.nombreMembres, taille: c.taille }) }}</span>
              </span>
              <span class="texte-discret">{{ c.ville }}</span>
              <span class="places" aria-hidden="true">
                <i v-for="n in c.taille" :key="n" :class="{ pleine: n <= c.nombreMembres }" />
              </span>
              <span class="resume__createur">
                <JAvatar :nom="c.createur.nom" :url="c.createur.avatarUrl" :taille="24" />
                {{ t('cercles.creePar', { nom: c.createur.nom }) }}
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.cercles { display: grid; gap: var(--space-12); }
.cercles ul { list-style: none; margin: 0; padding: 0; }

/* L'en-tête : le texte à gauche, le cercle du moment à droite sur grand écran. */
.tete { display: grid; gap: var(--space-8); align-items: center; padding-top: var(--space-4); }
.tete--double { grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); }
@media (max-width: 900px) { .tete--double { grid-template-columns: 1fr; } }
.tete__texte { display: grid; gap: var(--space-4); justify-items: start; }
.tete__texte h1 { margin: 0; font-size: clamp(2.5rem, 5vw, 3.75rem); line-height: 1.05; }
.tete__texte p { margin: 0; max-width: 34rem; color: var(--ink-muted); font-size: 1.125rem; }
.creer { margin-top: var(--space-2); }

/* La carte du moment : un fond teinté aux couleurs de la marque, le visage du bénéficiaire du tour cerclé d'orange. */
.vedette {
  --fond-vedette: color-mix(in srgb, var(--surface) 90%, var(--action));
  display: grid; gap: var(--space-3); padding: var(--space-8);
  border-radius: var(--radius-xl, 28px); text-decoration: none; color: var(--ink);
  background:
    radial-gradient(120% 90% at 100% 0%, color-mix(in srgb, var(--proof) 22%, transparent), transparent 60%),
    radial-gradient(90% 80% at 0% 100%, color-mix(in srgb, var(--action) 24%, transparent), transparent 65%),
    var(--fond-vedette);
  border: 1px solid color-mix(in srgb, var(--action) 30%, var(--line));
  box-shadow: 0 24px 60px -30px rgb(0 0 0 / 0.45);
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.vedette:hover { transform: translateY(-2px); border-color: color-mix(in srgb, var(--action) 60%, transparent); }
.vedette__surtitre { color: var(--ink-muted); font-weight: 600; }
.vedette__titre { font-family: var(--font-display); font-size: clamp(1.5rem, 2.6vw, 2.125rem); line-height: 1.15; }
.vedette__cercle { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.vedette__suivi { display: grid; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.vedette__action { justify-self: start; margin-top: var(--space-2); padding: var(--space-2) var(--space-5, 20px); border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-weight: 700; }

.barre { display: block; height: 10px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 12%, transparent); overflow: hidden; }
.barre i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--action), var(--proof)); }

/* Les visages qui se chevauchent, comme autour d'une même table ; les places libres en pointillés. */
.visages { display: flex; align-items: center; padding-left: 10px; }
.visages__un, .visages__libre { display: flex; margin-left: -10px; border-radius: 50%; box-shadow: 0 0 0 3px var(--surface); }
/* Sur le fond teinté de la carte, les initiales prennent la couleur de la surface pour rester visibles. */
.vedette .visages__un :deep(.j-avatar--initiales) { background: var(--surface); border: 1.5px solid var(--line-strong); }
.visages__libre { width: 44px; height: 44px; border: 2px dashed var(--line-strong); background: var(--surface); }
.visages--petit .visages__libre { width: 32px; height: 32px; }
.vedette .visages__un, .vedette .visages__libre { box-shadow: 0 0 0 3px var(--fond-vedette); }
.vedette .visages__un--tour { position: relative; z-index: 1; box-shadow: 0 0 0 3px var(--proof); }

.groupe { display: grid; gap: var(--space-4); }
.groupe__tete { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-1) var(--space-4); }
.groupe__tete h2, .groupe__tete p { margin: 0; }
.grille { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }

.resume { display: grid; gap: var(--space-2); align-content: start; padding: var(--space-6); color: var(--ink); text-decoration: none; height: 100%; }
.resume__haut { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-3); font-size: 1.125rem; }
.pastille-statut { flex: none; padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600; background: var(--action-soft); color: var(--action-ink); white-space: nowrap; }
.pastille-statut--en_cours { background: var(--proof); color: var(--on-proof); }
.places { display: flex; gap: 6px; margin-block: var(--space-1); }
.places i { width: 14px; height: 14px; border-radius: 50%; border: 2px dashed var(--line-strong); }
.places i.pleine { border: 0; background: var(--action); }
.resume__etape { margin-top: var(--space-1); font-weight: 600; }
.resume--pour-moi { border-color: color-mix(in srgb, var(--action) 45%, var(--line)); }
.resume__raisons { display: grid; gap: 2px; margin-top: var(--space-1); }
.resume__raison { color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; }
.resume__raison::before { content: '✓ '; }
.resume__createur { display: flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }

@media (prefers-reduced-motion: reduce) { .vedette, .vedette:hover { transition: none; transform: none; } }
</style>
