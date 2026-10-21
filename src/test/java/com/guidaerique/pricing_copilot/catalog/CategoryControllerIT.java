package com.guidaerique.pricing_copilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.dto.CategoryRequest;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CategoryControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private CategoryRequest criarRequestValido(String nome) {
        return new CategoryRequest(
                nome,
                Vertical.CONSUMER_ELECTRONICS,
                new BigDecimal("0.1000"),
                new BigDecimal("0.4000"),
                new BigDecimal("0.2000")
        );
    }

    // ---------------------------------------------------------------
    // Matriz de Autorização RBAC
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ADMIN deve conseguir cadastrar uma nova categoria (201 Created)")
    @WithMockUser(roles = "ADMIN")
    void adminDeveCriarCategoria() throws Exception {
        CategoryRequest request = criarRequestValido("Smartphones-" + UUID.randomUUID());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is(request.name())))
                .andExpect(jsonPath("$.vertical", is("CONSUMER_ELECTRONICS")));
    }

    @Test
    @DisplayName("OPERATOR deve receber 403 Forbidden ao tentar cadastrar categoria")
    @WithMockUser(roles = "OPERATOR")
    void operatorNaoDeveCriarCategoria() throws Exception {
        CategoryRequest request = criarRequestValido("Tablets-" + UUID.randomUUID());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("EXECUTIVE deve receber 403 Forbidden ao tentar atualizar categoria")
    @WithMockUser(roles = "EXECUTIVE")
    void executiveNaoDeveAtualizarCategoria() throws Exception {
        Category category = categoryRepository.save(new Category(
                "Cat-" + UUID.randomUUID(),
                Vertical.SMART_HOME,
                new BigDecimal("0.1500"),
                new BigDecimal("0.3500"),
                new BigDecimal("0.1000")
        ));

        CategoryRequest updateRequest = criarRequestValido(category.getName());

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Todos os perfis autenticados devem conseguir consultar categorias")
    @WithMockUser(roles = "EXECUTIVE")
    void executiveDeveConsultarCategorias() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OPERATOR deve receber 403 Forbidden ao tentar excluir categoria")
    @WithMockUser(roles = "OPERATOR")
    void operatorNaoDeveExcluirCategoria() throws Exception {
        mockMvc.perform(delete("/api/categories/999999"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Regras de Negócio e Validações
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Deve rejeitar categoria com margem mínima maior ou igual à máxima (422 RFC 7807)")
    @WithMockUser(roles = "ADMIN")
    void deveRejeitarMargemMinimaMaiorQueMaxima() throws Exception {
        CategoryRequest requestInvalido = new CategoryRequest(
                "Invalida-" + UUID.randomUUID(),
                Vertical.COMPUTER_GAMING,
                new BigDecimal("0.3000"),
                new BigDecimal("0.2000"), // Mínima maior que a máxima
                new BigDecimal("0.1000")
        );

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Violação de Regra de Negócio")))
                .andExpect(jsonPath("$.status", is(422)));
    }

    @Test
    @DisplayName("Deve rejeitar categoria com variação superior a 50% (400 Bad Request via Bean Validation)")
    @WithMockUser(roles = "ADMIN")
    void deveRejeitarVariacaoSuperiorAoMaximoPermitido() throws Exception {
        CategoryRequest requestInvalido = new CategoryRequest(
                "VariacaoAlta-" + UUID.randomUUID(),
                Vertical.COMPUTER_GAMING,
                new BigDecimal("0.1000"),
                new BigDecimal("0.3000"),
                new BigDecimal("0.5001") // Excede o teto de 0.5000
        );

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxVariation", notNullValue()));
    }
}