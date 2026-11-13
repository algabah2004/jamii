<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import { JBouton, JChamp, JDeplier } from '../../design-system'

/*
 * Publier le cercle dans une de mes communautés (contracts/communautes.yaml, `publier`) : une discussion pré-remplie
 * avec le lien du cercle. Dans la publication, ce lien s'affiche en carte « Rejoindre le cercle » (CarteCercleLien).
 */
const props = defineProps<{ cercleId: string; cercleNom: string; ville: string; manquants: number }>()
const { t } = useI18n()

type Communaute = { slug: string; nom: string }
const communautes = ref<Communaute[] | null>(null)
const ouvert = ref(false)
const choix = ref('')
const titre = ref('')
const contenu = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const publiee = ref<{ id: string; nom: string } | null>(null)

const lien = () => `${window.location.origin}/cercles/${props.cercleId}`

async function ouvrir() {
  ouvert.value = true
  erreur.value = null
  publiee.value = null
  titre.value = t('cercles.partage.titreParDefaut', { cercle: props.cercleNom, n: props.manquants }, props.manquants)
  contenu.value = t('cercles.partage.contenuParDefaut', { ville: props.ville, lien: lien() })
  if (communautes.value) return
  const { data } = await api.GET('/api/communautes', { params: { query: { membre: 'moi', limite: 50 } } })
  communautes.value = (data?.elements ?? []).map((c) => ({ slug: c.slug, nom: c.nom }))
  choix.value = communautes.value[0]?.slug ?? ''
}

// Les limites du contrat : un titre de 10 caractères, un texte de 20.
const valide = computed(() => !!choix.value && titre.value.trim().length >= 10 && contenu.value.trim().length >= 20)

async function publier() {
  envoi.value = true
  erreur.value = null
  try {
    const { data } = await api.POST('/api/communautes/{slug}/publications', {
      params: { path: { slug: choix.value } },
      body: { type: 'discussion', titre: titre.value.trim(), contenu: contenu.value.trim() },
    })
    if (data) {
      publiee.value = { id: data.id, nom: communautes.value?.find((c) => c.slug === choix.value)?.nom ?? '' }
      ouvert.value = false
    } else erreur.value = t('cercles.partage.erreur')
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section class="carte partage" aria-labelledby="partage-titre" data-test="partager-cercle">
    <div class="partage__tete">
      <div>
        <h2 id="partage-titre">{{ t('cercles.partage.titre') }}</h2>
        <p class="texte-discret">{{ t('cercles.partage.aide') }}</p>
      </div>
      <JBouton v-if="!ouvert" variante="secondaire" data-test="ouvrir-partage" @click="ouvrir">{{ t('cercles.partage.publier') }}</JBouton>
    </div>
    <p v-if="publiee" role="status" class="succes" data-test="partage-publie">
      {{ t('cercles.partage.publie', { communaute: publiee.nom }) }}
      <RouterLink :to="`/publications/${publiee.id}`">{{ t('cercles.partage.voir') }}</RouterLink>
    </p>
    <JDeplier>
      <form v-if="ouvert" class="partage__form" data-test="form-partage" @submit.prevent="publier">
        <p v-if="communautes && !communautes.length" class="texte-discret" data-test="partage-sans-communaute">
          {{ t('cercles.partage.aucuneCommunaute') }} <RouterLink to="/communautes">{{ t('menu.communautes') }}</RouterLink>
        </p>
        <template v-else>
          <label class="partage__choix">
            <span>{{ t('cercles.partage.communaute') }}</span>
            <select v-model="choix" required>
              <option v-for="c in communautes ?? []" :key="c.slug" :value="c.slug">{{ c.nom }}</option>
            </select>
          </label>
          <JChamp v-model="titre" :libelle="t('cercles.partage.champTitre')" :maxlength="150" requis />
          <JChamp v-model="contenu" :libelle="t('cercles.partage.champTexte')" multiligne :lignes="4" :maxlength="10000" requis />
          <div class="partage__commandes">
            <JBouton type="submit" :desactive="!valide" :occupe="envoi">{{ t('cercles.partage.publier') }}</JBouton>
            <JBouton variante="secondaire" @click="ouvert = false">{{ t('communautes.annuler') }}</JBouton>
          </div>
        </template>
        <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      </form>
    </JDeplier>
  </section>
</template>

<style scoped>
.partage { display: grid; gap: var(--space-3); padding: var(--space-6); }
.partage__tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.partage__tete h2, .partage__tete p { margin: 0; }
.partage__form { display: grid; gap: var(--space-3); }
.partage__choix { display: grid; gap: var(--space-1); font-weight: 600; }
.partage__choix select { min-height: 44px; padding: 0 var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: inherit; font-weight: 400; }
.partage__commandes { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.succes { margin: 0; color: var(--action-ink); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
