import { nextTick, watch, type Ref, type WatchSource } from 'vue'

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
