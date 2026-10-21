package com.guidaerique.pricing_copilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.security.domain.Role;
import com.guidaerique.pricing_copilot.security.dto.LoginRequest;
import com.guidaerique.pricing_copilot.security.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthSecurityIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userEmail;
    private final String rawPassword = "Password@123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userEmail = "operator-" + UUID.randomUUID() + "@pricingcopilot.com";
        AppUser user = new AppUser(
                userEmail,
                passwordEncoder.encode(rawPassword),
                Role.OPERATOR
        );
        userRepository.save(user);
    }

    @Test
    @DisplayName("Deve autenticar com sucesso e retornar token JWT com tipo Bearer")
    void deveAutenticarComSucesso() throws Exception {
        LoginRequest request = new LoginRequest(userEmail, rawPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", notNullValue()));
    }

    @Test
    @DisplayName("Deve rejeitar autenticação com senha incorreta e retornar ProblemDetail 401")
    void deveRejeitarSenhaIncorreta() throws Exception {
        LoginRequest request = new LoginRequest(userEmail, "SenhaErrada");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title", is("Não Autorizado")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.detail", notNullValue()));
    }

    @Test
    @DisplayName("Deve rejeitar autenticação para usuário inexistente")
    void deveRejeitarUsuarioInexistente() throws Exception {
        LoginRequest request = new LoginRequest("inexistente@pricingcopilot.com", rawPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Deve bloquear requisições a rotas privadas sem header Authorization")
    void deveBloquearRotaPrivadaSemToken() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve autenticar e acessar rota privada utilizando o token JWT emitido")
    void deveAcessarRotaPrivadaComJwtValido() throws Exception {
        LoginRequest request = new LoginRequest(userEmail, rawPassword);

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(responseBody).get("accessToken").asText();

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}