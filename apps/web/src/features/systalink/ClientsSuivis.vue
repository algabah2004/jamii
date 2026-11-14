<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/systalink'
import { JBouton } from '../../design-system'
import { nomPays } from './calculs'

/*
 * Les clients qui ont demandé à être suivis (contracts/systalink.yaml) : seulement les personnes qui ont coché
 * « Recevoir les offres Systalink adaptées à mon projet ». Réservé au rôle `systalink` (ou `admin`) : l'API répond
 * 401 ou 403 aux autres, et le bloc ne s'affiche pas. Le besoin est montré reformulé, jamais le texte d'origine.
 */
type ClientSuivi = components['schemas']['ClientSuivi']
type StatutClient = components['schemas']['StatutClient']

const STATUTS: StatutClient[] = ['nouveau', 'contacte', 'client', 'sans_suite']
const { t, locale } = useI18n()

const filtre = ref<StatutClient | ''>('')
const clients = ref<ClientSuivi[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'interdit' | 'indisponible'>('chargement')

async function charger(curseur?: string) {
  if (!curseur) etat.value = etat.value === 'interdit' ? 'interdit' : 'chargement'
  try {
    const { data, response } = await api.GET('/api/systalink/clients-suivis', {
      params: { query: { ...(filtre.value ? { statut: filtre.value } : {}), ...(curseur ? { curseur } : {}), limite: 20 } },
    })
    if (response.status === 401 || response.status === 403) return void (etat.value = 'interdit')
    // La route répond aussi en CSV (`Accept: text/csv`) : ici, on a demandé du JSON.
    if (!data || typeof data === 'string') return void (etat.value = 'indisponible')
    clients.value = curseur ? [...clients.value, ...data.elements] : data.elements
    suivant.value = data.curseurSuivant ?? null
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(filtre, () => void charger(), { immediate: true })

const enregistrement = ref<string | null>(null)
const erreur = ref<string | null>(null)
async function changerStatut(client: ClientSuivi, statut: StatutClient) {
  enregistrement.value = client.demandeId
  erreur.value = null
  try {
    const { data } = await api.PATCH('/api/systalink/clients-suivis/{demandeId}', {
      params: { path: { demandeId: client.demandeId } },
      body: { statut },
    })
    if (data) clients.value = clients.value.map((c) => (c.demandeId === data.demandeId ? data : c))
    else erreur.value = client.demandeId
  } catch {
    erreur.value = client.demandeId
  } finally {
    enregistrement.value = null
  }
}

/** L'export CSV : toutes les lignes du filtre (`Accept: text/csv`), enregistrées comme un fichier. */
const exportEnCours = ref(false)
async function exporter() {
  exportEnCours.value = true
  try {
    const adresse = new URL('/api/systalink/clients-suivis', window.location.origin)
    if (filtre.value) adresse.searchParams.set('statut', filtre.value)
    const reponse = await globalThis.fetch(adresse, { credentials: 'same-origin', headers: { Accept: 'text/csv' } })
    if (!reponse.ok) return
    const lien = document.createElement('a')
    lien.href = URL.createObjectURL(await reponse.blob())
    lien.download = 'jamii-clients-suivis.csv'
    lien.click()
    URL.revokeObjectURL(lien.href)
  } finally {
    exportEnCours.value = false
  }
}

const lieu = (c: ClientSuivi) => [c.ville, c.pays ? nomPays(c.pays, locale.value) : null].filter(Boolean).join(', ')
const date = (iso: string) => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(new Date(iso))
const vide = computed(() => etat.value === 'pret' && clients.value.length === 0)
</script>

<template>
  <section v-if="etat !== 'interdit'" class="carte bloc clients" aria-labelledby="clients-suivis" data-test="clients-suivis">
    <div class="tete">
      <div>
        <h2 id="clients-suivis">{{ t('systalink.clients.titre') }}</h2>
        <p class="texte-discret">{{ t('systalink.clients.intro') }}</p>
      </div>
      <div class="actions">
        <label class="filtre">
          <span class="visuellement-cache">{{ t('systalink.clients.filtre') }}</span>
          <select v-model="filtre" data-test="filtre-statut">
            <option value="">{{ t('systalink.clients.tous') }}</option>
            <option v-for="s in STATUTS" :key="s" :value="s">{{ t(`systalink.clients.statuts.${s}`) }}</option>
          </select>
        </label>
        <JBouton variante="secondaire" :occupe="exportEnCours" :desactive="vide" data-test="export-csv" @click="exporter">{{ t('systalink.clients.csv') }}</JBouton>
      </div>
    </div>

    <p v-if="etat === 'indisponible'" class="texte-discret" role="status">{{ t('etat.indisponible') }}</p>
    <p v-else-if="vide" class="texte-discret" data-test="clients-vide">{{ t('systalink.clients.vide') }}</p>

    <ul v-else-if="etat === 'pret'" class="liste">
      <li v-for="c in clients" :key="c.demandeId" class="client" data-test="client">
        <div class="client__texte">
          <p class="client__resume">{{ c.resume }}</p>
          <p class="texte-discret">
            <template v-if="c.secteur">{{ c.secteur }} · </template><template v-if="lieu(c)">{{ lieu(c) }} · </template>{{ date(c.creeLe) }}
          </p>
          <p class="client__services">
            <span v-for="s in c.services" :key="s" class="etiquette">{{ s }}</span>
            <span v-if="c.developpeursNecessaires" class="etiquette etiquette--dev">{{ t('systalink.clients.avecDeveloppeurs') }}</span>
          </p>
        </div>
        <div class="client__contact">
          <strong>{{ c.contact.nom }}</strong>
          <a v-if="c.contact.email" :href="`mailto:${c.contact.email}`">{{ c.contact.email }}</a>
          <span v-else class="texte-discret">{{ t('systalink.clients.sansEmail') }}</span>
          <label>
            <span class="visuellement-cache">{{ t('systalink.clients.statut') }}</span>
            <select
              :value="c.statut"
              :disabled="enregistrement === c.demandeId"
              data-test="statut-client"
              @change="changerStatut(c, ($event.target as HTMLSelectElement).value as StatutClient)"
            >
              <option v-for="s in STATUTS" :key="s" :value="s">{{ t(`systalink.clients.statuts.${s}`) }}</option>
            </select>
          </label>
          <p v-if="erreur === c.demandeId" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
        </div>
      </li>
    </ul>
    <JBouton v-if="suivant" variante="secondaire" @click="charger(suivant)">{{ t('systalink.clients.plus') }}</JBouton>
  </section>
</template>

<style scoped>
.clients { display: grid; gap: var(--space-4); }
.tete { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: end; gap: var(--space-4); }
.tete h2 { margin: 0; }
.tete p { margin: var(--space-1) 0 0; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
select {
  min-height: 40px; padding: 0 var(--space-3); border-radius: var(--radius-md); border: 1px solid var(--line-strong);
  background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size);
}
.liste { list-style: none; margin: 0; padding: 0; display: grid; }
.client { display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 1fr) minmax(0, 15rem); padding: var(--space-4) 0; border-top: 1px solid var(--line); }
@media (max-width: 640px) { .client { grid-template-columns: 1fr; } }
.client p { margin: 0; }
.client__texte { display: grid; gap: var(--space-1); }
.client__resume { font-weight: 600; }
.client__services { display: flex; flex-wrap: wrap; gap: var(--space-1); margin-top: var(--space-1) !important; }
.etiquette--dev { border-style: dashed; }
.client__contact { display: grid; gap: var(--space-1); align-content: start; font-size: var(--type-body-sm-size); overflow-wrap: anywhere; }
.erreur { color: var(--danger); font-weight: 600; }
</style>
