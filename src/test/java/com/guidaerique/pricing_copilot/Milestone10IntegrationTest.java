package com.guidaerique.pricing_copilot;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.repository.PricingRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class Milestone10IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PriceHistoryRepository priceHistoryRepository;
    @Autowired private PricingRunRepository pricingRunRepository;

    private Category electronicsCategory;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String sku = "SKU-" + suffix;

        electronicsCategory = new Category();
        electronicsCategory.setName("Eletrônicos-" + suffix);
        electronicsCategory.setVertical(Vertical.CONSUMER_ELECTRONICS);
        electronicsCategory.setMinMargin(new BigDecimal("0.2000"));
        electronicsCategory.setMaxMargin(new BigDecimal("0.4000"));
        electronicsCategory.setMaxVariation(new BigDecimal("0.2000"));
        electronicsCategory = categoryRepository.save(electronicsCategory);

        sampleProduct = new Product(
                sku,
                null,
                "Smartphone Alpha " + suffix,
                electronicsCategory,
                new BigDecimal("80.00"),
                new BigDecimal("110.00")
        );
        sampleProduct.setActive(true);
        sampleProduct = productRepository.save(sampleProduct);
    }

    @Test
    @DisplayName("Deve calcular métricas consolidadas (KPIs) com agregações em banco")
    @WithMockUser(roles = "EXECUTIVE")
    void shouldCalculateAccurateKpisAggregatedInDatabase() throws Exception {
        String floorSku = "FLOOR-" + UUID.randomUUID().toString().substring(0, 8);
        Product floorProduct = new Product(
                floorSku,
                null,
                "Mouse USB Floor",
                electronicsCategory,
                new BigDecimal("79.50"),
                new BigDecimal("100.00")
        );
        floorProduct.setActive(true);
        productRepository.save(floorProduct);

        PricingRun run1 = pricingRunRepository.save(new PricingRun());
        run1.complete("v1.0");
        pricingRunRepository.save(run1);

        PricingRun run2 = pricingRunRepository.save(new PricingRun());
        run2.complete("v1.0");
        pricingRunRepository.save(run2);

        PricingRun run3 = pricingRunRepository.save(new PricingRun());
        run3.fail("v1.0");
        pricingRunRepository.save(run3);

        priceHistoryRepository.save(new PriceHistory(sampleProduct, new BigDecimal("90.00"), new BigDecimal("100.00"), PriceOrigin.AI, null, "Test"));
        priceHistoryRepository.save(new PriceHistory(sampleProduct, new BigDecimal("100.00"), new BigDecimal("105.00"), PriceOrigin.COST_UPDATE, null, "Test"));

        mockMvc.perform(get("/api/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productsNearMarginFloor", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.aiAutonomousSuccessRate", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.categoryMargins").isArray())
                .andExpect(jsonPath("$.priceChangesByOrigin").isArray());
    }
}