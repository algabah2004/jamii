<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { useSession } from '../../session'

type Membre = components['schemas']['MembreCommunaute']

/* Les membres d'une communauté (contracts/communautes.yaml v0.3) : la modération, puis les actifs, puis les autres. */
const props = defineProps<{ slug: string }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const membres = ref<Membre[]>([])
const total = ref(0)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const recherche = ref('')

async function charger() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes/{slug}/membres', { params: { path: { slug: props.slug }, query: { limite: 100 } } })
    if (!data) throw new Error('membres indisponibles')
    membres.value = data.elements
    total.value = data.total
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => props.slug, () => void charger(), { immediate: true })

const visibles = computed(() => {
  const q = recherche.value.trim().toLocaleLowerCase(locale.value)
  return q ? membres.value.filter((m) => m.personne.nom.toLocaleLowerCase(locale.value).includes(q)) : membres.value
})
const depuis = (iso: string) => new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric' }).format(new Date(iso))
</script>

<template>
  <section class="membres carte-espace" aria-labelledby="titre-membres" data-test="membres">
    <header class="membres__tete">
      <div>
        <h1 id="titre-membres">{{ t('communautes.barre.membres') }}</h1>
        <p class="texte-discret">{{ t('communautes.membresPage.total', { n: total }, total) }}</p>
      </div>
      <label class="recherche">
        <span class="visuellement-cache">{{ t('communautes.membresPage.chercher') }}</span>
        <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="m13.5 13.5 3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        <input v-model="recherche" type="search" :placeholder="t('communautes.membresPage.chercher')" />
      </label>
    </header>

    <JSquelette v-if="etat === 'chargement'" forme="cartes" :nombre="6" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">
      {{ t('etat.indisponible') }} <JBouton variante="discret" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </p>
    <p v-else-if="!visibles.length" class="texte-discret">{{ t('communautes.membresPage.aucun') }}</p>
    <ul v-else class="grille">
      <li v-for="m in visibles" :key="m.personne.id" class="membre" data-test="membre">
        <span class="membre__avatar">
          <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="56" />
          <span v-if="m.actif" class="membre__actif" :title="t('communautes.membresPage.actif')" />
        </span>
        <strong class="membre__nom">{{ m.personne.nom }}</strong>
        <span class="membre__etiquettes">
          <span v-if="m.role === 'moderateur'" class="pastille pastille--moderation">{{ t('communautes.membresPage.moderation') }}</span>
          <span v-if="m.actif" class="pastille pastille--actif">{{ t('communautes.membresPage.actif') }}</span>
        </span>
        <span class="texte-discret membre__depuis">{{ t('communautes.membresPage.depuis', { date: depuis(m.rejointLe) }) }}</span>
        <RouterLink v-if="moi && m.personne.id !== moi.id" :to="`/messages/${m.personne.id}`" class="membre__ecrire">{{ t('communautes.membresPage.ecrire') }}</RouterLink>
        <span v-else class="membre__ecrire membre__ecrire--moi">{{ t('communautes.membresPage.toi') }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.carte-espace { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); padding: var(--space-6); display: grid; gap: var(--space-6); }
.membres__tete { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--space-4); }
.membres__tete h1 { margin: 0; font-size: 1.5rem; }
.membres__tete p { margin: var(--space-1) 0 0; }
.recherche { display: flex; align-items: center; gap: var(--space-2); min-height: 40px; padding: 0 var(--space-3); border-radius: var(--radius-pill); background: var(--surface-raised); color: var(--ink-muted); }
.recherche input { border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; width: 14rem; max-width: 50vw; }
.recherche:focus-within { outline: 2px solid var(--focus); outline-offset: 1px; }
.grille { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(min(100%, 12.5rem), 1fr)); }
.membre {
  display: grid; justify-items: center; gap: var(--space-2); padding: var(--space-6) var(--space-4) var(--space-4); text-align: center;
  border: 1px solid var(--line); border-radius: var(--radius-lg); background: color-mix(in srgb, var(--surface-raised) 40%, var(--surface));
  transition: transform var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.membre:hover { transform: translateY(-2px); border-color: var(--line-strong); }
@media (prefers-reduced-motion: reduce) { .membre:hover { transform: none; } }
.membre__avatar { position: relative; display: grid; }
.membre__actif { position: absolute; inset-inline-end: 0; bottom: 2px; width: 14px; height: 14px; border-radius: 50%; background: #23a55a; border: 3px solid var(--surface); }
.membre__nom { font-size: 1rem; }
.membre__etiquettes { display: flex; flex-wrap: wrap; justify-content: center; gap: var(--space-1); min-height: 22px; }
.pastille { padding: 2px 10px; border-radius: var(--radius-pill); font-size: 0.75rem; font-weight: 700; }
.pastille--moderation { background: var(--ink); color: var(--bg); }
.pastille--actif { background: color-mix(in srgb, #23a55a 16%, transparent); color: #1a7f45; }
.membre__depuis { font-size: 0.8125rem; }
.membre__ecrire {
  margin-top: var(--space-2); width: 100%; min-height: 36px; display: grid; place-items: center; border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong); color: var(--ink); text-decoration: none; font-weight: 600; font-size: var(--type-body-sm-size);
}
.membre__ecrire:hover { background: var(--ink); color: var(--bg); border-color: var(--ink); }
.membre__ecrire--moi { border-style: dashed; color: var(--ink-muted); }
.membre__ecrire--moi:hover { background: none; color: var(--ink-muted); border-color: var(--line-strong); }
:root[data-theme='sombre'] .pastille--actif { color: #57d68d; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .pastille--actif { color: #57d68d; } }
</style>
