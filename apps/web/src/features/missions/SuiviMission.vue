<script setup lang="ts">
import IconeMission from './IconeMission.vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JChamp } from '../../design-system'
import { formaterDate, useAction } from './action'
import AnnulerMission from './AnnulerMission.vue'
import AvisMission from './AvisMission.vue'
import DesaccordMission from './DesaccordMission.vue'
import JalonSuivi from './JalonSuivi.vue'
import { demarrerMission, formaterMontant, LIMITES_SUIVI, type Mission, type Travail } from './missions'

/*
 * Le travail d'une mission attribuée (contracts/missions.yaml, tag « suivi ») : le développeur retenu, le devis
 * accepté, les informations de départ et chaque jalon. Visible seulement du client, du développeur et d'un modérateur.
 * DESIGN PROVISOIRE, comme le reste du dossier.
 */
const props = defineProps<{ mission: Mission; travail: Travail }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t, locale } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const informations = ref('')
const peutDemarrer = computed(() => (props.mission.moi.actions as string[]).includes('demarrer'))
const jalons = computed(() => [...props.travail.devis.jalons].sort((a, b) => a.ordre - b.ordre))
const acceptes = computed(() => jalons.value.filter((j) => j.statut === 'accepte').length)

function demarrer() {
  if (!informations.value.trim()) return
  void agir(() => demarrerMission(props.mission.id, props.mission.version, informations.value.trim()))
}
</script>

<template>
  <section class="carte suivi m-bloc" aria-labelledby="suivi-titre" data-test="suivi">
    <header class="m-tete">
      <IconeMission nom="suivi" />
      <div><h2 id="suivi-titre">{{ t('missions.suivi.titre') }}</h2></div>
    </header>

    <!-- L'avancement d'un coup d'œil : la part des jalons acceptés. -->
    <div class="m-progression" aria-hidden="true"><span :style="{ inlineSize: `${jalons.length ? (acceptes / jalons.length) * 100 : 0}%` }"></span></div>

    <dl class="faits m-tuiles">
      <div>
        <dt>{{ t('missions.suivi.developpeur') }}</dt>
        <dd class="personne"><JAvatar :nom="travail.developpeur.nom" :url="travail.developpeur.avatarUrl" :taille="28" /> {{ travail.developpeur.nom }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.suivi.total') }}</dt>
        <dd class="m-fort">{{ formaterMontant(travail.devis.total, locale) }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.suivi.avancement') }}</dt>
        <dd class="m-fort" data-test="avancement">{{ t('missions.suivi.jalonsAcceptes', { n: acceptes, total: jalons.length }, acceptes) }}</dd>
      </div>
      <div v-if="travail.demarreLe">
        <dt>{{ t('missions.suivi.demarreLe') }}</dt>
        <dd>{{ formaterDate(travail.demarreLe, locale) }}</dd>
      </div>
    </dl>

    <form v-if="peutDemarrer" class="demarrer m-encart" data-test="form-demarrer" @submit.prevent="demarrer">
      <p>{{ t('missions.suivi.demarrer.intro') }}</p>
      <JChamp
        v-model="informations"
        :libelle="t('missions.suivi.demarrer.informations')"
        :aide="t('missions.suivi.demarrer.aide')"
        multiligne
        requis
        :maxlength="LIMITES_SUIVI.informationsMax"
      />
      <div>
        <JBouton type="submit" :occupe="envoi" :desactive="!informations.trim()" data-test="demarrer">{{ t('missions.suivi.demarrer.envoyer') }}</JBouton>
      </div>
      <p v-if="message" role="alert" class="erreur" data-test="erreur-demarrer">{{ message }}</p>
    </form>
    <p v-else-if="mission.statut === 'attribuee'" class="note m-note" data-test="attente-depart">{{ t('missions.suivi.attenteDepart') }}</p>

    <section v-if="travail.informationsDepart" class="m-encart" aria-labelledby="depart-titre">
      <h3 id="depart-titre">{{ t('missions.suivi.informationsDepart') }}</h3>
      <!-- Texte libre du client : affiché échappé, avec ses retours à la ligne. -->
      <p class="depart" data-test="informations-depart">{{ travail.informationsDepart }}</p>
    </section>

    <p v-if="travail.devis.retouchesComprises > 0" class="texte-discret" data-test="retouches">
      {{ t('missions.suivi.retouchesRestantes', { n: travail.retouchesRestantes, total: travail.devis.retouchesComprises }) }}
    </p>

    <ol class="jalons" :aria-label="t('missions.suivi.jalons')">
      <JalonSuivi v-for="j in jalons" :key="j.id" :mission="mission" :jalon="j" @maj="emit('maj', $event)" />
    </ol>

    <AvisMission :mission="mission" :travail="travail" @maj="emit('maj', $event)" />
    <DesaccordMission :mission="mission" :travail="travail" @maj="emit('maj', $event)" />
    <AnnulerMission :mission="mission" :travail="travail" @maj="emit('maj', $event)" />
  </section>
</template>

<style scoped>
.suivi { display: grid; gap: var(--space-6); }
.suivi h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.personne { display: flex; gap: var(--space-2); align-items: center; }
.demarrer { display: grid; gap: var(--space-4); }
.demarrer p, .depart, .texte-discret { margin: 0; }
.depart { white-space: pre-line; }
.jalons { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-4); }
.m-encart { padding: var(--space-4) var(--space-5, 20px); border-radius: 14px; background: var(--surface-raised); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
