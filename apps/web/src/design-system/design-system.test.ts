import { readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { genererCss } from '../../scripts/tokens-css.mjs'
import { appliquerTheme, JBadgePreuve, JBouton, JChamp } from '.'

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
