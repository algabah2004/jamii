import type { MonProfil } from './profils'

/**
 * La jauge « profil complet » de Mon profil : chaque étape compte pareil, et la première qui manque devient la
 * suggestion du moment. Rien n'est obligatoire : la jauge aide, elle ne bloque jamais.
 */
export type EtapeProfil = 'titre' | 'bio' | 'lieu' | 'competences' | 'phares' | 'langues' | 'liens' | 'realisations' | 'publie'

/** La section du panneau « Modifier » qui remplit chaque étape (`publie` se fait depuis la page). */
export const SECTION_ETAPE: Record<EtapeProfil, SectionEdition | null> = {
  titre: 'essentiel',
  bio: 'essentiel',
  lieu: 'essentiel',
  competences: 'competences',
  phares: 'competences',
  langues: 'competences',
  liens: 'liens',
  realisations: 'realisations',
  publie: null,
}

export type SectionEdition = 'essentiel' | 'competences' | 'liens' | 'realisations'
export const SECTIONS_EDITION: SectionEdition[] = ['essentiel', 'competences', 'liens', 'realisations']

type Lu = Pick<
  MonProfil,
  'titre' | 'bio' | 'pays' | 'ville' | 'competences' | 'competencesPhares' | 'langues' | 'linkedin' | 'siteWeb' | 'realisations' | 'publie'
>

const FAIT: Record<EtapeProfil, (p: Lu) => boolean> = {
  titre: (p) => !!p.titre?.trim(),
  bio: (p) => (p.bio?.trim().length ?? 0) >= 40,
  lieu: (p) => !!p.pays || !!p.ville?.trim(),
  competences: (p) => p.competences.length >= 3,
  phares: (p) => p.competencesPhares.length > 0,
  langues: (p) => p.langues.length > 0,
  liens: (p) => !!p.linkedin || !!p.siteWeb,
  realisations: (p) => p.realisations.length > 0,
  publie: (p) => p.publie,
}

export const ETAPES = Object.keys(FAIT) as EtapeProfil[]

export function completude(p: Lu): { pourcent: number; manquantes: EtapeProfil[] } {
  const manquantes = ETAPES.filter((e) => !FAIT[e](p))
  return { pourcent: Math.round(((ETAPES.length - manquantes.length) / ETAPES.length) * 100), manquantes }
}
