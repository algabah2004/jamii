<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JBouton, JChamp } from '../../design-system'
import { creerCercle, LIMITES, TAILLES } from './cercles'

/* Créer un cercle : un nom, une ville, combien on sera. Le créateur en est le premier membre. */
const { t } = useI18n()
const router = useRouter()

const nom = ref('')
const ville = ref('')
const taille = ref<number>(6)
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const valide = computed(() => nom.value.trim().length >= LIMITES.nomMin && ville.value.trim().length >= LIMITES.villeMin)

async function creer() {
  if (!valide.value) return
  envoi.value = 'envoi'
  try {
    const r = await creerCercle({ nom: nom.value, ville: ville.value, taille: taille.value })
    if (!r.ok) return void (envoi.value = 'erreur')
    await router.push(`/cercles/${r.cercle.id}`)
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section class="nouveau">
    <p class="retour"><RouterLink to="/cercles">{{ t('cercles.tous') }}</RouterLink></p>
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('cercles.nouveau.titre') }}</h1>
        <p>{{ t('cercles.nouveau.intro') }}</p>
      </div>
    </header>

    <form class="carte formulaire" data-test="nouveau-cercle" @submit.prevent="creer">
      <JChamp v-model="nom" :libelle="t('cercles.nouveau.nom')" :aide="t('cercles.nouveau.nomAide')" :maxlength="LIMITES.nomMax" requis />
      <JChamp v-model="ville" :libelle="t('cercles.nouveau.ville')" :maxlength="LIMITES.villeMax" requis />
      <fieldset class="tailles">
        <legend>{{ t('cercles.nouveau.taille') }}</legend>
        <label v-for="n in TAILLES" :key="n" class="tailles__choix">
          <input v-model="taille" type="radio" name="taille" :value="n" />
          <span>{{ t('cercles.nouveau.personnes', { n }) }}</span>
        </label>
        <p class="texte-discret">{{ t('cercles.nouveau.tailleAide', { n: taille }) }}</p>
      </fieldset>
      <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('cercles.erreurs.autre') }}</p>
      <div>
        <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="creer">{{ t('cercles.nouveau.creer') }}</JBouton>
      </div>
    </form>
  </section>
</template>

<style scoped>
.nouveau { display: grid; gap: var(--space-6); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.formulaire { display: grid; gap: var(--space-6); padding: var(--space-8); max-width: 40rem; }
.tailles { border: 0; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.tailles legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.tailles p { flex-basis: 100%; margin: var(--space-1) 0 0; }
.tailles__choix { position: relative; }
.tailles__choix input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
.tailles__choix span {
  display: inline-flex; align-items: center; min-height: 40px; padding: 0 var(--space-4);
  border: 1px solid var(--line-strong); border-radius: var(--radius-pill); background: var(--surface);
  font-size: var(--type-body-sm-size); font-weight: 600;
  transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.tailles__choix input:checked + span { background: var(--action); border-color: var(--action); color: var(--on-action); }
.tailles__choix input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
