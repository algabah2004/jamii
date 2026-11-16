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
const recherche = ref('')
const filtre = ref<'tous' | 'mes' | 'ouverts' | 'recommandes'>('tous')
const ville = ref('')
const tri = ref<'recent' | 'places'>('recent')
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

const catalogue = computed(() => [...mes.value, ...pourMoi.value.map((r) => r.cercle), ...ouverts.value])
const villes = computed(() => [...new Set(catalogue.value.map((c) => c.ville).filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b, locale.value)))
const placesLibres = computed(() => catalogue.value.filter((c) => c.statut === 'ouvert').reduce((n, c) => n + Math.max(0, c.taille - c.nombreMembres), 0))
const normaliser = (texte: string) => texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase(locale.value)
function correspond(c: CercleResume) {
  const mots = normaliser(recherche.value.trim()).split(/\s+/)
  return mots.every((mot) => normaliser(`${c.nom} ${c.ville ?? ''} ${c.createur.nom}`).includes(mot)) && (!ville.value || c.ville === ville.value)
}
function trier<T extends CercleResume>(cercles: T[]): T[] {
  return [...cercles].sort((a, b) => tri.value === 'places'
    ? (b.taille - b.nombreMembres) - (a.taille - a.nombreMembres)
    : b.creeLe.localeCompare(a.creeLe))
}
const mesVisibles = computed(() => filtre.value === 'ouverts' || filtre.value === 'recommandes' ? [] : trier(mes.value.filter(correspond)))
const recommandesVisibles = computed(() => filtre.value === 'mes' ? [] : pourMoi.value.filter((r) => correspond(r.cercle)).sort((a, b) => tri.value === 'places' ? (b.cercle.taille - b.cercle.nombreMembres) - (a.cercle.taille - a.cercle.nombreMembres) : b.cercle.creeLe.localeCompare(a.cercle.creeLe)))
const ouvertsVisibles = computed(() => filtre.value === 'mes' || filtre.value === 'recommandes' ? [] : trier(ouverts.value.filter(correspond)))
const nombreResultats = computed(() => mesVisibles.value.length + recommandesVisibles.value.length + ouvertsVisibles.value.length)
const filtresActifs = computed(() => !!recherche.value || !!ville.value || filtre.value !== 'tous')
function reinitialiser() { recherche.value = ''; ville.value = ''; filtre.value = 'tous'; tri.value = 'recent' }
function explorer() { document.getElementById('recherche-cercles')?.focus() }
function afficherResultats() {
  const resultat = document.getElementById('resultats-cercles')
  resultat?.focus({ preventScroll: true })
  resultat?.scrollIntoView?.({ block: 'start' })
}
</script>

<template>
  <section class="cercles">
    <header class="tete">
      <div class="tete__texte">
        <span class="surtitre"><span class="point" /> {{ t('cercles.decouverte.signature') }}</span>
        <h1>{{ t('cercles.decouverte.titre') }}<br><em>{{ t('cercles.decouverte.titreAccent') }}</em></h1>
        <p>{{ t('cercles.intro') }}</p>
        <div class="tete__actions"><RouterLink to="/cercles/nouveau" class="j-bouton j-bouton--principal creer" data-test="nouveau"><span aria-hidden="true">＋</span> {{ t('cercles.nouveau.titre') }}</RouterLink><button type="button" class="j-bouton j-bouton--secondaire" @click="explorer">{{ t('cercles.decouverte.explorer') }} <span aria-hidden="true">↗</span></button></div>
        <div class="chiffres"><span><strong>{{ catalogue.length }}</strong>{{ t('cercles.decouverte.cercles') }}</span><span><strong>{{ placesLibres }}</strong>{{ t('cercles.decouverte.places') }}</span><span><strong>{{ villes.length }}</strong>{{ t('cercles.decouverte.villes') }}</span></div>
      </div>
      <RouterLink v-if="vedette" :to="`/cercles/${vedette.cercle.id}`" class="vedette" data-test="vedette">
        <span class="vedette__haut"><span class="surtitre">{{ t('cercles.decouverte.continuer') }}</span><span class="statut"><i />{{ t(`cercles.statut.${vedette.cercle.statut}`) }}</span></span>
        <span class="vedette__visuel" aria-hidden="true"><svg viewBox="0 0 320 160"><ellipse cx="160" cy="80" rx="125" ry="54" fill="none" stroke="currentColor" opacity=".2"/><ellipse cx="160" cy="80" rx="91" ry="37" fill="none" stroke="currentColor" stroke-dasharray="3 6" opacity=".3"/><path d="M60 113 120 42 214 37 275 97 198 131 60 113" fill="none" stroke="currentColor" opacity=".18"/><circle cx="160" cy="80" r="26" fill="var(--action-soft)" stroke="var(--action)"/><path d="m148 80 8 8 16-17" fill="none" stroke="var(--action-ink)" stroke-width="2" stroke-linecap="round"/><circle cx="60" cy="113" r="13" fill="var(--proof)"/><circle cx="120" cy="42" r="10" fill="var(--action)"/><circle cx="214" cy="37" r="14" fill="var(--surface)" stroke="var(--line-strong)"/><circle cx="275" cy="97" r="10" fill="var(--action)"/><circle cx="198" cy="131" r="8" fill="var(--proof)"/></svg></span>
        <span class="vedette__surtitre">{{ vedette.tour ? t('cercles.tourDe', { n: vedette.tour.numero, nom: prenom(vedette.tour.beneficiaire.nom) }) : vedette.cercle.nom }}</span>
        <strong class="vedette__titre">{{ vedette.tour ? (titreDuTour(vedette.cercle, vedette.tour) ?? t('cercles.projetDe', { nom: prenom(vedette.tour.beneficiaire.nom) })) : t('cercles.avantTirageAide', { n: vedette.manquants }, vedette.manquants) }}</strong>
        <span class="vedette__bas"><span class="visages" aria-hidden="true"><span v-for="m in vedette.cercle.membres" :key="m.personne.id" class="visages__un"><JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="30" /></span></span><span class="vedette__cercle">{{ vedette.cercle.nom }}</span><span class="fleche" aria-hidden="true">↗</span></span>
        <span v-if="vedette.tour && suivi" class="vedette__suivi"><span class="barre" role="progressbar" :aria-valuenow="pourcentage(suivi)" aria-valuemin="0" aria-valuemax="100" :aria-label="t('cercles.avancement')"><i :style="{ width: `${pourcentage(suivi)}%` }" /></span><span>{{ t('cercles.taches', { faites: suivi.faites, total: suivi.total }) }} <b>{{ pourcentage(suivi) }} %</b></span></span>
      </RouterLink>
      <div v-else class="comment"><span class="surtitre">{{ t('cercles.decouverte.comment') }}</span><h2>{{ t('cercles.decouverte.ensemble') }}</h2><ol><li>{{ t('cercles.decouverte.etape1') }}</li><li>{{ t('cercles.decouverte.etape2') }}</li><li>{{ t('cercles.decouverte.etape3') }}</li></ol></div>
    </header>

    <div class="exploration verre">
      <form class="recherche" role="search" @submit.prevent="afficherResultats">
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
        <input id="recherche-cercles" v-model="recherche" type="search" :placeholder="t('cercles.decouverte.rechercher')" :aria-label="t('cercles.decouverte.rechercher')" data-test="rechercher-cercles" />
        <button type="submit" class="j-bouton j-bouton--principal">{{ t('cercles.decouverte.chercher') }}</button>
      </form>
      <div class="exploration__bas"><div class="filtres" role="group" :aria-label="t('cercles.decouverte.filtres')"><button v-for="f in (['tous', 'mes', 'ouverts', 'recommandes'] as const)" :key="f" type="button" :aria-pressed="filtre === f" :data-test="`filtre-${f}`" @click="filtre = f">{{ t(`cercles.decouverte.filtre.${f}`) }}<span v-if="f === 'mes'">{{ mes.length }}</span></button></div><div class="selecteurs"><label><span class="visuellement-cache">{{ t('cercles.decouverte.ville') }}</span><select v-model="ville" data-test="ville-cercles"><option value="">{{ t('cercles.decouverte.toutesVilles') }}</option><option v-for="v in villes" :key="v" :value="v">{{ v }}</option></select></label><label><span class="visuellement-cache">{{ t('cercles.decouverte.trier') }}</span><select v-model="tri" data-test="tri-cercles"><option value="recent">{{ t('cercles.decouverte.recents') }}</option><option value="places">{{ t('cercles.decouverte.plusPlaces') }}</option></select></label></div></div>
    </div>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide"><p>{{ t('etat.indisponible') }}</p><JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton></div>
    <template v-else>
      <div id="resultats-cercles" class="resultats" tabindex="-1" aria-live="polite"><span>{{ t('cercles.decouverte.resultats', { n: nombreResultats }, nombreResultats) }}</span><button v-if="filtresActifs" type="button" @click="reinitialiser">{{ t('cercles.decouverte.effacer') }} ×</button><span v-else class="resultats__aide">{{ t('cercles.decouverte.aide') }}</span></div>
      <div v-if="!nombreResultats" class="etat-vide aucun" data-test="aucun-cercle"><h2>{{ t('cercles.decouverte.aucun') }}</h2><p>{{ t('cercles.decouverte.aucunAide') }}</p><JBouton variante="secondaire" @click="reinitialiser">{{ t('cercles.decouverte.effacer') }}</JBouton></div>
      <section v-if="recommandesVisibles.length" class="groupe" aria-labelledby="groupe-pour-moi"><div class="groupe__tete"><div><span class="surtitre">{{ t('cercles.decouverte.pourProfil') }}</span><h2 id="groupe-pour-moi">{{ t('reseau.pourToi.titre') }}</h2></div><span class="groupe__nombre">{{ recommandesVisibles.length }}</span></div>
        <ul class="grille" data-test="cercles-pour-moi"><li v-for="(r, i) in recommandesVisibles" :key="r.cercle.id"><RouterLink :to="`/cercles/${r.cercle.id}`" class="resume resume--pour-moi"><span class="resume__haut"><span class="embleme" :class="`embleme--${i % 3}`" aria-hidden="true">{{ r.cercle.nom.split(' ').map((m) => m[0]).slice(0, 2).join('') }}</span><span class="pastille-statut">{{ t('cercles.decouverte.placesRestantes', { n: r.cercle.taille - r.cercle.nombreMembres }, r.cercle.taille - r.cercle.nombreMembres) }}</span></span><strong class="resume__nom">{{ r.cercle.nom }}</strong><span class="resume__ville">{{ r.cercle.ville || t('cercles.decouverte.distance') }}</span><span v-if="r.cercle.profilsRecherches?.length" class="resume__profils" data-test="profils-recherches"><span class="visuellement-cache">{{ t('cercles.cherche') }}</span><span v-for="p in r.cercle.profilsRecherches" :key="p">{{ t(`cercles.profils.${p}`) }}</span></span><span class="resume__raisons"><span v-for="raison in r.raisons" :key="raison">✓ {{ t(`reseau.raisonsCercle.${raison}`, { competences: liste(r.competences) }) }}</span></span><span class="resume__bas"><span class="places" aria-hidden="true"><i v-for="n in r.cercle.taille" :key="n" :class="{ pleine: n <= r.cercle.nombreMembres }" /></span><span>{{ t('cercles.membres', { n: r.cercle.nombreMembres, taille: r.cercle.taille }) }}</span><span class="mini-fleche" aria-hidden="true">↗</span></span></RouterLink></li></ul>
      </section>
      <section v-if="mesVisibles.length || (!filtresActifs && !mes.length)" class="groupe" aria-labelledby="groupe-mes"><div class="groupe__tete"><div><span class="surtitre">{{ t('cercles.decouverte.tonEquipe') }}</span><h2 id="groupe-mes">{{ t('cercles.groupes.mes') }}</h2></div><span class="groupe__nombre">{{ mesVisibles.length }}</span></div><p v-if="!mes.length" class="texte-discret" data-test="vide-mes">{{ t('cercles.vide.mes') }}</p>
        <ul v-else class="grille" data-test="cercles-mes"><li v-for="c in mesVisibles" :key="c.id"><RouterLink :to="`/cercles/${c.id}`" class="resume resume--moi"><span class="resume__haut"><span class="embleme" aria-hidden="true">◎</span><span class="pastille-statut" :class="`pastille-statut--${c.statut}`">{{ t(`cercles.statut.${c.statut}`) }}</span></span><strong class="resume__nom">{{ c.nom }}</strong><span class="resume__ville">{{ c.ville || t('cercles.decouverte.distance') }}</span><span class="resume__etape">{{ etape(c) }}</span><span class="resume__bas"><span v-if="details[c.id]" class="visages" aria-hidden="true"><span v-for="m in details[c.id]!.membres" :key="m.personne.id" class="visages__un"><JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="28" /></span></span><span>{{ t('cercles.membres', { n: c.nombreMembres, taille: c.taille }) }}</span><span class="mini-fleche" aria-hidden="true">↗</span></span></RouterLink></li></ul>
      </section>
      <section v-if="ouvertsVisibles.length || (!filtresActifs && !ouverts.length)" class="groupe" aria-labelledby="groupe-ouverts"><div class="groupe__tete"><div><span class="surtitre">{{ t('cercles.decouverte.rencontrer') }}</span><h2 id="groupe-ouverts">{{ t('cercles.groupes.ouverts') }}</h2></div><span class="groupe__nombre">{{ ouvertsVisibles.length }}</span></div><p v-if="!ouverts.length" class="texte-discret" data-test="vide-ouverts">{{ t('cercles.vide.ouverts') }}</p>
        <ul v-else class="grille" data-test="cercles-ouverts"><li v-for="(c, i) in ouvertsVisibles" :key="c.id"><RouterLink :to="`/cercles/${c.id}`" class="resume"><span class="resume__haut"><span class="embleme" :class="`embleme--${i % 3}`" aria-hidden="true">{{ c.nom.split(' ').map((m) => m[0]).slice(0, 2).join('') }}</span><span class="pastille-statut">{{ t('cercles.decouverte.placesRestantes', { n: c.taille - c.nombreMembres }, c.taille - c.nombreMembres) }}</span></span><strong class="resume__nom">{{ c.nom }}</strong><span class="resume__ville">{{ c.ville || t('cercles.decouverte.distance') }}</span><span v-if="c.profilsRecherches?.length" class="resume__profils" data-test="profils-recherches"><span class="visuellement-cache">{{ t('cercles.cherche') }}</span><span v-for="p in c.profilsRecherches" :key="p">{{ t(`cercles.profils.${p}`) }}</span></span><span class="resume__createur"><JAvatar :nom="c.createur.nom" :url="c.createur.avatarUrl" :taille="24" />{{ t('cercles.creePar', { nom: c.createur.nom }) }}</span><span class="resume__bas"><span class="places" aria-hidden="true"><i v-for="n in c.taille" :key="n" :class="{ pleine: n <= c.nombreMembres }" /></span><span>{{ t('cercles.membres', { n: c.nombreMembres, taille: c.taille }) }}</span><span class="mini-fleche" aria-hidden="true">↗</span></span></RouterLink></li></ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.cercles { max-width: 1500px; margin-inline: auto; padding: 12px clamp(0px, 2vw, 32px) 40px; display: grid; gap: 32px; }
.cercles ul { list-style: none; padding: 0; margin: 0; }
.tete { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, .85fr); gap: clamp(24px, 5vw, 80px); align-items: center; }
.surtitre { color: var(--ink-muted); font-size: .8125rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.tete__texte > .surtitre { display: flex; align-items: center; gap: 8px; }
.point { width: 7px; height: 7px; border-radius: 50%; background: var(--proof); }
.tete h1 { font-size: clamp(2.5rem, 4.8vw, 4.75rem); letter-spacing: -.055em; line-height: 1.04; margin: 20px 0; }
.tete h1 em { color: var(--action-ink); font-style: normal; }
.tete__texte > p { max-width: 500px; margin: 0; font-size: .9375rem; line-height: 1.8; color: var(--ink-muted); }
.tete__actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 24px; }
.creer { --action: var(--proof); --on-action: var(--on-proof); }
.tete__actions .j-bouton { border-radius: 50px; font-size: .9375rem; gap: 8px; }
.chiffres { display: flex; gap: 28px; margin-top: 28px; }
.chiffres > span { display: grid; gap: 2px; color: var(--ink-muted); font-size: .8125rem; }
.chiffres > span:nth-child(2) strong { color: var(--proof-ink); }
.chiffres strong { font-size: 1.5rem; color: var(--ink); font-family: var(--font-display); }
.vedette, .comment { display: grid; gap: 12px; border-radius: 24px; border: 1px solid var(--line); padding: 24px 28px; background: radial-gradient(ellipse at 80% 0%, color-mix(in srgb, var(--action) 12%, transparent), transparent 70%), var(--surface); color: var(--ink); text-decoration: none; }
.vedette:hover { border-color: var(--action); }
.vedette__haut { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.statut { display: flex; gap: 6px; align-items: center; font-size: .8125rem; color: var(--action-ink); }
.statut i { width: 5px; height: 5px; background: currentColor; border-radius: 50%; }
.vedette__visuel { display: block; height: 115px; color: var(--action); }
.vedette__visuel svg { width: 100%; height: 100%; }
.vedette__surtitre { color: var(--ink-muted); font-size: .875rem; }
.vedette__titre { font-size: clamp(1.2rem, 2vw, 1.65rem); line-height: 1.2; font-family: var(--font-display); letter-spacing: -.025em; }
.vedette__bas { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 4px 0; }
.vedette__cercle { font-size: .8125rem; color: var(--ink-muted); }
.fleche, .mini-fleche { display: grid; place-items: center; border-radius: 50%; margin-left: auto; flex: none; }
.fleche { width: 36px; height: 36px; background: var(--proof); color: var(--on-proof); }
.vedette__suivi { display: grid; gap: 8px; }
.vedette__suivi > span:last-child { display: flex; justify-content: space-between; font-size: .8125rem; color: var(--ink-muted); }
.barre { height: 5px; border-radius: 6px; background: var(--surface-raised); overflow: hidden; }
.barre i { display: block; height: 100%; border-radius: inherit; background: var(--action); }
.comment h2 { font-size: 1.75rem; margin: 8px 0; }
.comment ol { display: grid; gap: 16px; padding-left: 22px; color: var(--ink-muted); }
.exploration { position: sticky; top: 80px; z-index: 4; padding: 16px; border-radius: 24px; }
.recherche { display: flex; gap: 12px; align-items: center; padding: 6px 6px 6px 16px; border-radius: 50px; background: var(--surface); border: 1px solid var(--line); }
.recherche svg { flex: none; color: var(--ink-muted); }
.recherche input { min-width: 0; width: 100%; flex: 1; border: 0; border-radius: 0; box-shadow: none; background: transparent; color: var(--ink); font: inherit; font-size: .875rem; padding: 8px 0; min-height: 32px; }
.recherche input:focus { outline: 0; }
.recherche:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
.recherche .j-bouton { border-radius: 50px; font-size: .9375rem; min-height: 44px; padding-inline: 24px; }
.exploration__bas { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding-top: 14px; }
.filtres { display: flex; flex-wrap: wrap; gap: 4px; }
.filtres button { min-height: 40px; padding: 8px 16px; border: 1px solid transparent; border-radius: 50px; background: transparent; color: var(--ink-muted); font: inherit; font-size: .875rem; cursor: pointer; }
.filtres button[aria-pressed='true'] { background: var(--proof-soft); color: var(--proof-ink); border-color: color-mix(in srgb, var(--proof) 35%, var(--line)); font-weight: 600; }
.filtres button span { margin-left: 8px; opacity: .7; }
.selecteurs { display: flex; gap: 8px; }
.selecteurs select { min-height: 40px; max-width: 220px; width: auto; border: 1px solid var(--line); border-radius: 50px; padding: 8px 28px 8px 12px; background-color: var(--surface); color: var(--ink-muted); font: inherit; font-size: .875rem; }
.resultats { scroll-margin-top: 290px; display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; margin-top: -12px; font-size: .875rem; color: var(--ink-muted); }
.resultats button { padding: 0; border: 0; background: transparent; color: var(--action-ink); font: inherit; cursor: pointer; }
.groupe { display: grid; gap: 16px; }
.groupe__tete { display: flex; align-items: center; gap: 12px; }
.groupe__tete h2 { margin: 5px 0 0; font-size: 1.375rem; letter-spacing: -.025em; }
.groupe__nombre { color: var(--ink-muted); border: 1px solid var(--line); border-radius: 8px; padding: 2px 8px; font-size: .8125rem; }
.grille { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr)); }
.resume { height: 100%; min-width: 0; display: flex; flex-direction: column; gap: 8px; padding: 24px; border-radius: 20px; border: 1px solid var(--line); background: var(--surface); color: var(--ink); text-decoration: none; transition: border-color 150ms ease, transform 150ms ease; }
.resume:hover { transform: translateY(-3px); border-color: var(--action); }
.resume__haut { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin-bottom: 10px; }
.embleme { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 14px; background: var(--action-soft); color: var(--action-ink); font-family: var(--font-display); font-size: 1.125rem; font-weight: 700; }
.embleme--1 { background: var(--proof-soft); color: var(--proof-ink); }
.embleme--2 { background: var(--surface-raised); color: var(--ink-muted); }
.pastille-statut { font-size: .8125rem; padding: 4px 8px; border-radius: 50px; background: var(--surface-raised); color: var(--ink-muted); white-space: nowrap; }
.pastille-statut--en_cours { background: var(--action-soft); color: var(--action-ink); }
.resume__nom { font-family: var(--font-display); font-size: 1.25rem; letter-spacing: -.025em; line-height: 1.25; }
.resume__ville { color: var(--ink-muted); font-size: .875rem; }
.resume__raisons { display: grid; gap: 5px; font-size: .8125rem; color: var(--action-ink); margin-block: 8px; }
.resume__etape { font-size: .875rem; margin-block: 8px; }
.resume__createur { display: flex; align-items: center; gap: 8px; font-size: .8125rem; color: var(--ink-muted); margin-block: 12px; }
.resume__bas { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding-top: 16px; border-top: 1px solid var(--line); margin-top: auto; font-size: .8125rem; color: var(--ink-muted); }
.places { display: flex; gap: 4px; }
.places i { width: 9px; height: 9px; border-radius: 50%; border: 1px dashed var(--line-strong); }
.places i.pleine { border: 0; background: var(--action); }
.mini-fleche { width: 28px; height: 28px; border: 1px solid var(--line); color: var(--ink); }
.visages { display: flex; padding-left: 7px; }
.visages__un { display: flex; border-radius: 50%; margin-left: -7px; box-shadow: 0 0 0 2px var(--surface); }
.aucun { padding: 50px 24px; }
@media (max-width: 960px) { .tete { gap: 24px; } .tete h1 { font-size: 2.8rem; } .exploration { top: 126px; } .chiffres { gap: 20px; } }
@media (max-width: 700px) { .tete { grid-template-columns: 1fr; } .tete h1 { font-size: 2.75rem; } .vedette__visuel { height: 85px; } .creer { --action: var(--proof); --on-action: var(--on-proof); }
.tete__actions .j-bouton { flex: 1; } .exploration { position: static; padding: 12px; border-radius: 20px; } .recherche { gap: 8px; padding-left: 10px; } .recherche .j-bouton { padding-inline: 14px; } .recherche input { font-size: .875rem; } .filtres { width: 100%; justify-content: space-between; gap: 0; } .filtres button { padding: 8px 10px; font-size: .8125rem; } .selecteurs { width: 100%; } .selecteurs > label { flex: 1; min-width: 0; } .selecteurs select { max-width: none; width: 100%; } .resultats__aide { display: none; } }
@media (prefers-reduced-motion: reduce) { .resume { transition: none; } .resume:hover { transform: none; } }
.resume__profils { display: flex; flex-wrap: wrap; gap: 6px; }
.resume__profils > span:not(.visuellement-cache) { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; }
</style>
