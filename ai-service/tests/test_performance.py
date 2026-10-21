"""
Teste de carga e benchmark de latência de inferência (SLA: p95 < 500ms para 50 itens).
"""

import time
import numpy as np
import pytest
from httpx import ASGITransport, AsyncClient
from main import app, app_state


@pytest.mark.asyncio
async def test_batch_50_items_p95_latency():
    # Inicializa parâmetros mockados para simulação
    app_state["model_version"] = "v1.0.0"
    app_state["categories"] = {
        "CONSUMER_ELECTRONICS": {"eta": -1.8, "r2": 0.8, "observations": 1000},
        "COMPUTER_GAMING": {"eta": -2.2, "r2": 0.8, "observations": 1000},
        "SMART_HOME": {"eta": -1.5, "r2": 0.8, "observations": 1000},
    }

    # Gera lote com 50 produtos representativos
    items = []
    for i in range(50):
        items.append({
            "sku": f"SKU-PERF-{i:03d}",
            "category_id": (i % 3) + 1,
            "cost_price": "120.50",
            "current_price": "180.00",
            "min_price": "140.00",
            "max_price": "220.00",
            "competitor_prices": ["175.00", "182.00", "179.90", "185.00"],
        })

    payload = {"run_id": 9999, "items": items}
    durations = []

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Warmup
        await client.post("/v1/suggestions", json=payload)

        # 30 execuções para extração percentilar estável
        for _ in range(30):
            t0 = time.perf_counter()
            resp = await client.post("/v1/suggestions", json=payload)
            t1 = time.perf_counter()

            assert resp.status_code == 200
            assert len(resp.json()["results"]) == 50
            durations.append((t1 - t0) * 1000.0)

    p95 = np.percentile(durations, 95)
    mean_lat = np.mean(durations)

    print(f"\n[BENCHMARK] Lote de 50 itens -> Latência Média: {mean_lat:.2f}ms | p95: {p95:.2f}ms")
    assert p95 < 500.0, f"Latência p95 ({p95:.2f}ms) violou o SLA de 500ms"