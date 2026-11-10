/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// En local, le front (5173) appelle l'API (8080) via ce proxy : même origine, donc le cookie
// de session et le jeton CSRF fonctionnent sans CORS.
const api = process.env.API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { '/api': api, '/health': api },
  },
  test: {
    environment: 'jsdom',
  },
})
