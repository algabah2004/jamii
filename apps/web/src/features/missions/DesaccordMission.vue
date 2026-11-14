<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JDeplier } from '../../design-system'
import { formaterDate, useAction } from './action'
import { LIMITES_FIN, ouvrirDesaccord, trancherDesaccord, type Mission, type Travail } from './missions'

/*
 * Le désaccord d'une mission : une partie l'ouvre (les livraisons sont alors gelées), un modérateur le tranche, et
 * sa décision est montrée aux deux parties (contracts/missions.yaml, `ouvrirDesaccord` et `trancherDesaccord`).
 */
const props = defineProps<{ mission: Mission; travail: Travail }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t, locale } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const peut = (nom: string) => (props.mission.moi.actions as string[]).includes(nom)
const desaccord = computed(() => props.travail.desaccord)
const ouvert = ref(false)
const raison = ref('')
const issue = ref<'terminee' | 'annulee' | null>(null)
const explication = ref('')

async function ouvrir() {
  if (raison.value.trim().length < LIMITES_FIN.desaccordMin) return
  if (await agir(() => ouvrirDesaccord(props.mission.id, props.mission.version, raison.value.trim()))) ouvert.value = false
}

function trancher() {
  const choix = issue.value
  if (!choix || explication.value.trim().length < LIMITES_FIN.desaccordMin) return
  void agir(() => trancherDesaccord(props.mission.id, props.mission.version, choix, explication.value.trim()))
}
</script>

<template>
  <div class="desaccord">
    <section v-if="desaccord" class="note" aria-labelledby="desaccord-titre" data-test="desaccord">
      <h3 id="desaccord-titre">{{ t('missions.fin.desaccord.titre') }}</h3>
      <p class="texte-discret">{{ t(`missions.fin.desaccord.ouvertPar.${desaccord.ouvertPar}`, { date: formaterDate(desaccord.ouvertLe, locale) }) }}</p>
      <p class="texte">{{ desaccord.raison }}</p>
      <template v-if="desaccord.issue">
        <p class="decision" data-test="decision">{{ t(`missions.fin.desaccord.issues.${desaccord.issue}`) }}</p>
        <p class="texte" data-test="explication">{{ desaccord.explication }}</p>
      </template>
      <p v-else class="texte-discret">{{ t('missions.fin.desaccord.enAttente') }}</p>
    </section>

    <template v-if="peut('ouvrir_desaccord')">
      <div>
        <JBouton variante="discret" :aria-expanded="ouvert" data-test="ouvrir-desaccord" @click="ouvert = !ouvert">{{ t('missions.fin.desaccord.ouvrir') }}</JBouton>
      </div>
      <JDeplier>
        <form v-if="ouvert" class="formulaire" data-test="form-desaccord" @submit.prevent="ouvrir">
          <p class="texte-discret">{{ t('missions.fin.desaccord.aide') }}</p>
          <JChamp v-model="raison" :libelle="t('missions.fin.desaccord.raison')" :aide="t('missions.fin.desaccord.aideRaison')" multiligne requis :maxlength="LIMITES_FIN.desaccordMax" />
          <div>
            <JBouton type="submit" :occupe="envoi" :desactive="raison.trim().length < LIMITES_FIN.desaccordMin" data-test="envoyer-desaccord">
              {{ t('missions.fin.desaccord.envoyer') }}
            </JBouton>
          </div>
        </form>
      </JDeplier>
    </template>

    <form v-if="peut('trancher')" class="formulaire" data-test="form-trancher" @submit.prevent="trancher">
      <fieldset>
        <legend>{{ t('missions.fin.trancher.issue') }}</legend>
        <label><input v-model="issue" type="radio" name="issue" value="terminee" /> {{ t('missions.fin.trancher.terminee') }}</label>
        <label><input v-model="issue" type="radio" name="issue" value="annulee" /> {{ t('missions.fin.trancher.annulee') }}</label>
      </fieldset>
      <JChamp v-model="explication" :libelle="t('missions.fin.trancher.explication')" :aide="t('missions.fin.trancher.aide')" multiligne requis :maxlength="LIMITES_FIN.desaccordMax" />
      <div>
        <JBouton type="submit" :occupe="envoi" :desactive="!issue || explication.trim().length < LIMITES_FIN.desaccordMin" data-test="trancher">
          {{ t('missions.fin.trancher.envoyer') }}
        </JBouton>
      </div>
    </form>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-desaccord">{{ message }}</p>
  </div>
</template>

<style scoped>
.desaccord, .formulaire { display: grid; gap: var(--space-4); }
.desaccord h3 { margin: 0; font-size: 1rem; }
.note { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); }
.note p { margin: 0; }
.texte { white-space: pre-line; }
.decision { font-weight: 700; }
fieldset { margin: 0; padding: 0; border: 0; display: grid; gap: var(--space-2); }
legend { font-weight: 600; margin-bottom: var(--space-2); }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
