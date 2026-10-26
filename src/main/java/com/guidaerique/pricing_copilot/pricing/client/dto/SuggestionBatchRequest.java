package com.guidaerique.pricing_copilot.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SuggestionBatchRequest(
    @JsonProperty("run_id") Long runId,
    List<SuggestionItemRequest> items
) {}