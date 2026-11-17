<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import BadgeIa from '../../components/BadgeIa.vue'
import { ajusterZone } from '../../design-system/zonesDeTexte'
import AyoAvatar from './AyoAvatar.vue'
import { creerDemande, LIMITES, titreDe } from './demandes'

/*
 * Ouvrir une discussion avec Ayo, l'assistant IA, sur la page « Tes demandes ». La personne écrit son besoin avec
 * ses mots, librement (Entrée envoie, Maj + Entrée va à la ligne). La discussion continue sur la page de la demande :
 * Ayo pose une question s'il lui manque quelque chose d'important, sinon il conseille tout de suite.
 */
const props = withDefaults(defineProps<{ titreInitial?: string; exempleInitial?: number | null }>(), { titreInitial: '', exempleInitial: null })
const { t, tm, rt } = useI18n()
const router = useRouter()

const saisie = ref('')
const envoi = ref<'repos' | 'envoi' | 'erreur' | 'quota'>('repos')
const zone = ref<HTMLTextAreaElement | null>(null)

const exemples = computed(() => (tm('demandes.exemples') as { titre: string; description: string }[]).map((e) => ({ titre: rt(e.titre), description: rt(e.description) })))
const longueur = computed(() => saisie.value.trim().length)
const valide = computed(() => longueur.value >= LIMITES.descriptionMin && longueur.value <= LIMITES.descriptionSaisieMax)

function placer(texte: string) {
  saisie.value = texte
  void nextTick(() => {
    if (!zone.value) return
    ajusterZone(zone.value)
    zone.value.focus()
    zone.value.setSelectionRange(saisie.value.length, saisie.value.length)
  })
}

/** Un exemple se met dans la zone de saisie : rien n'est envoyé à la place de la personne, elle l'adapte d'abord. */
const prendreExemple = (e: { description: string }) => placer(e.description)

async function envoyer() {
  if (!valide.value || envoi.value === 'envoi') return
  envoi.value = 'envoi'
  const description = saisie.value.trim()
  try {
    const creee = await creerDemande({ titre: titreDe(description), description })
    if (creee === 'quota') return void (envoi.value = 'quota')
    if (!creee) return void (envoi.value = 'erreur')
    await router.push(`/demandes/${creee.id}`)
  } catch {
    envoi.value = 'erreur'
  }
}

function touche(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void envoyer()
  }
}

// Arrivé avec un début de besoin (lien partagé, ancienne adresse) : il est déjà dans la zone de saisie.
if (props.exempleInitial !== null && exemples.value[props.exempleInitial]) saisie.value = exemples.value[props.exempleInitial]!.description
else if (props.titreInitial.trim()) saisie.value = props.titreInitial.trim().slice(0, LIMITES.descriptionSaisieMax)
</script>

<template>
  <section class="conversation conversation--vide" aria-labelledby="ayo-titre" data-test="conversation-demande">
    <header class="accueil">
      <AyoAvatar :taille="64" class="avatar-premier" />
      <p class="accueil__qui"><strong>{{ t('demandes.ayo.nom') }}</strong> <BadgeIa long /></p>
      <h2 id="ayo-titre" class="accueil__titre question-premiere">{{ t('demandes.mesDemandes.ayoBonjour') }}</h2>
      <p class="accueil__aide">{{ t('demandes.discussion.intro') }}</p>
    </header>

    <form class="saisie-ia" @submit.prevent="envoyer">
      <label for="saisie-ayo" class="visuellement-cache">{{ t('demandes.nouvelle.repondreAyo') }}</label>
      <textarea
        id="saisie-ayo"
        ref="zone"
        v-model="saisie"
        rows="2"
        :maxlength="LIMITES.descriptionSaisieMax"
        :placeholder="t('demandes.discussion.placeholder')"
        data-test="saisie-ayo"
        @input="zone && ajusterZone(zone)"
        @keydown="touche"
      />
      <div class="saisie-ia__bas">
        <span class="saisie-ia__aide" :class="{ ok: valide }">
          <template v-if="longueur > 0 && longueur < LIMITES.descriptionMin">{{ t('demandes.nouvelle.encore', { n: LIMITES.descriptionMin - longueur }) }}</template>
          <template v-else>{{ t('demandes.nouvelle.prudence') }}</template>
        </span>
        <button type="submit" class="saisie-ia__envoyer" :disabled="!valide || envoi === 'envoi'" :aria-busy="envoi === 'envoi' || undefined" :aria-label="t('demandes.nouvelle.envoyer')" data-test="envoyer">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M10 15.5v-11M5.5 9 10 4.5 14.5 9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>
    </form>
    <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('demandes.nouvelle.erreur') }}</p>
    <p v-else-if="envoi === 'quota'" role="alert" class="erreur" data-test="quota">{{ t('demandes.quota') }}</p>
    <div class="puces puces--accueil">
      <button v-for="e in exemples" :key="e.titre" type="button" class="puce" data-test="exemple" @click="prendreExemple(e)">{{ e.titre }}</button>
    </div>
  </section>
</template>

<style scoped>
.conversation { display: grid; gap: var(--space-4); width: 100%; max-width: 48rem; margin-inline: auto; }
/* Comme une nouvelle discussion avec une IA : tout au centre de l'écran. */
.conversation--vide { min-height: calc(100dvh - 7rem); align-content: center; gap: var(--space-6); }
.accueil { display: grid; justify-items: center; gap: var(--space-3); text-align: center; }
.accueil__qui { margin: 0; display: flex; align-items: center; gap: var(--space-2); font-size: var(--type-body-sm-size); }
.accueil__titre { margin: 0; font-size: clamp(1.625rem, 3.4vw, 2.25rem); line-height: 1.2; letter-spacing: -0.02em; }
.accueil__aide { margin: 0; max-width: 34rem; color: var(--ink-muted); }
.puces { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.puces--accueil { justify-content: center; }
.puce { min-height: 34px; padding: 0 var(--space-3); border: 1px dashed var(--line-strong); border-radius: var(--radius-pill); background: transparent; color: var(--ink); font: inherit; font-size: var(--type-body-sm-size); cursor: pointer; }
.puce:hover { border-style: solid; border-color: var(--ink-muted); }
.puce:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; text-align: center; }
.saisie-ia {
  display: grid; gap: var(--space-1); padding: var(--space-3) var(--space-3) var(--space-2) var(--space-4);
  border: 1px solid var(--line-strong); border-radius: 22px; background: var(--surface-raised);
  box-shadow: 0 1px 2px rgb(2 7 10 / 0.08), 0 10px 30px rgb(2 7 10 / 0.16);
}
.saisie-ia:focus-within { border-color: color-mix(in srgb, var(--ink) 35%, var(--line-strong)); outline: 2px solid var(--focus); outline-offset: 2px; }
.saisie-ia textarea { width: 100%; min-height: 0; max-height: 40vh; padding: var(--space-1) 0; border: 0; border-radius: 0; background: transparent; color: var(--ink); font: inherit; font-size: 1.0625rem; line-height: 1.5; resize: none; outline: none; box-shadow: none; }
.saisie-ia textarea::placeholder { color: color-mix(in srgb, var(--ink) 40%, transparent); }
.saisie-ia__bas { display: flex; align-items: center; gap: var(--space-3); }
.saisie-ia__aide { flex: 1; min-width: 0; color: var(--ink-muted); font-size: 0.8125rem; }
.saisie-ia__aide.ok { color: var(--ink-muted); }
.saisie-ia__envoyer { display: grid; place-items: center; flex: none; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: var(--ink); color: var(--surface); cursor: pointer; }
.saisie-ia__envoyer:disabled { opacity: 0.25; cursor: not-allowed; }
.saisie-ia__envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
</style>
