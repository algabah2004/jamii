/*
 * Mesure de la responsivité (voir docs/responsive.md, §4). Aucun paquet : à lancer dans la console du mode factice
 * (`npm run dev:factice`), sur n'importe quelle page :
 *
 *   const { auditer } = await import('/scripts/audit-responsive.js')
 *   await auditer(['/', '/cercles'], [320, 360, 768])   // routes et largeurs facultatives
 *   window.__audit                                      // le résultat, par route puis par largeur
 *
 * Pour chaque route et chaque largeur, le script ouvre la page dans une iframe et relève :
 * - `rognes` : les éléments qui sortent de l'écran sans pouvoir défiler (le contenu est caché : un défaut) ;
 * - `cibles` : les liens, boutons et champs de moins de 24 × 24 px (WCAG 2.5.8) ;
 * - `champs` : sous 720 px, les champs dont le texte fait moins de 16 px (iOS zoome alors tout seul) ;
 * - `url` : la route finale, pour repérer une redirection.
 */

export const ROUTES = [
  '/', '/connexion', '/profil', '/p/a1b2',
  '/communautes', '/communautes/nouvelle', '/communautes/flutter', '/communautes/flutter/salons/nouveau',
  '/cercles', '/cercles/nouveau', '/cercles/a0c1e2c1-0000-4000-8000-00000000da4a',
  '/messages', '/messages/c1a00000-0000-4000-8000-000000000002', '/notifications', '/contacts',
  '/demandes', '/missions', '/missions/nouvelle', '/missions/a3d1c6f0-5b2e-4c8a-9f17-3e6b0d4a2c91',
  '/hackathons', '/hackathons/nouveau', '/hackathons/d7a1e2c4-3b5f-4a6d-8e90-1f2a3b4c5d6e',
  '/vitrine', '/systalink', '/introuvable',
]
export const LARGEURS = [320, 360, 390, 768, 1024, 1440]

const nom = (e) => e.tagName.toLowerCase() + (typeof e.className === 'string' && e.className.trim() ? '.' + e.className.trim().split(/\s+/).slice(0, 2).join('.') : '')

/** Le premier ancêtre qui coupe ou fait défiler en largeur : un élément dans une rangée qui défile n'est pas un défaut. */
function conteneur(e) {
  for (let p = e.parentElement; p && p !== p.ownerDocument.body; p = p.parentElement) {
    const o = getComputedStyle(p).overflowX
    if (o === 'auto' || o === 'scroll') return 'defile'
    if (o === 'hidden' || o === 'clip') return p.id === 'app' ? 'rogne' : (p.getBoundingClientRect().right <= p.ownerDocument.documentElement.clientWidth + 1 ? 'rogne' : 'suit')
  }
  return 'rogne'
}

/** Mesure le document d'une page déjà chargée. */
export function mesurer(d, largeur) {
  const vw = d.documentElement.clientWidth
  // Une scène « pleine largeur » (margin-inline: calc(50% - 50vw)) dépasse de la largeur de la barre de défilement,
  // qui n'existe pas sur téléphone : on la tolère.
  const barre = d.defaultView.innerWidth - vw
  const visibles = (e) => {
    const r = e.getBoundingClientRect()
    if (r.width === 0 || r.height === 0) return false
    const s = getComputedStyle(e)
    return s.visibility !== 'hidden' && !e.closest('[aria-hidden="true"], [inert]')
  }
  const hors = [...d.body.querySelectorAll('*')].filter((e) => {
    const r = e.getBoundingClientRect()
    return r.width > 0 && (r.right > vw + 1 + barre || r.left < -1 - barre) && getComputedStyle(e).position !== 'fixed' && !e.closest('[aria-hidden="true"]')
  })
  const rognes = hors
    .filter((e) => !hors.includes(e.parentElement) && conteneur(e) === 'rogne')
    .map((e) => `${nom(e)} [${Math.round(e.getBoundingClientRect().left)}→${Math.round(e.getBoundingClientRect().right)}]`)
  const cibles = [...d.querySelectorAll('a[href], button, input:not([type=hidden]), select, textarea, [role=button], [role=tab], [role=switch]')]
    .filter(visibles)
    .filter((e) => {
      const r = e.getBoundingClientRect()
      // Un lien dans une phrase est exempté par WCAG 2.5.8.
      if (e.tagName === 'A' && getComputedStyle(e).display === 'inline') return false
      return r.height < 24 || r.width < 24
    })
    .map((e) => `${nom(e)} ${Math.round(e.getBoundingClientRect().width)}×${Math.round(e.getBoundingClientRect().height)}`)
  const champs = largeur <= 720
    ? [...d.querySelectorAll('input:not([type=checkbox]):not([type=radio]):not([type=range]):not([type=hidden]), select, textarea, [contenteditable=true]')]
        .filter(visibles)
        .filter((e) => parseFloat(getComputedStyle(e).fontSize) < 16)
        .map((e) => `${nom(e)} ${getComputedStyle(e).fontSize}`)
    : []
  return { rognes, cibles, champs, url: d.defaultView.location.pathname }
}

export async function auditer(routes = ROUTES, largeurs = LARGEURS, attente = 2000) {
  const f = document.createElement('iframe')
  f.style.cssText = 'position:fixed;left:0;top:0;height:800px;border:0;z-index:99999;background:#fff'
  document.body.appendChild(f)
  const resultat = {}
  window.__audit = null
  window.__auditEnCours = ''
  try {
    for (const largeur of largeurs) {
      f.style.width = `${largeur}px`
      for (const route of routes) {
        window.__auditEnCours = `${route} @${largeur}`
        f.src = route
        await new Promise((r) => (f.onload = r))
        await new Promise((r) => setTimeout(r, attente))
        ;(resultat[route] ??= {})[largeur] = mesurer(f.contentDocument, largeur)
      }
    }
  } finally {
    f.remove()
  }
  window.__audit = resultat
  return resultat
}

/** Un résumé lisible : seulement ce qui est en défaut. */
export function resumer(resultat = window.__audit) {
  const lignes = []
  for (const [route, parLargeur] of Object.entries(resultat ?? {})) {
    for (const [largeur, m] of Object.entries(parLargeur)) {
      const defauts = []
      if (m.url !== route) defauts.push(`→ ${m.url}`)
      if (m.rognes.length) defauts.push(`ROGNÉ ${m.rognes.slice(0, 4).join(', ')}`)
      if (m.cibles.length) defauts.push(`cibles<24 (${m.cibles.length}) ${m.cibles.slice(0, 4).join(', ')}`)
      if (m.champs.length) defauts.push(`champs<16px (${m.champs.length}) ${m.champs.slice(0, 2).join(', ')}`)
      if (defauts.length) lignes.push(`${route} @${largeur} : ${defauts.join(' | ')}`)
    }
  }
  return lignes.join('\n') || 'aucun défaut'
}
