/*
 * Rend scripts/apercu/apercu.html en public/og-image.png (1200 × 630), l'image d'aperçu des liens partagés.
 * Lancé à la main, pas au build : l'image est versionnée. Voir scripts/apercu/README.md.
 */
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'

const source = new URL('./apercu.html', import.meta.url)
const sortie = fileURLToPath(new URL('../../public/og-image.png', import.meta.url))

const navigateur = await chromium.launch()
try {
  const page = await navigateur.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 })
  await page.goto(source.href)
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: sortie, type: 'png' })
  console.log(`aperçu écrit : ${sortie}`)
} finally {
  await navigateur.close()
}
