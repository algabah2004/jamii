import { defineConfig, devices } from '@playwright/test'

/**
 * Tests de parcours sur la vraie pile (compose.yml) : le front servi par Caddy, l'API Spring et PostgreSQL.
 * Rien n'est simulé côté navigateur : ce que le test voit, un visiteur le voit.
 */
export default defineConfig({
  testDir: 'tests',
  fullyParallel: true,
  // Une vraie pile (Java, PostgreSQL, deux Caddy) : créer un compte démo rempli prend quelques secondes. Deux
  // navigateurs à la fois suffisent et évitent d'étouffer la machine de CI.
  workers: 2,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.JAMII_URL ?? 'http://localhost:8080',
    locale: 'fr-FR',
    // Le front marque ses éléments avec data-test (comme ses tests Vitest), pas data-testid.
    testIdAttribute: 'data-test',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'ordinateur', use: { ...devices['Desktop Chrome'] } },
    // Le téléphone compte : une bonne part des développeurs du continent navigue d'abord sur mobile.
    { name: 'telephone', use: { ...devices['Pixel 7'] } },
  ],
})
