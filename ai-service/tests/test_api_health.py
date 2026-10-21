"""
Testes de integridade das rotas de observabilidade e metadados do modelo.
"""

import pytest
from httpx import ASGITransport, AsyncClient
from main import app


@pytest.mark.asyncio
async def test_health_endpoint_returns_up():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "UP"}
        assert "x-correlation-id" in response.headers
        assert "x-response-time-ms" in response.headers


@pytest.mark.asyncio
async def test_model_info_returns_calibrated_metadata():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/v1/model/info")
        assert response.status_code in (200, 503)

        if response.status_code == 200:
            data = response.json()
            assert "model_version" in data
            assert "categories" in data
            assert "CONSUMER_ELECTRONICS" in data["categories"]