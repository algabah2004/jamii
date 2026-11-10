import { ref } from 'vue'
import { api } from './api/client'
import type { components } from './api/socle'

export type Moi = components['schemas']['Moi']

/** L'utilisateur connecté, ou null. `chargee` passe à vrai après la première vérification. */
export const moi = ref<Moi | null>(null)
export const chargee = ref(false)
export const apiInjoignable = ref(false)

export async function chargerSession(): Promise<void> {
  try {
    const { data, response } = await api.GET('/api/me')
    moi.value = data ?? null
    apiInjoignable.value = response.status >= 500
  } catch {
    // L'API ne répond pas : on reste utilisable en visiteur, avec un message clair.
    moi.value = null
    apiInjoignable.value = true
  } finally {
    chargee.value = true
  }
}

export async function seDeconnecter(): Promise<void> {
  await api.POST('/api/auth/deconnexion')
  moi.value = null
}
