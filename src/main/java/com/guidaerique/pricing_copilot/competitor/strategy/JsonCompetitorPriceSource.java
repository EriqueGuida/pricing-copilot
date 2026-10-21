package com.guidaerique.pricing_copilot.competitor.strategy;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.competitor.domain.Competitor;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPrice;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPriceSourceType;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorIngestionResponse;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorPriceItemRequest;
import com.guidaerique.pricing_copilot.competitor.dto.IngestionErrorDetail;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorPriceRepository;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JsonCompetitorPriceSource implements CompetitorPriceSource<List<CompetitorPriceItemRequest>> {

    private final ProductRepository productRepository;
    private final CompetitorRepository competitorRepository;
    private final CompetitorPriceRepository competitorPriceRepository;

    public JsonCompetitorPriceSource(
            ProductRepository productRepository,
            CompetitorRepository competitorRepository,
            CompetitorPriceRepository competitorPriceRepository
    ) {
        this.productRepository = productRepository;
        this.competitorRepository = competitorRepository;
        this.competitorPriceRepository = competitorPriceRepository;
    }

    @Override
    public CompetitorPriceSourceType getSourceType() {
        return CompetitorPriceSourceType.API;
    }

    @Override
    @Transactional
    public CompetitorIngestionResponse ingest(List<CompetitorPriceItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return new CompetitorIngestionResponse(0, 0, 0, List.of());
        }

        List<CompetitorPrice> validPrices = new ArrayList<>();
        List<IngestionErrorDetail> errors = new ArrayList<>();
        Map<String, Competitor> competitorCache = new HashMap<>();

        int index = 0;
        for (CompetitorPriceItemRequest item : items) {
            index++;
            String identifier = (item.sku() != null && !item.sku().isBlank())
                    ? item.sku()
                    : ((item.ean() != null && !item.ean().isBlank()) ? item.ean() : "ITEM-" + index);

            // Validações de regra de negócio
            if ((item.sku() == null || item.sku().isBlank()) && (item.ean() == null || item.ean().isBlank())) {
                errors.add(new IngestionErrorDetail(index, identifier, "SKU e EAN estão ausentes"));
                continue;
            }

            if (item.competitorName() == null || item.competitorName().isBlank()) {
                errors.add(new IngestionErrorDetail(index, identifier, "Nome do concorrente não informado"));
                continue;
            }

            if (item.price() == null || item.price().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new IngestionErrorDetail(index, identifier, "O preço deve ser estritamente positivo"));
                continue;
            }

            if (item.price().stripTrailingZeros().scale() > 2) {
                errors.add(new IngestionErrorDetail(index, identifier, "O preço possui mais de 2 casas decimais"));
                continue;
            }

            // Associação com catálogo
            Optional<Product> productOpt = Optional.empty();
            if (item.sku() != null && !item.sku().isBlank()) {
                productOpt = productRepository.findBySku(item.sku().trim());
            }
            if (productOpt.isEmpty() && item.ean() != null && !item.ean().isBlank()) {
                productOpt = productRepository.findByEan(item.ean().trim());
            }

            if (productOpt.isEmpty()) {
                errors.add(new IngestionErrorDetail(index, identifier, "Produto não encontrado no catálogo"));
                continue;
            }

            String compKey = item.competitorName().trim().toUpperCase();
            Competitor competitor = competitorCache.computeIfAbsent(compKey, k ->
                    competitorRepository.findByNameIgnoreCase(item.competitorName().trim())
                            .orElseGet(() -> competitorRepository.save(new Competitor(item.competitorName().trim())))
            );

            LocalDateTime collectedAt = item.collectedAt() != null ? item.collectedAt() : LocalDateTime.now();

            CompetitorPrice competitorPrice = new CompetitorPrice(
                    productOpt.get(),
                    competitor,
                    item.price(),
                    collectedAt,
                    CompetitorPriceSourceType.API
            );

            validPrices.add(competitorPrice);
        }

        if (!validPrices.isEmpty()) {
            competitorPriceRepository.saveAll(validPrices);
        }

        return new CompetitorIngestionResponse(
                items.size(),
                validPrices.size(),
                errors.size(),
                errors
        );
    }
}