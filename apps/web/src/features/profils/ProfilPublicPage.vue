<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import AbonnesProfil from '../reseau/AbonnesProfil.vue'
import ActionsProfil from '../reseau/ActionsProfil.vue'
import FicheProfil from './FicheProfil.vue'
import type { Profil } from './profils'

/**
 * Le profil de quelqu'un : la page du lien partagé et du QR code (#159), et celle où mène un clic sur une personne,
 * partout dans Jamii. Lisible sans compte. Connecté, on peut lui écrire et se mettre en contact. Si la personne n'a pas
 * encore publié son profil, on montre au moins qui elle est (lu via la conversation) et les mêmes actions.
 */
const route = useRoute()
const { t } = useI18n()
const { moi } = useSession()

const profil = ref<Profil | null>(null)
const etat = ref<'chargement' | 'pret' | 'non-publie' | 'introuvable' | 'indisponible'>('chargement')
const personne = ref<{ id: string; nom: string; avatarUrl?: string | null; ville?: string | null } | null>(null)
const prenom = (nom: string) => nom.split(' ')[0] ?? nom
const autre = computed(() => !!moi.value && moi.value.id !== String(route.params.id))

/** Connecté : qui est cette personne, même sans profil publié. `null` si elle n'existe pas. */
async function lirePersonne(id: string) {
  if (!moi.value) return null
  try {
    const { data } = await api.GET('/api/conversations/{personneId}/messages', { params: { path: { personneId: id }, query: { limite: 1 } } })
    return data?.personne ?? null
  } catch {
    return null
  }
}

async function charger(id: string) {
  etat.value = 'chargement'
  try {
    const { data, response } = await api.GET('/api/profils/{utilisateurId}', { params: { path: { utilisateurId: id } } })
    if (response.status === 404) {
      personne.value = await lirePersonne(id)
      etat.value = personne.value ? 'non-publie' : 'introuvable'
      if (personne.value) document.title = `${personne.value.nom} · Jamii`
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
    <div v-else-if="etat === 'non-publie' && personne" class="carte non-publie" data-test="non-publie">
      <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="104" />
      <div class="non-publie__texte">
        <h1>{{ personne.nom }}</h1>
        <p v-if="personne.ville" class="texte-discret">{{ personne.ville }}</p>
        <p class="texte-discret">{{ t('profil.public.nonPublie', { nom: prenom(personne.nom) }) }}</p>
        <AbonnesProfil v-if="moi" :personne-id="personne.id" :peut-suivre="autre" />
        <ActionsProfil v-if="autre" :personne-id="personne.id" :prenom="prenom(personne.nom)" />
      </div>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger(String(route.params.id))">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else-if="profil">
      <FicheProfil :profil="profil" :moi="moi?.id === profil.utilisateurId">
        <template v-if="moi" #boutons>
          <AbonnesProfil :personne-id="profil.utilisateurId" :peut-suivre="autre" />
          <ActionsProfil v-if="autre" :personne-id="profil.utilisateurId" :prenom="prenom(profil.nom)" />
        </template>
      </FicheProfil>
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
.non-publie { display: flex; flex-wrap: wrap; gap: var(--space-6); align-items: center; }
.non-publie__texte { display: grid; gap: var(--space-2); justify-items: start; min-width: 0; }
.non-publie__texte h1, .non-publie__texte p { margin: 0; }
.invitation { display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: center; justify-content: space-between; }
.invitation p { margin: 0; max-width: 40rem; }
.invitation__lien { font-weight: 700; }
</style>
