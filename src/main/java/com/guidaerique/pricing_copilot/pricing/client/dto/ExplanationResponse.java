package com.guidaerique.pricing_copilot.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ExplanationResponse(
    Double elasticity,
    @JsonProperty("competitor_median") BigDecimal competitorMedian,
    @JsonProperty("unconstrained_optimum") BigDecimal unconstrainedOptimum,
    @JsonProperty("clipped_by") String clippedBy,
    String confidence
) {}