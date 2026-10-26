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
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionResultResponse;
import com.guidaerique.pricing_copilot.pricing.domain.InvalidPriceSuggestionException;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBoundsCalculator;
import com.guidaerique.pricing_copilot.pricing.domain.PriceGuard;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.domain.event.PriceChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PricingItemProcessor {

    private static final Logger log = LoggerFactory.getLogger(PricingItemProcessor.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private CategoryRepository categoryRepository;
    private final PriceGuard priceGuard = new PriceGuard();
    private final PriceBoundsCalculator boundsCalculator = new PriceBoundsCalculator();

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

    @Autowired(required = false)
    public void setCategoryRepository(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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
        BigDecimal suggestedPrice = suggestion.suggestedPrice();

        Category currentCategory = product.getCategory();
        if (categoryRepository != null && currentCategory != null && currentCategory.getId() != null) {
            currentCategory = categoryRepository.findById(currentCategory.getId()).orElse(currentCategory);
        }

        PriceBounds effectiveBounds = (currentCategory != null)
                ? boundsCalculator.calculate(
                        product.getCostPrice(),
                        product.getCurrentPrice(),
                        currentCategory.getMinMargin(),
                        currentCategory.getMaxMargin(),
                        currentCategory.getMaxVariation()
                )
                : bounds;

        try {
            // RF-03 / RNF-02: Revalidação rigorosa contra os limites vigentes via PriceGuard
            priceGuard.validate(suggestedPrice, effectiveBounds);
        } catch (InvalidPriceSuggestionException ex) {
            log.error("Violacao de regra ao processar SKU {}: {}", product.getSku(), ex.getMessage());
            return ProcessOutcome.ERROR;
        }

        // Alerta de baixa confiança econométrica da IA
        if (suggestion.explanation() != null && "LOW".equalsIgnoreCase(suggestion.explanation().confidence())) {
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
        Product savedProduct = productRepository.saveAndFlush(product);

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

        // Cálculo unificado dos pisos/tetos nominais de margem via PriceBoundsCalculator
        Category category = currentCategory != null ? currentCategory : product.getCategory();
        BigDecimal cost = product.getCostPrice();
        BigDecimal pisoMargem = boundsCalculator.calculateMarginFloor(cost, category.getMinMargin());
        BigDecimal tetoMargem = boundsCalculator.calculateMarginCeiling(cost, category.getMaxMargin());

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
                effectiveBounds,
                false
        ));

        return ProcessOutcome.UPDATED;
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