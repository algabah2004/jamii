<script setup lang="ts">
import LienPersonne from '../../components/LienPersonne.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton } from '../../design-system'
import { useSession } from '../../session'
import TextureCommunaute from '../communautes/TextureCommunaute.vue'
import { teinteDe } from '../communautes/identite'
import {
  annoncer,
  depuis,
  formaterDate,
  lireDiscussion,
  lireEspace,
  ouvrirEspace,
  questionsOuvertes,
  retirerAnnonce,
  type Annonce,
  type Espace,
  type Hackathon,
  type QuestionOuverte,
} from './hackathons'

/*
 * La discussion d'un hackathon (contracts/hackathons.yaml, tag « discussion ») :
 * - les annonces de l'organisateur, lues par tous, même sans compte, en fil chronologique ;
 * - l'espace d'échange : une communauté liée, présentée comme telle (son identité, ses membres, ses salons), où les
 *   participants posent leurs questions (avec une réponse acceptée) et discutent. On réutilise les communautés au lieu
 *   de refaire un forum : modération, salons, appels.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const emit = defineEmits<{ nouvelles: [nombre: number] }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const TEXTE_MAX = 2000
const annonces = ref<Annonce[]>([])
const communaute = ref<string | null>(null)
const espace = ref<Espace | null>(null)
/** La communauté liée n'existe pas (ou plus) : on le dit, au lieu de mener à une page introuvable. */
const disparue = ref(false)
const questions = ref<QuestionOuverte[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const texte = ref('')
const envoi = ref(false)
const ouverture = ref(false)
const erreur = ref<string | null>(null)
const toutes = ref(false)
const redaction = ref(false)
/** L'annonce dont on demande la confirmation du retrait : retirer ne s'annule pas. */
const aRetirer = ref<string | null>(null)

/** Une annonce de moins de 48 h est « nouvelle » : mise en avant ici, comptée sur l'onglet. */
const RECENTE_MS = 48 * 3600 * 1000
const estRecente = (a: Annonce) => Date.now() - Date.parse(a.publieeLe) < RECENTE_MS
watch(annonces, (liste) => emit('nouvelles', liste.filter(estRecente).length), { immediate: true })

const organisateur = computed(() => props.hackathon.moi.role === 'organisateur')
const connecte = computed(() => Boolean(moi.value))
const visibles = computed(() => (toutes.value ? annonces.value : annonces.value.slice(0, 3)))
const peutAnnoncer = computed(() => organisateur.value && props.hackathon.statut !== 'annule')
const teinte = computed(() => (communaute.value ? teinteDe(communaute.value) : null))
const salons = computed(() => espace.value?.salons.length ? espace.value.salons : [{ slug: 'general', nom: 'général' }])

async function chargerEspace(slug: string) {
  disparue.value = false
  if (!connecte.value) {
    espace.value = null
    questions.value = []
    return
  }
  // `undefined` : la communauté ne répond pas (panne), ce qui n'est pas la même chose qu'une communauté disparue (`null`).
  const [e, q] = await Promise.all([lireEspace(slug).catch(() => undefined), questionsOuvertes(slug).catch(() => [])])
  espace.value = e ?? null
  disparue.value = e === null
  questions.value = q
}

async function charger() {
  try {
    const d = await lireDiscussion(props.hackathon.id)
    annonces.value = d.annonces
    communaute.value = d.communaute
    etat.value = 'pret'
    if (d.communaute) await chargerEspace(d.communaute)
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => [props.hackathon.id, connecte.value], () => void charger(), { immediate: true })

function message(code: string | null) {
  // 429 : `limite_atteinte` côté hackathons, `trop_de_requetes` côté communautés ; une seule phrase.
  const cle = code === 'limite_atteinte' ? 'trop_de_requetes' : code
  const connus = ['etat_invalide', 'trop_de_requetes']
  return t(`hackathons.discussion.erreurs.${cle && connus.includes(cle) ? cle : 'autre'}`)
}

async function publier() {
  const propre = texte.value.trim()
  if (!propre || envoi.value) return
  envoi.value = true
  erreur.value = null
  try {
    const r = await annoncer(props.hackathon.id, propre)
    if (r.ok) {
      annonces.value = [r.valeur, ...annonces.value]
      texte.value = ''
      redaction.value = false
    } else erreur.value = message(r.code)
  } catch {
    erreur.value = message(null)
  } finally {
    envoi.value = false
  }
}

async function retirer(a: Annonce) {
  aRetirer.value = null
  if (await retirerAnnonce(props.hackathon.id, a.id).catch(() => false)) {
    annonces.value = annonces.value.filter((x) => x.id !== a.id)
  } else erreur.value = message(null)
}

async function ouvrir() {
  if (ouverture.value) return
  ouverture.value = true
  erreur.value = null
  try {
    const r = await ouvrirEspace(props.hackathon)
    if (r.ok) {
      communaute.value = r.valeur
      await chargerEspace(r.valeur)
    } else erreur.value = message(r.code)
  } catch {
    erreur.value = message(null)
  } finally {
    ouverture.value = false
  }
}
</script>

<template>
  <section class="carte discussion" aria-labelledby="discussion-titre" data-test="discussion">
    <header class="discussion__tete">
      <h2 id="discussion-titre">{{ t('hackathons.discussion.titre') }}</h2>
      <p class="texte-discret">{{ t('hackathons.discussion.chapeau') }}</p>
    </header>
    <p v-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.discussion.indisponible') }}</p>
    <template v-else-if="etat === 'pret'">
      <!-- Les annonces : un fil, la plus récente en haut et mise en avant. -->
      <div class="bloc" aria-labelledby="annonces-titre">
        <div class="bloc__tete">
          <h3 id="annonces-titre" class="titre-icone">
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M3 10v4h3l5 4V6L6 10H3zm13.5 2a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z" fill="currentColor" /></svg>
            {{ t('hackathons.discussion.annonces') }}
            <span v-if="annonces.length" class="compte">{{ annonces.length }}</span>
          </h3>
          <JBouton v-if="peutAnnoncer && !redaction" variante="secondaire" data-test="nouvelle-annonce" @click="redaction = true">
            {{ t('hackathons.discussion.nouvelleCourt') }}
          </JBouton>
        </div>

        <form v-if="peutAnnoncer && redaction" class="formulaire" data-test="form-annonce" @submit.prevent="publier">
          <label for="annonce-texte" class="libelle">{{ t('hackathons.discussion.nouvelle') }}</label>
          <textarea id="annonce-texte" v-model="texte" rows="3" :maxlength="TEXTE_MAX" :placeholder="t('hackathons.discussion.exemple')" />
          <div class="ligne">
            <span class="texte-discret">{{ t('hackathons.discussion.aideAnnonce') }} · {{ texte.length }}/{{ TEXTE_MAX }}</span>
            <span class="boutons">
              <JBouton variante="discret" data-test="annuler-annonce" @click="redaction = false">{{ t('hackathons.discussion.annuler') }}</JBouton>
              <JBouton type="submit" :occupe="envoi" :desactive="!texte.trim()" data-test="publier-annonce">{{ t('hackathons.discussion.publier') }}</JBouton>
            </span>
          </div>
        </form>

        <p v-if="!annonces.length" class="vide" data-test="aucune-annonce">{{ t('hackathons.discussion.aucuneAnnonce') }}</p>
        <ol v-else class="fil">
          <li v-for="(a, i) in visibles" :key="a.id" class="annonce" :class="{ recente: i === 0 && estRecente(a) }" data-test="annonce">
            <span class="annonce__point" aria-hidden="true" />
            <div class="annonce__corps">
              <p class="annonce__tete">
                <span class="personne">
                  <LienPersonne :id="a.auteur.id" doublon><JAvatar :nom="a.auteur.nom" :url="a.auteur.avatarUrl" :taille="28" /></LienPersonne>
                  <span>
                    <strong><LienPersonne :id="a.auteur.id">{{ a.auteur.nom }}</LienPersonne></strong>
                    <span class="role">{{ t('hackathons.discussion.organisation') }}</span>
                  </span>
                </span>
                <span class="annonce__quand">
                  <span v-if="i === 0 && estRecente(a)" class="nouveau" data-test="annonce-nouvelle">{{ t('hackathons.discussion.nouveau') }}</span>
                  <time class="texte-discret" :datetime="a.publieeLe" :title="formaterDate(a.publieeLe, locale)">{{ depuis(a.publieeLe, locale) }}</time>
                </span>
              </p>
              <!-- Texte libre : affiché échappé, avec ses retours à la ligne. -->
              <p class="annonce__texte">{{ a.texte }}</p>
              <div v-if="organisateur" class="annonce__actions">
                <template v-if="aRetirer === a.id">
                  <span class="texte-discret" role="status">{{ t('hackathons.discussion.confirmerRetrait') }}</span>
                  <JBouton variante="discret" data-test="garder-annonce" @click="aRetirer = null">{{ t('hackathons.discussion.garder') }}</JBouton>
                  <JBouton variante="secondaire" class="danger" data-test="confirmer-retrait" @click="retirer(a)">{{ t('hackathons.discussion.retirer') }}</JBouton>
                </template>
                <JBouton v-else variante="discret" data-test="retirer-annonce" @click="aRetirer = a.id">{{ t('hackathons.discussion.retirer') }}</JBouton>
              </div>
            </div>
          </li>
        </ol>
        <div v-if="annonces.length > 3">
          <JBouton variante="discret" :aria-expanded="toutes" data-test="toutes-annonces" @click="toutes = !toutes">
            {{ toutes ? t('hackathons.discussion.moins') : t('hackathons.discussion.toutes', { n: annonces.length }) }}
          </JBouton>
        </div>
      </div>

      <!-- L'espace d'échange : la communauté du hackathon, reconnaissable à son identité visuelle. -->
      <div class="bloc" data-test="espace">
        <h3 class="titre-icone">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M4 4h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H9l-4 3v-3H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm16 4h0a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-1v3l-4-3h-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" /></svg>
          {{ t('hackathons.discussion.espace') }}
        </h3>

        <article v-if="communaute" class="espace" :style="{ '--teinte-fond': teinte?.fond, '--teinte-accent': teinte?.accent }" data-test="carte-espace">
          <div class="espace__bandeau" aria-hidden="true"><TextureCommunaute :slug="communaute" /></div>
          <div class="espace__corps">
            <div class="espace__identite">
              <span class="espace__marque" aria-hidden="true">#</span>
              <div>
                <p class="espace__nom">{{ espace?.nom ?? hackathon.titre }}</p>
                <p class="texte-discret">
                  {{ t('hackathons.discussion.communaute') }}
                  <template v-if="espace"> · {{ t('hackathons.discussion.membres', { n: espace.nombreMembres }, espace.nombreMembres) }}</template>
                </p>
              </div>
            </div>

            <p v-if="disparue" class="texte-discret" role="status" data-test="espace-disparu">{{ t('hackathons.discussion.espaceDisparu') }}</p>
            <p v-else>{{ t('hackathons.discussion.aideEspace') }}</p>

            <template v-if="connecte && !disparue">
              <div class="sous-bloc">
                <p class="sous-titre">{{ t('hackathons.discussion.questionsOuvertes') }}</p>
                <ul v-if="questions.length" class="questions" data-test="questions">
                  <li v-for="q in questions" :key="q.id">
                    <RouterLink :to="`/publications/${q.id}`" class="question__titre">{{ q.titre }}</RouterLink>
                    <span class="pastille" :class="{ 'pastille--attente': !q.nombreReponses }">{{ t('hackathons.discussion.reponses', { n: q.nombreReponses }, q.nombreReponses) }}</span>
                  </li>
                </ul>
                <p v-else class="texte-discret" data-test="aucune-question">{{ t('hackathons.discussion.aucuneQuestion') }}</p>
              </div>

              <nav class="sous-bloc" :aria-label="t('hackathons.discussion.salonsTitre')">
                <p class="sous-titre" aria-hidden="true">{{ t('hackathons.discussion.salonsTitre') }}</p>
                <ul class="salons">
                  <li v-for="s in salons" :key="s.slug">
                    <RouterLink :to="`/communautes/${communaute}?salon=${s.slug}`" class="salon" :data-test="s.slug === 'general' ? 'salons' : `salon-${s.slug}`">
                      <span aria-hidden="true">#</span>{{ s.nom }}
                    </RouterLink>
                  </li>
                </ul>
              </nav>
            </template>

            <div v-if="!disparue" class="liens">
              <RouterLink :to="`/communautes/${communaute}?onglet=ouvertes`" class="j-bouton j-bouton--principal" data-test="poser-question">
                {{ t('hackathons.discussion.poser') }}
              </RouterLink>
              <RouterLink :to="`/communautes/${communaute}`" class="j-bouton j-bouton--secondaire" data-test="voir-communaute">
                {{ espace && !espace.membre ? t('hackathons.discussion.rejoindre') : t('hackathons.discussion.voir') }}
              </RouterLink>
            </div>
            <p v-if="!connecte" class="texte-discret">{{ t('hackathons.discussion.connexion') }}</p>
          </div>
        </article>

        <div v-else-if="organisateur" class="appel">
          <p>{{ t('hackathons.discussion.aideOuvrir') }}</p>
          <div><JBouton :occupe="ouverture" data-test="ouvrir-espace" @click="ouvrir">{{ t('hackathons.discussion.ouvrir') }}</JBouton></div>
        </div>
        <p v-else class="vide" data-test="pas-d-espace">{{ t('hackathons.discussion.pasEncore') }}</p>
      </div>
      <p v-if="erreur" role="alert" class="erreur" data-test="erreur-discussion">{{ erreur }}</p>
    </template>
  </section>
</template>

<style scoped>
.discussion { display: grid; gap: var(--space-8); padding: var(--space-8); }
.discussion h2, .discussion h3, .discussion p { margin: 0; }
.discussion h2 { font-size: 1.25rem; }
.discussion__tete { display: grid; gap: var(--space-1); }
.discussion h3 { font-size: 1.125rem; font-weight: 700; }
.titre-icone { display: inline-flex; gap: var(--space-2); align-items: center; }
.titre-icone svg { color: var(--action); flex: none; }
.compte {
  min-inline-size: 1.5rem; padding: 0 var(--space-2); border-radius: var(--radius-pill); background: var(--surface);
  border: 1px solid var(--line-strong); font-size: 0.8125rem; font-weight: 700; text-align: center; font-variant-numeric: tabular-nums;
}
.bloc { display: grid; gap: var(--space-4); }
.bloc__tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.vide { padding: var(--space-4); border: 1px dashed var(--line-strong); border-radius: var(--radius-md); color: var(--ink-muted); text-align: center; }

/* Le formulaire d'annonce */
.formulaire { display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md); background: var(--surface); border: 1px solid var(--line-strong); }
.libelle { font-weight: 600; }
textarea {
  inline-size: 100%; padding: var(--space-3); border: 1px solid var(--line-strong); border-radius: var(--radius-md);
  background: var(--canvas, var(--surface)); color: var(--ink); font: inherit; resize: vertical;
}
textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ligne { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.boutons { display: inline-flex; gap: var(--space-2); }

/* Le fil des annonces : une ligne verticale, un point par annonce. */
.fil { position: relative; margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-4); }
.fil::before { content: ''; position: absolute; inset-block: var(--space-4); inset-inline-start: 7px; inline-size: 2px; background: var(--line-strong); }
.annonce { position: relative; display: grid; grid-template-columns: 16px 1fr; gap: var(--space-3); }
.annonce__point {
  position: relative; z-index: 1; margin-block-start: var(--space-4); inline-size: 16px; block-size: 16px; border-radius: 50%;
  background: var(--surface); border: 2px solid var(--line-strong);
}
.annonce.recente .annonce__point { background: var(--action); border-color: var(--action); box-shadow: 0 0 0 4px color-mix(in srgb, var(--action) 20%, transparent); }
.annonce__corps {
  display: grid; gap: var(--space-2); min-inline-size: 0; padding: var(--space-4); border-radius: var(--radius-md);
  border: 1px solid var(--line-strong); background: var(--surface);
}
.annonce.recente .annonce__corps { border-color: var(--action); background: color-mix(in srgb, var(--action) 7%, var(--surface)); }
.annonce.recente .annonce__texte { font-size: 1.0625rem; }
.annonce__tete { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-3); align-items: center; justify-content: space-between; }
.personne { display: inline-flex; gap: var(--space-2); align-items: center; }
.personne > span { display: grid; line-height: 1.25; }
.role { font-size: 0.8125rem; color: var(--ink-muted); }
.annonce__quand { display: inline-flex; gap: var(--space-2); align-items: center; }
.nouveau {
  padding: 0 var(--space-2); border-radius: var(--radius-pill); background: var(--cantaloupe); color: var(--midnight);
  font-size: 0.75rem; font-weight: 800;
}
.annonce__texte { white-space: pre-line; overflow-wrap: anywhere; }
.annonce__actions { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; justify-content: flex-end; }
.annonce__actions .danger { color: var(--danger); border-color: var(--danger); }

/* L'espace d'échange : une carte aux couleurs de sa communauté. */
.espace { overflow: hidden; border-radius: var(--radius-lg, var(--radius-md)); border: 1px solid var(--line-strong); background: var(--surface); }
.espace__bandeau {
  position: relative; block-size: 72px;
  background: linear-gradient(120deg, var(--teinte-fond, var(--action)), color-mix(in srgb, var(--teinte-accent, var(--cantaloupe)) 55%, var(--teinte-fond, var(--action))));
}
.espace__corps { display: grid; gap: var(--space-4); padding: 0 var(--space-6) var(--space-6); }
.espace__identite { display: flex; gap: var(--space-3); align-items: flex-start; margin-block-start: -28px; }
.espace__identite > div { display: grid; gap: 2px; padding-block-start: 32px; min-inline-size: 0; }
.espace__marque {
  display: grid; place-items: center; flex: none; inline-size: 56px; block-size: 56px; border-radius: var(--radius-md);
  background: var(--midnight); color: #fff; border: 3px solid var(--surface); font-size: 1.5rem; font-weight: 800;
}
.espace__nom { font-size: 1.125rem; font-weight: 700; }
.sous-bloc { display: grid; gap: var(--space-2); }
.sous-titre { font-size: 0.8125rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: var(--ink-muted); }
.questions { margin: 0; padding: 0; list-style: none; display: grid; }
.questions li {
  display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-3); align-items: center; justify-content: space-between;
  padding-block: var(--space-3); border-block-end: 1px solid var(--line-strong);
}
.questions li:last-child { border-block-end: 0; }
.question__titre { font-weight: 600; }
.pastille {
  padding: 2px var(--space-2); border-radius: var(--radius-pill); font-size: 0.8125rem; font-weight: 600;
  background: color-mix(in srgb, var(--action) 12%, transparent); color: var(--ink);
}
.pastille--attente { background: color-mix(in srgb, var(--cantaloupe) 25%, transparent); }
.salons { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.salon {
  display: inline-flex; gap: 2px; align-items: center; min-block-size: 36px; padding: 0 var(--space-3); border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong); color: var(--ink); text-decoration: none; font-weight: 600;
}
.salon span { color: var(--ink-muted); }
.salon:hover { border-color: var(--action); background: color-mix(in srgb, var(--action) 8%, transparent); }
.salon:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.liens { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.appel { display: grid; gap: var(--space-3); padding: var(--space-6); border: 1px dashed var(--action); border-radius: var(--radius-md); background: color-mix(in srgb, var(--action) 5%, transparent); }
.erreur { color: var(--danger); font-weight: 600; }
</style>
