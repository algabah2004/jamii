import { readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { genererCss } from '../../scripts/tokens-css.mjs'
import { appliquerTheme, choisirTheme, JAvatar, JBadgePreuve, JBouton, JChamp, JIllustration, themeActuel, themeRetenu } from '.'
import { contraste } from './contraste'

const lire = (chemin: string) => readFileSync(new URL(chemin, import.meta.url), 'utf8')

describe('tokens.css', () => {
  it('est à jour avec docs/design/tokens.json (sinon : npm run tokens)', () => {
    const tokens = JSON.parse(lire('../../../../docs/design/tokens.json'))
    expect(lire('./tokens.css')).toBe(genererCss(tokens))
  })

  it('donne les deux thèmes, et le bleu en texte change de valeur pour rester lisible sur blanc', () => {
    const css = lire('./tokens.css')
    expect(css).toContain("@media (prefers-color-scheme: dark)")
    expect(css).toContain(":root[data-theme='sombre']")
    expect(css).toMatch(/:root \{[^}]*--action-ink: #0270A6;/)
  })
})

describe('JChamp', () => {
  it('relie libellé, aide et erreur pour les lecteurs d’écran', () => {
    const champ = mount(JChamp, { props: { libelle: 'Ville', aide: '80 caractères au plus', erreur: 'Trop long' } })
    const entree = champ.get('input')
    expect(champ.get('label').attributes('for')).toBe(entree.attributes('id'))
    expect(entree.attributes('aria-invalid')).toBe('true')
    const decrit = entree.attributes('aria-describedby')!.split(' ')
    expect(decrit.map((id) => champ.get(`[id="${id}"]`).text())).toEqual(['80 caractères au plus', 'Trop long'])
  })

  it('sans erreur, le champ n’est pas marqué invalide', async () => {
    const champ = mount(JChamp, { props: { libelle: 'Bio', multiligne: true, modelValue: '' } })
    expect(champ.get('textarea').attributes('aria-invalid')).toBeUndefined()
    await champ.get('textarea').setValue('Développeuse Flutter')
    expect(champ.emitted('update:modelValue')?.[0]).toEqual(['Développeuse Flutter'])
  })
})

describe('JBouton', () => {
  it('pendant un envoi, il est désactivé et l’annonce', () => {
    const bouton = mount(JBouton, { props: { occupe: true }, slots: { default: 'Publier' } })
    expect(bouton.get('button').attributes()).toMatchObject({ disabled: '', 'aria-busy': 'true', type: 'button' })
  })

  it('avec href, c’est un lien', () => {
    const lien = mount(JBouton, { props: { href: '/profil', variante: 'secondaire' }, slots: { default: 'Voir' } })
    expect(lien.get('a').classes()).toContain('j-bouton--secondaire')
  })
})

describe('thème', () => {
  it('le choix de l’utilisateur passe avant celui du système, et « systeme » le rend', () => {
    appliquerTheme('sombre')
    expect(document.documentElement.dataset.theme).toBe('sombre')
    appliquerTheme('systeme')
    expect(document.documentElement.dataset.theme).toBeUndefined()
  })

  it('le badge de preuve ne célèbre qu’à la demande', () => {
    expect(mount(JBadgePreuve, { slots: { default: 'En ligne' } }).classes()).not.toContain('j-preuve--celebrer')
  })
})

describe('contrastes de la charte (WCAG 2.1 AA)', () => {
  const tokens = JSON.parse(lire('../../../../docs/design/tokens.json'))
  const valeurs = Object.fromEntries(tokens.color.tokens.map((t: { name: string; value: unknown }) => [t.name, t.value]))
  const couleur = (nom: string, theme: 'clair' | 'sombre'): string => {
    const v = valeurs[nom]
    if (typeof v === 'object') return v[theme]
    const ref = /^\{(.+)\}$/.exec(v)
    return ref ? couleur(ref[1]!, theme) : v
  }
  const PAIRES: [string, string, number][] = [
    ['ink', 'bg', 4.5], ['ink-muted', 'surface', 4.5], ['action-ink', 'surface', 4.5], ['action-ink', 'bg', 4.5],
    ['on-action', 'action', 4.5], ['on-proof', 'proof', 4.5], ['proof-ink', 'surface', 4.5], ['danger', 'surface', 4.5],
    ['line-strong', 'surface', 3], ['line-strong', 'bg', 3], ['focus', 'bg', 3],
  ]
  for (const theme of ['clair', 'sombre'] as const) {
    it.each(PAIRES)(`${theme} : %s sur %s tient %s:1`, (texte, fond, seuil) => {
      expect(contraste(couleur(texte, theme), couleur(fond, theme))).toBeGreaterThanOrEqual(seuil)
    })
  }

  it('la charte partenaire d’exemple tient aussi les seuils', () => {
    expect(contraste('#7BC86C', '#02070A')).toBeGreaterThanOrEqual(4.5)
    expect(contraste('#2F7A22', '#FFFFFF')).toBeGreaterThanOrEqual(4.5)
    expect(contraste('#2F7A22', '#F4F8FA')).toBeGreaterThanOrEqual(4.5)
    expect(contraste('#7BC86C', '#0B141A')).toBeGreaterThanOrEqual(4.5)
  })

  it('contraste() lit aussi le format rgb() renvoyé par le navigateur', () => {
    expect(contraste('rgb(0, 0, 0)', '#FFFFFF')).toBeCloseTo(21, 1)
  })
})

describe('thème retenu', () => {
  it('le choix est gardé dans ce navigateur, et une valeur inconnue revient à « systeme »', () => {
    choisirTheme('clair')
    expect(themeRetenu()).toBe('clair')
    expect(themeActuel.value).toBe('clair')
    localStorage.setItem('jamii.theme', 'violet')
    expect(themeRetenu()).toBe('systeme')
    choisirTheme('systeme')
  })
})

describe('JAvatar et JIllustration', () => {
  it('sans photo, les initiales (deux au plus), cachées aux lecteurs d’écran', () => {
    const avatar = mount(JAvatar, { props: { nom: 'Aïcha  Kouassi Yao' } })
    expect(avatar.text()).toBe('AK')
    expect(avatar.attributes('aria-hidden')).toBe('true')
  })

  it('l’illustration suit le thème et reste décorative par défaut', () => {
    const html = mount(JIllustration, { props: { nom: 'equipe' } }).html()
    expect(html).toContain('var(--action-soft)')
    expect(html).not.toContain('#DDF0FA')
    expect(html).toContain('aria-hidden="true"')
  })
})
