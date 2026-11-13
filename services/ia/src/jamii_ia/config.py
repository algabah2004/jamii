"""Configuration : uniquement des variables d'environnement (voir .env.example à la racine)."""

from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Reglages(BaseSettings):
    model_config = SettingsConfigDict(extra="ignore")

    # Même règle que apps/api : PORT (imposé par l'hébergeur) l'emporte sur IA_PORT.
    port: int = Field(default=8000, validation_alias=AliasChoices("PORT", "IA_PORT"))
    fournisseur: str = Field(default="simule", validation_alias="IA_PROVIDER")
    database_url: str = Field(default="", validation_alias="DATABASE_URL")
    # Secret partagé avec l'API. Aucune valeur par défaut : un oubli bloque le démarrage.
    jeton_interne: str = Field(default="", validation_alias="IA_JETON_INTERNE")
    # Faux pour un service qui ne fait que recevoir les tâches (ou pour les tests).
    travailleur_actif: bool = Field(default=True, validation_alias="IA_TRAVAILLEUR")
    limite_par_heure: int = Field(default=100, ge=1, validation_alias="IA_LIMITE_PAR_HEURE")
    # Fournisseur au format OpenAI (DeepSeek, OpenAI…) : voir fournisseurs/compatible_openai.py.
    url_fournisseur: str = Field(default="", validation_alias="IA_URL_FOURNISSEUR")
    modele: str = Field(default="", validation_alias="IA_MODELE")
    cle: str = Field(default="", validation_alias="IA_CLE", repr=False)
    # Jetons par jour (UTC), tous appels confondus. Au-delà : repli sans IA. 0 = pas de plafond.
    budget_jour: int = Field(default=200_000, ge=0, validation_alias="IA_BUDGET_JOUR")
    # Catalogue des services Systalink (fichier de données, modifiable sans toucher au code).
    catalogue: str = Field(default="", validation_alias="IA_CATALOGUE")


@lru_cache
def reglages() -> Reglages:
    return Reglages()
