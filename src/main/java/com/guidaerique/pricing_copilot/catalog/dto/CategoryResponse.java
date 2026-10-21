package com.guidaerique.pricing_copilot.catalog.dto;

import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;

import java.math.BigDecimal;

public record CategoryResponse(
        Long id,
        String name,
        Vertical vertical,
        BigDecimal minMargin,
        BigDecimal maxMargin,
        BigDecimal maxVariation
) {
    public static CategoryResponse fromEntity(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getVertical(),
                c.getMinMargin(),
                c.getMaxMargin(),
                c.getMaxVariation()
        );
    }
}