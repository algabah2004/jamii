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

/**
 * Quand l'API ne répond pas, on réessaie après ces attentes avant d'afficher « Jamii ne répond pas » : l'API redémarre
 * à chaque mise à jour du site (deploiement/README.md), et ce passage dure de quelques secondes à une minute.
 */
export const ATTENTES_MS: readonly number[] = [1_000, 2_000, 4_000]
/** Une fois le message affiché, on réessaie à ce rythme (onglet visible) : la page revient d'elle-même. */
export const REPRISE_MS = 10_000

const pause = (ms: number) => new Promise((fini) => setTimeout(fini, ms))
let reprise: ReturnType<typeof setTimeout> | null = null

/** Un essai : vrai si l'API a répondu (connecté ou non), faux si elle est injoignable ou en erreur. */
async function essayer(): Promise<boolean> {
  try {
    const { data, response } = await api.GET('/api/me')
    if (data) {
      moi.value = data
      accesSystalink.value = await lireAccesSystalink()
      etat.value = 'connecte'
      return true
    }
    if (response.status === 401) {
      moi.value = null
      accesSystalink.value = false
      etat.value = 'non_connecte'
      return true
    }
  } catch {
    /* réseau coupé ou API éteinte */
  }
  return false
}

/**
 * Appelé au démarrage : 401 veut dire « afficher l'accueil non connecté » (contrat socle). Une API qui ne répond pas
 * est réessayée quelques fois avant d'être signalée, puis en arrière-plan jusqu'à son retour.
 */
export async function chargerSession(attentes: readonly number[] = ATTENTES_MS): Promise<void> {
  if (reprise) clearTimeout(reprise)
  reprise = null
  // Depuis « Réessayer » : on montre qu'on cherche, sans effacer une page déjà affichée dans les autres cas.
  if (etat.value === 'indisponible') etat.value = 'chargement'
  if (await essayer()) return
  for (const attente of attentes) {
    await pause(attente)
    if (await essayer()) return
  }
  // On n'efface pas l'écran, on le signale.
  moi.value = null
  accesSystalink.value = false
  etat.value = 'indisponible'
  reprendrePlusTard()
}

function reprendrePlusTard() {
  reprise = setTimeout(async () => {
    reprise = null
    if (etat.value !== 'indisponible') return
    if (document.visibilityState !== 'visible' || !(await essayer())) reprendrePlusTard()
  }, REPRISE_MS)
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
