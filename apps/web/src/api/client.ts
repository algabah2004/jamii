import createClient, { type Middleware } from 'openapi-fetch'
import type { paths as CheminsSocle } from './generated/socle'
import type { paths as CheminsSystalink } from './generated/systalink'
import type { paths as CheminsCommunautes } from './generated/communautes'
import type { paths as CheminsVitrine } from './generated/vitrine'
import type { paths as CheminsProfils } from './generated/profils'

/** Un chemin par contrat : on ajoute ici chaque nouveau contrat généré. */
type paths = CheminsSocle & CheminsSystalink & CheminsVitrine & CheminsCommunautes & CheminsProfils

const METHODES_PROTEGEES = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

/** Lit le jeton CSRF posé par l'API (Spring Security, cookie XSRF-TOKEN). */
export function lireJetonCsrf(cookies: string = document.cookie): string | null {
  const morceau = cookies.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return morceau ? decodeURIComponent(morceau.slice('XSRF-TOKEN='.length)) : null
}

/** Ajoute l'en-tête X-XSRF-TOKEN sur les requêtes qui modifient des données. */
export const csrf: Middleware = {
  onRequest({ request }) {
    if (!METHODES_PROTEGEES.has(request.method)) return
    const jeton = lireJetonCsrf()
    if (jeton) request.headers.set('X-XSRF-TOKEN', jeton)
    return request
  },
}

// Même origine que l'API : le cookie de session HttpOnly suit tout seul, sans CORS.
// `fetch` est lu à chaque appel (et non capturé à l'import) pour que MSW puisse l'intercepter.
export const api = createClient<paths>({
  baseUrl: window.location.origin,
  credentials: 'same-origin',
  fetch: (requete) => globalThis.fetch(requete),
})
api.use(csrf)

/** Adresse de connexion GitHub. `retour` doit être un chemin relatif (contrat : CheminRetour). */
export function adresseConnexionGithub(retour = '/'): string {
  const sur = retour.startsWith('/') && !retour.startsWith('//') ? retour : '/'
  return `/api/auth/github?retour=${encodeURIComponent(sur)}`
}
