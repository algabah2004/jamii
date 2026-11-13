// Génère src/design-system/tokens.css depuis docs/design/tokens.json (la source de vérité de la charte).
// Usage : npm run tokens. Un test échoue si le fichier généré n'est plus à jour.
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** Noms déjà utilisés par les premiers écrans (#18) : gardés comme alias pour ne rien casser. */
const ALIAS = { relief: 'surface-raised', ligne: 'line', preuve: 'proof', discret: 'ink-muted' }

export function genererCss(tokens) {
  const parNom = Object.fromEntries(tokens.color.tokens.map((t) => [t.name, t.value]))
  const resoudre = (valeur, theme) => {
    if (typeof valeur === 'object') return resoudre(valeur[theme], theme)
    const ref = /^\{(.+)\}$/.exec(valeur)
    return ref ? resoudre(parNom[ref[1]], theme) : valeur
  }
  const couleurs = (theme) =>
    [
      ...tokens.color.tokens.map((t) => `  --${t.name}: ${resoudre(t.value, theme)};`),
      ...Object.entries(ALIAS).map(([alias, nom]) => `  --${alias}: var(--${nom});`),
    ].join('\n')
  const fixes = [
    `  --font-display: ${tokens.type.families.display};`,
    `  --font-text: ${tokens.type.families.text};`,
    // Titre éditorial (accueil) : facultatif dans tokens.json.
    ...(tokens.type.families.editorial ? [`  --font-editorial: ${tokens.type.families.editorial};`] : []),
    ...tokens.type.groups.flatMap((g) =>
      g.styles.flatMap((s) => [
        `  --type-${s.name}-size: ${s.fontSize};`,
        `  --type-${s.name}-line: ${s.lineHeight};`,
        `  --type-${s.name}-weight: ${s.fontWeight};`,
      ]),
    ),
    ...tokens.spacing.tokens.map((t) => `  --${t.name}: ${t.value};`),
    ...tokens.radius.tokens.map((t) => `  --${t.name}: ${t.value};`),
    ...tokens.motion.tokens.map((t) => `  --${t.name}: ${t.value};`),
  ].join('\n')
  // Une zone sombre dans une page claire (bandeau « en ligne en ce moment ») : seules les couleurs
  // qui changent d'un thème à l'autre sont redéclarées, pour qu'une charte partenaire garde son action.
  const zoneSombre = tokens.color.tokens
    .filter((t) => resoudre(t.value, 'clair') !== resoudre(t.value, 'sombre'))
    .map((t) => `  --${t.name}: ${resoudre(t.value, 'sombre')};`)
    .join('\n')
  const zoneClaire = tokens.color.tokens
    .filter((t) => resoudre(t.value, 'clair') !== resoudre(t.value, 'sombre'))
    .map((t) => `  --${t.name}: ${resoudre(t.value, 'clair')};`)
    .join('\n')
  const durees = tokens.motion.tokens.filter((t) => t.name.startsWith('motion-'))
  return `/* Généré par scripts/tokens-css.mjs depuis docs/design/tokens.json (v${tokens.version}). Ne pas modifier à la main : npm run tokens. */

:root {
  color-scheme: light;
${couleurs('clair')}
${fixes}
}

/* Thème sombre : celui du système, sauf si l'utilisateur a choisi (data-theme sur <html>). */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='clair']) {
    color-scheme: dark;
${couleurs('sombre').replaceAll('\n  ', '\n    ').replace(/^ {2}/, '    ')}
  }
}

:root[data-theme='sombre'] {
  color-scheme: dark;
${couleurs('sombre')}
}

/* Zone toujours sombre, quel que soit le thème de la page. */
.zone-sombre {
  color-scheme: dark;
${zoneSombre}
  background: var(--bg);
  color: var(--ink);
}

/* Zone toujours claire (cartes de verre posées sur une scène sombre). */
.zone-claire {
  color-scheme: light;
${zoneClaire}
  color: var(--ink);
}

/* Mouvement réduit : toutes les durées à 0, le contenu reste le même. */
@media (prefers-reduced-motion: reduce) {
  :root {
${durees.map((t) => `    --${t.name}: 0ms;`).join('\n')}
  }
}
`
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const source = fileURLToPath(new URL('../../../docs/design/tokens.json', import.meta.url))
  const cible = fileURLToPath(new URL('../src/design-system/tokens.css', import.meta.url))
  writeFileSync(cible, genererCss(JSON.parse(readFileSync(source, 'utf8'))))
  console.log('tokens.css à jour')
}
