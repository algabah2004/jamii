import { createApp, unref } from 'vue'
import App from './App.vue'
import { demarrerDesignSystem } from './design-system'
import { installerZonesDeTexte } from './design-system/zonesDeTexte'
import { i18n } from './i18n'
import { creerRouteur } from './router'
import { chargerSession } from './session'

async function demarrer() {
  demarrerDesignSystem()
  // `npm run dev:factice` : l'API est simulée par MSW, pour avancer sans le back.
  if (import.meta.env.MODE === 'factice') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledFrame: 'bypass' })
  }
  // La session est connue avant la première navigation, pour que le garde du routeur décide juste.
  await chargerSession()
  document.documentElement.lang = String(unref(i18n.global.locale))
  createApp(App).use(i18n).use(creerRouteur()).mount('#app')
  installerZonesDeTexte()
}

void demarrer()
