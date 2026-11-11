<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import tokens from '../../../../docs/design/tokens.json'
import { contraste } from './contraste'
import { appliquerMarque, choisirTheme, JBadgePreuve, JBouton, JChamp, themeActuel, type Marque } from '.'

// La page que le jury et l'équipe ouvrent pour voir la charte vivre : tout est lu dans les variables CSS.
const { t } = useI18n()

/** Chaque paire « texte sur fond » que l'interface utilise, avec le seuil qu'elle doit tenir. */
const PAIRES: { texte: string; fond: string; seuil: number }[] = [
  { texte: 'ink', fond: 'bg', seuil: 4.5 },
  { texte: 'ink-muted', fond: 'surface', seuil: 4.5 },
  { texte: 'action-ink', fond: 'surface', seuil: 4.5 },
  { texte: 'action-ink', fond: 'bg', seuil: 4.5 },
  { texte: 'on-action', fond: 'action', seuil: 4.5 },
  { texte: 'on-proof', fond: 'proof', seuil: 4.5 },
  { texte: 'proof-ink', fond: 'surface', seuil: 4.5 },
  { texte: 'danger', fond: 'surface', seuil: 4.5 },
  { texte: 'line-strong', fond: 'surface', seuil: 3 },
  { texte: 'focus', fond: 'bg', seuil: 3 },
]

const marque = ref<Marque>(document.documentElement.dataset.marque === 'partenaire' ? 'partenaire' : 'jamii')
const mesures = ref<{ texte: string; fond: string; seuil: number; rapport: number }[]>([])
const celebration = ref(0)
const champ = ref('')

function mesurer() {
  const style = getComputedStyle(document.documentElement)
  const lire = (nom: string) => style.getPropertyValue(`--${nom}`).trim()
  mesures.value = PAIRES.map((p) => ({ ...p, rapport: contraste(lire(p.texte), lire(p.fond)) }))
}

// Mesure après que le navigateur a appliqué les nouvelles variables.
watch(themeActuel, () => requestAnimationFrame(mesurer))
watch(marque, (v) => {
  appliquerMarque(v)
  mesurer()
})
onMounted(mesurer)

const groupesTypo = tokens.type.groups
const couleurs = tokens.color.tokens.filter((c) => !['midnight', 'electric-blue', 'cantaloupe'].includes(c.name))
</script>

<template>
  <section class="demo">
    <h1>{{ t('designSystem.titre') }}</h1>
    <p>{{ t('designSystem.intro') }}</p>

    <div class="carte reglages">
      <fieldset>
        <legend>{{ t('designSystem.theme') }}</legend>
        <label v-for="v in ['systeme', 'clair', 'sombre'] as const" :key="v">
          <input type="radio" name="theme" :value="v" :checked="themeActuel === v" @change="choisirTheme(v)" /> {{ t(`designSystem.themes.${v}`) }}
        </label>
      </fieldset>
      <fieldset>
        <legend>{{ t('designSystem.marque') }}</legend>
        <label v-for="v in ['jamii', 'partenaire'] as const" :key="v">
          <input v-model="marque" type="radio" name="marque" :value="v" data-test="marque" /> {{ t(`designSystem.marques.${v}`) }}
        </label>
        <small>{{ t('designSystem.marqueAide') }}</small>
      </fieldset>
    </div>

    <h2>{{ t('designSystem.composants') }}</h2>
    <div class="carte grille">
      <div class="rangee">
        <JBouton>{{ t('designSystem.exemples.principal') }}</JBouton>
        <JBouton variante="secondaire">{{ t('designSystem.exemples.secondaire') }}</JBouton>
        <JBouton variante="discret">{{ t('designSystem.exemples.discret') }}</JBouton>
        <JBouton occupe>{{ t('designSystem.exemples.occupe') }}</JBouton>
      </div>
      <div class="rangee">
        <JBadgePreuve :key="celebration" :celebrer="celebration > 0">✓ {{ t('designSystem.exemples.preuve') }}</JBadgePreuve>
        <JBouton variante="discret" @click="celebration++">{{ t('designSystem.exemples.celebrer') }}</JBouton>
        <span class="etiquette">flutter</span>
        <span class="etiquette">mobile-money</span>
      </div>
      <JChamp v-model="champ" :libelle="t('designSystem.exemples.champ')" :aide="t('designSystem.exemples.aide')" />
      <JChamp :libelle="t('designSystem.exemples.champ')" :erreur="t('designSystem.exemples.erreur')" model-value="https://" />
      <div class="proposition-ia">
        <strong>{{ t('designSystem.exemples.ia') }}</strong>
        <p>{{ t('designSystem.exemples.iaTexte') }}</p>
      </div>
    </div>

    <h2>{{ t('designSystem.contrastes') }}</h2>
    <p class="texte-discret">{{ t('designSystem.contrastesAide') }}</p>
    <table class="carte contrastes" data-test="contrastes">
      <thead>
        <tr><th>{{ t('designSystem.paire') }}</th><th>{{ t('designSystem.rapport') }}</th><th>{{ t('designSystem.seuil') }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="m in mesures" :key="m.texte + m.fond">
          <td>
            <span class="echantillon" :style="{ background: `var(--${m.fond})`, color: `var(--${m.texte})`, borderColor: 'var(--line)' }">Aa</span>
            <code>{{ m.texte }}</code> / <code>{{ m.fond }}</code>
          </td>
          <td>{{ m.rapport.toFixed(1) }}:1</td>
          <td :class="m.rapport >= m.seuil ? 'ok' : 'ko'">
            {{ m.rapport >= m.seuil ? '✓' : '✗' }} {{ m.seuil }}:1
          </td>
        </tr>
      </tbody>
    </table>

    <h2>{{ t('designSystem.couleurs') }}</h2>
    <ul class="couleurs">
      <li v-for="c in couleurs" :key="c.name" class="carte">
        <span class="pastille" :style="{ background: `var(--${c.name})` }" />
        <code>--{{ c.name }}</code>
        <small>{{ c.usage }}</small>
      </li>
    </ul>

    <h2>{{ t('designSystem.typographie') }}</h2>
    <div class="carte typo">
      <template v-for="g in groupesTypo" :key="g.name">
        <p
          v-for="s in g.styles"
          :key="s.name"
          :style="{ fontFamily: `var(--font-${g.family})`, fontSize: s.fontSize, lineHeight: s.lineHeight, fontWeight: s.fontWeight }"
        >
          <small><code>{{ s.name }}</code> · {{ s.fontSize }}</small><br />{{ s.sample }}
        </p>
      </template>
    </div>
  </section>
</template>

<style scoped>
.demo { display: grid; gap: var(--space-4); }
.demo h2 { margin-top: var(--space-6); }
.reglages { display: flex; flex-wrap: wrap; gap: var(--space-6); }
fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: var(--space-2); }
legend { font-weight: 600; margin-bottom: var(--space-2); }
.grille { display: grid; gap: var(--space-6); }
.rangee { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.contrastes { width: 100%; border-collapse: collapse; padding: 0; overflow: hidden; display: table; }
.contrastes th, .contrastes td { text-align: start; padding: var(--space-2) var(--space-4); border-bottom: 1px solid var(--line); }
.echantillon { display: inline-block; width: 2.5rem; text-align: center; font-weight: 700; border: 1px solid; border-radius: var(--radius-sm); margin-inline-end: var(--space-2); }
.ok { color: var(--ink); }
.ko { color: var(--danger); font-weight: 700; }
.couleurs { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); }
.couleurs li { display: grid; gap: var(--space-1); padding: var(--space-4); }
.pastille { height: 3rem; border-radius: var(--radius-sm); border: 1px solid var(--line); }
.typo p { margin: 0 0 var(--space-4); overflow-wrap: anywhere; }
code { font-size: 0.875em; }
</style>
