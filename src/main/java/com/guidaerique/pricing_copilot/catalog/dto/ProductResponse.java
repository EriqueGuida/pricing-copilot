package com.guidaerique.pricing_copilot.catalog.dto;

import com.guidaerique.pricing_copilot.catalog.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String sku,
        String ean,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal costPrice,
        BigDecimal currentPrice,
        LocalDateTime manualLockUntil,
        boolean active,
        Long version
) {
    public static ProductResponse fromEntity(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getEan(),
                p.getName(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getCostPrice(),
                p.getCurrentPrice(),
                p.getManualLockUntil(),
                p.isActive(),
                p.getVersion()
        );
    }
}