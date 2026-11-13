<script setup lang="ts">
/**
 * Fait apparaître ou disparaître son contenu en douceur : la hauteur suit, avec un fondu et un léger
 * glissement, au lieu d'un saut qui pousse toute la page. Durée tirée de la charte (--motion-base),
 * donc nulle si l'utilisateur réduit le mouvement. Sans Web Animations (tests), le changement est immédiat.
 */
const COURBE = 'cubic-bezier(.2,.8,.2,1)'

function duree(): number {
  const brute = getComputedStyle(document.documentElement).getPropertyValue('--motion-base').trim()
  const ms = Number.parseFloat(brute) || 0
  return ms * 1.4 // une hauteur qui change demande un peu plus de temps qu'un fondu
}

function animer(el: Element, de: Keyframe, a: Keyframe, fini: () => void, facteur = 1) {
  const e = el as HTMLElement
  const d = duree() * facteur
  if (!d || typeof e.animate !== 'function') return fini()
  e.style.overflow = 'hidden'
  const animation = e.animate([de, a], { duration: d, easing: COURBE })
  animation.onfinish = () => {
    e.style.overflow = ''
    fini()
  }
  animation.oncancel = fini
}

function entrer(el: Element, fini: () => void) {
  const hauteur = (el as HTMLElement).scrollHeight
  animer(el, { height: '0px', opacity: 0, transform: 'translateY(-6px)' }, { height: `${hauteur}px`, opacity: 1, transform: 'none' }, fini)
}
function sortir(el: Element, fini: () => void) {
  const hauteur = (el as HTMLElement).offsetHeight
  animer(el, { height: `${hauteur}px`, opacity: 1 }, { height: '0px', opacity: 0 }, fini, 0.8)
}
</script>

<template>
  <Transition :css="false" @enter="entrer" @leave="sortir"><slot /></Transition>
</template>
