<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import AccueilSection from './AccueilSection.vue'

const { t, tm, rt } = useI18n()
const cle = 'accueil.sectionMissions'

const etapes = computed(() => ['1', '2', '3', '4'].map((n) => ({ titre: t(`${cle}.etapes.${n}.titre`), texte: t(`${cle}.etapes.${n}.texte`) })))

/** Les deux côtés d'une mission : ce que chacun y fait. */
const COTES = [
  { qui: 'client', lien: '/missions/nouvelle' },
  { qui: 'developpeur', lien: '/missions' },
] as const
const points = (qui: string) => (tm(`${cle}.cotes.${qui}.points`) as string[]).map((p) => rt(p))

/** L'aperçu : un devis découpé en jalons, chacun à un moment différent du suivi. */
const JALONS = [
  { etat: 'fait', etiquettes: ['paye', 'recu'] },
  { etat: 'en-cours', etiquettes: ['livre'] },
  { etat: 'a-venir', etiquettes: ['aFaire'] },
] as const
</script>

<template>
  <AccueilSection
    id="section-missions"
    teinte="bleu"
    :surtitre="t(`${cle}.surtitre`)"
    :titre="t(`${cle}.titre`)"
    :intro="t(`${cle}.intro`)"
    :titre-etapes="t(`${cle}.titreEtapes`)"
    :etapes="etapes"
  >
    <template #actions>
      <RouterLink to="/missions" class="bouton-teinte" data-test="section-missions-voir">{{ t(`${cle}.voir`) }} →</RouterLink>
      <RouterLink to="/missions/nouvelle" class="lien-teinte">{{ t(`${cle}.publier`) }}</RouterLink>
    </template>

    <template #apercu>
      <div class="devis">
        <span class="devis__entete">
          <span class="pastille-avatar">AD</span>
          <span class="traits"><i class="trait trait--fort" style="width: 75%" /><i class="trait" style="width: 45%" /></span>
          <b class="puce puce--pleine">{{ t(`${cle}.apercu.devis`) }}</b>
        </span>
        <span v-for="(j, i) in JALONS" :key="i" :class="['jalon', `jalon--${j.etat}`]">
          <b class="jalon__rond">{{ j.etat === 'fait' ? '✓' : i + 1 }}</b>
          <span class="traits"><i class="trait trait--fort" :style="{ width: `${70 - i * 12}%` }" /><i class="trait" style="width: 35%" /></span>
          <span class="jalon__puces">
            <b v-for="e in j.etiquettes" :key="e" :class="['puce', { 'puce--pleine': j.etat === 'fait' }]">{{ t(`${cle}.apercu.${e}`) }}</b>
          </span>
        </span>
      </div>
      <span class="propositions">
        <span class="propositions__avatars"><span v-for="a in ['KY', 'FS', 'NW']" :key="a" class="pastille-avatar">{{ a }}</span></span>
        {{ t(`${cle}.apercu.propositions`) }}
      </span>
    </template>

    <ul class="cotes">
      <li v-for="c in COTES" :key="c.qui" class="cote" :data-test="`cote-${c.qui}`">
        <h4>{{ t(`${cle}.cotes.${c.qui}.titre`) }}</h4>
        <ul class="cote__points">
          <li v-for="(p, i) in points(c.qui)" :key="i">{{ p }}</li>
        </ul>
        <RouterLink :to="c.lien" class="cote__lien">{{ t(`${cle}.cotes.${c.qui}.lien`) }} →</RouterLink>
      </li>
    </ul>

    <!-- Ce qu'un ingénieur demandera en premier : où passe l'argent. On le dit sans détour. -->
    <p class="argent" data-test="section-missions-argent">
      <strong>{{ t(`${cle}.argent.titre`) }}</strong> {{ t(`${cle}.argent.texte`) }}
    </p>
  </AccueilSection>
</template>

<style scoped>
.devis {
  position: relative; width: min(100%, 24rem); display: grid; gap: var(--space-3); padding: var(--space-4);
  border-radius: var(--radius-md); background: var(--surface);
  box-shadow: 0 18px 40px color-mix(in srgb, var(--ink) 16%, transparent);
  transform: rotate(-1.5deg);
}
.devis__entete, .jalon { display: flex; align-items: center; gap: var(--space-3); }
.devis__entete { padding-bottom: var(--space-3); border-bottom: 1px solid var(--line); }
.traits { flex: 1; display: grid; gap: 6px; min-width: 0; }
.trait { display: block; height: 7px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--ink) 16%, transparent); }
.trait--fort { height: 9px; background: color-mix(in srgb, var(--ink) 50%, transparent); }
.pastille-avatar {
  flex-shrink: 0; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%;
  background: var(--action-soft); color: var(--action-ink); font-size: 0.75rem; font-weight: 700; border: 2px solid var(--surface);
}
.puce {
  padding: 2px var(--space-2); border-radius: var(--radius-pill); white-space: nowrap;
  font-size: 0.6875rem; font-weight: 700;
  background: var(--action-soft); color: var(--action-ink);
}
.puce--pleine { background: var(--action); color: var(--midnight); }
.jalon__puces { display: flex; gap: 4px; }
.jalon__rond {
  flex-shrink: 0; display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%;
  font-size: 0.75rem; background: var(--surface); color: var(--ink-muted);
  border: 2px solid color-mix(in srgb, var(--ink) 18%, transparent);
}
.jalon--fait .jalon__rond { background: var(--action); border-color: var(--action); color: var(--midnight); }
.jalon--en-cours .jalon__rond { border-color: var(--action); color: var(--action-ink); box-shadow: 0 0 0 4px color-mix(in srgb, var(--action) 20%, transparent); }
.jalon--a-venir { opacity: 0.6; }
/* Une petite carte posée sur le devis : les propositions reçues. */
.propositions {
  position: relative; z-index: 1; justify-self: start; margin: calc(-1 * var(--space-2)) 0 0 4%; display: flex; align-items: center; gap: var(--space-2);
  padding: var(--space-2) var(--space-3); border-radius: var(--radius-pill);
  background: var(--surface); color: var(--ink); font-size: var(--type-body-sm-size); font-weight: 600;
  box-shadow: 0 10px 24px color-mix(in srgb, var(--ink) 16%, transparent);
  transform: rotate(2deg);
}
.propositions__avatars { display: flex; }
.propositions__avatars .pastille-avatar { width: 26px; height: 26px; font-size: 0.625rem; }
.propositions__avatars .pastille-avatar + .pastille-avatar { margin-inline-start: -8px; }

.cotes { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.cote {
  display: grid; gap: var(--space-3); align-content: start; padding: var(--space-6);
  border-radius: var(--radius-lg); background: var(--surface); border: 1px solid var(--line);
  transition: border-color var(--motion-fast) var(--ease-out), box-shadow var(--motion-fast) var(--ease-out);
}
.cote:hover { border-color: color-mix(in srgb, var(--action) 50%, var(--line)); box-shadow: 0 12px 28px color-mix(in srgb, var(--action) 14%, transparent); }
.cote h4 { margin: 0; font-family: var(--font-display); font-size: 1.25rem; }
.cote__points { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.cote__points li { position: relative; padding-inline-start: 1.5rem; color: var(--ink-muted); line-height: 1.5; }
.cote__points li::before { content: '✓'; position: absolute; inset-inline-start: 0; color: var(--action-ink); font-weight: 700; }
.cote__lien { color: var(--action-ink); font-weight: 600; text-underline-offset: 0.25em; }

.argent {
  margin: 0; padding: var(--space-4) var(--space-6); border-radius: var(--radius-md);
  background: var(--action-soft); color: var(--ink); line-height: 1.6;
  border-inline-start: 4px solid var(--action);
}

@media (max-width: 760px) {
  .cotes { grid-template-columns: 1fr; }
  .devis { transform: none; }
}
</style>
