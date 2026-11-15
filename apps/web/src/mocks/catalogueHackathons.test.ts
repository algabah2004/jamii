import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { reinitialiserHackathons } from './hackathons'
import { catalogueHackathons, DEFIS_DEMO } from './catalogueHackathons'
import { lireHackathon, lireRendu, lireResultats, listerEquipes, listerHackathons, listerRendus, noter, suivreNotation } from '../features/hackathons/hackathons'

beforeEach(() => reinitialiserHackathons(true))
afterEach(() => reinitialiserHackathons(false))

describe('le catalogue fictif des hackathons', () => {
  it('ajoute douze événements accessibles, les trois formats et les quatre phases', async () => {
    const page = await listerHackathons('tous')
    expect(page.elements).toHaveLength(DEFIS_DEMO.length + 1)
    expect(new Set(page.elements.map((h) => h.lieu)).size).toBe(3)
    expect(new Set(page.elements.map((h) => h.statut))).toEqual(new Set(['publie', 'en_cours', 'evaluation', 'resultats']))
    for (const e of catalogueHackathons()) {
      const h = await lireHackathon(e.hackathon.id)
      expect(h?.titre).toContain('· Démo')
      expect(h?.description).toContain('Événement fictif')
      const equipes = await listerEquipes(e.hackathon.id)
      expect(equipes).toHaveLength(2)
      expect(equipes.flatMap((x) => x.membres)).toHaveLength(h!.nombreInscrits)
      expect(Date.parse(h!.calendrier.inscriptionsOuvertesLe)).toBeLessThan(Date.now())
      expect(Date.parse(h!.calendrier.inscriptionsFermeesLe)).toBeLessThanOrEqual(Date.parse(h!.debutLe))
      expect(Date.parse(h!.debutLe)).toBeLessThan(Date.parse(h!.finRendusLe))
    }
    expect((await listerHackathons('miens')).elements).toHaveLength(1)
  })

  it('montre un classement public avec des projets, des prix et les retours du jury', async () => {
    const termine = catalogueHackathons().find((e) => e.hackathon.statut === 'resultats')!
    const resultats = await lireResultats(termine.hackathon.id)
    expect(resultats?.classement).toHaveLength(2)
    expect(resultats?.classement[0]?.rendu.titre).toBe('Ateliers ouverts')
    expect(resultats?.gagnants[0]?.prix.montant?.devise).toBe('XOF')
    expect(resultats?.classement[0]?.retourDuJury).toHaveLength(1)
  })

  it('ne mélange pas les rendus ni les notes de deux hackathons', async () => {
    const actifs = catalogueHackathons().filter((e) => e.rendus.length)
    const a = actifs.find((e) => e.hackathon.statut === 'evaluation')!
    const b = actifs.find((e) => e.hackathon.statut === 'en_cours')!
    const rendus = await listerRendus(a.hackathon.id)
    expect(rendus.map((r) => r.titre)).toEqual(a.rendus.map((r) => r.titre))
    expect(await lireRendu(b.hackathon.id, a.equipes[0]!.id)).toBeNull()
    await noter(a.hackathon.id, a.rendus[0]!.id, a.hackathon.criteres.map((c) => ({ critereId: c.id!, valeur: 4 })), null)
    expect((await suivreNotation(a.hackathon.id)).classement).toHaveLength(1)
    expect((await suivreNotation(b.hackathon.id)).classement).toHaveLength(0)
  })
})
