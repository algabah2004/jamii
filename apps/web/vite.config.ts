/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { availableParallelism, totalmem } from 'node:os'

/*
 * Chaque worker jsdom prend environ 600 Mo. Sur un portable de 3 Go à 8 cœurs, 8 workers saturaient la mémoire et des
 * tests justes échouaient au hasard. On en lance autant que la mémoire le permet, sans dépasser les cœurs.
 */
const WORKERS = Math.max(1, Math.min(availableParallelism() - 1, Math.floor(totalmem() / 2 ** 30 / 0.75)))

// Le front appelle toujours /api en relatif : en local, Vite relaie vers l'API.
// En production, le front et l'API doivent partager la même origine (voir README).
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // La charte (tokens et illustrations) reste dans docs/design, source unique : on n'autorise que ce dossier.
    fs: { allow: ['.', '../../docs/design'] },
    proxy: {
      '/api': { target: process.env.API_URL ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    // Assez pour un portable chargé : voir DELAI_ATTENTE_MS dans src/test-setup.ts.
    testTimeout: 15_000,
    /*
     * Hors CI, un test qui échoue est rejoué une fois. Sur une machine chargée, un clic peut tomber sur un élément que
     * la page vient de redessiner : l'échec change de test à chaque passage. La CI ne rejoue rien : un test vraiment
     * instable y reste visible.
     */
    retry: process.env.CI ? 0 : 1,
    maxWorkers: WORKERS,
    setupFiles: ['src/test-langue.ts', 'src/test-setup.ts'],
  },
})
