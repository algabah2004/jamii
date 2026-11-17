import { nextTick, onBeforeUnmount, onMounted, watch, type Ref, type WatchSource } from 'vue'

/** La place laissée au bord d'une rangée, sous le fondu (32 px) : un élément au focus doit être lisible en entier. */
export const MARGE_FONDU = 32

/**
 * De combien faire défiler une rangée pour qu'un élément y soit visible en entier, hors des fondus des bords.
 * 0 s'il l'est déjà. Les positions sont celles de `getBoundingClientRect` (gauche, droite).
 */
export function decalagePourVoir(rangee: { left: number; right: number }, el: { left: number; right: number }, marge = MARGE_FONDU) {
  const gauche = rangee.left + marge
  const droite = rangee.right - marge
  if (el.right - el.left > droite - gauche) return el.left - gauche // plus large que la place : on montre son début
  if (el.left < gauche) return el.left - gauche
  if (el.right > droite) return el.right - droite
  return 0
}

/** La rangée qui défile à l'horizontale et contient l'élément, s'il y en a une. */
function rangeeDe(el: HTMLElement): HTMLElement | null {
  for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
    const ox = getComputedStyle(p).overflowX
    if ((ox === 'auto' || ox === 'scroll') && p.scrollWidth > p.clientWidth) return p
  }
  return null
}

/**
 * Au clavier, Chrome ne fait pas défiler une rangée quand l'élément qui reçoit le focus y est à moitié visible :
 * « Missions » restait coupé sous le fondu du menu. Une seule écoute pour toute l'application : la rangée amène
 * l'élément en entier. Seule la rangée défile, jamais la page.
 */
export function garderFocusVisibleDansLesRangees() {
  const surFocus = (e: FocusEvent) => {
    const el = e.target
    if (!(el instanceof HTMLElement)) return
    const rangee = rangeeDe(el)
    if (!rangee) return
    const delta = decalagePourVoir(rangee.getBoundingClientRect(), el.getBoundingClientRect())
    if (delta) rangee.scrollLeft += delta
  }
  onMounted(() => document.addEventListener('focusin', surFocus))
  onBeforeUnmount(() => document.removeEventListener('focusin', surFocus))
}

/**
 * Une rangée qui défile à l'horizontale sur téléphone (menu, onglets) : quand le choix actif change, il est ramené
 * au centre de la rangée. Seule la rangée défile, jamais la page (pas de scrollIntoView). Voir docs/responsive.md.
 */
export function centrerActif(rangee: Ref<HTMLElement | null>, selecteur: string, sources: WatchSource[]) {
  watch(
    sources,
    async () => {
      await nextTick()
      const r = rangee.value
      const actif = r?.querySelector<HTMLElement>(selecteur)
      if (!r || !actif || r.scrollWidth <= r.clientWidth) return
      r.scrollLeft = actif.offsetLeft - (r.clientWidth - actif.offsetWidth) / 2
    },
    { flush: 'post', immediate: true },
  )
}
