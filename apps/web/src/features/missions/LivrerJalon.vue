<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { useAction } from './action'
import { LIMITES_SUIVI, lireLiens, livrerJalon, type Jalon, type Mission } from './missions'

/** Le développeur livre un jalon : un message pour le client, et jusqu'à cinq liens `https` (test, dépôt, fichiers). */
const props = defineProps<{ mission: Mission; jalon: Jalon }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t } = useI18n()

const message = ref('')
const liens = ref('')
const { envoi, message: erreur, agir } = useAction((m) => emit('maj', m))

const liensLus = computed(() => lireLiens(liens.value))
const pret = computed(() => message.value.trim().length >= LIMITES_SUIVI.messageMin && liensLus.value !== null)

async function livrer() {
  if (!pret.value || !liensLus.value) return
  const ok = await agir(() => livrerJalon(props.mission.id, props.jalon.id, props.mission.version, message.value.trim(), liensLus.value ?? []))
  if (ok) {
    message.value = ''
    liens.value = ''
  }
}
</script>

<template>
  <form class="livrer" data-test="form-livrer" @submit.prevent="livrer">
    <JChamp
      v-model="message"
      :libelle="t('missions.suivi.livrer.message')"
      :aide="t('missions.suivi.livrer.aideMessage')"
      multiligne
      requis
      :maxlength="LIMITES_SUIVI.messageMax"
    />
    <JChamp
      v-model="liens"
      :libelle="t('missions.suivi.livrer.liens')"
      :aide="t('missions.suivi.livrer.aideLiens')"
      :erreur="liensLus === null ? t('missions.suivi.livrer.liensInvalides') : undefined"
      multiligne
      :lignes="3"
    />
    <div>
      <JBouton type="submit" :occupe="envoi" :desactive="!pret" data-test="livrer">{{ t('missions.suivi.livrer.envoyer') }}</JBouton>
    </div>
    <p v-if="erreur" role="alert" class="erreur" data-test="erreur-livrer">{{ erreur }}</p>
  </form>
</template>

<style scoped>
/* Livrer : un encart teinté dans le jalon, pour qu'on voie que c'est à soi d'agir. */
.livrer { display: grid; gap: var(--space-4); padding: var(--space-5, 20px); border-radius: 14px; border: 1px dashed color-mix(in srgb, var(--action) 50%, var(--line)); background: color-mix(in srgb, var(--action) 5%, var(--surface)); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
