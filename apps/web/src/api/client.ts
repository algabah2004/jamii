import createClient, { type Middleware } from 'openapi-fetch'
import type { paths as Socle } from './socle'
import type { paths as Projets } from './projets'

// Les types viennent des contrats (`npm run contrats`) : le front ne peut pas diverger de l'API.

/** Le serveur pose le cookie XSRF-TOKEN ; on le renvoie sur toute requête qui modifie (docs/api-conventions.md). */
export function jetonCsrf(cookies: string = document.cookie): string | undefined {
  const ligne = cookies.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return ligne ? decodeURIComponent(ligne.slice('XSRF-TOKEN='.length)) : undefined
}

const csrf: Middleware = {
  onRequest({ request }) {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method)) {
      const jeton = jetonCsrf()
      if (jeton) request.headers.set('X-XSRF-TOKEN', jeton)
    }
    return request
  },
}

// Par défaut, la même origine que la page (proxy Vite en local, même domaine en production).
const options = {
  baseUrl: import.meta.env.VITE_API_URL ?? globalThis.location?.origin ?? '',
  credentials: 'include' as const,
  // Résolu à chaque appel (et non à la création), pour pouvoir le simuler dans les tests.
  fetch: (requete: Request) => globalThis.fetch(requete),
}

export const api = createClient<Socle & Projets>(options)
api.use(csrf)

/** Adresse qui démarre la connexion GitHub, puis revient sur `retour` (chemin relatif uniquement). */
export function adresseConnexionGithub(retour = '/profil'): string {
  return `${options.baseUrl}/api/auth/github?retour=${encodeURIComponent(retour)}`
}
