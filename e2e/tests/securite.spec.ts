import { expect, test } from '@playwright/test'
import { entrerEnDemo } from './outils'

test.describe('Sécurité vue du navigateur', () => {
  test('les pages portent les en-têtes de sécurité (CSP, cadre interdit, nosniff)', async ({ request }) => {
    for (const chemin of ['/', '/missions']) {
      const entetes = (await request.get(chemin)).headers()
      expect(entetes['content-security-policy']).toContain("script-src 'self'")
      expect(entetes['content-security-policy']).toContain("frame-ancestors 'none'")
      expect(entetes['x-frame-options']).toBe('DENY')
      expect(entetes['x-content-type-options']).toBe('nosniff')
      expect(entetes['server']).toBeUndefined()
    }
  })

  test("l'API refuse un visiteur sans session, au format d'erreur du contrat", async ({ request }) => {
    const reponse = await request.get('/api/me')
    expect(reponse.status()).toBe(401)
    expect(reponse.headers()['content-type']).toContain('application/problem+json')
    expect((await reponse.json()).code).toBe('non_connecte')
  })

  test("un visiteur sans compte ne reçoit aucune réponse en erreur de l'API", async ({ page }) => {
    const erreurs: string[] = []
    page.on('response', (r) => {
      if (r.url().includes('/api/') && r.status() >= 400) erreurs.push(`${r.status()} ${r.url()}`)
    })
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Trouve ton équipe.')
    await page.waitForLoadState('networkidle')
    expect(erreurs).toEqual([])
    const session = await (await page.request.get('/api/auth/session')).json()
    expect(session).toEqual({ connecte: false, moi: null })
  })

  test('une écriture sans jeton CSRF est refusée, même avec une session', async ({ page }) => {
    await entrerEnDemo(page)
    // Même cookie de session, mais sans l'en-tête X-XSRF-TOKEN que pose le front.
    const reponse = await page.request.post('/api/auth/deconnexion', { headers: { 'X-XSRF-TOKEN': '' } })
    expect(reponse.status()).toBe(403)
    expect((await page.request.get('/api/me')).status()).toBe(200)
  })

  test('une page réservée renvoie vers la connexion, puis revient où on voulait aller', async ({ page }) => {
    await page.goto('/cercles')
    await expect(page).toHaveURL(/\/connexion\?retour=(%2F|\/)cercles$/)
    await page.getByRole('button', { name: 'Explorer avec le compte démo' }).click()
    await expect(page).toHaveURL(/\/cercles$/)
  })
})
