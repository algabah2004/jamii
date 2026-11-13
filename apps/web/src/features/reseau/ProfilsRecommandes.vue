<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JAvatar, JBouton, JChamp, JDeplier } from '../../design-system'
import { envoyerMessage, LONGUEUR_MESSAGE, lireProfilsRecommandes, prenom, type ProfilRecommande } from './reseau'

/* Dans un cercle ouvert : les profils qui apportent ce qui manque au cercle, et l'invitation par message. */
const props = defineProps<{ cercleId: string; cercleNom: string }>()
const { t, locale } = useI18n()

const profils = ref<ProfilRecommande[]>([])
const manquantes = ref<string[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
async function charger() {
  try {
    const r = await lireProfilsRecommandes(props.cercleId)
    profils.value = r.elements
    manquantes.value = r.competencesManquantes
    etat.value = 'pret'
  } catch {
    // Les recommandations aident, elles ne bloquent rien : sans elles, la section ne s'affiche pas.
    etat.value = 'indisponible'
  }
}
void charger()

const liste = (mots: readonly string[]) => new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(mots)

// Une invitation à la fois : le message est pré-rempli, on peut le changer avant d'envoyer.
const ouvert = ref<string | null>(null)
const texte = ref('')
const envoi = ref(false)
const erreur = ref<string | null>(null)
const envoye = ref<string | null>(null)

function ouvrir(p: ProfilRecommande) {
  ouvert.value = p.profil.utilisateurId
  erreur.value = null
  envoye.value = null
  const competences = p.competences.length ? p.competences : manquantes.value.slice(0, 2)
  texte.value = t('reseau.profils.messageParDefaut', { nom: prenom(p.profil.nom), cercle: props.cercleNom, competences: liste(competences) })
}

const valide = computed(() => texte.value.trim().length > 0 && texte.value.length <= LONGUEUR_MESSAGE)
async function inviter(p: ProfilRecommande) {
  envoi.value = true
  erreur.value = null
  try {
    const r = await envoyerMessage(p.profil.utilisateurId, texte.value, props.cercleId)
    if (r.ok) {
      p.invite = true
      ouvert.value = null
      envoye.value = t('reseau.profils.envoye', { nom: prenom(p.profil.nom) })
    } else erreur.value = t(`reseau.erreurs.${r.code ?? 'autre'}`, t('reseau.erreurs.autre'))
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <section v-if="etat === 'pret'" class="carte recos" aria-labelledby="recos-titre" data-test="profils-recommandes">
    <h2 id="recos-titre">{{ t('reseau.profils.titre') }}</h2>
    <p v-if="manquantes.length" class="texte-discret">{{ t('reseau.profils.aide', { competences: liste(manquantes) }) }}</p>
    <p v-if="!profils.length" class="texte-discret" data-test="profils-vide">{{ t('reseau.profils.vide') }}</p>
    <p v-if="envoye" role="status" class="succes" data-test="invitation-envoyee">{{ envoye }}</p>

    <ul v-if="profils.length" class="recos__liste">
      <li v-for="p in profils" :key="p.profil.utilisateurId" class="reco">
        <div class="reco__tete">
          <JAvatar :nom="p.profil.nom" :url="p.profil.avatarUrl" :taille="40" />
          <div>
            <strong>{{ p.profil.nom }}</strong>
            <span class="texte-discret">
              {{ [p.profil.ville, p.profil.heuresParSemaine ? t('reseau.heures', { n: p.profil.heuresParSemaine }) : null].filter(Boolean).join(' · ') }}
            </span>
          </div>
          <span v-if="p.invite" class="etiquette reco__invite">{{ t('reseau.profils.invite') }}</span>
          <JBouton v-else-if="ouvert !== p.profil.utilisateurId" variante="secondaire" :data-test="`inviter-${p.profil.utilisateurId}`" @click="ouvrir(p)">
            {{ t('reseau.profils.inviter') }}
          </JBouton>
        </div>
        <!-- Pourquoi ce profil : on le dit, plutôt qu'un score opaque. -->
        <ul class="reco__raisons">
          <li v-for="r in p.raisons" :key="r">{{ t(`reseau.raisonsProfil.${r}`, { competences: liste(p.competences) }) }}</li>
        </ul>
        <ul class="reco__competences" :aria-label="t('reseau.competences')">
          <li v-for="c in p.profil.competences" :key="c" class="etiquette" :class="{ 'reco__competence--manque': p.competences.includes(c) }">{{ c }}</li>
        </ul>
        <JDeplier>
          <form v-if="ouvert === p.profil.utilisateurId" class="reco__form" data-test="form-invitation" @submit.prevent="inviter(p)">
            <JChamp v-model="texte" :libelle="t('reseau.profils.message', { nom: prenom(p.profil.nom) })" multiligne :lignes="3" :maxlength="LONGUEUR_MESSAGE" requis />
            <div class="reco__commandes">
              <JBouton type="submit" :desactive="!valide" :occupe="envoi">{{ t('reseau.profils.envoyer') }}</JBouton>
              <JBouton variante="secondaire" @click="ouvert = null">{{ t('communautes.annuler') }}</JBouton>
            </div>
            <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>
          </form>
        </JDeplier>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.recos { display: grid; gap: var(--space-3); padding: var(--space-6); }
.recos h2 { margin: 0; }
.recos ul { list-style: none; margin: 0; padding: 0; }
.recos__liste { display: grid; gap: var(--space-3); }
.reco { display: grid; gap: var(--space-2); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-lg); }
.reco__tete { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.reco__tete > div { display: grid; flex: 1; min-width: 10rem; }
.reco__raisons { display: grid; gap: 2px; color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.reco__raisons li::before { content: '✓ '; color: var(--action-ink); font-weight: 700; }
.reco__competences { display: flex; flex-wrap: wrap; gap: var(--space-1); }
.reco__competence--manque { background: var(--action-soft); color: var(--action-ink); font-weight: 600; }
.reco__invite { background: var(--action-soft); color: var(--action-ink); }
.reco__form { display: grid; gap: var(--space-3); padding-top: var(--space-2); }
.reco__commandes { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.succes { margin: 0; color: var(--action-ink); font-weight: 600; }
.erreur { margin: 0; color: var(--danger, #b42318); }
</style>
