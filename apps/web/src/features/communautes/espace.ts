import type { InjectionKey } from 'vue'
import type { components } from '../../api/generated/communautes'

type Communaute = components['schemas']['Communaute']

/**
 * Ce que les pages de l'espace d'une communauté disent au cadre qui les entoure (CadreCommunaute) :
 * quelle communauté montrer dans la barre, quel lien y est actif, et où poser le côté droit.
 */
export interface EtatEspace {
  /** La communauté de la barre ; `null` : pas de barre (communauté introuvable). */
  actuelle: string | null
  actif: string
  communaute: Communaute | null
  /** Augmente quand la barre doit relire ses salons (après « Rejoindre », un salon supprimé). */
  recharge: number
  /** La colonne de droite, où les pages envoient leur côté (`#contexte`). */
  cote: HTMLElement | null
}

export const CLE_ESPACE: InjectionKey<EtatEspace> = Symbol('espace-communaute')
