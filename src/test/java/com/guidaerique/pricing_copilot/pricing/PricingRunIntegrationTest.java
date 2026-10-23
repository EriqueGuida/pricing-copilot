package com.guidaerique.pricing_copilot.pricing;

import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.client.PriceSuggestionClient;
import com.guidaerique.pricing_copilot.pricing.client.dto.ExplanationResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionResultResponse;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import com.guidaerique.pricing_copilot.IntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class PricingRunIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private AlertRepository alertRepository;

    @MockBean
    private PriceSuggestionClient suggestionClient;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = new Category();
        category.setName("Gaming-" + suffix);
        category.setVertical(Vertical.COMPUTER_GAMING);
        category.setMinMargin(new BigDecimal("0.1000"));
        category.setMaxMargin(new BigDecimal("0.4000"));
        category.setMaxVariation(new BigDecimal("0.2000"));
        category = categoryRepository.save(category);

        product1 = new Product(
                "SKU-01-" + suffix,
                null,
                "Teclado Mecânico " + suffix,
                category,
                new BigDecimal("100.00"),
                new BigDecimal("130.00")
        );
        product1.setActive(true);
        product1 = productRepository.save(product1);

        product2 = new Product(
                "SKU-02-" + suffix,
                null,
                "Headset Wireless " + suffix,
                category,
                new BigDecimal("200.00"),
                new BigDecimal("250.00")
        );
        product2.setActive(true);
        product2 = productRepository.save(product2);
    }

    private void mockAiResponse(String modelVersion, SuggestionResultResponse... specificResults) {
        Map<String, SuggestionResultResponse> map = Arrays.stream(specificResults)
                .collect(Collectors.toMap(SuggestionResultResponse::sku, Function.identity()));

        when(suggestionClient.requestSuggestions(any())).thenAnswer(invocation -> {
            SuggestionBatchRequest req = invocation.getArgument(0);
            List<SuggestionResultResponse> results = req.items().stream().map(item -> {
                if (map.containsKey(item.sku())) {
                    return map.get(item.sku());
                }
                // Resposta neutra para produtos pré-existentes no banco
                BigDecimal current = new BigDecimal(item.currentPrice());
                return new SuggestionResultResponse(
                        item.sku(),
                        current,
                        "BASELINE_FALLBACK",
                        new ExplanationResponse(0.0, null, current, "NONE", "HIGH")
                );
            }).toList();
            return new SuggestionBatchResponse(modelVersion, results);
        });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cenário 1: Ciclo com múltiplos itens atualiza catálogo e persiste auditoria em price_history")
    void shouldExecutePricingCycleSuccessfullyForMultipleItems() throws Exception {
        BigDecimal newPrice1 = new BigDecimal("140.00");
        BigDecimal newPrice2 = new BigDecimal("260.00");

        SuggestionResultResponse res1 = new SuggestionResultResponse(
                product1.getSku(),
                newPrice1,
                "ELASTICITY",
                new ExplanationResponse(-1.8, new BigDecimal("142.00"), newPrice1, "NONE", "HIGH")
        );
        SuggestionResultResponse res2 = new SuggestionResultResponse(
                product2.getSku(),
                newPrice2,
                "ELASTICITY",
                new ExplanationResponse(-2.0, new BigDecimal("265.00"), newPrice2, "NONE", "HIGH")
        );

        mockAiResponse("v1.2.0", res1, res2);

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(PricingRunStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.modelVersion").value("v1.2.0"));

        Product updatedP1 = productRepository.findById(product1.getId()).orElseThrow();
        Product updatedP2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(updatedP1.getCurrentPrice()).isEqualByComparingTo(newPrice1);
        assertThat(updatedP2.getCurrentPrice()).isEqualByComparingTo(newPrice2);

        List<PriceHistory> history = priceHistoryRepository.findAll().stream()
                .filter(h -> h.getProduct().getId().equals(product1.getId()) || h.getProduct().getId().equals(product2.getId()))
                .toList();

        assertThat(history).hasSize(2);
        assertThat(history).allMatch(h -> h.getOrigin() == PriceOrigin.AI);
        assertThat(history).allMatch(h -> "v1.2.0".equals(h.getModelVersion()));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("Cenário 2: Isolamento Transacional (REQUIRES_NEW) - Um item com erro não aborta os válidos")
    void shouldIsolateItemFailuresAndCompleteAsPartial() throws Exception {
        BigDecimal invalidPrice = new BigDecimal("180.00"); // Viola variação máxima (> 20%)
        BigDecimal validPrice = new BigDecimal("260.00");

        SuggestionResultResponse res1 = new SuggestionResultResponse(
                product1.getSku(),
                invalidPrice,
                "ELASTICITY",
                new ExplanationResponse(-1.5, null, invalidPrice, "NONE", "HIGH")
        );
        SuggestionResultResponse res2 = new SuggestionResultResponse(
                product2.getSku(),
                validPrice,
                "ELASTICITY",
                new ExplanationResponse(-2.0, null, validPrice, "NONE", "HIGH")
        );

        mockAiResponse("v1.0.0", res1, res2);

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(PricingRunStatus.PARTIAL.name()));

        Product p1 = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(p1.getCurrentPrice()).isEqualByComparingTo("130.00");

        Product p2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(p2.getCurrentPrice()).isEqualByComparingTo(validPrice);

        List<PriceHistory> historyP1 = priceHistoryRepository.findAll().stream()
                .filter(h -> h.getProduct().getId().equals(product1.getId()))
                .toList();
        List<PriceHistory> historyP2 = priceHistoryRepository.findAll().stream()
                .filter(h -> h.getProduct().getId().equals(product2.getId()))
                .toList();

        assertThat(historyP1).isEmpty();
        assertThat(historyP2).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cenário 3: Sugestão idêntica ao preço atual deve ser ignorada")
    void shouldSkipWhenSuggestedPriceEqualsCurrentPrice() throws Exception {
        BigDecimal identicalPrice = new BigDecimal("130.00");

        SuggestionResultResponse res1 = new SuggestionResultResponse(
                product1.getSku(),
                identicalPrice,
                "BASELINE_FALLBACK",
                new ExplanationResponse(0.0, null, identicalPrice, "NONE", "HIGH")
        );

        product2.setActive(false);
        productRepository.save(product2);

        mockAiResponse("v1.0.0", res1);

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(PricingRunStatus.SUCCESS.name()));

        long historyCount = priceHistoryRepository.findAll().stream()
                .filter(h -> h.getProduct().getId().equals(product1.getId()))
                .count();

        assertThat(historyCount).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cenário 4: Sugestão com LOW_CONFIDENCE atualiza preço e emite Alerta")
    void shouldCreateAlertWhenAiReturnsLowConfidence() throws Exception {
        BigDecimal newPrice = new BigDecimal("135.00");

        SuggestionResultResponse res = new SuggestionResultResponse(
                product1.getSku(),
                newPrice,
                "BASELINE_FALLBACK",
                new ExplanationResponse(-0.8, null, newPrice, "NONE", "LOW")
        );

        product2.setActive(false);
        productRepository.save(product2);

        mockAiResponse("v1.0.0", res);

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        List<Alert> alerts = alertRepository.findAll().stream()
                .filter(a -> a.getProduct().getId().equals(product1.getId()))
                .toList();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.LOW_CONFIDENCE);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("Cenário 5: Produtos com trava manual (manual_lock_until) ativa NÃO são enviados para a IA")
    void shouldIgnoreProductsUnderManualLock() throws Exception {
        product1.setManualLockUntil(LocalDateTime.now().plusHours(24));
        productRepository.save(product1);

        BigDecimal newPrice2 = new BigDecimal("255.00");
        SuggestionResultResponse res2 = new SuggestionResultResponse(
                product2.getSku(),
                newPrice2,
                "ELASTICITY",
                new ExplanationResponse(-1.9, null, newPrice2, "NONE", "HIGH")
        );

        mockAiResponse("v1.0.0", res2);

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        Product p1 = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(p1.getCurrentPrice()).isEqualByComparingTo("130.00");

        Product p2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(p2.getCurrentPrice()).isEqualByComparingTo(newPrice2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cenário 6: Microsserviço de IA inacessível gera alerta AI_UNAVAILABLE e encerra como FAILED")
    void shouldHandleAiTimeoutGracefully() throws Exception {
        when(suggestionClient.requestSuggestions(any()))
                .thenThrow(new ResourceAccessException("Read timeout: IA não respondeu em 2000ms"));

        mockMvc.perform(post("/api/pricing/runs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(PricingRunStatus.FAILED.name()));

        assertThat(productRepository.findById(product1.getId()).orElseThrow().getCurrentPrice())
                .isEqualByComparingTo("130.00");
        assertThat(productRepository.findById(product2.getId()).orElseThrow().getCurrentPrice())
                .isEqualByComparingTo("250.00");

        List<Alert> alerts = alertRepository.findAll().stream()
                .filter(a -> a.getProduct().getId().equals(product1.getId()) || a.getProduct().getId().equals(product2.getId()))
                .toList();

        assertThat(alerts).hasSize(2);
        assertThat(alerts).allMatch(a -> a.getType() == AlertType.AI_UNAVAILABLE);
    }
}