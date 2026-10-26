package com.guidaerique.pricing_copilot.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SuggestionBatchResponse(
    @JsonProperty("model_version") String modelVersion,
    List<SuggestionResultResponse> results
) {}