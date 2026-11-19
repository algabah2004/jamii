/*
 * Pré-rendu de l'accueil (docs/adr/0011), lancé par `npm run build` après le build du navigateur.
 *
 * 1. Vite construit src/entree-prerendu.ts pour Node (mode SSR), dans un dossier temporaire.
 * 2. Node rend l'accueil d'un visiteur sans compte en HTML.
 * 3. dist/app.html garde la coquille vide (toutes les autres pages, servies par Caddy en repli) ;
 *    dist/index.html reçoit l'accueil rendu, servi pour « / ».
 *
 * Aucun serveur Node en production : Caddy sert deux fichiers statiques. Le navigateur reprend ensuite la page (main.ts).
 */
import { readFile, rm, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'vite'

const racine = fileURLToPath(new URL('..', import.meta.url))
const dist = `${racine}dist`
const sortieServeur = `${racine}dist-prerendu`
const CONTENEUR_VIDE = '<div id="app"></div>'

// Au build, aucune API : un appel parti pendant le rendu échoue tout de suite, sans attendre le réseau.
globalThis.fetch = () => Promise.reject(new Error('pré-rendu : pas d’API au build'))

await build({
  root: racine,
  logLevel: 'warn',
  build: { ssr: 'src/entree-prerendu.ts', outDir: sortieServeur, emptyOutDir: true, copyPublicDir: false },
})

try {
  const { rendreAccueil } = await import(pathToFileURL(`${sortieServeur}/entree-prerendu.js`).href)
  const accueil = await rendreAccueil()
  if (!accueil.includes('accueil')) throw new Error('le rendu ne contient pas l’accueil')

  const coquille = await readFile(`${dist}/index.html`, 'utf8')
  if (!coquille.includes(CONTENEUR_VIDE)) throw new Error(`${CONTENEUR_VIDE} introuvable dans dist/index.html`)

  await writeFile(`${dist}/app.html`, coquille)
  await writeFile(`${dist}/index.html`, coquille.replace(CONTENEUR_VIDE, `<div id="app" data-prerendu>${accueil}</div>`))
  console.log(`pré-rendu : accueil écrit dans dist/index.html (${Math.round(accueil.length / 1024)} Ko de HTML)`)
} finally {
  await rm(sortieServeur, { recursive: true, force: true })
}
