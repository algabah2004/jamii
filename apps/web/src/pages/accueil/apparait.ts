import type { Directive } from 'vue'

/**
 * `v-apparait` : l'élément monte en fondu la première fois qu'il entre dans l'écran.
 * La valeur est un délai en millisecondes, pour décaler les cartes d'une même grille.
 *
 * Rien n'est jamais caché sans pouvoir réapparaître : si le navigateur n'a pas d'IntersectionObserver
 * ou d'animations (tests, vieux navigateurs), ou si la personne réduit les animations, l'élément
 * reste simplement visible.
 */
const COURBE = 'cubic-bezier(.2,.8,.2,1)'
const observateurs = new WeakMap<Element, IntersectionObserver>()

function animationsPermises(el: HTMLElement): boolean {
  return (
    typeof IntersectionObserver !== 'undefined' &&
    typeof el.animate === 'function' &&
    typeof matchMedia === 'function' &&
    !matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

export const vApparait: Directive<HTMLElement, number | undefined> = {
  mounted(el, { value }) {
    if (!animationsPermises(el)) return
    el.style.opacity = '0'
    const observateur = new IntersectionObserver(
      (entrees) => {
        if (!entrees.some((e) => e.isIntersecting)) return
        observateur.disconnect()
        observateurs.delete(el)
        el.style.opacity = ''
        el.animate(
          [
            { opacity: 0, transform: 'translateY(28px)' },
            { opacity: 1, transform: 'none' },
          ],
          { duration: 650, delay: value ?? 0, easing: COURBE, fill: 'backwards' },
        )
      },
      // L'élément apparaît un peu avant d'être entièrement à l'écran.
      { rootMargin: '0px 0px -8% 0px' },
    )
    observateur.observe(el)
    observateurs.set(el, observateur)
  },
  unmounted(el) {
    observateurs.get(el)?.disconnect()
    observateurs.delete(el)
  },
}
