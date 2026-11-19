import { ATTRIBUT_PRERENDU, creerApplication, peutHydrater } from './application'
import { demarrerDesignSystem } from './design-system'
import { installerZonesDeTexte } from './design-system/zonesDeTexte'
import { i18n } from './i18n'
import { chargerSession, useSession } from './session'

async function demarrer() {
  demarrerDesignSystem()
  // `npm run dev:factice` : l'API est simulée par MSW, pour avancer sans le back.
  if (import.meta.env.MODE === 'factice') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledFrame: 'bypass' })
  }
  // La session est connue avant la première navigation, pour que le garde du routeur décide juste.
  // Pendant ce temps, l'accueil pré-rendu est déjà affiché (scripts/prerendre.mjs).
  await chargerSession()
  const conteneur = document.getElementById('app')!
  const hydratable = peutHydrater({
    prerendu: conteneur.hasAttribute(ATTRIBUT_PRERENDU),
    chemin: window.location.pathname,
    etat: useSession().etat.value,
    langue: i18n.global.locale as unknown as string,
  })
  const { app, routeur } = creerApplication({ hydratable })
  // L'hydratation exige que la route soit résolue avant le montage, comme au pré-rendu.
  await routeur.isReady()
  // Montage à neuf : Vue remplace le contenu pré-rendu par la page demandée.
  app.mount(conteneur)
  conteneur.removeAttribute(ATTRIBUT_PRERENDU)
  installerZonesDeTexte()
}

void demarrer()
