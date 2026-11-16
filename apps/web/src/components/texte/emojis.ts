/*
 * Les emojis de Jamii (#157) : un choix de ~170 emojis courants, rangés par catégorie et retrouvables par des mots
 * français et anglais, et la conversion des smileys tapés (« :) » devient « 🙂 »).
 * Pas de bibliothèque : les emojis s'affichent avec la police du système, rien à télécharger, aucune licence en jeu.
 */

export type Emoji = { e: string; mots: string }
export type Categorie = { cle: 'visages' | 'gestes' | 'coeurs' | 'tech' | 'fete' | 'nature'; icone: string; emojis: Emoji[] }

// Chaque ligne : l'emoji, puis ses mots-clés (sans accents, français et anglais).
const liste = (texte: string): Emoji[] =>
  texte.trim().split('\n').map((ligne) => {
    const [e, ...mots] = ligne.trim().split(' ')
    return { e: e!, mots: mots.join(' ') }
  })

export const CATEGORIES: Categorie[] = [
  {
    cle: 'visages',
    icone: '😀',
    emojis: liste(`
      😀 sourire content grin smile happy
      😃 sourire joie smiley happy
      😄 rire joie heureux laugh smile
      😁 sourire dents grin beam
      😆 rire mdr lol laughing
      😅 rire gene sueur sweat
      🤣 mdr ptdr rire rofl
      😂 rire larmes mdr joy lol
      🙂 sourire leger slight smile
      🙃 inverse ironie upside down
      😉 clin oeil wink
      😊 timide content blush
      😇 ange innocent angel
      🥰 amour coeurs love
      😍 amoureux yeux coeur heart eyes
      🤩 waouh etoiles star struck
      😘 bisou kiss
      😋 miam delicieux yum
      😛 langue tongue
      😜 langue clin oeil crazy
      🤪 fou zany crazy
      🤑 argent money
      🤗 calin hug
      🤭 oups main bouche oops
      🤫 chut secret shush
      🤔 reflechir hmm think thinking
      🤐 bouche cousue zip
      🤨 sourcil doute suspicious
      😐 neutre neutral
      😑 blase expressionless
      😶 sans voix silence
      😏 narquois smirk
      😒 bof agace unamused
      🙄 yeux ciel eye roll
      😬 grimace gene grimace
      😌 soulage relieved
      😔 pensif triste pensive
      😪 sommeil sleepy
      😴 dodo dormir sleep
      😷 masque malade mask sick
      🤒 malade fievre sick
      🤯 choque explosion mind blown
      🥳 fete anniversaire party
      😎 cool lunettes sunglasses
      🤓 geek nerd lunettes
      🧐 monocle examiner
      😕 confus perplexe confused
      😟 inquiet worried
      🙁 triste sad frown
      😮 surpris waouh wow surprised
      😲 stupefait astonished
      🥺 supplier yeux pleading
      😢 pleurer triste cry
      😭 pleurer fort sob
      😱 peur cri scream
      😤 fier colere triumph
      😡 colere enerve angry
      🤬 insulte rage cursing
      😈 diable malin devil
      💀 mort crane skull dead
      💩 caca poop
      🤡 clown
      👻 fantome ghost
      👽 alien extraterrestre
      🤖 robot bot ia ai
    `),
  },
  {
    cle: 'gestes',
    icone: '👍',
    emojis: liste(`
      👍 pouce ok oui bien like thumbs up
      👎 pouce bas non dislike thumbs down
      👌 parfait ok perfect
      ✌️ victoire paix peace
      🤞 croiser doigts chance luck
      🤟 love you
      🤘 rock
      🤙 appelle moi call
      👈 gauche left
      👉 droite right
      👆 haut up
      👇 bas down
      ☝️ attention index
      ✋ main stop hand
      👋 coucou salut bonjour hello wave
      👏 bravo applaudir clap
      🙌 hourra celebrer raise hands
      👐 mains ouvertes open hands
      🤲 paumes palms
      🤝 accord poignee main deal handshake
      🙏 merci priere svp thanks please pray
      ✍️ ecrire write
      💪 force muscle strong
      🫡 salut respect salute
      🧠 cerveau idee brain
      👀 regarder yeux look eyes
      🫶 coeur mains heart hands
    `),
  },
  {
    cle: 'coeurs',
    icone: '❤️',
    emojis: liste(`
      ❤️ coeur amour rouge love heart red
      🧡 coeur orange heart
      💛 coeur jaune heart
      💚 coeur vert heart
      💙 coeur bleu heart
      💜 coeur violet heart
      🖤 coeur noir heart
      🤍 coeur blanc heart
      💔 coeur brise broken heart
      💕 coeurs hearts
      💖 coeur brillant sparkling heart
      💯 cent parfait hundred
      💢 colere anger
      💥 boum explosion boom
      💫 etourdi dizzy
      💬 bulle message discussion speech
      💭 pensee thought
      💤 dodo sleep zzz
      ✅ fait valide oui done check
      ❌ non faux erreur no cross
      ⚠️ attention warning
      ❓ question
      ❗ important exclamation
      ⭐ etoile star
      ✨ brillant magie sparkles
      🔥 feu top fire lit
      ⚡ eclair rapide flash
    `),
  },
  {
    cle: 'tech',
    icone: '💻',
    emojis: liste(`
      💻 ordinateur portable code laptop
      🖥️ ecran desktop
      ⌨️ clavier keyboard
      🖱️ souris mouse
      📱 telephone mobile phone
      ☁️ cloud nuage
      🌐 web internet monde
      🔒 securite cadenas lock secure
      🔑 cle key
      🛠️ outils reparer tools
      ⚙️ reglage engrenage settings gear
      🐛 bug insecte
      🧪 test experience
      🚀 deployer fusee lancer launch rocket ship
      📦 paquet livraison package
      🗄️ base donnees serveur database
      📊 graphique stats chart
      📈 hausse croissance growth
      📉 baisse decline
      📝 note ecrire memo
      📌 epingle pin
      📎 trombone piece jointe clip
      🔗 lien link
      📅 calendrier date calendar
      ⏰ reveil heure alarm
      ⏳ attente sablier wait
      💡 idee ampoule idea
      🎯 objectif cible target goal
      🏆 trophee victoire trophy win
      🥇 premier or medal
      💰 argent money
      💳 carte paiement card
      📣 annonce megaphone
      🔔 notification cloche bell
      🧩 puzzle piece
      🤖 robot ia bot ai
    `),
  },
  {
    cle: 'fete',
    icone: '🎉',
    emojis: liste(`
      🎉 fete bravo felicitations party tada
      🎊 confettis
      🎂 gateau anniversaire birthday cake
      🎁 cadeau gift
      🎈 ballon balloon
      🥂 trinquer cheers
      🍾 champagne
      ☕ cafe coffee
      🍵 the tea
      🍕 pizza
      🍔 burger
      🍟 frites fries
      🍗 poulet chicken
      🍚 riz rice
      🍲 plat ragout stew
      🥭 mangue mango
      🍌 banane banana
      🍉 pastèque pasteque watermelon
      ⚽ foot football soccer
      🏀 basket basketball
      🎮 jeu jeux video game
      🎵 musique music
      🎧 casque ecouter headphones
      🎤 micro chanter mic
      📷 photo camera
      🎬 film cinema movie
    `),
  },
  {
    cle: 'nature',
    icone: '🌍',
    emojis: liste(`
      🌍 afrique terre monde africa world
      🌞 soleil sun
      🌙 lune nuit moon
      ⭐ etoile star
      🌈 arc en ciel rainbow
      🌧️ pluie rain
      ⛈️ orage storm
      🌊 vague mer wave
      🌴 palmier palm
      🌳 arbre baobab tree
      🌱 pousse debut grow seedling
      🌸 fleur flower
      🦁 lion
      🐘 elephant
      🦒 girafe giraffe
      🦓 zebre zebra
      🐆 leopard
      🦜 perroquet parrot
      🐐 chevre goat
      🐫 chameau camel
      🐢 tortue lent turtle slow
      🦊 renard fox
      🐶 chien dog
      🐱 chat cat
      🐝 abeille travail bee
      🦋 papillon butterfly
    `),
  },
]

export const TOUS: Emoji[] = CATEGORIES.flatMap((c) => c.emojis).filter((x, i, l) => l.findIndex((y) => y.e === x.e) === i)

const normaliser = (texte: string) => texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

/** Les emojis dont un mot commence par la recherche (« rir » trouve 😆 et 😂). */
export function chercherEmojis(recherche: string, max = 60): Emoji[] {
  const q = normaliser(recherche)
  if (!q) return []
  return TOUS.filter((x) => x.e === recherche.trim() || x.mots.split(' ').some((m) => m.startsWith(q))).slice(0, max)
}

// ---------------------------------------------------------------- les smileys tapés

/** « :) » devient « 🙂 ». Seulement quand le smiley est un mot à lui seul : « http://… » ou « (8) » ne bougent pas. */
export const SMILEYS: Record<string, string> = {
  ':)': '🙂', ':-)': '🙂', '(:': '🙂',
  ':(': '🙁', ':-(': '🙁',
  ';)': '😉', ';-)': '😉',
  ':D': '😄', ':-D': '😄',
  'xD': '😆', 'XD': '😆',
  ':P': '😛', ':p': '😛', ':-P': '😛', ':-p': '😛',
  ':o': '😮', ':O': '😮', ':-o': '😮', ':-O': '😮',
  ":'(": '😢', ':|': '😐', ':-|': '😐', ':/': '😕', ':-/': '😕',
  ':*': '😘', ':-*': '😘', '^^': '😊', '^_^': '😊',
  '<3': '❤️', '</3': '💔', 'B)': '😎', 'B-)': '😎', ':@': '😡',
}

/** Est-on dans du code (`…` ou bloc ```) à cette position ? Là, on ne convertit rien. */
function dansDuCode(texte: string, position: number): boolean {
  const avant = texte.slice(0, position)
  const blocs = avant.split('```').length - 1
  if (blocs % 2 === 1) return true
  const ligne = avant.slice(avant.lastIndexOf('\n') + 1).replaceAll('```', '')
  return (ligne.split('`').length - 1) % 2 === 1
}

export type Conversion = { texte: string; curseur: number; debut: number; avant: string; apres: string }

/**
 * Convertit le smiley qui finit juste avant `fin` (le mot entre le dernier espace et `fin`).
 * `null` s'il n'y a rien à convertir.
 */
export function convertirSmiley(texte: string, fin: number, curseur = fin): Conversion | null {
  let debut = fin
  while (debut > 0 && !/\s/.test(texte[debut - 1]!)) debut--
  const mot = texte.slice(debut, fin)
  const emoji = SMILEYS[mot]
  if (!emoji || dansDuCode(texte, debut)) return null
  const nouveau = texte.slice(0, debut) + emoji + texte.slice(fin)
  return { texte: nouveau, curseur: curseur + emoji.length - mot.length, debut, avant: mot, apres: emoji }
}

/** Juste après une frappe : si on vient de taper un espace ou un saut de ligne après un smiley, on le convertit. */
export function convertirEnTapant(texte: string, curseur: number): Conversion | null {
  if (curseur < 2 || !/\s/.test(texte[curseur - 1]!)) return null
  return convertirSmiley(texte, curseur - 1, curseur)
}

/** À l'envoi : un smiley tout à la fin du message est converti aussi. */
export function convertirALaFin(texte: string): string {
  const fin = texte.trimEnd().length
  return convertirSmiley(texte, fin)?.texte ?? texte
}

/** Un message fait seulement d'emojis (3 au plus) : on peut l'afficher en grand. */
export function seulementDesEmojis(texte: string): boolean {
  const t = texte.trim()
  if (!t || t.length > 24) return false
  const graphemes = typeof Intl !== 'undefined' && 'Segmenter' in Intl
    ? [...new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(t.replace(/\s+/g, ''))].map((s) => s.segment)
    : [...t.replace(/\s+/g, '')]
  return graphemes.length <= 3 && graphemes.every((g) => /\p{Extended_Pictographic}/u.test(g))
}
