<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { lireCercles, type CercleResume } from './cercles'
import { lireCerclesRecommandes, type CercleRecommande } from '../reseau/reseau'

/* Mes cercles, puis ceux qui cherchent encore des membres (contracts/cercles.yaml, `portee`). */
const { t, locale } = useI18n()
const mes = ref<CercleResume[]>([])
const ouverts = ref<CercleResume[]>([])
const pourMoi = ref<CercleRecommande[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')

async function charger() {
  etat.value = 'chargement'
  try {
    // Les recommandations aident sans rien bloquer : si elles ne répondent pas, la page s'affiche sans elles.
    const [a, b, r] = await Promise.all([lireCercles('mes'), lireCercles('ouverts'), lireCerclesRecommandes().catch(() => [])])
    mes.value = a
    pourMoi.value = r
    // Un cercle n'apparaît qu'une fois : dans « mes cercles », sinon dans les recommandations, sinon dans les ouverts.
    ouverts.value = b.filter((c) => !a.some((x) => x.id === c.id) && !r.some((x) => x.cercle.id === c.id))
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

const liste = (mots: readonly string[]) => new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(mots)
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
      <section v-if="pourMoi.length" class="groupe pour-moi" aria-labelledby="groupe-pour-moi">
        <h2 id="groupe-pour-moi">{{ t('reseau.pourToi.titre') }}</h2>
        <p class="texte-discret">{{ t('reseau.pourToi.aide') }}</p>
        <ul class="grille-cartes" data-test="cercles-pour-moi">
          <li v-for="r in pourMoi" :key="r.cercle.id">
            <RouterLink :to="`/cercles/${r.cercle.id}`" class="carte carte-cliquable resume resume--pour-moi">
              <span class="resume__haut">
                <strong>{{ r.cercle.nom }}</strong>
                <span class="resume__statut">{{ t('cercles.membres', { n: r.cercle.nombreMembres, taille: r.cercle.taille }) }}</span>
              </span>
              <span class="texte-discret">{{ r.cercle.ville }}</span>
              <span class="resume__places" aria-hidden="true">
                <i v-for="n in r.cercle.taille" :key="n" :class="{ pleine: n <= r.cercle.nombreMembres }" />
              </span>
              <!-- Pourquoi ce cercle : dit en clair, à partir des codes de l'API. -->
              <span v-for="raison in r.raisons" :key="raison" class="resume__raison">{{ t(`reseau.raisonsCercle.${raison}`, { competences: liste(r.competences) }) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>
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
.resume--pour-moi { border-color: color-mix(in srgb, var(--action) 45%, var(--line)); }
.resume__raison { color: var(--action-ink); font-size: var(--type-body-sm-size); font-weight: 600; }
.resume__raison::before { content: '✓ '; }
.groupe > .texte-discret { margin: calc(-1 * var(--space-2)) 0 var(--space-4); }
.resume__createur { display: flex; align-items: center; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
</style>
