<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import ActionsProfil from '../reseau/ActionsProfil.vue'
import FicheProfil from './FicheProfil.vue'
import IconeProfil from './IconeProfil.vue'
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
    <div v-else-if="etat === 'non-publie' && personne" class="non-publie" data-test="non-publie">
      <div class="non-publie__fond" aria-hidden="true" />
      <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="112" class="non-publie__photo" />
      <div class="non-publie__texte">
        <h1>{{ personne.nom }}</h1>
        <p v-if="personne.ville" class="non-publie__ville"><IconeProfil nom="lieu" />{{ personne.ville }}</p>
        <p class="non-publie__note"><IconeProfil nom="cadenas" :taille="16" />{{ t('profil.public.nonPublie', { nom: prenom(personne.nom) }) }}</p>
        <ActionsProfil v-if="autre" :personne-id="personne.id" :prenom="prenom(personne.nom)" />
      </div>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger(String(route.params.id))">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else-if="profil">
      <FicheProfil :profil="profil" :moi="moi?.id === profil.utilisateurId">
        <template v-if="autre" #actions>
          <ActionsProfil :personne-id="profil.utilisateurId" :prenom="prenom(profil.nom)" />
        </template>
      </FicheProfil>
      <aside v-if="!moi" class="invitation" data-test="invitation">
        <div>
          <p class="invitation__titre">{{ t('profil.public.invitationTitre') }}</p>
          <p class="invitation__texte">{{ t('profil.public.invitationTexte') }}</p>
        </div>
        <RouterLink to="/connexion" class="invitation__lien">{{ t('profil.public.rejoindre') }} <IconeProfil nom="fleche" :taille="16" /></RouterLink>
      </aside>
    </template>
  </section>
</template>

<style scoped>
.profil-public { display: grid; gap: 24px; }
.etat-vide { display: grid; gap: var(--space-3); justify-items: start; }
.etat-vide h1, .etat-vide p { margin: 0; }
.non-publie { position: relative; isolation: isolate; overflow: hidden; display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-end; padding: 80px clamp(20px, 4vw, 44px) 32px; border: 1px solid var(--line); border-radius: 28px; background: var(--surface); box-shadow: var(--ombre-carte); }
.non-publie__fond { position: absolute; inset: 0 0 auto; height: 130px; z-index: -1; background: radial-gradient(ellipse 60% 120% at 15% 0%, color-mix(in srgb, var(--action) 30%, transparent), transparent 70%), linear-gradient(180deg, var(--action-soft), var(--surface)); }
.non-publie__photo { border-radius: 50%; box-shadow: 0 0 0 5px var(--surface); }
.non-publie__texte { display: grid; gap: 10px; justify-items: start; min-width: 0; flex: 1 1 18rem; }
.non-publie__texte h1, .non-publie__texte p { margin: 0; }
.non-publie__texte h1 { font-size: clamp(2rem, 4vw, 3rem); letter-spacing: -.04em; line-height: 1.05; }
.non-publie__ville { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.non-publie__note { display: flex; align-items: center; gap: 8px; color: var(--ink-muted); }
.invitation { display: flex; flex-wrap: wrap; gap: 16px 24px; align-items: center; justify-content: space-between; padding: 24px clamp(20px, 3vw, 32px); border-radius: 24px; background: radial-gradient(ellipse at 100% 0, color-mix(in srgb, var(--proof) 35%, transparent), transparent 60%), var(--midnight); color: #EEF4F7; }
.invitation p { margin: 0; }
.invitation__titre { font-family: var(--font-display); font-size: 1.375rem; font-weight: 700; letter-spacing: -.02em; }
.invitation__texte { color: #9DB0BC; max-width: 46ch; }
.invitation__lien { display: inline-flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 22px; border-radius: 50px; background: var(--proof); color: var(--on-proof); font-weight: 700; text-decoration: none; }
</style>
