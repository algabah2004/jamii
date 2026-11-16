import type { components } from '../../api/generated/cercles'
export type TacheBacklog = components['schemas']['TacheBacklog']
export type ErreurCsv = { ligne: number; code: 'format' | 'entete' | 'titre' | 'description' | 'priorite' | 'echeance' | 'doublon' | 'limite' | 'vide' }
export const BACKLOG_MAX = 100

/** CSV UTF-8 : virgule ou point-virgule, guillemets doublés, cellules sur plusieurs lignes et BOM. */
export function lireBacklogCsv(source: string): { taches: TacheBacklog[]; erreurs: ErreurCsv[] } {
  const texte = source.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
  const entete = texte.split('\n')[0] ?? ''
  const delimiteur = (entete.match(/;/g)?.length ?? 0) > (entete.match(/,/g)?.length ?? 0) ? ';' : ','
  const lignes: { valeurs: string[]; numero: number }[] = []
  let champs: string[] = [], cellule = '', citee = false, fermee = false, numero = 1, debut = 1
  const ajouter = () => { champs.push(cellule.trim()); cellule = ''; fermee = false }
  const ranger = () => { ajouter(); if (champs.some(Boolean)) lignes.push({ valeurs: champs, numero: debut }); champs = []; debut = numero + 1 }
  for (let i = 0; i < texte.length; i++) {
    const c = texte[i]!
    if (citee) {
      if (c === '"') { if (texte[i + 1] === '"') { cellule += '"'; i++ } else { citee = false; fermee = true } }
      else { cellule += c; if (c === '\n') numero++ }
    } else if (c === delimiteur) ajouter()
    else if (c === '\n') { ranger(); numero++ }
    else if (c === '"' && !cellule.trim() && !fermee) { cellule = ''; citee = true }
    else if (c === '"' || (fermee && c.trim())) return { taches: [], erreurs: [{ ligne: numero, code: 'format' }] }
    else if (!fermee) cellule += c
  }
  if (citee) return { taches: [], erreurs: [{ ligne: debut, code: 'format' }] }
  ranger()
  const normaliser = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim().replace(/[ _-]+/g, '')
  const noms = lignes.shift()?.valeurs.map(normaliser) ?? []
  const trouver = (...alias: string[]) => noms.findIndex((n) => alias.includes(n))
  const col = { titre: trouver('titre', 'title', 'tache', 'task', 'summary', 'resume'), description: trouver('description', 'details'), priorite: trouver('priorite', 'priority'), echeance: trouver('echeance', 'deadline', 'duedate') }
  if (col.titre < 0) return { taches: [], erreurs: [{ ligne: 1, code: 'entete' }] }
  if (!lignes.length) return { taches: [], erreurs: [{ ligne: 2, code: 'vide' }] }
  if (lignes.length > BACKLOG_MAX) return { taches: [], erreurs: [{ ligne: lignes[BACKLOG_MAX]!.numero, code: 'limite' }] }
  const taches: TacheBacklog[] = [], erreurs: ErreurCsv[] = [], vus = new Set<string>()
  const priorites: Record<string, TacheBacklog['priorite']> = { haute: 'haute', high: 'haute', urgente: 'haute', normale: 'normale', normal: 'normale', medium: 'normale', basse: 'basse', low: 'basse' }
  for (const l of lignes) {
    const valeur = (n: number) => n < 0 ? '' : (l.valeurs[n] ?? '')
    const titre = valeur(col.titre), description = valeur(col.description), p = normaliser(valeur(col.priorite)), echeance = valeur(col.echeance)
    const priorite = p ? priorites[p] : 'normale'
    const erreur = (code: ErreurCsv['code']) => erreurs.push({ ligne: l.numero, code })
    if (l.valeurs.length !== noms.length) erreur('format')
    if (titre.length < 3 || titre.length > 160) erreur('titre')
    if (description.length > 2000) erreur('description')
    if (!priorite) erreur('priorite')
    if (echeance && (!/^\d{4}-\d{2}-\d{2}$/.test(echeance) || !Number.isFinite(Date.parse(echeance)) || new Date(echeance).toISOString().slice(0, 10) !== echeance)) erreur('echeance')
    if (vus.has(normaliser(titre))) erreur('doublon')
    vus.add(normaliser(titre))
    taches.push({ titre, description: description || null, priorite: priorite ?? 'normale', echeance: echeance || null })
  }
  return { taches: erreurs.length ? [] : taches, erreurs }
}
