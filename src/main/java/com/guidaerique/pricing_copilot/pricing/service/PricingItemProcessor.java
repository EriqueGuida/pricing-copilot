package com.guidaerique.pricing_copilot.pricing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionResultResponse;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;
import com.guidaerique.pricing_copilot.pricing.domain.PriceGuard;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.domain.event.PriceChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingItemProcessor {

    private static final Logger log = LoggerFactory.getLogger(PricingItemProcessor.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PriceGuard priceGuard = new PriceGuard();

    public PricingItemProcessor(ProductRepository productRepository,
                                PriceHistoryRepository priceHistoryRepository,
                                AlertRepository alertRepository,
                                ApplicationEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public enum ProcessOutcome {
        UPDATED,
        SKIPPED,
        ERROR
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessOutcome processItemSuccess(Product product,
                                            PriceBounds bounds,
                                            SuggestionResultResponse suggestion,
                                            PricingRun pricingRun,
                                            String modelVersion) {
        try {
            BigDecimal suggestedPrice = suggestion.suggestedPrice();
            
            // RF-03 / RNF-02: Validação rigorosa dos limites via PriceGuard
            priceGuard.validate(suggestedPrice, bounds);

            // Alerta de baixa confiança econométrica da IA
            if ("LOW".equalsIgnoreCase(suggestion.explanation().confidence())) {
                createAlert(product, AlertType.LOW_CONFIDENCE,
                        "IA sugeriu preco com baixa confianca para o SKU " + product.getSku());
            }

            BigDecimal currentPrice = product.getCurrentPrice();
            if (currentPrice.compareTo(suggestedPrice) == 0) {
                log.info("SKU {}: Preco sugerido identico ao vigente (R$ {}). Ignorado.", product.getSku(), currentPrice);
                return ProcessOutcome.SKIPPED;
            }

            // Atualização do produto (incremento de @Version automático pelo JPA)
            product.setCurrentPrice(suggestedPrice);
            Product savedProduct = productRepository.save(product);

            // Serialização do JSON de auditoria
            String explanationJson = null;
            try {
                explanationJson = objectMapper.writeValueAsString(suggestion.explanation());
            } catch (JsonProcessingException e) {
                log.warn("Falha na serializacao da explicacao para SKU {}: {}", product.getSku(), e.getMessage());
            }

            // Gravação imutável do histórico
            PriceHistory history = new PriceHistory(
                    savedProduct,
                    currentPrice,
                    suggestedPrice,
                    PriceOrigin.AI,
                    null,
                    "Atualizacao autonoma via IA (" + suggestion.strategy() + ")"
            );
            history.setPricingRunId(pricingRun.getId());
            history.setModelVersion(modelVersion);
            history.setExplanation(explanationJson);

            priceHistoryRepository.save(history);

            // Cálculo dos pisos/tetos nominais de margem para o evento
            Category category = product.getCategory();
            BigDecimal cost = product.getCostPrice();
            BigDecimal pisoMargem = cost.divide(BigDecimal.ONE.subtract(category.getMinMargin()), 4, RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);
            BigDecimal tetoMargem = cost.divide(BigDecimal.ONE.subtract(category.getMaxMargin()), 4, RoundingMode.FLOOR).setScale(2, RoundingMode.FLOOR);

            // Disparo desacoplado de evento de domínio
            eventPublisher.publishEvent(new PriceChangedEvent(
                    savedProduct,
                    currentPrice,
                    suggestedPrice,
                    cost,
                    category.getMinMargin(),
                    category.getMaxMargin(),
                    pisoMargem,
                    tetoMargem,
                    bounds,
                    false
            ));

            return ProcessOutcome.UPDATED;

        } catch (Exception ex) {
            log.error("Violacao de regra ao processar SKU {}: {}", product.getSku(), ex.getMessage());
            return ProcessOutcome.ERROR;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAiUnavailable(Product product, String reason) {
        createAlert(product, AlertType.AI_UNAVAILABLE, reason);
    }

    private void createAlert(Product product, AlertType type, String message) {
        Alert alert = new Alert(product, type, message);
        alertRepository.save(alert);
    }
}