package com.guidaerique.pricing_copilot.pricing.service;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorPriceRepository;
import com.guidaerique.pricing_copilot.pricing.client.PriceSuggestionClient;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionItemRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionResultResponse;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBoundsCalculator;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.repository.PricingRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PricingRunService {

    private static final Logger log = LoggerFactory.getLogger(PricingRunService.class);
    private static final int BATCH_SIZE = 100;

    private final ProductRepository productRepository;
    private final CompetitorPriceRepository competitorPriceRepository;
    private final PricingRunRepository pricingRunRepository;
    private final PriceSuggestionClient suggestionClient;
    private final PricingItemProcessor itemProcessor;
    private final PriceBoundsCalculator boundsCalculator = new PriceBoundsCalculator();

    public PricingRunService(ProductRepository productRepository,
                             CompetitorPriceRepository competitorPriceRepository,
                             PricingRunRepository pricingRunRepository,
                             PriceSuggestionClient suggestionClient,
                             PricingItemProcessor itemProcessor) {
        this.productRepository = productRepository;
        this.competitorPriceRepository = competitorPriceRepository;
        this.pricingRunRepository = pricingRunRepository;
        this.suggestionClient = suggestionClient;
        this.itemProcessor = itemProcessor;
    }

    public PricingRun triggerManualRun() {
        log.info("Disparando Pricing Run manualmente.");
        return executePricingCycle();
    }

    @Scheduled(cron = "${pricing.schedule.cron:0 0 3 * * ?}")
    public void triggerScheduledRun() {
        log.info("Executando ciclo agendado de precificacao.");
        executePricingCycle();
    }

    private PricingRun executePricingCycle() {
        PricingRun run = new PricingRun();
        run = pricingRunRepository.save(run);

        LocalDateTime now = LocalDateTime.now();
        List<Product> eligibleProducts = productRepository.findAllEligibleForPricing(now);

        if (eligibleProducts.isEmpty()) {
            log.info("Nenhum produto elegivel para precificacao.");
            run.complete("NONE");
            return pricingRunRepository.save(run);
        }

        List<List<Product>> partitions = partitionList(eligibleProducts, BATCH_SIZE);
        String lastModelVersion = "v1.0.0";

        for (List<Product> batch : partitions) {
            Map<String, Product> productBySku = batch.stream()
                    .collect(Collectors.toMap(Product::getSku, Function.identity()));
            Map<String, PriceBounds> boundsBySku = new HashMap<>();

            List<SuggestionItemRequest> requestItems = new ArrayList<>();

            for (Product p : batch) {
                PriceBounds bounds = boundsCalculator.calculate(
                        p.getCostPrice(),
                        p.getCurrentPrice(),
                        p.getCategory().getMinMargin(),
                        p.getCategory().getMaxMargin(),
                        p.getCategory().getMaxVariation()
                );
                boundsBySku.put(p.getSku(), bounds);

                List<String> compPrices = competitorPriceRepository.findTop5LatestPrices(p.getId())
                        .stream()
                        .map(cp -> cp.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .toList();

                requestItems.add(new SuggestionItemRequest(
                        p.getSku(),
                        p.getCategory().getId(),
                        p.getCostPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        p.getCurrentPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        bounds.minPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        bounds.maxPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        compPrices
                ));
            }

            SuggestionBatchRequest batchRequest = new SuggestionBatchRequest(run.getId(), requestItems);
            SuggestionBatchResponse response = null;

            try {
                response = suggestionClient.requestSuggestions(batchRequest);
                if (response != null && response.modelVersion() != null) {
                    lastModelVersion = response.modelVersion();
                }
            } catch (Exception ex) {
                log.error("Falha de comunicacao com microsservico de IA: {}", ex.getMessage());
                for (Product p : batch) {
                    run.incrementProcessed();
                    run.incrementError();
                    itemProcessor.recordAiUnavailable(p, "Falha na integracao com servico de IA: " + ex.getMessage());
                }
                continue;
            }

            Map<String, SuggestionResultResponse> suggestionsBySku = response.results().stream()
                    .collect(Collectors.toMap(SuggestionResultResponse::sku, Function.identity()));

            for (Product product : batch) {
                run.incrementProcessed();
                SuggestionResultResponse suggestion = suggestionsBySku.get(product.getSku());

                if (suggestion == null) {
                    run.incrementError();
                    continue;
                }

                PriceBounds bounds = boundsBySku.get(product.getSku());
                PricingItemProcessor.ProcessOutcome outcome = itemProcessor.processItemSuccess(
                        product,
                        bounds,
                        suggestion,
                        run,
                        lastModelVersion
                );

                switch (outcome) {
                    case UPDATED -> run.incrementUpdated();
                    case SKIPPED -> run.incrementSkipped();
                    case ERROR -> run.incrementError();
                }
            }
        }

        run.complete(lastModelVersion);
        return pricingRunRepository.save(run);
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}