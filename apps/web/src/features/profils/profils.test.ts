import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory } from 'vue-router'
import { serveur } from '../../test-setup'
import { moiDemo } from '../../mocks/handlers'
import { i18n } from '../../i18n'
import { creerRouteur } from '../../router'
import { chargerSession } from '../../session'
import App from '../../App.vue'
import { competencesPhares, couleurLangage, hoteLisible, ilYa, lienProfil, lireCompetences, pourcentage, urlDrapeau } from './profils'

async function ouvrir(chemin = '/profil') {
  ;(i18n.global.locale as unknown as { value: string }).value = 'fr'
  await chargerSession()
  const routeur = creerRouteur(createMemoryHistory())
  await routeur.push(chemin)
  const page = mount(App, { global: { plugins: [i18n, routeur] } })
  await vi.waitFor(() => expect(page.text()).not.toContain('Chargement'))
  return page
}

const probleme = (status: number, code: string, erreurs?: { champ: string; code: string }[]) =>
  HttpResponse.json({ type: `urn:jamii:erreur:${code}`, title: code, status, code, erreurs }, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('Mon profil', () => {
  it('sans profil, propose de le générer depuis GitHub, puis l’affiche, toujours privé', async () => {
    const page = await ouvrir()
    expect(page.find('[data-test="sans-profil"]').text()).toContain('Rien de privé n\'est lu')
    await page.get('[data-test="generer"]').trigger('click')
    await vi.waitFor(() => expect(page.find('[data-test="langages"]').exists()).toBe(true), { timeout: 5000 })

    expect(page.get('[data-test="langages"]').text()).toContain('Dart')
    expect(page.get('[data-test="langages"]').text()).toMatch(/75\s?%/)
    expect(page.get('[data-test="depots"]').text()).toContain('marche-facile')
    expect(page.get('[data-test="visibilite"]').text()).toContain('privé')
    expect(page.find('[data-test="projets-vide"]').exists()).toBe(true)
  })

  it('sans GitHub relié, propose « Relier mon GitHub », puis dit quand c’est fait ou pourquoi ça a échoué', async () => {
    serveur.use(http.get('/api/me', () => HttpResponse.json({ ...moiDemo, loginGithub: null, methodesConnexion: ['email'] })))
    let page = await ouvrir()
    await vi.waitFor(() => expect(page.find('[data-test="relier-github"]').exists()).toBe(true))
    expect(page.get('[data-test="relier-github"]').attributes('href')).toBe('/api/auth/github?lier=1')
    expect(page.find('[data-test="generer"]').exists()).toBe(false)
    page.unmount()

    page = await ouvrir('/profil?erreurGithub=github_deja_utilise')
    await vi.waitFor(() => expect(page.find('[data-test="erreur-lien"]').exists()).toBe(true))
    expect(page.get('[data-test="erreur-lien"]').text()).toContain('déjà relié à un autre compte')
    page.unmount()

    serveur.use(http.get('/api/me', () => HttpResponse.json(moiDemo)))
    page = await ouvrir('/profil?github=relie')
    await vi.waitFor(() => expect(page.find('[data-test="github-relie"]').exists()).toBe(true))
    expect(page.find('[data-test="generer"]').exists()).toBe(true)
  })

  it('la carte de compétences : rien de gardé d’avance, je retire ce qui est faux, puis je garde le reste avec ses preuves', async () => {
    const page = await profilPret()
    const carte = page.get('[data-test="carte-competences"]')
    expect(carte.text()).toContain('Proposé par l\'IA à partir de tes dépôts publics')
    expect(carte.text()).toContain('marche-facile')
    expect(page.get('[data-test="competences"]').text()).not.toContain('Confirmé')

    const cases = carte.findAll('[data-test="garder"]')
    expect(cases).toHaveLength(4)
    await cases[3]!.setValue(false)
    await carte.get('[data-test="garder-carte"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="competences"]').text()).toContain('Confirmé'))
    const publiques = page.get('[data-test="competences"]').text()
    expect(publiques).toContain('paiement-mobile')
    expect(publiques).not.toContain('sms')
  })

  it('publier puis rendre privé', async () => {
    const page = await profilPret()
    await page.get('[data-test="publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="visibilite"]').text()).toContain('publié'))
    await page.get('[data-test="publier"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="visibilite"]').text()).toContain('privé'))
  })

  it('modifier ce que je déclare, bloc par bloc : la ville, puis les compétences, s’ajoutent au profil', async () => {
    const page = await profilPret()
    await page.get('[data-test="crayon-lieu"]').trigger('click')
    const lieu = page.get('[data-test="formulaire"]')
    expect(lieu.text()).toContain('Tu modifies : Lieu et disponibilité')
    await lieu.findAll('input')[0]!.setValue('Thiès')
    await lieu.trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="message"]').text()).toBe('Ton profil est enregistré.'))
    expect(page.text()).toContain('Thiès')
    expect(page.find('[data-test="formulaire"]').exists()).toBe(false)

    await page.get('[data-test="crayon-competences"]').trigger('click')
    const competences = page.get('[data-test="formulaire"]')
    await competences.findAll('input').at(-1)!.setValue('UX design, Mobile Money')
    await competences.trigger('submit')
    await vi.waitFor(() => expect(page.get('[data-test="competences"]').text()).toContain('Mobile Money'))
  })

  it('un bloc n’envoie que ses propres champs : les autres blocs ne bougent pas', async () => {
    const page = await profilPret()
    let corps: Record<string, unknown> = {}
    serveur.use(
      http.patch('/api/me/profil', async ({ request }) => {
        corps = (await request.json()) as Record<string, unknown>
        return probleme(409, 'modification_concurrente')
      }),
    )
    await page.get('[data-test="crayon-liens"]').trigger('click')
    await page.get('[data-test="formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(Object.keys(corps).length).toBeGreaterThan(0))
    expect(Object.keys(corps).sort()).toEqual(['linkedin', 'siteWeb', 'version'])
  })

  it('un seul bloc est ouvert à la fois, et « Annuler » le referme sans rien enregistrer', async () => {
    const page = await profilPret()
    await page.get('[data-test="crayon-langues"]').trigger('click')
    expect(page.get('[data-test="formulaire"]').text()).toContain('Tu modifies : Langues')
    await page.get('[data-test="crayon-liens"]').trigger('click')
    expect(page.findAll('[data-test="formulaire"]')).toHaveLength(1)
    expect(page.get('[data-test="formulaire"]').text()).toContain('Tu modifies : Liens')
    await page.get('[data-test="annuler"]').trigger('click')
    expect(page.find('[data-test="formulaire"]').exists()).toBe(false)
    expect(page.find('[data-test="message"]').exists()).toBe(false)
  })

  it('si le profil a changé ailleurs (409), recharge la dernière version et le dit', async () => {
    const page = await profilPret()
    serveur.use(http.patch('/api/me/profil', () => probleme(409, 'modification_concurrente')))
    await page.get('[data-test="modifier"]').trigger('click')
    await page.get('[data-test="formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[data-test="message"]').text()).toContain('autre onglet'))
    expect(page.find('[data-test="formulaire"]').exists()).toBe(false)
  })

  it('une valeur refusée par l’API est signalée sur son champ', async () => {
    const page = await profilPret()
    serveur.use(http.patch('/api/me/profil', () => probleme(400, 'validation', [{ champ: 'ville', code: 'trop_long' }])))
    await page.get('[data-test="crayon-lieu"]').trigger('click')
    await page.get('[data-test="formulaire"]').trigger('submit')
    await vi.waitFor(() => expect(page.find('[aria-invalid="true"]').exists()).toBe(true))
  })

  it('l’anneau dit quelles preuves existent, sans jamais donner de note', async () => {
    serveur.use(
      http.get('/api/me/profil', () =>
        HttpResponse.json({
          utilisateurId: 'x', nom: 'Aïcha Kouassi', disponibilite: 'disponible', langages: [{ nom: 'Dart', part: 1 }],
          langues: [], competencesPhares: [], realisations: [],
          competences: [], depotsGithub: [], misAJourLe: '2026-11-11T10:00:00Z', publie: true, afficherResumeIa: true, version: 2,
          generation: { statut: 'terminee' }, projets: [],
          fiabilite: [{ type: 'tache_livree_a_temps', nombre: 11, sur: 12, contestees: 0 }],
        }),
      ),
    )
    const page = await ouvrir()
    const anneau = page.get('.anneau__svg')
    expect(anneau.attributes('aria-label')).toContain('tâches livrées à la date prévue')
    expect(anneau.attributes('aria-label')).not.toContain('réponses acceptées')
    expect(page.text()).toContain('Trait plein : vérifié par Jamii')
    expect(page.text()).toContain('Pointillé : déclaré par la personne')
    expect(page.text()).not.toMatch(/\d+\s?\/\s?100/)
  })

  it('montre les projets en ligne avec leur badge, et chaque point de fiabilité sans note globale', async () => {
    serveur.use(
      http.get('/api/me/profil', () =>
        HttpResponse.json({
          utilisateurId: 'x', nom: 'Aïcha Kouassi', disponibilite: 'disponible', langages: [{ nom: 'Dart', part: 1 }],
          langues: [], competencesPhares: [], realisations: [],
          competences: [], depotsGithub: [], misAJourLe: '2026-11-11T10:00:00Z', publie: true, afficherResumeIa: true, version: 2,
          generation: { statut: 'terminee' },
          projets: [{ projetId: 'p1', titre: 'Marché Facile', role: 'porteur', paysEquipe: ['CI', 'KE'], enLigne: true, hebergeur: 'datacloud' }],
          fiabilite: [
            { type: 'tache_livree_a_temps', nombre: 11, sur: 12, contestees: 0 },
            { type: 'reponse_acceptee', nombre: 7, contestees: 1 },
          ],
        }),
      ),
    )
    const page = await ouvrir()
    expect(page.get('[data-test="projets"]').text()).toContain('Hébergé sur Systalink')
    const points = page.get('[data-test="fiabilite"]').text()
    expect(points).toContain('11 sur 12')
    expect(points).toContain('tâches livrées à la date prévue')
    expect(points).toContain('7réponses acceptées')
    expect(points).toContain('dont 1 en cours de vérification')
    expect(page.text()).toContain('Pourquoi on peut compter sur Aïcha')
  })

  it('si GitHub ne répond pas, le dit et garde le profil précédent', async () => {
    serveur.use(
      http.get('/api/me/profil', () =>
        HttpResponse.json({
          utilisateurId: 'x', nom: 'Awa Diop', disponibilite: 'disponible', langages: [{ nom: 'Dart', part: 1 }],
          langues: [], competencesPhares: [], realisations: [],
          competences: [], projets: [], depotsGithub: [], fiabilite: [], misAJourLe: '2026-11-11T10:00:00Z',
          publie: false, afficherResumeIa: true, version: 3,
          generation: { statut: 'echec', erreur: 'github_indisponible' },
        }),
      ),
    )
    const page = await ouvrir()
    expect(page.text()).toContain('GitHub ne répond pas')
    expect(page.get('[data-test="langages"]').text()).toContain('Dart')
  })
})

describe('profil professionnel (#159)', () => {
  const pro = {
    utilisateurId: 'b2c3', nom: 'Kofi Mensah', titre: 'Développeur backend · Java', pays: 'GH', ville: 'Accra',
    disponibilite: 'disponible', linkedin: 'https://www.linkedin.com/in/kofi', siteWeb: 'https://kofi.dev',
    langues: ['en', 'ak'], competencesPhares: ['spring-boot'], loginGithub: 'kofi',
    realisations: [{ titre: 'Caisse en ligne', role: 'Développeur', annee: 2025, technologies: ['java'], url: 'https://caisse.exemple.gh' }],
    langages: [], competences: [{ competence: 'java', source: 'github' }], projets: [], depotsGithub: [], fiabilite: [],
    misAJourLe: '2026-11-11T10:00:00Z',
  }

  it('montre le titre, le drapeau, les liens, les langues et les réalisations « Déclaré »', async () => {
    serveur.use(http.get('/api/profils/:id', () => HttpResponse.json(pro)))
    const page = await ouvrir('/p/b2c3')
    await vi.waitFor(() => expect(page.find('[data-test="identite"]').exists()).toBe(true))
    expect(page.get('[data-test="titre"]').text()).toBe('Développeur backend · Java')
    expect(page.get('[data-test="drapeau"]').attributes('src')).toContain('gh.svg')
    expect(page.get('[data-test="liens"]').text()).toContain('LinkedIn')
    expect(page.get('[data-test="liens"]').text()).toContain('kofi.dev')
    expect(page.get('[data-test="langues"]').text()).toContain('Anglais')
    const realisations = page.get('[data-test="realisations"]').text()
    expect(realisations).toContain('Caisse en ligne')
    expect(realisations).toContain('Déclaré')
    expect(page.get('[data-test="phares"]').text()).toContain('spring-boot')
  })

  it('un visiteur ne voit aucun crayon ni aucune barre de modification', async () => {
    serveur.use(http.get('/api/profils/:id', () => HttpResponse.json(pro)))
    const page = await ouvrir('/p/a1b2')
    await vi.waitFor(() => expect(page.find('[data-test="identite"]').exists()).toBe(true))
    expect(page.find('.crayon').exists()).toBe(false)
    expect(page.find('[data-test="barre-proprio"]').exists()).toBe(false)
  })

  it('un profil privé ou inconnu le dit, sans rien révéler', async () => {
    serveur.use(http.get('/api/profils/:id', () => probleme(404, 'introuvable')))
    const page = await ouvrir('/p/inconnu')
    await vi.waitFor(() => expect(page.find('[data-test="introuvable"]').exists()).toBe(true))
  })

  it("propose d'écrire à la personne et de se mettre en contact", async () => {
    serveur.use(http.get('/api/profils/:id', () => HttpResponse.json(pro)))
    const page = await ouvrir('/p/b2c3')
    await vi.waitFor(() => expect(page.find('[data-test="ecrire"]').exists()).toBe(true))
    expect(page.get('[data-test="ecrire"]').text()).toBe('Écrire à Kofi')
    expect(page.get('[data-test="ecrire"]').attributes('href')).toBe('/messages/b2c3')
  })

  it("montre au moins qui c'est, avec les actions, quand le profil n'est pas publié", async () => {
    const kofi = 'c1a00000-0000-4000-8000-000000000002'
    const page = await ouvrir(`/p/${kofi}`)
    await vi.waitFor(() => expect(page.find('[data-test="non-publie"]').exists()).toBe(true))
    expect(page.get('[data-test="non-publie"] h1').text()).toBe('Kofi Yao')
    expect(page.get('[data-test="ecrire"]').attributes('href')).toBe(`/messages/${kofi}`)
    await vi.waitFor(() => expect(page.find('[data-test="contact"]').text()).toBe('Ajouter aux contacts'))
    await page.get('[data-test="contact"]').trigger('click')
    await vi.waitFor(() => expect(page.get('[data-test="contact"]').text()).toBe('Demande envoyée · annuler'))
  })

  it("n'affiche pas les actions sur son propre profil", async () => {
    serveur.use(http.get('/api/profils/:id', () => HttpResponse.json({ ...pro, utilisateurId: '6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11' })))
    const page = await ouvrir('/p/6f1c2a4e-8b7d-4c3e-9a10-2b5d7e9f0a11')
    await vi.waitFor(() => expect(page.find('[data-test="identite"]').exists()).toBe(true))
    expect(page.find('[data-test="actions-profil"]').exists()).toBe(false)
  })

  it('montre « Infos » avec le résumé de l’IA, l’activité GitHub et les recommandations des coéquipiers', async () => {
    serveur.use(
      http.get('/api/profils/:id', () =>
        HttpResponse.json({
          ...pro, bio: 'Je construis des API.', resumeIa: 'Développeur Java, surtout des API de paiement.',
          depotsGithub: [{ nom: 'caisse', url: 'https://github.com/kofi/caisse', etoiles: 12, langage: 'Java', misAJourLe: '2026-11-01T10:00:00Z' }],
          langages: [{ nom: 'Java', part: 1 }],
          fiabilite: [{ type: 'avis_coequipier', nombre: 1, contestees: 0 }],
        }),
      ),
      http.get('/api/profils/:id/preuves', () =>
        HttpResponse.json({
          elements: [{ id: 'a1', type: 'avis_coequipier', source: { module: 'projets', id: 'p1', titre: 'Marché Facile' }, detail: 'Toujours à l’heure.', auteur: { id: 'c3', nom: 'Awa Diop' }, contestee: false, creeLe: '2026-11-10T10:00:00Z' }],
          curseurSuivant: null,
        }),
      ),
    )
    const page = await ouvrir('/p/b2c3')
    await vi.waitFor(() => expect(page.find('[data-test="avis"]').exists()).toBe(true))
    expect(page.get('[data-test="liens"]').text()).toContain('★ 12 étoiles')
    expect(page.get('[data-test="a-propos"]').text()).toContain('Résumé proposé par l')
    expect(page.get('[data-test="avis"]').text()).toContain('Toujours à l’heure.')
    expect(page.get('[data-test="depots"]').text()).toContain('Kofi a mis à jour ce dépôt')
    expect(page.get('[data-test="avis"]').text()).toContain('Ont travaillé ensemble sur Marché Facile')
  })

  it('le lien public et le drapeau se construisent sans erreur', () => {
    expect(lienProfil('abc', 'https://jamii.exemple')).toBe('https://jamii.exemple/p/abc')
    expect(urlDrapeau('SN')).toContain('sn.svg')
    expect(urlDrapeau('xx')).toBeNull()
    expect(hoteLisible('https://www.kofi.dev/')).toBe('kofi.dev')
    expect(competencesPhares({ competencesPhares: [], competences: [{ competence: 'dart', source: 'github' }] })).toEqual(['dart'])
  })
})

describe('outils', () => {
  it('la part d’un langage s’écrit en pourcentage arrondi', () => {
    expect(pourcentage(0.745, 'en')).toBe('75%')
  })
  it('les compétences saisies : sans vides ni doublons', () => {
    expect(lireCompetences(' Flutter, ,firebase,Flutter ')).toEqual(['Flutter', 'firebase'])
  })
  it('chaque langage a sa couleur, stable même hors de la table', () => {
    expect(couleurLangage('TypeScript')).toBe('#3178C6')
    expect(couleurLangage('Zig')).toBe(couleurLangage('zig'))
    expect(couleurLangage(null)).toMatch(/^hsl\(/)
  })
  it('la mise à jour d’un dépôt se dit en temps relatif', () => {
    const maintenant = Date.parse('2026-11-16T12:00:00Z')
    expect(ilYa('2026-11-13T12:00:00Z', 'fr', maintenant)).toBe('il y a 3 jours')
    expect(ilYa('2026-09-01T12:00:00Z', 'en', maintenant)).toBe('3 months ago')
    expect(ilYa(null, 'fr', maintenant)).toBeNull()
  })
})

async function profilPret() {
  const page = await ouvrir()
  await page.get('[data-test="generer"]').trigger('click')
  await vi.waitFor(() => expect(page.find('[data-test="langages"]').exists()).toBe(true), { timeout: 5000 })
  return page
}
