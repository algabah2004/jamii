<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
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
const champ = ref<HTMLInputElement | null>(null)
// Revenir des contacts : la liste glisse depuis la gauche. À l'ouverture de la page, elle est simplement là.
const revenu = ref(false)
function fermerContacts() {
  revenu.value = true
  filtre.value = 'tous'
}
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
  // Le panneau s'ouvre la recherche prête : on tape le nom tout de suite.
  void nextTick(() => champ.value?.focus({ preventScroll: true }))
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
      <!-- Le panneau « Nouveau message » glisse par-dessus la liste : une flèche retour, la recherche, les contacts.
           Une animation CSS à l'apparition seulement : rien n'attend la fin d'une sortie. -->
      <header v-if="filtre === 'contacts'" key="contacts" class="boite__tete boite__tete--panneau entree-droite">
        <button type="button" class="retour" :aria-label="t('reseau.messages.tous')" data-test="fermer-contacts" @click="fermerContacts">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M12.5 4.5 7 10l5.5 5.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <h1>{{ t('reseau.messages.nouveau') }}</h1>
      </header>
      <header v-else key="liste" class="boite__tete" :class="{ 'entree-gauche': revenu }">
        <h1>{{ t('reseau.messages.titre') }}</h1>
        <button class="nouveau" type="button" data-test="nouveau-message" :title="t('reseau.messages.nouveau')" @click="nouveauxContacts">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" /><path d="m13.5 6.5 4 4" stroke="currentColor" stroke-width="1.8" /></svg>
          <span class="visuellement-cache">{{ t('reseau.messages.nouveau') }}</span>
        </button>
      </header>
      <div class="outils">
        <label class="recherche">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
          <input ref="champ" v-model="recherche" type="search" :placeholder="t('reseau.messages.rechercher')" :aria-label="t('reseau.messages.rechercher')" data-test="rechercher-conversation" />
        </label>
        <div v-if="filtre !== 'contacts'" class="filtres" :aria-label="t('reseau.messages.filtres')" role="group">
          <button type="button" :aria-pressed="filtre === 'tous'" @click="filtre = 'tous'">{{ t('reseau.messages.toutes') }}</button>
          <button type="button" :aria-pressed="filtre === 'nonLus'" data-test="filtre-non-lus" @click="filtre = 'nonLus'">{{ t('reseau.messages.nonLusCourt') }}<span v-if="totalNonLus" class="filtres__nombre">{{ totalNonLus }}</span></button>
        </div>
        <p v-else class="outils__titre">{{ t('reseau.onglets.contacts') }}</p>
      </div>
      <div v-if="filtre === 'contacts'" key="contacts" class="boite__liste entree-droite">
        <JSquelette v-if="chargeContacts" :nombre="3" />
        <div v-else-if="erreurContacts" role="alert" class="liste-vide"><p>{{ t('etat.indisponible') }}</p><JBouton variante="secondaire" @click="nouveauxContacts">{{ t('etat.reessayer') }}</JBouton></div>
        <ul v-else-if="contactsVisibles.length" class="conversations" data-test="contacts-messagerie">
          <li v-for="c in contactsVisibles" :key="c.personne.id"><RouterLink :to="`/messages/${c.personne.id}`" class="conversation" :class="{ 'conversation--active': selection === c.personne.id }" :aria-current="selection === c.personne.id ? 'page' : undefined">
            <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="44" /><span class="conversation__texte"><strong>{{ c.personne.nom }}</strong><span class="apercu">{{ c.personne.ville || t('reseau.contacts.ecrire') }}</span></span>
            <svg class="conversation__ecrire" viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M7.5 4.5 13 10l-5.5 5.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </RouterLink></li>
        </ul>
        <div v-else class="liste-vide"><p>{{ recherche ? t('reseau.messages.aucunResultat') : t('reseau.contacts.vide') }}</p><RouterLink to="/communautes">{{ t('menu.communautes') }}</RouterLink></div>
        <RouterLink to="/contacts" class="tous-contacts">{{ t('reseau.contacts.mes') }} <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div v-else key="liste" class="boite__liste" :class="{ 'entree-gauche': revenu }">
        <JSquelette v-if="etat === 'chargement'" :nombre="4" />
        <div v-else-if="etat === 'indisponible'" role="alert" class="liste-vide"><p>{{ t('etat.indisponible') }}</p><JBouton variante="secondaire" @click="charger">{{ t('etat.reessayer') }}</JBouton></div>
        <TransitionGroup v-else-if="visibles.length" tag="ul" name="conversation" class="conversations" data-test="conversations">
          <li v-for="c in visibles" :key="c.personne.id">
            <RouterLink :to="`/messages/${c.personne.id}`" class="conversation" :class="{ 'conversation--active': selection === c.personne.id, 'conversation--nouvelle': c.nonLus }" :aria-current="selection === c.personne.id ? 'page' : undefined">
              <span class="avatar-presence">
                <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="48" />
                <span v-if="presences.get(c.personne.id)?.enLigne" class="point-en-ligne" data-test="en-ligne" :title="t('presence.enLigne')" />
              </span>
              <span class="visuellement-cache" v-if="presences.get(c.personne.id)?.enLigne">{{ t('presence.enLigne') }}</span>
              <span class="conversation__texte"><span class="conversation__haut"><strong>{{ c.personne.nom }}</strong><time :datetime="c.dernierMessage.envoyeLe">{{ quand(c.dernierMessage.envoyeLe, locale) }}</time></span><span class="conversation__bas"><span class="apercu">{{ apercu(c) }}</span><span v-if="c.nonLus" class="pastille" :aria-label="t('reseau.messages.nonLus', { n: c.nonLus }, c.nonLus)">{{ c.nonLus }}</span></span><span v-if="c.dernierMessage.invitation" class="etiquette">{{ t('reseau.messages.invitationCourte') }}</span></span>
            </RouterLink>
          </li>
        </TransitionGroup>
        <div v-else class="liste-vide" data-test="messages-vide"><p>{{ recherche ? t('reseau.messages.aucunResultat') : filtre === 'nonLus' ? t('reseau.messages.toutLu') : t('reseau.messages.vide') }}</p></div>
      </div>
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
/* Messagerie (#157) : deux panneaux flottants, des transitions courtes et douces (ease-out), jamais de saut. */
.messagerie { --doux: var(--ease-out, cubic-bezier(.2,.8,.2,1)); --rebond: var(--ease-pop, cubic-bezier(.34,1.56,.64,1)); display: grid; grid-template-columns: minmax(320px, 380px) minmax(0, 1fr); flex: 1; min-height: 0; gap: 14px; background: transparent; }
.avatar-presence { position: relative; display: inline-flex; flex: none; }
.point-en-ligne { position: absolute; right: 1px; bottom: 1px; width: 13px; height: 13px; border-radius: 50%; background: var(--en-ligne); box-shadow: 0 0 0 2.5px var(--surface); }
.point-en-ligne::after { content: ''; position: absolute; inset: 0; border-radius: inherit; background: var(--en-ligne); animation: onde 2.4s var(--doux) infinite; }

.boite { min-width: 0; min-height: 0; display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: 28px; background: var(--surface); overflow: hidden; box-shadow: 0 1px 2px color-mix(in srgb, var(--ink) 4%, transparent), 0 18px 40px -24px color-mix(in srgb, var(--ink) 18%, transparent); }
.boite__tete { display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 20px 18px 12px 22px; }
.boite__tete h1 { margin: 0; flex: 1; min-width: 0; font-size: 1.625rem; letter-spacing: -0.045em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.boite__tete--panneau { padding-left: 12px; }
.boite__tete--panneau h1 { font-size: 1.25rem; letter-spacing: -0.02em; }
.retour { display: grid; place-items: center; flex: none; width: 40px; height: 40px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink); cursor: pointer; transition: background 160ms var(--doux), transform 220ms var(--rebond); }
.retour:hover { background: var(--bg); transform: translateX(-2px); }
.retour:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.nouveau {
  display: grid; place-items: center; flex: none; width: 44px; height: 44px; padding: 0; border: 0; border-radius: 50%;
  background: var(--proof); color: var(--on-proof); cursor: pointer;
  box-shadow: inset 0 1px 0 color-mix(in srgb, white 35%, transparent), 0 8px 20px -6px color-mix(in srgb, var(--proof) 55%, transparent);
  transition: transform 260ms var(--rebond), box-shadow 260ms var(--doux);
}
.nouveau:hover { transform: translateY(-2px) rotate(-8deg); box-shadow: inset 0 1px 0 color-mix(in srgb, white 35%, transparent), 0 12px 26px -6px color-mix(in srgb, var(--proof) 65%, transparent); }
.nouveau:active { transform: scale(0.92); transition-duration: 90ms; }
.nouveau:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; }

.surtitre { font-size: 0.75rem; letter-spacing: 0.14em; text-transform: uppercase; font-weight: 700; color: var(--ink-muted); }
.outils { padding: 4px 14px 14px; }
.recherche { display: flex; gap: 10px; align-items: center; background: var(--bg); border: 1px solid transparent; border-radius: 16px; padding: 11px 14px; color: var(--ink-muted); transition: background 200ms var(--doux), border-color 200ms var(--doux), box-shadow 200ms var(--doux); }
.recherche:hover { border-color: var(--line); }
.recherche input { width: 100%; min-width: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 0.9375rem; outline: 0; min-height: 20px; padding: 0; box-shadow: none; border-radius: 0; }
.recherche:focus-within { background: var(--surface); border-color: color-mix(in srgb, var(--proof) 45%, var(--line)); box-shadow: 0 0 0 4px color-mix(in srgb, var(--proof) 14%, transparent); color: var(--proof-ink); }

/* Deux filtres en puces : Toutes, Non lues. */
.filtres { display: flex; gap: 6px; margin-top: 12px; }
.filtres button { display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: var(--radius-pill); min-height: 32px; padding: 4px 13px; font: inherit; font-size: 0.8125rem; font-weight: 600; color: var(--ink-muted); background: transparent; cursor: pointer; white-space: nowrap; transition: color 180ms var(--doux), background 180ms var(--doux), border-color 180ms var(--doux); }
.filtres button:hover { color: var(--ink); border-color: var(--line-strong, var(--line)); }
.filtres button[aria-pressed="true"] { color: var(--bg); background: var(--ink); border-color: var(--ink); }
.filtres button:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.filtres__nombre { display: inline-grid; place-items: center; min-width: 18px; height: 18px; padding: 0 5px; border-radius: var(--radius-pill); background: var(--proof); color: var(--on-proof); font-size: 0.6875rem; font-weight: 800; }
.outils__titre { margin: 14px 4px 0; font-size: 0.75rem; letter-spacing: 0.12em; text-transform: uppercase; font-weight: 700; color: var(--ink-muted); }
.tous-contacts { display: inline-flex; gap: 6px; margin: 10px 12px; color: var(--ink-muted); font-size: 0.875rem; font-weight: 600; text-decoration: none; }
.tous-contacts:hover { color: var(--ink); }
.conversation__ecrire { flex: none; color: var(--ink-muted); transition: transform 220ms var(--doux), color 180ms; }
.conversation:hover .conversation__ecrire { transform: translateX(3px); color: var(--proof-ink); }
/* Le panneau des contacts arrive de la droite ; la liste revient de la gauche. */
.entree-droite { animation: depuis-droite 240ms var(--doux) backwards; }
.entree-gauche { animation: depuis-gauche 240ms var(--doux) backwards; }
@keyframes depuis-droite { from { opacity: 0; transform: translateX(16px); } }
@keyframes depuis-gauche { from { opacity: 0; transform: translateX(-16px); } }

.boite__liste { flex: 1; overflow-y: auto; min-height: 0; padding: 6px 8px 8px; border-top: 1px solid var(--line); scrollbar-width: thin; }
.conversations { position: relative; list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
/* La liste se réordonne quand un message arrive : chaque ligne glisse à sa nouvelle place. */
.conversation-move { transition: transform 380ms var(--doux); }
.conversation {
  position: relative; display: flex; gap: 12px; align-items: center; padding: 12px; border-radius: 18px; text-decoration: none; color: var(--ink);
  transition: background 220ms var(--doux), transform 220ms var(--doux), box-shadow 220ms var(--doux);
}
.conversation:hover { background: var(--bg); }
.conversation:active { transform: scale(0.985); transition-duration: 90ms; }
.conversation:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
/* La conversation ouverte : un gris neutre, tiré de l'encre, qui suit le thème. */
.conversation--active, .conversation--active:hover { background: color-mix(in srgb, var(--ink) 7%, var(--surface)); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 10%, transparent); }
.conversation__texte { flex: 1; min-width: 0; display: grid; gap: 3px; font-size: 0.875rem; }
.conversation__bas { display: flex; align-items: center; gap: 8px; min-width: 0; }
.conversation__bas .apercu { flex: 1; min-width: 0; }
.conversation__haut { display: flex; align-items: baseline; gap: 8px; justify-content: space-between; }
.conversation strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.9375rem; }
.conversation time { font-size: 0.75rem; color: var(--ink-muted); white-space: nowrap; font-variant-numeric: tabular-nums; }
.conversation--nouvelle time { color: var(--proof-ink); font-weight: 700; }
.apercu { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-muted); font-size: 0.875rem; }
.conversation--nouvelle .apercu { color: var(--ink); font-weight: 600; }
.etiquette { justify-self: start; margin-top: 2px; border-radius: var(--radius-pill); padding: 1px 9px; background: var(--proof-soft); color: var(--proof-ink); font-size: 0.75rem; font-weight: 700; }
.pastille { flex: none; background: var(--proof); color: var(--on-proof); border-radius: var(--radius-pill); min-width: 22px; height: 22px; padding: 0 6px; display: grid; place-items: center; font-size: 0.75rem; font-weight: 800; box-shadow: 0 4px 10px -3px color-mix(in srgb, var(--proof) 60%, transparent); }
.liste-vide { padding: 24px 16px; font-size: 0.9375rem; color: var(--ink-muted); line-height: 1.7; }
.espace-discussion { position: relative; min-width: 0; min-height: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 28px; background: var(--bg); box-shadow: 0 18px 40px -28px color-mix(in srgb, var(--ink) 20%, transparent); }
.espace-discussion > * { height: 100%; }
.bienvenue { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px; text-align: center; overflow: hidden; background: var(--bg); }
.bienvenue::before, .bienvenue::after { content: ''; position: absolute; border-radius: 50%; filter: blur(60px); pointer-events: none; }
.bienvenue::before { width: 420px; height: 420px; top: 18%; left: 50%; margin-left: -210px; background: color-mix(in srgb, var(--proof) 14%, transparent); animation: derive 14s ease-in-out infinite alternate; }
.bienvenue::after { width: 280px; height: 280px; bottom: 6%; right: 12%; background: color-mix(in srgb, var(--en-ligne) 9%, transparent); animation: derive 18s ease-in-out infinite alternate-reverse; }
.bienvenue > * { position: relative; }
.bienvenue__repere { position: absolute !important; top: 26px; left: 30px; padding: 6px 12px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 70%, transparent); border: 1px solid var(--line); backdrop-filter: blur(10px); font-size: 0.8125rem; font-weight: 600; color: var(--ink-muted); display: flex; align-items: center; gap: 8px; }
.bienvenue__repere::before { content: ''; width: 7px; height: 7px; border-radius: 50%; background: var(--en-ligne); box-shadow: 0 0 0 3px color-mix(in srgb, var(--en-ligne) 20%, transparent); }
.illustration { width: 168px; height: 168px; display: grid; place-items: center; margin-bottom: 28px; }
.illustration__orbite { position: absolute; inset: 0; border-radius: 50%; background: radial-gradient(circle, color-mix(in srgb, var(--surface) 90%, transparent) 40%, transparent 70%); border: 1px solid color-mix(in srgb, var(--proof) 16%, transparent); }
.illustration__orbite::after { content: ''; position: absolute; inset: 16px; border: 1.5px dashed color-mix(in srgb, var(--proof) 28%, transparent); border-radius: 50%; animation: tourne 40s linear infinite; }
.illustration svg { position: relative; --action: var(--proof); --action-soft: var(--proof-soft); --action-ink: var(--proof-ink); filter: drop-shadow(0 12px 18px color-mix(in srgb, var(--proof) 25%, transparent)); animation: flotte 5s ease-in-out infinite; }
.bienvenue h2 { max-width: 560px; margin: 12px 0; font-size: clamp(1.75rem, 3vw, 2.75rem); line-height: 1.1; letter-spacing: -0.05em; text-wrap: balance; }
.bienvenue p { max-width: 400px; margin: 0 0 28px; color: var(--ink-muted); font-size: 0.9375rem; line-height: 1.7; text-wrap: pretty; }
.bienvenue .j-bouton { font-size: 0.9375rem; gap: 12px; transition: transform 260ms var(--rebond), box-shadow 260ms var(--doux); }
.bienvenue .j-bouton:hover { transform: translateY(-2px); }
.bienvenue .j-bouton span { transition: transform 260ms var(--doux); }
.bienvenue .j-bouton:hover span { transform: translateX(4px); }
.bienvenue .j-bouton--principal { --action: var(--proof); --on-action: var(--on-proof); box-shadow: 0 10px 24px -10px color-mix(in srgb, var(--proof) 70%, transparent); }
.bienvenue__details { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 48px; color: var(--ink-muted); font-size: 0.8125rem; }
.bienvenue__details span { padding: 6px 12px; border-radius: var(--radius-pill); background: color-mix(in srgb, var(--surface) 75%, transparent); border: 1px solid var(--line); }
.bienvenue__details span::before { content: '✓'; color: var(--proof-ink); font-weight: 800; margin-right: 6px; }

@keyframes onde { 0% { opacity: 0.6; transform: scale(1); } 70%, 100% { opacity: 0; transform: scale(2.2); } }
@keyframes flotte { 50% { transform: translateY(-7px); } }
@keyframes tourne { to { transform: rotate(360deg); } }
@keyframes derive { to { transform: translate(40px, -24px) scale(1.08); } }
@media (prefers-reduced-motion: reduce) {
  .messagerie *, .messagerie *::before, .messagerie *::after { animation: none !important; transition-duration: 0ms !important; }
}
@media (max-width: 960px) { .messagerie { grid-template-columns: 320px minmax(0, 1fr); } .bienvenue { padding: 24px; } }
@media (max-width: 720px) { .messagerie { grid-template-columns: minmax(0, 1fr); border-radius: 16px; gap: 0; } .espace-discussion { display: none; } .messagerie--ouverte .boite { display: none; } .messagerie--ouverte .espace-discussion { display: block; } .boite { border-radius: 22px; } .espace-discussion { border-radius: 22px; } .boite__tete { padding: 14px 14px 10px 18px; } }
</style>
