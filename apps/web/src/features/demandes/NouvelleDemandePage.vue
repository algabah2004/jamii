<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { JBouton, JChamp, JIllustration } from '../../design-system'
import { creerDemande, demandeValide, DUREES, LIMITES, type NouvelleDemande } from './demandes'

/*
 * « Une PME décrit son besoin » (étape 3 de la démo). Écrit pour un commerçant, pas pour un développeur :
 * des mots simples, des exemples à reprendre, et aucune question technique.
 */
const { t, tm, rt } = useI18n()
const router = useRouter()

const demande = ref<NouvelleDemande>({ titre: '', description: '' })
const duree = ref<number | 'inconnue'>('inconnue')
const envoi = ref<'repos' | 'envoi' | 'erreur'>('repos')
const valide = computed(() => demandeValide(demande.value))

/** Des exemples à reprendre d'un clic : ils montrent le niveau de détail attendu. */
const exemples = computed(() => (tm('demandes.exemples') as { titre: string; description: string }[]).map((e) => ({ titre: rt(e.titre), description: rt(e.description) })))
function reprendre(e: { titre: string; description: string }) {
  demande.value = { ...demande.value, titre: e.titre, description: e.description }
}

async function envoyer() {
  if (!valide.value) return
  envoi.value = 'envoi'
  try {
    const creee = await creerDemande({ ...demande.value, dureeSemaines: duree.value === 'inconnue' ? undefined : duree.value })
    if (!creee) return void (envoi.value = 'erreur')
    await router.push(`/demandes/${creee.id}`)
  } catch {
    envoi.value = 'erreur'
  }
}
</script>

<template>
  <section class="nouvelle fond-verre">
    <header class="page-tete motif motif--croix">
      <div class="page-tete__texte">
        <h1>{{ t('demandes.nouvelle.titre') }}</h1>
        <p>{{ t('demandes.nouvelle.intro') }}</p>
      </div>
      <JIllustration nom="question" class="page-tete__illustration" />
    </header>

    <div class="mise-en-page">
      <form class="carte formulaire" data-test="demande" @submit.prevent="envoyer">
        <JChamp
          v-model="demande.titre"
          :libelle="t('demandes.nouvelle.champTitre')"
          :aide="t('demandes.nouvelle.aideTitre')"
          :maxlength="LIMITES.titreMax"
          requis
        />
        <JChamp
          v-model="demande.description"
          :libelle="t('demandes.nouvelle.champDescription')"
          :aide="t('demandes.nouvelle.aideDescription', { n: LIMITES.descriptionMin })"
          multiligne
          :lignes="7"
          :maxlength="LIMITES.descriptionMax"
          requis
        />
        <fieldset class="durees">
          <legend>{{ t('demandes.nouvelle.duree') }}</legend>
          <label v-for="d in [...DUREES, 'inconnue' as const]" :key="d" class="durees__choix">
            <input v-model="duree" type="radio" name="duree" :value="d" />
            <span>{{ d === 'inconnue' ? t('demandes.nouvelle.jeNeSaisPas') : t('demandes.nouvelle.semaines', { n: d }, d) }}</span>
          </label>
        </fieldset>
        <p v-if="envoi === 'erreur'" role="alert" class="erreur">{{ t('demandes.nouvelle.erreur') }}</p>
        <div class="commandes">
          <JBouton type="submit" :desactive="!valide" :occupe="envoi === 'envoi'" data-test="envoyer">{{ t('demandes.nouvelle.envoyer') }}</JBouton>
          <span class="texte-discret">{{ t('demandes.nouvelle.rassurance') }}</span>
        </div>
      </form>

      <aside class="cote">
        <section class="carte bloc" aria-labelledby="exemples">
          <h2 id="exemples">{{ t('demandes.nouvelle.exemplesTitre') }}</h2>
          <ul class="exemples">
            <li v-for="e in exemples" :key="e.titre">
              <button type="button" class="exemple" data-test="exemple" @click="reprendre(e)">
                <strong>{{ e.titre }}</strong>
                <span>{{ e.description }}</span>
              </button>
            </li>
          </ul>
        </section>
        <section class="carte bloc" aria-labelledby="ensuite">
          <h2 id="ensuite">{{ t('demandes.nouvelle.ensuiteTitre') }}</h2>
          <ol class="etapes">
            <li>{{ t('demandes.nouvelle.ensuite1') }}</li>
            <li>{{ t('demandes.nouvelle.ensuite2') }}</li>
            <li>{{ t('demandes.nouvelle.ensuite3') }}</li>
          </ol>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.mise-en-page { display: grid; gap: var(--space-6); grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); align-items: start; }
@media (max-width: 860px) { .mise-en-page { grid-template-columns: 1fr; } }
.formulaire { display: grid; gap: var(--space-6); padding: var(--space-8); }
.durees { border: 0; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.durees legend { font-size: var(--type-label-size); font-weight: var(--type-label-weight); margin-bottom: var(--space-2); }
.durees__choix { position: relative; }
.durees__choix input { position: absolute; opacity: 0; inset: 0; cursor: pointer; }
.durees__choix span {
  display: inline-flex; align-items: center; min-height: 40px; padding: 0 var(--space-4);
  border: 1px solid var(--line-strong); border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--surface) 60%, transparent);
  font-size: var(--type-body-sm-size); font-weight: 600;
  transition: background var(--motion-fast) var(--ease-out), border-color var(--motion-fast) var(--ease-out);
}
.durees__choix input:checked + span { background: var(--action); border-color: var(--action); color: var(--on-action); }
.durees__choix input:focus-visible + span { outline: 2px solid var(--focus); outline-offset: 2px; }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.commandes { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); }

.cote { display: grid; gap: var(--space-4); }
.bloc { display: grid; gap: var(--space-3); padding: var(--space-6); }
.bloc h2 { margin: 0; font-size: 1.0625rem; }
.exemples { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
.exemple {
  width: 100%; display: grid; gap: 2px; text-align: start; cursor: pointer;
  padding: var(--space-3) var(--space-4); border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--surface) 55%, transparent); border: 1px solid transparent;
  transition: border-color var(--motion-fast) var(--ease-out), transform var(--motion-fast) var(--ease-out);
}
.exemple:hover { border-color: var(--action); transform: translateY(-1px); }
.exemple strong { font-size: var(--type-body-sm-size); }
.exemple span { color: var(--ink-muted); font-size: var(--type-body-sm-size); display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.etapes { margin: 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); color: var(--ink-muted); font-size: var(--type-body-sm-size); }
.etapes ::marker { color: var(--ink); font-weight: 700; }
</style>
