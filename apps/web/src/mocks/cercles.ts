import { http, HttpResponse } from 'msw'
import type { Cercle, CercleResume, MembreCercle, Tour } from '../features/cercles/cercles'

// Données de démo (docs/priorites.md) : le cercle de Dakar. Personnes fictives, adresses en example.com.
const JOUR = 86_400_000
const ilYa = (jours: number) => new Date(Date.now() - jours * JOUR).toISOString()
const dans = (jours: number) => new Date(Date.now() + jours * JOUR).toISOString()

const AWA = '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11'

function membre(id: string, nom: string, ville: string, pays: string, competences: string[], ordre: number, confiance: number, idee: string): MembreCercle {
  return { utilisateurId: id, nom, avatarUrl: null, ville, pays, competences, ordre, confiance, idee: { titre: idee, description: `${idee}, pensé pour un téléphone et une connexion lente.` } }
}

const membresDakar: MembreCercle[] = [
  membre(AWA, 'Awa Diop', 'Dakar', 'SN', ['vue', 'typescript'], 1, 92, 'Appli de covoiturage étudiant'),
  membre('c1a00000-0000-4000-8000-000000000002', 'Kofi Yao', 'Abidjan', 'CI', ['spring', 'postgresql'], 2, 88, 'Boutique en ligne pour artisans'),
  membre('c1a00000-0000-4000-8000-000000000003', 'Fatou Ndiaye', 'Thiès', 'SN', ['design', 'figma'], 3, 95, 'Suivi des cultures par SMS'),
  membre('c1a00000-0000-4000-8000-000000000004', 'Moussa Traoré', 'Bamako', 'ML', ['flutter', 'dart'], 4, 81, 'Plateforme de cours de wolof'),
  membre('c1a00000-0000-4000-8000-000000000005', 'Nia Wanjiru', 'Nairobi', 'KE', ['python', 'data'], 5, 90, 'Carnet de santé pour les cliniques'),
  membre('c1a00000-0000-4000-8000-000000000006', 'Yann Kouassi', 'Dakar', 'SN', ['devops', 'docker'], 6, 86, 'Annuaire des artisans du quartier'),
]

function tour(numero: number, m: MembreCercle, statut: Tour['statut'], debut: number): Tour {
  const enCours = statut === 'en_cours'
  return {
    numero,
    beneficiaireId: m.utilisateurId,
    projetId: enCours ? 'b1d2c3a4-0000-4000-8000-0000000000a1' : null,
    titre: m.idee!.titre,
    statut,
    debut: debut < 0 ? ilYa(-debut) : dans(debut),
    fin: dans(debut + 14),
    avancement: enCours ? { faites: 7, total: 12 } : { faites: 0, total: 0 },
    depot: enCours ? { fournisseur: 'gitea', url: 'https://code.example.com/cercle-dakar/covoiturage-etudiant', cloneUrl: 'https://code.example.com/cercle-dakar/covoiturage-etudiant.git' } : null,
  }
}

export const cercleDakar: Cercle = {
  id: 'cercle-dakar',
  nom: 'Cercle de Dakar',
  ville: 'Dakar',
  pays: 'SN',
  taille: 6,
  statut: 'en_cours',
  tirageFait: true,
  membres: membresDakar,
  tours: membresDakar.map((m, i) => tour(i + 1, m, i === 0 ? 'en_cours' : 'a_venir', i * 14 - 3)),
  messageGriot: {
    texte: 'Jour 3 du tour d’Awa : 7 tâches sur 12 terminées. Kofi a livré l’écran de connexion, Fatou la base de données. Demain : la carte des trajets. On est dans les temps.',
    le: ilYa(0.1),
  },
}

export const cercleOuvert: Cercle = {
  id: 'cercle-abidjan',
  nom: 'Cercle d’Abidjan',
  ville: 'Abidjan',
  pays: 'CI',
  taille: 5,
  statut: 'ouvert',
  tirageFait: false,
  membres: membresDakar.slice(1, 4).map((m) => ({ ...m, ordre: null })),
  tours: [],
  messageGriot: null,
}

const cercles = [cercleDakar, cercleOuvert]

function resume(c: Cercle): CercleResume {
  const t = c.tours.find((x) => x.statut === 'en_cours')
  const beneficiaire = t && c.membres.find((m) => m.utilisateurId === t.beneficiaireId)
  return {
    id: c.id, nom: c.nom, ville: c.ville, pays: c.pays, taille: c.taille, statut: c.statut,
    membres: c.membres.map(({ utilisateurId, nom, avatarUrl }) => ({ utilisateurId, nom, avatarUrl })),
    tourEnCours: t && beneficiaire ? { numero: t.numero, beneficiaire: beneficiaire.nom } : null,
  }
}

export const handlersCercles = [
  http.get('/api/cercles', () => HttpResponse.json({ elements: cercles.map(resume), suivant: null })),
  http.get('/api/cercles/:id', ({ params }) => {
    const c = cercles.find((x) => x.id === params.id)
    return c
      ? HttpResponse.json(c)
      : HttpResponse.json({ type: 'urn:jamii:erreur:introuvable', title: 'Introuvable', status: 404, code: 'introuvable' }, { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
  }),
]
