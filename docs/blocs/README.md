# Les modules

> **Depuis le samedi 14 novembre 2026, sur décision de l'équipe : un module par personne.** Chacun possède son module de bout en bout (API, écran, contrat, tests) et avance sans attendre les autres.

| Pseudo GitHub | Module | Plan | Suivi |
|---|---|---|---|
| `rev22rse22` | 💬 **Communautés façon Discord** : salons, messages, appels vocaux et vidéo, profils, design system | [communautes.md](communautes.md) | [#155](https://github.com/algabah2004/jamii/issues/155) |
| `algabah2004` | 🤖 **Agents IA et Systalink** : agents, mise en ligne DataCloud, espace Systalink. Il reste chef de projet | [agents-systalink.md](agents-systalink.md) | [#156](https://github.com/algabah2004/jamii/issues/156) |
| `Amar233-ui` | 🔁 **Cercles et collaboration** : la Tech Tontine, Gitea, tâches et jalons, vitrine, ossature du front | [cercles.md](cercles.md) | [#157](https://github.com/algabah2004/jamii/issues/157) |
| `sowibrr` | 🧾 **Freelance** : du besoin au cahier des charges publié, avec l'agent IA. Il garde le socle de l'API | [freelance.md](freelance.md) | [#158](https://github.com/algabah2004/jamii/issues/158) |

## L'histoire qui relie les 4 modules (la démo)

Le thème de la CADEV : **la communauté et la collaboration entre développeurs.**

1. **Freelance (Sow)** : une PME décrit son besoin. L'agent IA l'aide à le structurer et à rédiger le cahier des charges, puis elle publie sa demande.
2. **Communautés (Fadel)** : la demande apparaît dans un salon. Les développeurs en discutent par écrit, puis en appel.
3. **Cercles (Amar)** : ils forment un cercle et construisent le projet ensemble, sur le dépôt Gitea du tour, avec les tâches et les jalons.
4. **Agents IA et Systalink (Alga)** : le projet est déployé sur DataCloud. Il passe dans la vitrine, et l'espace Systalink le compte.

Chaque passage d'un module à l'autre se fait par un **contrat** ou par un **événement public**, jamais en lisant le code ou les tables d'un autre (voir [`docs/equipe.md`](../equipe.md)).

Chaque responsable **possède** son fichier de plan et le fait évoluer par PR. Le chef de projet valide le but et le critère « terminé quand ».

Les anciens plans de blocs (jusqu'au 14 novembre) restent pour l'historique : [socle-api.md](socle-api.md), [design-profils.md](design-profils.md) et [ia-systalink.md](ia-systalink.md).
