package com.guidaerique.pricing_copilot.pricing.client;

import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchRequest;
import com.guidaerique.pricing_copilot.pricing.client.dto.SuggestionBatchResponse;

public interface PriceSuggestionClient {
    SuggestionBatchResponse requestSuggestions(SuggestionBatchRequest request);
}