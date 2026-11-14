# 💬 Communautés façon Discord

**Responsable :** @rev22rse22 · **Suivi :** #155 · **Repris d'@Amar233-ui le 14 novembre** (le module `communautes` et ses écrans).

## But
Un développeur arrive, voit les communautés et leurs **salons**, discute par écrit et rejoint un **appel vocal ou vidéo**. C'est le cœur du thème de la CADEV : la communauté et l'entraide entre développeurs.

## Territoire
- API : `communautes`, `reseau`, `profils` ;
- écrans : `communautes`, `reseau`, `profils`, `design-system` ;
- contrats : `communautes.yaml`, `reseau.yaml`, `profils.yaml`.

## Ce qui existe déjà
- **Les communautés** : rejoindre, publier une question, une discussion ou « montre ton projet », répondre, accepter une réponse.
- **Les propositions de l'IA** : l'IA propose une réponse, un autre membre la valide. Le module publie l'événement `ReponseAcceptee` quand une réponse humaine est acceptée.
- **Le réseau** : les messages, les contacts et les notifications.

## Avant la soumission (dimanche 15, 12 h GMT)
1. **Les salons** d'une communauté, lus en direct.
2. **Les appels vocaux et vidéo** dans un salon. Choix technique arrêté samedi à 20 h GMT, à noter dans `docs/systalink-services.md`. Les pistes sont détaillées dans #155 : Jitsi, LiveKit sur DataCloud, ou WebRTC natif.
3. **Afficher les demandes publiées par le module freelance**, en écoutant l'événement `DemandePubliee`.
4. La demande #114 : la section « communautes » de l'export du compte.

## Terminé quand
Pendant la démo, un développeur ouvre le salon où la demande d'une PME est apparue, en discute, puis lance un appel avec un autre membre.
