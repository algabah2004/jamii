<script setup lang="ts">
import { JBouton, JSquelette } from '../../design-system'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { api } from '../../api/client'
import type { components } from '../../api/generated/communautes'
import { nomPays } from '../systalink/calculs'
import { monogramme } from './communautes'

type Communaute = components['schemas']['Communaute']

const { t, locale } = useI18n()
const communautes = ref<Communaute[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const { data } = await api.GET('/api/communautes', { params: { query: { limite: 50 } } })
    if (!data) throw new Error('communautés indisponibles')
    communautes.value = data.elements ?? []
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

// Les siennes d'abord : c'est là qu'on revient le plus souvent. Puis celles à découvrir.
const groupes = computed(() =>
  [
    { cle: 'miennes', liste: communautes.value.filter((c) => c.membre) },
    { cle: 'aDecouvrir', liste: communautes.value.filter((c) => !c.membre) },
  ].filter((g) => g.liste.length > 0),
)
</script>

<template>
  <section>
    <header class="page-tete">
      <div class="page-tete__texte">
        <h1>{{ t('communautes.titre') }}</h1>
        <p>{{ t('communautes.intro') }}</p>
      </div>
    </header>

    <JSquelette v-if="etat === 'chargement'" forme="cartes" :nombre="4" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else>
      <section v-for="g in groupes" :key="g.cle" class="groupe" :aria-labelledby="`groupe-${g.cle}`">
        <h2 :id="`groupe-${g.cle}`">{{ t(`communautes.groupes.${g.cle}`) }}</h2>
        <ul class="grille-cartes" data-test="communautes">
          <li v-for="c in g.liste" :key="c.id" class="communaute">
            <div class="communaute__tete">
              <span class="communaute__monogramme" :class="{ 'communaute__monogramme--antenne': c.pays }" aria-hidden="true">
                {{ monogramme(c.nom) }}
              </span>
              <span v-if="c.membre" class="etiquette" data-test="deja-membre">✓ {{ t('communautes.tuEnFaisPartie') }}</span>
            </div>
            <h3>
              <!-- Le lien couvre toute la carte (::after), le titre reste le seul nom annoncé. -->
              <RouterLink :to="`/communautes/${c.slug}`" class="communaute__lien">{{ c.nom }}</RouterLink>
            </h3>
            <p v-if="c.pays" class="communaute__antenne">{{ t('communautes.antenne', { pays: nomPays(c.pays, locale) }) }}</p>
            <p class="communaute__description">{{ c.description }}</p>
            <dl class="communaute__chiffres">
              <div>
                <dt>{{ t('communautes.chiffres.membres') }}</dt>
                <dd>{{ c.nombreMembres.toLocaleString(locale) }}</dd>
              </div>
              <div>
                <dt>{{ t('communautes.chiffres.questionsOuvertes') }}</dt>
                <dd :class="{ 'a-aider': c.questionsOuvertes > 0 }">{{ c.questionsOuvertes }}</dd>
              </div>
            </dl>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.groupe + .groupe { margin-top: var(--space-12); }
.groupe h2 { font-size: var(--type-h3-size); line-height: var(--type-h3-line); margin-bottom: var(--space-4); }

.communaute {
  position: relative;
  display: flex; flex-direction: column; gap: var(--space-2);
  padding: var(--space-6);
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg);
  transition: border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.communaute:hover { border-color: var(--action); }
.communaute:has(:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.communaute__tete { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-2); margin-bottom: var(--space-2); }
.communaute__monogramme {
  display: grid; place-items: center;
  width: 48px; height: 48px; border-radius: var(--radius-md);
  background: var(--action-soft); color: var(--action-ink);
  font-family: var(--font-display); font-weight: 700; font-size: 1.125rem; letter-spacing: -0.5px;
}
.communaute__monogramme--antenne { background: var(--surface-raised); color: var(--ink); }
.communaute h3 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.communaute__lien { color: var(--ink); text-decoration: none; }
.communaute__lien::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
.communaute__lien:focus-visible { outline: none; }
.communaute__antenne { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.communaute__description { margin: 0; color: var(--ink-muted); }
.communaute__chiffres { display: flex; gap: var(--space-8); margin: auto 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); }
.communaute__chiffres div { display: flex; flex-direction: column-reverse; }
.communaute__chiffres dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.communaute__chiffres dd { margin: 0; font-family: var(--font-display); font-size: 1.5rem; font-weight: 700; line-height: 1.2; font-variant-numeric: tabular-nums; }
/* Des questions attendent une réponse : en bleu d'action, c'est une invitation à aider (pas une preuve). */
.communaute__chiffres dd.a-aider { color: var(--action-ink); }
</style>
