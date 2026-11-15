import { describe, expect, it } from 'vitest'
import type { Tache } from './cercles'
import { competencesUtiles, proposerRepartition } from './repartition'

const tache = (id: string, titre: string, extra: Partial<Tache> = {}): Tache => ({
  id, titre, description: null, priorite: 'normale', statut: 'a_faire', proposeeParIa: false, version: 1, creeLe: `2026-11-15T10:0${id}:00Z`, assignee: null, ...extra,
})
const awa = { id: 'a', nom: 'Awa', competences: ['react', 'ux-design'] }
const kofi = { id: 'k', nom: 'Kofi', competences: ['spring', 'postgresql'] }
const binta = { id: 'b', nom: 'Binta', competences: ['flutter', 'paiement-mobile'] }

describe('competencesUtiles', () => {
  it('relie les mots de tous les jours aux compétences', () => {
    expect(competencesUtiles("L'écran d'accueil", awa.competences)).toEqual(['react'])
    expect(competencesUtiles('Les tables de la base de données', kofi.competences)).toEqual(['postgresql'])
    expect(competencesUtiles('Brancher le paiement Wave', binta.competences)).toEqual(['paiement-mobile'])
    expect(competencesUtiles('Paiement mobile', binta.competences)).toContain('paiement-mobile')
  })
})

describe('proposerRepartition', () => {
  it('confie chaque tâche libre au membre dont les compétences correspondent, jamais au bénéficiaire', () => {
    const r = proposerRepartition([tache('1', "Maquette de l'écran d'accueil"), tache('2', "L'API des commandes"), tache('3', 'Paiement Orange Money')], [awa, kofi, binta], null)
    expect(r.map((s) => s.membre.nom)).toEqual(['Awa', 'Kofi', 'Binta'])
    expect(proposerRepartition([tache('1', 'Écran')], [awa, kofi], 'a').map((s) => s.membre.nom)).toEqual(['Kofi'])
  })

  it('partage le travail quand aucune compétence ne correspond, et ignore les tâches déjà prises ou livrées', () => {
    const r = proposerRepartition(
      [
        tache('1', 'Rédiger le texte', { statut: 'en_cours', assignee: { id: 'a', nom: 'Awa' } }),
        tache('2', 'Appeler les clientes'),
        tache('3', 'Préparer la démo'),
        tache('4', 'Déjà fait', { statut: 'livree' }),
      ],
      [awa, kofi, binta],
      null,
    )
    expect(r.map((s) => s.tache.id)).toEqual(['2', '3'])
    // Awa porte déjà une tâche : les deux libres vont aux deux autres.
    expect(r.map((s) => s.membre.nom).sort()).toEqual(['Binta', 'Kofi'])
    expect(r.every((s) => s.competences.length === 0)).toBe(true)
  })

  it('ne propose rien sans équipe', () => {
    expect(proposerRepartition([tache('1', 'Écran')], [awa], 'a')).toEqual([])
  })
})
