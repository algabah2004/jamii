import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN, ID_DAKAR, ID_SAINT_LOUIS } from '../../mocks/cercles'

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  return { page, routeur }
}

const KWAME = 'd2e00000-0000-4000-8000-000000000001'

describe('les cercles qui ont besoin de moi', () => {
  it('recommande un cercle ouvert en disant pourquoi, et mène à sa page', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.find('[data-test="cercles-pour-moi"]').exists()).toBe(true))
    const carte = page.find('[data-test="cercles-pour-moi"] a')
    expect(carte.text()).toContain('Cercle d’Abidjan')
    expect(carte.text()).toContain('Il leur manque flutter, que tu connais')
    expect(carte.attributes('href')).toBe(`/cercles/${ID_ABIDJAN}`)
  })

  it("affiche quand même mes cercles si les recommandations ne répondent pas", async () => {
    serveur.use(http.get('/api/recommandations/cercles', () => HttpResponse.json({}, { status: 503 })))
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles-mes"] li')).toHaveLength(2))
    expect(page.find('[data-test="cercles-pour-moi"]').exists()).toBe(false)
    expect(page.find('[role="alert"]').exists()).toBe(false)
  })
})

describe('les profils qui manquent au cercle', () => {
  it('montre, dans mon cercle ouvert, les profils disponibles qui apportent ce qui manque, la même ville d’abord', async () => {
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find('[data-test="profils-recommandes"]').exists()).toBe(true))
    const noms = page.findAll('[data-test="profils-recommandes"] .reco strong').map((n) => n.text())
    expect(noms[0]).toBe('Ibrahima Fall')
    expect(noms).toContain('Kwame Mensah')
    // Indisponible : jamais recommandé.
    expect(noms).not.toContain('Zawadi Otieno')
    expect(page.find('[data-test="profils-recommandes"]').text()).toContain('Dans la ville du cercle')
  })

  it("invite par un message pré-rempli, puis marque l'invitation comme envoyée", async () => {
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(true))
    await page.find(`[data-test="inviter-${KWAME}"]`).trigger('click')
    const form = page.find('[data-test="form-invitation"]')
    expect((form.find('textarea').element as HTMLTextAreaElement).value).toContain('Bonjour Kwame, notre « Cercle de Saint-Louis »')
    await form.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="invitation-envoyee"]').text()).toContain('Kwame la verra dans ses messages'))
    expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(false)
  })

  it('venu d’un besoin, invite en un clic les développeurs recommandés pour ce besoin', async () => {
    const envois: { id: string; corps: { texte: string; cercleId?: string } }[] = []
    serveur.use(
      http.post('/api/conversations/:id/messages', async ({ params, request }) => {
        if (params.id === 'inconnu-0000') return HttpResponse.json({ code: 'introuvable' }, { status: 404 })
        envois.push({ id: String(params.id), corps: (await request.json()) as { texte: string; cercleId?: string } })
        return HttpResponse.json({ id: 'm1', auteurId: 'moi', texte: 'x', envoyeLe: '2026-11-14T00:00:00Z' }, { status: 201 })
      }),
    )
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    const duBesoin = [{ id: KWAME, nom: 'Kwame Mensah' }, { id: 'inconnu-0000', nom: 'Amina Diallo' }, { pas: 'valide' }]
    await routeur.push({ path: `/cercles/${ID_SAINT_LOUIS}`, state: { duBesoin } })
    const page = mount(App, { global: { plugins: [i18n, routeur] } })
    await vi.waitFor(() => expect(page.find('[data-test="inviter-du-besoin"]').exists()).toBe(true))
    expect(page.find('[data-test="du-besoin"]').text()).toContain('Recommandés pour ton besoin\u202f: Kwame et Amina.')
    // Aucun message ne part sans le clic.
    expect(envois).toHaveLength(0)

    await page.find('[data-test="inviter-du-besoin"]').trigger('click')
    // Les invitations partent l'une après l'autre : on attend la dernière (l'échec d'Amina), pas la première (#190).
    await vi.waitFor(() => expect(page.find('[data-test="du-besoin"] [role="alert"]').text()).toContain('pour Amina'))
    expect(envois).toEqual([{ id: KWAME, corps: expect.objectContaining({ cercleId: ID_SAINT_LOUIS, texte: expect.stringContaining('Bonjour Kwame') }) }])
    expect(page.find('[data-test="invites-du-besoin"]').text()).toContain('Invitation envoyée à Kwame.')
    expect(page.find('[data-test="du-besoin"] [role="alert"]').text()).toContain('pour Amina')
    // Seule Amina reste à inviter ; Kwame est marqué invité dans la liste aussi.
    expect(page.find('[data-test="inviter-du-besoin"]').text()).toBe("L'inviter au cercle")
    expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(false)
  })

  it("explique un refus de l'API (cercle complet entre-temps)", async () => {
    serveur.use(http.post('/api/conversations/:id/messages', () => HttpResponse.json({ code: 'cercle_ferme' }, { status: 409 })))
    const { page } = await ouvrir(`/cercles/${ID_SAINT_LOUIS}`)
    await vi.waitFor(() => expect(page.find(`[data-test="inviter-${KWAME}"]`).exists()).toBe(true))
    await page.find(`[data-test="inviter-${KWAME}"]`).trigger('click')
    await page.find('[data-test="form-invitation"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="form-invitation"] [role="alert"]').text()).toBe("Le cercle est complet, ou l'ordre est déjà tiré."))
  })

  it("n'apparaît ni pour qui n'est pas membre, ni dans un cercle déjà tiré", async () => {
    const abidjan = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(abidjan.page.find('[data-test="rejoindre"]').exists()).toBe(true))
    expect(abidjan.page.find('[data-test="profils-recommandes"]').exists()).toBe(false)
    const dakar = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(dakar.page.find('[data-test="tour"]').exists()).toBe(true))
    expect(dakar.page.find('[data-test="profils-recommandes"]').exists()).toBe(false)
  })
})
