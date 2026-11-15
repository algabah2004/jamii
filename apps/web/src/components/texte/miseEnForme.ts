/*
 * La mise en forme des messages : un petit Markdown, sûr. On ne laisse jamais passer de HTML écrit par quelqu'un (il
 * pourrait cacher un script qui s'exécuterait chez tous les membres). Le texte est découpé en blocs et en morceaux ;
 * TexteRiche.vue en fait des éléments Vue, toujours échappés.
 *
 * Ce qui est compris :
 *   **gras**  *italique* ou _italique_  ~~barré~~  `code`  [texte](https://lien)  https://lien-nu  @Prénom
 *   ```bloc de code```   > citation   - liste   1. liste numérotée
 */

export type Morceau =
  | { type: 'texte'; texte: string }
  | { type: 'gras' | 'italique' | 'barre'; enfants: Morceau[] }
  | { type: 'code'; texte: string }
  | { type: 'lien'; url: string; enfants: Morceau[] }
  | { type: 'mention'; texte: string }

export type Bloc =
  | { type: 'paragraphe'; lignes: Morceau[][] }
  | { type: 'code'; texte: string; langue: string | null }
  | { type: 'citation'; lignes: Morceau[][] }
  | { type: 'liste'; ordonnee: boolean; elements: Morceau[][] }

/** Seuls les liens web et e-mail sont cliquables : jamais `javascript:` ni `data:`. */
export function lienSur(url: string): string | null {
  const u = url.trim()
  if (/^https?:\/\/[^\s<>"]+$/i.test(u)) return u
  if (/^mailto:[^\s<>"@]+@[^\s<>"]+$/i.test(u)) return u
  return null
}

// L'ordre compte : le code d'abord (rien n'est interprété dedans), puis les liens, puis les styles.
const MOTIF = new RegExp(
  [
    '`([^`\\n]+)`', // 1 code
    '\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)', // 2 texte, 3 url
    '\\*\\*([^*\\n](?:[^\\n]*?[^*\\n])?)\\*\\*', // 4 gras
    '~~([^~\\n]+)~~', // 5 barré
    '(?<![\\w*])\\*([^*\\s](?:[^*\\n]*[^*\\s])?)\\*(?!\\*)', // 6 italique *x*
    '(?<![\\w_])_([^_\\s](?:[^_\\n]*[^_\\s])?)_(?![\\w_])', // 7 italique _x_
    '(https?:\\/\\/[^\\s<>"]+[^\\s<>".,;:!?)\\]])', // 8 lien nu
    '(?<![\\w@])@([A-Za-zÀ-ÖØ-öø-ÿ][\\wÀ-ÖØ-öø-ÿ-]{1,30})', // 9 mention
  ].join('|'),
  'g',
)

export function morceaux(texte: string): Morceau[] {
  const sortie: Morceau[] = []
  let dernier = 0
  const ajouterTexte = (t: string) => {
    if (!t) return
    const precedent = sortie.at(-1)
    if (precedent?.type === 'texte') precedent.texte += t
    else sortie.push({ type: 'texte', texte: t })
  }
  for (const m of texte.matchAll(MOTIF)) {
    ajouterTexte(texte.slice(dernier, m.index))
    dernier = m.index! + m[0].length
    if (m[1] !== undefined) sortie.push({ type: 'code', texte: m[1] })
    else if (m[2] !== undefined) {
      const url = lienSur(m[3]!)
      if (url) sortie.push({ type: 'lien', url, enfants: morceaux(m[2]) })
      else ajouterTexte(m[0])
    } else if (m[4] !== undefined) sortie.push({ type: 'gras', enfants: morceaux(m[4]) })
    else if (m[5] !== undefined) sortie.push({ type: 'barre', enfants: morceaux(m[5]) })
    else if (m[6] !== undefined || m[7] !== undefined) sortie.push({ type: 'italique', enfants: morceaux((m[6] ?? m[7])!) })
    else if (m[8] !== undefined) sortie.push({ type: 'lien', url: m[8], enfants: [{ type: 'texte', texte: m[8] }] })
    else if (m[9] !== undefined) sortie.push({ type: 'mention', texte: `@${m[9]}` })
  }
  ajouterTexte(texte.slice(dernier))
  return sortie
}

const LISTE = /^\s*[-*•]\s+(.*)$/
const LISTE_NUMEROTEE = /^\s*\d{1,3}[.)]\s+(.*)$/
const CITATION = /^\s*>\s?(.*)$/
const CLOTURE = /^\s*```\s*([\w+-]{0,20})\s*$/

export function blocs(texte: string): Bloc[] {
  const lignes = texte.replace(/\r\n?/g, '\n').split('\n')
  const sortie: Bloc[] = []
  let i = 0
  while (i < lignes.length) {
    const ligne = lignes[i]!
    const cloture = CLOTURE.exec(ligne)
    if (cloture) {
      const code: string[] = []
      i++
      while (i < lignes.length && !CLOTURE.test(lignes[i]!)) code.push(lignes[i++]!)
      i++ // la clôture (ou la fin du texte, si on l'a oubliée)
      sortie.push({ type: 'code', texte: code.join('\n'), langue: cloture[1] || null })
      continue
    }
    if (!ligne.trim()) {
      i++
      continue
    }
    if (CITATION.test(ligne)) {
      const cites: Morceau[][] = []
      while (i < lignes.length && CITATION.test(lignes[i]!)) cites.push(morceaux(CITATION.exec(lignes[i++]!)![1]!))
      sortie.push({ type: 'citation', lignes: cites })
      continue
    }
    const ordonnee = LISTE_NUMEROTEE.test(ligne)
    if (ordonnee || LISTE.test(ligne)) {
      const motif = ordonnee ? LISTE_NUMEROTEE : LISTE
      const elements: Morceau[][] = []
      while (i < lignes.length && motif.test(lignes[i]!)) elements.push(morceaux(motif.exec(lignes[i++]!)![1]!))
      sortie.push({ type: 'liste', ordonnee, elements })
      continue
    }
    const paragraphe: Morceau[][] = []
    while (i < lignes.length && lignes[i]!.trim() && !CLOTURE.test(lignes[i]!) && !CITATION.test(lignes[i]!) && !LISTE.test(lignes[i]!) && !LISTE_NUMEROTEE.test(lignes[i]!)) {
      paragraphe.push(morceaux(lignes[i++]!))
    }
    sortie.push({ type: 'paragraphe', lignes: paragraphe })
  }
  return sortie
}

// --- Écrire : ce que font les boutons de la barre de mise en forme, sur la sélection d'une zone de texte ---

export type Format = 'gras' | 'italique' | 'barre' | 'code' | 'bloc' | 'lien' | 'liste' | 'citation'

/** Le texte après mise en forme, et la nouvelle sélection (début, fin). */
export function appliquerFormat(texte: string, debut: number, fin: number, format: Format, exemple = ''): { texte: string; debut: number; fin: number } {
  const choisi = texte.slice(debut, fin) || exemple
  const autour = (avant: string, apres: string) => {
    const nouveau = texte.slice(0, debut) + avant + choisi + apres + texte.slice(fin)
    return { texte: nouveau, debut: debut + avant.length, fin: debut + avant.length + choisi.length }
  }
  const debutDeLigne = texte.lastIndexOf('\n', debut - 1) + 1
  const prefixer = (prefixe: (n: number) => string) => {
    const bloc = texte.slice(debutDeLigne, fin) || exemple
    const transforme = bloc.split('\n').map((l, n) => prefixe(n) + l).join('\n')
    const nouveau = texte.slice(0, debutDeLigne) + transforme + texte.slice(Math.max(fin, debutDeLigne))
    return { texte: nouveau, debut: debutDeLigne, fin: debutDeLigne + transforme.length }
  }
  switch (format) {
    case 'gras': return autour('**', '**')
    case 'italique': return autour('_', '_')
    case 'barre': return autour('~~', '~~')
    case 'code': return choisi.includes('\n') ? autour('```\n', '\n```') : autour('`', '`')
    case 'bloc': return autour(`${debut > 0 && texte[debut - 1] !== '\n' ? '\n' : ''}\`\`\`\n`, '\n```\n')
    case 'lien': {
      const r = autour('[', '](https://)')
      // La sélection se place sur l'adresse, à compléter.
      const url = r.fin + 2
      return { texte: r.texte, debut: url, fin: url + 'https://'.length }
    }
    case 'liste': return prefixer(() => '- ')
    case 'citation': return prefixer(() => '> ')
  }
}
