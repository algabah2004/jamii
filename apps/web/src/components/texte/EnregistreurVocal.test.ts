import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import EnregistreurVocal from './EnregistreurVocal.vue'

enableAutoUnmount(afterEach)

// jsdom ne sait pas envoyer un FormData par fetch (« Premature close ») : l'envoi est simulé ici, comme dans
// cercles.test.ts. Le parcours réel (`vocal`, sorte `audio`) est vérifié par l'API (FichiersTest).
const envois = vi.hoisted(() => [] as { nom: string; destinataire?: string; vocal: boolean }[])
vi.mock('./fichiers', async (original) => {
  const vrai = await original<typeof import('./fichiers')>()
  return {
    ...vrai,
    envoyerFichier: async (f: File, destinataire?: string, _cercle?: string, vocal = false) => {
      envois.push({ nom: f.name, destinataire, vocal })
      const id = crypto.randomUUID()
      const piece = { id, nom: f.name, type: f.type, sorte: vocal ? 'audio' : 'document', taille: f.size, url: `/api/fichiers/${id}` } as const
      ;(await import('../../mocks/fichiers')).enregistrerPieceFactice(piece, destinataire)
      return { ok: true, piece }
    },
  }
})

/** Un faux MediaRecorder (jsdom n'en a pas) : un morceau de son à chaque arrêt. */
class FauxEnregistreur {
  static dernier: FauxEnregistreur | null = null
  static isTypeSupported = (f: string) => f === 'audio/webm;codecs=opus'
  state: 'inactive' | 'recording' = 'inactive'
  mimeType: string
  ondataavailable: ((e: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  options: { mimeType?: string; audioBitsPerSecond?: number }
  constructor(_flux: unknown, options: { mimeType?: string; audioBitsPerSecond?: number }) {
    this.options = options
    this.mimeType = options.mimeType ?? ''
    FauxEnregistreur.dernier = this
  }
  start() { this.state = 'recording' }
  stop() {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['son'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

let piste: { stop: ReturnType<typeof vi.fn> }
function installer({ micro = true } = {}) {
  piste = { stop: vi.fn() }
  vi.stubGlobal('MediaRecorder', FauxEnregistreur)
  const getUserMedia = vi.fn(async () => {
    if (!micro) throw new DOMException('refusé', 'NotAllowedError')
    return { getTracks: () => [piste] }
  })
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true })
}

const monter = () => mount(EnregistreurVocal, { global: { plugins: [i18n] } })

beforeEach(() => {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('le message vocal (#157)', () => {
  it('enregistre en Opus léger, montre la durée, puis envoie un fichier et libère le micro', async () => {
    installer()
    const v = monter()
    await v.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(v.find('[data-test="vocal-en-cours"]').exists()).toBe(true))
    expect(FauxEnregistreur.dernier!.options).toEqual({ mimeType: 'audio/webm;codecs=opus', audioBitsPerSecond: 32_000 })
    vi.advanceTimersByTime(5_000)
    await v.vm.$nextTick()
    expect(v.text()).toContain('Enregistrement… 0:05')

    await v.get('[data-test="envoyer-vocal"]').trigger('click')
    const [[fichier]] = v.emitted('envoyer') as [[File]]
    expect(fichier.name).toBe('message-vocal.webm')
    expect(fichier.type).toBe('audio/webm')
    expect(piste.stop).toHaveBeenCalled()
    expect(v.find('[data-test="enregistrer-vocal"]').exists()).toBe(true)
  })

  it("n'envoie rien si on annule, ni un message de moins d'une seconde", async () => {
    installer()
    const v = monter()
    await v.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(v.find('[data-test="annuler-vocal"]').exists()).toBe(true))
    vi.advanceTimersByTime(3_000)
    await v.get('[data-test="annuler-vocal"]').trigger('click')
    expect(v.emitted('envoyer')).toBeUndefined()
    expect(piste.stop).toHaveBeenCalled()

    await v.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(v.find('[data-test="envoyer-vocal"]').exists()).toBe(true))
    await v.get('[data-test="envoyer-vocal"]').trigger('click')
    expect(v.emitted('envoyer')).toBeUndefined()
    expect(v.emitted('erreur')).toEqual([['trop_court']])
  })

  it("s'arrête et envoie tout seul au bout de 3 minutes", async () => {
    installer()
    const v = monter()
    await v.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(v.find('[data-test="vocal-en-cours"]').exists()).toBe(true))
    vi.advanceTimersByTime(180_000)
    expect(v.emitted('envoyer')).toHaveLength(1)
  })

  it('prévient quand le micro est refusé', async () => {
    installer({ micro: false })
    const v = monter()
    await v.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(v.emitted('erreur')).toEqual([['micro_refuse']]))
  })

  it("dans une discussion : le vocal part comme un message, et se lit avec un lecteur", async () => {
    vi.useRealTimers()
    installer()
    ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
    await chargerSession()
    const routeur = creerRouteur(createMemoryHistory())
    await routeur.push('/messages/c1a00000-0000-4000-8000-000000000002')
    const page = mount(App, { global: { plugins: [i18n, routeur] } })
    await vi.waitFor(() => expect(page.find('[data-test="enregistrer-vocal"]').exists()).toBe(true))
    await page.get('[data-test="enregistrer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="envoyer-vocal"]').exists()).toBe(true))
    // Plus d'une seconde d'enregistrement.
    const maintenant = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(maintenant + 4_000)
    await page.get('[data-test="envoyer-vocal"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="message-vocal"]').exists()).toBe(true))
    expect(page.get('[data-test="message-vocal"] audio').attributes('src')).toMatch(/^\/api\/fichiers\//)
    expect(envois).toEqual([{ nom: 'message-vocal.webm', destinataire: 'c1a00000-0000-4000-8000-000000000002', vocal: true }])
  })
})
