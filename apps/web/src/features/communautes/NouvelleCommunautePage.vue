<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JBouton, JChamp } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import BanniereCommunaute from './BanniereCommunaute.vue'
import { PAYS_ANTENNES, THEMES, type Theme } from './identite'

type Communaute = components['schemas']['Communaute']

/*
 * Créer une communauté (contracts/communautes.yaml v0.4) : un nom, une description, un thème, et un pays pour une antenne.
 * La bannière se dessine en direct à droite. Qui la crée en devient le modérateur ; elle démarre avec trois salons.
 */
const { t, locale } = useI18n()
const router = useRouter()

const nom = ref('')
const description = ref('')
const theme = ref<Theme>('web')
const pays = ref<string>('SN')
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'trop'>('repos')
const valide = computed(() => nom.value.trim().length >= 3 && description.value.trim().length >= 10)

/** Un aperçu : la même bannière que celle de la communauté une fois créée (le nom court change l'identité). */
const apercu = computed<Communaute>(() => ({
  id: 'apercu', slug: (nom.value.trim() || 'nouvelle').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-'),
  nom: nom.value.trim() || t('communautes.nouvelle.nomExemple'), description: description.value.trim() || t('communautes.nouvelle.descriptionExemple'),
  theme: theme.value, pays: theme.value === 'antenne' ? pays.value : null, nombreMembres: 1, questionsOuvertes: 0, membre: true, moderateur: true,
}))

async function creer() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  try {
    const { data, response } = await api.POST('/api/communautes', {
      body: {
        nom: nom.value.trim(), description: description.value.trim(), theme: theme.value,
        ...(theme.value === 'antenne' ? { pays: pays.value } : {}),
      },
    })
    if (!data) return void (envoi.value = response.status === 429 ? 'trop' : 'erreur')
    await router.push({ path: `/communautes/${data.slug}`, query: { salon: 'general' } })
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section class="creation" aria-labelledby="titre-creation">
    <p class="retour"><RouterLink to="/communautes">{{ t('communautes.retour') }}</RouterLink></p>
    <div class="creation__grille">
      <form class="formulaire" data-test="nouvelle-communaute" @submit.prevent="creer">
        <header>
          <h1 id="titre-creation">{{ t('communautes.nouvelle.titre') }}</h1>
          <p class="texte-discret">{{ t('communautes.nouvelle.intro') }}</p>
        </header>
        <JChamp v-model="nom" :libelle="t('communautes.nouvelle.nom')" :aide="t('communautes.nouvelle.nomAide')" :maxlength="80" requis />
        <JChamp v-model="description" :libelle="t('communautes.nouvelle.description')" multiligne :lignes="3" :maxlength="500" requis />
        <fieldset class="themes">
          <legend>{{ t('communautes.nouvelle.theme') }}</legend>
          <label v-for="th in THEMES" :key="th" class="theme">
            <input v-model="theme" type="radio" name="theme" :value="th" />
            <span>{{ t(`communautes.themes.${th}`) }}</span>
          </label>
        </fieldset>
        <label v-if="theme === 'antenne'" class="pays">
          <span>{{ t('communautes.nouvelle.pays') }}</span>
          <select v-model="pays" data-test="pays">
            <option v-for="p in PAYS_ANTENNES" :key="p" :value="p">{{ nomPays(p, locale) }}</option>
          </select>
        </label>
        <ul class="promesses">
          <li>✓ {{ t('communautes.nouvelle.moderateur') }}</li>
          <li>✓ {{ t('communautes.nouvelle.salons') }}</li>
        </ul>
        <p v-if="envoi === 'trop'" role="alert" class="erreur">{{ t('communautes.nouvelle.trop') }}</p>
        <p v-else-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('etat.indisponible') }}</p>
        <div>
          <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="creer">{{ t('communautes.nouvelle.creer') }}</JBouton>
        </div>
      </form>

      <aside class="apercu" :aria-label="t('communautes.nouvelle.apercu')">
        <p class="apercu__titre">{{ t('communautes.nouvelle.apercu') }}</p>
        <BanniereCommunaute :communaute="apercu" />
      </aside>
    </div>
  </section>
</template>

<style scoped>
.creation { display: grid; gap: var(--space-4); padding-top: var(--space-4); }
.retour { margin: 0; font-size: var(--type-body-sm-size); }
.creation__grille { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 32rem) minmax(0, 1fr); align-items: start; }
@media (max-width: 960px) { .creation__grille { grid-template-columns: 1fr; } }
.formulaire {
  display: grid; gap: var(--space-6); padding: var(--space-8);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte);
}
.formulaire h1 { margin: 0; font-size: 1.75rem; }
.formulaire header p { margin: var(--space-2) 0 0; }
.themes { border: 0; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.themes legend { margin-bottom: var(--space-2); font-size: var(--type-label-size); font-weight: var(--type-label-weight); }
.theme { position: relative; }
.theme input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.theme span {
  display: inline-flex; align-items: center; min-height: 36px; padding: 0 var(--space-4); border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong); background: var(--surface); font-size: var(--type-body-sm-size); font-weight: 600;
}
.theme input:checked + span { background: var(--ink); border-color: var(--ink); color: var(--bg); }
.theme input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }
.pays { display: grid; gap: var(--space-2); font-size: var(--type-label-size); font-weight: var(--type-label-weight); }
.pays select { min-height: 44px; padding: 0 var(--space-3); border-radius: var(--radius-md); border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font: inherit; }
.promesses { list-style: none; margin: 0; padding: var(--space-4); display: grid; gap: var(--space-2); border-radius: var(--radius-md); background: var(--surface-raised); font-size: var(--type-body-sm-size); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.apercu { position: sticky; top: calc(64px + var(--space-6)); display: grid; gap: var(--space-2); }
.apercu__titre { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
</style>
