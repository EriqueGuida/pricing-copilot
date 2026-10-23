package com.guidaerique.pricing_copilot.pricing.dto;

import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.domain.PricingRunStatus;

import java.time.LocalDateTime;

public record PricingRunResponse(
    Long id,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    PricingRunStatus status,
    int processedItems,
    int updatedItems,
    int skippedItems,
    int errorItems,
    String modelVersion
) {
    public static PricingRunResponse fromEntity(PricingRun run) {
        return new PricingRunResponse(
            run.getId(),
            run.getStartedAt(),
            run.getFinishedAt(),
            run.getStatus(),
            run.getProcessedItems(),
            run.getUpdatedItems(),
            run.getSkippedItems(),
            run.getErrorItems(),
            run.getModelVersion()
        );
    }
}