import { expect, test } from '@playwright/test'
import { entrerEnDemo, surveillerConsole } from './outils'

test.describe('Le parcours du compte démo, sur la vraie API', () => {
  test('un visiteur entre en démo et retrouve son profil rempli', async ({ page }) => {
    const erreurs = surveillerConsole(page)
    await entrerEnDemo(page)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(erreurs()).toEqual([])
  })

  test('il parcourt les cercles, les communautés et ses messages', async ({ page }) => {
    const erreurs = surveillerConsole(page)
    await entrerEnDemo(page)
    for (const chemin of ['/cercles', '/communautes', '/messages', '/notifications', '/demandes']) {
      await page.goto(chemin)
      await expect(page).toHaveURL(new RegExp(`${chemin}`))
      await expect(page.locator('main, #contenu').first()).toBeVisible()
      // Aucune page ne doit tomber sur « Jamii ne répond pas » ou sur la page introuvable.
      await expect(page.getByText('Page introuvable')).toHaveCount(0)
    }
    expect(erreurs()).toEqual([])
  })

  test('chaque visiteur a son propre compte démo : deux sessions ne se voient pas', async ({ browser }) => {
    const [a, b] = await Promise.all([browser.newContext(), browser.newContext()])
    const [pageA, pageB] = await Promise.all([a.newPage(), b.newPage()])
    await entrerEnDemo(pageA)
    await entrerEnDemo(pageB)
    const moiA = await (await pageA.request.get('/api/me')).json()
    const moiB = await (await pageB.request.get('/api/me')).json()
    expect(moiA.id).not.toBe(moiB.id)
    await Promise.all([a.close(), b.close()])
  })

  test('se déconnecter ferme vraiment la session côté serveur', async ({ page }) => {
    await entrerEnDemo(page)
    expect((await page.request.get('/api/me')).status()).toBe(200)
    await page.goto('/')
    const bouton = page.getByRole('button', { name: 'Se déconnecter' }).first()
    if (!(await bouton.isVisible())) await page.getByTestId('bouton-menu').click()
    await page.getByRole('button', { name: 'Se déconnecter' }).first().click()
    await expect(page.getByTestId('lien-connexion')).toBeVisible()
    expect((await page.request.get('/api/me')).status()).toBe(401)
  })
})
