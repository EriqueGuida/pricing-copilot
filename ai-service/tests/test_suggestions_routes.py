"""
Testes funcionais completos para o endpoint POST /v1/suggestions.
"""

from decimal import Decimal
import pytest
from httpx import ASGITransport, AsyncClient
from main import app, app_state


@pytest.fixture(autouse=True)
def setup_mock_model_state():
    """Garante estado do modelo previsível durante os testes."""
    app_state["model_version"] = "v1.0.0"
    app_state["categories"] = {
        "CONSUMER_ELECTRONICS": {
            "eta": -1.9066,
            "beta": 0.5231,
            "alpha": 12.4,
            "r2": 0.83,
            "p_value_eta": 1e-10,
            "observations": 1460,
            "status": "CALIBRATED",
        },
        "COMPUTER_GAMING": {
            "eta": -2.0000,
            "beta": 0.6000,
            "alpha": 13.0,
            "r2": 0.80,
            "p_value_eta": 1e-10,
            "observations": 1460,
            "status": "CALIBRATED",
        },
    }
    yield
    app_state.clear()


@pytest.mark.asyncio
async def test_suggestions_empty_batch_rejected():
    """Valida rejeição de lote vazio (min_length=1)."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {"run_id": 1, "items": []}
        response = await client.post("/v1/suggestions", json=payload)
        assert response.status_code == 422


@pytest.mark.asyncio
async def test_suggestions_elasticity_strategy_high_confidence():
    """
    Categoria 2 (COMPUTER_GAMING):
    cost = 100.00, eta = -2.0 -> markup factor = -2 / (1 - 2) = 2.0
    p* = 200.00. Com limites [150.00, 250.00], o preço não é recortado.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "run_id": 100,
            "items": [
                {
                    "sku": "SKU-GAMING-001",
                    "category_id": 2,
                    "cost_price": "100.00",
                    "current_price": "190.00",
                    "min_price": "150.00",
                    "max_price": "250.00",
                    "competitor_prices": ["195.00", "205.00"],
                }
            ],
        }
        response = await client.post("/v1/suggestions", json=payload)
        assert response.status_code == 200

        data = response.json()
        assert data["model_version"] == "v1.0.0"
        result = data["results"][0]

        assert result["sku"] == "SKU-GAMING-001"
        assert result["suggested_price"] == "200.00"
        assert result["strategy"] == "ELASTICITY"
        assert result["explanation"]["confidence"] == "HIGH"
        assert result["explanation"]["clipped_by"] == "NONE"
        assert result["explanation"]["competitor_median"] == "200.00"


@pytest.mark.asyncio
async def test_suggestions_clipped_by_max_price():
    """Preço ótimo é 200.00, mas max_price = 180.00 -> clipped_by = MAX_PRICE."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "run_id": 101,
            "items": [
                {
                    "sku": "SKU-GAMING-MAX",
                    "category_id": 2,
                    "cost_price": "100.00",
                    "current_price": "170.00",
                    "min_price": "150.00",
                    "max_price": "180.00",
                    "competitor_prices": [],
                }
            ],
        }
        response = await client.post("/v1/suggestions", json=payload)
        assert response.status_code == 200

        result = response.json()["results"][0]
        assert result["suggested_price"] == "180.00"
        assert result["explanation"]["clipped_by"] == "MAX_PRICE"


@pytest.mark.asyncio
async def test_suggestions_clipped_by_min_price():
    """Preço ótimo é 200.00, mas min_price = 220.00 -> clipped_by = MIN_PRICE."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "run_id": 102,
            "items": [
                {
                    "sku": "SKU-GAMING-MIN",
                    "category_id": 2,
                    "cost_price": "100.00",
                    "current_price": "210.00",
                    "min_price": "220.00",
                    "max_price": "260.00",
                    "competitor_prices": [],
                }
            ],
        }
        response = await client.post("/v1/suggestions", json=payload)
        assert response.status_code == 200

        result = response.json()["results"][0]
        assert result["suggested_price"] == "220.00"
        assert result["explanation"]["clipped_by"] == "MIN_PRICE"


@pytest.mark.asyncio
async def test_suggestions_fallback_low_confidence_unknown_category():
    """Categoria não cadastrada (999) aciona BASELINE_FALLBACK com confidence=LOW."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "run_id": 103,
            "items": [
                {
                    "sku": "SKU-UNKNOWN-001",
                    "category_id": 999,
                    "cost_price": "50.00",
                    "current_price": "70.00",
                    "min_price": "60.00",
                    "max_price": "90.00",
                    "competitor_prices": ["100.00"],  # Mediana = 100.00 -> 100 * 0.99 = 99.00 -> Clip em 90.00
                }
            ],
        }
        response = await client.post("/v1/suggestions", json=payload)
        assert response.status_code == 200

        result = response.json()["results"][0]
        assert result["strategy"] == "BASELINE_FALLBACK"
        assert result["explanation"]["confidence"] == "LOW"
        assert result["suggested_price"] == "90.00"  # Recortado em max_price
        assert result["explanation"]["clipped_by"] == "MAX_PRICE"