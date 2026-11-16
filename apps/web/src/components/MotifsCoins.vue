<script setup lang="ts">
/*
 * Des taches de petits dessins au trait, posées sur les bords d'une page (en haut, sur les côtés, en bas) et qui se
 * fondent dans le fond. Chaque page de la barre a sa planche, avec ce qui lui correspond (docs/design/motifs/page-*.svg).
 * Purement décoratif. La page qui l'accueille porte la classe `avec-motifs` (base.css).
 */
export type Planche = 'communautes' | 'cercles' | 'demandes' | 'missions' | 'hackathons' | 'vitrine' | 'systalink'
defineProps<{ planche: Planche }>()
</script>

<template>
  <div class="motifs-coins" :class="`motifs-coins--${planche}`" aria-hidden="true" data-test="motifs-coins" />
</template>

<style scoped>
.motifs-coins {
  position: absolute; inset: calc(-1 * var(--space-6)) 0 0; z-index: -1; pointer-events: none;
  background-color: var(--action-ink); opacity: 0.24;
  /* Des taches larges sur les deux côtés et une en haut au centre, avec un dessin serré. */
  --tache-1: radial-gradient(46rem 34rem at 0% 0%, #000 18%, transparent 74%);
  --tache-2: radial-gradient(40rem 34rem at 100% 14rem, #000 18%, transparent 72%);
  --tache-3: radial-gradient(40rem 36rem at 0% 55%, #000 18%, transparent 72%);
  --tache-4: radial-gradient(40rem 36rem at 100% 75%, #000 18%, transparent 72%);
  --tache-5: radial-gradient(56rem 20rem at 50% 0%, #000 12%, transparent 72%);
  -webkit-mask-image: var(--planche), var(--tache-1), var(--tache-2), var(--tache-3), var(--tache-4), var(--tache-5);
  mask-image: var(--planche), var(--tache-1), var(--tache-2), var(--tache-3), var(--tache-4), var(--tache-5);
  -webkit-mask-size: 210px, 100% 100%, 100% 100%, 100% 100%, 100% 100%, 100% 100%;
  mask-size: 210px, 100% 100%, 100% 100%, 100% 100%, 100% 100%, 100% 100%;
  -webkit-mask-repeat: repeat, no-repeat, no-repeat, no-repeat, no-repeat, no-repeat;
  mask-repeat: repeat, no-repeat, no-repeat, no-repeat, no-repeat, no-repeat;
  /* Le motif, gardé seulement là où il y a une tache (l'union des cinq). */
  -webkit-mask-composite: source-in, source-over, source-over, source-over, source-over;
  mask-composite: intersect, add, add, add, add;
}
.motifs-coins--communautes { --planche: url('../../../../docs/design/motifs/tech-objets.svg'); }
.motifs-coins--cercles { --planche: url('../../../../docs/design/motifs/page-cercles.svg'); }
.motifs-coins--demandes { --planche: url('../../../../docs/design/motifs/page-demandes.svg'); }
.motifs-coins--missions { --planche: url('../../../../docs/design/motifs/page-missions.svg'); }
.motifs-coins--hackathons { --planche: url('../../../../docs/design/motifs/page-hackathons.svg'); }
.motifs-coins--vitrine { --planche: url('../../../../docs/design/motifs/page-vitrine.svg'); }
.motifs-coins--systalink { --planche: url('../../../../docs/design/motifs/page-systalink.svg'); }
:root[data-theme='sombre'] .motifs-coins { opacity: 0.3; }
@media (prefers-color-scheme: dark) { :root:not([data-theme='clair']) .motifs-coins { opacity: 0.3; } }
@media (prefers-contrast: more) { .motifs-coins { display: none; } }
</style>
