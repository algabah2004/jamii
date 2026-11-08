#!/usr/bin/env bash
# Configure le dépôt GitHub de l'équipe en une seule commande.
# Prérequis : gh installé et connecté (gh auth login), et scripts/equipe.conf rempli.
# Usage : bash scripts/setup-github.sh        (à lancer depuis la racine du dépôt)
# On peut le relancer sans risque : ce qui existe déjà est ignoré ou mis à jour.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/equipe.conf

for v in LEAD FRONT BACK INTEG DESIGN; do
  [[ -n "${!v}" ]] || { echo "❌ Remplis $v dans scripts/equipe.conf"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "❌ Lance d'abord : gh auth login"; exit 1; }

REPO="$LEAD/$REPO_NAME"
ok()   { echo "  ✅ $*"; }
warn() { echo "  ⚠️  $*"; }

echo "▶ 1. CODEOWNERS (le chef de projet relit tout)"
mkdir -p .github
printf '# Toute PR doit être approuvée par le chef de projet\n* @%s\n' "$LEAD" > .github/CODEOWNERS
sed -i "s/@LEAD/@$LEAD/; s/@FRONT/@$FRONT/; s/@BACK/@$BACK/; s/@INTEG/@$INTEG/; s/@DESIGN/@$DESIGN/" docs/equipe.md
ok "CODEOWNERS et docs/equipe.md mis à jour"

echo "▶ 2. Commit initial et création du dépôt $REPO"
[[ -d .git ]] || git init -q -b main
git add -A
git diff --cached --quiet || git commit -q -m "chore: initialisation du projet Jamii (docs, organisation, modèles)"
if gh repo view "$REPO" >/dev/null 2>&1; then
  ok "le dépôt existe déjà"
  git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$REPO.git"
  git push -u origin main
else
  gh repo create "$REPO" --"$REPO_VISIBILITY" --source=. --remote=origin --push \
    --description "Plateforme d'échange des développeurs africains — CADEV 2026" && ok "dépôt créé"
fi
gh repo edit "$REPO" --enable-projects --enable-issues --delete-branch-on-merge \
  --enable-squash-merge --enable-merge-commit=false --enable-rebase-merge=false >/dev/null && ok "options du dépôt"

echo "▶ 3. Invitation des collaborateurs (droit d'écriture, pas d'admin)"
for u in "$FRONT" "$BACK" "$INTEG" "$DESIGN"; do
  gh api -X PUT "repos/$REPO/collaborators/$u" -f permission=push >/dev/null \
    && ok "invitation envoyée à $u" || warn "échec pour $u (pseudo correct ?)"
done

echo "▶ 4. Étiquettes"
lab() { gh label create "$1" --color "$2" --description "$3" --repo "$REPO" --force >/dev/null && ok "$1"; }
lab "type: fonctionnalité" 1D76DB "Nouvelle fonctionnalité"
lab "type: bug"            D73A4A "Quelque chose ne marche pas"
lab "type: doc"            0075CA "Documentation"
lab "type: recherche"      5319E7 "Tester, se renseigner, comparer"
lab "type: proposition"    C5DEF5 "Idée à valider par le chef de projet"
lab "domaine: frontend"    FBCA04 ""
lab "domaine: backend"     F9A03F ""
lab "domaine: ia-systalink" 0E8A16 "Yokk, MCP, DataCloud, Orion…"
lab "domaine: design"      E99695 ""
lab "domaine: démo"        BFD4F2 ""
lab "priorité: haute"      B60205 ""
lab "priorité: moyenne"    FBCA04 ""
lab "priorité: basse"      C2E0C6 ""
lab "bloqué"               000000 "Prévenir le chef de projet"

echo "▶ 5. Jalons"
ms() { gh api "repos/$REPO/milestones" -f title="$1" -f description="$2" >/dev/null 2>&1 && ok "$1" || ok "$1 (existe déjà)"; }
ms "S1 · Fondations"          "Comptes Systalink testés, stack choisie, maquettes, squelette de l'appli"
ms "S2 · Profils et communautés" "Connexion GitHub, profil généré, communautés avec échanges"
ms "S3 · Demandes et déploiement" "Demandes des PME, équipes, déploiement DataCloud"
ms "Démo CADEV"               "Démo de 3 minutes, vidéo, soumission"

echo "▶ 6. Protection de la branche main"
cat > /tmp/jamii-protect.json <<EOF
{"required_status_checks":null,"enforce_admins":false,
 "required_pull_request_reviews":{"required_approving_review_count":1,"require_code_owner_reviews":true,"dismiss_stale_reviews":true},
 "restrictions":null,"allow_force_pushes":false,"allow_deletions":false}
EOF
gh api -X PUT "repos/$REPO/branches/main/protection" --input /tmp/jamii-protect.json >/dev/null 2>&1 \
  && ok "main protégée : PR obligatoire et approbation du chef de projet" \
  || warn "protection impossible (un dépôt privé demande GitHub Pro, gratuit pour les étudiants). La règle reste écrite dans CONTRIBUTING.md"
rm -f /tmp/jamii-protect.json

echo "▶ 7. Tâches de départ (S1)"
issue() { # titre | assigné | étiquettes | corps
  if gh issue list --repo "$REPO" --state all --search "\"$1\" in:title" --json title -q '.[].title' | grep -qxF "$1"; then
    ok "existe déjà : $1"; return; fi
  gh issue create --repo "$REPO" --title "$1" --assignee "$2" --label "$3" \
    --milestone "S1 · Fondations" --body "$4" >/dev/null && ok "$1 → @$2"
}
issue "Obtenir le règlement officiel de la CADEV" "$LEAD" "type: recherche,priorité: haute" \
"Écrire à cadev@systalink.com (ou WhatsApp +221 76 660 40 41) pour avoir : dates, taille des équipes, livrables, critères du jury, pondération entre public et jury.

**Terminé quand** les réponses sont ajoutées dans docs/concept.md."
issue "Inscrire l'équipe sur la plateforme CADEV" "$LEAD" "type: recherche,priorité: haute" \
"https://platform.systalink.com/sign-up?from=cadev-landing : créer le compte, puis inviter les 4 membres (étape « Construis ta team »).

**Terminé quand** les 5 membres apparaissent dans l'équipe CADEV."
issue "Déployer un hello world GitHub sur DataCloud" "$INTEG" "type: recherche,domaine: ia-systalink,priorité: haute" \
"- [ ] Créer un compte Systalink
- [ ] Déployer une petite appli depuis un dépôt GitHub
- [ ] Ouvrir une PR et vérifier si une préview est créée automatiquement
- [ ] Déployer un hello world **Spring Boot (Java 21)** et un hello world **FastAPI (Python)**. Sont-ils supportés ? Avec ou sans Docker ?
- [ ] Créer une base PostgreSQL gérée : y a-t-il des sauvegardes automatiques ?
- [ ] Noter les étapes, le temps, les limites gratuites et les frameworks supportés

**Terminé quand** la procédure est écrite dans docs/systalink-services.md, avec l'URL de l'appli en ligne."
issue "Tester le serveur MCP et l'API Systalink" "$INTEG" "type: recherche,domaine: ia-systalink,priorité: haute" \
"- [ ] Générer une clé sur platform.systalink.com/mcp
- [ ] Trouver la vraie URL du serveur MCP (mcp.systalink.com ne répond pas au 08/11)
- [ ] Brancher la clé à un client MCP et lister les outils disponibles
- [ ] Appeler api.systalink.com avec la clé : quelles routes répondent ?

**Terminé quand** on sait si le « déploiement en un clic » est faisable. Réponse : oui, non, ou à quelle condition."
issue "Tester Yokk (offre gratuite, API, widget)" "$INTEG" "type: recherche,domaine: ia-systalink,priorité: moyenne" \
"Créer un compte sur yokk.ai, construire un petit agent (« transforme un besoin en cahier des charges ») et vérifier : offre gratuite et quotas, clé d'API, widget intégrable.

**Terminé quand** on a un exemple d'appel à l'API qui fonctionne, ou bien la liste précise de ce qui bloque."
issue "Valider l'architecture (docs/architecture.md)" "$BACK" "type: proposition,domaine: backend,priorité: haute" \
"Relire la proposition docs/architecture.md avec @$FRONT et @$INTEG.
- [ ] Tour de table : qui maîtrise Spring Boot, Vue, FastAPI ?
- [ ] Attendre le test DataCloud de @$INTEG : Java et Python sont-ils supportés ?
- [ ] Si Java n'est pas supporté, adapter la proposition en NestJS

**Terminé quand** le statut de docs/architecture.md passe à « validé » par le chef de projet."
issue "Modèle de données v1" "$BACK" "type: fonctionnalité,domaine: backend,priorité: moyenne" \
"Schéma des entités : Utilisateur, Profil, Communauté, Publication/Réponse, Demande (PME), Projet, Équipe. Un diagramme suffit.

**Terminé quand** docs/modele-donnees.md est fusionné."
issue "Squelette de l'appli web et navigation" "$FRONT" "type: fonctionnalité,domaine: frontend,priorité: moyenne" \
"Une fois l'architecture validée (Vue 3 + TypeScript + Vite) : projet initialisé, pages vides (Accueil, Profil, Communautés, Demandes, Projets), navigation en mots clairs (voir docs/vocabulaire.md).

**Terminé quand** l'appli démarre en local avec un README d'installation."
issue "Maquettes : profil, communauté, demande" "$DESIGN" "type: fonctionnalité,domaine: design,priorité: haute" \
"Maquettes mobiles et ordinateur des 3 écrans clés, en suivant docs/vocabulaire.md.

**Terminé quand** le lien des maquettes est dans cette issue et validé par le chef de projet."
issue "Interviewer 5 développeurs et 2 PME" "$DESIGN" "type: recherche,domaine: design,priorité: moyenne" \
"Vérifier les vrais problèmes : former une équipe, faire vivre un projet après un hackathon, exprimer un besoin quand on n'est pas technique. Questions courtes, avec des citations à l'appui.

**Terminé quand** la synthèse est écrite dans docs/entretiens.md."

echo "▶ 8. Tableau de suivi (GitHub Projects)"
if gh project list --owner "$LEAD" --format json -q '.projects[].title' 2>/dev/null | grep -qx "Jamii · CADEV"; then
  ok "tableau déjà existant"
else
  NUM=$(gh project create --owner "$LEAD" --title "Jamii · CADEV" --format json -q .number 2>/dev/null)
  if [[ -n "$NUM" ]]; then
    gh project link "$NUM" --owner "$LEAD" --repo "$REPO" >/dev/null 2>&1
    for u in $(gh issue list --repo "$REPO" --state open --limit 100 --json url -q '.[].url'); do
      gh project item-add "$NUM" --owner "$LEAD" --url "$u" >/dev/null
    done
    ok "tableau créé : https://github.com/users/$LEAD/projects/$NUM"
  else
    warn "lance 'gh auth refresh -s project' puis relance ce script"
  fi
fi

echo
echo "🎉 Terminé : https://github.com/$REPO"
echo "   Tes 4 amis doivent accepter l'invitation (e-mail ou https://github.com/$REPO/invitations)."
