<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton } from '../../design-system'
import type { MonProfil } from './profils'

/*
 * La carte de compétences proposée par l'IA (contracts/profils.yaml, `competencesProposees`). Rien n'est public tant
 * que le développeur ne l'a pas gardé : il coche ce qui est juste, retire le reste, puis enregistre
 * (`competencesIaRetenues`). Chaque compétence montre son niveau et ses preuves : les dépôts publics qui la justifient.
 */
const props = defineProps<{ profil: MonProfil }>()
const emit = defineEmits<{ misAJour: [profil: MonProfil] }>()
const { t } = useI18n()

const proposees = computed(() => props.profil.competencesProposees ?? [])
const retenuesEnregistrees = computed(() => props.profil.competences.filter((c) => c.source === 'ia').map((c) => c.competence))
/** Jamais rien de gardé : tout est coché, mais rien n'est publié avant « Garder ». Sinon, ce qui a été gardé. */
const dejaDecide = computed(() => retenuesEnregistrees.value.length > 0)

const cochees = ref<string[]>([])
watch(
  () => [proposees.value, retenuesEnregistrees.value] as const,
  () => {
    cochees.value = dejaDecide.value ? [...retenuesEnregistrees.value] : proposees.value.map((c) => c.competence)
  },
  { immediate: true },
)

const modifie = computed(() => {
  const a = [...cochees.value].sort().join()
  return !dejaDecide.value || a !== [...retenuesEnregistrees.value].sort().join()
})

const envoi = ref<'repos' | 'envoi' | 'fait' | 'conflit' | 'erreur'>('repos')
async function garder() {
  envoi.value = 'envoi'
  try {
    const ordre = proposees.value.map((c) => c.competence).filter((c) => cochees.value.includes(c))
    const { data, response } = await api.PATCH('/api/me/profil', {
      headers: { 'Content-Type': 'application/merge-patch+json' },
      body: { competencesIaRetenues: ordre, version: props.profil.version },
    })
    if (data) {
      emit('misAJour', data)
      envoi.value = 'fait'
    } else {
      envoi.value = response.status === 409 ? 'conflit' : 'erreur'
    }
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section v-if="proposees.length" class="carte carte-competences" aria-labelledby="carte-competences" data-test="carte-competences">
    <div class="tete">
      <h2 id="carte-competences">{{ t('profil.carte.titre') }}</h2>
      <p class="mention">✨ {{ t('profil.carte.mention') }}</p>
    </div>
    <p class="texte-discret">{{ t('profil.carte.aide') }}</p>

    <ul class="liste">
      <li v-for="c in proposees" :key="c.competence" class="ligne" :class="{ 'ligne--retiree': !cochees.includes(c.competence) }">
        <label class="ligne__case">
          <input v-model="cochees" type="checkbox" :value="c.competence" data-test="garder" />
          <span class="ligne__nom">{{ c.competence }}</span>
          <span class="niveau" :class="`niveau--${c.niveau}`">{{ t(`profil.carte.niveaux.${c.niveau}`) }}</span>
        </label>
        <p class="ligne__raison">{{ c.raison }}</p>
        <p class="ligne__preuves">
          <span class="texte-discret">{{ t('profil.carte.preuves') }}</span>
          <span v-for="p in c.preuves" :key="p" class="depot">{{ p }}</span>
        </p>
      </li>
    </ul>

    <div class="commandes">
      <JBouton :desactive="!modifie" :occupe="envoi === 'envoi'" data-test="garder-carte" @click="garder">
        {{ t('profil.carte.garder', { n: cochees.length }, cochees.length) }}
      </JBouton>
      <p v-if="envoi === 'fait' && !modifie" role="status" class="texte-discret">✓ {{ t('profil.carte.garde') }}</p>
      <p v-else-if="envoi === 'conflit'" role="alert" class="erreur">{{ t('profil.modifier.conflit') }}</p>
      <p v-else-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
    </div>
  </section>
</template>

<style scoped>
.carte-competences { display: grid; gap: var(--space-4); }
.tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2) var(--space-4); }
.tete h2 { margin: 0; }
/* Charte : une proposition de l'IA n'est pas une preuve, d'où le pointillé. */
.mention {
  margin: 0; padding: 2px var(--space-3); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--action-soft) 70%, transparent); font-size: var(--type-label-size); font-weight: 600;
}
.carte-competences > .texte-discret { margin: 0; }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.ligne {
  display: grid; gap: var(--space-1); padding: var(--space-3) var(--space-4); border-radius: var(--radius-md);
  border: 1px solid var(--line); transition: opacity var(--motion-fast) var(--ease-out);
}
.ligne--retiree { opacity: 0.55; }
.ligne--retiree .ligne__nom { text-decoration: line-through; }
.ligne__case { display: flex; align-items: center; gap: var(--space-3); cursor: pointer; min-height: 32px; }
.ligne__case input { width: 20px; height: 20px; margin: 0; flex: none; accent-color: var(--action); }
.ligne__nom { font-weight: 700; }
.niveau { margin-inline-start: auto; padding: 1px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-label-size); font-weight: 600; background: var(--action-soft); color: var(--ink); }
.niveau--confirme { background: var(--action); color: var(--on-action); }
.ligne__raison, .ligne__preuves { margin: 0; padding-inline-start: calc(20px + var(--space-3)); font-size: var(--type-body-sm-size); }
.ligne__preuves { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-1) var(--space-2); }
.depot { font-family: var(--font-text); padding: 0 var(--space-2); border-radius: var(--radius-sm); background: color-mix(in srgb, var(--ink) 7%, transparent); }
.commandes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.commandes p { margin: 0; }
.erreur { color: var(--danger); font-weight: 600; }
</style>
