<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import { api } from '../../api/client'
import { JBouton, JIllustration, JSquelette } from '../../design-system'
import { useSession } from '../../session'
import CarteCompetences from './CarteCompetences.vue'
import EditeurProfil from './EditeurProfil.vue'
import FicheProfil from './FicheProfil.vue'
import IconeProfil from './IconeProfil.vue'
import PartagerCarte from './PartagerCarte.vue'
import { completude, SECTION_ETAPE, type EtapeProfil, type SectionEdition } from './completude'
import type { MonProfil } from './profils'

const { t } = useI18n()
const { moi } = useSession()

const profil = ref<MonProfil | null>(null)
const etat = ref<'chargement' | 'aucun' | 'pret' | 'indisponible'>('chargement')
const occupe = ref(false)
const message = ref<string | null>(null)
let minuterie: ReturnType<typeof setTimeout> | undefined

/** Pendant la génération, on relit le profil toutes les 2 secondes, 2 minutes au plus (l'API abandonne après). */
const ATTENTE_MS = 2000
const ESSAIS_MAX = 60

async function charger(essai = 0) {
  clearTimeout(minuterie)
  try {
    const { data, response } = await api.GET('/api/me/profil')
    if (response.status === 404) {
      profil.value = null
      etat.value = 'aucun'
      return
    }
    if (!data) throw new Error('profil indisponible')
    profil.value = data
    etat.value = data.langages.length || data.generation.statut === 'terminee' ? 'pret' : 'aucun'
    if (data.generation.statut === 'en_cours' && essai < ESSAIS_MAX) {
      minuterie = setTimeout(() => void charger(essai + 1), ATTENTE_MS)
    }
  } catch {
    etat.value = 'indisponible'
  }
}
onBeforeUnmount(() => clearTimeout(minuterie))
void charger()

const enCours = computed(() => profil.value?.generation.statut === 'en_cours')
const echec = computed(() => (profil.value?.generation.statut === 'echec' ? profil.value.generation.erreur : null))
const sansGithub = computed(() => !moi.value?.loginGithub)

/*
 * Relier son GitHub (proposition 0005) : une personne venue par Google ou par e-mail rattache son compte GitHub à son
 * compte Jamii, puis génère sa carte de compétences. L'API revient ici avec `?github=relie` ou `?erreurGithub=…`.
 */
const route = useRoute()
const lienGithub = '/api/auth/github?lier=1'
const githubRelie = computed(() => route.query.github === 'relie')
const ERREURS_LIEN = ['github_deja_utilise', 'compte_deja_relie', 'refus_github', 'requete_invalide', 'github_indisponible']
const erreurLien = computed(() => {
  const code = route.query.erreurGithub
  if (typeof code !== 'string' || !code) return null
  return ERREURS_LIEN.includes(code) ? code : 'github_indisponible'
})

async function generer() {
  occupe.value = true
  message.value = null
  try {
    const { response } = await api.POST('/api/me/profil/generation')
    if (response.status === 429) message.value = t('profil.generation.tropSouvent')
    else if (!response.ok && response.status !== 409) message.value = t('etat.indisponible')
    await charger()
  } finally {
    occupe.value = false
  }
}

async function changerVisibilite() {
  if (!profil.value) return
  occupe.value = true
  try {
    const { data } = profil.value.publie
      ? await api.POST('/api/me/profil/retirer')
      : await api.POST('/api/me/profil/publier')
    if (data) profil.value = data
  } finally {
    occupe.value = false
  }
}

// ---------- La jauge « profil complet » ----------
const jauge = computed(() => (profil.value ? completude(profil.value) : null))
const prochaine = computed<EtapeProfil | null>(() => jauge.value?.manquantes[0] ?? null)
function faireEtape(e: EtapeProfil) {
  const section = SECTION_ETAPE[e]
  if (section) ouvrirEdition(section)
  else void changerVisibilite()
}

// ---------- Modifier : le panneau, et l'aperçu en direct derrière lui ----------
const edition = ref(false)
const sectionEdition = ref<SectionEdition>('essentiel')
const apercu = ref<Partial<MonProfil> | null>(null)
const affiche = computed(() => (profil.value && edition.value && apercu.value ? { ...profil.value, ...apercu.value } : profil.value))

function ouvrirEdition(section: SectionEdition = 'essentiel') {
  sectionEdition.value = section
  message.value = null
  apercu.value = null
  edition.value = true
}
async function fermerEdition() {
  edition.value = false
  apercu.value = null
  await nextTick()
  document.querySelector<HTMLElement>('[data-test="modifier"]')?.focus()
}
function enregistre(p: MonProfil) {
  profil.value = p
  void fermerEdition()
  message.value = t('profil.modifier.enregistre')
}
async function conflit() {
  // Le panneau se ferme d'abord : la dernière version s'affiche ensuite, sans mélanger l'ancien brouillon.
  void fermerEdition()
  message.value = t('profil.modifier.conflit')
  await charger()
}
</script>

<template>
  <section class="mon-profil">
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />

    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>

    <!-- Pas encore de profil : on propose de le générer, en disant ce qu'on lit. -->
    <div v-else-if="etat === 'aucun'" class="accueil" data-test="sans-profil">
      <div class="accueil__texte">
        <p class="accueil__surtitre"><i aria-hidden="true" />{{ t('profil.generation.surtitre') }}</p>
        <h1>{{ t('profil.generation.titre') }}<span>{{ t('profil.generation.accent') }}</span></h1>
        <p class="accueil__intro">{{ t('profil.generation.texte') }}</p>
        <ul class="accueil__lu">
          <li v-for="cle in ['langages', 'sujets', 'etoiles']" :key="cle"><span><IconeProfil nom="coche" :taille="14" /></span>{{ t(`profil.generation.lu.${cle}`) }}</li>
          <li class="jamais"><span><IconeProfil nom="cadenas" :taille="14" /></span>{{ t('profil.generation.lu.jamais') }}</li>
        </ul>
        <template v-if="sansGithub">
          <p class="texte-discret">{{ t('profil.generation.sansGithub') }}</p>
          <a :href="lienGithub" class="accueil__principal" data-test="relier-github"><IconeProfil nom="github" />{{ t('profil.generation.relier') }}</a>
        </template>
        <template v-else>
          <p v-if="enCours" role="status" class="en-cours" data-test="en-cours"><span class="en-cours__roue" aria-hidden="true" />{{ t('profil.generation.enCours') }}</p>
          <button v-else type="button" class="accueil__principal" :disabled="occupe" :aria-busy="occupe || undefined" data-test="generer" @click="generer">
            <IconeProfil nom="github" />{{ t('profil.generation.bouton') }}
          </button>
          <p class="texte-discret accueil__prive"><IconeProfil nom="cadenas" :taille="14" />{{ t('profil.generation.prive') }}</p>
        </template>
        <p v-if="githubRelie && !sansGithub" role="status" class="relie" data-test="github-relie">✓ {{ t('profil.generation.relie') }}</p>
        <p v-if="erreurLien" role="alert" class="erreur" data-test="erreur-lien">{{ t(`profil.generation.erreursLien.${erreurLien}`) }}</p>
        <p v-if="echec" role="alert" class="erreur">{{ t(`profil.generation.erreurs.${echec}`) }}</p>
        <p v-if="message" role="alert" class="erreur">{{ message }}</p>
      </div>
      <div class="accueil__visuel">
        <JIllustration nom="profil-prouve" />
      </div>
    </div>

    <template v-else-if="profil && affiche">
      <FicheProfil :profil="affiche" moi @modifier="ouvrirEdition">
        <template #actions>
          <div class="barre">
            <button
              type="button"
              class="visibilite"
              :class="{ 'visibilite--publie': profil.publie }"
              role="switch"
              :aria-checked="profil.publie"
              :disabled="occupe"
              data-test="publier"
              @click="changerVisibilite"
            >
              <span class="visibilite__interrupteur" aria-hidden="true"><i /></span>
              <span class="visibilite__texte" data-test="visibilite">
                <strong>{{ profil.publie ? t('profil.visibilite.etatPublie') : t('profil.visibilite.etatPrive') }}</strong>
                <small>{{ profil.publie ? t('profil.visibilite.aidePublie') : t('profil.visibilite.aidePrive') }}</small>
              </span>
            </button>
            <div class="barre__boutons">
              <button type="button" class="barre__principal" data-test="modifier" @click="ouvrirEdition()">
                <IconeProfil nom="crayon" />{{ t('profil.modifier.bouton') }}
              </button>
              <PartagerCarte :profil="profil" :publie="profil.publie" />
              <RouterLink v-if="profil.publie" :to="`/p/${profil.utilisateurId}`" class="barre__lien" data-test="voir-public">
                <IconeProfil nom="oeil" />{{ t('profil.visibilite.voirPublic') }}</RouterLink>
              <button
                type="button"
                class="barre__icone"
                :disabled="occupe || enCours"
                :title="enCours ? t('profil.generation.enCours') : t('profil.generation.regenerer')"
                data-test="regenerer"
                @click="generer"
              >
                <IconeProfil nom="rafraichir" :class="{ tourne: enCours }" />
                <span class="visuellement-cache">{{ enCours ? t('profil.generation.enCours') : t('profil.generation.regenerer') }}</span>
              </button>
            </div>
          </div>
        </template>

        <template #apres-entete>
          <p v-if="message" role="status" class="annonce" data-test="message">{{ message }}</p>
          <p v-if="echec" role="alert" class="annonce annonce--erreur">{{ t(`profil.generation.erreurs.${echec}`) }}</p>

          <section v-if="jauge && prochaine" class="jauge" :aria-label="t('profil.completude.titre')" data-test="completude">
            <div class="jauge__anneau" :style="{ '--p': jauge.pourcent }" role="img" :aria-label="t('profil.completude.pourcent', { n: jauge.pourcent })">
              <span>{{ jauge.pourcent }}<small>%</small></span>
            </div>
            <div class="jauge__texte">
              <strong>{{ t('profil.completude.titre') }}</strong>
              <p>{{ t(`profil.completude.conseils.${prochaine}`) }}</p>
            </div>
            <button type="button" class="jauge__action" data-test="etape-suivante" @click="faireEtape(prochaine)">
              {{ t(`profil.completude.actions.${prochaine}`) }} <IconeProfil nom="fleche" :taille="14" />
            </button>
          </section>
        </template>

        <template #debut>
          <CarteCompetences :profil="profil" @mis-a-jour="(p) => (profil = p)" />
        </template>
      </FicheProfil>

      <EditeurProfil
        v-if="edition"
        :profil="profil"
        :section="sectionEdition"
        @apercu="(c) => (apercu = c)"
        @enregistre="enregistre"
        @conflit="conflit"
        @fermer="fermerEdition"
      />
    </template>
  </section>
</template>

<style scoped>
.mon-profil { display: grid; gap: 24px; }

/* ---------- Pas encore de profil : la même entrée en matière que les pages d'exploration. ---------- */
.accueil { display: grid; gap: clamp(28px, 5vw, 80px); grid-template-columns: minmax(0, 1.15fr) minmax(0, .85fr); align-items: center; padding-block: clamp(12px, 3vw, 40px); }
.accueil__texte { display: grid; gap: 18px; justify-items: start; }
.accueil__texte > * { margin: 0; }
.accueil__surtitre { display: flex; align-items: center; gap: 10px; color: var(--ink-muted); font-size: .875rem; letter-spacing: .12em; text-transform: uppercase; font-weight: 700; }
.accueil__surtitre i { width: 7px; height: 7px; border-radius: 50%; background: var(--proof); }
.accueil h1 { font-size: clamp(2.5rem, 4.6vw, 4.25rem); line-height: 1.04; letter-spacing: -.05em; max-width: 16ch; }
.accueil h1 span { display: block; color: var(--action-ink); }
.accueil__intro { font-size: 1.0625rem; line-height: 1.8; color: var(--ink-muted); max-width: 52ch; }
.accueil__lu { list-style: none; padding: 0; display: grid; gap: 10px; font-weight: 600; }
.accueil__lu li { display: flex; align-items: center; gap: 10px; }
.accueil__lu li > span { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--action-soft); color: var(--action-ink); }
.accueil__lu .jamais { color: var(--ink-muted); font-weight: 500; }
.accueil__lu .jamais > span { background: var(--surface-raised); color: var(--ink-muted); }
.accueil__principal { display: inline-flex; align-items: center; gap: 10px; min-height: 52px; padding: 12px 24px; border: 0; border-radius: 50px; background: var(--proof); color: var(--on-proof); font-weight: 700; font-size: 1rem; text-decoration: none; cursor: pointer; box-shadow: 0 10px 24px color-mix(in srgb, var(--proof) 30%, transparent); }
.accueil__principal:disabled { opacity: .6; cursor: progress; }
.accueil__prive { display: flex; align-items: center; gap: 6px; }
.accueil__visuel { padding: clamp(24px, 4vw, 48px); border: 1px solid var(--line-strong); border-radius: 28px; background: radial-gradient(ellipse at 90% 0, var(--action-soft), transparent 65%), var(--surface); box-shadow: var(--ombre-carte); }
.en-cours { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--action-ink); }
.en-cours__roue { width: 20px; height: 20px; border-radius: 50%; border: 3px solid var(--action-soft); border-top-color: var(--action); animation: tourner .8s linear infinite; }
.erreur { color: var(--danger); font-weight: 600; }
.relie { font-weight: 700; color: var(--success, var(--ink)); }
@media (max-width: 760px) { .accueil { grid-template-columns: minmax(0, 1fr); } .accueil__visuel { order: -1; max-width: 20rem; } }

/* ---------- La barre du propriétaire, dans l'en-tête ---------- */
.barre { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 16px; padding-top: 18px; border-top: 1px solid var(--line); }
.visibilite { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 8px 16px 8px 10px; border: 1px solid var(--line-strong); border-radius: 18px; background: var(--surface); color: var(--ink); text-align: start; cursor: pointer; }
.visibilite:hover { border-color: var(--action); }
.visibilite:disabled { cursor: progress; }
.visibilite__interrupteur { position: relative; width: 44px; height: 26px; flex: none; border-radius: 50px; background: var(--line-strong); transition: background var(--motion-fast) var(--ease-out); }
.visibilite__interrupteur i { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.25); transition: transform var(--motion-base) var(--ease-pop); }
.visibilite--publie .visibilite__interrupteur { background: #1FA35B; }
.visibilite--publie .visibilite__interrupteur i { transform: translateX(18px); }
.visibilite__texte { display: grid; line-height: 1.3; }
.visibilite__texte strong { font-size: .9375rem; }
.visibilite__texte small { color: var(--ink-muted); font-size: .8125rem; }
.barre__boutons { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.barre__principal { display: inline-flex; align-items: center; gap: 8px; min-height: 46px; padding: 10px 20px; border: 0; border-radius: 50px; background: var(--proof); color: var(--on-proof); font-weight: 700; cursor: pointer; }
.barre__principal:hover { filter: brightness(1.05); }
.barre__lien { display: inline-flex; align-items: center; gap: 8px; min-height: 46px; padding: 0 12px; font-weight: 600; text-decoration: none; }
.barre__lien:hover { text-decoration: underline; }
.barre__icone { display: grid; place-items: center; width: 46px; height: 46px; border: 1px solid var(--line-strong); border-radius: 50%; background: var(--surface); color: var(--ink); cursor: pointer; }
.barre__icone:hover:not(:disabled) { border-color: var(--action); }
.barre__icone:disabled { opacity: .55; cursor: progress; }
.tourne { animation: tourner 1s linear infinite; }
@keyframes tourner { to { transform: rotate(360deg); } }

.annonce { margin: 0; padding: 12px 16px; border-radius: 14px; background: var(--action-soft); color: var(--ink); font-weight: 600; }
.annonce--erreur { background: color-mix(in srgb, var(--danger) 12%, transparent); color: var(--danger); }

/* ---------- La jauge « profil complet » ---------- */
.jauge { display: flex; flex-wrap: wrap; align-items: center; gap: 16px 20px; padding: 16px 20px; border: 1px solid var(--line); border-radius: 22px; background: linear-gradient(100deg, var(--action-soft), var(--surface) 55%); }
.jauge__anneau { --p: 0; display: grid; place-items: center; width: 64px; height: 64px; flex: none; border-radius: 50%; background: conic-gradient(var(--action) calc(var(--p) * 1%), var(--surface-raised) 0); }
.jauge__anneau span { display: grid; place-items: center; width: 50px; height: 50px; border-radius: 50%; background: var(--surface); font-family: var(--font-display); font-weight: 700; font-size: 1.125rem; }
.jauge__anneau small { font-size: .625rem; margin-top: -4px; }
.jauge__anneau span { grid-auto-flow: column; align-items: baseline; }
.jauge__texte { flex: 1 1 16rem; display: grid; gap: 2px; min-width: 0; }
.jauge__texte p { margin: 0; color: var(--ink-muted); font-size: .9375rem; }
.jauge__action { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 18px; border: 1px solid var(--action); border-radius: 50px; background: var(--surface); color: var(--action-ink); font-weight: 700; cursor: pointer; }
.jauge__action:hover { background: var(--action-soft); }

@media (max-width: 640px) {
  .barre__boutons { margin-inline-start: 0; }
  .visibilite { width: 100%; }
}
@media (prefers-reduced-motion: reduce) { .tourne, .en-cours__roue { animation: none; } .visibilite__interrupteur i { transition: none; } }
</style>
