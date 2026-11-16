<script setup lang="ts">
import EnTeteOutil from './EnTeteOutil.vue'
import IconeCercle from './IconeCercle.vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
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
// L'équipe du jour : qui a déjà donné son point, qui ne l'a pas encore fait. Ceux qui ont répondu passent devant.
const equipe = computed(() =>
  props.cercle.membres
    .map((m) => ({ personne: m.personne, repondu: pointsDuJour.value.includes(m.personne.id) }))
    .sort((a, b) => Number(b.repondu) - Number(a.repondu)),
)
const nbRepondu = computed(() => equipe.value.filter((m) => m.repondu).length)
const manquants = computed(() => equipe.value.filter((m) => !m.repondu).map((m) => m.personne))
const heure = (iso: string) => new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso))

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
    <EnTeteOutil id="points-titre" icone="points" :titre="t('cercles.qg.outils.points')" :aide="t('cercles.qg.pointsAide')" />

    <!-- Le soir venu : où en est l'équipe aujourd'hui, puis mon point (ou la confirmation qu'il est donné). -->
    <div class="soir carte">
      <div class="soir__equipe" data-test="equipe-du-soir">
        <div class="soir__compte">
          <p class="soir__surtitre">{{ t('cercles.salon.aujourdhui') }}</p>
          <p class="soir__chiffre"><strong>{{ nbRepondu }}</strong>/{{ equipe.length }}</p>
          <p class="soir__legende">{{ t('cercles.points.ontRepondu', { n: nbRepondu }, nbRepondu) }}</p>
        </div>
        <div class="soir__jauge" aria-hidden="true"><span :style="{ width: `${equipe.length ? (nbRepondu / equipe.length) * 100 : 0}%` }" /></div>
        <ul class="soir__avatars">
          <li v-for="m in equipe" :key="m.personne.id" :class="{ repondu: m.repondu }" :title="`${m.personne.nom} · ${m.repondu ? t('cercles.points.repondu') : t('cercles.points.pasEncore')}`">
            <JAvatar :nom="m.personne.nom" :url="m.personne.avatarUrl" :taille="34" />
            <span v-if="m.repondu" class="soir__coche"><IconeCercle nom="coche" :taille="11" /></span>
            <span class="visuellement-cache">{{ m.personne.nom }}, {{ m.repondu ? t('cercles.points.repondu') : t('cercles.points.pasEncore') }}</span>
          </li>
        </ul>
      </div>

      <form v-if="!fait" class="mon-point" data-test="repondre-point" @submit.prevent="repondre">
        <h3>{{ t('cercles.qg.question') }}</h3>
        <div class="mon-point__champs">
          <label class="champ champ--fait">
            <span class="champ__tete"><IconeCercle nom="coche" :taille="15" />{{ t('cercles.salon.fait') }}</span>
            <textarea id="op-fait" v-model="point.fait" rows="3" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.faitAide')" />
          </label>
          <label class="champ champ--prevu">
            <span class="champ__tete"><IconeCercle nom="suivant" :taille="15" />{{ t('cercles.salon.prevu') }}</span>
            <textarea id="op-prevu" v-model="point.prevu" rows="3" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.prevuAide')" />
          </label>
          <label class="champ champ--bloque">
            <span class="champ__tete"><IconeCercle nom="bloque" :taille="15" />{{ t('cercles.salon.bloqueFacultatif') }}</span>
            <textarea id="op-bloque" v-model="point.bloque" rows="3" :maxlength="LIMITES_SALON.point" :placeholder="t('cercles.salon.bloqueAide')" />
          </label>
        </div>
        <div class="mon-point__pied">
          <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
          <p v-else class="texte-discret">{{ t('cercles.points.uneFoisParJour') }}</p>
          <button type="submit" class="envoyer" :disabled="!pret">{{ t('cercles.salon.envoyerPoint') }}</button>
        </div>
      </form>
      <p v-else class="mon-point mon-point--fait" data-test="point-fait">
        <span class="mon-point__pastille"><IconeCercle nom="coche" :taille="16" /></span>
        <span>
          <strong>{{ t('cercles.salon.pointFaitAujourdhui') }}</strong>
          <span v-if="manquants.length && etat === 'pret'" class="texte-discret"> {{ t('cercles.qg.pasEncore', { noms: manquants.map((p) => prenom(p.nom)).join(', ') }) }}</span>
        </span>
      </p>
    </div>

    <p v-if="etat === 'chargement'" class="texte-discret" role="status">{{ t('etat.chargement') }}</p>
    <p v-else-if="etat === 'erreur'" class="texte-discret" role="alert">{{ t('etat.indisponible') }}</p>
    <p v-else-if="!jours.length" class="vide carte">{{ t('cercles.qg.aucunPoint') }}</p>

    <!-- Les journées, de la plus récente à la plus ancienne, le long d'un fil. -->
    <ol v-if="jours.length" class="journees">
      <li v-for="j in jours" :key="j.jour" class="journee" data-test="jour-de-points">
        <h3 class="journee__titre"><span class="journee__point" aria-hidden="true" />{{ libelleJour(j.jour) }} <span class="texte-discret">· {{ t('cercles.qg.reponses', { n: j.points.length }, j.points.length) }}</span></h3>
        <ul class="reponses">
          <li v-for="p in j.points" :key="p.id" class="carte reponse" :class="{ 'reponse--bloque': p.point?.bloque }">
            <header class="reponse__tete">
              <LienPersonne :id="p.auteur?.id" doublon><JAvatar :nom="p.auteur?.nom ?? '?'" :url="p.auteur?.avatarUrl" :taille="32" /></LienPersonne>
              <LienPersonne :id="p.auteur?.id" class="reponse__nom">{{ p.auteur?.nom }}</LienPersonne>
              <time :datetime="p.envoyeLe" class="reponse__heure">{{ heure(p.envoyeLe) }}</time>
            </header>
            <dl v-if="p.point" class="reponse__blocs">
              <div class="bloc bloc--fait"><dt><IconeCercle nom="coche" :taille="14" />{{ t('cercles.salon.fait') }}</dt><dd>{{ p.point.fait }}</dd></div>
              <div class="bloc bloc--prevu"><dt><IconeCercle nom="suivant" :taille="14" />{{ t('cercles.salon.prevu') }}</dt><dd>{{ p.point.prevu }}</dd></div>
              <div v-if="p.point.bloque" class="bloc bloc--bloque">
                <dt><IconeCercle nom="bloque" :taille="14" />{{ t('cercles.salon.bloque') }}</dt>
                <dd>{{ p.point.bloque }}</dd>
                <RouterLink :to="{ query: { outil: 'salon' } }" class="bloc__aide">{{ t('cercles.points.aiderAuSalon', { nom: prenom(p.auteur?.nom ?? '') }) }}</RouterLink>
              </div>
            </dl>
          </li>
        </ul>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.outil-points { display: grid; gap: var(--space-6); }
/* Trois couleurs, toujours les mêmes : fait (vert), prévu (bleu), bloqué (orange). */
.outil-points { --c-fait: var(--en-ligne); --c-prevu: var(--action); --c-bloque: var(--proof); }

.soir { display: grid; gap: var(--space-5); padding: var(--space-5) var(--space-6); }
.soir__equipe { display: grid; grid-template-columns: auto minmax(6rem, 1fr) auto; align-items: center; gap: var(--space-5); }
.soir__compte { display: grid; }
.soir__surtitre { margin: 0; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; }
.soir__chiffre { margin: 0; font-size: 1.75rem; line-height: 1.1; color: var(--ink-muted); }
.soir__chiffre strong { color: var(--ink); }
.soir__legende { margin: 0; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.soir__jauge { height: 8px; border-radius: var(--radius-pill); background: var(--surface-raised); overflow: hidden; }
.soir__jauge span { display: block; height: 100%; border-radius: inherit; background: var(--c-fait); transition: width var(--motion-base) var(--ease-out); }
.soir__avatars { list-style: none; margin: 0; padding: 0; display: flex; gap: var(--space-2); }
.soir__avatars li { position: relative; display: grid; border-radius: 50%; opacity: 0.4; filter: grayscale(0.6); }
.soir__avatars li.repondu { opacity: 1; filter: none; box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--c-fait); }
.soir__coche { position: absolute; right: -4px; bottom: -4px; display: grid; place-items: center; width: 17px; height: 17px; border-radius: 50%; background: var(--c-fait); color: #fff; box-shadow: 0 0 0 2px var(--surface); }

.mon-point { display: grid; gap: var(--space-3); margin: 0; padding-top: var(--space-5); border-top: 1px solid var(--line); }
.mon-point h3 { margin: 0; font-size: 1.125rem; }
.mon-point__champs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-3); }
.champ { display: grid; gap: var(--space-2); align-content: start; padding: var(--space-3); border-radius: var(--radius-md); background: var(--surface-raised); border-top: 3px solid var(--c); }
.champ--fait { --c: var(--c-fait); }
.champ--prevu { --c: var(--c-prevu); }
.champ--bloque { --c: var(--c-bloque); }
.champ__tete { display: flex; align-items: center; gap: 6px; font-size: var(--type-body-sm-size); font-weight: 700; }
.champ__tete :deep(svg) { color: var(--c); }
.champ textarea { width: 100%; box-sizing: border-box; padding: var(--space-2) var(--space-3); border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--surface); color: var(--ink); font: inherit; resize: vertical; }
.champ textarea:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
.mon-point__pied { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); }
.mon-point__pied p { margin: 0; font-size: var(--type-body-sm-size); }
.envoyer { flex: none; min-height: 44px; padding: 0 var(--space-6); border: 0; border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font: inherit; font-weight: 600; cursor: pointer; }
.envoyer:disabled { opacity: 0.5; cursor: not-allowed; }
.envoyer:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.mon-point--fait { display: flex; align-items: center; gap: var(--space-3); }
.mon-point__pastille { display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 50%; background: color-mix(in srgb, var(--c-fait) 18%, transparent); color: var(--c-fait); }

.vide { margin: 0; color: var(--ink-muted); text-align: center; }
.journees { list-style: none; margin: 0; padding: 0 0 0 var(--space-5); display: grid; gap: var(--space-6); border-left: 2px solid var(--line); margin-left: 7px; }
.journee { display: grid; gap: var(--space-3); }
.journee__titre { position: relative; margin: 0; font-size: var(--type-body-size); }
.journee__titre::first-letter { text-transform: uppercase; }
.journee__point { position: absolute; left: calc(-1 * var(--space-5) - 9px); top: 0.35em; width: 12px; height: 12px; border-radius: 50%; background: var(--surface); border: 2px solid var(--ink-muted); }
.journee:first-child .journee__point { border-color: var(--c-fait); background: var(--c-fait); }
.reponses { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr)); gap: var(--space-3); }
.reponse { display: grid; gap: var(--space-3); align-content: start; padding: var(--space-4); }
.reponse--bloque { box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--c-bloque) 45%, transparent); }
.reponse__tete { display: flex; align-items: center; gap: var(--space-2); }
.reponse__nom { font-weight: 700; }
.reponse__heure { margin-inline-start: auto; color: var(--ink-muted); font-size: 0.8125rem; }
.reponse__blocs { margin: 0; display: grid; gap: var(--space-3); }
.bloc { display: grid; gap: 2px; padding-left: var(--space-3); border-left: 2px solid var(--c); }
.bloc--fait { --c: var(--c-fait); }
.bloc--prevu { --c: var(--c-prevu); }
.bloc--bloque { --c: var(--c-bloque); padding: var(--space-2) var(--space-3); border-radius: 0 var(--radius-sm) var(--radius-sm) 0; background: color-mix(in srgb, var(--c-bloque) 10%, transparent); }
.bloc dt { display: flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; }
.bloc dt :deep(svg) { color: var(--c); }
.bloc dd { margin: 0; overflow-wrap: anywhere; }
.bloc__aide { justify-self: start; margin-top: var(--space-1); color: var(--proof-ink); font-size: var(--type-body-sm-size); font-weight: 700; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }

@media (max-width: 1100px) {
  .soir__equipe { grid-template-columns: auto minmax(0, 1fr); }
  .soir__avatars { grid-column: 1 / -1; flex-wrap: wrap; }
}
@media (max-width: 760px) {
  .soir { padding: var(--space-4); }
  .mon-point__champs { grid-template-columns: minmax(0, 1fr); }
  .mon-point__pied { flex-direction: column; align-items: stretch; }
}
@media (prefers-reduced-motion: reduce) { .soir__jauge span { transition: none; } }
</style>
