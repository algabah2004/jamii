<script setup lang="ts">
import { JAvatar, JSquelette } from '../../design-system'
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../../api/client'
import type { components } from '../../api/generated/cercles'
import { ilYa } from '../vitrine/vitrine'

/**
 * Le code du tour, vu depuis Jamii : les derniers commits et les demandes de fusion du dépôt, lus par l'API chez le
 * fournisseur (GitHub aujourd'hui, Gitea demain). On code et on fusionne chez le fournisseur ; on suit ici.
 */
const props = defineProps<{ cercleId: string; numero: number }>()
type Activite = components['schemas']['ActiviteDepot']

const { t, locale } = useI18n()
const activite = ref<Activite | null>(null)
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
const copie = ref(false)

onMounted(async () => {
  try {
    const { data } = await api.GET('/api/cercles/{cercleId}/tours/{numero}/activite', {
      params: { path: { cercleId: props.cercleId, numero: props.numero } },
    })
    activite.value = data ?? null
    etat.value = data ? 'pret' : 'erreur'
  } catch {
    etat.value = 'erreur'
  }
})

async function copier(texte: string) {
  try {
    await navigator.clipboard.writeText(texte)
    copie.value = true
    setTimeout(() => (copie.value = false), 2000)
  } catch {
    // Presse-papiers refusé : la commande reste sélectionnable à la main.
  }
}
</script>

<template>
  <section class="code" aria-labelledby="code-titre" data-test="code-du-tour">
    <header class="code__tete">
      <h3 id="code-titre">{{ t('cercles.code.titre') }}</h3>
      <a v-if="activite?.depot" :href="activite.depot.url" target="_blank" rel="noopener noreferrer" class="code__lien">
        {{ t('cercles.code.ouvrir', { fournisseur: activite.depot.fournisseur === 'gitea' ? 'Gitea' : 'GitHub' }) }}
        <span class="visuellement-cache">({{ t('vitrine.nouvelOnglet') }})</span>
      </a>
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <p v-else-if="etat === 'erreur'" role="alert" class="note">{{ t('etat.indisponible') }}</p>
    <p v-else-if="activite?.etat === 'aucun_depot'" class="note" data-test="aucun-depot">{{ t('cercles.code.aucunDepot') }}</p>

    <template v-else-if="activite?.depot">
      <div class="clone">
        <code data-test="clone">git clone {{ activite.depot.cloneUrl }}</code>
        <button type="button" class="clone__copier" @click="copier(`git clone ${activite.depot.cloneUrl}`)">
          {{ copie ? t('cercles.code.copie') : t('cercles.code.copier') }}
        </button>
      </div>

      <p v-if="activite.etat === 'indisponible'" class="note" data-test="indisponible">{{ t('cercles.code.indisponible') }}</p>
      <template v-else>
        <h4>{{ t('cercles.code.commits') }}</h4>
        <ol v-if="activite.commits.length" class="liste" data-test="commits">
          <li v-for="c in activite.commits" :key="c.sha">
            <JAvatar :nom="c.auteur?.login ?? '?'" :url="c.auteur?.avatarUrl" :taille="28" />
            <a :href="c.url" target="_blank" rel="noopener noreferrer" class="liste__titre">{{ c.message || c.sha.slice(0, 7) }}</a>
            <span class="liste__meta">{{ c.auteur?.login ?? t('cercles.code.inconnu') }} · {{ ilYa(c.date, locale) }}</span>
          </li>
        </ol>
        <p v-else class="note">{{ t('cercles.code.aucunCommit') }}</p>

        <h4>{{ t('cercles.code.demandes') }}</h4>
        <ol v-if="activite.demandesDeFusion.length" class="liste" data-test="demandes">
          <li v-for="d in activite.demandesDeFusion" :key="d.numero">
            <span class="etat" :class="`etat--${d.etat}`">{{ t(`cercles.code.etat.${d.etat}`) }}</span>
            <a :href="d.url" target="_blank" rel="noopener noreferrer" class="liste__titre">#{{ d.numero }} {{ d.titre }}</a>
            <span class="liste__meta">{{ d.auteur?.login ?? t('cercles.code.inconnu') }} · {{ ilYa(d.misAJourLe, locale) }}</span>
          </li>
        </ol>
        <p v-else class="note">{{ t('cercles.code.aucuneDemande') }}</p>
      </template>
    </template>
  </section>
</template>

<style scoped>
.code { display: grid; gap: var(--space-3); padding-top: var(--space-4); border-top: 1px solid var(--line); }
.code__tete { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: var(--space-2); }
.code h3 { margin: 0; font-size: var(--type-body-size); }
.code h4 { margin: var(--space-2) 0 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
.code__lien { font-size: var(--type-body-sm-size); font-weight: 600; }
.note { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.clone { display: flex; align-items: center; gap: var(--space-2); min-width: 0; padding: var(--space-2) var(--space-3); background: var(--surface-raised); border: 1px solid var(--line); border-radius: var(--radius-md); }
.clone code { flex: 1; min-width: 0; overflow-x: auto; white-space: nowrap; font-size: var(--type-body-sm-size); }
.clone__copier { flex-shrink: 0; padding: var(--space-1) var(--space-2); border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); cursor: pointer; }
.clone__copier:hover { border-color: var(--action); }
.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.liste li { display: grid; grid-template-columns: auto minmax(0, 1fr); column-gap: var(--space-2); align-items: center; }
.liste li > :first-child { grid-row: span 2; }
.liste__titre { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink); font-weight: 600; }
.liste__meta { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.etat { padding: 2px var(--space-2); border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 600; background: var(--surface-raised); color: var(--ink-muted); }
.etat--ouverte { background: var(--action-soft); color: var(--action-ink); }
/* Orange = la preuve : du code fusionné. */
.etat--fusionnee { background: var(--proof-soft); color: var(--proof-ink); }
</style>
