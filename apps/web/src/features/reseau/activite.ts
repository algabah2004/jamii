import { readonly, ref } from 'vue'
import { api } from '../../api/client'
import type { components } from '../../api/generated/reseau'

/*
 * Les pastilles du menu (contracts/reseau.yaml, GET /api/me/activite) : relues toutes les 30 secondes,
 * seulement quand l'onglet est visible, et tout de suite quand on y revient. Pas de WebSocket.
 */
export type Activite = components['schemas']['Activite']
export const INTERVALLE_MS = 30_000

const activite = ref<Activite | null>(null)
let minuteur: ReturnType<typeof setInterval> | null = null

export async function rafraichirActivite(): Promise<void> {
  try {
    const { data } = await api.GET('/api/me/activite')
    // Une erreur garde les derniers compteurs connus : une pastille ne doit pas clignoter pour une panne réseau.
    if (data) activite.value = data
  } catch {
    /* on réessaiera au prochain passage */
  }
}

const auRetour = () => {
  if (document.visibilityState === 'visible') void rafraichirActivite()
}

export function suivreActivite(): void {
  if (minuteur) return
  void rafraichirActivite()
  minuteur = setInterval(() => {
    if (document.visibilityState === 'visible') void rafraichirActivite()
  }, INTERVALLE_MS)
  document.addEventListener('visibilitychange', auRetour)
}

export function arreterActivite(): void {
  if (minuteur) clearInterval(minuteur)
  minuteur = null
  activite.value = null
  document.removeEventListener('visibilitychange', auRetour)
}

export const useActivite = () => readonly(activite)
