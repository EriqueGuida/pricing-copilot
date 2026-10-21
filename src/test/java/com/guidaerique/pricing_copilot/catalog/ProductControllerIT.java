package com.guidaerique.pricing_copilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.dto.ProductCreateRequest;
import com.guidaerique.pricing_copilot.catalog.dto.ProductUpdateRequest;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ProductControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        category = categoryRepository.save(new Category(
                "Hardware-" + UUID.randomUUID(),
                Vertical.COMPUTER_GAMING,
                new BigDecimal("0.2000"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.2000")
        ));
    }

    // ---------------------------------------------------------------
    // Matriz de Autorização RBAC
    // ---------------------------------------------------------------

    @Test
    @DisplayName("OPERATOR deve conseguir cadastrar produto com preço dentro da margem (201 Created)")
    @WithMockUser(roles = "OPERATOR")
    void operatorDeveCadastrarProdutoValido() throws Exception {
        // Custo = 100.00 -> Piso Margem (20%): 100 / (1 - 0.20) = 125.00
        // Preço atual = 130.00 (Válido: dentro da faixa de margem)
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        ProductCreateRequest request = new ProductCreateRequest(
                sku,
                null,
                "Teclado Mecânico RGB",
                category.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("130.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.sku", is(sku)))
                .andExpect(jsonPath("$.costPrice", is(100.00)))
                .andExpect(jsonPath("$.currentPrice", is(130.00)))
                .andExpect(jsonPath("$.version", is(0))); // Concorrência otimista inicializa em zero
    }

    @Test
    @DisplayName("EXECUTIVE deve receber 403 Forbidden ao tentar cadastrar produto")
    @WithMockUser(roles = "EXECUTIVE")
    void executiveNaoDeveCadastrarProduto() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-" + UUID.randomUUID(),
                null,
                "Mousepad Gamer",
                category.getId(),
                new BigDecimal("50.00"),
                new BigDecimal("70.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Regras de Negócio e Margem Comercial (RF-01)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve rejeitar produto com preço inicial abaixo do piso de margem (422 RFC 7807)")
    @WithMockUser(roles = "ADMIN")
    void deveRejeitarProdutoComPrecoAbaixoDoPiso() throws Exception {
        // Custo = 100.00, Margem Mínima = 20% -> Piso = 125.00.
        // Preço de venda informado = 110.00 (Margem efetiva = 9.09% < 20.00%)
        ProductCreateRequest requestInvalido = new ProductCreateRequest(
                "SKU-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Headset Surround 7.1",
                category.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("110.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Violação de Regra de Negócio")))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.status", is(422)));
    }

    @Test
    @DisplayName("Deve rejeitar produto com preço inicial acima do teto de margem (422 RFC 7807)")
    @WithMockUser(roles = "ADMIN")
    void deveRejeitarProdutoComPrecoAcimaDoTeto() throws Exception {
        // Custo = 100.00, Margem Máxima = 50% -> Teto = 100 / (1 - 0.50) = 200.00.
        // Preço informado = 250.00 (Excede o teto permitido)
        ProductCreateRequest requestInvalido = new ProductCreateRequest(
                "SKU-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Monitor Gamer 240Hz",
                category.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("250.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Violação de Regra de Negócio")));
    }

    @Test
    @DisplayName("Deve rejeitar cadastro de produto com SKU duplicado (422 RFC 7807)")
    @WithMockUser(roles = "ADMIN")
    void deveRejeitarSkuDuplicado() throws Exception {
        String sku = "SKU-DUP-" + UUID.randomUUID().toString().substring(0, 8);

        ProductCreateRequest primeiroRequest = new ProductCreateRequest(
                sku,
                null,
                "Produto Original",
                category.getId(),
                new BigDecimal("50.00"),
                new BigDecimal("70.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primeiroRequest)))
                .andExpect(status().isCreated());

        ProductCreateRequest segundoRequest = new ProductCreateRequest(
                sku,
                null,
                "Produto Duplicado",
                category.getId(),
                new BigDecimal("50.00"),
                new BigDecimal("70.00")
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(segundoRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", is("Já existe um produto com o SKU: " + sku)));
    }

    @Test
    @DisplayName("Deve permitir atualização cadastral do produto por OPERATOR")
    @WithMockUser(roles = "OPERATOR")
    void operatorDeveAtualizarProduto() throws Exception {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        ProductCreateRequest createRequest = new ProductCreateRequest(
                sku,
                "7891234567890",
                "Webcam HD",
                category.getId(),
                new BigDecimal("80.00"),
                new BigDecimal("110.00")
        );

        String responseBody = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readTree(responseBody).get("id").asLong();

        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "7891234567890",
                "Webcam Full HD 1080p",
                category.getId(),
                new BigDecimal("85.00")
        );

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Webcam Full HD 1080p")))
                .andExpect(jsonPath("$.costPrice", is(85.00)));
    }
}