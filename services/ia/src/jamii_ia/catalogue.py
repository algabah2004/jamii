"""Le catalogue des services Systalink : un fichier de données que Systalink modifie sans code.

L'IA reçoit `id` et `pourQuoi` pour choisir. Le nom, le prix et le lien affichés viennent
toujours du fichier, jamais de l'IA : elle ne peut ni inventer un service ni changer un prix.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

# services/ia/catalogue-systalink.yaml, à côté de pyproject.toml.
_PAR_DEFAUT = Path(__file__).resolve().parents[2] / "catalogue-systalink.yaml"


class CatalogueInvalideError(ValueError):
    pass


@dataclass(frozen=True)
class ServiceCatalogue:
    id: str
    nom: str
    pour_quoi: str
    prix_indicatif: str | None
    lien: str


class Catalogue:
    def __init__(self, services: list[ServiceCatalogue]) -> None:
        self._services = {service.id: service for service in services}

    @classmethod
    def charger(cls, chemin: str = "") -> "Catalogue":
        fichier = Path(chemin) if chemin else _PAR_DEFAUT
        try:
            contenu = yaml.safe_load(fichier.read_text(encoding="utf-8")) or {}
            services = [
                ServiceCatalogue(
                    id=str(s["id"]),
                    nom=str(s["nom"]),
                    pour_quoi=" ".join(str(s["pourQuoi"]).split()),
                    prix_indicatif=s.get("prixIndicatif"),
                    lien=str(s["lien"]),
                )
                for s in contenu.get("services", [])
            ]
        except (OSError, yaml.YAMLError, KeyError, TypeError) as erreur:
            raise CatalogueInvalideError(
                f"catalogue Systalink illisible ({fichier}): {erreur}"
            ) from None
        if not services:
            raise CatalogueInvalideError(f"catalogue Systalink vide ({fichier})")
        for service in services:
            if not service.lien.startswith("https://"):
                raise CatalogueInvalideError(f"lien non https pour « {service.id} »")
        return cls(services)

    def pour_l_ia(self) -> list[dict[str, str]]:
        return [{"id": s.id, "pourQuoi": s.pour_quoi} for s in self._services.values()]

    def completer(self, id_service: str, pour_ce_besoin: str) -> dict[str, Any] | None:
        """Le service complet pour le contrat, ou `None` si l'identifiant n'existe pas."""
        service = self._services.get(id_service)
        if service is None:
            return None
        return {
            "id": service.id,
            "nom": service.nom,
            "pourCeBesoin": pour_ce_besoin,
            "prixIndicatif": service.prix_indicatif,
            "lien": service.lien,
        }
