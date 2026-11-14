<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JChamp } from '../../design-system'
import {
  inviterJure,
  LIMITES_JURY,
  lienExportNotes,
  lireIdentifiant,
  listerJures,
  listerRendus,
  suivreNotation,
  type Hackathon,
  type Jure,
  type Notation,
  type Rendu,
} from './hackathons'

/*
 * Le jury, côté organisateur (contracts/hackathons.yaml, tag « jury ») : inviter des jurés par le lien de leur profil,
 * suivre qui a noté quoi, lire le classement proposé (score pondéré) et exporter les notes en CSV.
 */
const props = defineProps<{ hackathon: Hackathon }>()
const { t, locale } = useI18n()

const jures = ref<Jure[]>([])
const notation = ref<Notation | null>(null)
const rendus = ref<Map<string, Rendu>>(new Map())
const indisponible = ref(false)
const profil = ref('')
const groupe = ref('')
const envoi = ref(false)
const code = ref<string | null>(null)

const peutInviter = computed(() => (props.hackathon.moi.actions as string[]).includes('inviter_jure'))
const voitClassement = computed(() => ['evaluation', 'resultats'].includes(props.hackathon.statut))
const identifiant = computed(() => lireIdentifiant(profil.value))
const score = (n: number) => new Intl.NumberFormat(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
const message = computed(() => {
  if (!code.value) return ''
  const connus = ['etat_invalide', 'validation']
  return t(`hackathons.jury.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

async function charger() {
  try {
    jures.value = await listerJures(props.hackathon.id)
    if (voitClassement.value) {
      const [n, liste] = await Promise.all([suivreNotation(props.hackathon.id), listerRendus(props.hackathon.id)])
      notation.value = n
      rendus.value = new Map(liste.map((r) => [r.id, r]))
    }
    indisponible.value = false
  } catch {
    indisponible.value = true
  }
}
watch(() => [props.hackathon.id, props.hackathon.statut], () => void charger(), { immediate: true })

async function inviter() {
  if (!identifiant.value || envoi.value) return
  envoi.value = true
  code.value = null
  try {
    const r = await inviterJure(props.hackathon.id, identifiant.value, groupe.value.trim() || null)
    if (r.ok) {
      profil.value = ''
      groupe.value = ''
      await charger()
    } else code.value = r.code ?? 'autre'
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section class="carte jury" aria-labelledby="jury-titre" data-test="jury">
    <h2 id="jury-titre">{{ t('hackathons.jury.titre') }}</h2>
    <p v-if="indisponible" role="alert" class="texte-discret">{{ t('hackathons.jury.indisponible') }}</p>
    <p v-else-if="!jures.length" class="texte-discret" data-test="aucun-jure">{{ t('hackathons.jury.aucun') }}</p>
    <ul v-else class="liste">
      <li v-for="j in jures" :key="j.personne.id" data-test="jure">
        <span class="personne"><JAvatar :nom="j.personne.nom" :url="j.personne.avatarUrl" :taille="24" /> {{ j.personne.nom }}</span>
        <span v-if="j.groupe" class="texte-discret">{{ t('hackathons.jury.groupe', { groupe: j.groupe }) }}</span>
        <span class="texte-discret" data-test="avancement-jure">{{ t('hackathons.jury.avancement', { n: j.rendusNotes, total: j.rendusNotes + j.rendusATraiter }) }}</span>
      </li>
    </ul>

    <form v-if="peutInviter" class="formulaire" data-test="form-jure" @submit.prevent="inviter">
      <h3>{{ t('hackathons.jury.inviter') }}</h3>
      <JChamp
        v-model="profil"
        :libelle="t('hackathons.jury.profil')"
        :aide="t('hackathons.jury.aideProfil')"
        :erreur="profil.trim() && !identifiant ? t('hackathons.jury.profilInvalide') : undefined"
        requis
      />
      <JChamp v-model="groupe" :libelle="t('hackathons.jury.champGroupe')" :aide="t('hackathons.jury.aideGroupe')" :maxlength="LIMITES_JURY.groupeMax" />
      <div>
        <JBouton type="submit" variante="secondaire" :occupe="envoi" :desactive="!identifiant" data-test="inviter-jure">{{ t('hackathons.jury.envoyer') }}</JBouton>
      </div>
    </form>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-jury">{{ message }}</p>

    <section v-if="voitClassement && notation" aria-labelledby="classement-titre" data-test="classement">
      <h3 id="classement-titre">{{ t('hackathons.jury.classement') }}</h3>
      <p v-if="!notation.classement.length" class="texte-discret">{{ t('hackathons.jury.aucuneNote') }}</p>
      <div v-else class="defilement">
      <table>
        <caption class="texte-discret">{{ t('hackathons.jury.aideClassement') }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ t('hackathons.jury.rang') }}</th>
            <th scope="col">{{ t('hackathons.jury.projet') }}</th>
            <th scope="col">{{ t('hackathons.jury.score') }}</th>
            <th scope="col">{{ t('hackathons.jury.jures') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in notation.classement" :key="c.renduId" data-test="ligne-classement">
            <td>{{ c.rang }}</td>
            <td>{{ rendus.get(c.renduId)?.titre ?? '—' }}</td>
            <td>{{ score(c.scorePondere) }} / 5</td>
            <td>{{ c.nombreDeJures }}</td>
          </tr>
        </tbody>
      </table>
      </div>
      <p>
        <a :href="lienExportNotes(hackathon.id)" download="notes.csv" data-test="export-notes">{{ t('hackathons.jury.exporter') }}</a>
      </p>
    </section>
  </section>
</template>

<style scoped>
.jury, .formulaire { display: grid; gap: var(--space-4); }
.jury { padding: var(--space-8); }
.jury h2 { margin: 0; font-size: 1.25rem; }
.jury h3 { margin: 0 0 var(--space-2); font-size: 1rem; }
.liste { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.liste li { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.personne { display: flex; gap: var(--space-2); align-items: center; font-weight: 600; }
.texte-discret { margin: 0; }
.defilement { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
caption { text-align: start; margin-bottom: var(--space-2); }
th, td { text-align: start; padding: var(--space-2); border-bottom: 1px solid var(--line-strong); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
