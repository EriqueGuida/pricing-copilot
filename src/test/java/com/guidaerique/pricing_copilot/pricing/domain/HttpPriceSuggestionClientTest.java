package com.guidaerique.pricing_copilot.pricing;

import com.guidaerique.pricing_copilot.pricing.client.HttpPriceSuggestionClient;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(HttpPriceSuggestionClient.class)
class HttpPriceSuggestionClientTest {

    @Autowired
    private HttpPriceSuggestionClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    @DisplayName("Deve serializar decimais em string e desserializar resposta da IA com precisão monetária")
    void shouldSerializeMonetaryStringsCorrectly() {
        SuggestionItemRequest item = new SuggestionItemRequest(
                "SKU-TEST-01",
                1L,
                "100.00",
                "130.00",
                "111.12",
                "166.66",
                List.of("128.00", "135.50")
        );
        SuggestionBatchRequest request = new SuggestionBatchRequest(10L, List.of(item));

        String mockAiResponse = """
                {
                  "model_version": "v1.0.0",
                  "results": [
                    {
                      "sku": "SKU-TEST-01",
                      "suggested_price": "145.50",
                      "strategy": "ELASTICITY",
                      "explanation": {
                        "elasticity": -1.82,
                        "competitor_median": "131.75",
                        "unconstrained_optimum": "148.00",
                        "clipped_by": "NONE",
                        "confidence": "HIGH"
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo("/v1/suggestions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Valida a serialização com 2 casas em formato string
                .andExpect(jsonPath("$.run_id").value(10))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-TEST-01"))
                .andExpect(jsonPath("$.items[0].cost_price").value("100.00"))
                .andExpect(jsonPath("$.items[0].min_price").value("111.12"))
                .andRespond(withSuccess(mockAiResponse, MediaType.APPLICATION_JSON));

        SuggestionBatchResponse response = client.requestSuggestions(request);

        assertThat(response.modelVersion()).isEqualTo("v1.0.0");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).suggestedPrice()).isEqualByComparingTo(new BigDecimal("145.50"));
        assertThat(response.results().get(0).explanation().elasticity()).isEqualTo(-1.82);

        server.verify();
    }
}