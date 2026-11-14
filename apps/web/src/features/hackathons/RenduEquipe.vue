<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JSquelette } from '../../design-system'
import { estHttps, formaterDate, LIMITES_RENDU, lireRendu, rendre, type Hackathon, type Rendu } from './hackathons'

/*
 * Le rendu de mon équipe (contracts/hackathons.yaml, `lireRendu` et `rendre`) : le produit en ligne, le dépôt, une
 * vidéo et les réponses aux questions. On peut le modifier jusqu'à l'heure limite. Le lien est vérifié après
 * l'enregistrement : une panne réseau n'empêche jamais de rendre, et l'écran dit si le lien répond.
 */
const props = defineProps<{ hackathon: Hackathon; equipeId: string }>()
const { t, locale } = useI18n()

const rendu = ref<Rendu | null>(null)
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const titre = ref('')
const description = ref('')
const lienProduit = ref('')
const depot = ref('')
const video = ref('')
const reponses = ref<string[]>([])
const envoi = ref(false)
const code = ref<string | null>(null)
const enregistre = ref(false)

const peutRendre = computed(() => (props.hackathon.moi.actions as string[]).includes('rendre'))
const lienFacultatifValide = (texte: string) => !texte.trim() || estHttps(texte.trim())
const pret = computed(
  () =>
    titre.value.trim().length >= LIMITES_RENDU.titreMin &&
    description.value.trim().length >= LIMITES_RENDU.descriptionMin &&
    estHttps(lienProduit.value.trim()) &&
    lienFacultatifValide(depot.value) &&
    lienFacultatifValide(video.value),
)
const message = computed(() => {
  if (!code.value) return ''
  const connus = ['delai_depasse', 'etat_invalide', 'modification_concurrente', 'lien_invalide', 'adresse_interne', 'validation']
  return t(`hackathons.rendu.erreurs.${connus.includes(code.value) ? code.value : 'autre'}`)
})

function remplir(r: Rendu | null) {
  rendu.value = r
  titre.value = r?.titre ?? ''
  description.value = r?.description ?? ''
  lienProduit.value = r?.lienProduit ?? ''
  depot.value = r?.depot ?? ''
  video.value = r?.video ?? ''
  reponses.value = props.hackathon.questions.map((_, i) => r?.reponses[i] ?? '')
}

async function charger() {
  etat.value = 'chargement'
  try {
    remplir(await lireRendu(props.hackathon.id, props.equipeId))
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
watch(() => [props.hackathon.id, props.equipeId], () => void charger(), { immediate: true })

async function envoyer() {
  if (!pret.value || envoi.value) return
  envoi.value = true
  code.value = null
  enregistre.value = false
  try {
    const r = await rendre(props.hackathon.id, props.equipeId, {
      ...(rendu.value ? { version: rendu.value.version } : {}),
      titre: titre.value.trim(),
      description: description.value.trim(),
      lienProduit: lienProduit.value.trim(),
      depot: depot.value.trim() || null,
      video: video.value.trim() || null,
      reponses: reponses.value.map((x) => x.trim()),
    })
    if (r.ok) {
      remplir(r.valeur)
      enregistre.value = true
    } else code.value = r.code ?? 'autre'
  } catch {
    code.value = 'autre'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section class="carte rendu" aria-labelledby="rendu-titre" data-test="rendu">
    <h2 id="rendu-titre">{{ t('hackathons.rendu.titre') }}</h2>
    <JSquelette v-if="etat === 'chargement'" :nombre="2" />
    <p v-else-if="etat === 'indisponible'" role="alert" class="texte-discret">{{ t('hackathons.rendu.indisponible') }}</p>
    <template v-else>
      <p v-if="rendu" class="etat-lien" :data-repond="String(rendu.lienRepond)" data-test="etat-lien" role="status">
        {{ rendu.lienRepond === null ? t('hackathons.rendu.lienNonVerifie') : rendu.lienRepond ? t('hackathons.rendu.lienRepond') : t('hackathons.rendu.lienNeRepondPas') }}
      </p>
      <p v-if="rendu" class="texte-discret">{{ t('hackathons.rendu.modifieLe', { date: formaterDate(rendu.modifieLe, locale) }) }}</p>
      <p v-else-if="!peutRendre" class="texte-discret" data-test="aucun-rendu">{{ t('hackathons.rendu.aucun') }}</p>

      <form v-if="peutRendre" class="formulaire" data-test="form-rendu" @submit.prevent="envoyer">
        <p class="texte-discret">{{ t('hackathons.rendu.intro', { date: formaterDate(hackathon.calendrier.finRendusLe, locale) }) }}</p>
        <JChamp v-model="titre" :libelle="t('hackathons.rendu.champTitre')" :maxlength="LIMITES_RENDU.titreMax" requis />
        <JChamp
          v-model="description"
          :libelle="t('hackathons.rendu.description')"
          :aide="t('hackathons.rendu.aideDescription', { n: LIMITES_RENDU.descriptionMin })"
          multiligne
          :maxlength="LIMITES_RENDU.descriptionMax"
          requis
        />
        <JChamp
          v-model="lienProduit"
          type="url"
          :libelle="t('hackathons.rendu.lienProduit')"
          :aide="hackathon.deploiementObligatoire ? t('hackathons.rendu.aideLienObligatoire') : t('hackathons.rendu.aideLien')"
          :erreur="lienProduit.trim() && !estHttps(lienProduit.trim()) ? t('hackathons.rendu.lienHttps') : undefined"
          requis
        />
        <JChamp v-model="depot" type="url" :libelle="t('hackathons.rendu.depot')" :erreur="lienFacultatifValide(depot) ? undefined : t('hackathons.rendu.lienHttps')" />
        <JChamp v-model="video" type="url" :libelle="t('hackathons.rendu.video')" :erreur="lienFacultatifValide(video) ? undefined : t('hackathons.rendu.lienHttps')" />
        <JChamp
          v-for="(q, i) in hackathon.questions"
          :key="q"
          v-model="reponses[i]"
          :libelle="q"
          multiligne
          :lignes="3"
          :maxlength="LIMITES_RENDU.reponseMax"
        />
        <div>
          <JBouton type="submit" :occupe="envoi" :desactive="!pret" data-test="rendre">{{ rendu ? t('hackathons.rendu.modifier') : t('hackathons.rendu.envoyer') }}</JBouton>
        </div>
        <p v-if="enregistre" role="status" class="succes" data-test="rendu-enregistre">{{ t('hackathons.rendu.enregistre') }}</p>
      </form>
      <p v-if="message" role="alert" class="erreur" data-test="erreur-rendu">{{ message }}</p>
    </template>
  </section>
</template>

<style scoped>
.rendu, .formulaire { display: grid; gap: var(--space-4); }
.rendu { padding: var(--space-8); }
.rendu h2 { margin: 0; font-size: 1.25rem; }
.texte-discret { margin: 0; }
.etat-lien { margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); border-inline-start: 3px solid var(--line-strong); font-weight: 600; }
.etat-lien[data-repond='true'] { border-color: var(--action); }
.etat-lien[data-repond='false'] { border-color: var(--danger); }
.succes { margin: 0; font-weight: 600; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
