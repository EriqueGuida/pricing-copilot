"""
Serviço de Inferência e Otimização de Preços em FastAPI.
Arquitetura 100% Stateless (ADR-002).
"""

from contextlib import asynccontextmanager
import json
import logging
import time
import uuid
from typing import Any, Dict

from fastapi import FastAPI, HTTPException, Request, Response, status

from config import settings
from optimizer import evaluate_pricing_strategy
from schemas import (
    HealthResponse,
    ModelInfoResponse,
    SuggestionRequest,
    SuggestionResponse,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s"
)
logger = logging.getLogger("pricing-ai-service")

# Estado de modelos carregado em memória na inicialização (somente leitura)
app_state: Dict[str, Any] = {
    "model_version": "unknown",
    "calibrated_at": "",
    "categories": {},
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Carrega o artefato calibrado na inicialização sem conexão com banco."""
    artifact_path = settings.model_artifact_path
    if artifact_path.exists():
        try:
            with open(artifact_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                app_state["model_version"] = data.get("model_version", "v1.0.0")
                app_state["calibrated_at"] = data.get("calibrated_at", "")
                app_state["categories"] = data.get("categories", {})
                logger.info(
                    "Artefato econométrico carregado com sucesso: versão=%s, categorias=%s",
                    app_state["model_version"],
                    list(app_state["categories"].keys()),
                )
        except Exception as e:
            logger.error("Falha ao desserializar artefato econométrico: %s", e)
    else:
        logger.warning("Artefato não encontrado em %s. Rodando em modo fallback.", artifact_path)

    yield
    app_state.clear()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Microsserviço de otimização econométrica de preços para o Copiloto de Precificação.",
    lifespan=lifespan,
)


@app.middleware("http")
async def correlation_and_latency_middleware(request: Request, call_next):
    """Propaga o cabeçalho X-Correlation-ID e registra tempo de resposta."""
    correlation_id = request.headers.get("X-Correlation-ID", str(uuid.uuid4()))
    start_time = time.perf_counter()

    response: Response = await call_next(request)

    latency_ms = (time.perf_counter() - start_time) * 1000.0
    response.headers["X-Correlation-ID"] = correlation_id
    response.headers["X-Response-Time-Ms"] = f"{latency_ms:.2f}"

    logger.info(
        "method=%s path=%s status=%d latency=%.2fms correlation_id=%s",
        request.method,
        request.url.path,
        response.status_code,
        latency_ms,
        correlation_id,
    )
    return response


@app.get("/health", response_model=HealthResponse, tags=["Observabilidade"])
def health_check():
    return HealthResponse(status="UP")


@app.get(f"{settings.api_prefix}/model/info", response_model=ModelInfoResponse, tags=["Modelo Econométrico"])
def get_model_info():
    if not app_state["categories"]:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Nenhum artefato econométrico ativo no momento."
        )
    return ModelInfoResponse(
        model_version=app_state["model_version"],
        calibrated_at=app_state["calibrated_at"],
        categories=app_state["categories"],
    )


@app.post(f"{settings.api_prefix}/suggestions", response_model=SuggestionResponse, tags=["Otimização de Preços"])
def calculate_suggestions(payload: SuggestionRequest):
    """
    Processa lote de itens e retorna sugestões ótimas com clipping seguro.
    Definido como 'def' síncrono para que o FastAPI execute o loop CPU-bound
    em threadpool sem bloquear o event loop principal.
    """
    results = []
    categories_data = app_state.get("categories", {})

    for item in payload.items:
        vertical_name = item.vertical or settings.category_vertical_map.get(item.category_id, "UNKNOWN")
        decision = evaluate_pricing_strategy(
            item=item,
            model_params=categories_data,
            vertical_name=vertical_name,
        )
        results.append(decision)

    return SuggestionResponse(
        model_version=app_state.get("model_version", "v1.0.0"),
        results=results,
    )