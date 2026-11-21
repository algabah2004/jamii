import { expect, test } from '@playwright/test'
import { surveillerConsole } from './outils'

test.describe("L'accueil pré-rendu (docs/adr/0011)", () => {
  test('le HTML servi contient déjà la page, avant tout JavaScript', async ({ request }) => {
    const reponse = await request.get('/')
    expect(reponse.status()).toBe(200)
    const html = await reponse.text()
    expect(html).toContain('data-prerendu')
    expect(html).toContain('Trouve ton équipe.')
    expect(html).toContain('og:title')
  })

  test("l'image d'aperçu des liens est servie, au format annoncé", async ({ request }) => {
    const html = await (await request.get('/')).text()
    expect(html).toContain('<meta property="og:image" content="https://jamii.dev/og-image.png" />')
    const image = await request.get('/og-image.png')
    expect(image.status()).toBe(200)
    expect(image.headers()['content-type']).toBe('image/png')
    // En-tête PNG puis largeur et hauteur du bloc IHDR : 1200 × 630, comme le disent og:image:width et og:image:height.
    const octets = await image.body()
    expect([octets.readUInt32BE(16), octets.readUInt32BE(20)]).toEqual([1200, 630])
  })

  test("les autres pages reçoivent la coquille vide, jamais l'accueil", async ({ request }) => {
    for (const chemin of ['/missions', '/p/inconnu', '/cercles']) {
      const html = await (await request.get(chemin)).text()
      expect(html).toContain('<div id="app"></div>')
      expect(html).not.toContain('data-prerendu')
    }
  })

  test("l'accueil s'affiche sans JavaScript", async ({ browser }) => {
    const contexte = await browser.newContext({ javaScriptEnabled: false })
    const page = await contexte.newPage()
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Trouve ton équipe.')
    await contexte.close()
  })

  test("le navigateur reprend la page sans erreur d'hydratation", async ({ page }) => {
    const erreurs = surveillerConsole(page)
    await page.goto('/')
    // La marque disparaît quand Vue a pris la main sur le HTML pré-rendu.
    await expect(page.locator('#app:not([data-prerendu])')).toBeAttached()
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Trouve ton équipe.')
    expect(erreurs()).toEqual([])
  })

  test("en anglais, la page est redessinée dans la langue du visiteur, sans erreur", async ({ browser }) => {
    const contexte = await browser.newContext({ locale: 'en-US' })
    const page = await contexte.newPage()
    const erreurs = surveillerConsole(page)
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Find your team.')
    expect(erreurs()).toEqual([])
    await contexte.close()
  })
})
