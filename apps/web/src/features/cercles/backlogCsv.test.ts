import { describe, expect, it } from 'vitest'
import { lireBacklogCsv } from './backlogCsv'

describe('préparer un backlog CSV', () => {
  it('lit le BOM, le point-virgule, les accents et les descriptions citées sur plusieurs lignes', () => {
    const r = lireBacklogCsv('\uFEFFtitre;description;priorité;échéance\r\nConnexion;"Premier écran; puis\r\nle retour \"accueil\"";haute;2027-01-02')
    // Les guillemets internes d'un CSV se doublent.
    const valide = lireBacklogCsv('\uFEFFtitre;description;priorité;échéance\r\nConnexion;"Premier écran; puis\r\nle retour ""accueil""";haute;2027-01-02')
    expect(r.erreurs.length).toBeGreaterThan(0)
    expect(valide.erreurs).toEqual([])
    expect(valide.taches).toEqual([{ titre: 'Connexion', description: 'Premier écran; puis\nle retour "accueil"', priorite: 'haute', echeance: '2027-01-02' }])
  })
  it('comprend les en-têtes anglais et laisse les champs facultatifs vides', () => {
    expect(lireBacklogCsv('title,description,priority,due_date\nMaquette,"Parcours, écrans",high,\nTests,,,').taches).toEqual([
      { titre: 'Maquette', description: 'Parcours, écrans', priorite: 'haute', echeance: null },
      { titre: 'Tests', description: null, priorite: 'normale', echeance: null },
    ])
  })
  it('rejette tout le fichier en signalant les vraies lignes invalides', () => {
    const r = lireBacklogCsv('titre,priorite,echeance\nConnexion,haute,2027-02-30\nconnexion,mauvaise,')
    expect(r.taches).toEqual([])
    expect(r.erreurs).toContainEqual({ ligne: 2, code: 'echeance' })
    expect(r.erreurs).toContainEqual({ ligne: 3, code: 'doublon' })
    expect(r.erreurs).toContainEqual({ ligne: 3, code: 'priorite' })
    expect(lireBacklogCsv('description\nSans titre').erreurs[0]?.code).toBe('entete')
  })
  it('borne le nombre de tâches et ne perd pas les formules ou balises saisies comme texte', () => {
    expect(lireBacklogCsv('titre\n' + Array.from({ length: 101 }, (_, i) => `Tâche ${i}`).join('\n')).erreurs[0]?.code).toBe('limite')
    expect(lireBacklogCsv('titre,description\n=1+2,<script>texte</script>').taches[0]).toMatchObject({ titre: '=1+2', description: '<script>texte</script>' })
  })
})
