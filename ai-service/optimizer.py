"""
Motor analítico de cálculo de preço ótimo por elasticidade e fallback baseline.
"""

from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, Literal, Optional, Tuple, Union
from config import settings
from schemas import (
    Explanation,
    SuggestionItem,
    SuggestionResult,
)

# Aliases para retrocompatibilidade com os testes e módulos anteriores
OptimizationInput = SuggestionItem
DecisionOutput = SuggestionResult
DecisionExplanation = Explanation


def calculate_optimal_price_decimal(
    cost_price: Union[Decimal, float, str],
    elasticity: Union[float, Decimal, str]
) -> Decimal:
    """
    Calcula o preço ótimo maximizador de lucro em aritmética Decimal estrita
    para evitar perda de precisão IEEE 754:
    p* = cost * (eta / (1 + eta))
    Exige demanda elástica: eta < -1.
    """
    cost_dec = cost_price if isinstance(cost_price, Decimal) else Decimal(str(cost_price))
    eta_dec = elasticity if isinstance(elasticity, Decimal) else Decimal(str(elasticity))

    if eta_dec >= Decimal("-1.0"):
        raise ValueError(
            f"Elasticidade inelástica ou não convergente (eta = {eta_dec} >= -1). "
            "Impossível calcular preço ótimo sem limites de saturação."
        )
    markup_factor = eta_dec / (Decimal("1") + eta_dec)
    return (cost_dec * markup_factor).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


# Mantém o alias apontando para a implementação Decimal estrita
calculate_optimal_price = calculate_optimal_price_decimal


def _clip_price(
    price: Decimal, min_price: Decimal, max_price: Decimal
) -> Tuple[Decimal, Literal["MIN_PRICE", "MAX_PRICE", "NONE"]]:
    """Recorta o preço nas faixas permitidas e retorna o fator limitante."""
    if price < min_price:
        return min_price, "MIN_PRICE"
    if price > max_price:
        return max_price, "MAX_PRICE"
    return price, "NONE"


def evaluate_pricing_strategy(
    item: SuggestionItem,
    model_params: Dict[str, dict],
    vertical_name: str,
) -> SuggestionResult:
    """
    Avalia a melhor estratégia de precificação para o item:
    1. Se os parâmetros forem confiáveis: calcula preço ótimo por elasticidade.
    2. Caso contrário: aciona BASELINE_FALLBACK com confiança LOW.
    3. Em ambos os casos, aplica recorte mandatório em [min_price, max_price].
    """
    params = model_params.get(vertical_name, {})
    eta = params.get("eta")
    r2 = params.get("r2", 0.0)
    obs_count = params.get("observations", 0)

    r2_min = getattr(settings, "r2_threshold", 0.30)
    obs_min = getattr(settings, "min_observations_threshold", 30)

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

    # 2. Critérios de Baixa Confiança (Fallback)
    is_low_confidence = (
        eta is None
        or eta >= -1.0
        or r2 < r2_min
        or obs_count < obs_min
    )

    if is_low_confidence:
        if comp_median is not None:
            raw_baseline = (comp_median * Decimal("0.99")).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        else:
            raw_baseline = item.current_price

        final_price, clipped_by = _clip_price(raw_baseline, item.min_price, item.max_price)

        return SuggestionResult(
            sku=item.sku,
            suggested_price=final_price,
            strategy="BASELINE_FALLBACK",
            explanation=Explanation(
                elasticity=float(eta) if eta is not None else 0.0,
                competitor_median=comp_median,
                unconstrained_optimum=raw_baseline,
                clipped_by=clipped_by,
                confidence="LOW",
            ),
        )

    # 3. Otimização Irrestrita em Decimal estrito (Elasticidade de Alta Confiança)
    unconstrained_opt = calculate_optimal_price_decimal(item.cost_price, eta)

    # 4. Clipping Seguro nos Limites Definidos pelo Negócio
    final_price, clipped_by = _clip_price(unconstrained_opt, item.min_price, item.max_price)

    return SuggestionResult(
        sku=item.sku,
        suggested_price=final_price,
        strategy="ELASTICITY",
        explanation=Explanation(
            elasticity=float(eta),
            competitor_median=comp_median,
            unconstrained_optimum=unconstrained_opt,
            clipped_by=clipped_by,
            confidence="HIGH",
        ),
    )