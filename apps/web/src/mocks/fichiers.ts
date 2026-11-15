import { http, HttpResponse } from 'msw'
import type { components } from '../api/generated/fichiers'

type Piece = components['schemas']['PieceJointe']

/*
 * Les fichiers joints (contracts/fichiers.yaml) en mémoire, pour le mode factice et les tests : on garde le fichier
 * envoyé et on le rend tel quel. Le vrai serveur vérifie le type d'après les octets ; ici, d'après le type annoncé.
 */
const fichiers = new Map<string, { piece: Piece; contenu: Blob; destinataire?: string; cercle?: string }>()

export function reinitialiserFichiers() {
  fichiers.clear()
}

/** Pour les tests : une pièce jointe connue du serveur simulé, sans passer par un envoi. */
export function enregistrerPieceFactice(piece: Piece, destinataire?: string) {
  fichiers.set(piece.id, { piece, contenu: new Blob([]), destinataire })
}

/** Les pièces jointes connues, dans l'ordre demandé (pour les messages et publications des mocks). */
export function piecesDe(ids: string[] | undefined, destinataire?: string, cercle?: string): Piece[] {
  return (ids ?? []).map((id) => fichiers.get(id)?.destinataire === destinataire && fichiers.get(id)?.cercle === cercle ? fichiers.get(id)?.piece : undefined).filter((p): p is Piece => !!p)
}

const sorteDe = (type: string): Piece['sorte'] => (type.startsWith('image/') ? 'image' : type.startsWith('video/') ? 'video' : 'document')

export const handlersFichiers = [
  http.post('/api/fichiers', async ({ request }) => {
    const form = await request.formData()
    const fichier = form.get('fichier')
    if (!(fichier instanceof Blob)) return HttpResponse.json({ code: 'validation' }, { status: 400 })
    const type = fichier.type || 'application/octet-stream'
    if (/html|svg|javascript/.test(type)) return HttpResponse.json({ code: 'type_refuse' }, { status: 415 })
    const sorte = sorteDe(type)
    const max = sorte === 'video' ? 25 : 10
    if (fichier.size > max * 1024 * 1024) return HttpResponse.json({ code: 'trop_gros' }, { status: 413 })
    const id = crypto.randomUUID()
    const nom = fichier instanceof File ? fichier.name : 'fichier'
    const piece: Piece = { id, nom, type, sorte, taille: fichier.size, url: `/api/fichiers/${id}` }
    fichiers.set(id, { piece, contenu: fichier, destinataire: form.get('destinataireId')?.toString(), cercle: form.get('cercleId')?.toString() })
    return HttpResponse.json(piece, { status: 201 })
  }),
  http.get('/api/fichiers/:id', ({ params }) => {
    const f = fichiers.get(String(params.id))
    if (!f) return new HttpResponse(null, { status: 404 })
    return new HttpResponse(f.contenu, { headers: { 'Content-Type': f.piece.type } })
  }),
]
