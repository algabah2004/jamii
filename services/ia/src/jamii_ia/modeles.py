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


Texte = Annotated[str, StringConstraints(min_length=1, max_length=500)]


class Fonctionnalite(BaseModel):
    titre: Texte
    description: Texte
    priorite: Literal["indispensable", "utile", "plus_tard"]


class CahierDesCharges(BaseModel):
    resume: str = Field(min_length=1, max_length=800)
    objectifs: list[Texte] = Field(max_length=6)
    fonctionnalites: list[Fonctionnalite] = Field(max_length=15)
    horsPerimetre: list[Texte] = Field(max_length=8)


class Jalon(BaseModel):
    titre: Texte
    semaine: int = Field(ge=1, le=26)
    livrable: Texte


class TachePlan(BaseModel):
    titre: Texte
    description: Texte | None = None
    jalon: int = Field(ge=0)
    competences: list[Competence] = Field(max_length=5)
    membreSuggere: UUID | None = None
    raison: Texte | None = None


class RoleManquant(BaseModel):
    role: Texte
    competences: list[Competence] = Field(max_length=5)
    raison: Texte


class ServiceSuggere(BaseModel):
    service: Literal["datacloud", "orion", "yokk", "domaine"]
    raison: Texte


class PlanProjet(BaseModel):
    cahierDesCharges: CahierDesCharges
    jalons: list[Jalon] = Field(min_length=1, max_length=8)
    taches: list[TachePlan] = Field(max_length=40)
    rolesManquants: list[RoleManquant] = Field(max_length=6)
    servicesSuggeres: list[ServiceSuggere] = Field(max_length=4)


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
