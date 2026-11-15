/*
 * Les zones d'écriture de toute l'application grandissent avec leur texte, au lieu d'afficher une barre de défilement.
 * Sur Windows (et sur Linux), les barres sont toujours visibles : un `<textarea>` dont le contenu dépasse d'un pixel
 * montrait une barre verticale, là où macOS la cache. Ici, la hauteur suit le contenu ; la barre n'apparaît qu'au-delà de
 * la hauteur maximale du champ (`max-height`, 40 % de l'écran par défaut, base.css).
 *
 * Installé une fois au démarrage : un seul écouteur pour toute la page, et un observateur pour les zones qui
 * apparaissent (dialogues, formulaires dépliés). `ajusterZone` sert aussi quand le texte change sans saisie (après un
 * envoi, la zone est vidée par le code).
 */
export function ajusterZone(zone: HTMLTextAreaElement) {
  if (zone.dataset.hauteurFixe !== undefined || !zone.isConnected) return
  const style = getComputedStyle(zone)
  const bords = parseFloat(style.borderTopWidth) + parseFloat(style.borderBottomWidth)
  zone.style.height = 'auto'
  const voulue = zone.scrollHeight + bords
  const max = parseFloat(style.maxHeight)
  const deborde = Number.isFinite(max) && voulue > max
  zone.style.height = `${deborde ? max : voulue}px`
  zone.style.overflowY = deborde ? 'auto' : 'hidden'
}

let installe = false

export function installerZonesDeTexte(racine: Document = document) {
  if (installe || typeof MutationObserver === 'undefined') return
  installe = true
  const ajusterPlusTard = (zone: HTMLTextAreaElement) => requestAnimationFrame(() => ajusterZone(zone))
  racine.addEventListener('input', (e) => {
    if (e.target instanceof HTMLTextAreaElement) ajusterZone(e.target)
  }, true)
  // Après Entrée (un message envoyé vide la zone) : on réajuste quand Vue a mis la valeur à jour.
  racine.addEventListener('keyup', (e) => {
    if (e.target instanceof HTMLTextAreaElement) ajusterPlusTard(e.target)
  }, true)
  racine.addEventListener('focusin', (e) => {
    if (e.target instanceof HTMLTextAreaElement) ajusterZone(e.target)
  })
  new MutationObserver((changements) => {
    for (const c of changements) {
      for (const n of c.addedNodes) {
        if (n instanceof HTMLTextAreaElement) ajusterPlusTard(n)
        else if (n instanceof HTMLElement) n.querySelectorAll('textarea').forEach((z) => ajusterPlusTard(z))
      }
    }
  }).observe(racine.body, { childList: true, subtree: true })
  racine.querySelectorAll('textarea').forEach((z) => ajusterPlusTard(z))
}
