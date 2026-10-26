"""
Testes unitários e invariantes de negócio para o motor de precificação e otimização.
"""

from decimal import Decimal
import pytest
from optimizer import (
    calculate_optimal_price_decimal,
    evaluate_pricing_strategy,
    OptimizationInput,
)


def test_optimal_price_analytical_match():
    """
    Testa a fórmula analítica p* = c * (eta / (1 + eta)) em Decimal.
    Exemplo à mão:
    Custo c = 100.00
    eta = -2.0
    Markup factor = -2.0 / (1 - 2.0) = -2.0 / -1.0 = 2.0
    p* = 100.00 * 2.0 = 200.00
    """
    cost = Decimal("100.00")
    elasticity = Decimal("-2.0")
    p_star = calculate_optimal_price_decimal(cost, elasticity)
    assert p_star == Decimal("200.00")

    # Exemplo 2:
    # c = 50.00, eta = -1.5
    # Markup factor = -1.5 / -0.5 = 3.0
    # p* = 50.00 * 3.0 = 150.00
    assert calculate_optimal_price_decimal(Decimal("50.00"), Decimal("-1.5")) == Decimal("150.00")


def test_defensive_inelastic_elasticity_raises_value_error():
    """Se a demanda for inelástica (eta >= -1), deve lançar exceção ou não convergir."""
    with pytest.raises(ValueError, match="inelástica ou não convergente"):
        calculate_optimal_price_decimal(Decimal("100.00"), Decimal("-1.0"))

    with pytest.raises(ValueError, match="inelástica ou não convergente"):
        calculate_optimal_price_decimal(Decimal("100.00"), Decimal("-0.5"))

    with pytest.raises(ValueError, match="inelástica ou não convergente"):
        calculate_optimal_price_decimal(Decimal("100.00"), Decimal("0.5"))


def test_evaluate_pricing_strategy_clipping_bounds():
    """
    Garante que preços ótimos fora do intervalo sofrerão clipping estrito.
    """
    # Custo: 100, eta = -2.0 -> Preço ótimo irrestrito = 200.00
    model_params = {
        "GAMING": {"eta": -2.0, "r2": 0.85, "observations": 100}
    }

    # Cenário A: Teto corta o preço ótimo (teto = 180.00)
    input_above_max = OptimizationInput(
        sku="SKU-TEST-001",
        category_id=1,
        cost_price=Decimal("100.00"),
        current_price=Decimal("160.00"),
        min_price=Decimal("120.00"),
        max_price=Decimal("180.00"),
        competitor_prices=[Decimal("170.00")],
    )

    decision = evaluate_pricing_strategy(input_above_max, model_params, "GAMING")
    assert decision.strategy == "ELASTICITY"
    assert decision.suggested_price == Decimal("180.00")
    assert decision.explanation.clipped_by == "MAX_PRICE"
    assert decision.explanation.unconstrained_optimum == Decimal("200.00")

    # Cenário B: Piso corta o preço ótimo (piso = 220.00)
    input_below_min = OptimizationInput(
        sku="SKU-TEST-002",
        category_id=1,
        cost_price=Decimal("100.00"),
        current_price=Decimal("210.00"),
        min_price=Decimal("220.00"),
        max_price=Decimal("250.00"),
        competitor_prices=[Decimal("215.00")],
    )

    decision_min = evaluate_pricing_strategy(input_below_min, model_params, "GAMING")
    assert decision_min.suggested_price == Decimal("220.00")
    assert decision_min.explanation.clipped_by == "MIN_PRICE"


def test_baseline_fallback_on_low_confidence():
    """
    Se o R² for inferior a 0.30 ou a amostragem for insuficiente,
    deve acionar a estratégia BASELINE_FALLBACK com confiança LOW.
    """
    degraded_params = {
        "ELECTRONICS": {"eta": -1.8, "r2": 0.15, "observations": 20}  # R² baixo e n < 30
    }

    item = OptimizationInput(
        sku="SKU-FALLBACK",
        category_id=1,
        cost_price=Decimal("100.00"),
        current_price=Decimal("140.00"),
        min_price=Decimal("120.00"),
        max_price=Decimal("160.00"),
        competitor_prices=[Decimal("150.00"), Decimal("152.00")],  # Mediana = 151.00
    )

    decision = evaluate_pricing_strategy(item, degraded_params, "ELECTRONICS")

    assert decision.strategy == "BASELINE_FALLBACK"
    assert decision.explanation.confidence == "LOW"
    # Preço esperado: 151.00 * 0.99 = 149.49
    assert decision.suggested_price == Decimal("149.49")
    assert decision.explanation.clipped_by == "NONE"