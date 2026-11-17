<script setup lang="ts">
import IconeMission from './IconeMission.vue'
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
  <section class="carte devis m-bloc" :data-ton="peutAccepter ? 'orange' : undefined" aria-labelledby="devis-propose-titre" data-test="devis-propose">
    <header class="m-tete">
      <IconeMission nom="devis" />
      <div><h2 id="devis-propose-titre">{{ t('missions.devis.propose') }}</h2></div>
    </header>
    <p class="personne">
      <JAvatar :nom="travail.developpeur.nom" :url="travail.developpeur.avatarUrl" :taille="32" />
      {{ t('missions.devis.par', { nom: travail.developpeur.nom, date: formaterDate(devis.envoyeLe, locale) }) }}
    </p>

    <ol class="jalons">
      <li v-for="j in jalons" :key="j.id" data-test="jalon-devis">
        <span class="jalons__numero" aria-hidden="true">{{ j.ordre }}</span>
        <div class="jalons__corps">
          <strong>{{ t('missions.suivi.jalon', { n: j.ordre }) }} · {{ j.titre }}</strong>
          <span class="texte-discret">{{ t('missions.devis.delaiJalon', { n: j.delaiJours }, j.delaiJours) }}</span>
          <span v-if="j.description" class="texte">{{ j.description }}</span>
        </div>
        <span class="m-montant jalons__montant">{{ formaterMontant(j.montant, locale) }}</span>
      </li>
    </ol>

    <dl class="faits m-tuiles">
      <div>
        <dt>{{ t('missions.suivi.total') }}</dt>
        <dd class="m-fort" data-test="total-devis">{{ formaterMontant(devis.total, locale) }}</dd>
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
    <p v-else class="note m-note" data-test="attente-client">{{ t('missions.devis.attenteClient') }}</p>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-accepter">{{ message }}</p>
  </section>
</template>

<style scoped>
.devis { display: grid; gap: var(--space-5, 20px); }
.devis h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.personne { margin: 0; display: flex; gap: var(--space-2); align-items: center; }
/* Les jalons en frise : un numéro relié au suivant, le montant à droite. */
.jalons { margin: 0; padding: 0; list-style: none; display: grid; }
.jalons li { position: relative; display: grid; grid-template-columns: auto 1fr auto; gap: var(--space-3); align-items: start; padding-block-end: var(--space-4); }
.jalons li:not(:last-child)::before { content: ''; position: absolute; inset-block: 2rem 0; inset-inline-start: calc(1rem - 1px); inline-size: 2px; background: color-mix(in srgb, var(--action) 35%, var(--line)); }
.jalons__numero { display: grid; place-items: center; inline-size: 2rem; block-size: 2rem; border-radius: 50%; background: var(--action); color: var(--on-action, #fff); font-weight: 800; font-size: .875rem; }
.jalons__corps { display: grid; gap: 2px; padding-block-start: 4px; min-width: 0; }
.jalons__montant { padding-block-start: 4px; white-space: nowrap; }
.texte { margin: 0; white-space: pre-line; }
.texte-discret { margin: 0; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
