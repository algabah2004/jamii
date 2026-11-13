import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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
