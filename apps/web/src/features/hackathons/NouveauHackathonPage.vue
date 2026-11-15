<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JBouton, JChamp } from '../../design-system'
import {
  creerHackathon,
  DEVISES,
  LIEUX,
  LIMITES,
  lireCalendrier,
  lireQuestions,
  MODES,
  sommePoids,
  versMontant,
  type NouveauHackathon,
} from './hackathons'
import EditeurQuestionnaire from '../questionnaires/EditeurQuestionnaire.vue'
import { nettoyer, problemes, type Question } from '../questionnaires/questionnaire'

/*
 * Créer un hackathon : on enregistre d'abord un brouillon (personne ne le voit), puis on le publie depuis la page du
 * hackathon. Chaque champ en erreur est signalé en même temps, avec son code venu de l'API.
 * DESIGN PROVISOIRE : l'écran montre le parcours avec les composants actuels ; son design sera repris avec le design system.
 */
const { t } = useI18n()
const router = useRouter()

const titre = ref('')
const theme = ref('')
const description = ref('')
const lieu = ref<(typeof LIEUX)[number]>('en_ligne')
const ville = ref('')
const dates = ref({ ouvertes: '', fermees: '', debut: '', fin: '', resultats: '' })
const min = ref(2)
const max = ref(5)
const mode = ref<(typeof MODES)[number]>('ouvert')
const deploiement = ref(false)
const code = ref('')
const questions = ref('')
const questionnaire = ref<Question[]>([])
const criteres = ref([{ nom: '', poids: 50 }, { nom: '', poids: 50 }])
const prix = ref<{ nom: string; montant: string; devise: string; sponsor: string }[]>([])
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const champs = ref<Record<string, string>>({})

const calendrier = computed(() => lireCalendrier(dates.value))
const criteresValides = computed(
  () => criteres.value.length > 0 && criteres.value.every((c) => c.nom.trim().length >= 2 && Number.isInteger(c.poids) && c.poids >= 1 && c.poids <= 100),
)
const valide = computed(
  () =>
    titre.value.trim().length >= LIMITES.titreMin &&
    theme.value.trim().length >= LIMITES.themeMin &&
    description.value.trim().length >= LIMITES.descriptionMin &&
    calendrier.value !== null &&
    min.value >= 1 && max.value <= 10 && min.value <= max.value &&
    criteresValides.value &&
    !Object.keys(problemes(questionnaire.value)).length,
)

/** Le message d'un champ en erreur ; une erreur sur un élément (`criteres[0].nom`) est montrée sur toute sa liste. */
const GROUPES = ['titre', 'theme', 'description', 'calendrier', 'tailleEquipe', 'criteres', 'prix']
function erreur(champ: string): string | undefined {
  const touche = Object.keys(champs.value).some((k) => k === champ || k.startsWith(`${champ}[`) || k.startsWith(`${champ}.`))
  return touche ? t(`hackathons.nouveau.invalide.${GROUPES.includes(champ) ? champ : 'autre'}`) : undefined
}

const ajouterCritere = () => criteres.value.length < LIMITES.criteresMax && criteres.value.push({ nom: '', poids: 10 })
const retirerCritere = (i: number) => criteres.value.length > 1 && criteres.value.splice(i, 1)
const ajouterPrix = () => prix.value.length < LIMITES.prixMax && prix.value.push({ nom: '', montant: '', devise: 'XOF', sponsor: '' })
const retirerPrix = (i: number) => prix.value.splice(i, 1)

async function creer() {
  if (!valide.value || !calendrier.value || envoi.value === 'envoi') return
  champs.value = {}
  const lesPrix: NonNullable<NouveauHackathon['prix']> = []
  for (const p of prix.value.filter((p) => p.nom.trim())) {
    const montant = p.montant.trim() ? versMontant(p.montant, p.devise) : null
    if (p.montant.trim() && !montant) return void (champs.value = { prix: 'montant_invalide' })
    lesPrix.push({ nom: p.nom.trim(), montant, sponsor: p.sponsor.trim() || null })
  }
  const questionsLues = lireQuestions(questions.value)
  const hackathon: NouveauHackathon = {
    titre: titre.value.trim(),
    theme: theme.value.trim(),
    description: description.value.trim(),
    lieu: lieu.value,
    calendrier: calendrier.value,
    tailleEquipe: { min: min.value, max: max.value },
    modeInscription: mode.value,
    deploiementObligatoire: deploiement.value,
    criteres: criteres.value.map((c) => ({ nom: c.nom.trim(), poids: c.poids })),
    ...(ville.value.trim() ? { ville: ville.value.trim() } : {}),
    ...(code.value.trim() ? { codeDeConduite: code.value.trim() } : {}),
    ...(questionsLues.length ? { questions: questionsLues } : {}),
    ...(questionnaire.value.length ? { questionnaire: nettoyer(questionnaire.value) } : {}),
    ...(lesPrix.length ? { prix: lesPrix } : {}),
  }
  envoi.value = 'envoi'
  try {
    const r = await creerHackathon(hackathon)
    if (r.ok) return void (await router.push(`/hackathons/${r.valeur.id}`))
    champs.value = r.champs
    envoi.value = Object.keys(r.champs).length ? 'repos' : 'erreur'
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section class="nouveau">
    <p class="retour"><RouterLink to="/hackathons">{{ t('hackathons.detail.retour') }}</RouterLink></p>
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('hackathons.nouveau.titre') }}</h1>
        <p>{{ t('hackathons.nouveau.intro') }}</p>
      </div>
    </header>

    <form class="carte formulaire" data-test="nouveau-hackathon" @submit.prevent="creer">
      <JChamp v-model="titre" :libelle="t('hackathons.nouveau.champTitre')" :erreur="erreur('titre')" :maxlength="LIMITES.titreMax" requis />
      <JChamp v-model="theme" :libelle="t('hackathons.nouveau.champTheme')" :erreur="erreur('theme')" :maxlength="LIMITES.themeMax" requis />
      <JChamp
        v-model="description"
        :libelle="t('hackathons.nouveau.champDescription')"
        :aide="t('hackathons.nouveau.aideDescription', { n: LIMITES.descriptionMin })"
        :erreur="erreur('description')"
        multiligne
        :lignes="6"
        :maxlength="LIMITES.descriptionMax"
        requis
      />

      <fieldset class="choix">
        <legend>{{ t('hackathons.nouveau.lieu') }}</legend>
        <label v-for="l in LIEUX" :key="l"><input v-model="lieu" type="radio" name="lieu" :value="l" :data-test="`lieu-${l}`" /> <span>{{ t(`hackathons.lieux.${l}`) }}</span></label>
      </fieldset>
      <JChamp v-model="ville" :libelle="t('hackathons.nouveau.ville')" :maxlength="80" />

      <fieldset class="dates">
        <legend>{{ t('hackathons.nouveau.dates') }}</legend>
        <p class="texte-discret">{{ t('hackathons.nouveau.datesAide') }}</p>
        <label v-for="cle in (['ouvertes', 'fermees', 'debut', 'fin', 'resultats'] as const)" :key="cle" class="date">
          <span>{{ t(`hackathons.calendrier.${cle}`) }}</span>
          <input v-model="dates[cle]" type="datetime-local" :data-test="`date-${cle}`" />
        </label>
        <p v-if="erreur('calendrier')" role="alert" class="erreur">{{ erreur('calendrier') }}</p>
      </fieldset>

      <fieldset class="taille">
        <legend>{{ t('hackathons.nouveau.equipes') }}</legend>
        <label class="date"><span>{{ t('hackathons.nouveau.min') }}</span><input v-model.number="min" type="number" min="1" max="10" data-test="min" /></label>
        <label class="date"><span>{{ t('hackathons.nouveau.max') }}</span><input v-model.number="max" type="number" min="1" max="10" data-test="max" /></label>
        <p v-if="erreur('tailleEquipe')" role="alert" class="erreur">{{ erreur('tailleEquipe') }}</p>
      </fieldset>

      <fieldset class="choix">
        <legend>{{ t('hackathons.nouveau.mode') }}</legend>
        <label v-for="m in MODES" :key="m"><input v-model="mode" type="radio" name="mode" :value="m" :data-test="`mode-${m}`" /> <span>{{ t(`hackathons.nouveau.${m}`) }}</span></label>
      </fieldset>
      <div class="questionnaire">
        <EditeurQuestionnaire v-model="questionnaire" contexte="hackathon" />
        <p v-if="erreur('questionnaire')" role="alert" class="erreur">{{ erreur('questionnaire') }}</p>
      </div>
      <label class="case"><input v-model="deploiement" type="checkbox" data-test="deploiement" /> <span>{{ t('hackathons.nouveau.deploiement') }}</span></label>

      <JChamp v-model="code" :libelle="t('hackathons.nouveau.code')" :aide="t('hackathons.nouveau.aideCode')" multiligne :lignes="4" :maxlength="LIMITES.codeMax" />
      <JChamp v-model="questions" :libelle="t('hackathons.nouveau.questions')" :aide="t('hackathons.nouveau.aideQuestions')" multiligne :lignes="3" />

      <fieldset class="liste" data-test="criteres">
        <legend>{{ t('hackathons.nouveau.criteres') }}</legend>
        <p class="texte-discret">{{ t('hackathons.nouveau.criteresAide') }} ({{ sommePoids(criteres) }})</p>
        <div v-for="(c, i) in criteres" :key="i" class="ligne">
          <JChamp v-model="c.nom" :libelle="t('hackathons.nouveau.critereNomChamp', { n: i + 1 })" :maxlength="60" />
          <label class="poids"><span>{{ t('hackathons.nouveau.criterePoids') }}</span><input v-model.number="c.poids" type="number" min="1" max="100" /></label>
          <button type="button" class="lien" :disabled="criteres.length < 2" @click="retirerCritere(i)">{{ t('hackathons.nouveau.retirer') }}</button>
        </div>
        <p v-if="erreur('criteres')" role="alert" class="erreur">{{ erreur('criteres') }}</p>
        <button type="button" class="lien" data-test="ajouter-critere" @click="ajouterCritere">{{ t('hackathons.nouveau.ajouterCritere') }}</button>
      </fieldset>

      <fieldset class="liste" data-test="prix">
        <legend>{{ t('hackathons.nouveau.prix') }}</legend>
        <div v-for="(p, i) in prix" :key="i" class="ligne ligne--prix">
          <JChamp v-model="p.nom" :libelle="t('hackathons.nouveau.prixNom', { n: i + 1 })" :maxlength="80" />
          <JChamp v-model="p.montant" :libelle="t('hackathons.nouveau.prixMontant')" />
          <label class="poids"><span>{{ t('hackathons.nouveau.devise') }}</span><select v-model="p.devise"><option v-for="d in DEVISES" :key="d" :value="d">{{ d }}</option></select></label>
          <JChamp v-model="p.sponsor" :libelle="t('hackathons.nouveau.prixSponsor')" :maxlength="80" />
          <button type="button" class="lien" @click="retirerPrix(i)">{{ t('hackathons.nouveau.retirer') }}</button>
        </div>
        <p v-if="erreur('prix')" role="alert" class="erreur">{{ erreur('prix') }}</p>
        <button type="button" class="lien" data-test="ajouter-prix" @click="ajouterPrix">{{ t('hackathons.nouveau.ajouterPrix') }}</button>
      </fieldset>

      <p v-if="envoi === 'erreur'" role="alert" class="erreur" data-test="erreur">{{ t('hackathons.nouveau.erreur') }}</p>
      <div>
        <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="enregistrer">{{ t('hackathons.nouveau.enregistrer') }}</JBouton>
      </div>
    </form>
  </section>
</template>

<style scoped>
.nouveau { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.formulaire { display: grid; gap: var(--space-6); padding: var(--space-8); max-width: 48rem; }
fieldset { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.choix label, .case { display: flex; gap: var(--space-3); align-items: center; }
.date, .poids { display: grid; gap: var(--space-2); font-weight: 600; max-width: 18rem; }
.taille { grid-template-columns: repeat(2, minmax(0, 10rem)); }
.date input, .poids input, .poids select { min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: inherit; font: inherit; }
.ligne { display: grid; gap: var(--space-3); grid-template-columns: minmax(0, 1fr) 8rem auto; align-items: end; }
.ligne--prix { grid-template-columns: repeat(2, minmax(0, 1fr)); }
@media (max-width: 640px) { .ligne { grid-template-columns: 1fr; } .taille { grid-template-columns: 1fr; } }
.lien { justify-self: start; background: none; border: 0; padding: var(--space-2) 0; color: var(--ink); font: inherit; font-weight: 600; text-decoration: underline; cursor: pointer; }
.lien:disabled { opacity: 0.5; cursor: default; }
.lien:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.questionnaire { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); border: 1px solid var(--line-strong); }
</style>
