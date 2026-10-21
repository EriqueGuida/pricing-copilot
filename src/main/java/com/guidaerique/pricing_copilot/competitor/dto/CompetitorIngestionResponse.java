package com.guidaerique.pricing_copilot.competitor.dto;

import java.util.List;

public record CompetitorIngestionResponse(
        int totalProcessed,
        int successCount,
        int failureCount,
        List<IngestionErrorDetail> errors
) {}