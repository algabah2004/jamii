<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JSquelette } from '../../design-system'
import { confirmerResultats, LIMITES_RESULTATS, listerRendus, suivreNotation, type Hackathon, type Rendu } from './hackathons'

/*
 * Confirmer les gagnants, côté organisateur (contracts/hackathons.yaml, `confirmerResultats`). Le n-ième prix revient,
 * dans la proposition, à l'équipe classée n-ième par le jury. L'organisateur peut choisir une autre équipe, mais doit
 * alors dire pourquoi : la raison est gardée avec les résultats. Une fois publiés, les notes sont verrouillées.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const emit = defineEmits<{ publie: [] }>()
const { t } = useI18n()

const rendus = ref<Rendu[]>([])
const proposes = ref<string[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const choix = ref<{ equipeId: string; raison: string }[]>([])
const envoi = ref(false)
const code = ref<string | null>(null)

const prix = computed(() => props.hackathon.prix.filter((p): p is typeof p & { id: string } => !!p.id))
const ecart = (i: number) => !!choix.value[i]?.equipeId && choix.value[i]!.equipeId !== proposes.value[i]
const complet = computed(
  () => prix.value.length > 0 && choix.value.every((c, i) => c.equipeId && (!ecart(i) || c.raison.trim().length > 0)),
)
const message = (c: string) =>
  t(`hackathons.confirmation.erreurs.${['etat_invalide', 'modification_concurrente', 'validation'].includes(c) ? c : 'autre'}`)

async function charger() {
  try {
    const [notation, liste] = await Promise.all([suivreNotation(props.hackathon.id), listerRendus(props.hackathon.id)])
    rendus.value = liste
    proposes.value = [...notation.classement].sort((a, b) => a.rang - b.rang).map((l) => l.equipeId)
    choix.value = prix.value.map((_, i) => ({ equipeId: proposes.value[i] ?? '', raison: '' }))
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.hackathon.id, () => void charger(), { immediate: true })

async function publier() {
  if (!complet.value || envoi.value) return
  envoi.value = true
  code.value = null
  try {
    const gagnants = prix.value.map((p, i) => ({
      prixId: p.id,
      equipeId: choix.value[i]!.equipeId,
      raison: ecart(i) ? choix.value[i]!.raison.trim() : null,
    }))
    const r = await confirmerResultats(props.hackathon.id, props.hackathon.version, gagnants)
    if (r.ok) emit('publie')
    else code.value = r.code ?? 'autre'
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section class="carte confirmation" aria-labelledby="confirmation-titre" data-test="confirmation">
    <h2 id="confirmation-titre">{{ t('hackathons.confirmation.titre') }}</h2>
    <p class="texte-discret">{{ t('hackathons.confirmation.intro') }}</p>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.confirmation.indisponible') }}</p>
    <p v-else-if="!prix.length" class="texte-discret" data-test="sans-prix">{{ t('hackathons.confirmation.sansPrix') }}</p>
    <p v-else-if="!rendus.length" class="texte-discret" data-test="sans-rendu">{{ t('hackathons.confirmation.sansRendu') }}</p>
    <form v-else class="formulaire" data-test="form-resultats" @submit.prevent="publier">
      <fieldset v-for="(p, i) in prix" :key="p.id" data-test="prix-a-attribuer">
        <legend>{{ p.nom }}</legend>
        <label class="choix">
          <span>{{ t('hackathons.confirmation.gagnant') }}</span>
          <select v-model="choix[i]!.equipeId" required data-test="gagnant">
            <option value="" disabled>{{ t('hackathons.confirmation.choisir') }}</option>
            <option v-for="r in rendus" :key="r.equipeId" :value="r.equipeId">
              {{ r.titre }}{{ r.equipeId === proposes[i] ? ` (${t('hackathons.confirmation.propose')})` : '' }}
            </option>
          </select>
        </label>
        <JChamp
          v-if="ecart(i)"
          v-model="choix[i]!.raison"
          :libelle="t('hackathons.confirmation.raison')"
          :aide="t('hackathons.confirmation.aideRaison')"
          multiligne
          :lignes="2"
          :maxlength="LIMITES_RESULTATS.raisonMax"
          requis
          data-test="raison"
        />
      </fieldset>
      <p class="texte-discret">{{ t('hackathons.confirmation.verrou') }}</p>
      <div>
        <JBouton type="submit" :occupe="envoi" :desactive="!complet" data-test="publier-resultats">
          {{ t('hackathons.confirmation.publier') }}
        </JBouton>
      </div>
      <p v-if="code" role="alert" class="erreur" data-test="erreur-resultats">{{ message(code) }}</p>
    </form>
  </section>
</template>

<style scoped>
.confirmation, .formulaire { display: grid; gap: var(--space-4); }
.confirmation { padding: clamp(var(--space-4), 6vw, var(--space-8)); }
.confirmation h2 { margin: 0; font-size: 1.25rem; }
.texte-discret { margin: 0; }
fieldset { margin: 0; padding: 0; border: 0; display: grid; gap: var(--space-3); }
legend { font-weight: 600; margin-bottom: var(--space-1); }
.choix { display: grid; gap: var(--space-1); font-weight: 600; }
.choix select { min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; font-weight: 400; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
