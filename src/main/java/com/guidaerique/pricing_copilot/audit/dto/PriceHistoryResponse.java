package com.guidaerique.pricing_copilot.audit.dto;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceHistoryResponse(
        Long id,
        Long productId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        PriceOrigin origin,
        String changedByEmail,
        Long pricingRunId,
        String modelVersion,
        String reason,
        String explanation,
        LocalDateTime createdAt
) {
    public static PriceHistoryResponse fromEntity(PriceHistory h) {
        return new PriceHistoryResponse(
                h.getId(),
                h.getProduct().getId(),
                h.getOldPrice(),
                h.getNewPrice(),
                h.getOrigin(),
                h.getChangedBy() != null ? h.getChangedBy().getEmail() : null,
                h.getPricingRunId(),
                h.getModelVersion(),
                h.getReason(),
                h.getExplanation(),
                h.getCreatedAt()
        );
    }
}