import { api } from '../../api/client'
import type { components } from '../../api/generated/profils'

export type ProfilResume = components['schemas']['ProfilResume']

export type Filtres = { q: string; competence: string; disponibles: boolean }

/** Le nom doit compter 2 caractères au moins (contracts/profils.yaml) : en dessous, on ne l'envoie pas. */
export const NOM_MIN = 2

/**
 * Une compétence telle que l'API l'attend (`^[a-z0-9][a-z0-9.+#-]*$`) : « Spring Boot » → spring-boot, « Réseau » → reseau.
 * Ce qui reste invalide est retiré, pour ne jamais envoyer une recherche que l'API refuserait.
 */
export const normaliserCompetence = (texte: string) =>
  texte.normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase()
    .replace(/\s+/g, '-').replace(/[^a-z0-9.+#-]/g, '').replace(/^[^a-z0-9]+/, '').slice(0, 50)

/** Chercher des développeurs parmi les profils publiés (GET /api/profils). */
export async function chercherProfils(f: Filtres, curseur?: string | null) {
  const q = f.q.trim()
  const competence = normaliserCompetence(f.competence)
  const { data } = await api.GET('/api/profils', {
    params: {
      query: {
        limite: 20,
        ...(q.length >= NOM_MIN ? { q: q.slice(0, 50) } : {}),
        ...(competence ? { competence: [competence] } : {}),
        ...(f.disponibles ? { disponibilite: 'disponible' as const } : {}),
        ...(curseur ? { curseur } : {}),
      },
    },
  })
  if (!data) throw new Error('recherche indisponible')
  return data
}
