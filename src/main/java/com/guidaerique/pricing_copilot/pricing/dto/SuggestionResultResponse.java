package com.guidaerique.pricing_copilot.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record SuggestionResultResponse(
    String sku,
    @JsonProperty("suggested_price") BigDecimal suggestedPrice,
    String strategy,
    ExplanationResponse explanation
) {}