"""
Contratos e schemas de validação Pydantic v2 com aritmética decimal estrita.
"""

from decimal import Decimal
from typing import Any, Dict, List, Literal, Optional
from pydantic import BaseModel, Field, field_serializer, field_validator


def _format_decimal_two_places(value: Optional[Decimal]) -> Optional[str]:
    if value is None:
        return None
    return f"{value:.2f}"


class SuggestionItem(BaseModel):
    sku: str = Field(..., min_length=1, max_length=60, description="SKU do produto")
    category_id: int = Field(..., gt=0, description="ID da categoria no catálogo")
    cost_price: Decimal = Field(..., gt=0, description="Preço de custo de aquisição")
    current_price: Decimal = Field(..., gt=0, description="Preço atual de catálogo")
    min_price: Decimal = Field(..., gt=0, description="Piso operacional seguro (calculado pelo Java)")
    max_price: Decimal = Field(..., gt=0, description="Teto operacional seguro (calculado pelo Java)")
    competitor_prices: List[Decimal] = Field(default_factory=list, description="Coletas recentes de concorrentes")

    @field_validator("min_price")
    @classmethod
    def validate_min_less_than_max(cls, v: Decimal, info) -> Decimal:
        max_p = info.data.get("max_price")
        if max_p is not None and v > max_p:
            raise ValueError(f"min_price ({v}) não pode ser maior que max_price ({max_p})")
        return v

    @field_serializer("cost_price", "current_price", "min_price", "max_price", when_used="json")
    def serialize_prices(self, v: Decimal) -> str:
        return _format_decimal_two_places(v)  # type: ignore

    @field_serializer("competitor_prices", when_used="json")
    def serialize_comp_prices(self, v: List[Decimal]) -> List[str]:
        return [_format_decimal_two_places(item) for item in v]  # type: ignore


class SuggestionRequest(BaseModel):
    run_id: int = Field(..., gt=0, description="Identificador da execução do ciclo")
    items: List[SuggestionItem] = Field(..., min_length=1, max_length=500, description="Lote de itens para precificação")


class Explanation(BaseModel):
    elasticity: float = Field(..., description="Elasticidade-preço estimada utilizada")
    competitor_median: Optional[Decimal] = Field(None, description="Mediana dos concorrentes informada")
    unconstrained_optimum: Optional[Decimal] = Field(None, description="Preço ótimo analítico sem restrições")
    clipped_by: Literal["MIN_PRICE", "MAX_PRICE", "NONE"] = Field(..., description="Restrição que recortou o preço")
    confidence: Literal["HIGH", "LOW"] = Field(..., description="Nível de confiança estatística da decisão")

    @field_serializer("competitor_median", "unconstrained_optimum", when_used="json")
    def serialize_optional_decimals(self, v: Optional[Decimal]) -> Optional[str]:
        return _format_decimal_two_places(v)


class SuggestionResult(BaseModel):
    sku: str = Field(..., description="SKU correspondente")
    suggested_price: Decimal = Field(..., gt=0, description="Preço sugerido final com clipping")
    strategy: Literal["ELASTICITY", "BASELINE_FALLBACK"] = Field(..., description="Estratégia adotada")
    explanation: Explanation

    @field_serializer("suggested_price", when_used="json")
    def serialize_suggested(self, v: Decimal) -> str:
        return _format_decimal_two_places(v)  # type: ignore


class SuggestionResponse(BaseModel):
    model_version: str = Field(..., description="Versão do modelo econométrico ativo")
    results: List[SuggestionResult] = Field(..., description="Lista de decisões de preço")


class HealthResponse(BaseModel):
    status: str = "UP"


class ModelCategoryMetrics(BaseModel):
    eta: float
    beta: float
    alpha: float
    r2: float
    p_value_eta: float
    observations: int
    status: str


class ModelInfoResponse(BaseModel):
    model_version: str
    calibrated_at: str
    categories: Dict[str, ModelCategoryMetrics]