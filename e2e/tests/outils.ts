import { expect, type Page } from '@playwright/test'

/**
 * Relève les erreurs de la console et de la page pendant un test. Un jury qui ouvre les outils du navigateur ne doit
 * rien y trouver de rouge, hors réponses HTTP attendues (un 401 sur /api/me pour un visiteur sans compte, par exemple).
 */
export function surveillerConsole(page: Page): () => string[] {
  const erreurs: string[] = []
  page.on('pageerror', (e) => erreurs.push(`exception : ${e.message}`))
  page.on('console', (m) => {
    if (m.type() !== 'error' && !/hydrat/i.test(m.text())) return
    // Le navigateur journalise lui-même chaque réponse 4xx/5xx : ce n'est pas une erreur du code.
    if (/Failed to load resource/.test(m.text())) return
    erreurs.push(`${m.type()} : ${m.text()}`)
  })
  return () => erreurs
}

/**
 * Ouvre une session sur un compte démo neuf, comme un visiteur avec le bouton « Explorer avec le compte démo »
 * (POST /api/auth/demo). On attend que la page de connexion soit quittée et que le serveur reconnaisse la session.
 */
export async function entrerEnDemo(page: Page): Promise<void> {
  await page.goto('/connexion')
  await page.getByRole('button', { name: 'Explorer avec le compte démo' }).click()
  await expect(page).not.toHaveURL(/\/connexion/)
  await expect.poll(async () => (await page.request.get('/api/me')).status()).toBe(200)
}
