<script setup lang="ts">
import Composeur from '../../components/texte/Composeur.vue'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import TexteRiche from '../../components/texte/TexteRiche.vue'
import type { PieceJointe } from '../../components/texte/fichiers'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { rejoindreCercle } from '../cercles/cercles'
import { rafraichirActivite } from './activite'
import {
  ajouterContact,
  envoyerMessage,
  lancerAppel,
  LONGUEUR_MESSAGE,
  lireConversation,
  lireLien,
  prenom,
  type Message,
  type Personne,
  type StatutLien,
} from './reseau'

/*
 * La conversation avec une personne, comme une messagerie : l'en-tête avec l'appel, le fil groupé par jour, et la
 * zone de saisie collée en bas (Entrée envoie, Maj + Entrée va à la ligne). Le fil se relit toutes les 5 secondes
 * tant que l'onglet est visible : pas de WebSocket, comme les salons. Les textes restent du texte brut (jamais v-html).
 */
const emit = defineEmits<{ actualise: [] }>()
const { t, locale } = useI18n()
const route = useRoute()
const routeur = useRouter()

const personne = ref<Personne | null>(null)
const messages = ref<Message[]>([])
const etat = ref<'chargement' | 'pret' | 'introuvable' | 'indisponible'>('chargement')
const fil = ref<HTMLElement | null>(null)

async function charger() {
  etat.value = 'chargement'
  try {
    const r = await lireConversation(String(route.params.personneId))
    if (!r) {
      etat.value = 'introuvable'
      return
    }
    personne.value = r.personne
    messages.value = r.messages
    etat.value = 'pret'
    lien.value = await lireLien(r.personne.id).catch(() => null)
    // La lecture a marqué la conversation comme lue : la pastille du menu se met à jour tout de suite.
    void rafraichirActivite()
    emit('actualise')
    await descendre()
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => route.params.personneId, charger, { immediate: true })

/** Relire en silence : les nouveaux messages arrivent sans recharger l'écran. */
async function relire() {
  if (etat.value !== 'pret' || (typeof document !== 'undefined' && document.visibilityState === 'hidden')) return
  try {
    const r = await lireConversation(String(route.params.personneId))
    if (!r) return
    const dernier = messages.value.at(-1)?.id
    const enBas = !fil.value || fil.value.scrollHeight - fil.value.scrollTop - fil.value.clientHeight < 80
    messages.value = r.messages
    if (r.messages.at(-1)?.id !== dernier) {
      emit('actualise')
      void rafraichirActivite()
      if (enBas) await descendre()
    }
  } catch {
    // La prochaine relecture réessaiera.
  }
}
const minuterie = setInterval(() => void relire(), 5000)
onBeforeUnmount(() => clearInterval(minuterie))

async function descendre() {
  await nextTick()
  if (fil.value) fil.value.scrollTop = fil.value.scrollHeight
}

/** Le fil groupé par jour : « Aujourd'hui », « Hier », puis la date. */
const jours = computed(() => {
  const groupes: { cle: string; titre: string; messages: Message[] }[] = []
  for (const m of messages.value) {
    const cle = new Date(m.envoyeLe).toDateString()
    if (groupes.at(-1)?.cle !== cle) groupes.push({ cle, titre: titreJour(m.envoyeLe), messages: [] })
    groupes.at(-1)!.messages.push(m)
  }
  return groupes
})
function titreJour(iso: string) {
  const date = new Date(iso)
  const aujourdhui = new Date()
  const hier = new Date(aujourdhui)
  hier.setDate(aujourdhui.getDate() - 1)
  if (date.toDateString() === aujourdhui.toDateString()) return t('reseau.discussion.aujourdhui')
  if (date.toDateString() === hier.toDateString()) return t('reseau.discussion.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long' }).format(date)
}
const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
/** Deux bulles de suite de la même personne se collent : l'avatar n'est montré qu'une fois. */
const suite = (liste: Message[], i: number) => i > 0 && liste[i - 1]!.deMoi === liste[i]!.deMoi

// Le lien de contact : un seul bouton, qui dit ce qu'il fera (demander, accepter) ou l'état (demande envoyée, contact).
const lien = ref<StatutLien | null>(null)
const ajout = ref(false)
async function ajouter() {
  if (!personne.value) return
  ajout.value = true
  try {
    lien.value = (await ajouterContact(personne.value.id)) ?? lien.value
    void rafraichirActivite()
  } finally {
    ajout.value = false
  }
}

// ---------------------------------------------------------------- écrire
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const pieces = ref<PieceJointe[]>([])
const composeur = ref<InstanceType<typeof Composeur> | null>(null)
const valide = computed(() => (texte.value.trim().length > 0 || pieces.value.length > 0) && texte.value.length <= LONGUEUR_MESSAGE)

async function envoyer() {
  if (!valide.value || !personne.value || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await envoyerMessage(personne.value.id, texte.value, undefined, pieces.value.map((p) => p.id))
    if (r.ok) {
      messages.value.push(r.message)
      emit('actualise')
      texte.value = ''
      pieces.value = []
      composeur.value?.rafraichir()
      await descendre()
      composeur.value?.focus()
    } else erreur.value = t(`reseau.erreurs.${r.code ?? 'autre'}`, t('reseau.erreurs.autreMessage'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    envoi.value = false
  }
}

// ---------------------------------------------------------------- appeler (#157)
const appel = ref<'repos' | 'envoi' | 'erreur'>('repos')
/**
 * L'onglet de l'appel s'ouvre pendant le clic (sinon le navigateur le bloque), puis reçoit l'adresse de la salle quand
 * l'API a répondu. L'appel est annoncé dans la conversation : l'autre personne le rejoint d'un clic.
 */
async function appeler() {
  if (!personne.value || appel.value === 'envoi') return
  appel.value = 'envoi'
  erreur.value = null
  const fenetre = typeof window.open === 'function' ? window.open('', '_blank') : null
  try {
    const r = await lancerAppel(personne.value.id)
    if (!r.ok || !r.message.appel) {
      fenetre?.close()
      appel.value = 'erreur'
      erreur.value = t(`reseau.erreurs.${r.ok ? 'autre' : (r.code ?? 'autre')}`, t('reseau.discussion.appelImpossible'))
      return
    }
    if (fenetre) {
      fenetre.opener = null
      fenetre.location.href = r.message.appel.url
    }
    messages.value.push(r.message)
    emit('actualise')
    appel.value = 'repos'
    await descendre()
  } catch {
    fenetre?.close()
    appel.value = 'erreur'
    erreur.value = t('reseau.discussion.appelImpossible')
  }
}

// Rejoindre depuis l'invitation : la même route que le bouton de la page du cercle (cercles.yaml).
const rejoint = ref<string | null>(null)
const erreurInvitation = ref<string | null>(null)
async function rejoindre(cercleId: string) {
  rejoint.value = cercleId
  erreurInvitation.value = null
  try {
    const r = await rejoindreCercle(cercleId)
    if (r.ok) await routeur.push(`/cercles/${cercleId}`)
    // Déjà membre : on y va quand même, c'est ce que la personne voulait.
    else if (r.code === 'deja_membre') await routeur.push(`/cercles/${cercleId}`)
    else erreurInvitation.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreurInvitation.value = t('etat.indisponible')
  } finally {
    rejoint.value = null
  }
}
const placeLibre = (m: Message) => !!m.invitation && m.invitation.cercle.statut === 'ouvert' && m.invitation.cercle.nombreMembres < m.invitation.cercle.taille
</script>

<template>
  <section class="discussion" :aria-busy="etat === 'chargement'">
    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'introuvable'" class="etat-vide">
      <h1>{{ t('introuvable.titre') }}</h1>
      <RouterLink to="/messages">{{ t('reseau.messages.tous') }}</RouterLink>
    </div>
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <div v-else-if="personne" class="cadre">
      <header class="tete verre">
        <RouterLink to="/messages" class="tete__retour" :aria-label="t('reseau.messages.tous')">
          <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M12.5 4.5 7 10l5.5 5.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </RouterLink>
        <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="44" />
        <div class="tete__qui">
          <h2>{{ personne.nom }}</h2>
          <p>
            <RouterLink :to="`/p/${personne.id}`" class="tete__profil">{{ t('reseau.messages.voirProfil') }}</RouterLink><span v-if="personne.ville"> · {{ personne.ville }}</span>
            <span v-if="lien && lien !== 'aucun' && lien !== 'recue'" class="tete__lien" data-test="lien-contact">{{ t(`reseau.contacts.statut.${lien}`) }}</span>
          </p>
        </div>
        <div class="tete__actions">
          <JBouton v-if="lien === 'aucun' || lien === 'recue'" :variante="lien === 'recue' ? 'principal' : 'secondaire'" :occupe="ajout" data-test="ajouter-contact" @click="ajouter">
            {{ lien === 'recue' ? t('reseau.contacts.accepter') : t('reseau.contacts.ajouter') }}
          </JBouton>
          <button type="button" class="rond rond--appel" :disabled="appel === 'envoi'" :aria-label="t('reseau.discussion.appeler', { nom: prenom(personne.nom) })" :title="t('reseau.discussion.appeler', { nom: prenom(personne.nom) })" data-test="appeler" @click="appeler">
            <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
          </button>
        </div>
      </header>

      <div ref="fil" class="fil" data-test="fil-defilant">
        <div v-if="!messages.length" class="accueil" data-test="conversation-vide">
          <JAvatar :nom="personne.nom" :url="personne.avatarUrl" :taille="72" />
          <strong>{{ personne.nom }}</strong>
          <p>{{ t('reseau.messages.premier', { nom: prenom(personne.nom) }) }}</p>
        </div>

        <ol class="jours" data-test="fil" aria-live="polite">
          <template v-for="j in jours" :key="j.cle">
            <li class="jour" aria-hidden="true"><span>{{ j.titre }}</span></li>
            <li
              v-for="(m, i) in j.messages"
              :key="m.id"
              class="ligne"
              :class="[m.deMoi ? 'bulle--moi' : 'bulle--autre', { 'ligne--suite': suite(j.messages, i) }]"
            >
              <span class="ligne__avatar" aria-hidden="true">
                <JAvatar v-if="!m.deMoi && !suite(j.messages, i)" :nom="personne.nom" :url="personne.avatarUrl" :taille="28" />
              </span>

              <!-- L'annonce d'un appel : une carte, avec « Rejoindre » tant qu'il est en cours. -->
              <div v-if="m.appel" class="appel-carte" :class="{ 'appel-carte--fini': !m.appel.enCours }" data-test="appel">
                <span class="appel-carte__icone" aria-hidden="true">
                  <svg viewBox="0 0 20 20" width="18" height="18"><path d="M6.6 3.5 8.4 7a1 1 0 0 1-.2 1.1L7 9.3a10 10 0 0 0 3.7 3.7l1.2-1.2a1 1 0 0 1 1.1-.2l3.5 1.8a1 1 0 0 1 .5 1.2l-.6 1.9a2 2 0 0 1-2.2 1.4C8.9 17.2 2.8 11.1 2.1 5.8A2 2 0 0 1 3.5 3.6l1.9-.6a1 1 0 0 1 1.2.5Z" fill="currentColor" /></svg>
                </span>
                <span class="appel-carte__texte">
                  <strong>{{ m.deMoi ? t('reseau.discussion.tuAppelles') : t('reseau.discussion.tAppelle', { nom: prenom(personne.nom) }) }}</strong>
                  <span>{{ m.appel.enCours ? t('reseau.discussion.enCours') : t('reseau.discussion.termine') }} · {{ heure(m.envoyeLe) }}</span>
                </span>
                <a v-if="m.appel.enCours" :href="m.appel.url" target="_blank" rel="noopener noreferrer" class="appel-carte__rejoindre" data-test="rejoindre-appel">
                  {{ t('reseau.discussion.rejoindre') }}<span class="visuellement-cache"> {{ t('communautes.salons.nouvelOnglet') }}</span>
                </a>
              </div>

              <div v-else class="bulle">
                <TexteRiche v-if="m.texte" class="bulle__texte" :texte="m.texte" />
                <PiecesJointes :pieces="m.piecesJointes ?? []" />
                <!-- L'invitation : le cercle tel qu'il est maintenant, et le bouton pour le rejoindre s'il reste de la place. -->
                <div v-if="m.invitation" class="invitation" data-test="invitation">
                  <span class="invitation__titre">{{ t('reseau.messages.invitation') }}</span>
                  <RouterLink :to="`/cercles/${m.invitation.cercle.id}`" class="invitation__cercle">{{ m.invitation.cercle.nom }}</RouterLink>
                  <span class="invitation__infos">{{ m.invitation.cercle.ville ? `${m.invitation.cercle.ville} · ` : '' }}{{ t('cercles.membres', { n: m.invitation.cercle.nombreMembres, taille: m.invitation.cercle.taille }) }}</span>
                  <template v-if="!m.deMoi">
                    <JBouton v-if="placeLibre(m)" :occupe="rejoint === m.invitation.cercle.id" data-test="rejoindre-invitation" @click="rejoindre(m.invitation.cercle.id)">{{ t('cercles.rejoindre') }}</JBouton>
                    <span v-else class="invitation__infos">{{ t('reseau.messages.cercleFerme') }}</span>
                  </template>
                </div>
                <span class="bulle__meta">
                  <time :datetime="m.envoyeLe">{{ heure(m.envoyeLe) }}</time>
                  <span v-if="m.deMoi" class="bulle__lu" :class="{ 'bulle__lu--oui': m.luLe }" :title="m.luLe ? t('reseau.discussion.lu') : t('reseau.discussion.envoye')">
                    {{ m.luLe ? '✓✓' : '✓' }}<span class="visuellement-cache">{{ m.luLe ? t('reseau.discussion.lu') : t('reseau.discussion.envoye') }}</span>
                  </span>
                </span>
              </div>
            </li>
          </template>
        </ol>
        <p v-if="erreurInvitation" role="alert" class="erreur">{{ erreurInvitation }}</p>
      </div>

      <form class="saisie" data-test="form-message" @submit.prevent="envoyer">
        <Composeur
          ref="composeur"
          v-model="texte"
          v-model:pieces="pieces"
          id-zone="message"
          :placeholder="t('reseau.messages.ecrire', { nom: prenom(personne.nom) })"
          :libelle-envoyer="t('reseau.messages.envoyer')"
          :envoi="envoi"
          :erreur="erreur"
          :max="LONGUEUR_MESSAGE"
          test-envoyer="envoyer-message"
          @envoyer="envoyer"
        />
      </form>
    </div>
  </section>
</template>

<style scoped>
.discussion { height: 100%; min-width: 0; }
.cadre {
  display: grid; grid-template-rows: auto 1fr auto auto;
  height: 100%; min-height: 0;
  background: var(--bg); overflow: hidden;
}

.tete { display: flex; align-items: center; gap: var(--space-3); min-height: 80px; padding: 16px 20px; margin: 14px 14px -10px; border-radius: 22px; z-index: 2; }
.tete__retour { display: none; place-items: center; width: 36px; height: 36px; border-radius: 50%; color: var(--ink); }
.tete__retour:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.tete__qui { flex: 1; min-width: 0; }
.tete__qui h2 { margin: 0; font-size: 1.0625rem; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tete__qui p { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.tete__profil { color: var(--ink-muted); text-decoration: none; }
.tete__profil:hover { color: var(--action-ink); text-decoration: underline; }
.tete__lien { padding: 0 8px; border-radius: var(--radius-pill); background: var(--action-soft); color: var(--action-ink); font-weight: 600; font-size: 0.875rem; }
.tete__actions { display: flex; align-items: center; gap: var(--space-2); }

.rond { display: grid; place-items: center; flex: none; width: 44px; height: 44px; border-radius: 50%; border: 0; cursor: pointer; transition: transform var(--motion-fast, 120ms) ease, background var(--motion-fast, 120ms) ease; }
.rond:disabled { opacity: 0.45; cursor: default; }
.rond:not(:disabled):hover { transform: scale(1.06); }
.rond:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.rond--appel { background: var(--action-soft); color: var(--action-ink); }
.rond--envoyer { background: var(--proof); color: var(--on-proof); }

.fil {
  min-height: 0; overflow-y: auto; padding: 34px clamp(20px, 4vw, 64px) 40px; overscroll-behavior: contain;
  background:
    radial-gradient(circle at 1px 1px, color-mix(in srgb, var(--ink) 3%, transparent) 1px, transparent 0) 0 0 / 22px 22px,
    var(--bg);
}
.accueil { display: grid; justify-items: center; gap: var(--space-2); padding: var(--space-8, 3rem) var(--space-4); text-align: center; color: var(--ink-muted); }
.accueil strong { color: var(--ink); font-size: 1.125rem; }
.accueil p { margin: 0; }

.jours { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.jour { display: flex; justify-content: center; margin: var(--space-4) 0 var(--space-2); }
.jour span { padding: 2px 12px; border-radius: var(--radius-pill); background: var(--surface); border: 1px solid var(--line); color: var(--ink-muted); font-size: 0.875rem; font-weight: 600; text-transform: capitalize; }

.ligne { display: flex; align-items: flex-end; gap: var(--space-2); margin-top: var(--space-2); animation: arrive 180ms ease-out; }
.ligne--suite { margin-top: 0; }
.ligne__avatar { flex: none; width: 28px; }
.bulle--moi { flex-direction: row-reverse; }
.bulle--moi .ligne__avatar { display: none; }

.bulle { display: grid; gap: var(--space-1); max-width: min(75%, 34rem); padding: var(--space-2) var(--space-3); border-radius: 18px; box-shadow: 0 1px 1px color-mix(in srgb, var(--ink) 8%, transparent); }
.bulle--autre .bulle { background: var(--surface); color: var(--ink); border-bottom-left-radius: 6px; }
.bulle--moi .bulle { background: var(--proof); color: var(--on-proof); border-bottom-right-radius: 6px; }
.ligne--suite.bulle--autre .bulle { border-top-left-radius: 6px; }
.ligne--suite.bulle--moi .bulle { border-top-right-radius: 6px; }
.bulle__texte { margin: 0; }
.bulle__meta { display: inline-flex; justify-self: end; gap: 4px; align-items: center; font-size: 0.8125rem; opacity: 0.85; }
.bulle__lu { letter-spacing: -2px; }
.bulle__lu--oui { font-weight: 800; }

.invitation { display: grid; gap: var(--space-1); justify-items: start; margin-top: var(--space-1); padding: var(--space-3); border-radius: 12px; background: var(--surface); color: var(--ink); border: 1px solid var(--line); }
.invitation__titre { font-size: 0.875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-muted); }
.invitation__cercle { font-weight: 700; color: var(--ink); }
.invitation__infos { color: var(--ink-muted); font-size: var(--type-body-sm-size); }

.appel-carte { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4); border-radius: 16px; background: var(--surface); border: 1px solid var(--line); max-width: min(85%, 26rem); }
.appel-carte__icone { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; background: var(--success, #1f9d55); color: #fff; flex: none; }
.appel-carte--fini .appel-carte__icone { background: var(--line-strong); color: var(--ink-muted); }
.appel-carte__texte { display: grid; flex: 1; line-height: 1.3; }
.appel-carte__texte span { color: var(--ink-muted); font-size: 0.9375rem; }
.appel-carte__rejoindre { padding: 6px 14px; border-radius: var(--radius-pill); background: var(--success, #1f9d55); color: #fff; font-weight: 700; text-decoration: none; white-space: nowrap; }
.appel-carte__rejoindre:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.saisie { margin: 0 var(--space-6) var(--space-4); z-index: 2; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

@keyframes arrive { from { opacity: 0; transform: translateY(6px); } }
@media (prefers-reduced-motion: reduce) {
  .ligne { animation: none; }
  .rond { transition: none; }
}
@media (max-width: 45rem) {
  .tete__retour { display: grid; }
  .tete { padding: 10px; margin: 10px 10px -10px; gap: 8px; min-height: 76px; flex-wrap: wrap; }
  .tete__qui { min-width: 100px; }
  .tete__actions { margin-left: auto; }
  .tete__actions .j-bouton { font-size: 0.8125rem; padding-inline: 8px; }
  .fil { padding: 30px 16px 40px; }
  .saisie { margin: 0 var(--space-3) var(--space-3); }
  .aide { display: none; }
  .bulle { max-width: 85%; }
}
</style>
