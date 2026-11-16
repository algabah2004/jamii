import { encode } from 'uqr'
import { competencesPhares, urlDrapeau, type Profil } from './profils'
import { nomPays } from '../systalink/calculs'

/**
 * La carte de profil à partager (#159, #302) : une image 1200 × 630, le format des aperçus LinkedIn, WhatsApp et X.
 * Dessinée dans un canvas, sans bibliothèque : l'anneau de preuves autour de la photo, le nom, le titre, le pays,
 * les preuves en chiffres et le QR code du profil. Elle est montrée en petit sur la page : seule l'image est grande.
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
  preuveClaire: '#FFB59E',
  actionClaire: '#8FD3F5',
}

/** Les quatre preuves, dans l'ordre de l'anneau : la couleur d'un arc est celle du chiffre qui lui correspond. */
const PREUVES = [
  { type: 'projet_en_ligne', couleur: C.preuve },
  { type: 'tache_livree_a_temps', couleur: C.action },
  { type: 'reponse_acceptee', couleur: C.preuveClaire },
  { type: 'avis_coequipier', couleur: C.actionClaire },
] as const
const TITRE = '"Familjen Grotesk", "Noto Sans", system-ui, sans-serif'
const TEXTE = '"Noto Sans", system-ui, sans-serif'

export interface TextesCarte {
  langue: string
  competences: string
  /** « projets en ligne vérifiés », « réponses acceptées »… : le libellé d'une preuve, accordé au nombre. */
  preuve: (type: string, n: number) => string
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

/** Coupe le texte en lignes de la largeur donnée, `max` au plus ; la dernière finit par « … » s'il reste du texte. */
function lignes(ctx: CanvasRenderingContext2D, texte: string, largeur: number, max: number): string[] {
  const mots = texte.split(/\s+/).filter(Boolean)
  const resultat: string[] = []
  let ligne = ''
  for (let i = 0; i < mots.length; i++) {
    const essai = ligne ? `${ligne} ${mots[i]}` : mots[i]!
    if (ctx.measureText(essai).width <= largeur || !ligne) {
      ligne = essai
      continue
    }
    if (resultat.length === max - 1) {
      return [...resultat, ajuster(ctx, [ligne, ...mots.slice(i)].join(' '), largeur)]
    }
    resultat.push(ligne)
    ligne = mots[i]!
  }
  if (ligne) resultat.push(ajuster(ctx, ligne, largeur))
  return resultat
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
  lueur.addColorStop(0, 'rgba(2, 151, 222, 0.20)')
  lueur.addColorStop(1, 'rgba(2, 151, 222, 0)')
  ctx.fillStyle = lueur
  ctx.fillRect(0, 0, LARGEUR, HAUTEUR)
  ctx.textBaseline = 'alphabetic'

  // L'anneau de preuves : un arc par type, plein quand la personne en a ; le fond en pointillé dit « pas encore ».
  const cx = 186
  const cy = 196
  const rayon = 112
  ctx.save()
  ctx.strokeStyle = C.discret
  ctx.globalAlpha = 0.5
  ctx.lineWidth = 3
  ctx.lineCap = 'round'
  ctx.setLineDash([1, 12])
  ctx.beginPath()
  ctx.arc(cx, cy, rayon, 0, Math.PI * 2)
  ctx.stroke()
  ctx.restore()
  PREUVES.forEach(({ type, couleur }, i) => {
    if (!(profil.fiabilite.find((f) => f.type === type)?.nombre ?? 0)) return
    const debut = ((-84 + i * 90) * Math.PI) / 180
    ctx.strokeStyle = couleur
    ctx.lineWidth = 10
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.arc(cx, cy, rayon, debut, debut + (79 * Math.PI) / 180)
    ctx.stroke()
  })

  // Photo ronde, ou les initiales.
  const at = 184
  ctx.save()
  ctx.beginPath()
  ctx.arc(cx, cy, at / 2, 0, Math.PI * 2)
  ctx.clip()
  if (avatar) {
    ctx.drawImage(avatar, cx - at / 2, cy - at / 2, at, at)
  } else {
    ctx.fillStyle = C.surface
    ctx.fillRect(cx - at / 2, cy - at / 2, at, at)
    ctx.fillStyle = C.actionClaire
    ctx.font = `700 68px ${TITRE}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(initiales(profil.nom), cx, cy + 4)
    ctx.textAlign = 'start'
    ctx.textBaseline = 'alphabetic'
  }
  ctx.restore()

  // Identité : le nom en grand, avec le point orange de la marque, puis le titre et le lieu.
  const tx = 360
  const largeurTexte = 560
  ctx.fillStyle = C.texte
  ctx.font = `700 64px ${TITRE}`
  const nom = ajuster(ctx, profil.nom, largeurTexte - 24)
  ctx.fillText(nom, tx, 190)
  ctx.fillStyle = C.preuve
  ctx.fillText('.', tx + ctx.measureText(nom).width, 190)
  let y = 240
  if (profil.titre) {
    ctx.fillStyle = C.texte
    ctx.font = `500 26px ${TITRE}`
    for (const ligne of lignes(ctx, profil.titre, largeurTexte, 2)) {
      ctx.fillText(ligne, tx, y)
      y += 34
    }
    y += 14
  }
  const lieu = [profil.ville, profil.pays && nomPays(profil.pays, textes.langue)].filter(Boolean).join(', ')
  if (lieu) {
    let lx = tx
    if (drapeau) {
      ctx.save()
      arrondi(ctx, lx, y - 22, 36, 27, 4)
      ctx.clip()
      ctx.drawImage(drapeau, lx, y - 22, 36, 27)
      ctx.restore()
      lx += 50
    }
    ctx.fillStyle = C.discret
    ctx.font = `600 24px ${TEXTE}`
    ctx.fillText(ajuster(ctx, lieu, largeurTexte - (lx - tx)), lx, y)
  }

  // Les preuves en chiffres. Sans preuve, les compétences mises en avant prennent la place : jamais de case vide.
  const preuves = PREUVES.map((p) => ({ ...p, nombre: profil.fiabilite.find((f) => f.type === p.type)?.nombre ?? 0 })).filter((p) => p.nombre > 0)
  ctx.strokeStyle = C.ligne
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(64, 392)
  ctx.lineTo(LARGEUR - 64, 392)
  ctx.stroke()
  if (preuves.length) {
    const colonne = (LARGEUR - 128) / 4
    preuves.forEach((p, i) => {
      const x = 64 + i * colonne
      ctx.fillStyle = p.couleur
      ctx.font = `700 76px ${TITRE}`
      ctx.fillText(String(p.nombre), x, 480)
      ctx.fillStyle = C.discret
      ctx.font = `400 20px ${TEXTE}`
      lignes(ctx, textes.preuve(p.type, p.nombre), colonne - 32, 2).forEach((ligne, k) => ctx.fillText(ligne, x, 516 + k * 26))
    })
  } else {
    const phares = competencesPhares(profil)
    ctx.fillStyle = C.discret
    ctx.font = `600 20px ${TEXTE}`
    ctx.fillText(textes.competences.toUpperCase(), 64, 432)
    let px = 64
    ctx.font = `600 26px ${TEXTE}`
    for (const c of phares) {
      const l = Math.min(ctx.measureText(c).width + 44, 300)
      if (px + l > LARGEUR - 64) break
      ctx.fillStyle = C.surface
      arrondi(ctx, px, 452, l, 52, 26)
      ctx.fill()
      ctx.strokeStyle = C.ligne
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.fillStyle = C.texte
      ctx.fillText(ajuster(ctx, c, l - 44), px + 22, 487)
      px += l + 14
    }
  }

  // Pied : la marque, puis le lien du profil.
  ctx.fillStyle = C.texte
  ctx.font = `700 32px ${TITRE}`
  ctx.fillText('Jamii', 64, 588)
  ctx.fillStyle = C.preuve
  ctx.fillText('.', 64 + ctx.measureText('Jamii').width, 588)
  ctx.fillStyle = C.discret
  ctx.font = `400 22px ${TEXTE}`
  ctx.textAlign = 'right'
  ctx.fillText(ajuster(ctx, lien.replace(/^https?:\/\//, ''), 560), LARGEUR - 64, 588)
  ctx.textAlign = 'start'

  // QR code vers le profil public, en haut à droite.
  const qx = LARGEUR - 64 - 188
  const qy = 64
  dessinerQr(ctx, lien, qx, qy, 188)
  ctx.fillStyle = C.discret
  ctx.font = `600 20px ${TEXTE}`
  ctx.textAlign = 'center'
  ctx.fillText(textes.scanner, qx + 94, qy + 188 + 32)
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
