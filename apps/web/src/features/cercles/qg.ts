/*
 * Le QG du cercle (refonte inspirée de Basecamp) : les outils d'un cercle et les règles d'affichage, sans Vue.
 */
import type { Tache } from './cercles'
import { DUREE_TOUR_JOURS } from './cercles'
import type { MessageCercle } from './salon'

export const OUTILS = ['qg', 'salon', 'taches', 'points', 'decisions', 'calendrier', 'cercle'] as const
export type Outil = (typeof OUTILS)[number]

/**
 * L'outil ouvert, lu dans l'adresse. `?onglet=salon` reste compris (anciens liens). Par défaut : le QG quand l'ordre est
 * tiré, la page du cercle avant (il s'agit alors de se réunir et de tirer l'ordre).
 */
export function outilDepuis(query: Record<string, unknown>, membre: boolean, tire: boolean): Outil {
  if (!membre) return 'cercle'
  const demande = (query.outil ?? (query.onglet === 'salon' ? 'salon' : undefined)) as string | undefined
  if (demande && (OUTILS as readonly string[]).includes(demande)) return demande as Outil
  return tire ? 'qg' : 'cercle'
}

/** La hauteur de la colline en x (0 à 200), en coordonnées SVG (haut = 10, bas = 74). */
function hauteur(x: number) {
  return 74 - 64 * Math.sin((Math.PI * x) / 200)
}

/**
 * Chaque tâche posée sur la colline : à faire en montée, en cours au sommet, livrée en descente. Les points d'un même
 * état s'écartent un peu les uns des autres pour rester lisibles.
 */
export function pointsDeLaColline(taches: Pick<Tache, 'id' | 'titre' | 'statut'>[]) {
  const zones = { a_faire: [22, 72], en_cours: [82, 118], livree: [128, 182] } as const
  const parStatut = { a_faire: [] as typeof taches, en_cours: [] as typeof taches, livree: [] as typeof taches }
  for (const t of taches) parStatut[t.statut].push(t)
  return (Object.keys(parStatut) as (keyof typeof parStatut)[]).flatMap((statut) => {
    const liste = parStatut[statut]
    const [debut, fin] = zones[statut]
    return liste.map((t, i) => {
      const x = liste.length === 1 ? (debut + fin) / 2 : debut + ((fin - debut) * i) / (liste.length - 1)
      return { id: t.id, titre: t.titre, statut, x, y: hauteur(x) }
    })
  })
}

/** Les points du soir, regroupés par jour (le plus récent d'abord) : la façon Basecamp de relire les réponses. */
export function pointsParJour(messages: MessageCercle[]) {
  const jours = new Map<string, MessageCercle[]>()
  for (const m of messages) {
    if (m.type !== 'point') continue
    const jour = m.envoyeLe.slice(0, 10)
    jours.set(jour, [...(jours.get(jour) ?? []), m])
  }
  return [...jours.entries()].sort(([a], [b]) => b.localeCompare(a)).map(([jour, points]) => ({ jour, points }))
}

/** Les 14 jours du tour, à partir de sa date d'ouverture : aujourd'hui, et la livraison (fixe : la règle de la tontine). */
export function joursDuTour(debut: string, maintenant = new Date()) {
  const premier = new Date(debut)
  premier.setUTCHours(0, 0, 0, 0)
  const aujourdhui = maintenant.toISOString().slice(0, 10)
  return Array.from({ length: DUREE_TOUR_JOURS }, (_, i) => {
    const date = new Date(premier.getTime() + i * 86_400_000)
    const iso = date.toISOString().slice(0, 10)
    return {
      numero: i + 1,
      date,
      iso,
      aujourdhui: iso === aujourdhui,
      passe: iso < aujourdhui,
      jalon: i === DUREE_TOUR_JOURS - 1 ? ('livraison' as const) : null,
    }
  })
}
