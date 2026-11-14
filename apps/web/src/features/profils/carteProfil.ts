import { encode } from 'uqr'
import { competencesPhares, nomLangue, urlDrapeau, type Profil } from './profils'
import { nomPays } from '../systalink/calculs'

/**
 * La carte de profil à partager (#159) : une image 1200 × 630, le format des aperçus LinkedIn, WhatsApp et X.
 * Dessinée dans un canvas, sans bibliothèque : photo, nom, titre, pays, 3 compétences, chiffres, QR code du profil.
 */
export const LARGEUR = 1200
export const HAUTEUR = 630

// Les couleurs de Jamii (design-system/tokens.css, thème sombre) : la carte a la même allure partout.
const C = {
  fond: '#071017',
  surface: '#0F1C24',
  ligne: '#1E3340',
  texte: '#EEF4F7',
  discret: '#9DB0BC',
  action: '#0297DE',
  preuve: '#FC734D',
}
const TITRE = '"Familjen Grotesk", "Noto Sans", system-ui, sans-serif'
const TEXTE = '"Noto Sans", system-ui, sans-serif'

export interface TextesCarte {
  langue: string
  competences: string
  projetsVerifies: (n: number) => string
  realisations: (n: number) => string
  scanner: string
}

function chargerImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const image = new Image()
    // Les avatars GitHub autorisent le CORS : sans cet attribut, le canvas serait « sali » et l'export refusé.
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = src
  })
}

function arrondi(ctx: CanvasRenderingContext2D, x: number, y: number, l: number, h: number, r: number) {
  ctx.beginPath()
  ctx.roundRect(x, y, l, h, r)
}

/** Coupe le texte avec « … » s'il dépasse la largeur donnée. */
function ajuster(ctx: CanvasRenderingContext2D, texte: string, largeur: number): string {
  if (ctx.measureText(texte).width <= largeur) return texte
  let t = texte
  while (t.length > 1 && ctx.measureText(`${t}…`).width > largeur) t = t.slice(0, -1)
  return `${t.trimEnd()}…`
}

function initiales(nom: string): string {
  return nom.split(/\s+/).filter(Boolean).slice(0, 2).map((m) => m[0]!.toUpperCase()).join('')
}

function dessinerQr(ctx: CanvasRenderingContext2D, lien: string, x: number, y: number, taille: number) {
  const qr = encode(lien, { ecc: 'M', border: 0 })
  const marge = 18
  ctx.fillStyle = '#FFFFFF'
  arrondi(ctx, x, y, taille, taille, 20)
  ctx.fill()
  const module = (taille - 2 * marge) / qr.size
  ctx.fillStyle = '#02070A'
  qr.data.forEach((ligne, i) =>
    ligne.forEach((plein, j) => {
      // +0.5 : pas de fines lignes blanches entre deux modules voisins.
      if (plein) ctx.fillRect(x + marge + j * module, y + marge + i * module, module + 0.5, module + 0.5)
    }),
  )
}

export async function dessinerCarte(profil: Profil, lien: string, textes: TextesCarte): Promise<HTMLCanvasElement> {
  const canvas = document.createElement('canvas')
  canvas.width = LARGEUR
  canvas.height = HAUTEUR
  const ctx = canvas.getContext('2d')!
  await Promise.allSettled([
    document.fonts?.load(`700 64px ${TITRE}`),
    document.fonts?.load(`400 28px ${TEXTE}`),
    document.fonts?.load(`600 24px ${TEXTE}`),
  ])
  const [avatar, drapeau] = await Promise.all([
    profil.avatarUrl ? chargerImage(profil.avatarUrl) : Promise.resolve(null),
    urlDrapeau(profil.pays) ? chargerImage(urlDrapeau(profil.pays)!) : Promise.resolve(null),
  ])

  // Fond : une lueur bleue discrète en haut à gauche, comme sur le site.
  ctx.fillStyle = C.fond
  ctx.fillRect(0, 0, LARGEUR, HAUTEUR)
  const lueur = ctx.createRadialGradient(160, 120, 0, 160, 120, 700)
  lueur.addColorStop(0, 'rgba(2, 151, 222, 0.22)')
  lueur.addColorStop(1, 'rgba(2, 151, 222, 0)')
  ctx.fillStyle = lueur
  ctx.fillRect(0, 0, LARGEUR, HAUTEUR)
  ctx.fillStyle = C.action
  ctx.fillRect(0, HAUTEUR - 8, LARGEUR, 8)

  // Marque
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = C.texte
  ctx.font = `700 34px ${TITRE}`
  ctx.fillText('jamii', 72, 92)
  ctx.fillStyle = C.action
  ctx.beginPath()
  ctx.arc(72 + ctx.measureText('jamii').width + 10, 86, 6, 0, Math.PI * 2)
  ctx.fill()

  // Photo ronde, avec un anneau de la couleur d'action.
  const ax = 72
  const ay = 150
  const at = 176
  ctx.save()
  ctx.beginPath()
  ctx.arc(ax + at / 2, ay + at / 2, at / 2, 0, Math.PI * 2)
  ctx.clip()
  if (avatar) {
    ctx.drawImage(avatar, ax, ay, at, at)
  } else {
    ctx.fillStyle = C.surface
    ctx.fillRect(ax, ay, at, at)
    ctx.fillStyle = C.texte
    ctx.font = `700 64px ${TITRE}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(initiales(profil.nom), ax + at / 2, ay + at / 2 + 4)
    ctx.textAlign = 'start'
    ctx.textBaseline = 'alphabetic'
  }
  ctx.restore()
  ctx.strokeStyle = C.action
  ctx.lineWidth = 5
  ctx.beginPath()
  ctx.arc(ax + at / 2, ay + at / 2, at / 2 + 6, 0, Math.PI * 2)
  ctx.stroke()

  // Identité
  const tx = 300
  const largeurTexte = 520
  ctx.fillStyle = C.texte
  ctx.font = `700 60px ${TITRE}`
  ctx.fillText(ajuster(ctx, profil.nom, largeurTexte), tx, 212)
  let y = 260
  if (profil.titre) {
    ctx.fillStyle = C.discret
    ctx.font = `400 30px ${TEXTE}`
    ctx.fillText(ajuster(ctx, profil.titre, largeurTexte), tx, y)
    y += 50
  }
  const lieu = [profil.ville, profil.pays && nomPays(profil.pays, textes.langue)].filter(Boolean).join(', ')
  if (lieu) {
    let lx = tx
    if (drapeau) {
      ctx.save()
      arrondi(ctx, lx, y - 24, 40, 30, 4)
      ctx.clip()
      ctx.drawImage(drapeau, lx, y - 24, 40, 30)
      ctx.restore()
      lx += 54
    }
    ctx.fillStyle = C.texte
    ctx.font = `600 26px ${TEXTE}`
    ctx.fillText(ajuster(ctx, lieu, largeurTexte - (lx - tx)), lx, y)
  }

  // Compétences mises en avant, en pastilles.
  const phares = competencesPhares(profil)
  let cy = 420
  if (phares.length) {
    ctx.fillStyle = C.discret
    ctx.font = `600 20px ${TEXTE}`
    ctx.fillText(textes.competences.toUpperCase(), 72, cy)
    cy += 22
    let px = 72
    ctx.font = `600 26px ${TEXTE}`
    for (const c of phares) {
      const l = Math.min(ctx.measureText(c).width + 44, 300)
      if (px + l > 820) break
      ctx.fillStyle = C.surface
      arrondi(ctx, px, cy, l, 52, 26)
      ctx.fill()
      ctx.strokeStyle = C.ligne
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.fillStyle = C.texte
      ctx.fillText(ajuster(ctx, c, l - 44), px + 22, cy + 35)
      px += l + 14
    }
  }

  // Ce qui se vérifie : projets en ligne sur Jamii, puis réalisations déclarées et langues.
  const verifies = profil.projets.filter((p) => p.enLigne).length
  const faits = [
    verifies ? textes.projetsVerifies(verifies) : null,
    profil.realisations.length ? textes.realisations(profil.realisations.length) : null,
    profil.langues.length ? profil.langues.slice(0, 3).map((l) => nomLangue(l, textes.langue)).join(' · ') : null,
  ].filter(Boolean) as string[]
  if (faits.length) {
    ctx.font = `400 24px ${TEXTE}`
    ctx.fillStyle = C.discret
    ctx.fillText(ajuster(ctx, faits.join('   •   '), 760), 72, 568)
    if (verifies) {
      ctx.fillStyle = C.preuve
      ctx.beginPath()
      ctx.arc(60, 560, 5, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  // QR code vers le profil public.
  const qx = 880
  const qy = 150
  const qt = 248
  dessinerQr(ctx, lien, qx, qy, qt)
  ctx.fillStyle = C.texte
  ctx.font = `600 22px ${TEXTE}`
  ctx.textAlign = 'center'
  ctx.fillText(textes.scanner, qx + qt / 2, qy + qt + 42)
  ctx.fillStyle = C.discret
  ctx.font = `400 18px ${TEXTE}`
  ctx.fillText(ajuster(ctx, lien.replace(/^https?:\/\//, ''), qt + 40), qx + qt / 2, qy + qt + 70)
  ctx.textAlign = 'start'

  return canvas
}

/** L'image en PNG. `null` si le navigateur refuse l'export (photo sans CORS) : on redessine alors sans la photo. */
export async function carteEnPng(profil: Profil, lien: string, textes: TextesCarte): Promise<Blob> {
  const versBlob = (c: HTMLCanvasElement) =>
    new Promise<Blob | null>((resolve) => {
      try {
        c.toBlob(resolve, 'image/png')
      } catch {
        resolve(null)
      }
    })
  const blob = await versBlob(await dessinerCarte(profil, lien, textes))
  if (blob) return blob
  const sansPhoto = await versBlob(await dessinerCarte({ ...profil, avatarUrl: null }, lien, textes))
  if (!sansPhoto) throw new Error('export impossible')
  return sansPhoto
}
