package com.guidaerique.pricing_copilot.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.dto.PriceOverrideRequest;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.security.domain.Role;
import com.guidaerique.pricing_copilot.security.repository.AppUserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PriceOverrideControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Product product;
    private static final String OPERATOR_EMAIL = "operator-override@test.com";
    private static final String ADMIN_EMAIL = "admin-override@test.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Custo = 100.00
        // Piso Margem (20%): 125.00
        // Teto Margem (40%): 166.66
        Category category = categoryRepository.save(new Category(
                "Cat-Override-" + UUID.randomUUID(),
                Vertical.SMART_HOME,
                new BigDecimal("0.2000"),
                new BigDecimal("0.4000"),
                new BigDecimal("0.1500")
        ));

        product = productRepository.save(new Product(
                "SKU-OVR-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Lâmpada Inteligente Wi-Fi",
                category,
                new BigDecimal("100.00"),
                new BigDecimal("140.00")
        ));

        if (!userRepository.existsByEmail(OPERATOR_EMAIL)) {
            userRepository.save(new AppUser(OPERATOR_EMAIL, "pass", Role.OPERATOR));
        }
        if (!userRepository.existsByEmail(ADMIN_EMAIL)) {
            userRepository.save(new AppUser(ADMIN_EMAIL, "pass", Role.ADMIN));
        }
    }

    @Test
    @DisplayName("OPERATOR deve conseguir aplicar override DENTRO da margem permitida")
    @WithMockUser(username = OPERATOR_EMAIL, roles = "OPERATOR")
    void operatorDeveAplicarOverrideDentroDaMargem() throws Exception {
        PriceOverrideRequest request = new PriceOverrideRequest(
                new BigDecimal("150.00"), // Dentro de [125.00, 166.66]
                "Ajuste operacional de rotina",
                12
        );

        mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice", is(150.00)))
                .andExpect(jsonPath("$.manualLockUntil", notNullValue()));
    }

    @Test
    @DisplayName("OPERATOR deve receber 403 Forbidden ao tentar forçar preço FORA da margem")
    @WithMockUser(username = OPERATOR_EMAIL, roles = "OPERATOR")
    void operatorDeveReceber403ForaDaMargem() throws Exception {
        PriceOverrideRequest requestAbaixo = new PriceOverrideRequest(
                new BigDecimal("110.00"), // Abaixo do piso de 125.00
                "Tentativa de queima indevida",
                24
        );

        mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestAbaixo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title", is("Acesso Negado")));
    }

    @Test
    @DisplayName("ADMIN deve conseguir aplicar override FORA da margem com justificativa >= 10 caracteres")
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void adminDeveAplicarOverrideForaDaMargemComJustificativa() throws Exception {
        PriceOverrideRequest request = new PriceOverrideRequest(
                new BigDecimal("115.00"),
                "Autorização formal da diretoria comercial para queima",
                24
        );

        mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice", is(115.00)));
    }

    @Test
    @DisplayName("ADMIN deve receber 422 ao tentar override sem justificativa válida")
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void adminSemJustificativaDeveReceber422() throws Exception {
        PriceOverrideRequest request = new PriceOverrideRequest(
                new BigDecimal("115.00"),
                "Curto", // < 10 caracteres
                24
        );

        mockMvc.perform(put("/api/products/" + product.getId() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Violação de Regra de Negócio")));
    }

    @Test
    @DisplayName("Deve remover o lock manual via DELETE /api/products/{id}/lock")
    @WithMockUser(username = OPERATOR_EMAIL, roles = "OPERATOR")
    void deveRemoverLockManual() throws Exception {
        mockMvc.perform(delete("/api/products/" + product.getId() + "/lock"))
                .andExpect(status().isNoContent());

        Product productSemLock = productRepository.findById(product.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(productSemLock.getManualLockUntil());
    }

    @Test
    @DisplayName("Deve resolver um alerta operacional via PATCH /api/alerts/{id}/resolve")
    @WithMockUser(username = OPERATOR_EMAIL, roles = "OPERATOR")
    void deveResolverAlertaOperacional() throws Exception {
        Alert alerta = alertRepository.save(new Alert(
                product,
                AlertType.MARGIN_BREACH,
                "Alerta de margem para teste de resolução"
        ));

        mockMvc.perform(patch("/api/alerts/" + alerta.getId() + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));
    }
}