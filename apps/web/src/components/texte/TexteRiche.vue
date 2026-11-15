<script setup lang="ts">
import { h, type VNode } from 'vue'
import { blocs, type Morceau } from './miseEnForme'

/*
 * Un message mis en forme (miseEnForme.ts) : construit élément par élément avec `h()`, jamais avec `v-html`. Le texte
 * de chacun reste du texte : rien de ce qu'écrit un membre ne peut devenir du code dans la page des autres.
 */
const props = defineProps<{ texte: string }>()

function rendreMorceaux(liste: Morceau[]): (VNode | string)[] {
  return liste.map((m) => {
    switch (m.type) {
      case 'texte': return m.texte
      case 'gras': return h('strong', rendreMorceaux(m.enfants))
      case 'italique': return h('em', rendreMorceaux(m.enfants))
      case 'barre': return h('s', rendreMorceaux(m.enfants))
      case 'code': return h('code', { class: 'tr-code' }, m.texte)
      case 'mention': return h('span', { class: 'tr-mention' }, m.texte)
      case 'lien': return h('a', { href: m.url, target: '_blank', rel: 'noopener noreferrer nofollow ugc' }, rendreMorceaux(m.enfants))
    }
  })
}

function lignes(l: Morceau[][]) {
  return l.flatMap((morceaux, i) => (i ? [h('br'), ...rendreMorceaux(morceaux)] : rendreMorceaux(morceaux)))
}

const Rendu = () =>
  blocs(props.texte).map((b) => {
    switch (b.type) {
      case 'paragraphe': return h('p', lignes(b.lignes))
      case 'citation': return h('blockquote', lignes(b.lignes))
      case 'liste': return h(b.ordonnee ? 'ol' : 'ul', b.elements.map((e) => h('li', rendreMorceaux(e))))
      case 'code': return h('pre', { class: 'tr-bloc' }, [h('code', { 'data-langue': b.langue ?? undefined }, b.texte)])
    }
  })
</script>

<template>
  <div class="texte-riche"><Rendu /></div>
</template>

<style>
.texte-riche { display: grid; gap: var(--space-2); overflow-wrap: anywhere; line-height: 1.55; }
.texte-riche > * { margin: 0; }
.texte-riche ul, .texte-riche ol { padding-inline-start: 1.4em; display: grid; gap: 2px; }
.texte-riche blockquote { padding: 2px 0 2px var(--space-3); border-left: 3px solid var(--line-strong); color: var(--ink-muted); }
.texte-riche a { color: var(--action-ink); text-decoration: underline; text-underline-offset: 2px; text-decoration-thickness: 1px; }
.texte-riche .tr-code { padding: 1px 5px; border-radius: 5px; background: var(--surface-raised); border: 1px solid var(--line); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.875em; }
.texte-riche .tr-bloc { max-width: 100%; overflow-x: auto; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--surface-raised); border: 1px solid var(--line); }
.texte-riche .tr-bloc code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.8125rem; line-height: 1.6; white-space: pre; }
.texte-riche .tr-mention { padding: 0 3px; border-radius: 4px; background: var(--action-soft); color: var(--action-ink); font-weight: 600; }
</style>
