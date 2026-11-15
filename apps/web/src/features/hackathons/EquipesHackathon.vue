<script setup lang="ts">
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { inviterMembre, listerEquipes, listerSansEquipe, type Equipe, type Hackathon, type Inscription } from './hackathons'

/*
 * Les équipes d'un hackathon (contracts/hackathons.yaml, `listerEquipes`), et pour le chef d'une équipe incomplète,
 * « Je cherche une équipe » : les participants seuls qu'il peut inviter (`listerSansEquipe`, `inviterMembre`).
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t } = useI18n()

const equipes = ref<Equipe[]>([])
const seuls = ref<Inscription[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const envoi = ref<string | null>(null)
const code = ref<string | null>(null)

const maPlace = computed(() => props.hackathon.moi.equipeId ?? null)
const mienne = computed(() => equipes.value.find((e) => e.id === maPlace.value) ?? null)
const peutInviter = computed(() => (props.hackathon.moi.actions as string[]).includes('inviter') && mienne.value !== null && !mienne.value.complete)
const message = computed(() => {
  if (!code.value) return ''
  const connus = ['equipe_complete', 'deja_inscrit', 'equipes_verrouillees', 'delai_depasse', 'interdit']
  return t(`hackathons.equipes.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

async function charger() {
  try {
    equipes.value = await listerEquipes(props.hackathon.id)
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
    return
  }
  // La liste « je cherche une équipe » est réservée aux participants : un échec la laisse simplement vide.
  seuls.value = peutInviter.value ? await listerSansEquipe(props.hackathon.id).catch(() => []) : []
}
watch(() => [props.hackathon.id, props.hackathon.version, maPlace.value], () => void charger(), { immediate: true })

async function inviter(personne: Inscription) {
  if (!mienne.value || envoi.value) return
  envoi.value = personne.personne.id
  code.value = null
  try {
    const r = await inviterMembre(props.hackathon.id, mienne.value.id, personne.personne.id)
    if (r.ok) await charger()
    else code.value = r.code ?? 'autre'
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = null
  }
}
</script>

<template>
  <section class="carte equipes" aria-labelledby="equipes-titre" data-test="equipes">
    <h2 id="equipes-titre">{{ t('hackathons.equipes.titre') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.equipes.indisponible') }}</p>
    <p v-else-if="!equipes.length" class="texte-discret" data-test="aucune-equipe">{{ t('hackathons.equipes.aucune') }}</p>
    <ul v-else class="liste">
      <li v-for="e in equipes" :key="e.id" :data-mienne="e.id === maPlace" data-test="equipe">
        <p class="tete">
          <strong>{{ e.nom }}</strong>
          <span v-if="e.id === maPlace" class="etiquette" data-test="ma-equipe">{{ t('hackathons.equipes.mienne') }}</span>
          <span class="texte-discret">{{ e.complete ? t('hackathons.equipes.complete') : t('hackathons.equipes.places', { n: e.membres.length, max: e.tailleMax }) }}</span>
        </p>
        <ul class="membres" :aria-label="t('hackathons.equipes.membres', { nom: e.nom })">
          <li v-for="m in e.membres" :key="m.id">
            <LienPersonne :id="m.id" class="personne-lien"><JAvatar :nom="m.nom" :url="m.avatarUrl" :taille="24" /> {{ m.nom }}</LienPersonne>
            <span v-if="m.id === e.chef.id" class="texte-discret">· {{ t('hackathons.equipes.chef') }}</span>
          </li>
        </ul>
      </li>
    </ul>

    <section v-if="peutInviter" aria-labelledby="seuls-titre" data-test="sans-equipe">
      <h3 id="seuls-titre">{{ t('hackathons.equipes.seuls') }}</h3>
      <p v-if="!seuls.length" class="texte-discret" data-test="aucun-seul">{{ t('hackathons.equipes.aucunSeul') }}</p>
      <ul v-else class="seuls">
        <li v-for="s in seuls" :key="s.id" data-test="seul">
          <LienPersonne :id="s.personne.id" class="personne"><JAvatar :nom="s.personne.nom" :url="s.personne.avatarUrl" :taille="24" /> {{ s.personne.nom }}</LienPersonne>
          <span v-if="s.competences.length" class="texte-discret">{{ s.competences.join(', ') }}</span>
          <JBouton variante="secondaire" :occupe="envoi === s.personne.id" data-test="inviter" @click="inviter(s)">{{ t('hackathons.equipes.inviter') }}</JBouton>
        </li>
      </ul>
    </section>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-equipes">{{ message }}</p>
  </section>
</template>

<style scoped>
.equipes { display: grid; gap: var(--space-4); padding: var(--space-8); }
.equipes h2 { margin: 0; font-size: 1.25rem; }
.equipes h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.liste, .membres, .seuls { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.liste { gap: var(--space-3); }
.liste > li { display: grid; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line-strong); border-radius: var(--radius-md); }
.liste > li[data-mienne='true'] { border-color: var(--action); }
.tete { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: baseline; }
.etiquette { margin: 0; }
.membres li, .personne, .personne-lien { display: flex; gap: var(--space-2); align-items: center; }
.seuls li { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
