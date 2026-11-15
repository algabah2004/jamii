import { flushPromises, type VueWrapper } from '@vue/test-utils'

/** Parcourt les étapes visibles, comme une personne ; aucun champ masqué n’est rempli. */
export async function continuer(page: VueWrapper, nombre = 1) {
  for (let i = 0; i < nombre; i++) {
    const bouton = page.get('[data-test="assistant-suivant"]')
    if (bouton.attributes('disabled') !== undefined) throw new Error('Une étape obligatoire est incomplète')
    await page.get('form').trigger('submit')
    await flushPromises()
  }
}

export async function jusquApercu(page: VueWrapper) {
  for (let i = 0; i < 12 && page.find('[data-test="assistant-suivant"]').exists(); i++) await continuer(page)
}

export async function revenir(page: VueWrapper, etape: number) {
  await page.findAll('.etapes button')[etape]!.trigger('click')
  await flushPromises()
}
