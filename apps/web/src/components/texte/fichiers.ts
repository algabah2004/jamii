/*
 * Envoyer un fichier joint (contracts/fichiers.yaml) et le décrire. Le serveur décide du vrai type ; ici, on filtre
 * seulement pour éviter un aller-retour inutile, et on prévient tout de suite d'un fichier trop gros.
 */
import { api } from '../../api/client'
import type { components } from '../../api/generated/fichiers'

export type PieceJointe = components['schemas']['PieceJointe']

export const PIECES_MAX = 6
export const ACCEPTES = 'image/png,image/jpeg,image/gif,image/webp,video/mp4,video/quicktime,video/webm,application/pdf,.zip,.docx,.xlsx,.pptx,.txt,.md,.csv,.json'
const MO = 1024 * 1024

export function limiteDe(fichier: File) {
  return fichier.type.startsWith('video/') ? 25 * MO : 10 * MO
}

export type ResultatEnvoi = { ok: true; piece: PieceJointe } | { ok: false; code: 'trop_gros' | 'type_refuse' | 'trop_de_requetes' | 'erreur' }

export async function envoyerFichier(fichier: File): Promise<ResultatEnvoi> {
  if (fichier.size > limiteDe(fichier)) return { ok: false, code: 'trop_gros' }
  try {
    const { data, error, response } = await api.POST('/api/fichiers', {
      body: { fichier } as unknown as { fichier: string },
      bodySerializer: (corps: unknown) => {
        const form = new FormData()
        form.append('fichier', (corps as { fichier: File }).fichier)
        return form
      },
    })
    if (data) return { ok: true, piece: data }
    const code = (error as { code?: string } | undefined)?.code
    if (response.status === 413 || code === 'trop_gros') return { ok: false, code: 'trop_gros' }
    if (response.status === 415 || code === 'type_refuse') return { ok: false, code: 'type_refuse' }
    if (response.status === 429) return { ok: false, code: 'trop_de_requetes' }
    return { ok: false, code: 'erreur' }
  } catch {
    return { ok: false, code: 'erreur' }
  }
}

/** « 2,4 Mo », « 340 Ko » : la taille lisible, dans la langue de la page. */
export function tailleLisible(octets: number, locale: string) {
  const f = (n: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: n < 10 ? 1 : 0 }).format(n)
  if (octets >= MO) return `${f(octets / MO)} ${locale.startsWith('fr') ? 'Mo' : 'MB'}`
  return `${f(Math.max(1, octets / 1024))} ${locale.startsWith('fr') ? 'Ko' : 'KB'}`
}
