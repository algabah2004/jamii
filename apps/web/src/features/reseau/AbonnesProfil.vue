<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar } from '../../design-system'
import { changerSuivi, listerAbonnes, lireSuivi, nombreCourt, type ListeAbonnes, type Suivi } from './abonnes'

/*
 * Sur un profil (#157) : « 128 abonnés · 42 abonnements », chacun ouvre la liste. Sur le profil de quelqu'un d'autre,
 * le bouton « Suivre » / « Abonné ✓ » (un deuxième clic se désabonne), et « Te suit » s'il me suit.
 */
const props = defineProps<{ personneId: string; peutSuivre?: boolean }>()
const { t, locale } = useI18n()

const suivi = ref<Suivi | null>(null)
const occupe = ref(false)
const erreur = ref(false)
const liste = ref<{ sens: 'abonnes' | 'abonnements'; etat: 'chargement' | 'pret' | 'indisponible'; donnees: ListeAbonnes | null } | null>(null)
const fermer = ref<HTMLButtonElement | null>(null)
let declencheur: HTMLElement | null = null

async function charger() {
  try { suivi.value = await lireSuivi(props.personneId) } catch { suivi.value = null }
}
onMounted(charger)
watch(() => props.personneId, () => { liste.value = null; void charger() })

async function basculer() {
  if (!suivi.value || occupe.value) return
  occupe.value = true
  erreur.value = false
  try { suivi.value = await changerSuivi(props.personneId, !suivi.value.jeSuis) } catch { erreur.value = true } finally { occupe.value = false }
}

async function ouvrir(sens: 'abonnes' | 'abonnements', e: Event) {
  declencheur = e.currentTarget as HTMLElement
  liste.value = { sens, etat: 'chargement', donnees: null }
  void nextTick(() => fermer.value?.focus())
  try {
    const donnees = await listerAbonnes(props.personneId, sens)
    if (liste.value?.sens === sens) liste.value = { sens, etat: 'pret', donnees }
  } catch {
    if (liste.value?.sens === sens) liste.value = { sens, etat: 'indisponible', donnees: null }
  }
}

function fermerListe() {
  liste.value = null
  declencheur?.focus()
}
</script>

<template>
  <div v-if="suivi" class="abonnes" data-test="abonnes-profil">
    <button v-if="peutSuivre" type="button" class="suivre" :class="{ 'suivre--abonne': suivi.jeSuis }" :aria-pressed="suivi.jeSuis" :disabled="occupe" :aria-busy="occupe || undefined" data-test="suivre" @click="basculer">
      <svg v-if="!suivi.jeSuis" viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M10 4.5v11M4.5 10h11" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
      <svg v-else viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="m5 10.5 3 3 7-7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
      {{ suivi.jeSuis ? t('abonnes.abonne') : t('abonnes.suivre') }}
    </button>
    <p class="compteurs">
      <button type="button" class="compteur" data-test="voir-abonnes" @click="ouvrir('abonnes', $event)">
        <strong>{{ nombreCourt(suivi.abonnes, locale) }}</strong> {{ t('abonnes.abonnes', suivi.abonnes) }}
      </button>
      <span aria-hidden="true">·</span>
      <button type="button" class="compteur" data-test="voir-abonnements" @click="ouvrir('abonnements', $event)">
        <strong>{{ nombreCourt(suivi.abonnements, locale) }}</strong> {{ t('abonnes.abonnements', suivi.abonnements) }}
      </button>
      <span v-if="peutSuivre && suivi.meSuit" class="te-suit" data-test="te-suit">{{ t('abonnes.teSuit') }}</span>
    </p>
    <p v-if="erreur" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>

    <div v-if="liste" class="voile" @click.self="fermerListe" @keydown.esc="fermerListe">
      <section class="liste" role="dialog" aria-modal="true" aria-labelledby="liste-abonnes-titre" data-test="liste-abonnes">
        <header class="liste__tete">
          <h2 id="liste-abonnes-titre">{{ liste.sens === 'abonnes' ? t('abonnes.titreAbonnes') : t('abonnes.titreAbonnements') }}</h2>
          <button ref="fermer" type="button" class="liste__fermer" :aria-label="t('abonnes.fermer')" @click="fermerListe">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
          </button>
        </header>
        <p v-if="liste.etat === 'chargement'" class="liste__vide" role="status">{{ t('abonnes.chargement') }}</p>
        <p v-else-if="liste.etat === 'indisponible'" class="liste__vide" role="alert">{{ t('etat.indisponible') }}</p>
        <p v-else-if="!liste.donnees?.elements.length" class="liste__vide">{{ liste.sens === 'abonnes' ? t('abonnes.aucunAbonne') : t('abonnes.aucunAbonnement') }}</p>
        <ul v-else class="liste__personnes">
          <li v-for="a in liste.donnees.elements" :key="a.personne.id">
            <RouterLink :to="`/p/${a.personne.id}`" class="personne" @click="liste = null">
              <JAvatar :nom="a.personne.nom" :url="a.personne.avatarUrl" :taille="40" />
              <span class="personne__texte"><strong>{{ a.personne.nom }}</strong><span v-if="a.personne.ville">{{ a.personne.ville }}</span></span>
            </RouterLink>
          </li>
        </ul>
        <p v-if="liste.donnees && liste.donnees.total > liste.donnees.elements.length" class="liste__plus">
          {{ t('abonnes.etDautres', { n: liste.donnees.total - liste.donnees.elements.length }) }}
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.abonnes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.suivre { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-5); border: 1px solid var(--action); border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 700; cursor: pointer; }
.suivre--abonne { background: transparent; color: var(--action-ink); }
.suivre:hover:not(:disabled) { filter: brightness(1.05); }
.suivre:disabled { opacity: 0.6; cursor: wait; }
.suivre:focus-visible, .compteur:focus-visible, .liste__fermer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.compteurs { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin: 0; color: var(--ink-muted); }
.compteur { padding: 2px 4px; border: 0; border-radius: 6px; background: transparent; color: var(--ink-muted); font: inherit; cursor: pointer; }
.compteur strong { color: var(--ink); }
.compteur:hover { color: var(--action-ink); text-decoration: underline; }
.te-suit { padding: 1px 8px; border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted); font-size: 0.8125rem; font-weight: 600; }
.erreur { width: 100%; margin: 0; color: var(--danger); font-weight: 600; }
.voile { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: var(--space-4); background: rgb(2 7 10 / 0.45); }
.liste { display: grid; gap: var(--space-3); grid-template-rows: auto 1fr auto; width: min(26rem, 100%); max-height: min(34rem, 90vh); padding: var(--space-4); border-radius: 20px; background: var(--surface); color: var(--ink); box-shadow: 0 24px 60px rgb(0 0 0 / 0.3); }
.liste__tete { display: flex; align-items: center; justify-content: space-between; }
.liste__tete h2 { margin: 0; font-size: 1.125rem; }
.liste__fermer { display: grid; place-items: center; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink); cursor: pointer; }
.liste__fermer:hover { background: var(--surface-raised); }
.liste__personnes { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; overflow-y: auto; min-height: 0; }
.personne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2); border-radius: 12px; color: var(--ink); text-decoration: none; }
.personne:hover, .personne:focus-visible { background: var(--surface-raised); }
.personne__texte { display: grid; min-width: 0; }
.personne__texte strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.personne__texte span { color: var(--ink-muted); font-size: 0.8125rem; }
.liste__vide, .liste__plus { margin: 0; color: var(--ink-muted); text-align: center; padding: var(--space-3); }
</style>
