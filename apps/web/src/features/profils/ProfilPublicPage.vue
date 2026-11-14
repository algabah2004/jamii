<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import { JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import FicheProfil from './FicheProfil.vue'
import type { Profil } from './profils'

/** Le profil publié de quelqu'un (#159) : la page du lien partagé et du QR code. Lisible sans compte. */
const route = useRoute()
const { t } = useI18n()
const { moi } = useSession()

const profil = ref<Profil | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')

async function charger(id: string) {
  etat.value = 'chargement'
  try {
    const { data, response } = await api.GET('/api/profils/{utilisateurId}', { params: { path: { utilisateurId: id } } })
    if (response.status === 404) {
      etat.value = 'introuvable'
      return
    }
    if (!data) throw new Error('profil indisponible')
    profil.value = data
    etat.value = 'pret'
    document.title = `${data.nom}${data.titre ? ` · ${data.titre}` : ''} · Jamii`
  } catch {
    etat.value = 'indisponible'
  }
}

watch(() => String(route.params.id), (id) => void charger(id), { immediate: true })
</script>

<template>
  <section class="profil-public">
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide" data-test="introuvable">
      <h1>{{ t('profil.public.introuvable') }}</h1>
      <p class="texte-discret">{{ t('profil.public.introuvableAide') }}</p>
      <RouterLink to="/">{{ t('profil.public.decouvrir') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger(String(route.params.id))">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else-if="profil">
      <FicheProfil :profil="profil" :moi="moi?.id === profil.utilisateurId" />
      <aside v-if="!moi" class="carte invitation" data-test="invitation">
        <p><strong>{{ t('profil.public.invitationTitre') }}</strong> {{ t('profil.public.invitationTexte') }}</p>
        <RouterLink to="/connexion" class="invitation__lien">{{ t('profil.public.rejoindre') }}</RouterLink>
      </aside>
    </template>
  </section>
</template>

<style scoped>
.profil-public { display: grid; gap: var(--space-6); }
.etat-vide { display: grid; gap: var(--space-3); justify-items: start; }
.etat-vide h1, .etat-vide p { margin: 0; }
.invitation { display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: center; justify-content: space-between; }
.invitation p { margin: 0; max-width: 40rem; }
.invitation__lien { font-weight: 700; }
</style>
