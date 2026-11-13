<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { lireCercles, type CercleResume } from './cercles'

/* Mes cercles, puis ceux qui cherchent encore des membres (contracts/cercles.yaml, `portee`). */
const { t } = useI18n()
const mes = ref<CercleResume[]>([])
const ouverts = ref<CercleResume[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    const [a, b] = await Promise.all([lireCercles('mes'), lireCercles('ouverts')])
    mes.value = a
    // Un cercle ouvert dont je suis déjà membre est dans « mes cercles » : on ne le montre pas deux fois.
    ouverts.value = b.filter((c) => !a.some((x) => x.id === c.id))
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()
</script>

<template>
  <section class="cercles">
    <header class="page-tete motif motif--reseau">
      <div class="page-tete__texte">
        <h1>{{ t('cercles.titre') }}</h1>
        <p>{{ t('cercles.intro') }}</p>
        <RouterLink to="/cercles/nouveau" class="j-bouton j-bouton--principal creer" data-test="nouveau">{{ t('cercles.nouveau.titre') }}</RouterLink>
      </div>
    </header>

    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else>
      <section v-for="groupe in [{ cle: 'mes', liste: mes }, { cle: 'ouverts', liste: ouverts }]" :key="groupe.cle" class="groupe" :aria-labelledby="`groupe-${groupe.cle}`">
        <h2 :id="`groupe-${groupe.cle}`">{{ t(`cercles.groupes.${groupe.cle}`) }}</h2>
        <p v-if="!groupe.liste.length" class="texte-discret" :data-test="`vide-${groupe.cle}`">{{ t(`cercles.vide.${groupe.cle}`) }}</p>
        <ul v-else class="grille-cartes" :data-test="`cercles-${groupe.cle}`">
          <li v-for="c in groupe.liste" :key="c.id">
            <RouterLink :to="`/cercles/${c.id}`" class="carte carte-cliquable resume">
              <span class="resume__haut">
                <strong>{{ c.nom }}</strong>
                <span class="resume__statut" :class="`resume__statut--${c.statut}`">{{ t(`cercles.statut.${c.statut}`) }}</span>
              </span>
              <span class="texte-discret">{{ c.ville }} · {{ t('cercles.membres', { n: c.nombreMembres, taille: c.taille }) }}</span>
              <!-- Les places du cercle : pleines ou libres, on voit d'un coup d'œil s'il reste de la place. -->
              <span class="resume__places" aria-hidden="true">
                <i v-for="n in c.taille" :key="n" :class="{ pleine: n <= c.nombreMembres }" />
              </span>
              <span class="resume__createur">
                <JAvatar :nom="c.createur.nom" :url="c.createur.avatarUrl" :taille="24" />
                {{ t('cercles.creePar', { nom: c.createur.nom }) }}
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.cercles { display: grid; gap: var(--space-8); }
.cercles ul { list-style: none; margin: 0; padding: 0; }
.creer { margin-top: var(--space-4); justify-self: start; width: fit-content; }
.groupe h2 { margin-bottom: var(--space-4); }
.resume { display: grid; gap: var(--space-2); padding: var(--space-6); color: var(--ink); text-decoration: none; height: 100%; }
.resume__haut { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); font-size: 1.125rem; }
.resume__statut { padding: 2px var(--space-3); border-radius: var(--radius-pill); font-size: var(--type-body-sm-size); font-weight: 600; background: var(--action-soft); color: var(--action-ink); white-space: nowrap; }
.resume__statut--en_cours { background: var(--proof); color: var(--on-proof); }
.resume__places { display: flex; gap: 6px; margin-top: var(--space-1); }
.resume__places i { width: 14px; height: 14px; border-radius: 50%; border: 2px dashed var(--line-strong); }
.resume__places i.pleine { border: 0; background: var(--action); }
.resume__createur { display: flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
