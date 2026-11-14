<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton } from '../../design-system'
import { formaterDate, useAction } from './action'
import { accepterDevis, formaterMontant, type Mission, type Travail } from './missions'

/*
 * Le devis envoyé, pas encore accepté (contracts/missions.yaml, `accepterDevis`) : le client le lit et l'accepte, et
 * la mission est attribuée. Le devis vaut alors contrat ; les autres propositions sont écartées.
 */
const props = defineProps<{ mission: Mission; travail: Travail }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t, locale } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const devis = computed(() => props.travail.devis)
const jalons = computed(() => [...devis.value.jalons].sort((a, b) => a.ordre - b.ordre))
const peutAccepter = computed(() => (props.mission.moi.actions as string[]).includes('accepter_devis'))

const accepter = () => agir(() => accepterDevis(props.mission.id, devis.value.id, props.mission.version))
</script>

<template>
  <section class="carte devis" aria-labelledby="devis-propose-titre" data-test="devis-propose">
    <h2 id="devis-propose-titre">{{ t('missions.devis.propose') }}</h2>
    <p class="personne">
      <JAvatar :nom="travail.developpeur.nom" :url="travail.developpeur.avatarUrl" :taille="28" />
      {{ t('missions.devis.par', { nom: travail.developpeur.nom, date: formaterDate(devis.envoyeLe, locale) }) }}
    </p>

    <ol class="jalons">
      <li v-for="j in jalons" :key="j.id" data-test="jalon-devis">
        <strong>{{ t('missions.suivi.jalon', { n: j.ordre }) }} · {{ j.titre }}</strong>
        <span>{{ formaterMontant(j.montant, locale) }} · {{ t('missions.devis.delaiJalon', { n: j.delaiJours }, j.delaiJours) }}</span>
        <span v-if="j.description" class="texte">{{ j.description }}</span>
      </li>
    </ol>

    <dl class="faits">
      <div>
        <dt>{{ t('missions.suivi.total') }}</dt>
        <dd data-test="total-devis">{{ formaterMontant(devis.total, locale) }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.devis.retouchesComprises') }}</dt>
        <dd>{{ devis.retouchesComprises }}</dd>
      </div>
    </dl>
    <section v-if="devis.conditions" aria-labelledby="conditions-titre">
      <h3 id="conditions-titre">{{ t('missions.devis.conditions') }}</h3>
      <!-- Texte libre du développeur : affiché échappé, avec ses retours à la ligne. -->
      <p class="texte">{{ devis.conditions }}</p>
    </section>

    <template v-if="peutAccepter">
      <p class="texte-discret">{{ t('missions.devis.aideAccepter') }}</p>
      <div>
        <JBouton :occupe="envoi" data-test="accepter-devis" @click="accepter">{{ t('missions.devis.accepter') }}</JBouton>
      </div>
    </template>
    <p v-else class="note" data-test="attente-client">{{ t('missions.devis.attenteClient') }}</p>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-accepter">{{ message }}</p>
  </section>
</template>

<style scoped>
.devis { display: grid; gap: var(--space-4); padding: var(--space-8); }
.devis h2 { margin: 0; font-size: 1.25rem; }
.devis h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.personne { margin: 0; display: flex; gap: var(--space-2); align-items: center; }
.jalons { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.jalons li { display: grid; gap: var(--space-1); padding-inline-start: var(--space-4); border-inline-start: 3px solid var(--line-strong); }
.faits { margin: 0; display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); }
.faits dt { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.faits dd { margin: 0; font-weight: 600; }
.texte { margin: 0; white-space: pre-line; }
.texte-discret { margin: 0; }
.note { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--cantaloupe); background: color-mix(in srgb, var(--surface) 60%, transparent); font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
