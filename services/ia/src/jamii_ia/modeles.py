"""Les schémas de contracts/ia.yaml. Les noms de champs sont ceux du contrat (camelCase)."""

from datetime import datetime
from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

Competence = Annotated[str, StringConstraints(max_length=40)]


class Entree(BaseModel):
    # Un champ inconnu est refusé : on n'envoie à l'IA que ce que le contrat prévoit.
    model_config = ConfigDict(extra="forbid")


class MembreEquipe(Entree):
    membreId: UUID
    competences: list[Competence] = Field(max_length=20)
    heuresParSemaine: int | None = Field(default=None, ge=1, le=60)


class EntreePlanProjet(Entree):
    source: Literal["idee", "demande_pme"]
    titre: str = Field(min_length=3, max_length=120)
    description: str = Field(min_length=20, max_length=4000)
    langue: Literal["fr", "en"] = "fr"
    dureeSemaines: int | None = Field(default=None, ge=1, le=26)
    membres: list[MembreEquipe] = Field(default_factory=list, max_length=10)


class NouvelleTache(Entree):
    type: Literal["plan_projet"]
    entree: EntreePlanProjet


class Tache(BaseModel):
    id: UUID
    type: str
    statut: Literal["en_attente", "en_cours", "terminee", "echouee"]
    creeLe: datetime
    termineLe: datetime | None = None
    origine: Literal["ia", "cache", "repli"] | None = None
    resultat: dict[str, Any] | None = None
    erreurCode: str | None = None

    @classmethod
    def depuis_ligne(cls, ligne: dict[str, Any]) -> "Tache":
        return cls(
            id=ligne["id"],
            type=ligne["type"],
            statut=ligne["statut"],
            creeLe=ligne["cree_le"],
            termineLe=ligne["termine_le"],
            origine=ligne["origine"],
            resultat=ligne["resultat"],
            erreurCode=ligne["erreur_code"],
        )
