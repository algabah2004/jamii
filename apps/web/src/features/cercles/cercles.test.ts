import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { ID_ABIDJAN, ID_DAKAR } from '../../mocks/cercles'
import { installerFauxWebrtc } from '../reseau/fauxWebrtc'
import { reinitialiserAppelCercle } from './appelCercle'

// jsdom ne sait pas envoyer un FormData par fetch (« Premature close ») : l'envoi d'un fichier est simulé ici, avec la
// vraie règle de taille. Le parcours réel est vérifié par l'API (FichiersTest) et dans le navigateur.
vi.mock('../../components/texte/fichiers', async (original) => {
  const vrai = await original<typeof import('../../components/texte/fichiers')>()
  return {
    ...vrai,
    envoyerFichier: async (f: File) => {
      if (f.size > vrai.limiteDe(f)) return { ok: false, code: 'trop_gros' }
      const id = crypto.randomUUID()
      const piece = { id, nom: f.name, type: f.type, sorte: f.type.startsWith('image/') ? 'image' : 'document', taille: f.size, url: `/api/fichiers/${id}` } as const
      ;(await import('../../mocks/fichiers')).enregistrerPieceFactice(piece)
      return { ok: true, piece }
    },
  }
})
import { avancement, avantTirage, lireCercles, ordreDePassage, positionsAnneau, pourcentage, titreDuTour, type MembreCercle } from './cercles'

enableAutoUnmount(afterEach)

async function ouvrir(adresse: string) {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(adresse)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.find('main [role="status"]').exists() && page.text().includes('Chargement')).toBe(false))
  return { page, routeur }
}

const m = (id: string, rang: number | null, idee: string | null = null): MembreCercle => ({
  personne: { id, nom: id },
  rang,
  aDeposeIdee: !!idee,
  idee: idee ? { titre: idee, description: '…' } : null,
  rejointLe: '2026-11-01T00:00:00Z',
})

describe('les règles du cercle', () => {
  it("range les membres par rang, ceux sans rang à la fin dans leur ordre d'arrivée", () => {
    const ordre = ordreDePassage({ membres: [m('C', null), m('B', 2), m('D', null), m('A', 1)] })
    expect(ordre.map((x) => x.personne.id)).toEqual(['A', 'B', 'C', 'D'])
  })

  it("pose le premier membre en haut de l'anneau, puis tourne dans le sens des aiguilles d'une montre", () => {
    const [haut, droite, bas, gauche] = positionsAnneau(4, 40)
    expect(haut).toEqual({ x: 50, y: 10 })
    expect(droite).toEqual({ x: 90, y: 50 })
    expect(bas).toEqual({ x: 50, y: 90 })
    expect(gauche).toEqual({ x: 10, y: 50 })
  })

  it("n'autorise le tirage que si le cercle est complet, que chacun a son idée, et une seule fois", () => {
    const quatre = [m('A', null, 'a'), m('B', null, 'b'), m('C', null, 'c'), m('D', null, 'd')]
    expect(avantTirage({ membres: quatre, taille: 4, tirage: null }).pret).toBe(true)
    expect(avantTirage({ membres: quatre.slice(0, 3), taille: 4, tirage: null })).toEqual({ manquants: 1, sansIdee: 0, pret: false })
    expect(avantTirage({ membres: [...quatre.slice(0, 3), m('D', null)], taille: 4, tirage: null }).sansIdee).toBe(1)
    expect(avantTirage({ membres: quatre, taille: 4, tirage: { effectueLe: '', parId: 'A' } }).pret).toBe(false)
  })

  it("donne au tour le titre de l'idée de son bénéficiaire, et rien si on ne la voit pas", () => {
    expect(titreDuTour({ membres: [m('A', 1, 'Covoiturage')] }, { beneficiaire: { id: 'A', nom: 'A' } })).toBe('Covoiturage')
    expect(titreDuTour({ membres: [m('A', 1)] }, { beneficiaire: { id: 'A', nom: 'A' } })).toBeNull()
  })

  it('compte le jour du tour, les jours restants (jamais négatifs) et les tâches livrées', () => {
    const maintenant = new Date('2026-11-13T12:00:00Z')
    const taches = [{ statut: 'livree' as const }, { statut: 'en_cours' as const }, { statut: 'a_faire' as const }]
    expect(avancement('2026-11-11T09:00:00Z', taches, maintenant)).toEqual({ jour: 3, joursRestants: 11, faites: 1, total: 3 })
    expect(avancement('2026-10-01T09:00:00Z', [], maintenant).joursRestants).toBe(0)
    expect(pourcentage({ faites: 7, total: 12 })).toBe(58)
    expect(pourcentage({ faites: 0, total: 0 })).toBe(0)
  })
})

describe('la page du cercle', () => {
  it("montre l'anneau, le tour en cours avec son avancement et son dépôt, et le point du tour", async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="avancement"]').exists()).toBe(true))
    expect(page.findAll('.anneau__membre')).toHaveLength(6)
    expect(page.find('.anneau__membre--tour .anneau__nom').text()).toBe('Awa')
    expect(page.find('[data-test="tour"]').text()).toContain('Tour 1 · le projet de Awa')
    expect(page.find('[data-test="tour"] h2').text()).toBe('Appli de covoiturage étudiant')
    expect(page.find('[data-test="avancement"]').text()).toContain('7 tâches sur 12')
    expect(page.findAll('[data-test="ordre"] li')).toHaveLength(6)
  })

  it('montre le code du tour dans Jamii : commande pour cloner, derniers commits et demandes de fusion', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="commits"]').exists()).toBe(true))
    const code = page.get('[data-test="code-du-tour"]')
    expect(code.get('[data-test="clone"]').text()).toMatch(/^git clone https:\/\//)
    expect(code.findAll('[data-test="commits"] li').length).toBeGreaterThan(0)
    expect(code.get('[data-test="demandes"]').text()).toContain('Fusionnée')
    for (const lien of code.findAll('a[target="_blank"]')) {
      expect(lien.attributes('rel')).toContain('noopener')
    }
  })

  it("garde le lien du dépôt et le dit quand le fournisseur ne répond pas", async () => {
    serveur.use(
      http.get('/api/cercles/:id/tours/:numero/activite', () =>
        HttpResponse.json({
          etat: 'indisponible', luLe: '2026-11-14T21:00:00Z', commits: [], demandesDeFusion: [],
          depot: { fournisseur: 'github', url: 'https://github.com/jamii-cercles/x', cloneUrl: 'https://github.com/jamii-cercles/x.git' },
        }),
      ),
    )
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="indisponible"]').exists()).toBe(true))
    expect(page.get('[data-test="code-du-tour"]').text()).toContain('Ouvrir sur GitHub')
    expect(page.find('[data-test="commits"]').exists()).toBe(false)
  })

  it("cache les idées et le bouton du tirage à qui n'est pas membre", async () => {
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="attente-tirage"]').exists()).toBe(true))
    expect(page.find('[data-test="attente-tirage"]').text()).toContain('Il manque 2 membres')
    expect(page.find('[data-test="ordre"]').text()).toContain('Idée déposée')
    expect(page.find('[data-test="ordre"]').text()).not.toContain('Boutique en ligne')
    expect(page.find('[data-test="tirer"]').exists()).toBe(false)
  })

  it('dit que le cercle est introuvable, et propose de réessayer si le serveur ne répond pas', async () => {
    const { page } = await ouvrir('/cercles/a0c1e2c1-0000-4000-8000-000000000999')
    await vi.waitFor(() => expect(page.text()).toContain("Cette page n'existe pas."))

    serveur.use(http.get('/api/cercles/:id', () => HttpResponse.json({}, { status: 503 })))
    const autre = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(autre.page.find('[role="alert"]').exists()).toBe(true))
  })

  it("met en avant le tour en cours de mes cercles, avec ses membres et son avancement", async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.find('[data-test="vedette"]').text()).toContain('7 tâches sur 12'))
    const vedette = page.find('[data-test="vedette"]')
    expect(vedette.text()).toContain('Tour 1 · le projet de Awa')
    expect(vedette.text()).toContain('Appli de covoiturage étudiant')
    expect(vedette.attributes('href')).toBe(`/cercles/${ID_DAKAR}`)
    expect(vedette.findAll('.visages__un')).toHaveLength(6)
    // Mes cercles disent où ils en sont : le tour en cours, ou les membres qui manquent.
    expect(page.find('[data-test="cercles-mes"]').text()).toContain('Il manque 2 membres.')
  })

  it('sépare mes cercles de ceux qui cherchent des membres', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles-mes"] li')).toHaveLength(2))
    // Les recommandations sont limitées à six, les autres cercles restent explorables sans doublons.
    expect(page.findAll('[data-test="cercles-pour-moi"] li')).toHaveLength(6)
    expect(page.findAll('[data-test="cercles-ouverts"] li')).toHaveLength(16)
    const liens = page.findAll('.grille a').map((a) => a.attributes('href'))
    expect(new Set(liens).size).toBe(24)
    expect(page.find('nav a[href="/cercles"]').exists()).toBe(true)
  })
})

describe('le catalogue distant des cercles', () => {
  it('lit toutes les pages de l’API pour ne pas masquer les cercles après le vingtième', async () => {
    const curseurs: (string | null)[] = []
    serveur.use(http.get('/api/cercles', ({ request }) => {
      const query = new URL(request.url).searchParams
      expect(query.get('limite')).toBe('100')
      curseurs.push(query.get('curseur'))
      return HttpResponse.json(query.get('curseur')
        ? { elements: [{ id: 'deuxieme-page', nom: 'Dernier cercle' }], curseurSuivant: null }
        : { elements: [{ id: 'premiere-page', nom: 'Premier cercle' }], curseurSuivant: 'page-suivante' })
    }))
    expect((await lireCercles('ouverts')).map((c) => c.nom)).toEqual(['Premier cercle', 'Dernier cercle'])
    expect(curseurs).toEqual([null, 'page-suivante'])
  })

  it('refuse un curseur qui boucle au lieu de multiplier les requêtes', async () => {
    serveur.use(http.get('/api/cercles', () => HttpResponse.json({ elements: [], curseurSuivant: 'meme-page' })))
    await expect(lireCercles('ouverts')).rejects.toThrow('pagination des cercles invalide')
  })
})

describe('explorer les cercles', () => {
  it('recherche par nom, ville et créateur sans tenir compte des accents', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('.grille a')).toHaveLength(24))
    await page.get('[data-test="rechercher-cercles"]').setValue('THIES')
    expect(page.findAll('.grille a')).toHaveLength(1)
    expect(page.get('.grille').text()).toContain('Les Makers de Thiès')
    await page.get('[data-test="rechercher-cercles"]').setValue('Salma')
    expect(page.get('.grille').text()).toContain('Casa Web Studio')
    await page.get('[data-test="rechercher-cercles"]').setValue('aucun-cercle')
    expect(page.get('[data-test="aucun-cercle"]').text()).toContain('Pas encore le bon cercle')
    await page.get('[data-test="aucun-cercle"] button').trigger('click')
    expect(page.findAll('.grille a')).toHaveLength(24)
  })

  it('combine la ville et les filtres sans mélanger mes cercles et ceux à rejoindre', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('.grille a')).toHaveLength(24))
    await page.get('[data-test="ville-cercles"]').setValue('Dakar')
    expect(page.findAll('.grille a')).toHaveLength(2)
    await page.get('[data-test="filtre-mes"]').trigger('click')
    expect(page.findAll('.grille a')).toHaveLength(1)
    expect(page.get('.grille a').text()).toContain('Cercle de Dakar')
    await page.get('[data-test="filtre-ouverts"]').trigger('click')
    expect(page.findAll('.grille a')).toHaveLength(1)
    expect(page.get('.grille a').text()).toContain('Les Artisanes du Web')
  })

  it('trie les cercles à rejoindre par nombre de places disponibles', async () => {
    const { page } = await ouvrir('/cercles')
    await vi.waitFor(() => expect(page.findAll('[data-test="cercles-ouverts"] li')).toHaveLength(16))
    await page.get('[data-test="tri-cercles"]').setValue('places')
    const cartes = page.findAll('[data-test="cercles-ouverts"] li')
    expect(cartes[0]!.text()).toContain('5 places libres')
    expect(cartes.at(-1)!.text()).toContain('1 place libre')
  })
})

describe('du cercle vide au tirage', () => {
  it("crée un cercle, dépose son idée, puis tire l'ordre au sort : le premier tour commence", async () => {
    const { page, routeur } = await ouvrir('/cercles/nouveau')
    // Une question à la fois : le nom, combien on sera, puis l'aperçu. Pas de ville.
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    expect(page.find('[data-test="nouveau-cercle"]').text()).not.toContain('Ville')
    expect(page.get('[data-test="suivant"]').attributes('disabled')).toBeDefined()
    await page.get('[data-test="champ-nom"]').setValue('Cercle de Thiès')
    await page.get('[data-test="champ-nom"]').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.find('[data-test="taille-4"]').exists()).toBe(true))
    await page.get('[data-test="taille-4"]').trigger('change')
    // Les profils dont le cercle a besoin : facultatifs, un par place libre (3 ici).
    await vi.waitFor(() => expect(page.find('[data-test="profils"]').exists()).toBe(true), { timeout: 2000 })
    for (const p of ['mobile', 'design', 'back']) await page.get(`[data-test="profil-${p}"]`).setValue(true)
    expect((page.get('[data-test="profil-data"]').element as HTMLInputElement).disabled).toBe(true)
    await page.get('[data-test="profil-back"]').setValue(false)
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.text()).toContain('Prêt à lancer « Cercle de Thiès » ?'), { timeout: 2000 })
    expect(page.text()).toContain('3 personnes le rejoignent')
    expect(page.get('[data-test="profils-choisis"]').text()).toContain('Mobile et Design')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="succes"]').exists()).toBe(true))
    await page.get('[data-test="entrer"]').trigger('click')
    await vi.waitFor(() => expect(routeur.currentRoute.value.name).toBe('cercle'))
    await vi.waitFor(() => expect(page.find('[data-test="attente-tirage"]').exists()).toBe(true))
    expect(page.get('[data-test="profils-recherches"]').text()).toContain('Mobile')

    // Les 3 autres membres ont déjà déposé leur idée ; le tirage attend la mienne.
    expect(page.find('[data-test="tirer"]').attributes('disabled')).toBeDefined()
    await page.find('[data-test="ouvrir-idee"]').trigger('click')
    const form = page.find('[data-test="form-idee"]')
    await form.find('input').setValue('Appli de covoiturage étudiant')
    await form.find('textarea').setValue('Pour les étudiants de Thiès qui vont à Dakar chaque semaine.')
    await form.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="mon-idee"]').text()).toContain('Appli de covoiturage étudiant'))

    await page.find('[data-test="tirer"]').trigger('click')
    // Une fois l'ordre tiré, on arrive au QG du cercle ; « Le cercle » montre l'ordre de passage.
    await vi.waitFor(() => expect(page.find('[data-test="qg"]').exists()).toBe(true))
    await page.get('[data-test="onglet-cercle"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="tour"]').exists()).toBe(true))
    expect(page.findAll('[data-test="ordre"] li .ordre__numero').map((n) => n.text())).toEqual(['1', '2', '3', '4'])
  })

  it("revient en arrière sans perdre ce qu'on a écrit", async () => {
    const { page } = await ouvrir('/cercles/nouveau')
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    await page.get('[data-test="champ-nom"]').setValue('Cercle Ubuntu')
    await page.get('[data-test="suivant"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="taille-5"]').exists()).toBe(true))
    await page.get('[data-test="precedent"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="champ-nom"]').exists()).toBe(true))
    expect((page.get('[data-test="champ-nom"]').element as HTMLInputElement).value).toBe('Cercle Ubuntu')
  })

  it('rejoint un cercle ouvert : on voit alors les idées, et le bouton du tirage, grisé tant que le cercle n’est pas prêt', async () => {
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre"]').exists()).toBe(true))
    await page.find('[data-test="rejoindre"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('.anneau__membre')).toHaveLength(4))
    expect(page.find('[data-test="rejoindre"]').exists()).toBe(false)
    expect(page.find('[data-test="ordre"]').text()).toContain('Boutique en ligne')
    expect(page.find('[data-test="ouvrir-idee"]').exists()).toBe(true)
    // N'importe quel membre peut lancer le tirage (#157) ; ici le cercle n'est pas complet : bouton grisé.
    expect(page.find('[data-test="tirer"]').exists()).toBe(true)
    expect(page.find('[data-test="tirer"]').attributes('disabled')).toBeDefined()
  })

  it("explique un refus de l'API au lieu d'échouer en silence", async () => {
    serveur.use(http.post('/api/cercles/:id/membres', () => HttpResponse.json({ code: 'cercle_complet' }, { status: 409 })))
    const { page } = await ouvrir(`/cercles/${ID_ABIDJAN}`)
    await vi.waitFor(() => expect(page.find('[data-test="rejoindre"]').exists()).toBe(true))
    await page.find('[data-test="rejoindre"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[role="alert"]').text()).toBe('Le cercle est déjà complet.'))
  })
})

describe('livrer un tour', () => {
  it('demande confirmation, livre le tour et ouvre celui du membre suivant', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="livrer"]').exists()).toBe(true))
    await page.find('[data-test="livrer"]').trigger('click')
    expect(page.find('[data-test="confirmer-livraison"]').text()).toContain('le tour de Kofi commence')
    await page.find('[data-test="confirmer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="livre"]').text()).toContain('Le projet de Awa est livré.'))
    expect(page.find('[data-test="tour"]').text()).toContain('Tour 2 · le projet de Kofi')
    expect(page.find('.ordre__ligne--livre').text()).toContain('Awa Diop')
  })

  it("cache « Livrer » à un membre qui n'est ni le bénéficiaire ni le créateur", async () => {
    const original = await (await fetch(new URL(`/api/cercles/${ID_DAKAR}`, window.location.origin))).json()
    serveur.use(
      http.get('/api/cercles/:id', () =>
        HttpResponse.json({ ...original, moi: { estCreateur: false, estMembre: true }, tours: original.tours.map((x: { numero: number; beneficiaire: unknown }, i: number) => (i === 0 ? { ...x, beneficiaire: original.membres[1].personne } : x)) }),
      ),
    )
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="tour"]').exists()).toBe(true))
    expect(page.find('[data-test="livrer"]').exists()).toBe(false)
  })
})

describe('les tâches du tour', () => {
  it('ajoute une tâche, la prend, la coche livrée, et la barre du tour suit', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="taches-du-tour"] [data-test="ajouter-tache"]').exists()).toBe(true))
    const avant = page.findAll('[data-test="tache"]').length
    const faitesAvant = page.findAll('[data-test="cocher"]').filter((c) => (c.element as HTMLInputElement).checked).length

    await page.find('#nouvelle-tache').setValue('Écran de connexion')
    await page.find('[data-test="ajouter-tache"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="tache"]')).toHaveLength(avant + 1))
    expect(page.find('[data-test="avancement"]').text()).toContain(`${faitesAvant} tâches sur ${avant + 1}`)

    const nouvelle = () => page.findAll('[data-test="tache"]').find((li) => li.text().includes('Écran de connexion'))!
    await nouvelle().find('[data-test="prendre"]').trigger('click')
    await vi.waitFor(() => expect(nouvelle().find('[data-test="lacher"]').exists()).toBe(true))

    await nouvelle().find('[data-test="cocher"]').trigger('change')
    await vi.waitFor(() => expect(nouvelle().classes()).toContain('tache--livree'))
    expect(page.find('[data-test="avancement"]').text()).toContain(`${faitesAvant + 1} tâches sur ${avant + 1}`)
  })

  it("dit quand quelqu'un a changé la tâche entre-temps", async () => {
    serveur.use(http.patch('/api/projets/:p/taches/:t', () => HttpResponse.json({ code: 'modification_concurrente' }, { status: 409 })))
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="cocher"]').exists()).toBe(true))
    await page.find('[data-test="cocher"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="taches-du-tour"] [role="alert"]').text()).toContain('entre-temps'))
  })
})

describe('les personnes du cercle', () => {
  it('mènent à leur profil, dans l’anneau comme dans l’ordre de passage', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="ordre"]').exists()).toBe(true))
    const liens = page.findAll('[data-test="lien-personne"]').map((l) => l.attributes('href'))
    expect(liens).toContain('/p/6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11')
  })
})

describe('le salon du cercle', () => {
  it("n'apparaît qu'aux membres, dans son propre onglet", async () => {
    const { page: dehors } = await ouvrir(`/cercles/${ID_ABIDJAN}?onglet=salon`)
    await vi.waitFor(() => expect(dehors.find('[data-test="anneau"]').exists()).toBe(true))
    expect(dehors.find('[data-test="onglet-salon"]').exists()).toBe(false)
    expect(dehors.find('[data-test="salon-cercle"]').exists()).toBe(false)
    dehors.unmount()

    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=cercle`)
    await vi.waitFor(() => expect(page.find('[data-test="onglet-salon"]').exists()).toBe(true))
    await page.get('[data-test="onglet-salon"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="salon-cercle"]').exists()).toBe(true))
    expect(page.find('[data-test="anneau"]').exists()).toBe(false)
    page.unmount()
  })

  it("raconte les étapes, montre les points du jour et signale les demandes d’aide", async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.findAll('[data-test="evenement"]').length).toBeGreaterThan(3))
    expect(page.text()).toContain('Le tour 2 commence : place au projet de Kofi')
    expect(page.get('[data-test="point-du-jour"]').text()).toContain('Le tableau des pesées du jour.')
    expect(page.get('[data-test="coups-de-main"]').text()).toContain('Android 8')
    expect(page.get('[data-test="contexte"]').text()).toContain('Tour 1')
    page.unmount()
  })

  it('envoie un message avec Entrée et le montre tout de suite', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('#salon-texte').exists()).toBe(true))
    await page.get('#salon-texte').setValue('On fait la démo à 19 h ?')
    await page.get('#salon-texte').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]').at(-1)!.text()).toContain('On fait la démo à 19 h ?'))
    expect((page.get('#salon-texte').element as HTMLTextAreaElement).value).toBe('')
    page.unmount()
  })

  it('sur un message : réagir, répondre, corriger puis supprimer (#157)', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('#salon-texte').exists()).toBe(true))
    const dernier = () => page.findAll('[data-test="message"]').at(-1)!

    // Le message de Kofi a déjà deux 👍 : je réagis avec 🔥 depuis son menu.
    const kofi = page.findAll('[data-test="message"]').find((m) => m.text().includes('12 tâches'))!
    expect(kofi.find('[data-test="actions-message"]').exists()).toBe(true)
    await kofi.get('[data-test="actions-message"]').trigger('click')
    await kofi.findAll('[data-test="reaction-rapide"]').find((b) => b.text() === '🔥')!.trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]').find((m) => m.text().includes('12 tâches'))!.text()).toContain('🔥'))

    // Je lui réponds : la réponse cite son message.
    const kofiApres = page.findAll('[data-test="message"]').find((m) => m.text().includes('12 tâches'))!
    await kofiApres.get('[data-test="actions-message"]').trigger('click')
    await kofiApres.get('[data-test="action-repondre"]').trigger('click')
    expect(page.get('[data-test="bandeau-action"]').text()).toContain('12 tâches')
    await page.get('#salon-texte').setValue('Je prends les écrans')
    await page.get('#salon-texte').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(dernier().find('[data-test="citation"]').exists()).toBe(true))
    expect(dernier().get('[data-test="citation"]').text()).toContain('12 tâches')
    expect(page.find('[data-test="bandeau-action"]').exists()).toBe(false)

    // Je corrige ma réponse, puis je la supprime.
    await dernier().get('[data-test="actions-message"]').trigger('click')
    await dernier().get('[data-test="action-modifier"]').trigger('click')
    expect((page.get('#salon-texte').element as HTMLTextAreaElement).value).toBe('Je prends les écrans')
    await page.get('#salon-texte').setValue('Je prends les écrans de connexion')
    await page.get('#salon-texte').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(dernier().find('[data-test="modifie"]').exists()).toBe(true))
    expect(dernier().text()).toContain('Je prends les écrans de connexion')

    await dernier().get('[data-test="actions-message"]').trigger('click')
    await dernier().get('[data-test="action-supprimer"]').trigger('click')
    await dernier().get('[data-test="confirmer-suppression"]').trigger('click')
    await vi.waitFor(() => expect(dernier().find('[data-test="message-supprime"]').exists()).toBe(true))
    expect(dernier().find('[data-test="actions-message"]').exists()).toBe(false)
    // Le message d'un autre ne se corrige ni ne se supprime.
    await kofiApres.get('[data-test="actions-message"]').trigger('click')
    expect(kofiApres.find('[data-test="action-modifier"]').exists()).toBe(false)
    expect(kofiApres.find('[data-test="action-supprimer"]').exists()).toBe(false)
    page.unmount()
  })

  it('« Je peux aider » répond à la personne, « C’est réglé » retire le coup de main', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="coup-de-main"]').exists()).toBe(true))
    await page.get('[data-test="aider"]').trigger('click')
    expect((page.get('#salon-texte').element as HTMLTextAreaElement).value).toBe('@Moussa ')
    await page.get('[data-test="regler"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="coups-de-main"]').exists()).toBe(false))
    expect(page.text()).toContain('Réglé grâce à Awa')
    page.unmount()
  })

  it('demander un coup de main l’épingle en haut du salon', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="mode-bloque"]').exists()).toBe(true))
    await page.get('[data-test="mode-bloque"]').trigger('click')
    await page.get('#salon-texte').setValue('Le webhook Wave ne répond pas en test')
    await page.get('[data-test="saisie"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="coup-de-main"]')).toHaveLength(2))
    page.unmount()
  })

  it("lance l'appel du cercle dans Jamii, sans quitter la page, puis le termine", async () => {
    installerFauxWebrtc()
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="lancer-appel"]').exists()).toBe(true))
    await page.get('[data-test="lancer-appel"]').trigger('click')
    // La fenêtre d'appel s'ouvre par-dessus la page ; le salon propose d'y revenir.
    await vi.waitFor(() => expect(document.body.querySelector('[data-test="fenetre-appel-cercle"]')).not.toBeNull())
    await vi.waitFor(() => expect(page.find('[data-test="revenir-appel"]').exists()).toBe(true))
    await page.get('[data-test="terminer-appel"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="lancer-appel"]').exists()).toBe(true))
    reinitialiserAppelCercle()
    vi.unstubAllGlobals()
    page.unmount()
  })
})

describe('le QG du cercle (refonte inspirée de Basecamp)', () => {
  it('un membre arrive au QG : le tour en bandeau et un aperçu de chaque outil', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}`)
    await vi.waitFor(() => expect(page.find('[data-test="qg"]').exists()).toBe(true))
    for (const o of ['salon', 'taches', 'points', 'decisions', 'calendrier', 'cercle']) expect(page.find(`[data-test="outil-${o}"]`).exists()).toBe(true)
    await vi.waitFor(() => expect(page.get('[data-test="outil-decisions"]').text()).toContain('On garde Flutter'))
    expect(page.get('[data-test="outil-salon"]').text()).toContain('Android 8')
    expect(page.find('[data-test="colline"]').exists()).toBe(true)
    page.unmount()
  })

  it('le point du soir : je réponds à la question du jour, ma réponse se range sous « aujourd’hui »', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=points`)
    await vi.waitFor(() => expect(page.find('[data-test="repondre-point"]').exists()).toBe(true))
    expect(page.text()).toContain('Sur quoi as-tu travaillé aujourd’hui\u202f?')
    await page.get('#op-fait').setValue('Le tableau des pesées')
    await page.get('#op-prevu').setValue('Les tests hors ligne')
    await page.get('[data-test="repondre-point"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="point-fait"]').exists()).toBe(true))
    expect(page.findAll('[data-test="jour-de-points"]')[0]!.text()).toContain('Le tableau des pesées')
    page.unmount()
  })

  it('les décisions : on en consigne une, elle passe en tête', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=decisions`)
    await vi.waitFor(() => expect(page.findAll('[data-test="decision"]')).toHaveLength(1))
    await page.get('#decision-texte').setValue('La démo est jeudi à 19 h')
    await page.get('[data-test="nouvelle-decision"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="decision"]')).toHaveLength(2))
    expect(page.findAll('[data-test="decision"]')[0]!.text()).toContain('La démo est jeudi à 19 h')
    page.unmount()
  })

  it('le calendrier : 14 jours, aujourd’hui marqué, et le planning des tours', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=calendrier`)
    await vi.waitFor(() => expect(page.find('[data-test="jours-du-tour"]').exists()).toBe(true))
    expect(page.findAll('[data-test="jours-du-tour"] li')).toHaveLength(14)
    expect(page.findAll('[data-test="jours-du-tour"] [aria-current="date"]')).toHaveLength(1)
    expect(page.findAll('[data-test="planning"] li')).toHaveLength(6)
    page.unmount()
  })

  it('un ancien lien « ?onglet=salon » mène toujours au salon', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?onglet=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="salon-cercle"]').exists()).toBe(true))
    page.unmount()
  })
})

describe("l'outil Tâches", () => {
  it('crée une tâche complète : titre, description en deux parties, priorité, échéance, responsable', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=taches`)
    await vi.waitFor(() => expect(page.find('[data-test="nouvelle-tache"]').exists()).toBe(true))
    await page.get('[data-test="nouvelle-tache"]').trigger('click')
    expect(page.get('[data-test="enregistrer-tache"]').attributes('disabled')).toBeDefined()
    await page.get('[data-test="champ-titre"]').setValue('Écran de connexion')
    await page.get('[data-test="champ-faire"]').setValue('Connexion par numéro de téléphone.')
    await page.get('[data-test="champ-fini"]').setValue('le code SMS arrive en moins d’une minute.')
    await page.get('[data-test="priorite-haute"]').setValue(true)
    await page.get('[data-test="champ-echeance"]').setValue('2026-11-20')
    await page.get('[data-test="champ-responsable"]').setValue('6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11')
    await page.get('[data-test="formulaire-tache"]').trigger('submit')

    // Haute priorité : elle passe en tête de la liste, avec son résumé et son responsable.
    await vi.waitFor(() => expect(page.findAll('[data-test="ligne-tache"]')[0]!.text()).toContain('Écran de connexion'))
    const ligne = page.findAll('[data-test="ligne-tache"]')[0]!
    expect(ligne.text()).toContain('Connexion par numéro de téléphone.')
    expect(ligne.text()).toContain('Haute')

    await ligne.get('[data-test="ouvrir-tache"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="fiche-tache"]').exists()).toBe(true))
    const fiche = page.get('[data-test="fiche-tache"]').text()
    expect(fiche).toContain('C’est fini quand…')
    expect(fiche).toContain('le code SMS arrive en moins d’une minute.')
    expect(fiche).toContain('Awa Diop')
    page.unmount()
  })

  it('les filtres « Les miennes » et « Libres », et livrer depuis la fiche', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=taches`)
    await vi.waitFor(() => expect(page.findAll('[data-test="ligne-tache"]').length).toBeGreaterThan(0))
    const ouvertes = page.findAll('[data-test="ligne-tache"]').length
    await page.get('[data-test="filtre-miennes"]').trigger('click')
    expect(page.find('[data-test="aucune-tache"]').exists()).toBe(true)
    await page.get('[data-test="filtre-libres"]').trigger('click')
    expect(page.findAll('[data-test="ligne-tache"]')).toHaveLength(ouvertes)

    await page.get('[data-test="ouvrir-tache"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="fiche-livrer"]').exists()).toBe(true))
    await page.get('[data-test="fiche-livrer"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="ligne-tache"]')).toHaveLength(ouvertes - 1))
    page.unmount()
  })
})

describe('le chef de projet du tour', () => {
  it('tient le calendrier : il pose une étape, elle apparaît sur son jour', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=calendrier`)
    await vi.waitFor(() => expect(page.findAll('[data-test="etape-jour"]').length).toBeGreaterThan(0))
    const avant = page.findAll('[data-test="etape-jour"]').length
    await page.get('#etape-titre').setValue('Test avec deux peseurs')
    await page.get('#etape-date').setValue(new Date().toISOString().slice(0, 10))
    await page.get('[data-test="ajouter-etape"]').trigger('submit')
    await vi.waitFor(() => expect(page.findAll('[data-test="etape-jour"]')).toHaveLength(avant + 1))
    expect(page.get('[data-test="etapes"]').text()).toContain('Test avec deux peseurs')
    page.unmount()
  })

  it("un autre membre voit le calendrier et les décisions, mais c'est le chef qui les tient", async () => {
    const original = await (await fetch(new URL(`/api/cercles/${ID_DAKAR}`, window.location.origin))).json()
    const kofi = original.membres[1].personne
    serveur.use(
      http.get('/api/cercles/:id', () =>
        HttpResponse.json({ ...original, tours: original.tours.map((x: { numero: number }, i: number) => (i === 0 ? { ...x, beneficiaire: kofi } : x)) }),
      ),
    )
    const cal = await ouvrir(`/cercles/${ID_DAKAR}?outil=calendrier`)
    await vi.waitFor(() => expect(cal.page.find('[data-test="etapes"]').exists()).toBe(true))
    expect(cal.page.find('[data-test="ajouter-etape"]').exists()).toBe(false)
    expect(cal.page.find('[data-test="atteindre-etape"]').exists()).toBe(false)
    expect(cal.page.text()).toContain(`Tenu par ${kofi.nom}`)
    cal.page.unmount()

    const dec = await ouvrir(`/cercles/${ID_DAKAR}?outil=decisions`)
    await vi.waitFor(() => expect(dec.page.find('[data-test="decisions-reservees"]').exists()).toBe(true))
    expect(dec.page.find('[data-test="nouvelle-decision"]').exists()).toBe(false)
    dec.page.unmount()
  })
})


describe('écrire dans le salon : mise en forme et pièces jointes', () => {
  it('met en forme le texte (gras, code) sans jamais interpréter de HTML', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=salon`)
    await vi.waitFor(() => expect(page.find('#salon-texte').exists()).toBe(true))
    await page.get('#salon-texte').setValue('**Important** : lance `flutter doctor` <b>ici</b>')
    await page.get('#salon-texte').trigger('keydown', { key: 'Enter' })
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]').at(-1)!.find('strong').exists()).toBe(true))
    const dernier = page.findAll('[data-test="message"]').at(-1)!
    expect(dernier.find('strong').text()).toBe('Important')
    expect(dernier.find('code').text()).toBe('flutter doctor')
    expect(dernier.find('b').exists()).toBe(false)
    expect(dernier.text()).toContain('<b>ici</b>')
    page.unmount()
  })

  it('joint une image : aperçu avant envoi, puis visible dans le message', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="choisir-fichiers"]').exists()).toBe(true))
    const entree = page.get('[data-test="choisir-fichiers"]').element as HTMLInputElement
    const image = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'maquette.png', { type: 'image/png' })
    Object.defineProperty(entree, 'files', { configurable: true, value: [image] })
    await page.get('[data-test="choisir-fichiers"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="piece-a-envoyer"]').exists()).toBe(true))
    expect(page.get('[data-test="piece-a-envoyer"]').text()).toContain('maquette.png')
    await page.get('[data-test="saisie"] [data-test="envoyer"]').trigger('click')
    await vi.waitFor(() => expect(page.findAll('[data-test="message"]').at(-1)!.find('[data-test="pieces-jointes"] img').exists()).toBe(true))
    expect(page.find('[data-test="piece-a-envoyer"]').exists()).toBe(false)
    page.unmount()
  })

  it('refuse tout de suite un fichier trop gros, sans l’envoyer', async () => {
    const { page } = await ouvrir(`/cercles/${ID_DAKAR}?outil=salon`)
    await vi.waitFor(() => expect(page.find('[data-test="choisir-fichiers"]').exists()).toBe(true))
    const entree = page.get('[data-test="choisir-fichiers"]').element as HTMLInputElement
    const gros = new File(['x'], 'enorme.pdf', { type: 'application/pdf' })
    Object.defineProperty(gros, 'size', { value: 11 * 1024 * 1024 })
    Object.defineProperty(entree, 'files', { configurable: true, value: [gros] })
    await page.get('[data-test="choisir-fichiers"]').trigger('change')
    await vi.waitFor(() => expect(page.find('[data-test="saisie"] [role="alert"]').text()).toContain('enorme.pdf'))
    expect(page.find('[data-test="piece-a-envoyer"]').exists()).toBe(false)
    page.unmount()
  })
})
