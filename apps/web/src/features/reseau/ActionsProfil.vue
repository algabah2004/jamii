<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { ajouterContact, lireLien, retirerContact, type StatutLien } from './reseau'

/*
 * Ce qu'on peut faire depuis le profil de quelqu'un (connecté, et pas le sien) : lui écrire, et se mettre en contact.
 * Le message d'abord : c'est l'action la plus fréquente. Le lien suit l'état réel (contracts/reseau.yaml, StatutLien).
 */
const props = defineProps<{ personneId: string; prenom: string }>()
const { t } = useI18n()

const lien = ref<StatutLien | null>(null)
const occupe = ref(false)
const erreur = ref(false)

onMounted(async () => {
  try {
    lien.value = await lireLien(props.personneId)
  } catch {
    lien.value = 'aucun'
  }
})

async function contact() {
  occupe.value = true
  erreur.value = false
  try {
    if (lien.value === 'envoyee') {
      if (await retirerContact(props.personneId)) lien.value = 'aucun'
      else erreur.value = true
    } else {
      const statut = await ajouterContact(props.personneId)
      if (statut) lien.value = statut
      else erreur.value = true
    }
  } catch {
    erreur.value = true
  } finally {
    occupe.value = false
  }
}
</script>

<template>
  <div class="actions-profil" data-test="actions-profil">
    <RouterLink :to="`/messages/${personneId}`" class="j-bouton j-bouton--principal action" data-test="ecrire">
      <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M3.5 5.5A2 2 0 0 1 5.5 3.5h9a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H9l-3.5 3v-3h0a2 2 0 0 1-2-2z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" /></svg>
      {{ t('reseau.profil.ecrire', { nom: prenom }) }}
    </RouterLink>
    <p v-if="lien === 'accepte'" class="statut" data-test="en-contact">
      <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="m5 10.5 3 3 7-7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
      {{ t('reseau.profil.enContact') }}
    </p>
    <button
      v-else-if="lien"
      type="button"
      class="j-bouton j-bouton--secondaire action"
      :disabled="occupe"
      :aria-busy="occupe || undefined"
      data-test="contact"
      @click="contact"
    >
      {{ t(`reseau.profil.contact.${lien}`) }}
    </button>
    <p v-if="erreur" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
  </div>
</template>

<style scoped>
.actions-profil { justify-self: start; display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.action { justify-content: center; display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; text-decoration: none; }
.statut { display: inline-flex; align-items: center; gap: var(--space-1); margin: 0; color: var(--ink-muted); font-weight: 600; }
.erreur { width: 100%; margin: 0; color: var(--danger); font-weight: 600; }
</style>
