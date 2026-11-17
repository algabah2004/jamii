import { onBeforeUnmount, watchEffect } from 'vue'

/** La classe posée sur `<html>` : sur téléphone, App.vue masque alors l'en-tête et le pied du site. */
export const CLASSE_PLEIN_ECRAN = 'discussion-plein-ecran'

/**
 * Une discussion (une conversation privée, un salon) prend tout l'écran du téléphone, comme une application de
 * messagerie : son propre bandeau avec la flèche retour en haut, les messages, et la zone pour écrire en bas.
 * Seul le CSS décide du « téléphone » (720 px et moins) : sur grand écran, la classe ne change rien.
 */
export function usePleinEcranTelephone(actif: () => boolean) {
  watchEffect(() => document.documentElement.classList.toggle(CLASSE_PLEIN_ECRAN, actif()))
  onBeforeUnmount(() => document.documentElement.classList.remove(CLASSE_PLEIN_ECRAN))
}
