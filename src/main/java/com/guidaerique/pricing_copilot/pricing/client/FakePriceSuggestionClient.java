package com.guidaerique.pricing_copilot.pricing.client;

import com.guidaerique.pricing_copilot.pricing.client.dto.ExplanationResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionResultResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@ConditionalOnProperty(name = "pricing.ai-client.type", havingValue = "fake")
public class FakePriceSuggestionClient implements PriceSuggestionClient {

    @Override
    public SuggestionBatchResponse requestSuggestions(SuggestionBatchRequest request) {
        List<SuggestionResultResponse> results = request.items().stream()
                .map(item -> {
                    BigDecimal min = new BigDecimal(item.minPrice());
                    BigDecimal max = new BigDecimal(item.maxPrice());
                    BigDecimal suggested = min.add(max).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

                    ExplanationResponse explanation = new ExplanationResponse(
                            -1.75,
                            suggested,
                            suggested,
                            "NONE",
                            "HIGH"
                    );

                    return new SuggestionResultResponse(item.sku(), suggested, "ELASTICITY", explanation);
                })
                .toList();

        return new SuggestionBatchResponse("v1.0.0-mock", results);
    }
}