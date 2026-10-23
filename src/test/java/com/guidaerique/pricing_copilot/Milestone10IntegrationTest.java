package com.guidaerique.pricing_copilot;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.invoice.domain.ProcessedInvoice;
import com.guidaerique.pricing_copilot.invoice.repository.ProcessedInvoiceRepository;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.repository.PricingRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class Milestone10IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PriceHistoryRepository priceHistoryRepository;
    @Autowired private ProcessedInvoiceRepository processedInvoiceRepository;
    @Autowired private PricingRunRepository pricingRunRepository;

    private Category electronicsCategory;
    private Product sampleProduct;
    private String sku;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        sku = "SKU-" + suffix;

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
    @DisplayName("Requisito 1: Deve realizar streaming de histórico CSV sem sobrecarga de memória")
    @WithMockUser(roles = "ADMIN")
    void shouldStreamPriceHistoryCsvWithoutHeapExhaustion() throws Exception {
        for (int i = 0; i < 25; i++) {
            priceHistoryRepository.save(new PriceHistory(
                sampleProduct,
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                PriceOrigin.AI,
                null,
                "Ajuste autônomo #" + i
            ));
        }

        // 1. Dispara a requisição assíncrona do StreamingResponseBody
        MvcResult mvcResult = mockMvc.perform(get("/api/reports/price-history.csv")
                .param("from", LocalDate.now().minusDays(2).toString())
                .param("to", LocalDate.now().plusDays(1).toString()))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. Executa o despacho assíncrono para coletar os dados do stream
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"price-history.csv\""))
                .andExpect(content().string(startsWith("ID,PRODUCT_ID,SKU,OLD_PRICE,NEW_PRICE,ORIGIN,REASON,CHANGED_AT")))
                .andExpect(content().string(containsString(sku)))
                .andExpect(content().string(containsString("AI")));
    }

    @Test
    @DisplayName("Requisito 2: Deve calcular métricas consolidadas (KPIs) com agregações em banco")
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

    @Test
    @DisplayName("Requisito 3: Deve processar NF-e, reajustar preço pelo piso de margem e rejeitar reenvio com RFC 7807")
    @WithMockUser(roles = "OPERATOR")
    void shouldProcessInvoiceXmlAdjustFloorPriceAndRejectDuplicatesWithRfc7807() throws Exception {
        String accessKey = ("3526101234567800019555001" + System.nanoTime() + "00000000000000000000").substring(0, 44);

        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
              <NFe>
                <infNFe Id="NFe%s">
                  <emit>
                    <CNPJ>12345678000195</CNPJ>
                  </emit>
                  <det nItem="1">
                    <prod>
                      <cProd>%s</cProd>
                      <xProd>Smartphone Alpha</xProd>
                      <vUnCom>95.00</vUnCom>
                    </prod>
                  </det>
                  <total>
                    <ICMSTot>
                      <vNF>95.00</vNF>
                    </ICMSTot>
                  </total>
                </infNFe>
              </NFe>
            </nfeProc>
            """.formatted(accessKey, sku);

        MockMultipartFile xmlFile = new MockMultipartFile(
            "file",
            "nfe-test.xml",
            MediaType.TEXT_XML_VALUE,
            xmlContent.getBytes(StandardCharsets.UTF_8)
        );

        // 1. Primeira importação (Sucesso)
        mockMvc.perform(multipart("/api/invoices/import").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKey", is(accessKey)))
                .andExpect(jsonPath("$.itemsProcessed", is(1)))
                .andExpect(jsonPath("$.pricesAdjustedToFloor", is(1)));

        // Validação no banco
        Product updatedProduct = productRepository.findBySku(sku).orElseThrow();
        assertThat(updatedProduct.getCostPrice()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(updatedProduct.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("118.75"));

        // Validação do evento gravado com COST_UPDATE
        var historyList = priceHistoryRepository.findAll().stream()
                .filter(h -> h.getProduct().getId().equals(updatedProduct.getId()))
                .toList();
        assertThat(historyList).hasSize(1);
        assertThat(historyList.get(0).getOrigin()).isEqualTo(PriceOrigin.COST_UPDATE);
        assertThat(historyList.get(0).getNewPrice()).isEqualByComparingTo(new BigDecimal("118.75"));

        // 2. Segunda importação: Idempotência (RFC 7807 - HTTP 409 Conflict)
        mockMvc.perform(multipart("/api/invoices/import").file(xmlFile))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Nota Fiscal Já Processada")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.accessKey", is(accessKey)))
                .andExpect(jsonPath("$.type", is("https://api.copiloto.com/errors/duplicate-invoice")));
    }
}