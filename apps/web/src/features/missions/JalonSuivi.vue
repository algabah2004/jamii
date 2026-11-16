<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { JBouton, JChamp, JDeplier } from '../../design-system'
import { formaterDate, useAction } from './action'
import LivrerJalon from './LivrerJalon.vue'
import { enRetard } from './prochaineEtape'
import { accepterJalon, declarerPaiement, demanderRetouche, formaterMontant, LIMITES_SUIVI, type Jalon, type Mission } from './missions'

/*
 * Un jalon du devis : où il en est, ce qui a été livré, et ce que la personne peut en faire maintenant.
 * Les actions de `moi.actions` valent pour toute la mission ; on les rapporte ici au jalon d'après son statut.
 */
const props = defineProps<{ mission: Mission; jalon: Jalon }>()
const emit = defineEmits<{ maj: [mission: Mission] }>()
const { t, locale } = useI18n()
const { envoi, message, agir } = useAction((m) => emit('maj', m))

const peut = (nom: string) => (props.mission.moi.actions as string[]).includes(nom)
const statut = computed(() => props.jalon.statut)
/** La couleur de la pastille : bleu en cours, orange livré (à vérifier), vert accepté. */
const TONS: Record<string, string | undefined> = { a_faire: undefined, en_cours: 'bleu', livre: 'orange', accepte: 'vert' }
/** L'heure de la lecture de la page : un jalon pas livré dont la date est passée est en retard. */
const maintenant = Date.now()
const retard = computed(() => enRetard(props.jalon, maintenant))
const retouchesRestantes = computed(() => props.mission.travail?.retouchesRestantes ?? 0)

const peutLivrer = computed(() => peut('livrer') && statut.value === 'en_cours')
const peutRepondre = computed(() => peut('accepter_jalon') && statut.value === 'livre')
const peutRetoucher = computed(() => peut('demander_retouche') && statut.value === 'livre' && retouchesRestantes.value > 0)
const peutPayer = computed(() => peut('declarer_paye') && ['livre', 'accepte'].includes(statut.value) && props.jalon.paiement === 'non_paye')
const peutRecevoir = computed(() => peut('declarer_recu') && props.jalon.paiement === 'declare_paye')
/** L'acceptation automatique n'est annoncée que si elle est encore à venir, et hors désaccord. */
const acceptationAVenir = computed(() => {
  const le = props.jalon.acceptationAutomatiqueLe
  return statut.value === 'livre' && Boolean(le) && !props.mission.travail?.desaccord && Date.parse(le!) > Date.now()
})

const retoucheOuverte = ref(false)
const raison = ref('')

const date = (iso: string | null | undefined) => (iso ? formaterDate(iso, locale.value) : null)
const cible = () => [props.mission.id, props.jalon.id, props.mission.version] as const

const accepter = () => agir(() => accepterJalon(...cible()))
const payer = (sens: 'paye' | 'recu') => agir(() => declarerPaiement(...cible(), sens))
async function retoucher() {
  if (raison.value.trim().length < LIMITES_SUIVI.raisonMin) return
  if (await agir(() => demanderRetouche(...cible(), raison.value.trim()))) {
    raison.value = ''
    retoucheOuverte.value = false
  }
}
</script>

<template>
  <li :id="`jalon-${jalon.id}`" class="jalon" :data-statut="statut" data-test="jalon">
    <header class="tete">
      <h3><span class="numero" aria-hidden="true">{{ statut === 'accepte' ? '✓' : jalon.ordre }}</span>{{ t('missions.suivi.jalon', { n: jalon.ordre }) }} · {{ jalon.titre }}</h3>
      <p class="etiquettes">
        <span v-if="retard" class="etiquette etiquette--retard m-pastille" data-ton="rouge" data-test="en-retard">{{ t('missions.suivi.enRetard') }}</span>
        <span class="etiquette m-pastille" :data-ton="TONS[statut]" data-test="statut-jalon">{{ t(`missions.suivi.statuts.${statut}`) }}</span>
      </p>
    </header>

    <dl class="faits m-tuiles">
      <div>
        <dt>{{ t('missions.suivi.montant') }}</dt>
        <dd class="m-fort">{{ formaterMontant(jalon.montant, locale) }}</dd>
      </div>
      <div v-if="jalon.echeance">
        <dt>{{ t('missions.suivi.echeance') }}</dt>
        <dd data-test="echeance">{{ date(jalon.echeance) }}</dd>
      </div>
      <div>
        <dt>{{ t('missions.suivi.paiement') }}</dt>
        <dd data-test="paiement">{{ t(`missions.suivi.paiements.${jalon.paiement}`) }}</dd>
      </div>
    </dl>
    <p v-if="jalon.description" class="description">{{ jalon.description }}</p>

    <p v-if="acceptationAVenir" class="note m-note" data-test="acceptation-automatique">
      {{ t('missions.suivi.acceptationAutomatique', { date: date(jalon.acceptationAutomatiqueLe) }) }}
    </p>
    <p v-if="statut === 'accepte' && jalon.accepteLe" class="texte-discret" data-test="accepte-le">
      {{ t(jalon.acceptationAutomatique ? 'missions.suivi.accepteAutomatiquement' : 'missions.suivi.accepteLe', { date: date(jalon.accepteLe) }) }}
    </p>

    <ol v-if="jalon.livraisons.length" class="livraisons" :aria-label="t('missions.suivi.livraisons')">
      <li v-for="l in jalon.livraisons" :key="l.livreeLe" data-test="livraison">
        <p class="texte-discret">{{ t('missions.suivi.livreLe', { date: date(l.livreeLe) }) }}</p>
        <!-- Texte libre : affiché échappé, avec ses retours à la ligne. -->
        <p class="message">{{ l.message }}</p>
        <ul v-if="l.liens.length" class="liens">
          <li v-for="lien in l.liens" :key="lien"><a :href="lien" target="_blank" rel="noopener noreferrer nofollow">{{ lien }}</a></li>
        </ul>
        <p v-if="l.retouche" class="retouche" data-test="retouche">{{ t('missions.suivi.retoucheDemandee') }} {{ l.retouche }}</p>
      </li>
    </ol>

    <LivrerJalon v-if="peutLivrer" :mission="mission" :jalon="jalon" @maj="emit('maj', $event)" />

    <div v-if="peutRepondre || peutPayer || peutRecevoir" class="actions">
      <JBouton v-if="peutRepondre" :occupe="envoi" data-test="accepter" @click="accepter">{{ t('missions.suivi.accepter') }}</JBouton>
      <JBouton v-if="peutRetoucher" variante="secondaire" :aria-expanded="retoucheOuverte" data-test="ouvrir-retouche" @click="retoucheOuverte = !retoucheOuverte">
        {{ t('missions.suivi.retouche.ouvrir', { n: retouchesRestantes }, retouchesRestantes) }}
      </JBouton>
      <JBouton v-if="peutPayer" variante="secondaire" :occupe="envoi" data-test="payer" @click="payer('paye')">{{ t('missions.suivi.payer') }}</JBouton>
      <JBouton v-if="peutRecevoir" variante="secondaire" :occupe="envoi" data-test="recu" @click="payer('recu')">{{ t('missions.suivi.recu') }}</JBouton>
    </div>
    <p v-if="peutRepondre && !peutRetoucher" class="texte-discret" data-test="plus-de-retouche">{{ t('missions.suivi.retouche.epuisees') }}</p>
    <p v-if="peutPayer" class="texte-discret">{{ t('missions.suivi.payerAide') }}</p>

    <JDeplier>
      <form v-if="retoucheOuverte" class="retouche-form" data-test="form-retouche" @submit.prevent="retoucher">
        <JChamp
          v-model="raison"
          :libelle="t('missions.suivi.retouche.raison')"
          :aide="t('missions.suivi.retouche.aide')"
          multiligne
          requis
          :maxlength="LIMITES_SUIVI.raisonMax"
        />
        <div>
          <JBouton type="submit" :occupe="envoi" :desactive="raison.trim().length < LIMITES_SUIVI.raisonMin" data-test="envoyer-retouche">
            {{ t('missions.suivi.retouche.envoyer') }}
          </JBouton>
        </div>
      </form>
    </JDeplier>
    <p v-if="message" role="alert" class="erreur" data-test="erreur-jalon">{{ message }}</p>
  </li>
</template>

<style scoped>
/* Chaque jalon : une bande de couleur à gauche selon où il en est. */
.jalon {
  --etat: var(--line-strong);
  display: grid; gap: var(--space-4); padding: var(--space-5, 20px) var(--space-6);
  border: 1px solid var(--line); border-inline-start: 5px solid var(--etat); border-radius: 16px; background: var(--surface);
}
.jalon[data-statut='en_cours'] { --etat: var(--action); }
.jalon[data-statut='livre'] { --etat: var(--cantaloupe); background: color-mix(in srgb, var(--cantaloupe) 5%, var(--surface)); }
.jalon[data-statut='accepte'] { --etat: var(--succes, #1f9d62); }
.tete { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; justify-content: space-between; }
.tete h3 { margin: 0; display: flex; gap: var(--space-3); align-items: center; font-size: 1.0625rem; }
.numero {
  flex: none; display: grid; place-items: center; inline-size: 2rem; block-size: 2rem; border-radius: 50%;
  border: 2px solid var(--etat); color: var(--ink); font-size: .875rem; font-weight: 800;
}
.jalon[data-statut='accepte'] .numero { background: var(--etat); color: #fff; }
.jalon { scroll-margin-block-start: 6rem; }
.etiquettes { margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-2); }
.etiquette { margin: 0; }
/* « En retard » : le mot le dit, la couleur ne fait que le souligner. */
.etiquette--retard { border: 1px solid var(--danger); color: var(--danger); font-weight: 700; }
.description, .message { margin: 0; white-space: pre-line; }
.livraisons { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
.livraisons > li { padding: var(--space-3) var(--space-4); border-radius: 12px; background: var(--surface-raised); display: grid; gap: var(--space-1); }
.livraisons p { margin: 0; }
.liens { margin: 0; padding-inline-start: var(--space-4); overflow-wrap: anywhere; }
.retouche { font-weight: 600; padding-block-start: var(--space-2); border-block-start: 1px dashed var(--line-strong); }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.retouche-form { display: grid; gap: var(--space-4); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
.texte-discret { margin: 0; }
</style>
