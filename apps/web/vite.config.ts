/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

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
    setupFiles: ['src/test-setup.ts'],
  },
})
