<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { components } from '../../api/generated/communautes'
import { JBouton } from '../../design-system'
import { nomPays } from '../systalink/calculs'
import { monogramme } from './communautes'
import { etiquettesDe, teinteDe } from './identite'
import TextureCommunaute from './TextureCommunaute.vue'
import ChoixCouverture from './ChoixCouverture.vue'
import { api } from '../../api/client'
import type { PieceJointe } from '../../components/texte/fichiers'

type Communaute = components['schemas']['Communaute']

/*
 * La bannière d'une communauté : sa photo de couverture si elle en a une ; sinon son motif, avec son monogramme en
 * grand, des rubans aux couleurs de la charte et des étiquettes posées comme des autocollants (dessinés en CSS et SVG,
 * rien à charger). Un modérateur change la couverture ou revient au motif depuis la bannière.
 * `apercu` : à la création, le choix de la couverture est fait par la page, pas encore enregistré.
 */
const props = defineProps<{ communaute: Communaute; actifs?: number; adhesionEnCours?: boolean; apercu?: boolean }>()
const emit = defineEmits<{ adhesion: []; maj: [communaute: Communaute] }>()

const echec = ref(false)
watch(() => props.communaute.couverture, () => (echec.value = false))
const photo = computed(() => (props.communaute.couverture && !echec.value ? props.communaute.couverture : null))

async function changerCouverture(fichierId: string | null) {
  const { data } = await api.PUT('/api/communautes/{slug}/couverture', { params: { path: { slug: props.communaute.slug } }, body: { fichierId } })
  if (data) emit('maj', data)
}
const { t, locale } = useI18n()

const teinte = computed(() => teinteDe(props.communaute.slug))
const etiquettes = computed(() => etiquettesDe(props.communaute.theme))
/** Les étiquettes sont posées à des endroits et des angles fixes : la bannière ne bouge pas d'une visite à l'autre. */
const POSES = [
  { haut: '12%', droite: '4%', angle: -7 },
  { haut: '60%', droite: '5%', angle: 5 },
  { haut: '16%', droite: '27%', angle: 4 },
  { haut: '64%', droite: '29%', angle: -4 },
]
</script>

<template>
  <header class="banniere" :style="{ '--fond': teinte.fond, '--accent': teinte.accent, '--encre': teinte.encre }" data-test="banniere">
    <div v-if="photo" class="banniere__scene banniere__scene--photo">
      <img :src="photo" alt="" class="banniere__photo" data-test="banniere-photo" @error="echec = true" />
    </div>
    <div v-else class="banniere__scene" aria-hidden="true">
      <TextureCommunaute :slug="communaute.slug" />
      <svg class="banniere__rubans" viewBox="0 0 800 220" preserveAspectRatio="none">
        <path d="M-20 190 C 160 40, 300 260, 470 90 S 760 10, 840 120" fill="none" stroke="var(--accent)" stroke-width="38" stroke-linecap="round" opacity="0.9" />
        <path d="M-40 40 C 120 120, 260 -30, 420 60 S 700 230, 860 170" fill="none" stroke="var(--encre)" stroke-width="14" stroke-linecap="round" opacity="0.22" />
      </svg>
      <span class="banniere__geant">{{ monogramme(communaute.nom) }}</span>
      <span
        v-for="(e, i) in etiquettes"
        :key="e"
        class="autocollant"
        :class="`autocollant--${i % 3}`"
        :style="{ top: POSES[i]?.haut, right: POSES[i]?.droite, transform: `rotate(${POSES[i]?.angle ?? 0}deg)` }"
      >{{ e }}</span>
    </div>

    <ChoixCouverture
      v-if="communaute.moderateur && !apercu"
      :couverture="communaute.couverture"
      discret
      class="banniere__couverture"
      @image="(p: PieceJointe) => changerCouverture(p.id)"
      @motif="changerCouverture(null)"
    />

    <div class="banniere__corps">
      <div class="banniere__texte">
        <h1>{{ communaute.nom }}</h1>
        <p>{{ communaute.description }}</p>
        <ul class="banniere__chiffres">
          <li><strong>{{ communaute.nombreMembres.toLocaleString(locale) }}</strong> {{ t('communautes.chiffres.membres') }}</li>
          <li v-if="actifs"><span class="point-vert" aria-hidden="true" /><strong>{{ actifs }}</strong> {{ t('communautes.banniere.actifs', actifs) }}</li>
          <li><strong>{{ communaute.questionsOuvertes }}</strong> {{ t('communautes.chiffres.questionsOuvertes') }}</li>
          <li v-if="communaute.pays">{{ t('communautes.antenne', { pays: nomPays(communaute.pays, locale) }) }}</li>
        </ul>
      </div>
      <JBouton
        :variante="communaute.membre ? 'secondaire' : 'principal'"
        :occupe="adhesionEnCours"
        data-test="adhesion"
        @click="emit('adhesion')"
      >
        {{ communaute.membre ? t('communautes.banniere.membre') : t('communautes.rejoindre') }}
      </JBouton>
    </div>
  </header>
</template>

<style scoped>
.banniere { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--ombre-carte); overflow: hidden; }
.banniere__scene {
  position: relative; height: clamp(150px, 18vw, 220px); overflow: hidden;
  background:
    radial-gradient(circle at 18% 120%, color-mix(in srgb, var(--accent) 45%, transparent), transparent 55%),
    var(--fond);
}
.banniere__scene--photo { background: var(--surface-raised); }
.banniere__photo { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.banniere__couverture { position: absolute; top: var(--space-3); left: var(--space-3); z-index: 1; }
.banniere__rubans { position: absolute; inset: 0; width: 100%; height: 100%; }
.banniere__geant {
  position: absolute; left: 4%; bottom: -0.18em;
  font-family: var(--font-display); font-weight: 700; font-style: italic; letter-spacing: -0.04em;
  font-size: clamp(5.5rem, 13vw, 10rem); line-height: 1; color: var(--encre);
  text-shadow: 6px 6px 0 color-mix(in srgb, var(--accent) 80%, transparent);
}
.autocollant {
  position: absolute; padding: 6px 14px; border-radius: 12px; white-space: nowrap;
  font-family: var(--font-display); font-weight: 700; font-size: clamp(0.8rem, 1.2vw, 1rem);
  box-shadow: 0 6px 14px rgb(2 7 10 / 0.18);
  transition: transform var(--motion-base) var(--ease-pop);
}
.autocollant--0 { background: #FFFFFF; color: #02070A; }
.autocollant--1 { background: var(--accent); color: var(--fond); }
.autocollant--2 { background: #02070A; color: #FFFFFF; }
.banniere:hover .autocollant { translate: 0 -3px; }
@media (prefers-reduced-motion: reduce) { .autocollant { transition: none; } .banniere:hover .autocollant { translate: none; } }

.banniere__corps { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--space-4); padding: var(--space-4) var(--space-6) var(--space-6); }
.banniere__texte { flex: 1 1 28rem; min-width: 0; }
.banniere h1 { margin: 0; font-size: clamp(1.5rem, 2.6vw, 2.125rem); line-height: 1.1; letter-spacing: -0.02em; }
.banniere__texte > p { margin: var(--space-2) 0 0; max-width: 46rem; color: var(--ink-muted); }
.banniere__chiffres { list-style: none; margin: var(--space-3) 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.banniere__chiffres li {
  display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: var(--radius-pill);
  background: var(--surface-raised); color: var(--ink-muted); font-size: var(--type-body-sm-size);
}
.banniere__chiffres strong { color: var(--ink); font-variant-numeric: tabular-nums; }
.point-vert { width: 8px; height: 8px; border-radius: 50%; background: #23a55a; }

@media (max-width: 860px) {
  .autocollant:nth-of-type(n + 3) { display: none; }
  .banniere__corps { padding: var(--space-4); }
}
</style>
