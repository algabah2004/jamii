<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LienPersonne from '../../components/LienPersonne.vue'
import { JAvatar } from '../../design-system'
import { useSession } from '../../session'
import type { Cercle } from './cercles'
import { pointsParJour } from './qg'
import { envoyer, LIMITES_SALON, lireSalon, prenom, type MessageCercle } from './salon'

/*
 * Le point du soir (les « automatic check-ins » de Basecamp) : chaque jour, la même question au cercle. Les réponses
 * sont regroupées par jour : on relit la journée de l'équipe d'un coup, sans la chercher dans le fil.
 */
const props = defineProps<{ cercle: Cercle }>()
const { t, locale } = useI18n()
const { moi } = useSession()

const messages = ref<MessageCercle[]>([])
const pointsDuJour = ref<string[]>([])
const etat = ref<'chargement' | 'pret' | 'erreur'>('chargement')
const jours = computed(() => pointsParJour(messages.value))
const fait = computed(() => !!moi.value && pointsDuJour.value.includes(moi.value.id))
const manquants = computed(() => props.cercle.membres.filter((m) => !pointsDuJour.value.includes(m.personne.id)).map((m) => m.personne))

async function charger() {
  const r = await lireSalon(props.cercle.id).catch(() => null)
  if (!r?.ok) return void (etat.value = 'erreur')
  messages.value = r.salon.elements
  pointsDuJour.value = r.salon.pointsDuJour
  etat.value = 'pret'
}
onMounted(charger)

const point = ref({ fait: '', prevu: '', bloque: '' })
const envoi = ref(false)
const erreur = ref<string | null>(null)
const pret = computed(() => !envoi.value && !!point.value.fait.trim() && !!point.value.prevu.trim())
async function repondre() {
  if (!pret.value) return
  envoi.value = true
  erreur.value = null
  const r = await envoyer(props.cercle.id, { type: 'point', point: { fait: point.value.fait.trim(), prevu: point.value.prevu.trim(), bloque: point.value.bloque.trim() || null } }).catch(() => null)
  envoi.value = false
  if (!r?.ok) return void (erreur.value = t(r && r.code === 'point_deja_fait' ? 'cercles.salon.erreurs.pointDejaFait' : 'cercles.salon.erreurs.envoi'))
  messages.value = [...messages.value, r.message]
  if (moi.value) pointsDuJour.value = [...pointsDuJour.value, moi.value.id]
  point.value = { fait: '', prevu: '', bloque: '' }
}

function libelleJour(iso: string) {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  const hier = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10)
  if (iso === aujourdhui) return t('cercles.salon.aujourdhui')
  if (iso === hier) return t('cercles.salon.hier')
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(iso))
}
</script>

<template>
  <section class="outil-points" aria-labelledby="points-titre" data-test="outil-points-page">
    <header class="tete carte">
      <span class="icone" aria-hidden="true">🌙</span>
      <div>
        <h2 id="points-titre">{{ t('cercles.qg.outils.points') }}</h2>
        <p class="question">« {{ t('cercles.qg.question') }} »</p>
        <p class="texte-discret">{{ t('cercles.qg.pointsAide') }}</p>
      </div>
    </header>

    <form v-if="!fait" class="carte reponse" data-test="repondre-point" @submit.prevent="repondre">
      <h3>{{ t('cercles.qg.maReponse') }}</h3>
      <label for="op-fait">{{ t('cercles.salon.fait') }}</label>
      <textarea id="op-fait" v-model="point.fait" rows="2" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.faitAide')" />
      <label for="op-prevu">{{ t('cercles.salon.prevu') }}</label>
      <textarea id="op-prevu" v-model="point.prevu" rows="2" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.prevuAide')" />
      <label for="op-bloque">{{ t('cercles.salon.bloqueFacultatif') }}</label>
      <textarea id="op-bloque" v-model="point.bloque" rows="1" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.bloqueAide')" />
      <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
      <button type="submit" class="envoyer" :disabled="!pret">{{ t('cercles.salon.envoyerPoint') }}</button>
    </form>
    <p v-else class="carte fait" data-test="point-fait">✓ {{ t('cercles.salon.pointFaitAujourdhui') }}</p>

    <p v-if="manquants.length && etat === 'pret'" class="texte-discret">{{ t('cercles.qg.pasEncore', { noms: manquants.map((p) => prenom(p.nom)).join(', ') }) }}</p>

    <p v-if="etat === 'chargement'" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'erreur'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>
    <p v-else-if="!jours.length" class="texte-discret">{{ t('cercles.qg.aucunPoint') }}</p>

    <section v-for="j in jours" :key="j.jour" class="jour" :aria-label="libelleJour(j.jour)" data-test="jour-de-points">
      <h3>{{ libelleJour(j.jour) }} <span class="texte-discret">· {{ t('cercles.qg.reponses', { n: j.points.length }, j.points.length) }}</span></h3>
      <ul class="reponses">
        <li v-for="p in j.points" :key="p.id" class="carte reponse-membre">
          <LienPersonne :id="p.auteur?.id" doublon><JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="36" /></LienPersonne>
          <div>
            <p class="qui"><LienPersonne :id="p.auteur?.id"><strong>{{ p.auteur?.nom }}</strong></LienPersonne></p>
            <dl v-if="p.point">
              <div><dt>{{ t('cercles.salon.fait') }}</dt><dd>{{ p.point.fait }}</dd></div>
              <div><dt>{{ t('cercles.salon.prevu') }}</dt><dd>{{ p.point.prevu }}</dd></div>
              <div v-if="p.point.bloque" class="bloque"><dt>{{ t('cercles.salon.bloque') }}</dt><dd>{{ p.point.bloque }}</dd></div>
            </dl>
          </div>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.outil-points { display: grid; gap: var(--space-4); max-width: 52rem; }
.tete { display: flex; gap: var(--space-4); align-items: flex-start; }
.tete h2, .tete p { margin: 0; }
.icone { display: grid; place-items: center; flex: none; width: 48px; height: 48px; border-radius: 14px; background: var(--surface-raised); font-size: 24px; }
.question { font-size: 1.25rem; font-style: italic; margin: var(--space-1) 0 !important; }
.reponse { display: grid; gap: var(--space-2); }
.reponse h3 { margin: 0 0 var(--space-1); font-size: var(--type-body-size); }
.reponse label { font-weight: 600; font-size: var(--type-body-sm-size); }
.reponse textarea { width: 100%; box-sizing: border-box; padding: var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface-raised); color: var(--ink); font: inherit; resize: vertical; }
.reponse textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.envoyer { justify-self: end; min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; }
.envoyer:disabled { opacity: 0.5; cursor: not-allowed; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.fait { margin: 0; color: var(--proof-ink); font-weight: 600; }
.jour { display: grid; gap: var(--space-2); }
.jour h3 { margin: var(--space-2) 0 0; font-size: var(--type-body-size); }
.jour h3::first-letter { text-transform: uppercase; }
.reponses { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.reponse-membre { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: var(--space-3); }
.qui { margin: 0 0 var(--space-2); }
dl { margin: 0; display: grid; gap: var(--space-1); }
dl > div { display: grid; grid-template-columns: 5.5rem minmax(0, 1fr); gap: var(--space-2); }
dt { color: var(--ink-muted); font-size: var(--type-body-sm-size); font-weight: 600; }
dd { margin: 0; overflow-wrap: anywhere; }
.bloque dt, .bloque dd { color: var(--proof-ink); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
