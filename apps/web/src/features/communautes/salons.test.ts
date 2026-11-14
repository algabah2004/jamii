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
  it("ouvrent # général par défaut, puis un autre salon depuis la barre, avec l'appel pour un membre", async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.find('[data-test="salons"] h1').exists()).toBe(true))
    expect(page.find('[data-test="salons"] h1').text()).toBe('# général')
    expect(page.find('[data-test="barre-salon-general"]').attributes('aria-current')).toBe('page')

    await page.find('[data-test="barre-salon-entraide"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(3))
    expect(routeur.currentRoute.value.query.salon).toBe('entraide')
    expect(page.find('[data-test="barre-salon-entraide"]').attributes('aria-current')).toBe('page')
    const appel = page.find('[data-test="appel"]')
    expect(appel.attributes('href')).toBe('https://meet.jit.si/jamii-flutter-entraide-demo')
    expect(appel.attributes('target')).toBe('_blank')
    expect(appel.attributes('rel')).toBe('noopener noreferrer')
    page.unmount()
  })

  it('groupe les messages d’un même auteur et marque le changement de jour', async () => {
    const { page } = await ouvrir('/communautes/flutter?salon=general')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]')).toHaveLength(7))
    // Aya écrit deux fois à quelques minutes d'écart, avec Koffi entre les deux : pas de groupe. Fatou puis Wanjiru puis Fatou non plus.
    expect(page.findAll('.jour').map((j) => j.text())).toEqual(['Hier', "Aujourd'hui"])
    expect(page.findAll('.message--suite')).toHaveLength(0)
    page.unmount()
  })

  it('le forum des questions est dans la barre, avec le nombre de questions ouvertes', async () => {
    const { page, routeur } = await ouvrir('/communautes/flutter')
    await vi.waitFor(() => expect(page.find('[data-test="barre-questions"]').exists()).toBe(true))
    await page.find('[data-test="barre-questions"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="publications"] > li').length).toBeGreaterThan(0))
    expect(routeur.currentRoute.value.query.voir).toBe('questions')
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
      http.get('/api/communautes/:slug/salons/:salon/messages', ({ request }) => {
        const adresse = new URL(request.url)
        const apres = adresse.searchParams.get('apres')
        // La barre lit aussi le dernier message de chaque salon (limite=1) : on ne compte que la conversation.
        if (adresse.searchParams.get('limite') !== '1') lectures.push(apres)
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
    expect(page.find('[data-test="appel"]').exists()).toBe(false)
    expect(page.find('[data-test="ecrire"]').exists()).toBe(false)
    expect(page.find('[data-test="rejoindre-salon"]').exists()).toBe(true)
    expect(page.text()).toContain('Rejoins la communauté pour écrire ici')
    page.unmount()
  })
})
