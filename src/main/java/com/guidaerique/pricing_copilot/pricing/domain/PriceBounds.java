package com.guidaerique.pricing_copilot.pricing.domain;

import java.math.BigDecimal;

public record PriceBounds(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean variationLimitHit
) {
    public PriceBounds {
        if (minPrice == null || maxPrice == null) {
            throw new IllegalArgumentException(
                    "Os limites de preço são obrigatórios."
            );
        }

        if (minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "O piso não pode ser maior que o teto."
            );
        }
    }
}