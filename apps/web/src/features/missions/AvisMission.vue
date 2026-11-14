<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { useAction } from './action'
import { donnerAvis, LIMITES_FIN, type Mission, type Travail } from './missions'

/** Les avis d'une mission terminée : chaque partie note l'autre de 1 à 5, une seule fois (contracts/missions.yaml, `donnerAvis`). */
const props = defineProps<{ mission: Mission; travail: Travail }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const note = ref<number | null>(null)
const texte = ref('')
const avis = computed(() => props.travail.avis ?? [])
const peutDonner = computed(() => (props.mission.moi.actions as string[]).includes('donner_avis'))

function envoyer() {
  const choix = note.value
  if (!choix) return
  void agir(() => donnerAvis(props.mission.id, choix, texte.value.trim()))
}
</script>

<template>
  <section v-if="avis.length || peutDonner" class="avis" aria-labelledby="avis-titre" data-test="avis">
    <h3 id="avis-titre">{{ t('missions.fin.avis.titre') }}</h3>
    <ul v-if="avis.length" class="liste">
      <li v-for="a in avis" :key="a.auteur" data-test="un-avis">
        <p class="tete">{{ t(`missions.fin.avis.par.${a.auteur}`) }} · <span data-test="note">{{ t('missions.fin.avis.note', { n: a.note }) }}</span></p>
        <p v-if="a.texte" class="texte">{{ a.texte }}</p>
      </li>
    </ul>

    <form v-if="peutDonner" class="formulaire" data-test="form-avis" @submit.prevent="envoyer">
      <fieldset>
        <legend>{{ t('missions.fin.avis.question') }}</legend>
        <div class="notes">
          <label v-for="n in 5" :key="n"><input v-model="note" type="radio" name="note" :value="n" /> {{ n }}</label>
        </div>
        <small class="texte-discret">{{ t('missions.fin.avis.echelle') }}</small>
      </fieldset>
      <JChamp v-model="texte" :libelle="t('missions.fin.avis.texte')" :aide="t('missions.fin.avis.aide')" multiligne :lignes="3" :maxlength="LIMITES_FIN.avisMax" />
      <div>
        <JBouton type="submit" :occupe="envoi" :desactive="!note" data-test="donner-avis">{{ t('missions.fin.avis.envoyer') }}</JBouton>
      </div>
    </form>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-avis">{{ message }}</p>
  </section>
</template>

<style scoped>
.avis, .formulaire { display: grid; gap: var(--space-4); }
.avis h3 { margin: 0; font-size: 1rem; }
.liste { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.liste p { margin: 0; }
.tete { font-weight: 600; }
.texte { white-space: pre-line; }
fieldset { margin: 0; padding: 0; border: 0; display: grid; gap: var(--space-2); }
legend { font-weight: 600; margin-bottom: var(--space-2); }
.notes { display: flex; flex-wrap: wrap; gap: var(--space-4); }
.notes label { display: inline-flex; gap: var(--space-1); align-items: center; min-height: 44px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
