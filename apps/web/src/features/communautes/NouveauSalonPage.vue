<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JBouton, JChamp, JSquelette } from '../../design-system'
import EspaceCommunautes from './EspaceCommunautes.vue'

type Communaute = components['schemas']['Communaute']

/*
 * Créer un salon dans une communauté (contracts/communautes.yaml v0.4) : réservé aux modérateurs, vérifié par le
 * serveur. Le nom devient l'adresse du salon (« Offres d'emploi » → `#offres-d-emploi`) ; l'aperçu le montre en direct.
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const slug = computed(() => String(route.params.slug))

const communaute = ref<Communaute | null>(null)
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
async function charger() {
  etat.value = 'chargement'
  try {
    const { data, response } = await api.GET('/api/communautes/{slug}', { params: { path: { slug: slug.value } } })
    if (response.status === 404) return void (etat.value = 'introuvable')
    if (!data) throw new Error('communauté indisponible')
    communaute.value = data
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(slug, () => void charger(), { immediate: true })

const nom = ref('')
const sujet = ref('')
/** Comme le serveur : sans accents, minuscules, des tirets. */
const court = computed(() =>
  nom.value.replace(/^#\s*/, '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40))
const valide = computed(() => court.value.length >= 2 && sujet.value.trim().length >= 5)
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'existe' | 'interdit' | 'trop'>('repos')

const SUGGESTIONS = ['annonces', 'offres-emploi', 'ressources', 'evenements', 'detente'] as const

async function creer() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const { data, error, response } = await api.POST('/api/communautes/{slug}/salons', {
      params: { path: { slug: slug.value } },
      body: { nom: nom.value.trim(), sujet: sujet.value.trim() },
    })
    if (data) return void (await router.push({ path: `/communautes/${slug.value}`, query: { salon: data.slug } }))
    const code = (error as { code?: string } | undefined)?.code
    envoi.value = code === 'salon_existe' ? 'existe' : code === 'pas_moderateur' ? 'interdit' : code === 'trop_de_salons' ? 'trop' : response.status === 403 ? 'interdit' : 'erreur'
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <EspaceCommunautes :actuelle="etat === 'introuvable' ? undefined : slug" actif="nouveau-salon" :communaute="communaute">
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink>
    </div>
    <p v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">{{ t('etat.indisponible') }}</p>

    <section v-else-if="communaute && !communaute.moderateur" class="carte-espace refus" data-test="reserve-moderation">
      <h1>{{ t('communautes.nouveauSalon.titre') }}</h1>
      <p>{{ t('communautes.nouveauSalon.reserve') }}</p>
      <RouterLink :to="{ path: `/communautes/${slug}`, query: { voir: 'membres' } }">{{ t('communautes.nouveauSalon.voirModeration') }}</RouterLink>
    </section>

    <section v-else-if="communaute" class="carte-espace creation" aria-labelledby="titre-salon">
      <form class="formulaire" data-test="nouveau-salon" @submit.prevent="creer">
        <header>
          <h1 id="titre-salon">{{ t('communautes.nouveauSalon.titre') }}</h1>
          <p class="texte-discret">{{ t('communautes.nouveauSalon.intro', { communaute: communaute.nom }) }}</p>
        </header>
        <JChamp v-model="nom" :libelle="t('communautes.nouveauSalon.nom')" :aide="t('communautes.nouveauSalon.nomAide')" :maxlength="40" requis />
        <div class="suggestions">
          <span class="texte-discret">{{ t('communautes.nouveauSalon.idees') }}</span>
          <button v-for="s in SUGGESTIONS" :key="s" type="button" class="suggestion" @click="nom = s"># {{ s }}</button>
        </div>
        <JChamp v-model="sujet" :libelle="t('communautes.nouveauSalon.sujet')" :aide="t('communautes.nouveauSalon.sujetAide')" :maxlength="200" requis />
        <p v-if="envoi === 'existe'" role="alert" class="erreur">{{ t('communautes.nouveauSalon.existe') }}</p>
        <p v-else-if="envoi === 'interdit'" role="alert" class="erreur">{{ t('communautes.nouveauSalon.reserve') }}</p>
        <p v-else-if="envoi === 'trop'" role="alert" class="erreur">{{ t('communautes.nouveauSalon.trop') }}</p>
        <p v-else-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
        <div class="commandes">
          <RouterLink :to="`/communautes/${slug}`" class="j-bouton j-bouton--discret">{{ t('communautes.annuler') }}</RouterLink>
          <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="creer-salon">{{ t('communautes.nouveauSalon.creer') }}</JBouton>
        </div>
      </form>

      <aside class="apercu" :aria-label="t('communautes.nouvelle.apercu')">
        <p class="apercu__titre">{{ t('communautes.nouvelle.apercu') }}</p>
        <div class="apercu__salon">
          <span class="apercu__signe" aria-hidden="true">#</span>
          <strong data-test="apercu-salon">{{ court || t('communautes.nouveauSalon.exemple') }}</strong>
          <p>{{ sujet.trim() || t('communautes.nouveauSalon.sujetExemple') }}</p>
          <span class="apercu__appel">📞 {{ t('communautes.appels.lancer') }}</span>
        </div>
      </aside>
    </section>
  </EspaceCommunautes>
</template>

<style scoped>
.carte-espace { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); }
.etat-vide { margin-top: var(--space-6); }
.refus { padding: var(--space-8); display: grid; gap: var(--space-3); }
.refus h1, .refus p { margin: 0; }
.creation { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 30rem) minmax(0, 1fr); padding: var(--space-8); align-items: start; }
@media (max-width: 1100px) { .creation { grid-template-columns: 1fr; } }
.formulaire { display: grid; gap: var(--space-4); }
.formulaire h1 { margin: 0; font-size: 1.5rem; }
.formulaire header p { margin: var(--space-2) 0 0; }
.suggestions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin-top: calc(-1 * var(--space-2)); }
.suggestion {
  padding: 4px 12px; border-radius: var(--radius-pill); border: 1px dashed var(--line-strong); background: transparent;
  color: var(--ink-muted); font: inherit; font-size: 0.8125rem; cursor: pointer;
}
.suggestion:hover { color: var(--ink); border-style: solid; }
.commandes { display: flex; gap: var(--space-2); justify-content: flex-end; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.apercu { display: grid; gap: var(--space-2); }
.apercu__titre { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.apercu__salon {
  display: grid; gap: var(--space-2); justify-items: start; padding: var(--space-6); border-radius: var(--radius-lg);
  background: var(--surface-raised); border: 1px dashed var(--line-strong);
}
.apercu__signe { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 50%; background: var(--surface); font-size: 2rem; }
.apercu__salon strong { font-family: var(--font-display); font-size: 1.5rem; overflow-wrap: anywhere; }
.apercu__salon p { margin: 0; color: var(--ink-muted); }
.apercu__appel { padding: 4px 12px; border-radius: var(--radius-pill); background: color-mix(in srgb, #23a55a 14%, transparent); font-size: 0.8125rem; font-weight: 700; }
</style>
