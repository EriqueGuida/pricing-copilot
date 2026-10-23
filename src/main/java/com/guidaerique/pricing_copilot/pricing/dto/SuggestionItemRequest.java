package com.guidaerique.pricing_copilot.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SuggestionItemRequest(
    String sku,
    @JsonProperty("category_id") Long categoryId,
    @JsonProperty("cost_price") String costPrice,
    @JsonProperty("current_price") String currentPrice,
    @JsonProperty("min_price") String minPrice,
    @JsonProperty("max_price") String maxPrice,
    @JsonProperty("competitor_prices") List<String> competitorPrices
) {}