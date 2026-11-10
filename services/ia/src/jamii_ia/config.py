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
    limite_par_heure: int = Field(default=100, ge=1, validation_alias="IA_LIMITE_PAR_HEURE")


@lru_cache
def reglages() -> Reglages:
    return Reglages()
