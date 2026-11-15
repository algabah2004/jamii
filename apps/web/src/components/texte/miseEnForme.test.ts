import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TexteRiche from './TexteRiche.vue'
import { appliquerFormat, blocs, lienSur, morceaux } from './miseEnForme'

describe('la mise en forme des messages', () => {
  it('comprend gras, italique, barré, code, liens et mentions', () => {
    expect(morceaux('un **gros** _petit_ ~~faux~~ `x = 1` [doc](https://jamii.dev) @Kofi')).toEqual([
      { type: 'texte', texte: 'un ' },
      { type: 'gras', enfants: [{ type: 'texte', texte: 'gros' }] },
      { type: 'texte', texte: ' ' },
      { type: 'italique', enfants: [{ type: 'texte', texte: 'petit' }] },
      { type: 'texte', texte: ' ' },
      { type: 'barre', enfants: [{ type: 'texte', texte: 'faux' }] },
      { type: 'texte', texte: ' ' },
      { type: 'code', texte: 'x = 1' },
      { type: 'texte', texte: ' ' },
      { type: 'lien', url: 'https://jamii.dev', enfants: [{ type: 'texte', texte: 'doc' }] },
      { type: 'texte', texte: ' ' },
      { type: 'mention', texte: '@Kofi' },
    ])
  })

  it('range le texte en blocs : paragraphes, code, citations, listes', () => {
    const b = blocs('Bonjour\nla suite\n\n```js\nconst a = 1\n```\n> cité\n- un\n- deux\n1. premier')
    expect(b.map((x) => x.type)).toEqual(['paragraphe', 'code', 'citation', 'liste', 'liste'])
    expect(b[1]).toEqual({ type: 'code', texte: 'const a = 1', langue: 'js' })
  })

  it("n'accepte que des liens web : jamais javascript: ni data:", () => {
    expect(lienSur('javascript:alert(1)')).toBeNull()
    expect(lienSur('data:text/html,x')).toBeNull()
    expect(lienSur('https://example.com/a?b=c')).toBe('https://example.com/a?b=c')
    expect(morceaux('[clic](javascript:alert(1))')[0]).toEqual({ type: 'texte', texte: '[clic](javascript:alert(1))' })
  })

  it('affiche le HTML écrit par quelqu’un comme du texte, jamais comme du code', () => {
    const page = mount(TexteRiche, { props: { texte: '<img src=x onerror=alert(1)> **ok**' } })
    expect(page.find('img').exists()).toBe(false)
    expect(page.text()).toContain('<img src=x onerror=alert(1)>')
    expect(page.find('strong').text()).toBe('ok')
  })

  it('les boutons entourent la sélection, ou préfixent les lignes', () => {
    expect(appliquerFormat('dis bonjour', 4, 11, 'gras')).toEqual({ texte: 'dis **bonjour**', debut: 6, fin: 13 })
    expect(appliquerFormat('a\nb', 0, 3, 'liste').texte).toBe('- a\n- b')
    expect(appliquerFormat('', 0, 0, 'code', 'code').texte).toBe('`code`')
  })
})
