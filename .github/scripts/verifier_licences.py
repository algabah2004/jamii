"""Vérifie qu'aucune dépendance n'est sous licence GPL, AGPL ou LGPL (règlement CADEV, article 6).

Entrée : un ou plusieurs SBOM CycloneDX au format JSON (Maven ou Python).
- Une dépendance double licence (« EPL-2.0 OR GPL-2.0 ») est acceptée : on choisit la licence permissive.
- Une dépendance sans licence connue est signalée, sans faire échouer : à vérifier à la main.
- La liste complète est écrite dans le résumé du job (utile pour la déclaration à la soumission).

Usage : python3 verifier_licences.py bom1.json [bom2.json ...]
"""

import json
import os
import re
import sys

INTERDITE = re.compile(r"\b(A|L)?GPL\b|GNU (AFFERO |LESSER |LIBRARY )?GENERAL PUBLIC", re.IGNORECASE)


def alternatives(composant: dict) -> list[str]:
    """Les licences au choix d'un composant. Chaque entrée de la liste est une alternative."""
    choix = []
    for entree in composant.get("licenses", []):
        if "expression" in entree:
            choix += [partie.strip(" ()") for partie in re.split(r"\s+OR\s+", entree["expression"])]
        else:
            licence = entree.get("license", {})
            choix.append(licence.get("id") or licence.get("name") or "")
    return [c for c in choix if c]


def main(chemins: list[str]) -> int:
    interdites, inconnues, lignes = [], [], []
    for chemin in chemins:
        with open(chemin, encoding="utf-8") as fichier:
            sbom = json.load(fichier)
        for composant in sbom.get("components", []):
            nom = f"{composant.get('group', '') + ':' if composant.get('group') else ''}{composant['name']}"
            if nom.startswith(("dev.jamii", "jamii")):
                continue
            choix = alternatives(composant)
            lignes.append(f"| {nom} | {composant.get('version', '')} | {' ou '.join(choix) or '?'} |")
            if not choix:
                inconnues.append(nom)
            elif all(INTERDITE.search(c) for c in choix):
                interdites.append(f"{nom} ({' ou '.join(choix)})")

    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as sortie:
            sortie.write("### Licences des dépendances\n\n| Composant | Version | Licence |\n|---|---|---|\n")
            sortie.write("\n".join(sorted(lignes)) + "\n")

    print(f"{len(lignes)} dépendances vérifiées.")
    for nom in inconnues:
        print(f"::warning::Licence inconnue, à vérifier à la main : {nom}")
    for nom in interdites:
        print(f"::error::Licence interdite par le règlement CADEV (GPL, AGPL, LGPL) : {nom}")
    return 1 if interdites else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1:]))
