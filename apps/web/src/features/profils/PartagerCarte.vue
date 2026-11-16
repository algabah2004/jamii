<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton } from '../../design-system'
import IconeProfil from './IconeProfil.vue'
import { carteEnPng, HAUTEUR, LARGEUR } from './carteProfil'
import { lienProfil, type Profil } from './profils'

/** « Partager ma carte » (#159) : l'image du profil, à enregistrer ou à envoyer (WhatsApp, LinkedIn…), dans une fenêtre. */
const props = defineProps<{ profil: Profil; publie: boolean }>()
const { t, locale } = useI18n()

const ouvert = ref(false)
const occupe = ref(false)
const apercu = ref<string | null>(null)
const erreur = ref(false)
const copie = ref(false)
const fenetre = ref<HTMLElement | null>(null)
const bouton = ref<HTMLButtonElement | null>(null)
let fichier: File | null = null

const lien = () => lienProfil(props.profil.utilisateurId)
const nomFichier = () =>
  `jamii-${props.profil.nom.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-')}.png`

function clavier(e: KeyboardEvent) {
  if (e.key === 'Escape') fermer()
}

async function preparer() {
  ouvert.value = true
  occupe.value = true
  erreur.value = false
  document.addEventListener('keydown', clavier)
  await nextTick()
  fenetre.value?.focus()
  try {
    const blob = await carteEnPng(props.profil, lien(), {
      langue: locale.value,
      scanner: t('profil.partage.scanner'),
      disponibilite: t(`profil.disponibilite.${props.profil.disponibilite}`),
      chiffres: {
        projets: (n) => t('profil.chiffres.projets', n),
        realisations: (n) => t('profil.chiffres.realisations', n),
        competences: (n) => t('profil.chiffres.competences', n),
      },
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

function fermer() {
  ouvert.value = false
  document.removeEventListener('keydown', clavier)
  bouton.value?.focus()
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

onBeforeUnmount(() => {
  if (apercu.value) URL.revokeObjectURL(apercu.value)
  document.removeEventListener('keydown', clavier)
})
</script>

<template>
  <button ref="bouton" type="button" class="j-bouton j-bouton--secondaire partager" data-test="partager" @click="preparer">
    <IconeProfil nom="partager" />{{ t('profil.partage.bouton') }}
  </button>

  <div v-if="ouvert" class="partage" data-test="partage">
    <div class="partage__voile" aria-hidden="true" @click="fermer" />
    <section ref="fenetre" class="partage__fenetre" role="dialog" aria-modal="true" aria-labelledby="partage-titre" tabindex="-1">
      <header class="partage__entete">
        <div>
          <h2 id="partage-titre">{{ t('profil.partage.titre') }}</h2>
          <p class="texte-discret">{{ t('profil.partage.intro') }}</p>
        </div>
        <button type="button" class="partage__fermer" :aria-label="t('profil.partage.fermer')" @click="fermer"><IconeProfil nom="fermer" /></button>
      </header>
      <p v-if="!publie" class="avertissement" role="note"><IconeProfil nom="cadenas" :taille="16" />{{ t('profil.partage.nonPublie') }}</p>
      <div class="partage__apercu" :aria-busy="occupe">
        <img v-if="apercu" :src="apercu" :alt="t('profil.partage.alt', { nom: profil.nom })" :width="LARGEUR" :height="HAUTEUR" />
        <p v-else-if="erreur" role="alert">{{ t('etat.indisponible') }}</p>
        <div v-else class="partage__attente"><span /><p class="texte-discret">{{ t('profil.partage.preparation') }}</p></div>
      </div>
      <div v-if="apercu" class="barre">
        <a class="j-bouton j-bouton--principal barre__principal" :href="apercu" :download="nomFichier()" data-test="telecharger"><IconeProfil nom="telecharger" />{{ t('profil.partage.telecharger') }}</a>
        <JBouton v-if="partageNatif()" variante="secondaire" @click="partager">{{ t('profil.partage.envoyer') }}</JBouton>
        <JBouton variante="discret" @click="copierLien"><IconeProfil :nom="copie ? 'coche' : 'copier'" :taille="16" /> {{ copie ? t('profil.partage.copie') : t('profil.partage.copier') }}</JBouton>
      </div>
    </section>
  </div>
</template>

<style scoped>
.partager { display: inline-flex; align-items: center; gap: 8px; }
.partage { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; }
.partage__voile { position: absolute; inset: 0; background: color-mix(in srgb, var(--midnight) 50%, transparent); backdrop-filter: blur(3px); }
.partage__fenetre { position: relative; display: grid; gap: 16px; width: min(560px, 100%); max-height: calc(100dvh - 32px); overflow-y: auto; padding: 24px; border-radius: 28px; background: var(--surface); border: 1px solid var(--line); box-shadow: 0 30px 80px color-mix(in srgb, var(--midnight) 35%, transparent); outline: none; animation: monter var(--motion-base) var(--ease-out); }
@keyframes monter { from { transform: translateY(16px) scale(.98); opacity: 0; } }
.partage__entete { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.partage__entete h2 { margin: 0 0 4px; font-size: 1.5rem; letter-spacing: -.03em; }
.partage__entete p { margin: 0; }
.partage__fermer { display: grid; place-items: center; width: 40px; height: 40px; flex: none; border: 1px solid var(--line); border-radius: 50%; background: var(--surface); color: var(--ink); cursor: pointer; }
.partage__apercu { display: grid; place-items: center; aspect-ratio: 1080 / 680; border-radius: 18px; overflow: hidden; background: var(--midnight); box-shadow: 0 18px 40px color-mix(in srgb, var(--midnight) 30%, transparent); }
.partage__apercu img { display: block; width: 100%; height: auto; }
.partage__attente { display: grid; justify-items: center; gap: 12px; }
.partage__attente span { width: 28px; height: 28px; border-radius: 50%; border: 3px solid color-mix(in srgb, var(--action) 30%, transparent); border-top-color: var(--action); animation: tourner .8s linear infinite; }
.partage__attente p { color: #9DB0BC; margin: 0; }
@keyframes tourner { to { transform: rotate(360deg); } }
.avertissement { display: flex; align-items: flex-start; gap: 8px; margin: 0; padding: 12px 14px; border-radius: 14px; background: var(--proof-soft); color: var(--ink); font-size: var(--type-body-sm-size); }
.avertissement .icone-profil { margin-top: 2px; color: var(--proof-ink); }
.barre { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.barre__principal { display: inline-flex; align-items: center; gap: 8px; text-decoration: none; }
@media (prefers-reduced-motion: reduce) { .partage__fenetre { animation: none; } .partage__attente span { animation-duration: 2.4s; } }
</style>
