import type { Jalon, Mission } from './missions'

/*
 * La prochaine étape d'une mission en cours, pour la personne qui la lit : ce qu'elle doit faire maintenant, et sur
 * quel jalon. Elle s'affiche en haut du résumé, pour que l'action ne reste pas deux écrans plus bas.
 * On s'appuie sur `moi.actions` (calculé par l'API) : si l'API ne propose pas l'action, on ne l'annonce pas.
 */
export type ProchaineEtape = { cle: 'verifier' | 'payer' | 'livrer' | 'recevoir'; jalon: Jalon }

export function prochaineEtape(mission: Mission): ProchaineEtape | null {
  const travail = mission.travail
  if (mission.statut !== 'en_cours' || !travail?.devis.accepteLe) return null
  const actions = mission.moi.actions as string[]
  const jalons = [...travail.devis.jalons].sort((a, b) => a.ordre - b.ordre)
  const premier = (cle: ProchaineEtape['cle'], action: string, condition: (j: Jalon) => boolean): ProchaineEtape | null => {
    if (!actions.includes(action)) return null
    const jalon = jalons.find(condition)
    return jalon ? { cle, jalon } : null
  }
  // Dans l'ordre de ce qui bloque l'autre : vérifier une livraison avant de payer, livrer avant de confirmer un paiement.
  return (
    premier('verifier', 'accepter_jalon', (j) => j.statut === 'livre') ??
    premier('payer', 'declarer_paye', (j) => ['livre', 'accepte'].includes(j.statut) && j.paiement === 'non_paye') ??
    premier('livrer', 'livrer', (j) => j.statut === 'en_cours') ??
    premier('recevoir', 'declarer_recu', (j) => j.paiement === 'declare_paye')
  )
}

/** Un jalon pas encore livré dont la date est passée. */
export function enRetard(jalon: Jalon, maintenant: number): boolean {
  return Boolean(jalon.echeance) && ['a_faire', 'en_cours'].includes(jalon.statut) && Date.parse(jalon.echeance!) < maintenant
}
