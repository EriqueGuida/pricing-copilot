"""
Motor analítico de cálculo do preço ótimo por elasticidade da demanda e
estratégias defensivas de fallback (baseline).
"""

from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, List, Literal, Optional
from pydantic import BaseModel, Field


class OptimizationInput(BaseModel):
    sku: str
    category_id: int
    cost_price: Decimal = Field(gt=0)
    current_price: Decimal = Field(gt=0)
    min_price: Decimal = Field(gt=0)
    max_price: Decimal = Field(gt=0)
    competitor_prices: List[Decimal] = Field(default_factory=list)


class DecisionExplanation(BaseModel):
    elasticity: float
    competitor_median: Optional[Decimal] = None
    unconstrained_optimum: Optional[Decimal] = None
    clipped_by: Literal["MIN_PRICE", "MAX_PRICE", "NONE"]
    confidence: Literal["HIGH", "LOW"]


class DecisionOutput(BaseModel):
    sku: str
    suggested_price: Decimal
    strategy: Literal["ELASTICITY", "BASELINE_FALLBACK"]
    explanation: DecisionExplanation


def calculate_optimal_price(cost_price: float, elasticity: float) -> float:
    """
    Calcula o preço economicamente ótimo p* maximizador de lucro:
    L(p) = (p - c) * q(p)  =>  dL/dp = 0  =>  p* = c * (eta / (1 + eta))
    
    Exige demanda elástica: eta < -1.
    Se eta >= -1, o modelo teórico não tem solução finita de maximização.
    """
    if elasticity >= -1.0:
        raise ValueError(
            f"Elasticidade inelástica ou não convergente (eta = {elasticity:.4f} >= -1). "
            "Impossível calcular preço ótimo estável sem limites de saturação."
        )
    markup_factor = elasticity / (1.0 + elasticity)
    return cost_price * markup_factor


def evaluate_pricing_strategy(
    item: OptimizationInput,
    model_params: Dict[str, dict],
    vertical_name: str,
) -> DecisionOutput:
    """
    Arbitra entre precificação econométrica ótima e fallback baseline defensivo.
    Aplica clipping nos limites fornecidos pelo árbitro Java [min_price, max_price].
    """
    params = model_params.get(vertical_name, {})
    eta = params.get("eta")
    r2 = params.get("r2", 0.0)
    obs_count = params.get("observations", 0)

    # 1. Mediana dos concorrentes (se fornecida)
    comp_median: Optional[Decimal] = None
    if item.competitor_prices:
        sorted_comps = sorted(item.competitor_prices)
        mid = len(sorted_comps) // 2
        if len(sorted_comps) % 2 == 0:
            comp_median = ((sorted_comps[mid - 1] + sorted_comps[mid]) / Decimal("2")).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        else:
            comp_median = sorted_comps[mid].quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    # 2. Critérios de Confiança e Ativação de Baseline Fallback
    # Fallback se: eta ausente, eta inelástico (eta >= -1), R² baixo (< 0.30) ou pouca amostra (< 30)
    is_low_confidence = (
        eta is None
        or eta >= -1.0
        or r2 < 0.30
        or obs_count < 30
    )

    if is_low_confidence:
        # Política de Preço Baseline Fallback:
        # Se existirem concorrentes: Mediana * 0.99
        # Senão: Preço Atual
        if comp_median is not None:
            raw_baseline = (comp_median * Decimal("0.99")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        else:
            raw_baseline = item.current_price

        # Clipping defensivo no intervalo seguro
        final_price, clipped_by = _clip_price(raw_baseline, item.min_price, item.max_price)

        return DecisionOutput(
            sku=item.sku,
            suggested_price=final_price,
            strategy="BASELINE_FALLBACK",
            explanation=DecisionExplanation(
                elasticity=float(eta) if eta is not None else 0.0,
                competitor_median=comp_median,
                unconstrained_optimum=raw_baseline,
                clipped_by=clipped_by,
                confidence="LOW",
            ),
        )

    # 3. Otimização Irrestrita (Alta Confiança)
    unconstrained_opt_float = calculate_optimal_price(float(item.cost_price), float(eta))
    unconstrained_opt = Decimal(str(round(unconstrained_opt_float, 2))).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )

    # 4. Clipping Seguro nos Limites do Negócio
    final_price, clipped_by = _clip_price(unconstrained_opt, item.min_price, item.max_price)

    return DecisionOutput(
        sku=item.sku,
        suggested_price=final_price,
        strategy="ELASTICITY",
        explanation=DecisionExplanation(
            elasticity=float(eta),
            competitor_median=comp_median,
            unconstrained_optimum=unconstrained_opt,
            clipped_by=clipped_by,
            confidence="HIGH",
        ),
    )


def _clip_price(
    price: Decimal, min_price: Decimal, max_price: Decimal
) -> tuple[Decimal, Literal["MIN_PRICE", "MAX_PRICE", "NONE"]]:
    """Corta o preço nas faixas permitidas e documenta o motivo."""
    if price < min_price:
        return min_price, "MIN_PRICE"
    if price > max_price:
        return max_price, "MAX_PRICE"
    return price, "NONE"