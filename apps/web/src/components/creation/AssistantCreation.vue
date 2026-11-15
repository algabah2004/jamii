<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JBouton } from '../../design-system'

const props = defineProps<{
  titre: string
  etapes: { nom: string; titre: string; aide: string }[]
  valides: boolean[]
  retour: string
  retourLibelle: string
  action: string
  testFormulaire: string
  testAction: string
  occupe?: boolean
}>()
const etape = defineModel<number>('etape', { required: true })
const emit = defineEmits<{ terminer: [] }>()
const { t } = useI18n()
const contenu = ref<HTMLElement | null>(null)
const question = ref<HTMLElement | null>(null)
const sens = ref('avant')
const derniere = computed(() => etape.value === props.etapes.length - 1)
const courante = computed(() => props.etapes[etape.value]!)
watch(etape, (n, avant) => { sens.value = n > avant ? 'avant' : 'arriere' })
function focaliser() {
  const champ = contenu.value?.querySelector<HTMLElement>('input:not([disabled]):not([type="hidden"]), textarea:not([disabled]), select:not([disabled])')
  ;(champ ?? question.value)?.focus({ preventScroll: true })
  const position = question.value?.getBoundingClientRect()
  if (position && (position.top < 100 || position.bottom > window.innerHeight - 80)) question.value?.scrollIntoView?.({ block: 'start' })
}
onMounted(() => void nextTick(focaliser))
function aller(n: number) {
  if (!props.occupe && n >= 0 && n <= etape.value) etape.value = n
}
function avancer() {
  if (props.occupe || !props.valides[etape.value]) return
  if (!derniere.value) etape.value++
  else if (props.valides.every(Boolean)) emit('terminer')
}
</script>

<template>
  <section class="assistant" :aria-label="titre">
    <header class="assistant__haut"><RouterLink :to="retour" class="retour">← {{ retourLibelle }}</RouterLink><span class="rang" aria-live="polite">{{ t('creationGuidee.etape', { n: etape + 1, total: etapes.length }) }}</span></header>
    <div class="progression" role="progressbar" :aria-label="titre" :aria-valuenow="etape + 1" :aria-valuemin="1" :aria-valuemax="etapes.length"><span :style="{ width: `${(etape + 1) / etapes.length * 100}%` }" /></div>
    <div class="scene">
      <form class="carte panneau" :data-test="testFormulaire" novalidate @submit.prevent="avancer">
        <span class="surtitre"><i />{{ titre }}</span>
        <Transition :name="`glisse-${sens}`" mode="out-in" @after-enter="focaliser">
          <div :key="etape" class="etape" :data-test="`assistant-etape-${etape}`">
            <span class="numero" aria-hidden="true">{{ String(etape + 1).padStart(2, '0') }}</span>
            <h1 ref="question" tabindex="-1">{{ courante.titre }}</h1><p class="aide">{{ courante.aide }}</p>
            <div ref="contenu" class="contenu"><slot /></div>
          </div>
        </Transition>
        <div class="erreurs"><slot name="erreurs" /></div>
        <footer class="commandes"><JBouton v-if="etape" variante="secondaire" :desactive="occupe" data-test="assistant-precedent" @click="aller(etape - 1)">← {{ t('creationGuidee.precedent') }}</JBouton><JBouton type="submit" :desactive="!valides[etape]" :occupe="occupe" :data-test="derniere ? testAction : 'assistant-suivant'">{{ derniere ? action : t('creationGuidee.continuer') }} <span v-if="!derniere" aria-hidden="true">→</span></JBouton></footer>
      </form>
      <aside class="vivant" :aria-label="t('creationGuidee.apercu')">
        <div class="apercu"><div class="apercu__tete"><span class="surtitre">{{ t('creationGuidee.apercu') }}</span><span class="apercu__direct"><i />{{ t('creationGuidee.enDirect') }}</span></div><div class="apercu__illustration" aria-hidden="true"><svg viewBox="0 0 240 112"><rect x="49" y="20" width="120" height="76" rx="14" fill="var(--action-soft)" transform="rotate(-10 49 20)"/><rect x="70" y="9" width="120" height="86" rx="14" fill="var(--surface)" stroke="var(--line-strong)"/><path d="M89 33h62M89 47h79M89 61h43" stroke="var(--line-strong)" stroke-width="3" stroke-linecap="round"/><circle cx="183" cy="82" r="20" fill="var(--proof)"/><path d="m174 82 6 6 12-13" fill="none" stroke="var(--on-proof)" stroke-width="2" stroke-linecap="round"/></svg></div><slot name="apercu" /></div>
        <ol class="etapes" :aria-label="t('creationGuidee.parcours')"><li v-for="(e, i) in etapes" :key="i" :class="{ actuelle: i === etape, faite: i < etape }"><button type="button" :disabled="i > etape || occupe" :aria-current="i === etape ? 'step' : undefined" @click="aller(i)"><span aria-hidden="true">{{ i < etape ? '✓' : i + 1 }}</span>{{ e.nom }}</button></li></ol>
        <slot name="conseils" />
      </aside>
    </div>
  </section>
</template>

<style scoped>
.assistant { max-width: 1340px; margin-inline: auto; padding-inline: clamp(0px, 2vw, 24px); }
.assistant__haut { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 18px; }
.retour { text-decoration: none; color: var(--ink-muted); font-size: .875rem; }
.rang { font-size: .8125rem; color: var(--ink-muted); }
.progression { height: 4px; background: var(--line); border-radius: 8px; overflow: hidden; margin-bottom: 28px; }
.progression span { display: block; height: 100%; background: linear-gradient(90deg, var(--action), var(--proof)); border-radius: inherit; transition: width 240ms ease; }
.scene { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(280px, .85fr); gap: 28px; align-items: start; }
.panneau { display: flex; flex-direction: column; min-height: max(540px, calc(100dvh - 240px)); padding: clamp(24px, 4vw, 54px); border-radius: 26px; }
.surtitre { display: flex; align-items: center; gap: 8px; color: var(--ink-muted); font-size: .8125rem; font-weight: 700; text-transform: uppercase; letter-spacing: .08em; }
.surtitre > i, .apercu__direct i { width: 6px; height: 6px; border-radius: 50%; background: var(--proof); flex: none; }
.etape { display: grid; gap: 16px; padding-top: 30px; }
.numero { font-family: var(--font-display); color: var(--proof-ink); font-size: 1rem; font-weight: 700; }
.etape h1 { scroll-margin-top: 135px; font-size: clamp(1.8rem, 3.1vw, 2.8rem); letter-spacing: -.04em; line-height: 1.12; margin: 0; max-width: 22ch; outline: none; }
.aide { color: var(--ink-muted); font-size: .9375rem; line-height: 1.75; margin: 0 0 8px; max-width: 56ch; }
.contenu { display: grid; gap: 24px; }
.contenu :deep(.question-principale input), .contenu :deep(.question-principale textarea) { font-size: clamp(1.125rem, 2vw, 1.5rem); padding: 18px 0; border: 0; border-bottom: 2px solid var(--line-strong); border-radius: 0; background: transparent; box-shadow: none; line-height: 1.5; }
.contenu :deep(.question-principale input:focus), .contenu :deep(.question-principale textarea:focus) { outline: none; border-color: var(--focus); }
.contenu :deep(.question-principale .j-champ--erreur input), .contenu :deep(.question-principale .j-champ--erreur textarea) { border-color: var(--danger); }
.contenu :deep(.question-principale small) { margin-top: 6px; }
.contenu :deep(.grille-champs) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.contenu :deep(.choix label), .contenu :deep(.case) { min-height: 44px; }
.contenu :deep(.recap) { display: grid; gap: 16px; margin: 0; }
.contenu :deep(.recap > div) { display: grid; gap: 5px; border-bottom: 1px solid var(--line); padding-bottom: 16px; }
.contenu :deep(.recap dt) { color: var(--ink-muted); font-size: .8125rem; }
.contenu :deep(.recap dd) { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.contenu :deep(.pastilles) { display: flex; flex-wrap: wrap; gap: 8px; }
.contenu :deep(.pastille) { border: 1px dashed var(--line-strong); background: transparent; color: var(--ink); border-radius: 50px; padding: 7px 13px; cursor: pointer; font: inherit; font-size: .8125rem; }
.contenu :deep(.pastille:hover) { border-style: solid; background: var(--surface-raised); }
.erreurs { margin-top: 16px; }
.erreurs:empty { display: none; }
.commandes { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding-top: 32px; margin-top: auto; }
.commandes :deep(.j-bouton) { border-radius: 50px; font-size: .9375rem; }
.commandes :deep(.j-bouton--principal) { --action: var(--proof); --on-action: var(--on-proof); }
.vivant { position: sticky; top: 92px; display: grid; gap: 20px; min-width: 0; }
.apercu { padding: 24px; background: var(--surface); border: 1px solid var(--line); border-radius: 24px; display: grid; gap: 14px; overflow-wrap: anywhere; }
.apercu__tete { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; align-items: center; }
.apercu__tete .surtitre { font-size: .8125rem; }
.apercu__direct { display: flex; align-items: center; gap: 6px; color: var(--ink-muted); font-size: .8125rem; }
.apercu__direct i { background: var(--action); }
.apercu__illustration { height: 100px; text-align: center; }
.apercu__illustration svg { height: 100%; width: 100%; }
.apercu :deep(h2) { margin: 0; font-size: 1.35rem; line-height: 1.25; letter-spacing: -.025em; }
.apercu :deep(p) { margin: 0; color: var(--ink-muted); line-height: 1.7; font-size: .875rem; white-space: pre-wrap; }
.apercu :deep(.apercu__texte) { display: -webkit-box; -webkit-line-clamp: 4; line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.apercu :deep(.etiquettes) { display: flex; flex-wrap: wrap; gap: 6px; }
.apercu :deep(.etiquettes span) { padding: 4px 9px; border-radius: 8px; background: var(--action-soft); color: var(--action-ink); font-size: .8125rem; }
.apercu :deep(.apercu__info) { border-top: 1px solid var(--line); padding-top: 12px; display: grid; gap: 8px; font-size: .8125rem; color: var(--ink-muted); }
.etapes { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
.etapes button { display: flex; align-items: center; gap: 12px; width: 100%; padding: 9px 12px; border: 1px solid transparent; border-radius: 12px; background: transparent; text-align: start; font: inherit; color: var(--ink-muted); font-size: .875rem; cursor: pointer; }
.etapes button > span { flex: none; display: grid; place-items: center; width: 26px; height: 26px; border: 1px solid var(--line-strong); border-radius: 50%; font-size: .8125rem; }
.etapes .actuelle button { border-color: var(--line); background: var(--surface); color: var(--ink); font-weight: 600; }
.etapes .actuelle button > span { border-color: var(--proof); color: var(--proof-ink); }
.etapes .faite button > span { background: var(--ink); color: var(--bg); border-color: var(--ink); }
.etapes button:disabled { cursor: default; }
.etapes li:not(.actuelle):not(.faite) { opacity: .7; }
.glisse-avant-enter-active, .glisse-arriere-enter-active, .glisse-avant-leave-active, .glisse-arriere-leave-active { transition: transform 200ms ease, opacity 160ms ease; }
.glisse-avant-enter-from, .glisse-arriere-leave-to { opacity: 0; transform: translateX(18px); }
.glisse-arriere-enter-from, .glisse-avant-leave-to { opacity: 0; transform: translateX(-18px); }
@media (max-width: 960px) { .scene { grid-template-columns: minmax(0, 1.4fr) minmax(250px, 1fr); gap: 18px; } .panneau { padding: 28px; } .etape h1 { scroll-margin-top: 180px; } .vivant { top: 142px; } }
@media (max-width: 760px) { .scene { grid-template-columns: 1fr; } .panneau { min-height: 500px; padding: 24px; } .vivant { position: static; } .vivant .etapes { display: none; } .apercu__illustration { height: 72px; } .contenu :deep(.grille-champs) { grid-template-columns: 1fr; } .assistant__haut { align-items: start; } }
@media (prefers-reduced-motion: reduce) { .progression span, .glisse-avant-enter-active, .glisse-arriere-enter-active, .glisse-avant-leave-active, .glisse-arriere-leave-active { transition: none; } }
</style>
