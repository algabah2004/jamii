<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton } from '../../design-system'
import { carteEnPng } from './carteProfil'
import { lienProfil, type Profil } from './profils'

/** « Partager ma carte » (#159) : l'image du profil, à enregistrer ou à envoyer (WhatsApp, LinkedIn…). */
const props = defineProps<{ profil: Profil; publie: boolean }>()
const { t, locale } = useI18n()

const ouvert = ref(false)
const occupe = ref(false)
const apercu = ref<string | null>(null)
const erreur = ref(false)
const copie = ref(false)
let fichier: File | null = null

const lien = () => lienProfil(props.profil.utilisateurId)
const nomFichier = () =>
  `jamii-${props.profil.nom.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-')}.png`

async function preparer() {
  ouvert.value = true
  occupe.value = true
  erreur.value = false
  try {
    const blob = await carteEnPng(props.profil, lien(), {
      langue: locale.value,
      competences: t('profil.partage.competences'),
      projetsVerifies: (n) => t('profil.partage.projetsVerifies', { n }, n),
      realisations: (n) => t('profil.partage.realisations', { n }, n),
      scanner: t('profil.partage.scanner'),
    })
    fichier = new File([blob], nomFichier(), { type: 'image/png' })
    if (apercu.value) URL.revokeObjectURL(apercu.value)
    apercu.value = URL.createObjectURL(blob)
  } catch {
    erreur.value = true
  } finally {
    occupe.value = false
  }
}

const partageNatif = () => !!fichier && typeof navigator.canShare === 'function' && navigator.canShare({ files: [fichier] })

async function partager() {
  if (!fichier) return
  try {
    await navigator.share({ files: [fichier], title: props.profil.nom, text: t('profil.partage.message', { lien: lien() }) })
  } catch {
    // Partage annulé : rien à faire.
  }
}

async function copierLien() {
  try {
    await navigator.clipboard.writeText(lien())
    copie.value = true
    setTimeout(() => (copie.value = false), 2000)
  } catch {
    copie.value = false
  }
}

onBeforeUnmount(() => apercu.value && URL.revokeObjectURL(apercu.value))
</script>

<template>
  <JBouton variante="secondaire" data-test="partager" @click="preparer">{{ t('profil.partage.bouton') }}</JBouton>

  <section v-if="ouvert" class="carte partage" data-test="partage" :aria-label="t('profil.partage.titre')">
    <div class="partage__entete">
      <h2>{{ t('profil.partage.titre') }}</h2>
      <JBouton variante="discret" @click="ouvert = false">{{ t('profil.partage.fermer') }}</JBouton>
    </div>
    <p v-if="!publie" class="avertissement" role="note">{{ t('profil.partage.nonPublie') }}</p>
    <div class="partage__apercu" :aria-busy="occupe">
      <img v-if="apercu" :src="apercu" :alt="t('profil.partage.alt', { nom: profil.nom })" width="1200" height="630" />
      <p v-else-if="erreur" role="alert">{{ t('etat.indisponible') }}</p>
      <p v-else class="texte-discret">{{ t('profil.partage.preparation') }}</p>
    </div>
    <div v-if="apercu" class="barre">
      <a class="j-bouton-lien" :href="apercu" :download="nomFichier()" data-test="telecharger">{{ t('profil.partage.telecharger') }}</a>
      <JBouton v-if="partageNatif()" variante="secondaire" @click="partager">{{ t('profil.partage.envoyer') }}</JBouton>
      <JBouton variante="discret" @click="copierLien">{{ copie ? t('profil.partage.copie') : t('profil.partage.copier') }}</JBouton>
    </div>
  </section>
</template>

<style scoped>
.partage { display: grid; gap: var(--space-4); grid-column: 1 / -1; }
.partage__entete { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
.partage__entete h2 { margin: 0; font-size: var(--type-h3-size); line-height: var(--type-h3-line); }
.partage__apercu { display: grid; place-items: center; min-height: 10rem; border-radius: var(--radius-md); overflow: hidden; background: var(--surface-raised); }
.partage__apercu img { display: block; width: 100%; height: auto; }
.avertissement { margin: 0; padding: var(--space-3); border-radius: var(--radius-sm); background: var(--proof-soft); color: var(--ink); }
.barre { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: center; }
.j-bouton-lien { display: inline-flex; align-items: center; min-height: 44px; padding: var(--space-2) var(--space-5); border-radius: var(--radius-pill); background: var(--action); color: var(--on-action); font-weight: 600; text-decoration: none; }
</style>
