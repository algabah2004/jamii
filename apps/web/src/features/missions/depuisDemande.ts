import type { Demande, PlanProjet } from '../demandes/demandes'
import { LIMITES, lireCompetences } from './missions'

/*
 * Une mission née d'une demande (contracts/missions.yaml, `demandeId`) : on reprend le cahier des charges de la
 * demande pour préremplir le brouillon. La personne le relit et le corrige avant de le publier : rien n'est visible
 * des développeurs avant. Le texte écrit à l'origine par la personne n'est repris que si l'IA n'a pas rendu de plan.
 */

/** Les mots des titres de section du besoin, donnés par l'écran pour rester dans la langue de la personne. */
export type Libelles = { objectifs: string; indispensables: string; utiles: string; horsPerimetre: string }

export type Prefill = { titre: string; besoin: string; competences: string; ville: string }

/** Le plan de la demande : celui de l'orientation s'il existe (c'est celui que l'écran de la demande montre), sinon le plan seul. */
export function planDe(demande: Demande): PlanProjet | null {
  const orientation = demande.orientation?.resultat
  return (orientation ? orientation.plan : demande.plan.resultat) ?? null
}

/** Les compétences citées par le plan (rôles manquants, puis tâches), rangées et sans doublon, au plus 10 (limite d'une mission). */
export function competencesDuPlan(plan: PlanProjet): string[] {
  const brutes = [...plan.rolesManquants.flatMap((r) => r.competences), ...plan.taches.flatMap((t) => t.competences)]
  return lireCompetences(brutes.join(',')).slice(0, 10)
}

function liste(titre: string, lignes: string[]): string {
  return lignes.length ? `${titre}\n${lignes.map((l) => `- ${l}`).join('\n')}` : ''
}

/** Le besoin de la mission : le résumé, puis ce que le plan veut obtenir. Texte brut, dans la limite d'une mission. */
export function besoinDuPlan(plan: PlanProjet, libelles: Libelles): string {
  const c = plan.cahierDesCharges
  const fonctionnalites = (priorite: string) => c.fonctionnalites.filter((f) => f.priorite === priorite).map((f) => `${f.titre} : ${f.description}`)
  return [
    c.resume,
    liste(libelles.objectifs, c.objectifs),
    liste(libelles.indispensables, fonctionnalites('indispensable')),
    liste(libelles.utiles, fonctionnalites('utile')),
    liste(libelles.horsPerimetre, c.horsPerimetre),
  ]
    .filter(Boolean)
    .join('\n\n')
    .slice(0, LIMITES.besoinMax)
}

export function prefillDepuisDemande(demande: Demande, libelles: Libelles): Prefill {
  const plan = planDe(demande)
  return {
    titre: demande.titre.slice(0, LIMITES.titreMax),
    besoin: (plan ? besoinDuPlan(plan, libelles) : demande.description).slice(0, LIMITES.besoinMax),
    competences: plan ? competencesDuPlan(plan).join(', ') : '',
    ville: (demande.ville ?? '').slice(0, LIMITES.villeMax),
  }
}
