import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Mission, Resultat } from './missions'

/** Les codes d'erreur du suivi qui ont leur propre message ; les autres disent seulement que l'action n'a pas abouti. */
const CONNUS = ['modification_concurrente', 'etat_invalide', 'retouches_epuisees', 'annulation_non_permise', 'avis_deja_donne', 'validation', 'interdit'] as const

/**
 * Une action du suivi : un envoi à la fois, la mission à jour transmise par `fini`, et le message d'erreur à afficher.
 * L'API revérifie tout ; ici, on évite seulement le double clic.
 */
export function useAction(fini: (mission: Mission) => void) {
  const { t } = useI18n()
  const envoi = ref(false)
  const code = ref<string | null>(null)
  const message = computed(() =>
    code.value ? t(`missions.suivi.erreurs.${(CONNUS as readonly string[]).includes(code.value) ? code.value : 'autre'}`) : '',
  )

  async function agir(appel: () => Promise<Resultat<Mission>>): Promise<boolean> {
    if (envoi.value) return false
    envoi.value = true
    code.value = null
    try {
      const r = await appel()
      if (r.ok) fini(r.valeur)
      else code.value = r.code ?? 'autre'
      return r.ok
    } catch {
      code.value = 'autre'
      return false
    } finally {
      envoi.value = false
    }
  }

  return { envoi, message, agir }
}

/** « 14 novembre 2026 » dans la langue de la personne. */
export function formaterDate(iso: string, langue: string): string {
  return new Intl.DateTimeFormat(langue, { dateStyle: 'long' }).format(new Date(iso))
}
