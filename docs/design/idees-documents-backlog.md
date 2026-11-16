# Documents et backlog de l’idée d’un cercle

Le formulaire d’idée peut joindre un cahier des charges et importer un backlog CSV. Pendant l’édition, il prend toute la largeur disponible ; le document et l’aperçu du backlog se présentent côte à côte sur grand écran.

Le cahier des charges utilise l’API de fichiers existante, avec une destination `cercleId`. Son propriétaire le prévisualise avant enregistrement. Après enregistrement, les membres du cercle le consultent dans « Voir le dossier du projet ». Les autres comptes reçoivent 404 même avec le lien, les réponses utilisent no-store et le fichier ne peut être joint à un autre contexte. La suppression du cercle efface ses documents ; celle du propriétaire efface ses fichiers associés.

Le CSV se lit dans le navigateur ; son backlog normalisé est enregistré côté serveur avec l’idée. Le fichier CSV brut n’est pas conservé. Formats : UTF-8 avec ou sans BOM, virgule ou point-virgule, cellules citées et descriptions sur plusieurs lignes. Une colonne titre est requise ; description, priorité et échéance sont facultatives. Les en-têtes anglais sont reconnus. Priorités haute/normale/basse ou high/normal/low ; date AAAA-MM-JJ. Le modèle se télécharge depuis le formulaire. Limites : 1 Mo pour le CSV, 100 tâches, titre de 3 à 160 caractères, description de 2 000 caractères. Un fichier invalide ne remplace pas le backlog précédent et empêche l’enregistrement jusqu’à sa correction ou l’annulation de cet import.

Le projet du membre est créé seulement à l’ouverture de son tour. Son backlog devient alors les tâches du projet, dans le même ordre, à faire et non attribuées. Cette création et l’ouverture du tour partagent la même transaction et le verrou existant du cercle : un nouveau clic ne crée pas de doublons. Réenregistrer l’idée remplace la préparation ; après tirage elle est figée, tandis que les tâches créées restent modifiables par l’équipe avec les outils existants.

Un import déterministe est retenu pour les tâches déjà explicites du CSV. Une extraction par IA du cahier des charges demanderait une validation distincte et n’est pas nécessaire ici.

Vérification : cas ciblés du parseur CSV, parcours existant du cercle, accessibilité du formulaire, confidentialité du document et génération transactionnelle des tâches sur PostgreSQL, export, frontières entre modules et compilation. Vérification manuelle dans le navigateur avec un document texte et trois tâches CSV, puis sauvegarde et réouverture du dossier. Le dépôt d’idée renvoie un membre : la page recharge désormais le cercle avant de l’afficher.

Pistes d’amélioration : permettre de mapper des colonnes personnalisées d’autres outils de backlog. Les fichiers utilisent pour l’instant l’interface de stockage PostgreSQL existante.
