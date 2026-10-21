package com.guidaerique.pricing_copilot.competitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPrice;
import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPriceSourceType;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorPriceItemRequest;
import com.guidaerique.pricing_copilot.competitor.repository.CompetitorPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CompetitorPriceIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CompetitorPriceRepository competitorPriceRepository;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Category category = categoryRepository.save(new Category(
                "Cat-Comp-" + UUID.randomUUID(),
                Vertical.CONSUMER_ELECTRONICS,
                new BigDecimal("0.1000"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.2000")
        ));

        // Gera EANs únicos de 13 dígitos numéricos
        String ean1 = "789" + String.format("%010d", Math.abs(UUID.randomUUID().hashCode()));
        String ean2 = "789" + String.format("%010d", Math.abs(UUID.randomUUID().hashCode() + 1));

        product1 = productRepository.save(new Product(
                "SKU-CP-01-" + UUID.randomUUID().toString().substring(0, 6),
                ean1,
                "Smartphone Alpha",
                category,
                new BigDecimal("500.00"),
                new BigDecimal("800.00")
        ));

        product2 = productRepository.save(new Product(
                "SKU-CP-02-" + UUID.randomUUID().toString().substring(0, 6),
                ean2,
                "Smartphone Beta",
                category,
                new BigDecimal("600.00"),
                new BigDecimal("950.00")
        ));
    }

    // ---------------------------------------------------------------
    // 1. Ingestão Mista via CSV
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve processar CSV misto: persistir linhas válidas e detalhar erros das linhas inválidas")
    @WithMockUser(roles = "OPERATOR")
    void deveProcessarCsvMistoComSucessoResiliente() throws Exception {
        // Linha 1: Header
        // Linha 2: Válida (SKU 1, Preço 799.90)
        // Linha 3: Inválida (SKU inexistente)
        // Linha 4: Inválida (Preço negativo)
        // Linha 5: Inválida (Preço com 3 casas decimais)
        // Linha 6: Válida via EAN do Produto 2
        String csvContent = "sku,ean,competitor,price\n"
                + product1.getSku() + ",,Magazine Luiza,799.90\n"
                + "SKU-INEXISTENTE,,Kabum,150.00\n"
                + product1.getSku() + ",,Amazon,-10.00\n"
                + product1.getSku() + ",,Mercado Livre,750.005\n"
                + "," + product2.getEan() + ",Amazon,920.00\n";

        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "competitor_prices.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/competitor-prices/import")
                        .file(csvFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed", is(5)))
                .andExpect(jsonPath("$.successCount", is(2)))
                .andExpect(jsonPath("$.failureCount", is(3)))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].identifier", is("SKU-INEXISTENTE")))
                .andExpect(jsonPath("$.errors[0].message", is("Produto não encontrado no catálogo")))
                .andExpect(jsonPath("$.errors[1].identifier", is(product1.getSku())))
                .andExpect(jsonPath("$.errors[1].message", is("O preço deve ser estritamente positivo: -10.00")))
                .andExpect(jsonPath("$.errors[2].identifier", is(product1.getSku())))
                .andExpect(jsonPath("$.errors[2].message", is("O preço possui mais de 2 casas decimais: 750.005")));

        // Validação no banco de dados (Apenas 2 registros persistidos com source CSV)
        List<CompetitorPrice> precosProd1 = competitorPriceRepository.findByProductIdOrderByCollectedAtDesc(product1.getId());
        assertThat(precosProd1).hasSize(1);
        assertThat(precosProd1.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("799.90"));
        assertThat(precosProd1.get(0).getSource()).isEqualTo(CompetitorPriceSourceType.CSV);

        List<CompetitorPrice> precosProd2 = competitorPriceRepository.findByProductIdOrderByCollectedAtDesc(product2.getId());
        assertThat(precosProd2).hasSize(1);
        assertThat(precosProd2.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("920.00"));
        assertThat(precosProd2.get(0).getSource()).isEqualTo(CompetitorPriceSourceType.CSV);
    }

    // ---------------------------------------------------------------
    // 2. Ingestão Direta via JSON (Individual e Lote)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve processar lote de itens via JSON com sucesso")
    @WithMockUser(roles = "ADMIN")
    void deveProcessarLoteJsonComSucesso() throws Exception {
        List<CompetitorPriceItemRequest> requests = List.of(
                new CompetitorPriceItemRequest(product1.getSku(), null, "Kabum", new BigDecimal("780.00"), null),
                new CompetitorPriceItemRequest("SKU-FANTASMA", null, "FastShop", new BigDecimal("790.00"), null)
        );

        mockMvc.perform(post("/api/competitor-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed", is(2)))
                .andExpect(jsonPath("$.successCount", is(1)))
                .andExpect(jsonPath("$.failureCount", is(1)))
                .andExpect(jsonPath("$.errors[0].identifier", is("SKU-FANTASMA")));

        List<CompetitorPrice> prices = competitorPriceRepository.findByProductIdOrderByCollectedAtDesc(product1.getId());
        assertThat(prices).anyMatch(p -> p.getPrice().compareTo(new BigDecimal("780.00")) == 0
                && p.getSource() == CompetitorPriceSourceType.API);
    }

    @Test
    @DisplayName("Deve suportar ingestão individual via objeto JSON direto")
    @WithMockUser(roles = "OPERATOR")
    void deveProcessarItemIndividualJson() throws Exception {
        CompetitorPriceItemRequest single = new CompetitorPriceItemRequest(
                product1.getSku(),
                null,
                "Mercado Livre",
                new BigDecimal("785.50"),
                null
        );

        mockMvc.perform(post("/api/competitor-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(single)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed", is(1)))
                .andExpect(jsonPath("$.successCount", is(1)))
                .andExpect(jsonPath("$.failureCount", is(0)));
    }

    // ---------------------------------------------------------------
    // 3. Matriz RBAC
    // ---------------------------------------------------------------

    @Test
    @DisplayName("EXECUTIVE deve receber 403 Forbidden ao tentar importar preços de concorrentes")
    @WithMockUser(roles = "EXECUTIVE")
    void executiveNaoDeveImportarConcorrentes() throws Exception {
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "teste.csv", "text/csv", "sku,competitor,price".getBytes()
        );

        mockMvc.perform(multipart("/api/competitor-prices/import").file(csvFile))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/competitor-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }
}