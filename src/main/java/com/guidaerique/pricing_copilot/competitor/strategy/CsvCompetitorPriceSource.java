package com.guidaerique.pricing_copilot.competitor.strategy;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.competitor.domain.Competitor;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPrice;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPriceSourceType;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorIngestionResponse;
import com.guidaerique.pricing_copilot.competitor.dto.IngestionErrorDetail;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorPriceRepository;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorRepository;
import com.guidaerique.pricing_copilot.shared.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CsvCompetitorPriceSource implements CompetitorPriceSource<MultipartFile> {

    private static final Logger log = LoggerFactory.getLogger(CsvCompetitorPriceSource.class);

    private final ProductRepository productRepository;
    private final CompetitorRepository competitorRepository;
    private final CompetitorPriceRepository competitorPriceRepository;

    public CsvCompetitorPriceSource(
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
        return CompetitorPriceSourceType.CSV;
    }

    @Override
    @Transactional
    public CompetitorIngestionResponse ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("O arquivo CSV enviado está vazio ou ausente.");
        }

        List<CompetitorPrice> validPrices = new ArrayList<>();
        List<IngestionErrorDetail> errors = new ArrayList<>();
        Map<String, Competitor> competitorCache = new HashMap<>();

        int totalProcessed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean isFirstDataLine = true;
            Map<String, Integer> headerMap = null;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                // Ignora linhas vazias
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Detecta delimitador (vírgula ou ponto e vírgula)
                String delimiter = trimmed.contains(";") ? ";" : ",";
                String[] columns = trimmed.split(delimiter, -1);

                // Detecta cabeçalho inteligente
                if (isFirstDataLine && isHeader(columns)) {
                    headerMap = parseHeaders(columns);
                    isFirstDataLine = false;
                    continue;
                }
                isFirstDataLine = false;
                totalProcessed++;

                try {
                    String sku = extractColumn(columns, headerMap, "sku", 0);
                    String ean = extractColumn(columns, headerMap, "ean", 1);
                    String competitorName = extractColumn(columns, headerMap, "competitor", 2);
                    String priceStr = extractColumn(columns, headerMap, "price", 3);

                    String identifier = (sku != null && !sku.isBlank()) ? sku : ((ean != null && !ean.isBlank()) ? ean : "LINHA-" + lineNumber);

                    // Validação de identificador
                    if ((sku == null || sku.isBlank()) && (ean == null || ean.isBlank())) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "SKU e EAN estão ausentes"));
                        continue;
                    }

                    // Validação de nome do concorrente
                    if (competitorName == null || competitorName.isBlank()) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "Nome do concorrente não informado"));
                        continue;
                    }

                    // Validação e conversão do Preço (aritmética estrita BigDecimal)
                    if (priceStr == null || priceStr.isBlank()) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "Preço ausente"));
                        continue;
                    }

                    BigDecimal price;
                    try {
                        price = new BigDecimal(priceStr.replace(",", "."));
                    } catch (NumberFormatException e) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "Formato numérico inválido: " + priceStr));
                        continue;
                    }

                    if (price.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "O preço deve ser estritamente positivo: " + price));
                        continue;
                    }

                    if (price.stripTrailingZeros().scale() > 2) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "O preço possui mais de 2 casas decimais: " + price));
                        continue;
                    }

                    // Associação com produto no catálogo
                    Optional<Product> productOpt = Optional.empty();
                    if (sku != null && !sku.isBlank()) {
                        productOpt = productRepository.findBySku(sku);
                    }
                    if (productOpt.isEmpty() && ean != null && !ean.isBlank()) {
                        productOpt = productRepository.findByEan(ean);
                    }

                    if (productOpt.isEmpty()) {
                        errors.add(new IngestionErrorDetail(lineNumber, identifier, "Produto não encontrado no catálogo"));
                        continue;
                    }

                    // Obtenção ou cadastro automático do concorrente
                    String compKey = competitorName.trim().toUpperCase();
                    Competitor competitor = competitorCache.computeIfAbsent(compKey, k ->
                            competitorRepository.findByNameIgnoreCase(competitorName.trim())
                                    .orElseGet(() -> competitorRepository.save(new Competitor(competitorName.trim())))
                    );

                    CompetitorPrice competitorPrice = new CompetitorPrice(
                            productOpt.get(),
                            competitor,
                            price,
                            LocalDateTime.now(),
                            CompetitorPriceSourceType.CSV
                    );

                    validPrices.add(competitorPrice);

                } catch (Exception ex) {
                    log.error("Erro inesperado no processamento da linha {}: {}", lineNumber, ex.getMessage());
                    errors.add(new IngestionErrorDetail(lineNumber, "LINHA-" + lineNumber, "Erro interno de processamento da linha: " + ex.getMessage()));
                }
            }

        } catch (Exception e) {
            throw new BusinessRuleException("Falha na leitura do arquivo CSV: " + e.getMessage());
        }

        // Persistência em lote estritamente imutável (INSERTs)
        if (!validPrices.isEmpty()) {
            competitorPriceRepository.saveAll(validPrices);
        }

        return new CompetitorIngestionResponse(
                totalProcessed,
                validPrices.size(),
                errors.size(),
                errors
        );
    }

    private boolean isHeader(String[] columns) {
        for (String col : columns) {
            String lower = col.trim().toLowerCase();
            if (lower.contains("sku") || lower.contains("ean") || lower.contains("competitor")
                    || lower.contains("concorrente") || lower.contains("price") || lower.contains("preco")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> parseHeaders(String[] columns) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < columns.length; i++) {
            String header = columns[i].trim().toLowerCase();
            if (header.contains("sku")) map.put("sku", i);
            else if (header.contains("ean")) map.put("ean", i);
            else if (header.contains("competitor") || header.contains("concorrente")) map.put("competitor", i);
            else if (header.contains("price") || header.contains("preco")) map.put("price", i);
        }
        return map;
    }

    private String extractColumn(String[] columns, Map<String, Integer> headerMap, String key, int defaultIndex) {
        if (headerMap != null && headerMap.containsKey(key)) {
            int idx = headerMap.get(key);
            return (idx < columns.length) ? cleanQuotes(columns[idx]) : null;
        }
        return (defaultIndex < columns.length) ? cleanQuotes(columns[defaultIndex]) : null;
    }

    private String cleanQuotes(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}