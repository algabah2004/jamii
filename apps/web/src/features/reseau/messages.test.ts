import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN } from '../../mocks/cercles'
import { rafraichirActivite } from './activite'
import { quand } from './reseau'
import { reinitialiserAppel } from './appel'
import { installerFauxWebrtc } from './fauxWebrtc'

enableAutoUnmount(afterEach)

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  await rafraichirActivite()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

const KOFI = 'c1a00000-0000-4000-8000-000000000002'
const AMINA = 'd2e00000-0000-4000-8000-000000000002'

describe('les messages', () => {
  it('compte les nouveautés sur la cloche, et retire celle du message une fois la conversation lue', async () => {
    const { page, routeur } = await ouvrir('/cercles')
    // L'invitation de Kofi et la demande de contact d'Ibrahima.
    await vi.waitFor(() => expect(page.find('[data-test="lien-activite"]').attributes('aria-label')).toBe('Notifications, 2 nouvelles'))
    await routeur.push(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(page.find('[data-test="fil"]').exists()).toBe(true))
    await vi.waitFor(() => expect(page.find('[data-test="lien-activite"]').attributes('aria-label')).toBe('Notifications, 1 nouvelle'))
  })

  it('ouvre les messages depuis une autre page et distingue leur compteur des notifications', async () => {
    const { page, routeur } = await ouvrir('/missions')
    const acces = page.get('[data-test="lien-messages"]')
    expect(acces.attributes('href')).toBe('/messages')
    expect(acces.attributes('aria-label')).toBe('Messages, 1 non lu')
    await acces.trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe('/messages'))
    await routeur.push(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(acces.attributes('aria-label')).toBe('Messages'))
    expect(page.get('[data-test="lien-activite"]').attributes('aria-label')).toBe('Notifications, 1 nouvelle')
  })

  it('recherche une conversation et filtre les non lus', async () => {
    const { page } = await ouvrir('/messages')
    await vi.waitFor(() => expect(page.findAll('[data-test="conversations"] li')).toHaveLength(2))
    await page.get('[data-test="rechercher-conversation"]').setValue('AMINA')
    expect(page.findAll('[data-test="conversations"] li')).toHaveLength(1)
    expect(page.get('[data-test="conversations"]').text()).toContain('Amina Bello')
    await page.get('[data-test="rechercher-conversation"]').setValue('personne inconnue')
    expect(page.get('[data-test="messages-vide"]').text()).toContain('Aucune personne')
    await page.get('[data-test="rechercher-conversation"]').setValue('')
    await page.get('[data-test="filtre-non-lus"]').trigger('click')
    expect(page.findAll('[data-test="conversations"] li')).toHaveLength(1)
    expect(page.get('[data-test="conversations"]').text()).toContain('Kofi Yao')
  })

  it('commence un échange depuis les contacts et garde la liste en lisant et en répondant', async () => {
    const { page, routeur } = await ouvrir('/messages')
    await page.get('[data-test="nouveau-message"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="contacts-messagerie"]').text()).toContain('Amina Bello'))
    await page.get(`[data-test="contacts-messagerie"] a[href="/messages/${AMINA}"]`).trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="form-message"]').exists()).toBe(true))
    expect(routeur.currentRoute.value.path).toBe(`/messages/${AMINA}`)
    expect(page.find('[data-test="contacts-messagerie"]').exists()).toBe(true)
    await page.get('[data-test="form-message"] textarea').setValue('On avance ensemble !')
    await page.get('[data-test="form-message"]').trigger('submit')
    await vi.waitFor(() => expect(page.get('[data-test="fil"]').text()).toContain('On avance ensemble !'))
    await page.get('.filtres button').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="conversations"] li')[0]!.text()).toContain('On avance ensemble !'))
  })

  it('liste les notifications avec une phrase claire, puis les marque toutes comme lues', async () => {
    const { page } = await ouvrir('/notifications')
    await vi.waitFor(() => expect(page.findAll('[data-test="notifications"] li')).toHaveLength(3))
    const texte = page.find('[data-test="notifications"]').text()
    expect(texte).toContain("Kofi Yao t'invite à rejoindre « Cercle d’Abidjan ».")
    expect(texte).toContain("Ibrahima Fall veut t'ajouter à ses contacts.")
    expect(page.findAll('.ligne--nouvelle')).toHaveLength(2)
    await vi.waitFor(() => expect(page.find('[data-test="lien-activite"]').attributes('aria-label')).toBe('Notifications'))
    expect(page.find('[data-test="onglet-contacts"]').text()).toContain('1')
  })

  it("accepte une demande de contact : la personne passe dans mes contacts, avec « Écrire »", async () => {
    const { page } = await ouvrir('/contacts')
    await vi.waitFor(() => expect(page.find('[data-test="contacts-recues"]').text()).toContain('Ibrahima Fall'))
    expect(page.findAll('[data-test="contacts"] > li')).toHaveLength(1)
    await page.find('[data-test="accepter"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="contacts"] > li')).toHaveLength(2))
    expect(page.find('[data-test="contacts-recues"]').exists()).toBe(false)
    expect(page.find('[data-test="contacts"]').text()).toContain('Écrire')
  })

  it('demande le contact depuis une conversation, puis affiche « Demande envoyée »', async () => {
    const { page } = await ouvrir(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(page.find('[data-test="ajouter-contact"]').exists()).toBe(true))
    await page.find('[data-test="ajouter-contact"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="lien-contact"]').text()).toBe('Demande envoyée'))
    const amina = await ouvrir(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(amina.page.find('[data-test="lien-contact"]').text()).toBe('Dans tes contacts'))
  })

  it('liste les conversations, la plus récente en haut, avec l’aperçu de l’invitation', async () => {
    const { page } = await ouvrir('/messages')
    await vi.waitFor(() => expect(page.findAll('[data-test="conversations"] li')).toHaveLength(2))
    const [premiere, seconde] = page.findAll('[data-test="conversations"] li')
    expect(premiere!.text()).toContain('Kofi Yao')
    expect(premiere!.text()).toContain("T'invite à rejoindre « Cercle d’Abidjan »")
    expect(seconde!.text()).toContain('Avec plaisir, je regarde ton idée ce soir.')
  })

  it("rejoint un cercle depuis l'invitation reçue, et arrive sur sa page", async () => {
    const { page, routeur } = await ouvrir(`/messages/${KOFI}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre-invitation"]').exists()).toBe(true))
    expect(page.find('[data-test="invitation"]').text()).toContain('Cercle d’Abidjan')
    await page.find('[data-test="rejoindre-invitation"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.path).toBe(`/cercles/${ID_ABIDJAN}`))
  })

  it('envoie une réponse et l’ajoute en bas de la conversation', async () => {
    const { page } = await ouvrir(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(page.find('[data-test="form-message"]').exists()).toBe(true))
    await page.find('[data-test="form-message"] textarea').setValue('Merci Amina, à ce soir !')
    await page.find('[data-test="form-message"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="fil"] li').at(-1)!.text()).toContain('Merci Amina, à ce soir !'))
    expect(page.findAll('[data-test="fil"] li').at(-1)!.classes()).toContain('bulle--moi')
  })

  it('Entrée envoie le message, Maj + Entrée va à la ligne', async () => {
    const { page } = await ouvrir(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(page.find('[data-test="form-message"]').exists()).toBe(true))
    const zone = page.get('[data-test="form-message"] textarea')
    await zone.setValue('Ligne une')
    await zone.trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(page.findAll('[data-test="fil"] li').at(-1)!.text()).not.toContain('Ligne une')
    await zone.trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.findAll('[data-test="fil"] li').at(-1)!.text()).toContain('Ligne une'))
    // Le fil est groupé par jour, et mon message dit qu'il est envoyé.
    expect(page.find('.jour').text()).not.toBe('')
    expect(page.findAll('[data-test="fil"] li').at(-1)!.find('.bulle__lu').exists()).toBe(true)
  })

  it("appelle la personne dans Jamii : l'appel sonne dans la fenêtre d'appel, et raccrocher l'annule", async () => {
    installerFauxWebrtc()
    try {
      const { page } = await ouvrir(`/messages/${AMINA}`)
      await vi.waitFor(() => expect(page.find('[data-test="appeler-video"]').exists()).toBe(true))
      await page.get('[data-test="appeler-video"]').trigger('click')
      await vi.waitFor(() => expect(document.querySelector('[data-test="statut-appel"]')?.textContent).toContain('Ça sonne chez Amina'))
      expect(page.get('[data-test="appeler"]').attributes('disabled')).toBeDefined()
      ;(document.querySelector('[data-test="raccrocher"]') as HTMLButtonElement).click()
      await vi.waitFor(() => expect(document.querySelector('[data-test="statut-appel"]')?.textContent).toContain('Appel annulé'))
    } finally {
      reinitialiserAppel()
      vi.unstubAllGlobals()
    }
  })

  it("explique un refus de l'API plutôt que d'échouer en silence", async () => {
    serveur.use(http.post('/api/conversations/:id/messages', () => HttpResponse.json({ code: 'trop_de_requetes' }, { status: 429 })))
    const { page } = await ouvrir(`/messages/${AMINA}`)
    await vi.waitFor(() => expect(page.find('[data-test="form-message"]').exists()).toBe(true))
    await page.find('[data-test="form-message"] textarea').setValue('Bonjour')
    await page.find('[data-test="form-message"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="form-message"] [role="alert"]').text()).toBe("Le message n'est pas parti. Réessaie."))
  })

  it("dit l'heure pour un message du jour, et la date sinon", () => {
    const maintenant = new Date('2026-11-13T15:00:00')
    expect(quand('2026-11-13T09:05:00', 'fr', maintenant)).toBe('09:05')
    expect(quand('2026-11-10T09:05:00', 'fr', maintenant)).toBe('10 nov.')
  })
})
