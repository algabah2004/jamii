import { readonly, ref } from 'vue'
import { api } from './api/client'
import type { components } from './api/generated/socle'

export type Moi = components['schemas']['Moi']
export type EtatSession = 'chargement' | 'connecte' | 'non_connecte' | 'indisponible'

const etat = ref<EtatSession>('chargement')
const moi = ref<Moi | null>(null)
/** L'espace Systalink est réservé à son équipe : le menu ne montre le lien qu'à elle. */
const accesSystalink = ref(false)

async function lireAccesSystalink(): Promise<boolean> {
  try {
    const { data } = await api.GET('/api/systalink/acces')
    return data?.acces === true
  } catch {
    return false
  }
}

/** Appelé au démarrage : 401 veut dire « afficher l'accueil non connecté » (contrat socle). */
export async function chargerSession(): Promise<void> {
  try {
    const { data, response } = await api.GET('/api/me')
    if (data) {
      moi.value = data
      accesSystalink.value = await lireAccesSystalink()
      etat.value = 'connecte'
    } else {
      moi.value = null
      accesSystalink.value = false
      etat.value = response.status === 401 ? 'non_connecte' : 'indisponible'
    }
  } catch {
    // Réseau coupé ou API éteinte : on n'efface pas l'écran, on le signale.
    moi.value = null
    accesSystalink.value = false
    etat.value = 'indisponible'
  }
}

export async function seDeconnecter(): Promise<void> {
  await api.POST('/api/auth/deconnexion')
  moi.value = null
  accesSystalink.value = false
  etat.value = 'non_connecte'
}

export function useSession() {
  return { etat: readonly(etat), moi: readonly(moi), accesSystalink: readonly(accesSystalink) }
}
