<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import ConversationPage from './ConversationPage.vue'
import { useActivite } from './activite'
import { usePresence } from './presence'
import { lireContacts, lireConversations, quand, type Contact, type Conversation } from './reseau'

const { t, locale } = useI18n()
const route = useRoute()
const activite = useActivite()
const conversations = ref<Conversation[]>([])
const contacts = ref<Contact[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const recherche = ref('')
const filtre = ref<'tous' | 'nonLus' | 'contacts'>('tous')
const erreurContacts = ref(false)
const chargeContacts = ref(false)
const selection = computed(() => String(route.params.personneId ?? ''))
const totalNonLus = computed(() => conversations.value.reduce((n, c) => n + c.nonLus, 0))
const normaliser = (texte: string) => texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase(locale.value)
const correspond = (nom: string) => normaliser(nom).includes(normaliser(recherche.value.trim()))
const visibles = computed(() => conversations.value.filter((c) => correspond(c.personne.nom) && (filtre.value !== 'nonLus' || c.nonLus > 0)))
const contactsVisibles = computed(() => contacts.value.filter((c) => correspond(c.personne.nom)))

async function actualiser() {
  try { conversations.value = await lireConversations() } catch { /* Conserver la liste pendant une panne passagère. */ }
}
async function charger() {
  etat.value = 'chargement'
  try {
    conversations.value = await lireConversations()
    etat.value = 'pret'
  } catch { etat.value = 'indisponible' }
}
async function nouveauxContacts() {
  filtre.value = 'contacts'
  erreurContacts.value = false
  chargeContacts.value = true
  try { contacts.value = await lireContacts('accepte') }
  catch { erreurContacts.value = true }
  finally { chargeContacts.value = false }
}
void charger()
// Un point vert sur l'avatar des personnes en ligne (#157).
const presences = usePresence(() => conversations.value.map((c) => c.personne.id))
watch(() => activite.value?.messagesNonLus, () => void actualiser())
const minuterie = setInterval(() => {
  if (document.visibilityState === 'visible') void actualiser()
}, 30_000)
onBeforeUnmount(() => clearInterval(minuterie))
const apercu = (c: Conversation) =>
  (c.dernierMessage.deMoi ? t('reseau.messages.toi') + ' ' : '') + (c.dernierMessage.invitation && !c.dernierMessage.deMoi ? t('reseau.messages.invitationRecue', { cercle: c.dernierMessage.invitation.cercle.nom }) : c.dernierMessage.supprime ? t('reseau.actions.supprime') : c.dernierMessage.texte || (c.dernierMessage.piecesJointes?.some((p) => p.sorte === 'audio') ? '🎤 ' + t('vocal.message') : ''))
</script>

<template>
  <section class="messagerie" :class="{ 'messagerie--ouverte': selection }" :aria-label="t('reseau.messages.titre')">
    <aside class="boite" :aria-label="t('reseau.messages.conversations')">
      <header class="boite__tete">
        <span class="surtitre">{{ t('reseau.messages.espace') }}</span>
        <div class="boite__titre"><h1>{{ t('reseau.messages.titre') }}</h1><span class="nombre">{{ conversations.length }}</span></div>
        <p>{{ t('reseau.messages.intro') }}</p>
        <button class="nouveau" type="button" data-test="nouveau-message" @click="nouveauxContacts">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
          {{ t('reseau.messages.nouveau') }}
        </button>
      </header>
      <div class="outils">
        <label class="recherche">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
          <input v-model="recherche" type="search" :placeholder="t('reseau.messages.rechercher')" :aria-label="t('reseau.messages.rechercher')" data-test="rechercher-conversation" />
        </label>
        <div class="filtres" :aria-label="t('reseau.messages.filtres')" role="group">
          <button type="button" :aria-pressed="filtre === 'tous'" @click="filtre = 'tous'">{{ t('reseau.messages.toutes') }}</button>
          <button type="button" :aria-pressed="filtre === 'nonLus'" data-test="filtre-non-lus" @click="filtre = 'nonLus'">{{ t('reseau.messages.nonLusCourt') }} <span v-if="totalNonLus">{{ totalNonLus }}</span></button>
          <button type="button" :aria-pressed="filtre === 'contacts'" @click="nouveauxContacts">{{ t('reseau.onglets.contacts') }}</button>
        </div>
      </div>
      <div class="boite__liste">
        <template v-if="filtre === 'contacts'">
          <JSquelette v-if="chargeContacts" :nombre="3" />
          <div v-else-if="erreurContacts" role="alert" class="liste-vide"><p>{{ t('etat.indisponible') }}</p><JBouton variante="secondaire" @click="nouveauxContacts">{{ t('etat.reessayer') }}</JBouton></div>
          <ul v-else-if="contactsVisibles.length" class="conversations" data-test="contacts-messagerie">
            <li v-for="c in contactsVisibles" :key="c.personne.id"><RouterLink :to="`/messages/${c.personne.id}`" class="conversation" :class="{ 'conversation--active': selection === c.personne.id }" :aria-current="selection === c.personne.id ? 'page' : undefined">
              <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="44" /><span class="conversation__texte"><strong>{{ c.personne.nom }}</strong><span class="apercu">{{ c.personne.ville || t('reseau.contacts.ecrire') }}</span></span><span aria-hidden="true">↗</span>
            </RouterLink></li>
          </ul>
          <div v-else class="liste-vide"><p>{{ recherche ? t('reseau.messages.aucunResultat') : t('reseau.contacts.vide') }}</p><RouterLink to="/communautes">{{ t('menu.communautes') }}</RouterLink></div>
        </template>
        <JSquelette v-else-if="etat === 'chargement'" :nombre="4" />
        <div v-else-if="etat === 'indisponible'" role="alert" class="liste-vide"><p>{{ t('etat.indisponible') }}</p><JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton></div>
        <ul v-else-if="visibles.length" class="conversations" data-test="conversations">
          <li v-for="c in visibles" :key="c.personne.id">
            <RouterLink :to="`/messages/${c.personne.id}`" class="conversation" :class="{ 'conversation--active': selection === c.personne.id, 'conversation--nouvelle': c.nonLus }" :aria-current="selection === c.personne.id ? 'page' : undefined">
              <span class="avatar-presence">
                <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="48" />
                <span v-if="presences.get(c.personne.id)?.enLigne" class="point-en-ligne" data-test="en-ligne" :title="t('presence.enLigne')" />
              </span>
              <span class="visuellement-cache" v-if="presences.get(c.personne.id)?.enLigne">{{ t('presence.enLigne') }}</span>
              <span class="conversation__texte"><span class="conversation__haut"><strong>{{ c.personne.nom }}</strong><time :datetime="c.dernierMessage.envoyeLe">{{ quand(c.dernierMessage.envoyeLe, locale) }}</time></span><span class="apercu">{{ apercu(c) }}</span><span v-if="c.dernierMessage.invitation" class="etiquette">{{ t('reseau.messages.invitationCourte') }}</span></span>
              <span v-if="c.nonLus" class="pastille" :aria-label="t('reseau.messages.nonLus', { n: c.nonLus }, c.nonLus)">{{ c.nonLus }}</span>
            </RouterLink>
          </li>
        </ul>
        <div v-else class="liste-vide" data-test="messages-vide"><p>{{ recherche ? t('reseau.messages.aucunResultat') : filtre === 'nonLus' ? t('reseau.messages.toutLu') : t('reseau.messages.vide') }}</p></div>
      </div>
      <footer class="boite__pied"><RouterLink to="/contacts">{{ t('reseau.contacts.mes') }} <span aria-hidden="true">↗</span></RouterLink><RouterLink to="/notifications">{{ t('reseau.onglets.notifications') }} <span v-if="activite?.notificationsNonLues" class="petit-point" /></RouterLink></footer>
    </aside>
    <div class="espace-discussion">
      <ConversationPage v-if="selection" :key="selection" @actualise="actualiser" />
      <div v-else class="bienvenue">
        <span class="bienvenue__repere">{{ t('reseau.messages.prive') }}</span>
        <div class="illustration" aria-hidden="true"><span class="illustration__orbite" /><svg viewBox="0 0 80 80" width="80" height="80"><path d="M15 19h42a8 8 0 0 1 8 8v24a8 8 0 0 1-8 8H35L20 70V59h-5a8 8 0 0 1-8-8V27a8 8 0 0 1 8-8Z" fill="var(--action-soft)" stroke="var(--action)" stroke-width="1.5"/><path d="M24 34h25M24 43h17" stroke="var(--action-ink)" stroke-width="3" stroke-linecap="round"/><circle cx="62" cy="19" r="11" fill="var(--proof)"/><path d="m57 19 3 3 6-6" fill="none" stroke="var(--on-proof)" stroke-width="2" stroke-linecap="round"/></svg></div>
        <span class="surtitre">{{ t('reseau.messages.ensemble') }}</span>
        <h2>{{ t('reseau.messages.bienvenue') }}</h2>
        <p>{{ t('reseau.messages.choisir') }}</p>
        <RouterLink v-if="conversations[0]" :to="`/messages/${conversations[0].personne.id}`" class="j-bouton j-bouton--principal">{{ t('reseau.messages.reprendre') }} <span aria-hidden="true">→</span></RouterLink>
        <JBouton v-else @click="nouveauxContacts">{{ t('reseau.messages.nouveau') }}</JBouton>
        <div class="bienvenue__details"><span>{{ t('reseau.messages.detailMessages') }}</span><span>{{ t('reseau.messages.detailAppels') }}</span><span>{{ t('reseau.messages.detailCercles') }}</span></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.messagerie { display: grid; grid-template-columns: minmax(320px, 380px) minmax(0, 1fr); flex: 1; min-height: 0; gap: 14px; background: transparent; }
.avatar-presence { position: relative; display: inline-flex; flex: none; }
.point-en-ligne { position: absolute; right: 1px; bottom: 1px; width: 13px; height: 13px; border-radius: 50%; background: var(--en-ligne); box-shadow: 0 0 0 2.5px var(--surface); }
.boite { min-width: 0; min-height: 0; display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: 26px; background: var(--surface); overflow: hidden; }
.boite__tete { padding: 28px 24px 20px; }
.surtitre { font-size: 0.8125rem; letter-spacing: 0.13em; text-transform: uppercase; font-weight: 700; color: var(--ink-muted); }
.boite__titre { display: flex; gap: 12px; align-items: center; margin-top: 6px; }
.boite__titre h1 { margin: 0; font-size: 1.875rem; letter-spacing: -0.05em; }
.nombre { background: var(--surface-raised); border: 1px solid var(--line); border-radius: 8px; font-size: 0.875rem; font-weight: 600; padding: 1px 7px; }
.boite__tete p { font-size: 0.9375rem; color: var(--ink-muted); margin: 8px 0 20px; line-height: 1.6; }
.nouveau { display: flex; width: 100%; gap: 8px; justify-content: center; align-items: center; min-height: 42px; background: var(--proof); color: var(--on-proof); border: 0; border-radius: 50px; font: inherit; font-size: 0.9375rem; font-weight: 700; cursor: pointer; min-height: 44px; box-shadow: inset 0 1px 0 color-mix(in srgb, white 35%, transparent), 0 5px 16px color-mix(in srgb, var(--proof) 18%, transparent); }
.outils { padding: 0 16px 16px; border-bottom: 1px solid var(--line); }
.recherche { display: flex; gap: 8px; align-items: center; background: var(--bg); border: 1px solid var(--line); border-radius: 50px; padding: 10px 14px; color: var(--ink-muted); }
.recherche input { width: 100%; min-width: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 0.9375rem; outline: 0; min-height: 20px; padding: 0; box-shadow: none; border-radius: 0; }
.recherche:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
.filtres { display: flex; gap: 4px; margin-top: 12px; border-radius: 50px; background: var(--bg); padding: 4px; }
.filtres button { flex: 1; border: 1px solid transparent; border-radius: 50px; min-height: 36px; padding: 7px 4px; font: inherit; font-size: 0.875rem; color: var(--ink-muted); background: transparent; cursor: pointer; white-space: nowrap; }
.filtres button[aria-pressed="true"] { background: var(--proof-soft); color: var(--proof-ink); border-color: color-mix(in srgb, var(--proof) 30%, var(--line)); font-weight: 700; box-shadow: 0 2px 5px color-mix(in srgb, var(--ink) 5%, transparent); }
.filtres button span { margin-left: 3px; }
.boite__liste { flex: 1; overflow-y: auto; min-height: 0; padding: 8px; }
.conversations { list-style: none; margin: 0; padding: 0; }
.conversation { position: relative; display: flex; gap: 12px; align-items: center; padding: 17px 12px; border: 1px solid transparent; border-radius: 12px; text-decoration: none; color: var(--ink); transition: background 120ms ease; }
.conversation:hover { background: var(--bg); }
.conversation--active { background: var(--proof-soft); border-color: color-mix(in srgb, var(--proof) 35%, var(--line)); box-shadow: inset 3px 0 0 var(--proof); }
.conversation__texte { flex: 1; min-width: 0; display: grid; gap: 5px; font-size: 0.875rem; }
.conversation__haut { display: flex; align-items: baseline; gap: 8px; justify-content: space-between; }
.conversation strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation time { font-size: 0.8125rem; color: var(--ink-muted); white-space: nowrap; }
.apercu { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); font-size: 0.875rem; }
.conversation--nouvelle .apercu { color: var(--ink); font-weight: 600; }
.etiquette { justify-self: start; border: 1px solid var(--line); border-radius: 6px; padding: 1px 6px; font-size: 0.8125rem; color: var(--ink-muted); }
.pastille { background: var(--proof); color: var(--on-proof); border-radius: 50%; min-width: 20px; height: 20px; display: grid; place-items: center; font-size: 0.8125rem; font-weight: 700; }
.liste-vide { padding: 24px 16px; font-size: 0.9375rem; color: var(--ink-muted); line-height: 1.7; }
.boite__pied { display: flex; justify-content: space-between; gap: 8px; padding: 18px 24px; border-top: 1px solid var(--line); }
.boite__pied a { display: inline-flex; align-items: center; gap: 6px; color: var(--ink-muted); text-decoration: none; font-size: 0.875rem; }
.petit-point { width: 6px; height: 6px; background: var(--proof); border-radius: 50%; }
.espace-discussion { min-width: 0; min-height: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 26px; background: var(--bg); }
.bienvenue { position: relative; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px; text-align: center; background: radial-gradient(ellipse at 50% 44%, color-mix(in srgb, var(--proof) 7%, transparent), transparent 65%), var(--bg); }
.bienvenue__repere { position: absolute; top: 28px; left: 32px; font-size: 0.875rem; color: var(--ink-muted); display: flex; align-items: center; gap: 8px; }
.bienvenue__repere::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: var(--proof); }
.illustration { position: relative; width: 160px; height: 160px; display: grid; place-items: center; margin-bottom: 32px; }
.illustration__orbite { position: absolute; inset: 0; border: 1px solid color-mix(in srgb, var(--proof) 18%, transparent); border-radius: 50%; }
.illustration__orbite::after { content: ''; position: absolute; inset: 16px; border: 1px dashed color-mix(in srgb, var(--proof) 22%, transparent); border-radius: 50%; }
.illustration svg { position: relative; --action: var(--proof); --action-soft: var(--proof-soft); --action-ink: var(--proof-ink); }
.bienvenue h2 { max-width: 550px; margin: 12px 0; font-size: clamp(1.75rem, 3vw, 2.75rem); line-height: 1.15; letter-spacing: -0.045em; }
.bienvenue p { max-width: 400px; margin: 0 0 28px; color: var(--ink-muted); font-size: 0.875rem; line-height: 1.8; }
.bienvenue .j-bouton { font-size: 0.9375rem; gap: 16px; }
.bienvenue__details { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px 24px; margin-top: 60px; color: var(--ink-muted); font-size: 0.8125rem; }
.bienvenue .j-bouton--principal { --action: var(--proof); --on-action: var(--on-proof); }
.bienvenue__details span::before { content: '✓'; color: var(--proof-ink); margin-right: 6px; }
@media (max-width: 960px) { .messagerie { grid-template-columns: 320px minmax(0, 1fr); } .bienvenue { padding: 24px; } }
@media (max-width: 720px) { .messagerie { grid-template-columns: 1fr; border-radius: 16px; gap: 0; } .espace-discussion { display: none; } .messagerie--ouverte .boite { display: none; } .messagerie--ouverte .espace-discussion { display: block; } .boite { border-radius: 20px; } .espace-discussion { border-radius: 20px; } .boite__tete { padding: 18px 20px 14px; } .boite__tete p { margin-bottom: 12px; } }
</style>
