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
    # `tous` : utilisable sans développeur. `developpeurs` : sert à ce que des développeurs
    # construisent.
    pour_qui: str = "tous"
    # Ce que le service ne fait pas, et quand conseiller autre chose : c'est ce qui évite de forcer.
    limites: str = ""
    pas_pour_toi: str = ""


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
                    pour_qui=str(s.get("pourQui", "tous")),
                    limites=" ".join(str(s.get("limites") or "").split()),
                    pas_pour_toi=" ".join(str(s.get("pasPourToi") or "").split()),
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
            if service.pour_qui not in ("tous", "developpeurs"):
                raise CatalogueInvalideError(f"pourQui inconnu pour « {service.id} »")
            if not service.lien.startswith("https://"):
                raise CatalogueInvalideError(f"lien non https pour « {service.id} »")
        return cls(services)

    def pour_l_ia(self) -> list[dict[str, str]]:
        fiches = []
        for s in self._services.values():
            fiche = {"id": s.id, "pourQuoi": s.pour_quoi, "pourQui": s.pour_qui}
            if s.prix_indicatif:
                fiche["prix"] = s.prix_indicatif
            if s.limites:
                fiche["limites"] = s.limites
            if s.pas_pour_toi:
                fiche["pasPourToi"] = s.pas_pour_toi
            fiches.append(fiche)
        return fiches

    def pour_developpeurs(self, id_service: str) -> bool:
        service = self._services.get(id_service)
        return service is not None and service.pour_qui == "developpeurs"

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
