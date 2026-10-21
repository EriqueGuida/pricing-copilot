package com.guidaerique.pricing_copilot.alert.dto;

import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertStatus;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long productId,
        String productSku,
        String productName,
        AlertType type,
        AlertStatus status,
        String message,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static AlertResponse fromEntity(Alert a) {
        return new AlertResponse(
                a.getId(),
                a.getProduct().getId(),
                a.getProduct().getSku(),
                a.getProduct().getName(),
                a.getType(),
                a.getStatus(),
                a.getMessage(),
                a.getCreatedAt(),
                a.getResolvedAt()
        );
    }
}