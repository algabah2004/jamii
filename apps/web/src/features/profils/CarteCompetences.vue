<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import { JBouton } from '../../design-system'
import IconeProfil from './IconeProfil.vue'
import type { MonProfil } from './profils'

/*
 * La carte de compétences proposée par l'IA (contracts/profils.yaml, `competencesProposees`). Rien n'est public tant
 * que le développeur ne l'a pas gardé : il coche ce qui est juste, retire le reste, puis enregistre
 * (`competencesIaRetenues`). Chaque compétence montre son niveau et ses preuves : les dépôts publics qui la justifient.
 * Une fois gardée, la carte se replie en un bandeau : on peut la revoir, elle n'encombre plus le profil.
 */
const props = defineProps<{ profil: MonProfil }>()
const emit = defineEmits<{ misAJour: [profil: MonProfil] }>()
const { t } = useI18n()

const proposees = computed(() => props.profil.competencesProposees ?? [])
const retenuesEnregistrees = computed(() => props.profil.competences.filter((c) => c.source === 'ia').map((c) => c.competence))
/** Jamais rien de gardé : tout est coché, mais rien n'est publié avant « Garder ». Sinon, ce qui a été gardé. */
const dejaDecide = computed(() => retenuesEnregistrees.value.length > 0)
const ouverte = ref(!dejaDecide.value)

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
      ouverte.value = false
    } else {
      envoi.value = response.status === 409 ? 'conflit' : 'erreur'
    }
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section v-if="proposees.length" class="carte-ia" :class="{ 'carte-ia--repliee': !ouverte }" aria-labelledby="carte-competences" data-test="carte-competences">
    <div class="tete">
      <span class="tete__icone"><IconeProfil nom="etincelle" /></span>
      <div class="tete__texte">
        <h2 id="carte-competences">{{ t('profil.carte.titre') }}</h2>
        <p class="mention">{{ t('profil.carte.mention') }}</p>
      </div>
      <button v-if="!ouverte" type="button" class="revoir" :aria-expanded="false" data-test="revoir-carte" @click="ouverte = true">
        {{ t('profil.carte.revoir') }}
      </button>
    </div>

    <p v-if="!ouverte" class="resume" role="status">
      <IconeProfil nom="coche" :taille="16" />{{ t('profil.carte.resume', { n: retenuesEnregistrees.length }, retenuesEnregistrees.length) }}
    </p>

    <template v-else>
      <p class="aide">{{ t('profil.carte.aide') }}</p>
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
            <span v-for="p in c.preuves" :key="p" class="depot"><IconeProfil nom="code" :taille="12" />{{ p }}</span>
          </p>
        </li>
      </ul>

      <div class="commandes">
        <JBouton :desactive="!modifie" :occupe="envoi === 'envoi'" data-test="garder-carte" @click="garder">
          {{ t('profil.carte.garder', { n: cochees.length }, cochees.length) }}
        </JBouton>
        <JBouton v-if="dejaDecide && !modifie" variante="discret" @click="ouverte = false">{{ t('profil.carte.replier') }}</JBouton>
        <p v-if="envoi === 'conflit'" role="alert" class="erreur">{{ t('profil.modifier.conflit') }}</p>
        <p v-else-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
/* Charte : une proposition de l'IA n'est pas une preuve, d'où le pointillé. */
.carte-ia { display: grid; gap: 16px; padding: clamp(20px, 2.5vw, 28px); border: 1px dashed var(--line-strong); border-radius: 24px;
  background: radial-gradient(ellipse at 100% 0, color-mix(in srgb, var(--action-soft) 90%, transparent), transparent 60%), var(--surface); }
.carte-ia--repliee { gap: 10px; }
.tete { display: flex; align-items: center; gap: 12px; }
.tete__icone { display: grid; place-items: center; width: 36px; height: 36px; flex: none; border-radius: 12px; background: var(--action); color: var(--on-action); }
.tete__texte { flex: 1; min-width: 0; }
.tete h2 { margin: 0; font-size: 1.25rem; letter-spacing: -.02em; }
.mention { margin: 2px 0 0; color: var(--ink-muted); font-size: .8125rem; font-weight: 600; }
.revoir { flex: none; min-height: 40px; padding: 0 16px; border: 1px solid var(--line-strong); border-radius: 50px; background: var(--surface); color: var(--ink); font-weight: 600; cursor: pointer; }
.revoir:hover { border-color: var(--action); }
.resume { display: flex; align-items: center; gap: 8px; margin: 0; font-weight: 600; color: var(--action-ink); }
.aide { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.ligne { display: grid; gap: 4px; padding: 12px 16px; border-radius: 16px; border: 1px solid var(--line); background: var(--surface); transition: opacity var(--motion-fast) var(--ease-out); }
.ligne--retiree { opacity: 0.55; }
.ligne--retiree .ligne__nom { text-decoration: line-through; }
.ligne__case { display: flex; align-items: center; gap: 12px; cursor: pointer; min-height: 32px; }
.ligne__case input { width: 20px; height: 20px; margin: 0; flex: none; accent-color: var(--action); }
.ligne__nom { font-weight: 700; }
.niveau { margin-inline-start: auto; padding: 2px 12px; border-radius: 50px; font-size: .8125rem; font-weight: 700; background: var(--action-soft); color: var(--action-ink); }
.niveau--confirme { background: var(--action); color: var(--on-action); }
.ligne__raison, .ligne__preuves { margin: 0; padding-inline-start: 32px; font-size: var(--type-body-sm-size); }
.ligne__preuves { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; }
.depot { display: inline-flex; align-items: center; gap: 4px; padding: 1px 8px; border-radius: 6px; background: var(--surface-raised); font-size: .8125rem; }
.commandes { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.commandes p { margin: 0; }
.erreur { color: var(--danger); font-weight: 600; }
</style>
