<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { DEVISES, envoyerDevis, formaterMontant, LIMITES_DEVIS, versMontant, type Mission } from './missions'

/*
 * Le développeur retenu envoie son devis (contracts/missions.yaml, `envoyerDevis`) : des jalons avec un titre, un
 * montant et un délai compté depuis le démarrage. Le total est la somme des jalons, calculée ici : l'API la revérifie
 * (409 `somme_jalons_invalide`), mais la personne n'a pas à faire l'addition. Un nouveau devis remplace le précédent.
 */
const props = defineProps<{ mission: Mission; remplace: boolean }>()
const emit = defineEmits<{ envoye: [] }>()
const { t, locale } = useI18n()
const id = useId()

type LigneJalon = { cle: number; titre: string; montant: string; delai: string }
let suivante = 0
const nouvelle = (): LigneJalon => ({ cle: suivante++, titre: '', montant: '', delai: '' })

const devise = ref<string>(props.mission.budget?.devise ?? 'XOF')
const jalons = ref<LigneJalon[]>([nouvelle()])
const retouches = ref('1')
const conditions = ref('')
const envoi = ref(false)
const code = ref<string | null>(null)

const entier = (texte: string) => (/^\d+$/.test(texte.trim()) ? Number(texte.trim()) : null)
const lus = computed(() =>
  jalons.value.map((j) => {
    const montant = versMontant(j.montant, devise.value)
    const delai = entier(j.delai)
    const titreOk = j.titre.trim().length >= LIMITES_DEVIS.titreMin
    const delaiOk = delai !== null && delai >= LIMITES_DEVIS.delaiMin && delai <= LIMITES_DEVIS.delaiMax
    return { titre: j.titre.trim(), montant, delai, valide: titreOk && montant !== null && montant.centimes > 0 && delaiOk }
  }),
)
const nombreRetouches = computed(() => entier(retouches.value))
const total = computed(() => lus.value.reduce((somme, j) => somme + (j.montant?.centimes ?? 0), 0))
const pret = computed(
  () => lus.value.every((j) => j.valide) && nombreRetouches.value !== null && nombreRetouches.value <= LIMITES_DEVIS.retouchesMax,
)
const erreur = computed(() => {
  if (!code.value) return ''
  const connus = ['somme_jalons_invalide', 'etat_invalide', 'validation', 'interdit']
  return t(`missions.devis.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

function ajouter() {
  if (jalons.value.length < LIMITES_DEVIS.jalonsMax) jalons.value = [...jalons.value, nouvelle()]
}
function enlever(cle: number) {
  jalons.value = jalons.value.filter((j) => j.cle !== cle)
}

async function envoyer() {
  if (!pret.value || envoi.value || nombreRetouches.value === null) return
  envoi.value = true
  code.value = null
  try {
    const r = await envoyerDevis(props.mission.id, {
      total: { centimes: total.value, devise: devise.value },
      retouchesComprises: nombreRetouches.value,
      conditions: conditions.value.trim() || null,
      jalons: lus.value.map((j) => ({ titre: j.titre, montant: j.montant!, delaiJours: j.delai! })),
    })
    if (r.ok) emit('envoye')
    else code.value = r.code ?? 'autre'
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <form class="carte devis" aria-labelledby="devis-titre" data-test="form-devis" @submit.prevent="envoyer">
    <h2 id="devis-titre">{{ remplace ? t('missions.devis.remplacer') : t('missions.devis.titre') }}</h2>
    <p class="texte-discret">{{ t('missions.devis.intro') }}</p>

    <div class="devise">
      <label :for="`${id}-devise`">{{ t('missions.nouvelle.devise') }}</label>
      <select :id="`${id}-devise`" v-model="devise">
        <option v-for="d in DEVISES" :key="d" :value="d">{{ d }}</option>
      </select>
    </div>

    <ol class="jalons">
      <li v-for="(j, i) in jalons" :key="j.cle" data-test="ligne-jalon">
        <fieldset>
          <legend>{{ t('missions.suivi.jalon', { n: i + 1 }) }}</legend>
          <JChamp v-model="j.titre" :libelle="t('missions.devis.jalonTitre')" :maxlength="LIMITES_DEVIS.titreMax" requis />
          <div class="ligne">
            <JChamp
              v-model="j.montant"
              :libelle="t('missions.devis.jalonMontant')"
              :erreur="j.montant && !lus[i]?.montant ? t('missions.proposition.proposer.prixInvalide') : undefined"
              requis
            />
            <JChamp v-model="j.delai" type="number" :libelle="t('missions.devis.jalonDelai')" :aide="t('missions.devis.aideDelai')" requis />
          </div>
          <JBouton v-if="jalons.length > 1" variante="discret" data-test="enlever-jalon" @click="enlever(j.cle)">{{ t('missions.devis.enlever') }}</JBouton>
        </fieldset>
      </li>
    </ol>
    <div>
      <JBouton v-if="jalons.length < LIMITES_DEVIS.jalonsMax" variante="secondaire" data-test="ajouter-jalon" @click="ajouter">{{ t('missions.devis.ajouter') }}</JBouton>
    </div>

    <p class="total" data-test="total" aria-live="polite">{{ t('missions.devis.total', { montant: formaterMontant({ centimes: total, devise }, locale) }) }}</p>

    <JChamp v-model="retouches" type="number" :libelle="t('missions.devis.retouches')" :aide="t('missions.devis.aideRetouches')" requis />
    <JChamp v-model="conditions" :libelle="t('missions.devis.conditions')" :aide="t('missions.devis.aideConditions')" multiligne :lignes="3" :maxlength="LIMITES_DEVIS.conditionsMax" />

    <div>
      <JBouton type="submit" :occupe="envoi" :desactive="!pret" data-test="envoyer-devis">{{ t('missions.devis.envoyer') }}</JBouton>
    </div>
    <p v-if="erreur" role="alert" class="erreur" data-test="erreur-devis">{{ erreur }}</p>
  </form>
</template>

<style scoped>
.devis { display: grid; gap: var(--space-4); padding: var(--space-8); }
.devis h2 { margin: 0; font-size: 1.25rem; }
.texte-discret { margin: 0; }
.devise { display: grid; gap: var(--space-1); max-width: 12rem; }
.devise label { font-size: var(--type-label-size); line-height: var(--type-label-line); font-weight: var(--type-label-weight); }
.devise select { min-height: 44px; }
.jalons { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-4); }
fieldset { margin: 0; display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
legend { font-weight: 600; padding: 0 var(--space-2); }
.ligne { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); align-items: start; }
.total { margin: 0; font-weight: 700; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
