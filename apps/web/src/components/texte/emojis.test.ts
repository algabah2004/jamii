import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { i18n } from '../../i18n'
import Composeur from './Composeur.vue'
import TexteRiche from './TexteRiche.vue'
import { CATEGORIES, chercherEmojis, convertirALaFin, convertirEnTapant, seulementDesEmojis, TOUS } from './emojis'

enableAutoUnmount(afterEach)

describe('les emojis (#157)', () => {
  it('offre un vrai choix, sans doublon dans une catégorie, retrouvable en français et en anglais', () => {
    expect(TOUS.length).toBeGreaterThan(150)
    for (const c of CATEGORIES) expect(new Set(c.emojis.map((x) => x.e)).size).toBe(c.emojis.length)
    expect(chercherEmojis('rir').map((x) => x.e)).toEqual(expect.arrayContaining(['😂', '😆']))
    expect(chercherEmojis('Fusée').map((x) => x.e)).toContain('🚀')
    expect(chercherEmojis('thanks').map((x) => x.e)).toContain('🙏')
    expect(chercherEmojis('')).toEqual([])
  })

  it('convertit un smiley tapé seul, jamais dans un lien ni dans du code', () => {
    const tape = (texte: string) => convertirEnTapant(texte, texte.length)?.texte ?? texte
    expect(tape('Merci :) ')).toBe('Merci 🙂 ')
    expect(tape(':D\n')).toBe('😄\n')
    expect(tape('je t’aime <3 ')).toBe('je t’aime ❤️ ')
    expect(tape('voir http://jamii.dev ')).toBe('voir http://jamii.dev ')
    expect(tape('le point (8) ')).toBe('le point (8) ')
    expect(tape('`a :) ')).toBe('`a :) ')
    expect(tape('```\nx :) ')).toBe('```\nx :) ')
    // Pas encore d'espace : on attend la fin du mot.
    expect(convertirEnTapant('ok :', 4)).toBeNull()
    expect(convertirALaFin('À demain ;)')).toBe('À demain 😉')
    expect(convertirALaFin('rien à changer')).toBe('rien à changer')
  })

  it('reconnaît un message fait seulement d’emojis', () => {
    expect(seulementDesEmojis('🔥')).toBe(true)
    expect(seulementDesEmojis('👍🏾 🎉')).toBe(true)
    expect(seulementDesEmojis('❤️❤️❤️❤️')).toBe(false)
    expect(seulementDesEmojis('ok 👍')).toBe(false)
    expect(mount(TexteRiche, { props: { texte: '🎉' } }).classes()).toContain('texte-riche--emojis')
  })

  it('dans la zone de saisie : choisir un emoji, convertir « :) », annuler avec Retour arrière', async () => {
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    const envoyes: string[] = []
    const Hote = defineComponent(() => {
      const texte = ref('Salut')
      return () => h(Composeur, {
        modelValue: texte.value,
        'onUpdate:modelValue': (v: string) => { texte.value = v },
        onEnvoyer: () => { envoyes.push(texte.value) },
        placeholder: 'Écrire',
        libelleEnvoyer: 'Envoyer',
        idZone: 'zone-test',
        avecFichiers: false,
      })
    })
    const page = mount(Hote, { global: { plugins: [i18n] }, attachTo: document.body })
    const zone = page.get('textarea').element as HTMLTextAreaElement

    // Le choix : la recherche, puis un clic insère l'emoji au curseur.
    await page.get('[data-test="ouvrir-emojis"]').trigger('click')
    await page.get('[data-test="chercher-emoji"]').setValue('feu')
    await page.get('[data-test="emoji"]').trigger('click')
    await vi.waitFor(() => expect(zone.value).toBe('Salut🔥'))

    // « :) » suivi d'un espace devient 🙂 ; Retour arrière juste après remet « :) ».
    const taper = async (valeur: string) => {
      zone.value = valeur
      zone.setSelectionRange(valeur.length, valeur.length)
      zone.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: valeur.at(-1) }))
      await vi.waitFor(() => expect(zone.value).not.toBe(valeur))
    }
    await taper('Salut🔥 :) ')
    expect(zone.value).toBe('Salut🔥 🙂 ')
    await page.get('textarea').trigger('keydown', { key: 'Backspace' })
    await vi.waitFor(() => expect(zone.value).toBe('Salut🔥 :)'))

    // À l'envoi, le smiley final est converti aussi.
    await page.get('textarea').trigger('keydown', { key: 'Enter' })
    expect(envoyes).toEqual(['Salut🔥 🙂'])
  })
})
