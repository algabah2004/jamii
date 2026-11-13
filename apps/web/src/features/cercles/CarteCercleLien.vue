<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'
import { JBouton } from '../../design-system'
import { avantTirage, lireCercle, rejoindreCercle, type Cercle } from './cercles'

/*
 * Un texte (publication, réponse) qui contient le lien d'un cercle : on montre le cercle tel qu'il est maintenant,
 * avec « Rejoindre le cercle » s'il reste de la place. Au plus 3 cercles par texte.
 */
const props = defineProps<{ texte: string }>()
const { t } = useI18n()
const routeur = useRouter()

const MOTIF = /\/cercles\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/gi
const ids = computed(() => [...new Set([...props.texte.matchAll(MOTIF)].map((m) => m[1]!.toLowerCase()))].slice(0, 3))

const cercles = ref<Cercle[]>([])
watch(ids, async (liste) => {
  const lus = await Promise.all(liste.map((id) => lireCercle(id).catch(() => null)))
  cercles.value = lus.filter((c): c is Cercle => !!c)
}, { immediate: true })

const accueille = (c: Cercle) => !c.moi.estMembre && avantTirage(c).manquants > 0 && !c.tirage
const action = ref<string | null>(null)
const erreur = ref<string | null>(null)
async function rejoindre(c: Cercle) {
  action.value = c.id
  erreur.value = null
  try {
    const r = await rejoindreCercle(c.id)
    if (r.ok || r.code === 'deja_membre') await routeur.push(`/cercles/${c.id}`)
    else erreur.value = t(`cercles.erreurs.${r.code ?? 'autre'}`, t('cercles.erreurs.autre'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    action.value = null
  }
}
</script>

<template>
  <div v-if="cercles.length" class="cartes-cercle">
    <div v-for="c in cercles" :key="c.id" class="carte-cercle" data-test="carte-cercle">
      <span class="carte-cercle__titre">{{ t('cercles.partage.carte') }}</span>
      <RouterLink :to="`/cercles/${c.id}`" class="carte-cercle__nom">{{ c.nom }}</RouterLink>
      <span class="texte-discret">{{ c.ville }} · {{ t('cercles.membres', { n: c.membres.length, taille: c.taille }) }}</span>
      <span class="places" aria-hidden="true"><i v-for="n in c.taille" :key="n" :class="{ pleine: n <= c.membres.length }" /></span>
      <JBouton v-if="accueille(c)" :occupe="action === c.id" data-test="rejoindre-depuis-publication" @click="rejoindre(c)">{{ t('cercles.rejoindre') }}</JBouton>
      <RouterLink v-else :to="`/cercles/${c.id}`" class="j-bouton j-bouton--secondaire">{{ t('cercles.ouvrir') }}</RouterLink>
    </div>
    <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
  </div>
</template>

<style scoped>
.cartes-cercle { display: grid; gap: var(--space-3); }
.carte-cercle { display: grid; gap: var(--space-1); justify-items: start; max-width: 26rem; padding: var(--space-4); border-radius: var(--radius-lg); border: 1px solid color-mix(in srgb, var(--action) 40%, var(--line)); background: color-mix(in srgb, var(--action) 6%, var(--surface)); }
.carte-cercle__titre { font-size: var(--type-body-sm-size); color: var(--ink-muted); }
.carte-cercle__nom { font-weight: 700; font-size: 1.125rem; color: var(--ink); }
.places { display: flex; gap: 6px; margin-block: var(--space-1) var(--space-2); }
.places i { width: 14px; height: 14px; border-radius: 50%; border: 2px dashed var(--line-strong); }
.places i.pleine { border: 0; background: var(--action); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
