import { encode } from 'uqr'
import { competencesPhares, urlDrapeau, type Profil } from './profils'
import { nomPays } from '../systalink/calculs'

/**
 * La carte de profil à partager (#159) : une image 1080 × 680, aux proportions d'une carte de visite. Assez petite
 * pour se lire d'un coup d'œil dans WhatsApp ou LinkedIn, assez nette pour un écran de téléphone.
 * Dessinée dans un canvas, sans bibliothèque : photo, nom, titre, lieu, 3 compétences, chiffres et QR code du profil.
 */
export const LARGEUR = 1080
export const HAUTEUR = 680

// Les couleurs de Jamii (design-system/tokens.css, thème sombre) : la carte a la même allure partout.
const C = {
  fond: '#02070A',
  surface: '#0F1C24',
  ligne: '#1F2E38',
  texte: '#EEF4F7',
  discret: '#9DB0BC',
  action: '#0297DE',
  preuve: '#FC734D',
  vert: '#1FA35B',
  orange: '#F2A541',
}
const TITRE = '"Familjen Grotesk", "Noto Sans", system-ui, sans-serif'
const TEXTE = '"Noto Sans", system-ui, sans-serif'

export interface TextesCarte {
  langue: string
  scanner: string
  disponibilite: string
  chiffres: { projets: (n: number) => string; realisations: (n: number) => string; competences: (n: number) => string }
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
  const marge = 14
  ctx.fillStyle = '#FFFFFF'
  arrondi(ctx, x, y, taille, taille, 18)
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
    document.fonts?.load(`700 52px ${TITRE}`),
    document.fonts?.load(`400 24px ${TEXTE}`),
    document.fonts?.load(`600 22px ${TEXTE}`),
  ])
  const [avatar, drapeau] = await Promise.all([
    profil.avatarUrl ? chargerImage(profil.avatarUrl) : Promise.resolve(null),
    urlDrapeau(profil.pays) ? chargerImage(urlDrapeau(profil.pays)!) : Promise.resolve(null),
  ])
  const M = 56

  // Fond : nuit, une lueur bleue en haut à gauche et une lueur orange en bas à droite, comme l'accueil du site.
  ctx.fillStyle = C.fond
  ctx.fillRect(0, 0, LARGEUR, HAUTEUR)
  for (const [x, y, r, couleur] of [[120, 60, 560, '2, 151, 222'], [LARGEUR - 60, HAUTEUR + 40, 520, '252, 115, 77']] as const) {
    const lueur = ctx.createRadialGradient(x, y, 0, x, y, r)
    lueur.addColorStop(0, `rgba(${couleur}, 0.26)`)
    lueur.addColorStop(1, `rgba(${couleur}, 0)`)
    ctx.fillStyle = lueur
    ctx.fillRect(0, 0, LARGEUR, HAUTEUR)
  }
  ctx.strokeStyle = 'rgba(238, 244, 247, 0.08)'
  ctx.lineWidth = 2
  arrondi(ctx, 18, 18, LARGEUR - 36, HAUTEUR - 36, 28)
  ctx.stroke()

  // Marque, et disponibilité à droite.
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = C.texte
  ctx.font = `700 30px ${TITRE}`
  ctx.fillText('jamii', M, 84)
  ctx.fillStyle = C.preuve
  ctx.beginPath()
  ctx.arc(M + ctx.measureText('jamii').width + 9, 79, 5, 0, Math.PI * 2)
  ctx.fill()
  ctx.font = `600 20px ${TEXTE}`
  const dispo = textes.disponibilite
  const ld = ctx.measureText(dispo).width + 52
  const dx = LARGEUR - M - ld
  ctx.fillStyle = 'rgba(238, 244, 247, 0.08)'
  arrondi(ctx, dx, 54, ld, 40, 20)
  ctx.fill()
  ctx.fillStyle = profil.disponibilite === 'disponible' ? C.vert : profil.disponibilite === 'occupe' ? C.orange : C.discret
  ctx.beginPath()
  ctx.arc(dx + 22, 74, 6, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = C.texte
  ctx.fillText(dispo, dx + 38, 81)

  // Photo ronde, avec un anneau bleu.
  const at = 148
  const ax = M
  const ay = 140
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
    ctx.font = `700 54px ${TITRE}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(initiales(profil.nom), ax + at / 2, ay + at / 2 + 3)
    ctx.textAlign = 'start'
    ctx.textBaseline = 'alphabetic'
  }
  ctx.restore()
  ctx.strokeStyle = C.action
  ctx.lineWidth = 4
  ctx.beginPath()
  ctx.arc(ax + at / 2, ay + at / 2, at / 2 + 6, 0, Math.PI * 2)
  ctx.stroke()

  // Identité, à droite de la photo ; le QR code occupe la colonne de droite.
  const qt = 196
  const qx = LARGEUR - M - qt
  const tx = ax + at + 36
  const largeurTexte = qx - 40 - tx
  ctx.fillStyle = C.texte
  ctx.font = `700 50px ${TITRE}`
  ctx.fillText(ajuster(ctx, profil.nom, largeurTexte), tx, 196)
  let y = 238
  if (profil.titre) {
    ctx.fillStyle = C.action
    ctx.font = `600 24px ${TEXTE}`
    ctx.fillText(ajuster(ctx, profil.titre, largeurTexte), tx, y)
    y += 42
  }
  const lieu = [profil.ville, profil.pays && nomPays(profil.pays, textes.langue)].filter(Boolean).join(', ')
  if (lieu) {
    let lx = tx
    if (drapeau) {
      ctx.save()
      arrondi(ctx, lx, y - 20, 32, 24, 4)
      ctx.clip()
      ctx.drawImage(drapeau, lx, y - 20, 32, 24)
      ctx.restore()
      lx += 44
    }
    ctx.fillStyle = C.discret
    ctx.font = `400 22px ${TEXTE}`
    ctx.fillText(ajuster(ctx, lieu, largeurTexte - (lx - tx)), lx, y)
  }

  // Compétences mises en avant, en pastilles, sous la photo.
  const phares = competencesPhares(profil)
  let px = M
  const py = 334
  ctx.font = `600 22px ${TEXTE}`
  for (const c of phares) {
    const l = Math.min(ctx.measureText(c).width + 40, 260)
    if (px + l > qx - 24) break
    ctx.fillStyle = 'rgba(2, 151, 222, 0.16)'
    arrondi(ctx, px, py, l, 46, 23)
    ctx.fill()
    ctx.fillStyle = '#7CC8EE'
    ctx.fillText(ajuster(ctx, c, l - 40), px + 20, py + 31)
    px += l + 10
  }

  // Chiffres : uniquement ce qui se compte, jamais une note.
  const verifies = profil.projets.filter((p) => p.enLigne).length
  const chiffres = [
    { n: verifies, libelle: textes.chiffres.projets(verifies), preuve: verifies > 0 },
    { n: profil.realisations.length, libelle: textes.chiffres.realisations(profil.realisations.length), preuve: false },
    { n: profil.competences.length, libelle: textes.chiffres.competences(profil.competences.length), preuve: false },
  ]
  ctx.strokeStyle = C.ligne
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(M, 434)
  ctx.lineTo(qx - 32, 434)
  ctx.stroke()
  const colonne = (qx - 32 - M) / chiffres.length
  chiffres.forEach((c, i) => {
    const cx = M + i * colonne
    ctx.fillStyle = c.preuve ? C.preuve : C.texte
    ctx.font = `700 52px ${TITRE}`
    ctx.fillText(String(c.n), cx, 516)
    ctx.fillStyle = C.discret
    ctx.font = `400 19px ${TEXTE}`
    ctx.fillText(ajuster(ctx, c.libelle, colonne - 16), cx, 550)
  })

  // QR code vers le profil public, et l'adresse en dessous.
  const qy = 140
  dessinerQr(ctx, lien, qx, qy, qt)
  ctx.fillStyle = C.texte
  ctx.font = `600 20px ${TEXTE}`
  ctx.textAlign = 'center'
  ctx.fillText(textes.scanner, qx + qt / 2, qy + qt + 38)
  ctx.fillStyle = C.discret
  ctx.font = `400 16px ${TEXTE}`
  ctx.fillText(ajuster(ctx, lien.replace(/^https?:\/\//, ''), qt + 30), qx + qt / 2, qy + qt + 64)
  ctx.textAlign = 'start'

  // Bandeau aux couleurs de la marque, en bas.
  const bande = ctx.createLinearGradient(0, 0, LARGEUR, 0)
  bande.addColorStop(0, C.action)
  bande.addColorStop(1, C.preuve)
  ctx.fillStyle = bande
  arrondi(ctx, M, HAUTEUR - 70, LARGEUR - 2 * M, 6, 3)
  ctx.fill()

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
