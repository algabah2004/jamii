<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar } from '../../design-system'
import { lireConversations, transfererMessage, TRANSFERT_MAX, type Conversation, type Message } from './reseau'

/*
 * Transférer un message (#157) : je choisis jusqu'à 5 de mes conversations, chacune reçoit une copie marquée
 * « Transféré » (et une copie privée de ses fichiers, côté API).
 */
const props = defineProps<{ personneId: string; message: Message }>()
const emit = defineEmits<{ fermer: []; transfere: [n: number] }>()
const { t } = useI18n()

const conversations = ref<Conversation[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const recherche = ref('')
const choisis = ref<string[]>([])
const envoi = ref(false)
const erreur = ref<string | null>(null)
const champ = ref<HTMLInputElement | null>(null)

onMounted(async () => {
  try {
    conversations.value = await lireConversations()
    etat.value = 'pret'
    await nextTick()
    champ.value?.focus()
  } catch {
    etat.value = 'indisponible'
  }
})

const visibles = computed(() => {
  const q = recherche.value.trim().toLocaleLowerCase()
  return conversations.value.filter((c) => !q || c.personne.nom.toLocaleLowerCase().includes(q))
})

function basculer(id: string) {
  if (choisis.value.includes(id)) choisis.value = choisis.value.filter((x) => x !== id)
  else if (choisis.value.length < TRANSFERT_MAX) choisis.value = [...choisis.value, id]
}

async function transferer() {
  if (!choisis.value.length || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await transfererMessage(props.personneId, props.message.id, choisis.value)
    if (r.ok) emit('transfere', choisis.value.length)
    else erreur.value = t(`reseau.erreurs.${r.code ?? 'autreMessage'}`, t('reseau.erreurs.autreMessage'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <div class="voile" @click.self="emit('fermer')" @keydown.esc="emit('fermer')">
    <section class="transfert" role="dialog" aria-modal="true" aria-labelledby="transfert-titre" data-test="transfert">
      <header class="transfert__tete">
        <h2 id="transfert-titre">{{ t('reseau.actions.transfert.titre') }}</h2>
        <button type="button" class="fermer" :aria-label="t('reseau.actions.annuler')" @click="emit('fermer')">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        </button>
      </header>
      <label class="visuellement-cache" for="transfert-recherche">{{ t('reseau.actions.transfert.chercher') }}</label>
      <input id="transfert-recherche" ref="champ" v-model="recherche" type="search" class="recherche" :placeholder="t('reseau.actions.transfert.chercher')" />
      <p v-if="etat === 'indisponible'" role="alert" class="vide">{{ t('etat.indisponible') }}</p>
      <p v-else-if="etat === 'pret' && !visibles.length" class="vide">{{ t('reseau.actions.transfert.aucune') }}</p>
      <ul v-else class="liste" :aria-busy="etat === 'chargement'">
        <li v-for="c in visibles" :key="c.personne.id">
          <label class="choix" :class="{ 'choix--pris': choisis.includes(c.personne.id) }">
            <input
              type="checkbox"
              :checked="choisis.includes(c.personne.id)"
              :disabled="!choisis.includes(c.personne.id) && choisis.length >= TRANSFERT_MAX"
              data-test="choix-transfert"
              @change="basculer(c.personne.id)"
            />
            <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="36" />
            <span class="choix__nom">{{ c.personne.nom }}</span>
          </label>
        </li>
      </ul>
      <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      <footer class="transfert__pied">
        <span class="compte">{{ t('reseau.actions.transfert.compte', { n: choisis.length, max: TRANSFERT_MAX }) }}</span>
        <button type="button" class="envoyer" :disabled="!choisis.length || envoi" :aria-busy="envoi || undefined" data-test="envoyer-transfert" @click="transferer">
          {{ t('reseau.actions.transfert.envoyer') }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.voile { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: var(--space-4); background: rgb(2 7 10 / 0.45); }
.transfert { display: grid; gap: var(--space-3); width: min(26rem, 100%); max-height: min(36rem, 90vh); padding: var(--space-4); border-radius: 20px; background: var(--surface); color: var(--ink); box-shadow: 0 24px 60px rgb(0 0 0 / 0.3); grid-template-rows: auto auto 1fr auto auto; }
.transfert__tete { display: flex; align-items: center; justify-content: space-between; }
.transfert__tete h2 { margin: 0; font-size: 1.125rem; }
.fermer { display: grid; place-items: center; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink); cursor: pointer; }
.fermer:hover { background: var(--surface-raised); }
.recherche { width: 100%; box-sizing: border-box; min-height: 42px; padding: 0 var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-pill); background: var(--bg); color: var(--ink); font: inherit; }
.liste { list-style: none; margin: 0; padding: 0; overflow-y: auto; min-height: 0; display: grid; gap: 2px; align-content: start; }
.choix { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2); border-radius: 12px; cursor: pointer; }
.choix:hover, .choix:focus-within { background: var(--surface-raised); }
.choix--pris { background: var(--action-soft); }
.choix input { width: 18px; height: 18px; accent-color: var(--action); }
.choix__nom { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vide { margin: 0; color: var(--ink-muted); text-align: center; padding: var(--space-4); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.transfert__pied { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.compte { color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.envoyer { min-height: 40px; padding: 0 var(--space-5); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 700; cursor: pointer; }
.envoyer:disabled { opacity: 0.45; cursor: not-allowed; }
.envoyer:focus-visible, .fermer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
