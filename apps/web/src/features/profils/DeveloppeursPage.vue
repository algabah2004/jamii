<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { chercherProfils, NOM_MIN, type ProfilResume } from './recherche'

/*
 * Trouver un développeur (contracts/profils.yaml, GET /api/profils) : par son nom, une compétence, ou les seuls
 * disponibles. Seuls les profils publiés apparaissent ; le sien n'y est jamais.
 */
const { t } = useI18n()
const q = ref('')
const competence = ref('')
const disponibles = ref(false)
const profils = ref<ProfilResume[]>([])
const suivant = ref<string | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const suite = ref(false)
/** Un nom d'une seule lettre n'est pas envoyé : on le dit, plutôt que de montrer tout le monde sans explication. */
const nomTropCourt = computed(() => q.value.trim().length > 0 && q.value.trim().length < NOM_MIN)

// Chaque recherche a un numéro : une réponse lente qui arrive après une plus récente est ignorée.
let numero = 0
async function chercher() {
  const n = ++numero
  etat.value = 'chargement'
  try {
    const page = await chercherProfils({ q: q.value, competence: competence.value, disponibles: disponibles.value })
    if (n !== numero) return
    profils.value = page.elements
    suivant.value = page.curseurSuivant ?? null
    etat.value = 'pret'
  } catch {
    if (n === numero) etat.value = 'indisponible'
  }
}
async function plus() {
  if (!suivant.value || suite.value) return
  suite.value = true
  try {
    const page = await chercherProfils({ q: q.value, competence: competence.value, disponibles: disponibles.value }, suivant.value)
    profils.value = [...profils.value, ...page.elements]
    suivant.value = page.curseurSuivant ?? null
  } catch {
    // Le bouton reste : on peut réessayer.
  } finally {
    suite.value = false
  }
}

// On attend que la personne ait fini de taper (300 ms) avant de chercher.
let minuterie: ReturnType<typeof setTimeout> | undefined
watch([q, competence], () => {
  clearTimeout(minuterie)
  minuterie = setTimeout(() => void chercher(), 300)
})
watch(disponibles, () => void chercher())
onBeforeUnmount(() => clearTimeout(minuterie))
void chercher()
</script>

<template>
  <section class="developpeurs" aria-labelledby="titre-developpeurs">
    <header class="tete">
      <h1 id="titre-developpeurs">{{ t('developpeurs.titre') }}</h1>
      <p>{{ t('developpeurs.intro') }}</p>
    </header>

    <form class="filtres" role="search" :aria-label="t('developpeurs.rechercher')" @submit.prevent="chercher">
      <label class="champ champ--nom">
        <span class="visuellement-cache">{{ t('developpeurs.nom') }}</span>
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
        <input v-model="q" type="search" maxlength="50" autocomplete="off" :placeholder="t('developpeurs.nom')" data-test="recherche-nom" />
      </label>
      <label class="champ">
        <span class="visuellement-cache">{{ t('developpeurs.competence') }}</span>
        <input v-model="competence" type="search" maxlength="50" autocomplete="off" :placeholder="t('developpeurs.competence')" data-test="recherche-competence" />
      </label>
      <label class="dispo">
        <input v-model="disponibles" type="checkbox" data-test="recherche-disponibles" />
        {{ t('developpeurs.disponibles') }}
      </label>
    </form>
    <p v-if="nomTropCourt" class="aide" role="status">{{ t('developpeurs.nomCourt', { n: NOM_MIN }) }}</p>

    <JSquelette v-if="etat === 'chargement' && !profils.length" :nombre="4" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="chercher">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <p v-else-if="!profils.length" class="vide" role="status" data-test="aucun-profil">{{ t('developpeurs.aucun') }}</p>
    <template v-else>
      <p class="visuellement-cache" role="status">{{ t('developpeurs.resultats', { n: profils.length }, profils.length) }}</p>
      <ul class="liste" :aria-busy="etat === 'chargement'" data-test="profils">
        <li v-for="p in profils" :key="p.utilisateurId" class="carte" data-test="profil">
          <JAvatar :nom="p.nom" :url="p.avatarUrl" :taille="48" />
          <div class="carte__corps">
            <h2><RouterLink :to="`/p/${p.utilisateurId}`" class="carte__nom">{{ p.nom }}</RouterLink></h2>
            <p class="carte__infos">
              <span v-if="p.ville">{{ p.ville }}</span>
              <span class="dispo-etiquette" :class="`dispo-etiquette--${p.disponibilite}`">{{ t(`profil.disponibilite.${p.disponibilite}`) }}</span>
            </p>
            <ul v-if="p.competences.length" class="competences" :aria-label="t('developpeurs.competences')">
              <li v-for="c in p.competences.slice(0, 5)" :key="c">{{ c }}</li>
            </ul>
          </div>
          <RouterLink :to="`/messages/${p.utilisateurId}`" class="ecrire" :aria-label="t('developpeurs.ecrire', { nom: p.nom })" data-test="ecrire-profil">
            {{ t('reseau.contacts.ecrire') }}
          </RouterLink>
        </li>
      </ul>
      <div v-if="suivant" class="plus">
        <JBouton variante="secondaire" :occupe="suite" @click="plus">{{ t('developpeurs.plus') }}</JBouton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.developpeurs { display: grid; gap: var(--space-4); max-width: 48rem; margin-inline: auto; }
.tete h1 { margin: 0; font-size: clamp(1.75rem, 5vw, 2.5rem); letter-spacing: -0.04em; }
.tete p { margin: var(--space-2) 0 0; color: var(--ink-muted); }

.filtres { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) auto; gap: var(--space-2); align-items: center; }
.champ { display: flex; align-items: center; gap: var(--space-2); min-height: 48px; padding: 0 var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--surface); color: var(--ink-muted); }
.champ:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
.champ input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; padding: 0; box-shadow: none; }
.dispo { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; font-weight: 600; white-space: nowrap; cursor: pointer; }
.dispo input { width: 20px; height: 20px; accent-color: var(--action); }
.dispo input:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.aide { margin: 0; color: var(--ink-muted); font-size: 0.875rem; }

.liste { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.carte { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); }
.carte__corps { flex: 1; min-width: 0; display: grid; gap: 4px; }
.carte h2 { margin: 0; font-size: 1rem; }
.carte__nom { color: var(--ink); text-decoration: none; }
.carte__nom:hover { text-decoration: underline; }
.carte__nom:focus-visible, .ecrire:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; border-radius: 4px; }
.carte__infos { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; color: var(--ink-muted); font-size: 0.875rem; }
.dispo-etiquette { padding: 1px 8px; border-radius: var(--radius-pill); background: var(--surface-raised); font-size: 0.75rem; font-weight: 700; }
.dispo-etiquette--disponible { background: color-mix(in srgb, var(--en-ligne) 16%, transparent); color: var(--ink); }
.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 4px; }
.competences li { padding: 1px 8px; border: 1px solid var(--line); border-radius: var(--radius-pill); font-size: 0.75rem; color: var(--ink-muted); }
.ecrire { flex: none; display: inline-flex; align-items: center; min-height: 44px; padding: 0 var(--space-4); border: 1px solid var(--line-strong, var(--line)); border-radius: var(--radius-pill); color: var(--ink); font-weight: 600; text-decoration: none; }
.ecrire:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.vide { margin: 0; padding: var(--space-6) 0; color: var(--ink-muted); display: grid; justify-items: start; gap: var(--space-3); }
.plus { display: flex; justify-content: center; }

/* Téléphone : les champs l'un sous l'autre ; « Écrire » passe sous la carte, à pleine largeur, facile à toucher. */
@media (max-width: 600px) {
  .filtres { grid-template-columns: minmax(0, 1fr); }
  .carte { flex-wrap: wrap; align-items: flex-start; padding: var(--space-3); }
  .ecrire { flex: 1 1 100%; justify-content: center; }
}
</style>
