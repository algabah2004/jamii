<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp } from '../../design-system'
import { LIMITES, publierDemande, resumeValide, type Demande } from './demandes'

/*
 * Publier sa demande (#158). La personne a lu son cahier des charges : elle choisit ce que la communauté verra, un
 * résumé qu'elle peut modifier. Rien d'autre n'est publié (jamais son texte d'origine), et ce choix est explicite :
 * « L'IA propose, l'humain décide ». Le résumé est affiché comme du texte brut.
 */
const props = defineProps<{ demande: Demande; resumePropose?: string }>()
const emit = defineEmits<{ publiee: [demande: Demande] }>()
const { t, te, locale } = useI18n()

const resume = ref((props.resumePropose ?? '').slice(0, LIMITES.resumeMax))
const etat = ref<'repos' | 'envoi' | 'erreur' | 'pas_prete' | 'deja_publiee'>('repos')
const valide = computed(() => resumeValide(resume.value))
const publication = computed(() => props.demande.publication ?? null)
const dateDePublication = computed(() =>
  publication.value ? new Intl.DateTimeFormat(locale.value, { dateStyle: 'long' }).format(new Date(publication.value.publieeLe)) : '',
)

async function publier() {
  if (!valide.value || etat.value === 'envoi') return
  etat.value = 'envoi'
  try {
    const r = await publierDemande(props.demande.id, resume.value)
    if ('demande' in r) return void emit('publiee', r.demande)
    etat.value = r.erreur === 'plan_non_pret' ? 'pas_prete' : r.erreur === 'deja_publiee' ? 'deja_publiee' : 'erreur'
  } catch {
    etat.value = 'erreur'
  }
}

const message = computed(() => {
  if (etat.value === 'pas_prete') return t('demandes.publication.pasPrete')
  if (etat.value === 'deja_publiee') return t('demandes.publication.dejaPubliee')
  return etat.value === 'erreur' ? t('demandes.publication.erreur') : ''
})
</script>

<template>
  <section class="carte publication" aria-labelledby="publication-titre" data-test="publication">
    <h2 id="publication-titre">{{ t('demandes.publication.titre') }}</h2>

    <template v-if="publication">
      <p role="status" class="publication__fait" data-test="publiee">✓ {{ t('demandes.publication.faite', { date: dateDePublication }) }}</p>
      <p class="texte-discret">{{ t('demandes.publication.resumePublie') }}</p>
      <blockquote class="publication__resume" data-test="resume-publie">{{ publication.resume }}</blockquote>
    </template>

    <form v-else class="publication__formulaire" data-test="publication-formulaire" @submit.prevent="publier">
      <p>{{ t('demandes.publication.intro') }}</p>
      <JChamp
        v-model="resume"
        :libelle="t('demandes.publication.champ')"
        :aide="t('demandes.publication.aide', { max: LIMITES.resumeMax })"
        :erreur="resume.length > 0 && !valide ? t('demandes.publication.invalide', { min: LIMITES.resumeMin, max: LIMITES.resumeMax }) : undefined"
        multiligne
        :lignes="4"
        :maxlength="LIMITES.resumeMax"
        requis
      />
      <!-- Ce que la communauté verra, au fil de la frappe : le titre, le résumé et le contexte. Rien d'autre. -->
      <figure class="apercu" data-test="apercu-publication">
        <figcaption class="texte-discret">{{ t('demandes.publication.apercu') }}</figcaption>
        <div class="apercu__carte">
          <span class="apercu__badge">{{ t('demandes.publication.badge') }}</span>
          <strong>{{ demande.titre }}</strong>
          <p :class="{ vide: !resume.trim() }">{{ resume.trim() || t('demandes.publication.apercuVide') }}</p>
          <ul v-if="demande.secteur || demande.ville || demande.dureeSemaines" class="apercu__etiquettes">
            <li v-if="demande.secteur">{{ te(`demandes.secteurs.${demande.secteur}`) ? t(`demandes.secteurs.${demande.secteur}`) : demande.secteur }}</li>
            <li v-if="demande.ville">{{ demande.ville }}</li>
            <li v-if="demande.dureeSemaines">{{ t('demandes.nouvelle.semaines', { n: demande.dureeSemaines }, demande.dureeSemaines) }}</li>
          </ul>
        </div>
        <p class="texte-discret apercu__note">{{ t('demandes.publication.jamais') }}</p>
      </figure>
      <p v-if="message" role="alert" class="publication__erreur" data-test="publication-erreur">{{ message }}</p>
      <div>
        <JBouton type="submit" :desactive="!valide" :occupe="etat === 'envoi'" data-test="publier">{{ t('demandes.publication.bouton') }}</JBouton>
      </div>
    </form>
  </section>
</template>

<style scoped>
.publication { display: grid; gap: var(--space-4); padding: var(--space-6); }
.publication h2 { margin: 0; font-size: 1.0625rem; }
.publication p { margin: 0; }
.publication__formulaire { display: grid; gap: var(--space-4); }
.publication__fait { font-weight: 700; color: var(--success, var(--ink)); }
.publication__resume {
  margin: 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md);
  border-inline-start: 3px solid var(--action); background: color-mix(in srgb, var(--surface) 60%, transparent);
  white-space: pre-line;
}
.publication__erreur { color: var(--danger); font-weight: 600; }
.apercu { margin: 0; display: grid; gap: var(--space-2); }
.apercu figcaption { font-size: var(--type-body-sm-size); font-weight: 600; }
.apercu__carte {
  display: grid; gap: var(--space-2); padding: var(--space-4); border-radius: var(--radius-md);
  border: 1px solid var(--line); border-top: 4px solid var(--cantaloupe); background: var(--surface);
}
.apercu__carte p { white-space: pre-line; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.apercu__carte .vide { font-style: italic; color: color-mix(in srgb, var(--ink) 35%, transparent); }
.apercu__badge { justify-self: start; padding: 2px 10px; border-radius: var(--radius-pill); background: var(--proof-soft); color: var(--proof-ink); font-size: 0.75rem; font-weight: 700; }
.apercu__etiquettes { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.apercu__etiquettes li { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--surface-raised); font-size: 0.75rem; font-weight: 600; }
.apercu__note { font-size: 0.8125rem; }
</style>
