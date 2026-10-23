package com.guidaerique.pricing_copilot.pricing.client;

import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "pricing.ai-client.type", havingValue = "http", matchIfMissing = true)
public class HttpPriceSuggestionClient implements PriceSuggestionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPriceSuggestionClient.class);
    private final RestClient restClient;

    public HttpPriceSuggestionClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    @CircuitBreaker(name = "aiPricingService")
    @Retry(name = "aiPricingService")
    public SuggestionBatchResponse requestSuggestions(SuggestionBatchRequest request) {
        log.debug("Enviando lote de {} itens para IA (Run ID: {})", request.items().size(), request.runId());

        return restClient.post()
                .uri("/v1/suggestions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SuggestionBatchResponse.class);
    }
}