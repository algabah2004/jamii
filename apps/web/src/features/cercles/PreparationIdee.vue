<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PiecesJointes from '../../components/texte/PiecesJointes.vue'
import { envoyerFichier, type PieceJointe } from '../../components/texte/fichiers'
import { BACKLOG_MAX, lireBacklogCsv, type ErreurCsv, type TacheBacklog } from './backlogCsv'
const props = defineProps<{ cercleId: string; document: PieceJointe | null; backlog: TacheBacklog[] }>()
const emit = defineEmits<{ 'update:document': [PieceJointe | null]; 'update:backlog': [TacheBacklog[]]; occupe: [boolean]; invalide: [boolean] }>()
const { t } = useI18n()
const envoi = ref(false), erreur = ref<string | null>(null), erreursCsv = ref<ErreurCsv[]>([]), nomCsv = ref('')
let actif = true
onBeforeUnmount(() => { actif = false })
async function chargerDocument(e: Event) {
  const champ = e.target as HTMLInputElement, fichier = champ.files?.[0]
  if (!fichier || envoi.value) return
  erreur.value = null; envoi.value = true; emit('occupe', true)
  const r = await envoyerFichier(fichier, undefined, props.cercleId)
  if (!actif) return
  if (r.ok) emit('update:document', r.piece)
  else erreur.value = t(`fichiers.erreurs.${r.code}`, { nom: fichier.name })
  envoi.value = false; emit('occupe', false); champ.value = ''
}
async function chargerCsv(e: Event) {
  const champ = e.target as HTMLInputElement, fichier = champ.files?.[0]
  if (!fichier || envoi.value) return
  erreursCsv.value = []; erreur.value = null; emit('invalide', false)
  if (fichier.size > 1024 * 1024) { erreur.value = t('cercles.dossier.csvTaille'); emit('invalide', true); champ.value = ''; return }
  envoi.value = true; emit('occupe', true)
  try {
    const contenu = await fichier.text()
    if (!actif) return
    const r = lireBacklogCsv(contenu)
    erreursCsv.value = r.erreurs
    emit('invalide', r.erreurs.length > 0)
    if (!r.erreurs.length) { emit('update:backlog', r.taches); nomCsv.value = fichier.name }
  } catch { if (actif) { erreur.value = t('cercles.dossier.csvLecture'); emit('invalide', true) } }
  finally { if (actif) { envoi.value = false; emit('occupe', false); champ.value = '' } }
}
const modele = 'data:text/csv;charset=utf-8,' + encodeURIComponent('titre,description,priorite,echeance\nConcevoir les écrans,Préparer les parcours principaux,haute,\nCréer la connexion,Permettre aux membres de se connecter,normale,\n')
</script>

<template>
  <section class="preparation-idee" :aria-label="t('cercles.dossier.titre')">
    <div class="dossier-bloc">
      <div class="dossier-tete"><h3>{{ t('cercles.dossier.cahier') }}</h3><span>{{ t('cercles.dossier.facultatif') }}</span></div>
      <p>{{ t('cercles.dossier.cahierAide') }}</p>
      <PiecesJointes v-if="document" :pieces="[document]" />
      <div class="dossier-actions">
        <label class="choisir" :class="{ 'choisir--occupe': envoi }" for="idee-document">{{ document ? t('cercles.dossier.remplacer') : t('cercles.dossier.joindre') }}</label>
        <input id="idee-document" type="file" accept=".pdf,.docx,.txt,.md" :disabled="envoi" class="visuellement-cache" data-test="cahier-charges" @change="chargerDocument" />
        <button v-if="document" type="button" :disabled="envoi" @click="emit('update:document', null)">{{ t('cercles.dossier.retirer') }}</button>
      </div>
      <span class="dossier-note">{{ t('cercles.dossier.visibilite') }}</span>
    </div>
    <div class="dossier-bloc">
      <div class="dossier-tete"><h3>{{ t('cercles.dossier.backlog') }}</h3><span>{{ t('cercles.dossier.facultatif') }}</span></div>
      <p>{{ t('cercles.dossier.backlogAide', { n: BACKLOG_MAX }) }}</p>
      <div class="dossier-actions">
        <label class="choisir" for="idee-backlog">{{ t('cercles.dossier.importer') }}</label>
        <input id="idee-backlog" type="file" accept=".csv,text/csv" :disabled="envoi" class="visuellement-cache" data-test="backlog-csv" @change="chargerCsv" />
        <a :href="modele" download="modele-backlog-jamii.csv">{{ t('cercles.dossier.modele') }}</a>
      </div>
      <button v-if="erreursCsv.length || erreur" type="button" @click="erreursCsv = []; erreur = null; emit('invalide', false)">{{ t('cercles.dossier.ignorer') }}</button>
      <ul v-if="erreursCsv.length" class="erreurs-csv" role="alert" data-test="erreurs-backlog"><li v-for="(e, i) in erreursCsv.slice(0, 8)" :key="i">{{ t('cercles.dossier.ligne', { n: e.ligne }) }} : {{ t(`cercles.dossier.csvErreurs.${e.code}`) }}</li></ul>
      <div v-if="backlog.length" class="apercu-backlog" data-test="apercu-backlog">
        <div class="dossier-tete"><strong>{{ t('cercles.dossier.tachesPretes', { n: backlog.length }) }}</strong><button type="button" :disabled="envoi" @click="emit('update:backlog', []); nomCsv = ''; erreursCsv = []">{{ t('cercles.dossier.retirer') }}</button></div>
        <span v-if="nomCsv" class="dossier-note">{{ nomCsv }}</span>
        <ol><li v-for="(tache, i) in backlog" :key="i"><strong>{{ tache.titre }}</strong><span>{{ t(`cercles.tache.priorites.${tache.priorite ?? 'normale'}`) }}<template v-if="tache.echeance"> · {{ tache.echeance }}</template></span><p v-if="tache.description">{{ tache.description }}</p></li></ol>
        <p class="dossier-note">{{ t('cercles.dossier.creationAuTour') }}</p>
      </div>
    </div>
    <p v-if="envoi" role="status">{{ t('fichiers.envoi') }}</p>
    <p v-if="erreur" role="alert" class="erreurs-csv">{{ erreur }}</p>
  </section>
</template>
<style scoped>
.preparation-idee { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.2fr); align-items: start; gap: var(--space-4); min-width: 0; }
.preparation-idee > p { grid-column: 1 / -1; }
@media (max-width: 760px) { .preparation-idee { grid-template-columns: minmax(0, 1fr); } }
.dossier-bloc { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); }
.dossier-tete { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2); }
.dossier-tete h3 { margin: 0; font-size: 1rem; }
.dossier-tete > span, .dossier-note { color: var(--ink-muted); font-size: .875rem; }
p { margin: 0; font-size: .9375rem; line-height: 1.5; }
.dossier-actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); }
.choisir { display: inline-flex; align-items: center; min-height: 40px; padding: 6px 14px; background: var(--surface); color: var(--ink); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); cursor: pointer; font-weight: 600; font-size: .9375rem; }
.dossier-actions:has(input:focus-visible) .choisir { outline: 2px solid var(--focus); outline-offset: 2px; }
a, button { color: var(--action-ink); font: inherit; font-size: .875rem; }
button { border: 0; background: transparent; text-decoration: underline; cursor: pointer; }
button:disabled, .choisir--occupe { opacity: .5; }
.apercu-backlog { display: grid; gap: var(--space-2); padding: var(--space-3); background: var(--surface); border-radius: var(--radius-md); }
.apercu-backlog ol { margin: 0; padding-left: 24px; max-height: 280px; overflow: auto; }
.apercu-backlog li { padding: var(--space-2) 0; border-bottom: 1px solid var(--line); }
.apercu-backlog li > span { display: block; color: var(--ink-muted); font-size: .875rem; }
.apercu-backlog li p { font-size: .875rem; white-space: pre-wrap; }
.erreurs-csv { color: var(--danger); font-size: .875rem; }
</style>
