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


class Langage(Entree):
    nom: str = Field(max_length=40)
    part: float = Field(ge=0, le=1)


class DepotGithub(Entree):
    nom: str = Field(min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=300)
    langage: str | None = Field(default=None, max_length=40)
    sujets: list[Annotated[str, StringConstraints(max_length=50)]] = Field(
        default_factory=list, max_length=10
    )
    etoiles: int = Field(default=0, ge=0)
    misAJourLe: datetime | None = None
    extraitReadme: str | None = Field(default=None, max_length=1500)


class EntreeAnalyseProfil(Entree):
    langages: list[Langage] = Field(max_length=15)
    depots: list[DepotGithub] = Field(max_length=12)
    competencesDeclarees: list[Competence] = Field(default_factory=list, max_length=20)
    langue: Literal["fr", "en"] = "fr"


class EntreeOrientation(Entree):
    titre: str | None = Field(default=None, max_length=120)
    description: str = Field(min_length=20, max_length=4000)
    secteur: str | None = Field(default=None, max_length=60)
    pays: Annotated[str, StringConstraints(pattern=r"^[A-Z]{2}$")] | None = None
    langue: Literal["fr", "en"] = "fr"


class TachePlanProjet(Entree):
    type: Literal["plan_projet"]
    entree: EntreePlanProjet


class TacheAnalyseProfil(Entree):
    type: Literal["analyse_profil"]
    entree: EntreeAnalyseProfil


class TacheOrientation(Entree):
    type: Literal["orientation_besoin"]
    entree: EntreeOrientation


# L'entrée est validée avec le schéma de SON type, jamais deviné (union discriminée par `type`).
NouvelleTache = Annotated[
    TachePlanProjet | TacheAnalyseProfil | TacheOrientation, Field(discriminator="type")
]
TYPES_TACHE = ("plan_projet", "analyse_profil", "orientation_besoin")


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


CompetenceStricte = Annotated[
    str, StringConstraints(min_length=1, max_length=50, pattern=r"^[a-z0-9][a-z0-9.+#-]*$")
]


class CompetenceCarte(BaseModel):
    competence: CompetenceStricte
    niveau: Literal["debutant", "intermediaire", "confirme"]
    preuves: list[Annotated[str, StringConstraints(max_length=100)]] = Field(
        min_length=1, max_length=3
    )
    raison: Annotated[str, StringConstraints(min_length=1, max_length=200)]


class CarteCompetences(BaseModel):
    competences: list[CompetenceCarte] = Field(max_length=12)
    roleSuggere: str | None = Field(default=None, max_length=60)


class ServiceSystalink(BaseModel):
    id: str = Field(max_length=40)
    nom: str = Field(max_length=80)
    pourCeBesoin: str = Field(min_length=1, max_length=300)
    prixIndicatif: str | None = Field(default=None, max_length=80)
    lien: str


class Orientation(BaseModel):
    resume: str = Field(min_length=1, max_length=600)
    secteur: str = Field(min_length=1, max_length=60)
    services: list[ServiceSystalink] = Field(max_length=4)
    developpeursNecessaires: bool
    competences: list[CompetenceStricte] = Field(max_length=10)
    plan: PlanProjet | None


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
