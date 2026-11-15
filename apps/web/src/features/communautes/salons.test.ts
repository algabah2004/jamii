import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] }, attachTo: document.body })
  return { page, routeur }
}

afterEach(() => {
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('les salons en direct', () => {
  it('ouvrent # général, puis un autre salon depuis la barre ; un membre peut y lancer un appel', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter?salon=general')
    await vi.waitFor(() => expect(page.find('[data-test="salons"] h1').exists()).toBe(true))
    expect(page.find('[data-test="salons"] h1').text()).toBe('# général')
    expect(page.find('[data-test="barre-salon-general"]').attributes('aria-current')).toBe('page')

    await page.find('[data-test="barre-salon-entraide"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(3))
    expect(routeur.currentRoute.value.query.salon).toBe('entraide')
    expect(page.find('[data-test="barre-salon-entraide"]').attributes('aria-current')).toBe('page')
    // Pas de salle ouverte en permanence : tant que personne ne lance d'appel, il n'y a rien à rejoindre.
    expect(page.find('[data-test="lancer-appel"]').exists()).toBe(true)
    expect(page.find('[data-test="rejoindre-appel"]').exists()).toBe(false)
    page.unmount()
  })

  it('lancer un appel ouvre sa salle dans un nouvel onglet, l’annonce dans le salon, et on peut le terminer', async () => {
    const onglet = { opener: {} as unknown, location: { href: '' }, close: vi.fn() }
    const ouvrirOnglet = vi.spyOn(window, 'open').mockReturnValue(onglet as unknown as Window)
    const { page } = await ouvrir('/communautes/flutter?salon=entraide')
    await vi.waitFor(() => expect(page.find('[data-test="lancer-appel"]').exists()).toBe(true))

    await page.get('[data-test="lancer-appel"]').trigger('click')
    expect(ouvrirOnglet).toHaveBeenCalledWith('', '_blank')
    await vi.waitFor(() => expect(page.find('[data-test="appel-en-cours"]').exists()).toBe(true))
    expect(onglet.location.href).toMatch(/^https:\/\/meet\.jit\.si\/jamii-flutter-entraide-/)
    expect(onglet.opener).toBeNull()
    const rejoindre = page.get('[data-test="rejoindre-appel"]')
    expect(rejoindre.attributes('href')).toBe(onglet.location.href)
    expect(rejoindre.attributes('rel')).toBe('noopener noreferrer')
    expect(page.find('[data-test="lancer-appel"]').exists()).toBe(false)
    await vi.waitFor(() => expect(page.find('[data-test="annonce-appel"]').exists()).toBe(true))
    expect(page.get('[data-test="annonce-appel"]').text()).toContain('a lancé un appel')

    await page.get('[data-test="terminer-appel"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="lancer-appel"]').exists()).toBe(true))
    await vi.waitFor(() => expect(page.get('[data-test="annonce-appel"]').text()).toContain('Appel terminé'))
    ouvrirOnglet.mockRestore()
    page.unmount()
  })

  it('groupe les messages d’un même auteur et marque le changement de jour', async () => {
    const { page } = await ouvrir('/communautes/flutter?salon=general')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(7))
    // Aya écrit deux fois à quelques minutes d'écart, avec Koffi entre les deux : pas de groupe. Fatou puis Wanjiru puis Fatou non plus.
    // Les messages de démo s'étalent sur plus d'un jour : au moins deux séparateurs, et le dernier est « Aujourd'hui »
    // (le libellé des jours précédents dépend de l'heure à laquelle le test tourne).
    const jours = page.findAll('.jour').map((j) => j.text())
    expect(jours.length).toBeGreaterThanOrEqual(2)
    expect(jours.at(-1)).toBe("Aujourd'hui")
    expect(page.findAll('.message--suite')).toHaveLength(0)
    page.unmount()
  })

  it('on arrive sur le fil : la bannière, « En direct », et la recherche filtre les publications', async () => {
    const { page } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.findAll('[data-test="publication"]')).toHaveLength(4))
    expect(page.find('[data-test="banniere"]').text()).toContain('Flutter et Dart')
    expect(page.find('[data-test="barre-fil"]').attributes('aria-current')).toBe('page')
    await vi.waitFor(() => expect(page.find('[data-test="en-direct"]').text()).toContain('# entraide'))
    await page.get('[data-test="recherche"]').setValue('apk')
    expect(page.findAll('[data-test="publication"]')).toHaveLength(1)
    await page.get('[data-test="recherche"]').setValue('rien de tel ici')
    expect(page.find('[data-test="vide"]').text()).toBe('Rien ne correspond à ta recherche.')
    page.unmount()
  })

  it('les membres : la modération d’abord, et « Écrire » mène à la conversation', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.find('[data-test="barre-membres"]').exists()).toBe(true))
    await page.get('[data-test="barre-membres"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="membre"]').length).toBeGreaterThan(2))
    expect(routeur.currentRoute.value.query.voir).toBe('membres')
    expect(page.findAll('[data-test="membre"]')[0]!.text()).toContain('Modération')
    expect(page.find('[data-test="membre"] .membre__ecrire').attributes('href')).toMatch(/^\/messages\//)
    expect(page.find('[data-test="membre"] [data-test="lien-personne"]').attributes('href')).toMatch(/^\/p\//)
    page.unmount()
  })

  it('envoie un message, le montre tout de suite, et Entrée suffit', async () => {
    const { page } = await ouvrir('/communautes/flutter?salon=entraide')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(3))
    const zone = page.find('[data-test="ecrire"] textarea')
    await zone.setValue('  Merci, je teste Hive ce soir.  ')
    await zone.trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(4))
    expect(page.findAll('[data-test="message"]').at(-1)!.text()).toContain('Merci, je teste Hive ce soir.')
    expect((zone.element as HTMLTextAreaElement).value).toBe('')
    page.unmount()
  })

  it('relit seulement les nouveaux messages toutes les 3 secondes, et arrête en quittant la page', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const lectures: (string | null)[] = []
    serveur.use(
      http.get('/api/communautes/:slug/salons/:salon/messages', ({ params, request }) => {
        const adresse = new URL(request.url)
        const apres = adresse.searchParams.get('apres')
        // La barre et « En direct » lisent aussi les autres salons : on ne compte que la conversation de # entraide.
        if (params.salon === 'entraide' && adresse.searchParams.get('limite') !== '1') lectures.push(apres)
        const elements = apres === null
          ? [{ id: 'm1', numero: 7, auteur: { id: 'k', nom: 'Koffi Yao', avatarUrl: null }, texte: 'Premier', envoyeLe: new Date().toISOString() }]
          : [{ id: 'm2', numero: 8, auteur: { id: 'w', nom: 'Wanjiru Kamau', avatarUrl: null }, texte: 'Arrivé en direct', envoyeLe: new Date().toISOString() }]
        return HttpResponse.json({ elements })
      }),
    )
    const { page } = await ouvrir('/communautes/flutter?salon=entraide')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(1))
    expect(lectures).toEqual([null])

    vi.advanceTimersByTime(3000)
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(2))
    expect(lectures).toEqual([null, '7'])
    expect(page.text()).toContain('Arrivé en direct')

    page.unmount()
    vi.advanceTimersByTime(9000)
    expect(lectures).toHaveLength(2)
  })

  it("garde les messages et le dit quand la connexion coupe", async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const { page } = await ouvrir('/communautes/flutter?salon=entraide')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(3))
    serveur.use(http.get('/api/communautes/:slug/salons/:salon/messages', () => HttpResponse.error()))
    vi.advanceTimersByTime(3000)
    await vi.waitFor(() => expect(page.text()).toContain('Connexion perdue'))
    expect(page.findAll('[data-test="message"]')).toHaveLength(3)
    page.unmount()
  })

  it("à un non-membre : on lit, mais ni appel ni écriture, et on l'invite à rejoindre", async () => {
    const { page } = await ouvrir('/communautes/fintech?salon=general')
    await vi.waitFor(() => expect(page.find('[data-test="salons"] h1').exists()).toBe(true))
    expect(page.find('[data-test="lancer-appel"]').exists()).toBe(false)
    expect(page.find('[data-test="rejoindre-appel"]').exists()).toBe(false)
    expect(page.find('[data-test="ecrire"]').exists()).toBe(false)
    expect(page.find('[data-test="rejoindre-salon"]').exists()).toBe(true)
    expect(page.text()).toContain('Rejoins la communauté pour écrire ici')
    page.unmount()
  })
})
