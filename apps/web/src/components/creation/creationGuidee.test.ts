import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { continuer, revenir } from '../../test/creationGuidee'

enableAutoUnmount(afterEach)
async function ouvrir(chemin: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(chemin)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('[data-test="assistant-etape-0"]').exists()).toBe(true))
  return page
}

describe('les créations guidées', () => {
  it('conserve le titre et le besoin d’une mission quand on revient en arrière', async () => {
    const page = await ouvrir('/missions/nouvelle')
    expect(page.get('[data-test="assistant-suivant"]').attributes('disabled')).toBeDefined()
    expect(page.find('[data-test="creer"]').exists()).toBe(false)
    await page.get('form input[type="text"]').setValue('Une application pour les bénévoles')
    await continuer(page)
    await page.get('textarea').setValue('Nous voulons organiser les permanences et prévenir les bénévoles disponibles.')
    await continuer(page)
    await revenir(page, 0)
    expect((page.get('form input[type="text"]').element as HTMLInputElement).value).toBe('Une application pour les bénévoles')
    await continuer(page)
    expect((page.get('textarea').element as HTMLTextAreaElement).value).toContain('organiser les permanences')
    expect(page.get('aside h2').text()).toBe('Une application pour les bénévoles')
  })

  it('garde le calendrier d’un hackathon et bloque les dates incohérentes à leur étape', async () => {
    const page = await ouvrir('/hackathons/nouveau')
    await page.get('form input[type="text"]').setValue('Hackathon des associations')
    await continuer(page)
    await page.get('form input[type="text"]').setValue('Les outils du quartier')
    await page.get('textarea').setValue('Construire un outil utile aux associations du quartier pendant deux jours.')
    await continuer(page, 2)
    const dates = { ouvertes: '2026-12-01T09:00', fermees: '2026-12-10T09:00', debut: '2026-12-12T09:00', fin: '2026-12-14T09:00', resultats: '2026-12-16T09:00' }
    for (const [cle, valeur] of Object.entries(dates)) await page.get(`[data-test="date-${cle}"]`).setValue(valeur)
    await continuer(page)
    await revenir(page, 3)
    expect((page.get('[data-test="date-fin"]').element as HTMLInputElement).value).toBe('2026-12-14T09:00')
    await page.get('[data-test="date-fin"]').setValue('2026-12-11T09:00')
    expect(page.get('[data-test="assistant-suivant"]').attributes('disabled')).toBeDefined()
    expect(page.find('[data-test="enregistrer"]').exists()).toBe(false)
  })
})
