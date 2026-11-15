<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JBouton, JChamp } from '../../design-system'
import { creerMission, DEVISES, LIMITES, lireCompetences, versMontant, type NouvelleMission } from './missions'
import EditeurQuestionnaire from '../questionnaires/EditeurQuestionnaire.vue'
import { nettoyer, problemes, type Question } from '../questionnaires/questionnaire'

/*
 * Publier une mission : on enregistre d'abord un brouillon (personne ne le voit), puis on le publie depuis la page de la
 * mission. Chaque champ en erreur est signalé en même temps, avec son code venu de l'API.
 * DESIGN PROVISOIRE : l'écran montre le parcours avec les composants actuels ; son design sera repris avec le design system.
 */
const { t } = useI18n()
const router = useRouter()

const titre = ref('')
const besoin = ref('')
const competences = ref('')
const budget = ref('')
const devise = ref<string>('XOF')
const date = ref('')
const ville = ref('')
const visibilite = ref<'publique' | 'invitation'>('publique')
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const champs = ref<Record<string, string>>({})
const questionnaire = ref<Question[]>([])

const valide = computed(
  () =>
    titre.value.trim().length >= LIMITES.titreMin &&
    besoin.value.trim().length >= LIMITES.besoinMin &&
    lireCompetences(competences.value).length > 0 &&
    !Object.keys(problemes(questionnaire.value)).length,
)

/** Le message d'un champ en erreur : un texte par champ, quel que soit le code. */
const erreur = (champ: string) => (champs.value[champ] ? t(`missions.nouvelle.invalide.${champ in INVALIDES ? champ : 'autre'}`) : undefined)
const INVALIDES: Record<string, true> = { titre: true, besoin: true, competences: true, budget: true, dateSouhaitee: true, visibilite: true }

async function creer() {
  if (!valide.value || envoi.value === 'envoi') return
  champs.value = {}
  const montant = budget.value.trim() ? versMontant(budget.value, devise.value) : null
  if (budget.value.trim() && !montant) return void (champs.value = { budget: 'montant_invalide' })
  const mission: NouvelleMission = {
    titre: titre.value.trim(),
    besoin: besoin.value.trim(),
    competences: lireCompetences(competences.value),
    visibilite: visibilite.value,
    // Une mission est à distance par défaut (le contrat le dit) ; l'écran ne propose pas encore de choix.
    aDistance: true,
    ...(montant ? { budget: montant } : {}),
    ...(date.value ? { dateSouhaitee: date.value } : {}),
    ...(ville.value.trim() ? { ville: ville.value.trim() } : {}),
    ...(questionnaire.value.length ? { questionnaire: nettoyer(questionnaire.value) } : {}),
  }
  envoi.value = 'envoi'
  try {
    const r = await creerMission(mission)
    if (r.ok) return void (await router.push(`/missions/${r.valeur.id}`))
    champs.value = r.champs
    envoi.value = Object.keys(r.champs).length ? 'repos' : 'erreur'
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section class="nouvelle">
    <p class="retour"><RouterLink to="/missions">{{ t('missions.detail.retour') }}</RouterLink></p>
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('missions.nouvelle.titre') }}</h1>
        <p>{{ t('missions.nouvelle.intro') }}</p>
      </div>
    </header>

    <form class="carte formulaire" data-test="nouvelle-mission" @submit.prevent="creer">
      <JChamp v-model="titre" :libelle="t('missions.nouvelle.champTitre')" :aide="t('missions.nouvelle.aideTitre')" :erreur="erreur('titre')" :maxlength="LIMITES.titreMax" requis />
      <JChamp
        v-model="besoin"
        :libelle="t('missions.nouvelle.champBesoin')"
        :aide="t('missions.nouvelle.aideBesoin', { n: LIMITES.besoinMin })"
        :erreur="erreur('besoin')"
        multiligne
        :lignes="6"
        :maxlength="LIMITES.besoinMax"
        requis
      />
      <JChamp v-model="competences" :libelle="t('missions.nouvelle.champCompetences')" :aide="t('missions.nouvelle.aideCompetences')" :erreur="erreur('competences')" requis />

      <fieldset class="budget">
        <legend>{{ t('missions.nouvelle.budget') }}</legend>
        <JChamp v-model="budget" :libelle="t('missions.nouvelle.budget')" :aide="t('missions.nouvelle.aideBudget')" :erreur="erreur('budget')" />
        <label class="devise">
          <span>{{ t('missions.nouvelle.devise') }}</span>
          <select v-model="devise" data-test="devise">
            <option v-for="d in DEVISES" :key="d" :value="d">{{ d }}</option>
          </select>
        </label>
      </fieldset>

      <label class="date">
        <span>{{ t('missions.nouvelle.date') }}</span>
        <input v-model="date" type="date" data-test="date" />
        <span v-if="erreur('dateSouhaitee')" role="alert" class="erreur">{{ erreur('dateSouhaitee') }}</span>
      </label>
      <JChamp v-model="ville" :libelle="t('missions.nouvelle.ville')" :maxlength="LIMITES.villeMax" />

      <fieldset class="choix">
        <legend>{{ t('missions.nouvelle.visibilite') }}</legend>
        <label><input v-model="visibilite" type="radio" name="visibilite" value="publique" data-test="publique" /> <span>{{ t('missions.nouvelle.publique') }}</span></label>
        <label><input v-model="visibilite" type="radio" name="visibilite" value="invitation" data-test="invitation" /> <span>{{ t('missions.nouvelle.invitation') }}</span></label>
      </fieldset>

      <div class="questionnaire">
        <EditeurQuestionnaire v-model="questionnaire" contexte="mission" />
      </div>

      <p v-if="envoi === 'erreur'" role="alert" class="erreur" data-test="erreur">{{ t('missions.nouvelle.erreur') }}</p>
      <div>
        <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="creer">{{ t('missions.nouvelle.creer') }}</JBouton>
      </div>
    </form>
  </section>
</template>

<style scoped>
.nouvelle { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.formulaire { display: grid; gap: var(--space-6); padding: var(--space-8); max-width: 44rem; }
.budget, .choix { border: 0; margin: 0; padding: 0; display: grid; gap: var(--space-3); }
.budget legend, .choix legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.budget legend { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.devise, .date { display: grid; gap: var(--space-2); font-weight: 600; max-width: 14rem; }
.devise select, .date input { min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: inherit; font: inherit; }
.choix label { display: flex; gap: var(--space-3); align-items: center; font-weight: 400; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.questionnaire { padding: var(--space-4); border-radius: var(--radius-md); border: 1px solid var(--line-strong); }
</style>
