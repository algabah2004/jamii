<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JDeplier } from '../../design-system'
import { useAction } from './action'
import { annulerMission, LIMITES_FIN, maPartie, type Mission, type Travail } from './missions'

/*
 * Annuler une mission (contracts/missions.yaml, `annulerMission`) : d'un commun accord, ou seul dans les deux cas
 * prévus (l'API décide). Si l'autre partie a déjà demandé l'annulation, le bouton la confirme.
 */
const props = defineProps<{ mission: Mission; travail: Travail }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const ouvert = ref(false)
const raison = ref('')
const moi = computed(() => maPartie(props.mission))
const demandeePar = computed(() => props.travail.annulationDemandeePar)
const parAutre = computed(() => demandeePar.value !== null && demandeePar.value !== moi.value)
const peutAnnuler = computed(() => (props.mission.moi.actions as string[]).includes('annuler') && demandeePar.value !== moi.value)
const pret = computed(() => raison.value.trim().length >= LIMITES_FIN.annulationMin)

async function annuler() {
  if (!pret.value) return
  if (await agir(() => annulerMission(props.mission.id, props.mission.version, raison.value.trim()))) {
    raison.value = ''
    ouvert.value = false
  }
}
</script>

<template>
  <div class="annuler">
    <p v-if="demandeePar && mission.statut !== 'annulee'" class="note m-note" data-test="annulation-demandee">
      {{ parAutre ? t(`missions.fin.annulation.demandeePar.${demandeePar}`) : t('missions.fin.annulation.enAttente') }}
    </p>
    <template v-if="peutAnnuler">
      <div>
        <JBouton variante="discret" :aria-expanded="ouvert" data-test="ouvrir-annulation" @click="ouvert = !ouvert">
          {{ parAutre ? t('missions.fin.annulation.confirmer') : t('missions.fin.annulation.ouvrir') }}
        </JBouton>
      </div>
      <JDeplier>
        <form v-if="ouvert" class="formulaire" data-test="form-annulation" @submit.prevent="annuler">
          <p class="texte-discret">{{ parAutre ? t('missions.fin.annulation.aideAccord') : t('missions.fin.annulation.aide') }}</p>
          <JChamp v-model="raison" :libelle="t('missions.fin.annulation.raison')" multiligne requis :lignes="3" :maxlength="LIMITES_FIN.annulationMax" />
          <div>
            <JBouton type="submit" :occupe="envoi" :desactive="!pret" data-test="annuler">{{ t('missions.fin.annulation.envoyer') }}</JBouton>
          </div>
        </form>
      </JDeplier>
    </template>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-annulation">{{ message }}</p>
  </div>
</template>

<style scoped>
.annuler, .formulaire { display: grid; gap: var(--space-4); }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
