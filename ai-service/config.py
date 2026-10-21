"""
Configurações da aplicação e carregamento de artefatos econométricos.
"""

from pathlib import Path
from typing import Dict
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Copiloto Pricing AI Service"
    app_version: str = "1.0.0"
    api_prefix: str = "/v1"
    model_artifact_path: Path = Path(__file__).parent / "model_artifact.json"
    r2_threshold: float = 0.30
    min_observations_threshold: int = 30

    # Mapeamento determinístico Category ID -> Vertical de Negócio
    category_vertical_map: Dict[int, str] = {
        1: "CONSUMER_ELECTRONICS",
        2: "COMPUTER_GAMING",
        3: "SMART_HOME",
    }

    model_config = SettingsConfigDict(
        env_prefix="PRICING_AI_",
        case_sensitive=False
    )


settings = Settings()