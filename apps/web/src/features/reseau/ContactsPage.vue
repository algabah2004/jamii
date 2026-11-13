<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { JAvatar, JBouton, JSquelette } from '../../design-system'
import { rafraichirActivite } from './activite'
import OngletsReseau from './OngletsReseau.vue'
import { ajouterContact, lireContacts, retirerContact, type Contact } from './reseau'

/* Les demandes reçues d'abord (quelqu'un attend une réponse), puis mes contacts, puis mes demandes en attente. */
const { t } = useI18n()
const recues = ref<Contact[]>([])
const contacts = ref<Contact[]>([])
const envoyees = ref<Contact[]>([])
const etat = ref<'chargement' | 'pret' | 'indisponible'>('chargement')
const enCours = ref<string | null>(null)
const erreur = ref<string | null>(null)

async function charger() {
  try {
    const [r, a, e] = await Promise.all([lireContacts('recue'), lireContacts('accepte'), lireContacts('envoyee')])
    recues.value = r
    contacts.value = a
    envoyees.value = e
    etat.value = 'pret'
  } catch {
    etat.value = 'indisponible'
  }
}
void charger()

async function agir(personneId: string, appel: () => Promise<unknown>) {
  enCours.value = personneId
  erreur.value = null
  try {
    const ok = await appel()
    if (ok === null || ok === false) erreur.value = t('reseau.contacts.erreur')
    await charger()
    void rafraichirActivite()
  } catch {
    erreur.value = t('etat.indisponible')
  } finally {
    enCours.value = null
  }
}
</script>

<template>
  <section class="reseau-page">
    <header class="tete">
      <h1>{{ t('reseau.onglets.contacts') }}</h1>
      <OngletsReseau />
    </header>
    <p v-if="erreur" role="alert" class="erreur">{{ erreur }}</p>

    <JSquelette v-if="etat === 'chargement'" :nombre="3" />
    <div v-else-if="etat === 'indisponible'" role="alert" class="etat-vide">
      <p>{{ t('etat.indisponible') }}</p>
      <JBouton variante="secondaire" @click="charger()">{{ t('etat.reessayer') }}</JBouton>
    </div>
    <template v-else>
      <section v-if="recues.length" class="groupe" aria-labelledby="contacts-recues">
        <h2 id="contacts-recues">{{ t('reseau.contacts.recues') }}</h2>
        <ul class="liste carte" data-test="contacts-recues">
          <li v-for="c in recues" :key="c.personne.id" class="ligne ligne--demande">
            <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="44" />
            <span class="ligne__qui">
              <strong>{{ c.personne.nom }}</strong>
              <span class="texte-discret">{{ [c.personne.ville, ...(c.competences ?? []).slice(0, 3)].filter(Boolean).join(' · ') }}</span>
            </span>
            <span class="ligne__actions">
              <JBouton :occupe="enCours === c.personne.id" data-test="accepter" @click="agir(c.personne.id, () => ajouterContact(c.personne.id))">{{ t('reseau.contacts.accepter') }}</JBouton>
              <JBouton variante="secondaire" data-test="refuser" @click="agir(c.personne.id, () => retirerContact(c.personne.id))">{{ t('reseau.contacts.refuser') }}</JBouton>
            </span>
          </li>
        </ul>
      </section>

      <section class="groupe" aria-labelledby="contacts-mes">
        <h2 id="contacts-mes">{{ t('reseau.contacts.mes') }}</h2>
        <p v-if="!contacts.length" class="texte-discret" data-test="contacts-vide">{{ t('reseau.contacts.vide') }}</p>
        <ul v-else class="grille-cartes" data-test="contacts">
          <li v-for="c in contacts" :key="c.personne.id" class="carte contact">
            <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="48" />
            <strong>{{ c.personne.nom }}</strong>
            <span v-if="c.personne.ville" class="texte-discret">{{ c.personne.ville }}</span>
            <ul v-if="c.competences?.length" class="competences">
              <li v-for="x in c.competences" :key="x" class="etiquette">{{ x }}</li>
            </ul>
            <RouterLink :to="`/messages/${c.personne.id}`" class="j-bouton j-bouton--secondaire">{{ t('reseau.contacts.ecrire') }}</RouterLink>
          </li>
        </ul>
      </section>

      <section v-if="envoyees.length" class="groupe" aria-labelledby="contacts-envoyees">
        <h2 id="contacts-envoyees">{{ t('reseau.contacts.envoyees') }}</h2>
        <ul class="liste carte" data-test="contacts-envoyees">
          <li v-for="c in envoyees" :key="c.personne.id" class="ligne">
            <JAvatar :nom="c.personne.nom" :url="c.personne.avatarUrl" :taille="40" />
            <span class="ligne__qui"><strong>{{ c.personne.nom }}</strong></span>
            <JBouton variante="secondaire" :occupe="enCours === c.personne.id" @click="agir(c.personne.id, () => retirerContact(c.personne.id))">{{ t('reseau.contacts.annuler') }}</JBouton>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.reseau-page { display: grid; gap: var(--space-6); max-width: 60rem; }
.tete { display: grid; gap: var(--space-4); }
.tete h1 { margin: 0; }
.groupe { display: grid; gap: var(--space-3); }
.groupe h2 { margin: 0; }
.liste { list-style: none; margin: 0; padding: var(--space-2); display: grid; }
.ligne { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3); flex-wrap: wrap; }
.ligne--demande { background: color-mix(in srgb, var(--action) 8%, transparent); border-radius: var(--radius-md); }
.ligne__qui { display: grid; flex: 1; min-width: 10rem; }
.ligne__actions { display: flex; gap: var(--space-2); }
.contact { display: grid; justify-items: start; gap: var(--space-2); padding: var(--space-6); }
.competences { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.erreur { margin: 0; color: var(--danger); font-weight: 600; }
</style>
