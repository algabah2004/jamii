<script setup lang="ts">
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
  <section class="carte suivi" aria-labelledby="suivi-titre" data-test="suivi">
    <h2 id="suivi-titre">{{ t('missions.suivi.titre') }}</h2>

    <dl class="faits">
      <div>
        <dt>{{ t('missions.suivi.developpeur') }}</dt>
        <dd class="personne"><JAvatar :nom="travail.developpeur.nom" :url="travail.developpeur.avatarUrl" :taille="28" /> {{ travail.developpeur.nom }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.suivi.total') }}</dt>
        <dd>{{ formaterMontant(travail.devis.total, locale) }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.suivi.avancement') }}</dt>
        <dd data-test="avancement">{{ t('missions.suivi.jalonsAcceptes', { n: acceptes, total: jalons.length }, acceptes) }}</dd>
      </div>
      <div v-if="travail.demarreLe">
        <dt>{{ t('missions.suivi.demarreLe') }}</dt>
        <dd>{{ formaterDate(travail.demarreLe, locale) }}</dd>
      </div>
    </dl>

    <form v-if="peutDemarrer" class="demarrer" data-test="form-demarrer" @submit.prevent="demarrer">
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
    <p v-else-if="mission.statut === 'attribuee'" class="note" data-test="attente-depart">{{ t('missions.suivi.attenteDepart') }}</p>

    <section v-if="travail.informationsDepart" aria-labelledby="depart-titre">
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
.suivi { display: grid; gap: var(--space-6); padding: var(--space-8); }
.suivi h2 { margin: 0; font-size: 1.25rem; }
.suivi h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.faits { margin: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr)); }
.faits dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.faits dd { margin: 0; font-weight: 600; }
.personne { display: flex; gap: var(--space-2); align-items: center; }
.demarrer { display: grid; gap: var(--space-4); }
.demarrer p, .depart, .texte-discret { margin: 0; }
.depart { white-space: pre-line; }
.jalons { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-4); }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
