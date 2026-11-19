import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

/**
 * Accessibilité des pages publiques (WCAG 2.1 A et AA, règles d'axe-core) : aucune violation grave ou critique.
 * Les pages sont vues après la reprise par le navigateur, dans l'état qu'un visiteur voit.
 */
for (const chemin of ['/', '/connexion', '/vitrine', '/missions', '/hackathons']) {
  test(`${chemin} : aucune violation grave d'accessibilité`, async ({ page }) => {
    await page.goto(chemin)
    await expect(page.locator('#app:not([data-prerendu])')).toBeAttached()
    await page.waitForLoadState('networkidle')
    const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
    const graves = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    expect(graves.map((v) => `${v.id} (${v.nodes.length}) : ${v.help}`)).toEqual([])
  })
}
